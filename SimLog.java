import java.util.HashMap;
import java.util.Map;

// small logging helper for the simulation.
// a lot of the movement code runs every frame (100 times a second), so printing there floods
// the terminal and slows the sim down (printing to the console is surprisingly slow).
//  - debug(): everyday chatter like "New target set!". only printed when DEBUG is true
//  - warn():  real problems. always printed, but the same message at most once every 5 seconds
public final class SimLog {
    // set to true when you're debugging plane movement and want to see everything
    public static final boolean DEBUG = false;

    private static final long REPEAT_WINDOW_MS = 5000;
    private static final Map<String, Long> lastPrinted = new HashMap<>();

    private SimLog() {}

    public static void debug(String message) {
        if (DEBUG) {
            System.out.println(message);
        }
    }

    // synchronized because the weather threads can warn at the same time as the Swing thread
    public static synchronized void warn(String message) {
        long now = System.currentTimeMillis();
        Long last = lastPrinted.get(message);
        if (last != null && now - last < REPEAT_WINDOW_MS) {
            return; // already said this recently
        }
        if (lastPrinted.size() > 200) {
            lastPrinted.clear(); // stop the map growing forever if messages keep changing
        }
        lastPrinted.put(message, now);
        System.out.println(message);
    }
}
