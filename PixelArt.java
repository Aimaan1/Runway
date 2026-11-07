import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

// shared settings, palette and helpers for the cozy hand-drawn pixel-art look.
// the world is painted into a small low-resolution image where 1 "art pixel" = SCALE screen
// pixels, then blown back up with nearest-neighbour scaling so every art pixel is a crisp block.
// style rules used everywhere: warm saturated colours, dark brown outlines (never pure black),
// light coming from the top-left (highlight top/left edges, shade bottom/right edges)
public final class PixelArt {
    // how many screen pixels make up one art pixel
    public static final int SCALE = 3;

    // toggled at runtime with the P key
    private static volatile boolean enabled = true;

    // ---- shared palette
    public static final Color OUTLINE = new Color(58, 36, 28);      // warm dark brown outline
    public static final Color INK = new Color(74, 40, 20);          // text on parchment
    public static final Color INK_ACCENT = new Color(150, 70, 28);  // headings on parchment
    public static final Color INK_ALERT = new Color(176, 38, 30);   // warnings on parchment

    private static final Color WOOD = new Color(200, 122, 60);
    private static final Color WOOD_LIGHT = new Color(232, 162, 88);
    private static final Color WOOD_DARK = new Color(150, 82, 38);
    private static final Color WOOD_INNER = new Color(116, 58, 28);
    private static final Color PARCHMENT = new Color(252, 230, 174);
    private static final Color PARCHMENT_SHADE = new Color(236, 204, 142);
    private static final Color RIVET = new Color(252, 210, 112);

    private PixelArt() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    // antialiasing hint drawing code should use: smoothing off in pixel mode so edges stay hard
    public static Object aaHint() {
        return enabled ? RenderingHints.VALUE_ANTIALIAS_OFF : RenderingHints.VALUE_ANTIALIAS_ON;
    }

    // switches off every kind of smoothing on a graphics context
    public static void makeCrisp(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    // ---- drawing helpers (in art-pixel coordinates)

    public static void px(Graphics g, int x, int y, Color c) {
        g.setColor(c);
        g.fillRect(x, y, 1, 1);
    }

    public static void rect(Graphics g, int x, int y, int w, int h, Color c) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.setColor(c);
        g.fillRect(x, y, w, h);
    }

    // ---- colour helpers

    public static Color mix(Color a, Color b, double t) {
        return new Color(
            clamp((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t)),
            clamp((int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t)),
            clamp((int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t)));
    }

    public static Color shade(Color c, double factor) {
        return new Color(clamp((int) (c.getRed() * factor)), clamp((int) (c.getGreen() * factor)), clamp((int) (c.getBlue() * factor)));
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(alpha));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ---- deterministic randomness, so textures look hand-placed but never flicker between frames

    // repeatable pseudo-random value in 0..1 for an integer coordinate
    public static double hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / (double) 0x7fffffff;
    }

    // smooth value noise in 0..1, used for soft colour patches in grass and asphalt
    public static double noise(double x, double y, int seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(x0, y0, seed);
        double b = hash(x0 + 1, y0, seed);
        double c = hash(x0, y0 + 1, seed);
        double d = hash(x0 + 1, y0 + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    // ---- UI

    public static Font font(int size) {
        return new Font("Monospaced", Font.BOLD, size);
    }

    // inner padding (screen pixels) between a wooden box's edge and its text
    public static final int BOX_INSET = SCALE * 4 + 4;

    // draws a dialogue box in screen coordinates: dark outline, carved wooden frame lit from the
    // top-left, gold corner rivets and a parchment panel inside. built out of SCALE-sized blocks
    // so it lines up with the pixel art underneath
    public static void drawWoodenBox(Graphics g, int x, int y, int w, int h) {
        int u = SCALE;
        x = (x / u) * u;
        y = (y / u) * u;
        w = ((w + u - 1) / u) * u;
        h = ((h + u - 1) / u) * u;

        // outline with clipped corners for a rounded look
        rect(g, x + u, y, w - 2 * u, h, OUTLINE);
        rect(g, x, y + u, w, h - 2 * u, OUTLINE);
        // wooden frame, lit from the top-left
        rect(g, x + u, y + u, w - 2 * u, h - 2 * u, WOOD);
        rect(g, x + 2 * u, y + u, w - 4 * u, u, WOOD_LIGHT);
        rect(g, x + u, y + 2 * u, u, h - 4 * u, WOOD_LIGHT);
        rect(g, x + 2 * u, y + h - 2 * u, w - 4 * u, u, WOOD_DARK);
        rect(g, x + w - 2 * u, y + 2 * u, u, h - 4 * u, WOOD_DARK);
        // corner rivets
        rect(g, x + u, y + u, u, u, RIVET);
        rect(g, x + w - 2 * u, y + u, u, u, RIVET);
        rect(g, x + u, y + h - 2 * u, u, u, RIVET);
        rect(g, x + w - 2 * u, y + h - 2 * u, u, u, RIVET);
        // inner groove and parchment
        rect(g, x + 2 * u, y + 2 * u, w - 4 * u, h - 4 * u, WOOD_INNER);
        rect(g, x + 3 * u, y + 3 * u, w - 6 * u, h - 6 * u, PARCHMENT);
        rect(g, x + 3 * u, y + 3 * u, w - 6 * u, u, PARCHMENT_SHADE);
        rect(g, x + 3 * u, y + 3 * u, u, h - 6 * u, PARCHMENT_SHADE);
    }

    // darkens a bright UI colour (meant for dark backgrounds) so it reads on parchment
    public static Color forParchment(Color c) {
        return mix(c, new Color(40, 20, 10), 0.45);
    }
}
