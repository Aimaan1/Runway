import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;

public class AirTrafficControl implements drawable, Position {
    private static ArrayList<Aircraft> aircraftsInAirport;
    private static ArrayList<Node> airportNavigation;
    private Vector2 Location;
    private boolean OccupiedAirfield = false;
    private static Aircraft flyingAircraft = null;

    
    AirTrafficControl(ArrayList<Aircraft> aircraftCount, ArrayList<Node> airportMap, Vector2 buildingLocation) {
        aircraftsInAirport = aircraftCount;
        airportNavigation = airportMap;
        Location = buildingLocation;
    }

    public int getXPos() {
        return Location.xPos;
    }

    public int getYPos() {
        return Location.yPos;
    }

    public void setXPos(int xPos) {
        Location.xPos = xPos;
    }
    
    public void setYPos(int yPos) {

    }

    public Vector2 getPosition() {
        return Location;
    }

    public boolean getOccupiedAirfield() {
        return OccupiedAirfield;
    }

    // creating a flight path for planes
    public ArrayList<Node> calculateRoute(String NodeID, Node StartingNode) {
        ArrayList<Node> path = new ArrayList<>();
        if (NodeID != null && StartingNode != null) {
            try {
                StartingNode.setOccupied(false);
                path = findNode(NodeID, path, StartingNode);
                StartingNode.setOccupied(true);
                path = shortestPathNode(path);
            } catch (NullPointerException e) {
                System.out.println("NULL ERROR IN PATH: " + e);
                return null;
            }
        } else {
            throw new NullPointerException("Route calculations NodeID or startingNode is null or a incorrect data type!");
        }

        return path;
    }

    // find shortest path
    private ArrayList<Node> shortestPathNode(ArrayList<Node> givenArray) {
        ArrayList<Node> copy = new ArrayList<>(givenArray);
        Node lastNode = null; // used to remove anything beyond the last waypoint
        if (givenArray != null && !givenArray.isEmpty() && copy != null) {
            lastNode = givenArray.get(givenArray.size() - 1);
            for (int i = 0; i < givenArray.size(); i++) { // starting node to check if future nodes are neighbors
                int furtherProgression = 0; // used to compare which node is further down
                for (int c = i; c < givenArray.size(); c++) {
                    if (copy.get(i).checkIfNeighboring(givenArray.get(c))) { // detects whether a neighboring node has a vector2
                        //  && copy.get(i).getOccupied() == false || copy.get(i).checkIfNeighboring(givenArray.get(c)) && givenArray.get(i) == givenArray.get(givenArray.size()-1) || copy.get(i).checkIfNeighboring(givenArray.get(c)) && givenArray.get(i).getNodeTileRepresentation().equalsIgnoreCase("RUNWAY")
                        if (furtherProgression <= c && (i+1) < givenArray.size()) {
                            copy.set(i + 1, givenArray.get(c));
                            furtherProgression = c;
                        }
                    }
                    
                }
            }
         } else {
                throw new NullPointerException("The path is null, something broke in the path!");
        }

        boolean reachedEnd = false; // detect if the waypoint has reached last
        for (int i = 0; i < copy.size(); i++) {
            if (copy != null && copy.get(i).equals(lastNode)) {
                reachedEnd = true;
            } else if (reachedEnd == true) {
                copy.remove(i); // removes any waypoints beyond the end waypoint
                i--;
            }
        }
        return copy;
    }

    // used to create a navigational arraylist of points on the airport
        private ArrayList<Node> findNode(String TargetedNode, ArrayList<Node> givenArray, Node startingNode) {
        if (startingNode == null || givenArray.contains(startingNode) || startingNode.isOccupied && !startingNode.getNodeTileRepresentation().equalsIgnoreCase("RUNWAY")) { // ensure that a node can only be gone on once
            return null;
        }

        givenArray.add(startingNode); // adds

        // if correct node
        if (startingNode.getNodeID() != null && startingNode.getNodeID().equals(TargetedNode)) {
            return givenArray;
        }

        ArrayList<Node> finalPath; // used to hold all pathways and check if reached
        
        finalPath = findNode(TargetedNode, givenArray, startingNode.upperNode);
        if (finalPath != null) {
            return finalPath;
        }

        finalPath = findNode(TargetedNode, givenArray, startingNode.bottomNode);
        if (finalPath != null) {
            return finalPath;
        }

        finalPath = findNode(TargetedNode, givenArray, startingNode.leftNode);
        if (finalPath != null) {
            return finalPath;
        }

        finalPath = findNode(TargetedNode, givenArray, startingNode.rightNode);
        if (finalPath != null) {
            return finalPath;
        }

       givenArray.remove(givenArray.size()-1);
        System.out.println("Returned null in path!");
        return null; // after checking that all other slots are null, meaning this branch isn't it
    }
    
    // for drawing elements of airtraffic control: a tapered concrete shaft
    // topped with a glass observation cab, an antenna and a blinking beacon
    @Override
    public void visualRepresentation(Graphics drawer, int width, int height) {
        Graphics2D g2 = (Graphics2D) drawer;
        Object oldHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, PixelArt.aaHint());

        int baseX = Location.getXPos();
        int baseY = Location.getYPos();

        int towerHeight = height * 6;
        int shaftHalfWidthBottom = width / 2;
        int shaftHalfWidthTop = width / 3;
        int cabWidth = (int) (width * 1.9);
        int cabHeight = (int) (height * 1.1);

        int shaftBottomY = baseY;
        int shaftTopY = baseY - towerHeight;

        // ground shadow
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillOval(baseX - shaftHalfWidthBottom, shaftBottomY - 5, shaftHalfWidthBottom * 2, 16);

        // tapered concrete shaft
        Polygon shaft = new Polygon();
        shaft.addPoint(baseX - shaftHalfWidthBottom, shaftBottomY);
        shaft.addPoint(baseX + shaftHalfWidthBottom, shaftBottomY);
        shaft.addPoint(baseX + shaftHalfWidthTop, shaftTopY);
        shaft.addPoint(baseX - shaftHalfWidthTop, shaftTopY);
        g2.setPaint(new GradientPaint(baseX - shaftHalfWidthBottom, 0, new Color(220, 220, 215),
                baseX + shaftHalfWidthBottom, 0, new Color(150, 150, 148)));
        g2.fillPolygon(shaft);
        g2.setColor(new Color(110, 110, 105));
        g2.drawPolygon(shaft);

        // warning stripe partway up the shaft
        g2.setColor(new Color(190, 45, 45));
        g2.fillRect(baseX - shaftHalfWidthBottom + 2, shaftBottomY - (int) (towerHeight * 0.25), shaftHalfWidthBottom * 2 - 4, 6);

        // observation deck rim
        int cabX = baseX - cabWidth / 2;
        int cabY = shaftTopY - cabHeight;
        g2.setColor(new Color(85, 85, 85));
        g2.fillRect(cabX - 5, shaftTopY - 7, cabWidth + 10, 9);

        // cab body
        g2.setColor(new Color(65, 75, 85));
        g2.fillRoundRect(cabX, cabY, cabWidth, cabHeight, 10, 10);

        // glass band around the cab, with mullions
        int glassHeight = (int) (cabHeight * 0.6);
        g2.setColor(new Color(120, 205, 235, 235));
        g2.fillRoundRect(cabX + 4, cabY + 4, cabWidth - 8, glassHeight, 8, 8);
        g2.setColor(new Color(255, 255, 255, 110));
        for (int wx = cabX + 8; wx < cabX + cabWidth - 6; wx += 8) {
            g2.drawLine(wx, cabY + 4, wx, cabY + 4 + glassHeight);
        }

        // roof
        g2.setColor(new Color(55, 60, 65));
        g2.fillRect(cabX - 3, cabY - 6, cabWidth + 6, 6);

        // antenna mast and beacon light
        g2.setColor(Color.DARK_GRAY);
        g2.drawLine(baseX, cabY - 6, baseX, cabY - 28);
        g2.setColor(new Color(230, 40, 40));
        g2.fillOval(baseX - 4, cabY - 34, 8, 8);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint != null ? oldHint : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    // pixel-art version: a stone lighthouse-style tower with a red band, a wooden balcony, a glass
    // lookout cab under a shingled roof, and an antenna with a blinking beacon. art-pixel coords
    public void drawPixelArt(Graphics2D art, long frame) {
        int s = PixelArt.SCALE;
        Color out = PixelArt.OUTLINE;
        int cx = Location.getXPos() / s;
        int baseY = Location.getYPos() / s;
        int topY = baseY - 100;

        Color stone = new Color(198, 186, 168);
        Color stoneLight = new Color(224, 214, 196);
        Color stoneDark = new Color(152, 142, 128);
        Color mortar = new Color(132, 122, 110);
        Color wood = new Color(160, 104, 58);
        Color woodLight = new Color(192, 134, 80);
        Color woodDark = new Color(118, 74, 40);

        // shadow falling down-right
        PixelArt.rect(art, cx - 4, baseY - 1, 18, 3, new Color(20, 40, 10, 70));

        // tapered stone shaft, brick rows with staggered mortar joints
        for (int y = topY; y < baseY; y++) {
            int halfWidth = 8 - (baseY - y) * 2 / 100;
            int left = cx - halfWidth;
            int right = cx + halfWidth;
            PixelArt.px(art, left - 1, y, out);
            PixelArt.px(art, right, y, out);
            int row = (baseY - y) / 3;
            boolean mortarRow = (baseY - y) % 3 == 0;
            for (int x = left; x < right; x++) {
                Color c;
                if (mortarRow || (x - left + (row % 2) * 3) % 6 == 0) {
                    c = mortar;
                } else if (x < left + 2) {
                    c = stoneLight;
                } else if (x >= right - 2) {
                    c = stoneDark;
                } else {
                    c = PixelArt.hash(x / 6, row, 50) < 0.3 ? PixelArt.shade(stone, 0.94) : stone;
                }
                PixelArt.px(art, x, y, c);
            }
        }
        PixelArt.rect(art, cx - 9, baseY, 18, 1, out);

        // red band partway up
        int bandY = baseY - 28;
        int bandHalf = 8 - 28 * 2 / 100;
        PixelArt.rect(art, cx - bandHalf, bandY, bandHalf * 2, 3, new Color(184, 66, 52));
        PixelArt.rect(art, cx - bandHalf, bandY, bandHalf * 2, 1, new Color(220, 104, 82));
        PixelArt.rect(art, cx - bandHalf, bandY + 2, bandHalf * 2, 1, new Color(136, 44, 38));

        // small slit windows up the shaft
        for (int wy = baseY - 50; wy > topY + 6; wy -= 22) {
            PixelArt.rect(art, cx - 1, wy - 1, 3, 5, out);
            PixelArt.rect(art, cx, wy, 1, 3, new Color(250, 214, 120));
        }

        // arched door at the base
        PixelArt.rect(art, cx - 3, baseY - 9, 6, 9, out);
        PixelArt.rect(art, cx - 2, baseY - 8, 4, 8, wood);
        PixelArt.rect(art, cx - 2, baseY - 8, 4, 1, woodLight);
        PixelArt.px(art, cx, baseY - 4, woodDark);
        PixelArt.px(art, cx + 1, baseY - 4, new Color(244, 204, 92));

        // wooden balcony with railing posts
        int deckY = topY - 3;
        PixelArt.rect(art, cx - 13, deckY, 26, 4, out);
        PixelArt.rect(art, cx - 12, deckY + 1, 24, 2, wood);
        PixelArt.rect(art, cx - 12, deckY + 1, 24, 1, woodLight);
        PixelArt.rect(art, cx - 12, deckY - 4, 24, 1, woodDark);
        for (int x = cx - 12; x < cx + 12; x += 3) {
            PixelArt.rect(art, x, deckY - 3, 1, 3, woodDark);
        }

        // glass lookout cab
        int cabTop = deckY - 15;
        PixelArt.rect(art, cx - 10, cabTop, 20, 12, out);
        PixelArt.rect(art, cx - 9, cabTop + 1, 18, 10, new Color(128, 194, 222));
        for (int x = cx - 9; x < cx + 9; x += 4) {
            PixelArt.rect(art, x, cabTop + 1, 1, 10, wood);
        }
        PixelArt.px(art, cx - 7, cabTop + 2, new Color(226, 244, 252));
        PixelArt.px(art, cx - 6, cabTop + 2, new Color(226, 244, 252));
        PixelArt.px(art, cx - 7, cabTop + 3, new Color(226, 244, 252));
        PixelArt.px(art, cx + 2, cabTop + 3, new Color(226, 244, 252));
        PixelArt.rect(art, cx - 9, cabTop + 9, 18, 2, PixelArt.shade(new Color(128, 194, 222), 0.75));

        // pointed shingle roof
        Color[] roofTones = {new Color(104, 150, 200), new Color(72, 112, 168), new Color(50, 78, 126)};
        int roofBottom = cabTop;
        for (int i = 0; i < 10; i++) {
            int y = roofBottom - i;
            int half = 12 - i;
            PixelArt.rect(art, cx - half - 1, y, half * 2 + 2, 1, out);
            if (i < 9) {
                PixelArt.rect(art, cx - half, y, half * 2, 1, roofTones[i % 3]);
                PixelArt.px(art, cx - half, y, roofTones[0]);
            }
        }

        // antenna and blinking beacon
        int mastTop = roofBottom - 17;
        PixelArt.rect(art, cx, mastTop, 1, 8, new Color(84, 70, 70));
        PixelArt.rect(art, cx - 2, mastTop + 3, 5, 1, new Color(84, 70, 70));
        boolean beaconOn = (frame / 40) % 2 == 0;
        if (beaconOn) {
            art.setColor(new Color(255, 80, 60, 90));
            art.fillRect(cx - 2, mastTop - 4, 5, 5);
        }
        PixelArt.rect(art, cx - 1, mastTop - 3, 3, 3, out);
        PixelArt.px(art, cx, mastTop - 2, beaconOn ? new Color(255, 96, 72) : new Color(120, 40, 36));
    }

    // allows air traffic control to decide whether a plane can takeoff or not
    public void ClearAircraftForTakeOff(Aircraft selectedAircraft, ArrayList<Node> flightOutside, ArrayList<Node> airfieldRef) {
        if (airfieldRef != null && !airfieldRef.isEmpty() && selectedAircraft != null && flightOutside != null && !flightOutside.isEmpty()) {
            if (selectedAircraft.canFly() == true && selectedAircraft.getChosenToFly()) {
                System.out.println("Go for takeoff!!!");
                ResetAircraft(flyingAircraft);
                OccupiedAirfield = true;
                AirfieldNodeChanger(airfieldRef);
                flyingAircraft = selectedAircraft;
                flyingAircraft.setFlying(true);
                flyingAircraft.setFlightPath(flightOutside);
            }
        }
    }

    // changes all airfield nodes to be a value
    public void AirfieldNodeChanger (ArrayList<Node> airfieldRef) {
        if (airfieldRef != null && !airfieldRef.isEmpty())
        for (int i = 0; i < airfieldRef.size(); i++) {
            airfieldRef.get(i).setOccupied(OccupiedAirfield);
        }
    }

    // if aircraft flew off runway, free up runway
    public void checkIfAirfieldIsFree(int width, ArrayList<Node> airfieldRef) {
        if (flyingAircraft != null && flyingAircraft.getXPos() < 0 || flyingAircraft != null && !flyingAircraft.getCurrentNode().getNodeTileRepresentation().equalsIgnoreCase("RUNWAY")) {
            OccupiedAirfield = false;
            AirfieldNodeChanger(airfieldRef);
            flyingAircraft.setSelected(false);
            flyingAircraft = null;
        }
    }

    // detects if a aircraft is onfield
    public void checkIfAAircraftOnAirfield(ArrayList<Node> airfieldRef, Aircraft otherAircraft) {
        if (aircraftsInAirport != null && !aircraftsInAirport.isEmpty() && airfieldRef != null && !airfieldRef.isEmpty() && otherAircraft != null && flyingAircraft == null) {
            for (int i = 0; i < airfieldRef.size(); i++) {
                if (otherAircraft.getCurrentNode() == airfieldRef.get(i)) {
                    OccupiedAirfield = true;
                    AirfieldNodeChanger(airfieldRef);
                    flyingAircraft = otherAircraft;
                    flyingAircraft.setSelected(true);
                    i = airfieldRef.size();
                }
            }
        }
    }

    // check if clear for landing
    public void clearForLanding(Aircraft selectedAircraft, ArrayList<Node> airfieldRef, ArrayList<Node> airportNav) {
        if (airfieldRef != null && !airfieldRef.isEmpty() && selectedAircraft != null && airportNav != null && !airportNav.isEmpty()) {
            if (selectedAircraft.getFlying() && selectedAircraft.isAtLastNode()) {
                if (OccupiedAirfield == false && flyingAircraft == null) {
                    selectedAircraft.getCurrentNode().setOccupied(false);
                    flyingAircraft = selectedAircraft;
                    ResetAircraft(flyingAircraft);
                    flyingAircraft.setSelected(true);
                    flyingAircraft.setFlying(false);
                    flyingAircraft.setFlightPath(airportNav);
                    flyingAircraft.setStatus("GROUNDED");
                    OccupiedAirfield = true;
                    AirfieldNodeChanger(airfieldRef);
                    System.out.println("attempting to land!");
                }
            }
        }
    }

    // resets flight path of aircraft
    private void ResetAircraft (Aircraft selectedAircraft) {
        if (selectedAircraft != null) {
            selectedAircraft.resetIndex();
            selectedAircraft.setReachedTarget(false);
        }
    }

    // used for getting a random location on runway and gate
    public String getRandomNodeID(ArrayList<Node> nodeSet) {
        if (nodeSet != null && !nodeSet.isEmpty()) {
            int numberSelected = (int)(Math.random() * nodeSet.size());
            Node selectedNode = nodeSet.get(numberSelected);
            return selectedNode.getNodeID();
        } else {
            System.out.println("Cannot get a random node ID. Array is null or is empty");
            return null;
        }
    }
}

