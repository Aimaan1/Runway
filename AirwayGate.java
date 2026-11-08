import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.util.ArrayList;

public class AirwayGate implements drawable {

    private final String gateID;
    private boolean status;
    private Aircraft currentPlane;
    private Node gateNode;

    public AirwayGate(String gateID, boolean status, Node gateNode) {
        this.gateID = gateID;
        this.status = status;
        this.gateNode = gateNode;
        this.currentPlane = null;
    }

    public void parkPlane(Aircraft plane) throws OccupancyException {

        if (!status) {
            throw new OccupancyException(
                    "Gate " + gateID + " is closed."
            );
        }

        if (!isFree()) {
            throw new OccupancyException(
                    "Gate " + gateID + " is already occupied."
            );
        }

        currentPlane = plane;
        gateNode.setOccupied(true);
    }

    public Aircraft removePlane() {
        Aircraft departingPlane = currentPlane;

        currentPlane = null;
        gateNode.setOccupied(false);

        return departingPlane;
    }

    public boolean isFree() {
        return status && currentPlane == null;
    }

    public String getGateID() {
        return gateID;
    }

    public boolean getStatus() {
        return status;
    }

    public Aircraft getCurrentPlane() {
        return currentPlane;
    }

    public Node getGateNode() {
        return gateNode;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public void displayInfo() {
        System.out.println("Gate ID: " + gateID);
        System.out.println("Status: " + (status ? "Open" : "Closed"));
        System.out.println("Node: " + gateNode.getNodeID());

        if (currentPlane != null) {
            System.out.println("Plane at gate: " + currentPlane.getAircraftID());
        } else {
            System.out.println("No plane currently at the gate.");
        }
    }

    //below is Tim's silly works
    public void departingPlane(AirTrafficControl airController, String locationNode) {
        // if plane is docked and timer has reached 0
        if (currentPlane != null && currentPlane.getStatus().equalsIgnoreCase("DOCKED") && currentPlane.CooldownOver() && locationNode != null && !locationNode.isEmpty()) {
            try {
                gateNode.isOccupied = false;
                ArrayList<Node> path = airController.calculateRoute(locationNode, gateNode);
                if (path == null && currentPlane.CooldownOver()) {
                    currentPlane.setCountdown(100);
                    gateNode.isOccupied = true;
                }
                if (path != null && currentPlane.CooldownOver()) { // ensure path no null, maybe because its blocked, and tries later
                    currentPlane.setFlightPath(path); // creates new path so it doesn't collide with other aircrafts
                    currentPlane.setStatus("GROUNDED");
                    currentPlane.setGate(null);
                    currentPlane.setDocked(false);
                    removePlane(); // removes from gate
                }
            } catch (NullPointerException e) {
                System.out.println("Null pointer exception happened while leaving the gate!:" + e);
            } catch (Exception e) {
                System.out.println("An error occured while leaving gate: " + e);
            }
        }
    }

    // check if reached a gate
    public void PlaneAtGate (Aircraft selectedAircraft) {
        if (selectedAircraft != null && selectedAircraft.isAtLastNode() && !selectedAircraft.getStatus().equalsIgnoreCase("DOCKED") && currentPlane == null) {
                if (selectedAircraft.getPosition().compareVectors(this.getGateNode().getPosition()) && this.getStatus() == true) {
                    try {
                    selectedAircraft.setDocked(true);
                    selectedAircraft.setStatus("DOCKED");
                    int cooldown = (int)(Math.random() * (1000 - 500 + 1)) + 500;
                    selectedAircraft.setCountdown(cooldown); // pretend that people are getting on board + refueling
                    this.parkPlane(selectedAircraft);
                    selectedAircraft.setGate(this);
                    gateNode.isOccupied = true;
                    } catch (OccupancyException e) {
                    System.out.println("Occupancy Error at gate: " + e.getMessage());
                    } catch (Exception e) {
                       System.out.println("Error occured at gate: " + e.getMessage()); 
                }
            }
        } else {
            System.out.println("Aircraft not at this gate!"); 
        }
    }

    // for drawing elements of gate: a small terminal building with a jet
    // bridge reaching up to the stand, a gate ID plate and a status light
    @Override
    public void visualRepresentation(Graphics drawer, int width, int height) {
        Graphics2D g2 = (Graphics2D) drawer;
        Object oldHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, PixelArt.aaHint());

        int x = getGateNode().getXPos() - width/2;
        int y = getGateNode().getYPos();

        // shadow on the apron where the aircraft parks
        g2.setColor(new Color(0, 0, 0, 50));
        g2.fillOval(x + width/2 - 30, y - 8, 60, 14);

        // jet bridge, reaching up from the terminal toward the parked aircraft
        int bridgeWidth = Math.max(14, width / 6);
        int bridgeX = x + width/2 - bridgeWidth/2;
        int bridgeTopY = y - 70;
        g2.setColor(new Color(205, 205, 210));
        g2.fillRect(bridgeX, bridgeTopY, bridgeWidth, y - bridgeTopY);
        g2.setColor(new Color(150, 150, 155));
        for (int seg = bridgeTopY; seg < y; seg += 10) {
            g2.drawLine(bridgeX, seg, bridgeX + bridgeWidth, seg);
        }
        g2.setColor(new Color(180, 180, 185));
        g2.fillOval(bridgeX - 6, bridgeTopY - 12, bridgeWidth + 12, 20);

        // terminal building body, shaded from top to bottom
        GradientPaint wallPaint = new GradientPaint(x, y, new Color(238, 238, 240), x, y + height, new Color(198, 200, 205));
        g2.setPaint(wallPaint);
        g2.fillRoundRect(x, y, width, height, 14, 14);
        g2.setColor(new Color(140, 140, 145));
        g2.drawRoundRect(x, y, width, height, 14, 14);

        // roof lip
        g2.setColor(new Color(90, 95, 100));
        g2.fillRect(x - 4, y - 6, width + 8, 10);

        // row of terminal windows
        g2.setColor(new Color(120, 200, 230));
        int winW = Math.max(10, width / 6);
        int winY = y + 18;
        for (int wx = x + 10; wx + winW < x + width - 10; wx += winW + 8) {
            g2.fillRoundRect(wx, winY, winW, 20, 4, 4);
            g2.setColor(new Color(255, 255, 255, 120));
            g2.drawLine(wx + winW/2, winY, wx + winW/2, winY + 20);
            g2.setColor(new Color(120, 200, 230));
        }

        // status light: green when free, red when occupied, gray when closed
        Color lightColor = !status ? Color.GRAY : (isFree() ? new Color(60, 200, 90) : new Color(220, 60, 60));
        g2.setColor(lightColor);
        g2.fillOval(x + width - 22, y + height - 22, 14, 14);
        g2.setColor(Color.BLACK);
        g2.drawOval(x + width - 22, y + height - 22, 14, 14);

        // gate ID plate
        int plateWidth = Math.max(46, width/2);
        g2.setColor(new Color(30, 30, 30));
        g2.fillRoundRect(x + 8, y + height - 32, plateWidth, 22, 6, 6);
        // in pixel-art mode the text is drawn later at full resolution (see drawLabel) so it stays readable
        if (!PixelArt.isEnabled()) {
            drawLabelText(g2, width, height);
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint != null ? oldHint : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    // ------------------------------------------------------------------ pixel-art version

    // roof colours, picked per gate so the terminal row looks like a little village street
    private static final Color[][] ROOF_PALETTES = {
        {new Color(212, 106, 78), new Color(178, 74, 58), new Color(132, 50, 44)},   // red clay
        {new Color(104, 150, 200), new Color(72, 112, 168), new Color(50, 78, 126)},  // slate blue
        {new Color(122, 176, 92), new Color(86, 140, 66), new Color(58, 100, 50)},    // moss green
        {new Color(186, 122, 196), new Color(146, 86, 160), new Color(104, 58, 118)}  // plum
    };
    private static final Color PLASTER = new Color(242, 224, 184);
    private static final Color PLASTER_SPECK = new Color(226, 204, 162);
    private static final Color TIMBER = new Color(138, 88, 50);
    private static final Color TIMBER_LIGHT = new Color(170, 114, 66);
    private static final Color STONE = new Color(158, 150, 140);
    private static final Color STONE_LIGHT = new Color(186, 178, 166);
    private static final Color MORTAR = new Color(116, 108, 100);
    private static final Color GLASS = new Color(122, 188, 216);
    private static final Color GLASS_SHINE = new Color(222, 242, 250);
    private static final Color SIGN = new Color(192, 132, 72);
    private static final Color SIGN_LIGHT = new Color(220, 162, 96);
    private static final Color SIGN_DARK = new Color(148, 96, 50);

    // sign board position in art pixels: {x, y, w, h}
    private int[] signRect(int width, int height) {
        int s = PixelArt.SCALE;
        int bx = (getGateNode().getXPos() - width / 2) / s;
        int bw = width / s;
        int wallTop = getGateNode().getYPos() / s + 13;
        int sw = Math.min(bw - 8, 24);
        return new int[]{bx + (bw - sw) / 2, wallTop + 2, sw, 7};
    }

    // a cosy timber-framed terminal: shingled roof, plaster walls, flower boxes, a wooden
    // sign and a lantern beside the door that shows the gate status (green free, red busy)
    public void drawPixelArt(Graphics2D art, int width, int height, long frame) {
        int s = PixelArt.SCALE;
        Color out = PixelArt.OUTLINE;
        int bx = (getGateNode().getXPos() - width / 2) / s;
        int by = getGateNode().getYPos() / s;
        int bw = width / s;
        int bh = height / s;
        Color[] roof = ROOF_PALETTES[Math.abs(gateID.hashCode()) % ROOF_PALETTES.length];

        // soft shadow cast down-right onto the grass
        PixelArt.rect(art, bx + 3, by + 2, bw, bh, new Color(20, 40, 10, 70));

        // covered jet bridge reaching up toward the parked plane
        int bridgeW = 5;
        int bridgeX = bx + bw / 2 - bridgeW / 2;
        int bridgeTop = by - 70 / s;
        PixelArt.rect(art, bridgeX - 1, bridgeTop, bridgeW + 2, by - bridgeTop, out);
        PixelArt.rect(art, bridgeX, bridgeTop, bridgeW, by - bridgeTop, new Color(180, 184, 198));
        PixelArt.rect(art, bridgeX, bridgeTop, 1, by - bridgeTop, new Color(210, 214, 226));
        PixelArt.rect(art, bridgeX + bridgeW - 1, bridgeTop, 1, by - bridgeTop, new Color(140, 144, 162));
        for (int y = bridgeTop + 4; y < by; y += 4) {
            PixelArt.rect(art, bridgeX, y, bridgeW, 1, new Color(122, 126, 146));
            PixelArt.px(art, bridgeX + 2, y - 2, GLASS);
        }
        PixelArt.rect(art, bridgeX - 3, bridgeTop - 3, bridgeW + 6, 5, out);
        PixelArt.rect(art, bridgeX - 2, bridgeTop - 2, bridgeW + 4, 3, new Color(196, 200, 212));
        PixelArt.rect(art, bridgeX - 2, bridgeTop - 2, bridgeW + 4, 1, new Color(228, 232, 240));

        // walls: outline, plaster with speckles, timber frame
        int wallTop = by + 13;
        int footY = by + bh - 4;
        PixelArt.rect(art, bx, wallTop, bw, by + bh - wallTop, out);
        PixelArt.rect(art, bx + 1, wallTop, bw - 2, footY - wallTop, PLASTER);
        for (int y = wallTop; y < footY; y++) {
            for (int x = bx + 1; x < bx + bw - 1; x++) {
                if (PixelArt.hash(x, y, 40) < 0.06) {
                    PixelArt.px(art, x, y, PLASTER_SPECK);
                }
            }
        }
        PixelArt.rect(art, bx + 1, wallTop, 2, footY - wallTop, TIMBER);
        PixelArt.rect(art, bx + 1, wallTop, 1, footY - wallTop, TIMBER_LIGHT);
        PixelArt.rect(art, bx + bw - 3, wallTop, 2, footY - wallTop, TIMBER);
        PixelArt.rect(art, bx + 1, wallTop + 1, bw - 2, 1, PixelArt.withAlpha(TIMBER, 120)); // eave shadow
        int beamY = wallTop + 21;
        PixelArt.rect(art, bx + 1, beamY, bw - 2, 2, TIMBER);
        PixelArt.rect(art, bx + 1, beamY, bw - 2, 1, TIMBER_LIGHT);

        // stone foundation
        PixelArt.rect(art, bx + 1, footY, bw - 2, 3, STONE);
        PixelArt.rect(art, bx + 1, footY, bw - 2, 1, STONE_LIGHT);
        for (int x = bx + 2; x < bx + bw - 1; x += 4) {
            PixelArt.px(art, x, footY + 1, MORTAR);
            PixelArt.px(art, x + 2, footY + 2, MORTAR);
        }

        // sign board (text is drawn later, sharp, by drawLabel)
        int[] sign = signRect(width, height);
        PixelArt.rect(art, sign[0] - 1, sign[1] - 1, sign[2] + 2, sign[3] + 2, out);
        PixelArt.rect(art, sign[0], sign[1], sign[2], sign[3], SIGN);
        PixelArt.rect(art, sign[0], sign[1], sign[2], 1, SIGN_LIGHT);
        PixelArt.rect(art, sign[0], sign[1] + sign[3] - 1, sign[2], 1, SIGN_DARK);

        // two rows of windows with flower boxes
        int[] windowXs = {bx + 5, bx + bw - 12};
        int[] windowYs = {wallTop + 12, wallTop + 25};
        for (int wy : windowYs) {
            for (int wx : windowXs) {
                drawWindow(art, wx, wy, out);
            }
        }

        // front door with planks, a brass knob and a stone step
        int doorW = 7;
        int doorH = 11;
        int doorX = bx + bw / 2 - doorW / 2;
        int doorY = footY - doorH + 1;
        PixelArt.rect(art, doorX - 1, doorY - 1, doorW + 2, doorH + 1, out);
        PixelArt.rect(art, doorX, doorY, doorW, doorH, new Color(152, 96, 54));
        PixelArt.rect(art, doorX + 2, doorY + 1, 1, doorH - 1, new Color(122, 74, 40));
        PixelArt.rect(art, doorX + 4, doorY + 1, 1, doorH - 1, new Color(122, 74, 40));
        PixelArt.rect(art, doorX, doorY, doorW, 1, new Color(178, 118, 70));
        PixelArt.px(art, doorX, doorY, out);
        PixelArt.px(art, doorX + doorW - 1, doorY, out);
        PixelArt.px(art, doorX + 5, doorY + 6, new Color(244, 204, 92));
        PixelArt.rect(art, doorX - 1, footY + 1, doorW + 2, 2, STONE_LIGHT);

        // status lantern beside the door
        Color lamp = !status ? new Color(130, 126, 120) : (isFree() ? new Color(132, 236, 112) : new Color(244, 96, 72));
        int lx = doorX + doorW + 2;
        int ly = doorY + 2;
        if (status) {
            double pulse = (Math.sin(frame * 0.06) + 1) / 2;
            art.setColor(PixelArt.withAlpha(lamp, (int) (50 + pulse * 50)));
            art.fillRect(lx - 1, ly - 1, 5, 6);
        }
        PixelArt.px(art, lx + 1, ly - 1, out);
        PixelArt.rect(art, lx, ly, 3, 4, out);
        PixelArt.rect(art, lx + 1, ly + 1, 1, 2, lamp);

        // shingled roof with a slight overhang, drawn last so it sits over the wall tops
        int rx = bx - 2;
        int rw = bw + 4;
        int ry = by - 2;
        int rh = 15;
        PixelArt.rect(art, rx, ry, rw, rh, out);
        for (int y = ry + 1; y < ry + rh - 1; y++) {
            int band = (y - ry - 1) / 3;
            int inBand = (y - ry - 1) % 3;
            Color c = inBand == 0 ? roof[0] : inBand == 1 ? roof[1] : roof[2];
            PixelArt.rect(art, rx + 1, y, rw - 2, 1, c);
            if (inBand == 1) {
                for (int x = rx + 2 + (band % 2) * 3; x < rx + rw - 1; x += 6) {
                    PixelArt.px(art, x, y, roof[2]); // gaps between shingles, staggered per row
                }
            }
        }
        PixelArt.rect(art, rx + 1, ry + 1, rw - 2, 1, PixelArt.mix(roof[0], Color.WHITE, 0.25)); // ridge highlight
    }

    private void drawWindow(Graphics2D art, int wx, int wy, Color out) {
        PixelArt.rect(art, wx - 1, wy - 1, 9, 8, out);
        PixelArt.rect(art, wx, wy, 7, 6, GLASS);
        PixelArt.rect(art, wx + 3, wy, 1, 6, TIMBER);
        PixelArt.rect(art, wx, wy + 2, 7, 1, TIMBER);
        PixelArt.px(art, wx + 1, wy, GLASS_SHINE);
        PixelArt.px(art, wx, wy + 1, GLASS_SHINE);
        PixelArt.px(art, wx + 4, wy + 3, GLASS_SHINE);
        // flower box below
        PixelArt.rect(art, wx - 1, wy + 7, 9, 2, TIMBER);
        PixelArt.rect(art, wx - 1, wy + 8, 9, 1, PixelArt.shade(TIMBER, 0.75));
        Color[] blooms = {new Color(236, 92, 104), new Color(250, 222, 92), new Color(246, 244, 232)};
        for (int i = 0; i < 4; i++) {
            PixelArt.px(art, wx + i * 2, wy + 6, blooms[(i + wx) % blooms.length]);
            PixelArt.px(art, wx + i * 2 + 1, wy + 6, new Color(84, 150, 62));
        }
    }

    // draws the gate ID onto its sign at full resolution, on top of the pixelated scene
    public void drawLabel(Graphics drawer, int width, int height) {
        if (!PixelArt.isEnabled()) {
            return;
        }
        int s = PixelArt.SCALE;
        int[] sign = signRect(width, height);
        java.awt.Font font = PixelArt.font(11);
        drawer.setFont(font);
        java.awt.FontMetrics metrics = drawer.getFontMetrics(font);
        int textX = sign[0] * s + (sign[2] * s - metrics.stringWidth(gateID)) / 2;
        int textY = sign[1] * s + (sign[3] * s + metrics.getAscent() - metrics.getDescent()) / 2;
        drawer.setColor(PixelArt.INK);
        drawer.drawString(gateID, textX, textY);
    }

    private void drawLabelText(Graphics drawer, int width, int height) {
        int x = getGateNode().getXPos() - width/2;
        int y = getGateNode().getYPos();
        drawer.setColor(Color.WHITE);
        drawer.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));
        drawer.drawString(gateID, x + 14, y + height - 16);
    }
}
