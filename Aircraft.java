import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public abstract class Aircraft extends Moveable implements drawable, Status {
    private final String aircraftID;
    private String operator;
    private String model;
    private double fuelLevel;
    private final int capacity;
    private String status;
    private boolean flying = false;
    private int countdown = 0;
    private int framesToConsumeFuel = 200;
    private int MaxframesToConsumeFuel = framesToConsumeFuel;
    private AirwayGate gateAssigned = null;
    private Color color = null; // lets each aircraft be told apart visually; null = subclass picks its own default


    public Aircraft(String aircraftID, String operator, String model, double fuelLevel, int capacity, String status) {
        
        this.aircraftID = aircraftID;
        setOperator(operator);
        setModel(model);
        setFuelLevel(fuelLevel);
        this.capacity = capacity;
        this.status = status;
    }

    /**
     * Bounded generic range check shared by the aircraft setters.
     * Throws InvalidAircraftParameterException when value is outside [min, max].
     */
    protected static <T extends Comparable<T>> void requireInRange(T value, T min, T max, String field) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new InvalidAircraftParameterException(
                field + " must be between " + min + " and " + max + " (was " + value + ")");
        }
    }

    //setters 
    public void setOperator(String operator) {
        this.operator = operator;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setFuelLevel(double fuelLevel) {
        if (fuelLevel < 0) {
            throw new InvalidAircraftParameterException("Fuel level cannot be negative (was " + fuelLevel + ")");
        }
        this.fuelLevel = fuelLevel;
    }

    public void setStatus(String newStatus) {
        status = newStatus;
    }

    public void setFlying(boolean value) {
        flying = value;
    }

    public void setCountdown (int value) {
        countdown = value;
    }

    public void setGate(AirwayGate gateselected) {
        gateAssigned = gateselected;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    //getters
    public String getAircraftID() {
        return aircraftID;
    }

    public String getOperator() {
        return operator;
    }

    public String getModel() {
        return model;
    }

    public double getFuelLevel() {
        return fuelLevel;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean getFlying() {
        return flying;
    }

    public AirwayGate getAssignedGate() {
        return gateAssigned;
    }

    public Color getColor() {
        return color;
    }

    public boolean CooldownOver() {
        if (countdown <= 0) {
            return true;
        } else {
            return false;
        }
    }

    public String getStatus() {
        return status;
    }

    public void displayInfo() {
        System.out.println("Aircraft ID: " + aircraftID);
        System.out.println("Operator: " + operator);
        System.out.println("Model: " + model);
        System.out.println("Fuel Level: " + fuelLevel);
        System.out.println("Capacity: " + capacity);
        System.out.println("Status: " + getStatus());
    }

    public boolean canFly() {
        if (getReachedTarget() == true && getPosition().compareVectors(getFlightPath().get(getFlightPath().size()-1).getPosition()) == true) {
            Node lastNodeRef = getFlightPath().get(getFlightPath().size()-1);
            if (lastNodeRef.getNodeTileRepresentation().equalsIgnoreCase("RUNWAY")) {
                setStatus("Flying");
                return true;
            }
        }
        return false;
    }

    // used as a countdown before a plane leaves gate or flies off airfield
    public void decreaseCountdown() {
        if (countdown >= 0 && isAtGate() || countdown > 0 && getChosenToFly()) {
            countdown--;
        }
    }

    // allows plane to stay in on airfield for a second before flying away
    public void warmUpEngines() {
        if (countdown <= 0 && canFly()) {
            countdown = 200;
            System.out.println("Preparing for takeoff");
        }
    }

    // ------------------------------------------------------------------ pixel-art sprite

    // plane seen from above, nose pointing up. only the fill is described here:
    //  b = body, t = livery stripe, w = cockpit glass, e = engine.
    // the outline and the top-left lighting are worked out automatically after rotating,
    // so the sprite is always lit from the same side whichever way it faces
    private static final String[] PLANE_SPRITE = {
        ".......b.......",
        "......bwb......",
        "......bwb......",
        "......btb......",
        "......btb......",
        ".....bbtbb.....",
        "....bbbtbbb....",
        "..bbbbbtbbbbb..",
        "bbbbbbbtbbbbbbb",
        "bb.e..btb..e.bb",
        "......btb......",
        "......btb......",
        ".....bbtbb.....",
        "...bbbbtbbbb...",
        ".......t......."
    };

    private BufferedImage[] spriteCache;   // one per facing: 0 up, 1 right, 2 down, 3 left
    private BufferedImage[] shadowCache;
    private Color spriteCacheColor;
    private int facing = 0;

    // colour used when no colour has been set; subclasses pick their own
    protected Color defaultColor() {
        return Color.RED;
    }

    // draws the plane as a pixel sprite (snapped to the 4 directions) with a drop shadow that
    // sits further away while the plane is in the air. coordinates are art pixels
    public void drawPixelSprite(Graphics2D art) {
        Color base = getColor() != null ? getColor() : defaultColor();
        if (spriteCache == null || !base.equals(spriteCacheColor)) {
            buildSprites(base);
        }
        Vector2 target = getTarget();
        if (target != null) {
            int dx = target.getXPos() - getXPos();
            int dy = target.getYPos() - getYPos();
            if (dx != 0 || dy != 0) {
                facing = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0);
            }
        }
        int s = PixelArt.SCALE;
        int size = spriteCache[facing].getWidth();
        int x = getXPos() / s - size / 2;
        int y = getYPos() / s - size / 2;
        int shadowOffset = getFlying() ? 7 : 2;
        art.drawImage(shadowCache[facing], x + shadowOffset / 2, y + shadowOffset, null);
        art.drawImage(spriteCache[facing], x, y, null);
    }

    private void buildSprites(Color raw) {
        // soften pure screen colours (cyan, magenta...) into a warmer painted palette
        Color body = PixelArt.mix(raw, new Color(246, 226, 190), 0.22);
        Color bodyLight = PixelArt.mix(body, Color.WHITE, 0.38);
        Color bodyDark = PixelArt.shade(body, 0.7);
        Color outline = PixelArt.mix(PixelArt.shade(body, 0.3), PixelArt.OUTLINE, 0.6);
        Color stripe = new Color(248, 240, 220);
        Color stripeDark = new Color(212, 198, 176);
        Color glass = new Color(62, 98, 140);
        Color glassLight = new Color(160, 210, 236);
        Color engine = new Color(86, 84, 98);
        Color engineDark = new Color(58, 56, 68);

        spriteCache = new BufferedImage[4];
        shadowCache = new BufferedImage[4];
        spriteCacheColor = raw;

        int n = PLANE_SPRITE.length;
        char[][] grid = new char[n][n];
        for (int r = 0; r < n; r++) {
            grid[r] = PLANE_SPRITE[r].toCharArray();
        }
        for (int dir = 0; dir < 4; dir++) {
            int size = n + 2; // room for the outline
            BufferedImage sprite = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            BufferedImage shadow = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            int shadowRgb = new Color(24, 40, 16, 80).getRGB();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    char c = cell(grid, x - 1, y - 1);
                    Color colour = null;
                    if (c == '.') {
                        if (cell(grid, x - 2, y - 1) != '.' || cell(grid, x, y - 1) != '.'
                                || cell(grid, x - 1, y - 2) != '.' || cell(grid, x - 1, y) != '.') {
                            colour = outline;
                        }
                    } else {
                        boolean lit = cell(grid, x - 1, y - 2) == '.' || cell(grid, x - 2, y - 1) == '.';
                        boolean shaded = cell(grid, x - 1, y) == '.' || cell(grid, x, y - 1) == '.';
                        switch (c) {
                            case 't': colour = shaded && !lit ? stripeDark : stripe; break;
                            case 'w': colour = lit ? glassLight : glass; break;
                            case 'e': colour = shaded ? engineDark : engine; break;
                            default: colour = lit ? bodyLight : shaded ? bodyDark : body; break;
                        }
                    }
                    if (colour != null) {
                        sprite.setRGB(x, y, colour.getRGB());
                        shadow.setRGB(x, y, shadowRgb);
                    }
                }
            }
            spriteCache[dir] = sprite;
            shadowCache[dir] = shadow;
            grid = rotateClockwise(grid);
        }
    }

    private static char cell(char[][] grid, int x, int y) {
        if (y < 0 || y >= grid.length || x < 0 || x >= grid[y].length) {
            return '.';
        }
        return grid[y][x];
    }

    private static char[][] rotateClockwise(char[][] grid) {
        int n = grid.length;
        char[][] rotated = new char[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                rotated[r][c] = grid[n - 1 - c][r];
            }
        }
        return rotated;
    }

    // split drawing so the panel can render the plane into the pixel-art layer and the
    // info text sharp on top of it; visualRepresentation still draws both
    public abstract void drawSprite(Graphics drawer, int width, int height);
    public abstract void drawInfo(Graphics drawer, int width, int height);

    // draws an airplane silhouette (nose, swept wings, tail wings) centred on this aircraft's
    // position and rotated to face its current heading; shared by both aircraft types.
    // shape points are in local space with the nose at "up" (0, -height/2), then the whole
    // thing is translated/rotated onto the canvas via an AffineTransform
    protected void drawPlaneShape(Graphics drawer, int width, int height, Color fillColor) {
        int[] xPoints = {
            0,
            scale(width, 0.08), scale(width, 0.5), scale(width, 0.12), scale(width, 0.28), scale(width, 0.06),
            0,
            -scale(width, 0.06), -scale(width, 0.28), -scale(width, 0.12), -scale(width, 0.5), -scale(width, 0.08)
        };
        int[] yPoints = {
            -scale(height, 0.5),
            -scale(height, 0.15), scale(height, 0.05), scale(height, 0.15), scale(height, 0.4), scale(height, 0.3),
            scale(height, 0.5),
            scale(height, 0.3), scale(height, 0.4), scale(height, 0.15), scale(height, 0.05), -scale(height, 0.15)
        };

        Graphics2D canvas = (Graphics2D) drawer;
        AffineTransform originalTransform = canvas.getTransform();

        canvas.translate(getXPos(), getYPos());
        // the shape's nose rests pointing "up" (angle -90deg), so rotate the extra amount
        // needed to bring it to the heading angle
        canvas.rotate(computeHeadingRadians() + Math.PI / 2);

        canvas.setColor(fillColor);
        canvas.fillPolygon(xPoints, yPoints, xPoints.length);

        // livery stripe running the length of the fuselage
        canvas.setColor(fillColor.darker());
        int stripeWidth = Math.max(2, scale(width, 0.05));
        canvas.fillRect(-stripeWidth / 2, -scale(height, 0.5), stripeWidth, height);

        // engine nacelles slung under each wing
        canvas.setColor(new Color(60, 60, 65));
        int engineWidth = Math.max(3, scale(width, 0.08));
        int engineHeight = Math.max(4, scale(height, 0.12));
        canvas.fillOval(scale(width, 0.22) - engineWidth / 2, scale(height, 0.05), engineWidth, engineHeight);
        canvas.fillOval(-scale(width, 0.22) - engineWidth / 2, scale(height, 0.05), engineWidth, engineHeight);

        // cockpit windshield near the nose
        canvas.setColor(new Color(35, 45, 60));
        int cockpitWidth = Math.max(3, scale(width, 0.1));
        int cockpitHeight = Math.max(4, scale(height, 0.12));
        canvas.fillOval(-cockpitWidth / 2, -scale(height, 0.5) + scale(height, 0.04), cockpitWidth, cockpitHeight);

        // tail fin accent
        canvas.setColor(fillColor.brighter());
        int finWidth = Math.max(2, scale(width, 0.05));
        canvas.fillRect(-finWidth / 2, scale(height, 0.3), finWidth, scale(height, 0.2));

        canvas.setColor(Color.DARK_GRAY);
        canvas.drawPolygon(xPoints, yPoints, xPoints.length);

        canvas.setTransform(originalTransform);
    }

    // draws each line of text inside a retro RPG-style dialogue box (double border, dark
    // fill, monospaced font) instead of bare strings floating on the background;
    // (anchorX, anchorY) is the box's left-edge, vertical-centre point, so the box sits
    // beside whatever is at that height rather than on top of it
    protected void drawTextWindow(Graphics drawer, int anchorX, int anchorY, String[] lines) {
        if (PixelArt.isEnabled()) {
            // wooden frame + parchment dialogue box for the pixel-art look
            Font font = PixelArt.font(11);
            drawer.setFont(font);
            FontMetrics fm = drawer.getFontMetrics(font);
            int lineHeight = 13;
            int textWidth = 0;
            for (String line : lines) {
                textWidth = Math.max(textWidth, fm.stringWidth(line));
            }
            int inset = PixelArt.BOX_INSET;
            int boxWidth = textWidth + inset * 2;
            int boxHeight = lines.length * lineHeight + inset * 2 - 2;
            int boxY = anchorY - boxHeight / 2;
            PixelArt.drawWoodenBox(drawer, anchorX, boxY, boxWidth, boxHeight);
            for (int i = 0; i < lines.length; i++) {
                drawer.setColor(i == 0 ? PixelArt.INK_ACCENT : PixelArt.INK);
                drawer.drawString(lines[i], anchorX + inset, boxY + inset + lineHeight * (i + 1) - 4);
            }
            return;
        }
        Font retroFont = new Font("Monospaced", Font.PLAIN, 11);
        drawer.setFont(retroFont);
        FontMetrics metrics = drawer.getFontMetrics(retroFont);

        int lineHeight = 13;
        int padding = 5;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, metrics.stringWidth(line));
        }
        int boxWidth = textWidth + padding * 2;
        int boxHeight = lines.length * lineHeight + padding * 2;
        int x = anchorX;
        int y = anchorY - boxHeight / 2;

        // outer border
        drawer.setColor(Color.WHITE);
        drawer.fillRect(x, y, boxWidth, boxHeight);
        // dark inset fill, classic RPG textbox navy
        drawer.setColor(new Color(8, 8, 44));
        drawer.fillRect(x + 3, y + 3, boxWidth - 6, boxHeight - 6);
        // secondary inner border line for the double-lined look
        drawer.setColor(new Color(90, 100, 210));
        drawer.drawRect(x + 5, y + 5, boxWidth - 11, boxHeight - 11);

        drawer.setColor(Color.WHITE);
        for (int i = 0; i < lines.length; i++) {
            drawer.drawString(lines[i], x + padding + 3, y + padding + lineHeight * (i + 1) - 3);
        }
    }

    // angle (radians) from this aircraft's current position to its target, in screen-coordinate
    // atan2 terms; falls back to pointing "up" when there's no meaningful direction yet
    private double computeHeadingRadians() {
        Vector2 target = getTarget();
        if (target == null) {
            return -Math.PI / 2;
        }
        int dx = target.getXPos() - getXPos();
        int dy = target.getYPos() - getYPos();
        if (dx == 0 && dy == 0) {
            return -Math.PI / 2;
        }
        return Math.atan2(dy, dx);
    }

    private int scale(int dimension, double fraction) {
        return (int) Math.round(dimension * fraction);
    }

    //Consumes fuel
    public void influenceFuel() {
        if (!getStatus().equalsIgnoreCase("DOCKED")) {
            if (framesToConsumeFuel <= 0) {
                fuelLevel--;
                framesToConsumeFuel = MaxframesToConsumeFuel;
            } else {
                framesToConsumeFuel--;
            }
        } else if (getStatus().equalsIgnoreCase("DOCKED")) {
            if (framesToConsumeFuel <= MaxframesToConsumeFuel-10) {
                fuelLevel++;
                framesToConsumeFuel = MaxframesToConsumeFuel;
            } else {
                framesToConsumeFuel--;
            }
        }
    }
}
