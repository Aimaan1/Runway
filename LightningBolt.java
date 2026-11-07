import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A jagged lightning bolt, grown with the fork/join framework.
 *
 * HOW THE BOLT IS BUILT (midpoint displacement):
 *   1. Start with a straight line from the cloud to the ground.
 *   2. Push its midpoint sideways by a random amount -> two shorter lines.
 *   3. Repeat on each half, with a smaller push each time.
 * After 7 levels that gives a 129-point zig-zag that looks like lightning.
 *
 * PARALLELISM NOTE: step 3 is "divide and conquer" - the two halves don't
 * depend on each other, so SegmentTask forks one half onto another worker
 * thread while the current thread does the other half, then joins the results.
 * The side branches are independent too, so they all run at once with
 * ForkJoinTask.invokeAll().
 *
 * Immutable once built, so the paint thread can read it safely.
 */
public final class LightningBolt {
    private static final int MAIN_DEPTH = 7;
    private static final int BRANCH_DEPTH = 5;

    // each float[] is a polyline packed as x0, y0, x1, y1, ...; index 0 is the main channel
    private final List<float[]> polylines;
    private final float strikeX;
    private final float strikeY;

    private LightningBolt(List<float[]> polylines, float strikeX, float strikeY) {
        this.polylines = polylines;
        this.strikeX = strikeX;
        this.strikeY = strikeY;
    }

    public List<float[]> getPolylines() { return polylines; }
    public float getStrikeX() { return strikeX; }
    public float getStrikeY() { return strikeY; }

    /**
     * Builds a random bolt for a viewport of the given size. Call this from
     * inside a ForkJoinPool (e.g. via CompletableFuture.supplyAsync(..., pool))
     * so the forked subtasks run on that pool's worker threads.
     */
    public static LightningBolt generate(int width, int height) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double startX = random.nextDouble(width * 0.1, width * 0.9);
        double endX = startX + random.nextDouble(-160, 160);
        double endY = random.nextDouble(height * 0.25, height * 0.85);

        // main channel, built in parallel by the recursive task
        List<double[]> mainPath = new SegmentTask(startX, -20, endX, endY, height * 0.12, MAIN_DEPTH).invoke();

        // 2-5 side branches, each starting from a random point on the upper part of the main channel
        int branchCount = 2 + random.nextInt(4);
        List<SegmentTask> branchTasks = new ArrayList<>();
        for (int i = 0; i < branchCount; i++) {
            double[] from = mainPath.get(random.nextInt(Math.max(1, (int) (mainPath.size() * 0.7))));
            double angle = Math.toRadians(random.nextDouble(25, 70)) * (random.nextBoolean() ? 1 : -1);
            double branchLength = random.nextDouble(60, 190);
            double toX = from[0] + Math.sin(angle) * branchLength;
            double toY = from[1] + Math.cos(angle) * branchLength;
            branchTasks.add(new SegmentTask(from[0], from[1], toX, toY, branchLength * 0.18, BRANCH_DEPTH));
        }
        ForkJoinTask.invokeAll(branchTasks); // all branches grow at the same time

        List<float[]> polylines = new ArrayList<>();
        polylines.add(pack(mainPath));
        for (SegmentTask branch : branchTasks) {
            polylines.add(pack(branch.join()));
        }
        return new LightningBolt(polylines, (float) endX, (float) endY);
    }

    private static float[] pack(List<double[]> points) {
        float[] packed = new float[points.size() * 2];
        for (int i = 0; i < points.size(); i++) {
            packed[i * 2] = (float) points.get(i)[0];
            packed[i * 2 + 1] = (float) points.get(i)[1];
        }
        return packed;
    }

    /**
     * Recursively displaces the midpoint of one line segment. Returns the
     * points from start to end inclusive.
     */
    private static final class SegmentTask extends RecursiveTask<List<double[]>> {
        // below this depth the work is too small to be worth handing to another thread
        private static final int SEQUENTIAL_DEPTH = 3;

        private final double x1, y1, x2, y2, displacement;
        private final int depth;

        SegmentTask(double x1, double y1, double x2, double y2, double displacement, int depth) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.displacement = displacement;
            this.depth = depth;
        }

        @Override
        protected List<double[]> compute() {
            if (depth == 0) {
                List<double[]> ends = new ArrayList<>();
                ends.add(new double[]{x1, y1});
                ends.add(new double[]{x2, y2});
                return ends;
            }

            // push the midpoint sideways (perpendicular to the segment)
            double dx = x2 - x1;
            double dy = y2 - y1;
            double length = Math.max(0.0001, Math.hypot(dx, dy));
            double offset = ThreadLocalRandom.current().nextDouble(-displacement, displacement);
            double midX = (x1 + x2) / 2 + (-dy / length) * offset;
            double midY = (y1 + y2) / 2 + (dx / length) * offset;

            SegmentTask firstHalf = new SegmentTask(x1, y1, midX, midY, displacement / 2, depth - 1);
            SegmentTask secondHalf = new SegmentTask(midX, midY, x2, y2, displacement / 2, depth - 1);

            List<double[]> first;
            List<double[]> second;
            if (depth > SEQUENTIAL_DEPTH) {
                firstHalf.fork();               // hand the first half to another worker...
                second = secondHalf.compute();  // ...do the second half on this thread...
                first = firstHalf.join();       // ...then wait for the first half to finish
            } else {
                first = firstHalf.compute();
                second = secondHalf.compute();
            }

            // stitch the halves together, dropping the duplicated midpoint
            List<double[]> joined = new ArrayList<>(first.size() + second.size() - 1);
            joined.addAll(first);
            joined.addAll(second.subList(1, second.size()));
            return joined;
        }
    }
}
