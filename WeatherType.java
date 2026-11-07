import java.awt.Color;

/**
 * The three weather states the airport cycles through:
 * DAYTIME -> RAIN -> LIGHTNING -> DAYTIME -> ...
 *
 * Each state only describes its *targets* (how heavy the rain is, how dark it
 * gets, how windy). WeatherSystem eases the live values toward these targets
 * every tick, which is what makes the change between states look smooth.
 */
public enum WeatherType {
    //          label            secs  rain  dark  wind  clouds takeoffs  HUD colour
    DAYTIME  ("Clear daytime",    25,  0.00, 0.00, 0.00, 0.35,  true,  new Color(255, 214, 90)),
    RAIN     ("Rain",             20,  0.55, 0.35, 0.15, 0.80,  true,  new Color(120, 170, 255)),
    LIGHTNING("Lightning storm",  18,  1.00, 0.62, 0.45, 1.00,  false, new Color(200, 160, 255));

    private final String label;
    private final int durationSeconds;
    private final double rainIntensity; // 0..1, fraction of the raindrop pool that is falling
    private final double darkness;      // 0..1, how dark the night-style overlay gets
    private final double wind;          // horizontal drift of rain, as a fraction of fall speed
    private final double cloudCover;    // 0..1, how many cloud shadows drift over the field
    private final boolean takeoffsAllowed;
    private final Color hudColor;

    WeatherType(String label, int durationSeconds, double rainIntensity, double darkness,
                double wind, double cloudCover, boolean takeoffsAllowed, Color hudColor) {
        this.label = label;
        this.durationSeconds = durationSeconds;
        this.rainIntensity = rainIntensity;
        this.darkness = darkness;
        this.wind = wind;
        this.cloudCover = cloudCover;
        this.takeoffsAllowed = takeoffsAllowed;
        this.hudColor = hudColor;
    }

    /** The state that follows this one in the cycle (wraps back to the start). */
    public WeatherType next() {
        WeatherType[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public String getLabel() { return label; }
    public int getDurationSeconds() { return durationSeconds; }
    public double getRainIntensity() { return rainIntensity; }
    public double getDarkness() { return darkness; }
    public double getWind() { return wind; }
    public double getCloudCover() { return cloudCover; }
    public boolean isTakeoffAllowed() { return takeoffsAllowed; }
    public Color getHudColor() { return hudColor; }
}
