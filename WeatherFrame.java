import java.awt.image.BufferedImage;

/**
 * An immutable snapshot of everything needed to draw the weather for one frame.
 *
 * PARALLELISM NOTE: the weather is calculated on background threads, but Swing
 * must paint on its own Event Dispatch Thread. Instead of locking, the
 * background side builds a brand-new WeatherFrame each tick and publishes it
 * through a volatile field. The paint thread just grabs whichever frame is
 * newest. Nothing in a frame is changed after it is built, so the two sides
 * never get in each other's way.
 */
public final class WeatherFrame {
    final WeatherType type;
    final WeatherType nextType;
    final int secondsLeft;
    final float[] rainStreaks;       // x1, y1, x2, y2 per drop
    final int rainCount;
    final float[] splashes;          // x, y, age per splash
    final int splashCount;
    final BufferedImage lightingOverlay; // null when it's fully daytime
    final LightningBolt bolt;        // null when no bolt is visible this frame
    final float flash;               // 0..1 whole-screen lightning flash
    final float[] clouds;            // x, y, radiusX, radiusY per cloud shadow
    final int cloudCount;
    final float sunlight;            // 0..1 warmth of the daytime sun
    final int workerThreads;         // distinct threads that did weather work recently
    final double updateMillis;       // how long the last parallel update took
    final boolean groundStop;

    WeatherFrame(WeatherType type, WeatherType nextType, int secondsLeft,
                 float[] rainStreaks, int rainCount, float[] splashes, int splashCount,
                 BufferedImage lightingOverlay, LightningBolt bolt, float flash,
                 float[] clouds, int cloudCount, float sunlight,
                 int workerThreads, double updateMillis, boolean groundStop) {
        this.type = type;
        this.nextType = nextType;
        this.secondsLeft = secondsLeft;
        this.rainStreaks = rainStreaks;
        this.rainCount = rainCount;
        this.splashes = splashes;
        this.splashCount = splashCount;
        this.lightingOverlay = lightingOverlay;
        this.bolt = bolt;
        this.flash = flash;
        this.clouds = clouds;
        this.cloudCount = cloudCount;
        this.sunlight = sunlight;
        this.workerThreads = workerThreads;
        this.updateMillis = updateMillis;
        this.groundStop = groundStop;
    }

    static WeatherFrame empty() {
        return new WeatherFrame(WeatherType.DAYTIME, WeatherType.RAIN, WeatherType.DAYTIME.getDurationSeconds(),
                new float[0], 0, new float[0], 0, null, null, 0f, new float[0], 0, 1f, 0, 0, false);
    }
}
