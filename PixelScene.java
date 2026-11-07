import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

// paints the airport grounds in the cozy pixel-art style, in art-pixel coordinates
// (1 art pixel = PixelArt.SCALE screen pixels). the ground (grass, asphalt, paint markings)
// never changes, so it is painted once into a cached image; water and lights animate per frame
public class PixelScene {
    private static final int S = PixelArt.SCALE;

    // grass: three tones for soft mottled patches, plus tuft and rim colours
    private static final Color GRASS_DARK = new Color(72, 134, 50);
    private static final Color GRASS_MID = new Color(96, 160, 58);
    private static final Color GRASS_LIGHT = new Color(124, 182, 68);
    private static final Color GRASS_TUFT = new Color(52, 108, 44);
    private static final Color GRASS_TIP = new Color(156, 204, 86);
    private static final Color GRASS_RIM = new Color(58, 112, 44);

    // asphalt: a warm purple-grey, so it sits nicely next to the greens
    private static final Color ASPHALT_DARK = new Color(100, 96, 110);
    private static final Color ASPHALT_MID = new Color(108, 104, 118);
    private static final Color ASPHALT_LIGHT = new Color(115, 111, 125);
    private static final Color ASPHALT_SPECK = new Color(138, 134, 146);
    private static final Color ASPHALT_PIT = new Color(84, 80, 94);
    private static final Color ASPHALT_EDGE = new Color(66, 58, 70);
    private static final Color ASPHALT_SHADOW = new Color(90, 86, 100);
    private static final Color CRACK = new Color(78, 74, 88);

    private static final Color PAINT = new Color(240, 232, 206);
    private static final Color PAINT_WORN = new Color(206, 198, 180);
    private static final Color YELLOW = new Color(238, 188, 66);
    private static final Color YELLOW_WORN = new Color(200, 158, 70);

    private static final Color[] PETALS = {
        new Color(250, 248, 236), new Color(244, 146, 166), new Color(150, 176, 244), new Color(250, 206, 96)
    };
    private static final Color FLOWER_CENTRE = new Color(250, 214, 80);

    // water
    private static final Color DIRT_TOP = new Color(150, 106, 62);
    private static final Color DIRT_BOTTOM = new Color(112, 76, 44);
    private static final Color WATER = new Color(60, 128, 198);
    private static final Color WATER_DEEP = new Color(52, 114, 184);
    private static final Color WATER_SHADOW = new Color(44, 96, 164);
    private static final Color WAVE = new Color(116, 186, 232);
    private static final Color SPARKLE = new Color(236, 248, 255);
    private static final Color FOAM = new Color(206, 234, 248);

    private BufferedImage groundCache;
    private int cacheFrameWidth = -1;
    private int cacheFrameHeight = -1;

    // ------------------------------------------------------------------ ground (cached)

    // draws grass + asphalt + markings; frameWidth/frameHeight are the screen-space layout size
    public void drawGround(Graphics2D art, int artWidth, int artHeight, int frameWidth, int frameHeight) {
        if (groundCache == null || groundCache.getWidth() != artWidth || groundCache.getHeight() != artHeight
                || cacheFrameWidth != frameWidth || cacheFrameHeight != frameHeight) {
            groundCache = buildGround(artWidth, artHeight, frameWidth, frameHeight);
            cacheFrameWidth = frameWidth;
            cacheFrameHeight = frameHeight;
        }
        art.drawImage(groundCache, 0, 0, null);
    }

    private BufferedImage buildGround(int aw, int ah, int fw, int fh) {
        BufferedImage image = new BufferedImage(aw, ah, BufferedImage.TYPE_INT_RGB);

        // which art pixels are paved, using the same screen-space layout as the original drawing
        boolean[] paved = new boolean[aw * ah];
        markPaved(paved, aw, ah, 0, 0, fw, 150);                       // runway
        for (int i = 0; i < 8; i += 2) {                                 // connector roads
            markPaved(paved, aw, ah, fw / 7 * i, 150, fw / 7 - 10, 150);
        }
        markPaved(paved, aw, ah, 0, 300, fw, 150);                     // taxiway
        for (int i = 0; i < 5; i += 2) {                                 // roads to the gates
            markPaved(paved, aw, ah, fw / 5 * i, 450, fw / 5, 150);
        }

        // base texture, pixel by pixel
        for (int y = 0; y < ah; y++) {
            for (int x = 0; x < aw; x++) {
                Color c;
                if (isPaved(paved, aw, ah, x, y)) {
                    c = asphaltPixel(paved, aw, ah, x, y);
                } else {
                    c = grassPixel(paved, aw, ah, x, y);
                }
                image.setRGB(x, y, c.getRGB());
            }
        }

        Graphics2D g = image.createGraphics();
        PixelArt.makeCrisp(g);

        // details scattered on top: grass tufts, flowers and pebbles, and cracks in the asphalt
        for (int y = 1; y < ah - 2; y++) {
            for (int x = 1; x < aw - 4; x++) {
                boolean grassHere = !isPaved(paved, aw, ah, x, y) && !isPaved(paved, aw, ah, x + 3, y + 1)
                        && !isPaved(paved, aw, ah, x, y - 1) && !isPaved(paved, aw, ah, x + 3, y - 1);
                if (grassHere) {
                    double r = PixelArt.hash(x, y, 5);
                    if (r < 0.022) {
                        drawTuft(g, x, y);
                    } else if (r < 0.0255) {
                        drawFlower(g, x + 1, y, PETALS[(int) (PixelArt.hash(x, y, 6) * PETALS.length)]);
                    } else if (r < 0.0268) {
                        drawPebble(g, x, y);
                    }
                } else if (isPaved(paved, aw, ah, x, y) && isPaved(paved, aw, ah, x + 4, y + 3)
                        && PixelArt.hash(x, y, 11) < 0.0035) {
                    drawCrack(g, x, y);
                }
            }
        }

        drawMarkings(g, fw);
        g.dispose();
        return image;
    }

    private void markPaved(boolean[] paved, int aw, int ah, int sx, int sy, int sw, int sh) {
        int x0 = Math.max(0, sx / S);
        int y0 = Math.max(0, sy / S);
        int x1 = Math.min(aw, (sx + sw) / S);
        int y1 = Math.min(ah, (sy + sh) / S);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                paved[y * aw + x] = true;
            }
        }
    }

    private boolean isPaved(boolean[] paved, int aw, int ah, int x, int y) {
        if (x < 0 || y < 0 || x >= aw || y >= ah) {
            return false;
        }
        return paved[y * aw + x];
    }

    private Color asphaltPixel(boolean[] paved, int aw, int ah, int x, int y) {
        // hard dark edge where the asphalt meets the grass
        if (!isPaved(paved, aw, ah, x - 1, y) || !isPaved(paved, aw, ah, x + 1, y)
                || !isPaved(paved, aw, ah, x, y - 1) || !isPaved(paved, aw, ah, x, y + 1)) {
            // the outer map border isn't really grass, so don't outline along the screen edge
            boolean screenEdge = x == 0 || y == 0 || x == aw - 1 || y == ah - 1;
            if (!screenEdge) {
                return ASPHALT_EDGE;
            }
        }
        // the grass lip casts a little shadow onto the asphalt just below it
        if (y > 1 && !isPaved(paved, aw, ah, x, y - 2)) {
            return ASPHALT_SHADOW;
        }
        double r = PixelArt.hash(x, y, 1);
        if (r < 0.022) {
            return ASPHALT_SPECK;
        }
        if (r > 0.982) {
            return ASPHALT_PIT;
        }
        double n = PixelArt.noise(x / 9.0, y / 9.0, 2) + (PixelArt.hash(x, y, 3) - 0.5) * 0.08;
        return n < 0.34 ? ASPHALT_DARK : n > 0.68 ? ASPHALT_LIGHT : ASPHALT_MID;
    }

    private Color grassPixel(boolean[] paved, int aw, int ah, int x, int y) {
        if (isPaved(paved, aw, ah, x - 1, y) || isPaved(paved, aw, ah, x + 1, y)
                || isPaved(paved, aw, ah, x, y - 1) || isPaved(paved, aw, ah, x, y + 1)) {
            return GRASS_RIM;
        }
        // big soft patches + finer variation, with a little per-pixel jitter so patch borders
        // break up into hand-placed looking pixels instead of smooth curves
        double n = PixelArt.noise(x / 11.0, y / 11.0, 4) * 0.75 + PixelArt.noise(x / 4.0, y / 4.0, 5) * 0.25
                + (PixelArt.hash(x, y, 6) - 0.5) * 0.14;
        return n < 0.38 ? GRASS_DARK : n > 0.64 ? GRASS_LIGHT : GRASS_MID;
    }

    // small "v v" clump of blades with sunlit tips
    private void drawTuft(Graphics2D g, int x, int y) {
        PixelArt.px(g, x, y, GRASS_TUFT);
        PixelArt.px(g, x + 1, y + 1, GRASS_TUFT);
        PixelArt.px(g, x + 2, y, GRASS_TUFT);
        PixelArt.px(g, x + 3, y + 1, GRASS_TUFT);
        PixelArt.px(g, x, y - 1, GRASS_TIP);
        PixelArt.px(g, x + 2, y - 1, GRASS_TIP);
    }

    // plus-shaped flower with a yellow middle and a stem shadow
    private void drawFlower(Graphics2D g, int x, int y, Color petal) {
        PixelArt.px(g, x, y - 1, petal);
        PixelArt.px(g, x - 1, y, petal);
        PixelArt.px(g, x + 1, y, petal);
        PixelArt.px(g, x, y + 1, PixelArt.shade(petal, 0.82));
        PixelArt.px(g, x, y, FLOWER_CENTRE);
        PixelArt.px(g, x + 1, y + 1, GRASS_TUFT);
    }

    private void drawPebble(Graphics2D g, int x, int y) {
        PixelArt.px(g, x, y, new Color(196, 190, 176));
        PixelArt.px(g, x + 1, y, new Color(168, 160, 148));
        PixelArt.px(g, x, y + 1, new Color(120, 112, 104));
        PixelArt.px(g, x + 1, y + 1, new Color(104, 96, 90));
    }

    private void drawCrack(Graphics2D g, int x, int y) {
        PixelArt.px(g, x, y, CRACK);
        PixelArt.px(g, x + 1, y + 1, CRACK);
        PixelArt.px(g, x + 2, y + 1, CRACK);
        PixelArt.px(g, x + 3, y + 2, CRACK);
        if (PixelArt.hash(x, y, 12) < 0.5) {
            PixelArt.px(g, x + 2, y + 2, CRACK);
        }
    }

    // painted line in art pixels; a few pixels are faded so the paint looks worn
    private void paintLine(Graphics2D g, int x, int y, int w, int h, Color paint, Color worn) {
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                double r = PixelArt.hash(px, py, 13);
                if (r < 0.04) {
                    continue; // paint missing entirely
                }
                PixelArt.px(g, px, py, r < 0.18 ? worn : paint);
            }
        }
    }

    private void drawMarkings(Graphics2D g, int fw) {
        // runway: edge lines, dashed centre line, threshold bars at both ends
        paintLine(g, 0, 10 / S, fw / S, 1, PAINT, PAINT_WORN);
        paintLine(g, 0, 140 / S, fw / S, 1, PAINT, PAINT_WORN);
        for (int i = 120; i < fw - 130; i += 100) {
            paintLine(g, i / S, 75 / S, 50 / S, 1, PAINT, PAINT_WORN);
        }
        for (int i = 10; i < 120; i += 20) {
            paintLine(g, 10 / S, (i + 10) / S, 100 / S, 10 / S, PAINT, PAINT_WORN);
            paintLine(g, (fw - 130) / S, (i + 10) / S, 100 / S, 10 / S, PAINT, PAINT_WORN);
        }
        // connector roads: guide lines
        for (int i = 0; i < 8; i += 2) {
            int x = fw / 7 * i;
            int[] lines = {x + 5, x + fw / 7 - 16, x + fw / 7 / 2};
            for (int lx : lines) {
                paintLine(g, lx / S, 150 / S + 1, 1, 150 / S - 2, YELLOW, YELLOW_WORN);
            }
        }
        // taxiway: dashed centre line and edge lines
        for (int i = 0; i < fw; i += 100) {
            paintLine(g, i / S, 375 / S, 50 / S, 1, YELLOW, YELLOW_WORN);
        }
        paintLine(g, 0, 310 / S, fw / S, 1, YELLOW, YELLOW_WORN);
        paintLine(g, 0, 440 / S, fw / S, 1, YELLOW, YELLOW_WORN);
    }

    // ------------------------------------------------------------------ water (animated)

    // pond along the bottom of the map: dirt bank, shadowed shallows, shimmering wave dashes
    // and sparkles. sparkles are fixed positions (fractions 0..1) whose brightness animates
    public void drawWater(Graphics2D art, int artWidth, int artHeight, int topScreenY, long frame, ArrayList<double[]> sparkles) {
        int top = topScreenY / S;
        if (top >= artHeight) {
            return;
        }
        // grass rim + dirt bank
        PixelArt.rect(art, 0, top - 3, artWidth, 1, GRASS_RIM);
        PixelArt.rect(art, 0, top - 2, artWidth, 1, DIRT_TOP);
        PixelArt.rect(art, 0, top - 1, artWidth, 1, DIRT_BOTTOM);
        for (int x = 0; x < artWidth; x++) {
            if (PixelArt.hash(x, top, 30) < 0.12) {
                PixelArt.px(art, x, top - 2, DIRT_BOTTOM); // little stones in the bank
            }
        }

        // water body with gentle deep patches
        for (int y = top; y < artHeight; y++) {
            for (int x = 0; x < artWidth; x++) {
                Color c;
                if (y < top + 2) {
                    c = WATER_SHADOW; // shade cast by the bank
                } else {
                    double n = PixelArt.noise(x / 14.0, y / 6.0, 31);
                    c = n < 0.42 ? WATER_DEEP : WATER;
                }
                art.setColor(c);
                art.fillRect(x, y, 1, 1);
            }
        }

        // foam lapping at the bank, slowly sliding along
        int slide = (int) (frame / 14);
        for (int x = 0; x < artWidth; x++) {
            if (((x + slide) % 9) < 2) {
                PixelArt.px(art, x, top, FOAM);
            }
        }

        // wave dashes and sparkles that fade in and out in place
        int waterHeight = artHeight - top - 3;
        if (waterHeight <= 0) {
            return;
        }
        for (double[] s : sparkles) {
            int x = (int) (s[0] * artWidth);
            int y = top + 3 + (int) (s[1] * waterHeight);
            double wave = Math.sin(frame * 0.035 + s[2] * Math.PI * 2);
            if (wave > 0.15) {
                int len = 2 + (int) (s[2] * 3);
                PixelArt.rect(art, x, y, len, 1, WAVE);
                if (wave > 0.85) {
                    PixelArt.px(art, x + len / 2, y, SPARKLE);
                }
            }
        }
    }

    // ------------------------------------------------------------------ guide lights (animated)

    // small green ground lamps: a dark housing with a bright core and a soft plus-shaped glow;
    // brightness pulses with a phase offset per node so the flash travels down the path
    public void drawGuideLights(Graphics2D art, ArrayList<Node> nodes, long frame) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            int cx = (node.getXPos() + 5) / S;
            int cy = (node.getYPos() + 5) / S;
            double b = (Math.sin(frame * 0.05 - i * 0.6) + 1) / 2;

            // soft glow
            Color glow = new Color(120, 255, 130, (int) (30 + b * 70));
            art.setColor(glow);
            art.fillRect(cx - 2, cy - 1, 5, 3);
            art.fillRect(cx - 1, cy - 2, 3, 5);
            // housing
            PixelArt.rect(art, cx - 1, cy, 3, 2, PixelArt.OUTLINE);
            // lens
            PixelArt.px(art, cx, cy, PixelArt.mix(new Color(60, 150, 70), new Color(226, 255, 196), b));
            PixelArt.px(art, cx - 1, cy, PixelArt.mix(new Color(40, 100, 50), new Color(120, 230, 120), b));
            PixelArt.px(art, cx + 1, cy, PixelArt.mix(new Color(40, 100, 50), new Color(120, 230, 120), b));
        }
    }
}
