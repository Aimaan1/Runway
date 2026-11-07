import java.util.concurrent.ThreadLocalRandom;

/**
 * One falling raindrop.
 *
 * PARALLELISM NOTE: thousands of these are updated every tick with a parallel
 * stream. That is safe because fall() only ever touches *this* drop's own
 * fields - no two threads ever write to the same drop, so no locking is needed.
 * It also uses ThreadLocalRandom rather than a shared java.util.Random, since a
 * shared Random would make every worker thread fight over the same seed.
 */
public class RainDrop {
    private double x;
    private double y;
    private double speed;   // pixels per second
    private double length;  // streak length in pixels
    private double landY;   // the "ground" height this drop splashes at (gives the scene depth)

    public RainDrop(int width, int height) {
        respawn(width, height, true);
    }

    /**
     * Moves the drop one tick. Returns a Splash if it hit the ground this tick
     * (and respawns it at the top), otherwise null.
     */
    public Splash fall(double seconds, double wind, int width, int height) {
        x += wind * speed * seconds;
        y += speed * seconds;

        if (y >= landY) {
            Splash splash = new Splash((float) x, (float) landY, 0);
            respawn(width, height, false);
            return splash;
        }

        // wrap around horizontally so the wind never blows the rain off-screen
        if (x < -60) {
            x += width + 120;
        } else if (x > width + 60) {
            x -= width + 120;
        }
        return null;
    }

    /** Writes this drop's streak (x1, y1, x2, y2) into a shared array at its own slot. */
    public void writeStreak(float[] target, int offset, double wind) {
        // the streak trails behind the drop, slanted by the wind
        target[offset] = (float) (x - wind * length);
        target[offset + 1] = (float) (y - length);
        target[offset + 2] = (float) x;
        target[offset + 3] = (float) y;
    }

    private void respawn(int width, int height, boolean anywhereOnScreen) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(2, height);
        x = random.nextDouble(-60, safeWidth + 60);
        y = anywhereOnScreen ? random.nextDouble(-safeHeight, safeHeight) : random.nextDouble(-140, -10);
        speed = random.nextDouble(520, 860);
        length = random.nextDouble(8, 18);
        landY = random.nextDouble(safeHeight * 0.05, safeHeight);
    }
}
