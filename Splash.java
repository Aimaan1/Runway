/**
 * A small ripple left where a raindrop hits the ground.
 *
 * Immutable on purpose: aging a splash returns a NEW Splash instead of changing
 * this one. Immutable objects can be passed between threads (the weather worker
 * threads and the Swing paint thread) without any synchronisation.
 */
public final class Splash {
    public static final int LIFETIME_TICKS = 12;

    private final float x;
    private final float y;
    private final int age;

    public Splash(float x, float y, int age) {
        this.x = x;
        this.y = y;
        this.age = age;
    }

    public Splash older() {
        return new Splash(x, y, age + 1);
    }

    public boolean isAlive() {
        return age < LIFETIME_TICKS;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public int getAge() { return age; }
}
