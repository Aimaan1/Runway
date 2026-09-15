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
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

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
        g2.setColor(Color.WHITE);
        g2.drawString(gateID, x + 14, y + height - 16);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint != null ? oldHint : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }
}
