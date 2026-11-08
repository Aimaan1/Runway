import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Runs the airport's weather cycle (daytime -> rain -> lightning storm -> ...)
 * on background threads and draws the result over the simulation.
 *
 * Parallelism used here, and why:
 *
 *  1. ScheduledExecutorService ("weather-clock" thread)
 *     The weather ticks on its own thread at its own rate, so the heavy work
 *     never slows down the Swing timer that moves the aircraft.
 *
 *  2. A dedicated ForkJoinPool ("weather-worker-N" threads)
 *     All parallel weather work runs in this pool rather than the shared
 *     common pool, so it can't starve other parallel code in the program.
 *
 *  3. CompletableFuture (task parallelism)
 *     Rain physics and the lighting overlay are two unrelated jobs, so each
 *     tick starts them at the same time and waits for both with allOf().
 *
 *  4. Parallel streams (data parallelism)
 *     - every active raindrop falls in parallel, and the splashes are gathered
 *       with a thread-safe collect()
 *     - raindrop streaks are written into one shared array, each drop into
 *       its own slot, so no locking is needed
 *     - the night-lighting overlay is filled in parallel, one row per task
 *     - splashes are aged and filtered in parallel
 *
 *  5. Fork/join recursion (RecursiveTask)
 *     Lightning bolts are grown by splitting them in half recursively - see
 *     LightningBolt.
 *
 *  6. Safe hand-off between threads
 *     volatile WeatherFrame, AtomicReference for the bolt that's still being
 *     generated, AtomicBoolean for the "skip weather" key, and a concurrent
 *     Set to record which threads did work.
 */
public class WeatherSystem {
    private static final int TICK_MS = 30;
    private static final int MAX_DROPS = 600;
    private static final int MAX_SPLASHES = 150;
    private static final int MAX_CLOUDS = 8;
    private static final int OVERLAY_SCALE = 4;     // lighting is computed at 1/4 resolution then scaled up
    private static final double LIGHT_RADIUS = 85;  // pixels lit around each airfield light at night
    private static final double EASE = 0.02;        // how quickly live values move toward the targets

    private final List<Vector2> lightSources;
    private final ForkJoinPool weatherPool;
    private final ScheduledExecutorService clock;

    // ---- shared between threads ----
    private volatile WeatherFrame latestFrame = WeatherFrame.empty();
    private volatile int viewportWidth = 1000;
    private volatile int viewportHeight = 1000;
    private volatile boolean groundStop = false;
    private final AtomicBoolean skipRequested = new AtomicBoolean(false);
    private final AtomicBoolean boltBeingGenerated = new AtomicBoolean(false);
    private final AtomicReference<LightningBolt> finishedBolt = new AtomicReference<>();
    private final Set<String> threadsSeen = ConcurrentHashMap.newKeySet();

    // ---- only touched by the weather-clock thread ----
    private final RainDrop[] drops = new RainDrop[MAX_DROPS];
    private final double[][] clouds = new double[MAX_CLOUDS][5]; // x, y, radiusX, radiusY, speed
    private WeatherType current = WeatherType.DAYTIME;
    private long stateStartedAt = System.currentTimeMillis();
    private double rainIntensity = 0;
    private double darkness = 0;
    private double wind = 0;
    private double cloudCover = WeatherType.DAYTIME.getCloudCover();
    private List<Splash> splashes = new ArrayList<>();
    private LightningBolt visibleBolt = null;
    private long boltVisibleUntil = 0;
    private long nextStrikeAt = 0;
    private double flash = 0;
    private BufferedImage overlay = null;
    private double overlayDarkness = -1;
    private int overlayWidth = -1;
    private int overlayHeight = -1;
    private int tickCount = 0;

    public WeatherSystem(List<Vector2> lightSources) {
        this.lightSources = new ArrayList<>(lightSources);

        int cores = Runtime.getRuntime().availableProcessors();
        AtomicInteger workerNumber = new AtomicInteger(1);
        this.weatherPool = new ForkJoinPool(Math.max(2, cores),
                pool -> {
                    ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    worker.setName("weather-worker-" + workerNumber.getAndIncrement());
                    worker.setDaemon(true); // don't keep the program alive after the window closes
                    return worker;
                },
                null, false);

        this.clock = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "weather-clock");
            thread.setDaemon(true);
            return thread;
        });

        for (int i = 0; i < MAX_DROPS; i++) {
            drops[i] = new RainDrop(viewportWidth, viewportHeight);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (double[] cloud : clouds) {
            cloud[0] = random.nextDouble(-200, 1200);
            cloud[1] = random.nextDouble(0, 1000);
            cloud[2] = random.nextDouble(90, 200);
            cloud[3] = cloud[2] * random.nextDouble(0.45, 0.7);
            cloud[4] = random.nextDouble(12, 28);
        }
    }

    // ------------------------------------------------------------------ control

    public void start() {
        // scheduleAtFixedRate silently stops repeating if a tick throws, so catch everything
        clock.scheduleAtFixedRate(() -> {
            try {
                tick();
            } catch (Throwable error) {
                System.out.println("Weather tick failed: " + error);
            }
        }, 0, TICK_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        clock.shutdownNow();
        weatherPool.shutdownNow();
    }

    /** Jump straight to the next weather state (bound to the W key). Safe from any thread. */
    public void skipToNextWeather() {
        skipRequested.set(true);
    }

    /** True while lightning is about: air traffic control holds all departures. */
    public boolean isGroundStop() {
        return groundStop;
    }

    public WeatherType getCurrentWeather() {
        return latestFrame.type;
    }

    // ------------------------------------------------------------------ the tick (weather-clock thread)

    private void tick() {
        long now = System.currentTimeMillis();
        long started = System.nanoTime();
        double seconds = TICK_MS / 1000.0;
        int width = viewportWidth;
        int height = viewportHeight;
        tickCount++;

        // move on to the next weather state when this one has run its course
        if (skipRequested.getAndSet(false) || now - stateStartedAt >= current.getDurationSeconds() * 1000L) {
            current = current.next();
            stateStartedAt = now;
            nextStrikeAt = now + 1500;
            System.out.println("Weather changed to: " + current.getLabel());
        }
        groundStop = !current.isTakeoffAllowed();

        // ease the live values toward the current state's targets for smooth transitions
        rainIntensity += (current.getRainIntensity() - rainIntensity) * EASE;
        darkness += (current.getDarkness() - darkness) * EASE;
        wind += (current.getWind() - wind) * EASE;
        cloudCover += (current.getCloudCover() - cloudCover) * EASE;

        int activeDrops = (int) Math.round(MAX_DROPS * rainIntensity);
        double currentWind = wind;
        double currentDarkness = darkness + flash * -0.5; // a flash briefly lights up the night

        // TASK PARALLELISM: rain physics and lighting are independent, so run both at once
        CompletableFuture<List<Splash>> rainJob =
                CompletableFuture.supplyAsync(() -> updateRain(activeDrops, seconds, currentWind, width, height), weatherPool);
        CompletableFuture<BufferedImage> lightingJob =
                CompletableFuture.supplyAsync(() -> updateLighting(Math.max(0, currentDarkness), width, height), weatherPool);
        CompletableFuture<float[]> streaksJob =
                rainJob.thenApplyAsync(ignored -> buildRainStreaks(activeDrops, currentWind), weatherPool);

        // meanwhile, this thread handles the small sequential jobs
        updateLightning(now, width, height);
        float[] cloudData = updateClouds(seconds, width, height);

        CompletableFuture.allOf(lightingJob, streaksJob).join(); // wait for both parallel jobs

        List<Splash> newSplashes = rainJob.join();
        overlay = lightingJob.join();
        float[] streaks = streaksJob.join();

        // DATA PARALLELISM: age every splash, drop the faded ones, add the new ones
        List<Splash> nextSplashes = weatherPool.submit(() ->
                splashes.parallelStream()
                        .map(Splash::older)
                        .filter(Splash::isAlive)
                        .collect(Collectors.toList())).join();
        nextSplashes.addAll(newSplashes);
        if (nextSplashes.size() > MAX_SPLASHES) {
            nextSplashes = new ArrayList<>(nextSplashes.subList(nextSplashes.size() - MAX_SPLASHES, nextSplashes.size()));
        }
        splashes = nextSplashes;

        float[] splashData = new float[splashes.size() * 3];
        for (int i = 0; i < splashes.size(); i++) {
            Splash splash = splashes.get(i);
            splashData[i * 3] = splash.getX();
            splashData[i * 3 + 1] = splash.getY();
            splashData[i * 3 + 2] = splash.getAge();
        }

        // refresh the "threads used" figure every couple of seconds
        int workers = threadsSeen.size();
        if (tickCount % 70 == 0) {
            threadsSeen.clear();
        }

        int secondsLeft = (int) Math.max(0, (current.getDurationSeconds() * 1000L - (now - stateStartedAt)) / 1000);
        LightningBolt boltToShow = (visibleBolt != null && now < boltVisibleUntil && (tickCount / 2) % 3 != 0) ? visibleBolt : null;
        float sunlight = (float) Math.max(0, 1 - darkness * 2.5);
        double updateMillis = (System.nanoTime() - started) / 1_000_000.0;

        // publish the finished frame for the paint thread (volatile write)
        latestFrame = new WeatherFrame(current, current.next(), secondsLeft,
                streaks, activeDrops, splashData, splashes.size(), overlay, boltToShow, (float) flash,
                cloudData, cloudData.length / 4, sunlight, workers, updateMillis, groundStop);
    }

    /** Moves every active raindrop in parallel and collects the splashes they make. */
    private List<Splash> updateRain(int activeDrops, double seconds, double currentWind, int width, int height) {
        threadsSeen.add(Thread.currentThread().getName());
        return IntStream.range(0, activeDrops)
                .parallel()
                .mapToObj(i -> {
                    if ((i & 255) == 0) {
                        threadsSeen.add(Thread.currentThread().getName());
                    }
                    return drops[i].fall(seconds, currentWind, width, height);
                })
                .filter(Objects::nonNull)        // only drops that landed this tick
                .collect(Collectors.toList());   // collect() is safe in parallel; add() to a shared list is not
    }

    /** Each drop writes its streak into its own 4 slots of one array, so threads never overlap. */
    private float[] buildRainStreaks(int activeDrops, double currentWind) {
        float[] streaks = new float[activeDrops * 4];
        IntStream.range(0, activeDrops)
                .parallel()
                .forEach(i -> drops[i].writeStreak(streaks, i * 4, currentWind));
        return streaks;
    }

    /**
     * Builds a low-resolution darkness mask with pools of light around the
     * airfield lights. Every row is calculated by a different parallel task.
     * Skipped when nothing has visibly changed since last time.
     */
    private BufferedImage updateLighting(double targetDarkness, int width, int height) {
        int overlayW = Math.max(1, width / OVERLAY_SCALE);
        int overlayH = Math.max(1, height / OVERLAY_SCALE);
        threadsSeen.add(Thread.currentThread().getName());

        if (targetDarkness < 0.01) {
            overlayDarkness = 0;
            return null;
        }
        if (overlay != null && overlayW == overlayWidth && overlayH == overlayHeight
                && Math.abs(targetDarkness - overlayDarkness) < 0.008) {
            return overlay; // reuse the last one
        }

        BufferedImage image = new BufferedImage(overlayW, overlayH, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        double[][] lights = lightSources.stream()
                .map(light -> new double[]{light.getXPos() / (double) OVERLAY_SCALE, light.getYPos() / (double) OVERLAY_SCALE})
                .toArray(double[][]::new);
        double radius = LIGHT_RADIUS / OVERLAY_SCALE;
        double radiusSquared = radius * radius;

        IntStream.range(0, overlayH).parallel().forEach(row -> {
            threadsSeen.add(Thread.currentThread().getName());
            int rowStart = row * overlayW;
            for (int col = 0; col < overlayW; col++) {
                double light = 0;
                for (double[] source : lights) {
                    double dx = col - source[0];
                    double dy = row - source[1];
                    double distanceSquared = dx * dx + dy * dy;
                    if (distanceSquared < radiusSquared) {
                        double falloff = 1 - distanceSquared / radiusSquared;
                        light += falloff * falloff;
                    }
                }
                light = Math.min(1, light);
                int alpha = (int) (Math.min(1, targetDarkness) * (1 - 0.85 * light) * 255);
                // deep navy for darkness, shifting slightly warm inside the light pools
                int red = (int) (10 + 60 * light);
                int green = (int) (14 + 40 * light);
                int blue = (int) (42 - 20 * light);
                pixels[rowStart + col] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        });

        overlayDarkness = targetDarkness;
        overlayWidth = overlayW;
        overlayHeight = overlayH;
        return image;
    }

    /** Decides when lightning strikes, and starts building bolts in the background. */
    private void updateLightning(long now, int width, int height) {
        // pick up a bolt that a worker thread has finished building
        LightningBolt ready = finishedBolt.getAndSet(null);
        if (ready != null) {
            visibleBolt = ready;
            boltVisibleUntil = now + 600;
            flash = 1.0;
            System.out.println("Lightning strike near (" + (int) ready.getStrikeX() + ", " + (int) ready.getStrikeY() + ")");
        }

        flash *= 0.86;
        // occasional second, weaker flicker, like real lightning
        if (visibleBolt != null && now < boltVisibleUntil && flash < 0.25 && ThreadLocalRandom.current().nextInt(6) == 0) {
            flash = 0.55;
        }

        if (current == WeatherType.LIGHTNING && now >= nextStrikeAt && boltBeingGenerated.compareAndSet(false, true)) {
            nextStrikeAt = now + ThreadLocalRandom.current().nextLong(1300, 4000);
            // build the bolt asynchronously in the pool; the clock thread doesn't wait for it
            CompletableFuture
                    .supplyAsync(() -> {
                        threadsSeen.add(Thread.currentThread().getName());
                        return LightningBolt.generate(width, height);
                    }, weatherPool)
                    .whenComplete((bolt, error) -> {
                        if (bolt != null) {
                            finishedBolt.set(bolt);
                        } else {
                            System.out.println("Could not build lightning bolt: " + error);
                        }
                        boltBeingGenerated.set(false);
                    });
        }
    }

    /** Drifts cloud shadows across the airfield (tiny job, stays sequential). */
    private float[] updateClouds(double seconds, int width, int height) {
        int visible = (int) Math.round(MAX_CLOUDS * cloudCover);
        float[] data = new float[visible * 4];
        for (int i = 0; i < MAX_CLOUDS; i++) {
            double[] cloud = clouds[i];
            cloud[0] += (cloud[4] + wind * 120) * seconds;
            if (cloud[0] - cloud[2] > width) {
                cloud[0] = -cloud[2];
                cloud[1] = ThreadLocalRandom.current().nextDouble(0, Math.max(1, height));
            }
            if (i < visible) {
                data[i * 4] = (float) cloud[0];
                data[i * 4 + 1] = (float) cloud[1];
                data[i * 4 + 2] = (float) cloud[2];
                data[i * 4 + 3] = (float) cloud[3];
            }
        }
        return data;
    }

    // ------------------------------------------------------------------ drawing (Swing paint thread)

    /** Draws the weather over everything in the scene. Call from paintComponent. */
    public void drawWeather(Graphics drawer, int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
        WeatherFrame frame = latestFrame; // one volatile read; use this snapshot for the whole paint

        Graphics2D g2 = (Graphics2D) drawer.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, PixelArt.aaHint());

        // daytime: warm sunlight from the top-left corner
        if (frame.sunlight > 0.01f) {
            float radius = Math.max(width, height) * 0.9f;
            g2.setPaint(new RadialGradientPaint(0, 0, radius, new float[]{0f, 1f},
                    new Color[]{new Color(255, 225, 140, (int) (70 * frame.sunlight)), new Color(255, 225, 140, 0)}));
            g2.fillRect(0, 0, width, height);
        }

        // cloud shadows drifting over the ground
        int cloudAlpha = (int) (28 + (1 - frame.sunlight) * 22);
        g2.setColor(new Color(0, 0, 20, cloudAlpha));
        for (int i = 0; i < frame.cloudCount; i++) {
            float cx = frame.clouds[i * 4];
            float cy = frame.clouds[i * 4 + 1];
            float rx = frame.clouds[i * 4 + 2];
            float ry = frame.clouds[i * 4 + 3];
            g2.fillOval((int) (cx - rx), (int) (cy - ry), (int) (rx * 2), (int) (ry * 2));
            g2.fillOval((int) (cx - rx * 0.4), (int) (cy - ry * 1.3), (int) (rx * 1.1), (int) (ry * 1.4));
        }

        // darkness with pools of light around the airfield lights
        if (frame.lightingOverlay != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(frame.lightingOverlay, 0, 0, width, height, null);
        }

        // rain streaks (antialiasing off here: smoothing hundreds of lines every frame is slow)
        if (frame.rainCount > 0) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(new Color(185, 205, 235, 150));
            g2.setStroke(new BasicStroke(1.1f));
            float[] streaks = frame.rainStreaks;
            for (int i = 0; i < frame.rainCount; i++) {
                int o = i * 4;
                g2.drawLine((int) streaks[o], (int) streaks[o + 1], (int) streaks[o + 2], (int) streaks[o + 3]);
            }
        }

        // splash ripples, expanding and fading with age
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int i = 0; i < frame.splashCount; i++) {
            float x = frame.splashes[i * 3];
            float y = frame.splashes[i * 3 + 1];
            float age = frame.splashes[i * 3 + 2];
            float life = 1 - age / Splash.LIFETIME_TICKS;
            int size = (int) (2 + age * 0.9f);
            g2.setColor(new Color(200, 220, 245, (int) (160 * life)));
            g2.drawOval((int) x - size, (int) y - size / 3, size * 2, Math.max(1, size * 2 / 3));
        }

        // lightning bolt: wide soft glow, then a bright core
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, PixelArt.aaHint());
        if (frame.bolt != null) {
            Stroke original = g2.getStroke();
            List<float[]> lines = frame.bolt.getPolylines();
            for (int i = 0; i < lines.size(); i++) {
                float[] line = lines.get(i);
                int points = line.length / 2;
                int[] xs = new int[points];
                int[] ys = new int[points];
                for (int p = 0; p < points; p++) {
                    xs[p] = (int) line[p * 2];
                    ys[p] = (int) line[p * 2 + 1];
                }
                boolean main = i == 0;
                g2.setStroke(new BasicStroke(main ? 9f : 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(170, 140, 255, 80));
                g2.drawPolyline(xs, ys, points);
                g2.setStroke(new BasicStroke(main ? 2.6f : 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(245, 240, 255));
                g2.drawPolyline(xs, ys, points);
            }
            // glow where it hits the ground
            float sx = frame.bolt.getStrikeX();
            float sy = frame.bolt.getStrikeY();
            g2.setPaint(new RadialGradientPaint(sx, sy, 60, new float[]{0f, 1f},
                    new Color[]{new Color(230, 220, 255, 170), new Color(230, 220, 255, 0)}));
            g2.fillOval((int) sx - 60, (int) sy - 60, 120, 120);
            g2.setStroke(original);
        }

        // whole-sky flash
        if (frame.flash > 0.02f) {
            g2.setColor(new Color(235, 235, 255, (int) (Math.min(1f, frame.flash) * 120)));
            g2.fillRect(0, 0, width, height);
        }

        g2.dispose();
    }

    /** Draws a small retro-style weather panel with its left edge at x and its bottom at bottomY. */
    public void drawHud(Graphics drawer, int x, int bottomY) {
        WeatherFrame frame = latestFrame;
        String[] lines = {
            "WEATHER: " + frame.type.getLabel(),
            "Next: " + frame.nextType.getLabel() + " in " + frame.secondsLeft + "s",
            "Raindrops: " + frame.rainCount + "   Splashes: " + frame.splashCount,
            String.format("Parallel update: %.1f ms on %d threads", frame.updateMillis, frame.workerThreads),
            frame.groundStop ? "ATC: GROUND STOP - no takeoffs" : "ATC: departures normal"
        };

        if (PixelArt.isEnabled()) {
            // wooden frame + parchment box to match the pixel-art scene
            Graphics2D pg = (Graphics2D) drawer.create();
            Font pf = PixelArt.font(11);
            pg.setFont(pf);
            FontMetrics pm = pg.getFontMetrics(pf);
            int lh = 13;
            int tw = 0;
            for (String line : lines) {
                tw = Math.max(tw, pm.stringWidth(line));
            }
            int inset = PixelArt.BOX_INSET;
            int bw = tw + inset * 2;
            int bh = lines.length * lh + inset * 2 - 2;
            int by = bottomY - bh;
            PixelArt.drawWoodenBox(pg, x, by, bw, bh);
            for (int i = 0; i < lines.length; i++) {
                if (i == 0) {
                    pg.setColor(PixelArt.forParchment(frame.type.getHudColor()));
                } else if (i == 4 && frame.groundStop) {
                    pg.setColor(PixelArt.INK_ALERT);
                } else {
                    pg.setColor(PixelArt.INK);
                }
                pg.drawString(lines[i], x + inset, by + inset + lh * (i + 1) - 4);
            }
            drawWeatherKeyHint(pg, x, by - 6);
            pg.dispose();
            return;
        }

        Graphics2D g2 = (Graphics2D) drawer.create();
        Font font = new Font("Monospaced", Font.PLAIN, 11);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics(font);
        int lineHeight = 13;
        int padding = 6;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, metrics.stringWidth(line));
        }
        int boxWidth = textWidth + padding * 2 + 6;
        int boxHeight = lines.length * lineHeight + padding * 2 + 2;
        int y = bottomY - boxHeight;

        // same double-bordered navy box style the aircraft info windows use
        g2.setColor(Color.WHITE);
        g2.fillRect(x, y, boxWidth, boxHeight);
        g2.setColor(new Color(8, 8, 44));
        g2.fillRect(x + 3, y + 3, boxWidth - 6, boxHeight - 6);
        g2.setColor(new Color(90, 100, 210));
        g2.drawRect(x + 5, y + 5, boxWidth - 11, boxHeight - 11);

        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                g2.setColor(frame.type.getHudColor());
            } else if (i == 4 && frame.groundStop) {
                g2.setColor(new Color(255, 110, 110));
            } else {
                g2.setColor(Color.WHITE);
            }
            g2.drawString(lines[i], x + padding + 3, y + padding + lineHeight * (i + 1) - 1);
        }
        drawWeatherKeyHint(g2, x, y - 6);
        g2.dispose();
    }

    /**
     * Big, separate prompt telling the user they can press W to change the weather: a keyboard
     * key button next to a bold label. The key "presses" itself briefly every 1.6 seconds so it
     * catches the eye. bottomY is where the bottom edge of the prompt should sit.
     */
    private void drawWeatherKeyHint(Graphics2D g, int x, int bottomY) {
        boolean pixel = PixelArt.isEnabled();
        String label = "Press W to change the weather";
        Font labelFont = pixel ? PixelArt.font(14) : new Font("Monospaced", Font.BOLD, 14);
        FontMetrics labelMetrics = g.getFontMetrics(labelFont);

        int key = 27;                                     // keycap size (a multiple of the pixel scale)
        int pad = pixel ? PixelArt.BOX_INSET - 4 : 10;
        int width = pad * 2 + key + 12 + labelMetrics.stringWidth(label);
        int height = pad * 2 + key;
        int y = bottomY - height;

        // background panel, matching the style of the weather panel below it
        if (pixel) {
            PixelArt.drawWoodenBox(g, x, y, width, height);
        } else {
            g.setColor(Color.WHITE);
            g.fillRect(x, y, width, height);
            g.setColor(new Color(8, 8, 44));
            g.fillRect(x + 3, y + 3, width - 6, height - 6);
        }

        // keycap: dark outline, darker "side" underneath, light face on top.
        // while pressed the face sinks down and the side gets thinner
        boolean pressed = (System.currentTimeMillis() % 1600) < 200;
        int kx = x + pad;
        int ky = y + pad;
        int side = pressed ? 3 : 6;
        Color outline = pixel ? PixelArt.OUTLINE : new Color(20, 20, 30);
        Color sideColour = pixel ? new Color(170, 150, 128) : new Color(150, 150, 165);
        Color face = pressed ? new Color(255, 236, 150) : new Color(250, 246, 236);
        g.setColor(outline);
        g.fillRect(kx, ky, key, key);
        g.setColor(sideColour);
        g.fillRect(kx + 3, ky + 3, key - 6, key - 6);
        int faceY = ky + 3 + (6 - side);
        int faceH = key - 6 - 6;
        g.setColor(face);
        g.fillRect(kx + 3, faceY, key - 6, faceH);
        g.setColor(Color.WHITE);
        g.fillRect(kx + 3, faceY, key - 6, 3); // shine along the top of the key

        Font keyFont = new Font("Monospaced", Font.BOLD, 15);
        FontMetrics keyMetrics = g.getFontMetrics(keyFont);
        g.setFont(keyFont);
        g.setColor(pixel ? PixelArt.INK : new Color(20, 20, 40));
        g.drawString("W", kx + (key - keyMetrics.stringWidth("W")) / 2,
                faceY + (faceH + keyMetrics.getAscent() - keyMetrics.getDescent()) / 2);

        // the label itself
        g.setFont(labelFont);
        g.setColor(pixel ? PixelArt.INK_ACCENT : new Color(255, 220, 90));
        g.drawString(label, kx + key + 12,
                ky + (key + labelMetrics.getAscent() - labelMetrics.getDescent()) / 2);
    }
}
