import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

// this entire class is just used to display stuff on JPanel, pls don't delete as it is useful base 
// for drawing stuff

// should probably extend this class to anything with moveable or anything 
// that needs to be drawn or visualized in the simulator
public class JPanelVisualizer extends JPanel implements ActionListener {
    // important for this class
    private Timer timer;
    private int secondsPerFrame = 10; // in miliseconds

    private int aircraftCount = (int)(Math.random() * (10 - 1 + 1)) + 1;
    private Aircraft[] aircrafts = new Aircraft[1];
    private JFrame JframeRef;
    private ArrayList<Node> flightPath = new ArrayList<>();
    private ArrayList<Node> airportNav = new ArrayList<>();
    private ArrayList<Aircraft> aircraftsOnSite = new ArrayList<>();
    private AirTrafficControl airControl = new AirTrafficControl(aircraftsOnSite,  airportNav,new Vector2(400, 600));
    private ArrayList<Node> runway = new ArrayList<>();
    private ArrayList<Node> waitingBay = new ArrayList<>();
    private ArrayList<Node> outsideLoop = new ArrayList<>();
    private ArrayList<AirwayGate> allGates = new ArrayList<>();
    private FlightBoard flightBoard = new FlightBoard(aircraftsOnSite);
    // fixed seed so the grass texture doesn't re-randomize (and flicker) every repaint
    private ArrayList<double[]> grassTufts = new ArrayList<>();
    // fixed positions for water sparkle highlights; only their twinkle brightness animates
    private ArrayList<double[]> waterSparkles = new ArrayList<>();
    // advances every frame so wave ripples and sparkles animate smoothly over time
    private long frameCount = 0;

    // intializes time
    public JPanelVisualizer(JFrame jframePanel) {
        JframeRef = jframePanel;

        Node leftFlyOff = new Node(null, null, null, null, new Vector2(-200, 75), "Outside left", "RUNWAY");
        Node leftTopFlyOff = new Node(null, null, null, null, new Vector2(-200, -200), "Outside top left", "Outside");
        Node rightTopFlyOff = new Node(null, null, null, null, new Vector2(JframeRef.getWidth() + 200, -200), "Outside top right", "Outside");
        Node rightFlyOff = new Node(null, null, null, null, new Vector2(JframeRef.getWidth() + 100, 75), "Outside right", "Outside");
        
        Node airfieldNode1 = new Node(null, null, null, null, new Vector2(JframeRef.getWidth()/7 * 0 + (JframeRef.getWidth()/7)/2, 75), "A1", "RUNWAY");
        Node airfieldNode2 = new Node(null, null, airfieldNode1, null, new Vector2(JframeRef.getWidth()/7 * 2 + (JframeRef.getWidth()/7)/2, 75), "A2", "RUNWAY");
        Node airfieldNode3 = new Node(null, null, airfieldNode2, null, new Vector2(JframeRef.getWidth()/7 * 4 + (JframeRef.getWidth()/7)/2, 75), "A3", "RUNWAY");
        Node airfieldNode4 = new Node(null, null, airfieldNode3, null, new Vector2(JframeRef.getWidth()/7 * 6 + (JframeRef.getWidth()/7)/2, 75), "A4", "RUNWAY");

        Node miniRoadNode1 = new Node(airfieldNode1, null, null, null, new Vector2(JframeRef.getWidth()/7 * 0 + (JframeRef.getWidth()/7)/2, 225), "B1", "WAITINGBAY");
        Node miniRoadNode2 = new Node(airfieldNode2, null, null, null, new Vector2(JframeRef.getWidth()/7 * 2 + (JframeRef.getWidth()/7)/2, 225), "B2", "WAITINGBAY");
        Node miniRoadNode3 = new Node(airfieldNode3, null, null, null, new Vector2(JframeRef.getWidth()/7 * 4 + (JframeRef.getWidth()/7)/2, 225), "B3", "WAITINGBAY");
        Node miniRoadNode4 = new Node(airfieldNode4, null, null, null, new Vector2(JframeRef.getWidth()/7 * 6 + (JframeRef.getWidth()/7)/2, 225), "B4", "WAITINGBAY");

        Node TaxiWayNode1 = new Node(miniRoadNode1, null, null, null, new Vector2(JframeRef.getWidth()/7 * 0 + (JframeRef.getWidth()/7)/2, 375), "C1", "TAXIWAY");
        Node TaxiWayNode2 = new Node(miniRoadNode2, null, TaxiWayNode1, null, new Vector2(JframeRef.getWidth()/7 * 2 + (JframeRef.getWidth()/7)/2, 375), "C2", "TAXIWAY");
        Node TaxiWayNode3 = new Node(miniRoadNode3, null, TaxiWayNode2, null, new Vector2(JframeRef.getWidth()/7 * 4 + (JframeRef.getWidth()/7)/2, 375), "C3", "TAXIWAY");
        Node TaxiWayNode4 = new Node(miniRoadNode4, null, TaxiWayNode3, null, new Vector2(JframeRef.getWidth()/7 * 6 + (JframeRef.getWidth()/7)/2, 375), "C4", "TAXIWAY");

        Node GatePathNode1 = new Node(TaxiWayNode1, null, null, null, new Vector2(JframeRef.getWidth()/9 * 1, 500), "D1", "GATEPATH");
        Node GatePathNode2 = new Node(TaxiWayNode2, null, GatePathNode1, null, new Vector2(JframeRef.getWidth()/9 * 3, 500), "D2", "GATEPATH");
        Node GatePathNode3 = new Node(TaxiWayNode3, null, GatePathNode2, null, new Vector2(JframeRef.getWidth()/9 * 5, 500), "D3", "GATEPATH");
        Node GatePathNode4 = new Node(TaxiWayNode4, null, GatePathNode3, null, new Vector2(JframeRef.getWidth()/9 * 7, 500), "D4", "GATEPATH");

        Node TestGate1 = new Node(GatePathNode1, null, null, null, new Vector2(JframeRef.getWidth()/9 * 1, 600), "E1", "GATE");
        Node TestGate2 = new Node(GatePathNode2, null, TestGate1, null, new Vector2(JframeRef.getWidth()/9 * 3, 600), "E2", "GATE");
        Node TestGate3 = new Node(GatePathNode3, null, TestGate2, null, new Vector2(JframeRef.getWidth()/9 * 5, 600), "E3", "GATE");
        Node TestGate4 = new Node(GatePathNode4, null, TestGate3, null, new Vector2(JframeRef.getWidth()/9 * 7, 600), "E4", "GATE");

        // setting up map nodes
        airfieldNode1.setBottomNode(miniRoadNode1);
        airfieldNode1.setRightNode(airfieldNode2);
        airfieldNode2.setBottomNode(miniRoadNode2);
        airfieldNode2.setRightNode(airfieldNode3);
        airfieldNode3.setBottomNode(miniRoadNode3);
        airfieldNode3.setRightNode(airfieldNode4);
        airfieldNode4.setBottomNode(miniRoadNode4);

        miniRoadNode1.setBottomNode(TaxiWayNode1);
        miniRoadNode2.setBottomNode(TaxiWayNode2);
        miniRoadNode3.setBottomNode(TaxiWayNode3);
        miniRoadNode4.setBottomNode(TaxiWayNode4);

        TaxiWayNode1.setBottomNode(GatePathNode1);
        TaxiWayNode1.setRightNode(TaxiWayNode2);
        TaxiWayNode2.setBottomNode(GatePathNode2);
        TaxiWayNode2.setRightNode(TaxiWayNode3);
        TaxiWayNode3.setBottomNode(GatePathNode3);
        TaxiWayNode3.setRightNode(TaxiWayNode4);
        TaxiWayNode4.setBottomNode(GatePathNode4);

        GatePathNode1.setBottomNode(TestGate1);
        GatePathNode1.setRightNode(GatePathNode2);
        GatePathNode2.setBottomNode(TestGate2);
        GatePathNode2.setRightNode(GatePathNode3);
        GatePathNode3.setBottomNode(TestGate3);
        GatePathNode3.setRightNode(GatePathNode4);
        GatePathNode4.setBottomNode(TestGate4);

        TestGate1.setRightNode(TestGate2);
        TestGate2.setRightNode(TestGate3);
        TestGate3.setRightNode(TestGate4);

        // add to airport nav
        airportNav.add(airfieldNode1);
        airportNav.add(airfieldNode2);
        airportNav.add(airfieldNode3);
        airportNav.add(airfieldNode4);

        airportNav.add(miniRoadNode1);
        airportNav.add(miniRoadNode2);
        airportNav.add(miniRoadNode3);
        airportNav.add(miniRoadNode4);

        airportNav.add(TaxiWayNode1);
        airportNav.add(TaxiWayNode2);
        airportNav.add(TaxiWayNode3);
        airportNav.add(TaxiWayNode4);

        airportNav.add(GatePathNode1);
        airportNav.add(GatePathNode2);
        airportNav.add(GatePathNode3);
        airportNav.add(GatePathNode4);

        // marked runway
        runway.add(airfieldNode1);
        runway.add(airfieldNode2);
        runway.add(airfieldNode3);
        runway.add(airfieldNode4);

        // marked waitingBay
        waitingBay.add(miniRoadNode1);
        waitingBay.add(miniRoadNode2);
        waitingBay.add(miniRoadNode3);
        waitingBay.add(miniRoadNode4);

        // outside loop
        outsideLoop.add(leftFlyOff);
        outsideLoop.add(leftTopFlyOff);
        outsideLoop.add(rightTopFlyOff);
        outsideLoop.add(rightFlyOff);

        // airway gates
        AirwayGate Gate1 = new AirwayGate("Gate01",true , TestGate1);
        AirwayGate Gate2 = new AirwayGate("Gate02",true , TestGate2);
        AirwayGate Gate3 = new AirwayGate("Gate03",true , TestGate3);
        AirwayGate Gate4 = new AirwayGate("Gate04",true , TestGate4);

        allGates.add(Gate1);
        allGates.add(Gate2);
        allGates.add(Gate3);
        allGates.add(Gate4);

        // establishing navigational path
        flightPath = airControl.calculateRoute("A4", TestGate1);

        Aircraft testFlight = new CargoPlane("Test aircraft", "Thyme the geat", "Hawking404", 1500.00, 50,"GROUNDED", 500.00, 250.00);
        testFlight.setVector2(200, 400);
        testFlight.setFlightPath(flightPath);
        testFlight.setColor(Color.ORANGE);
        aircraftsOnSite.add(testFlight);

        Aircraft testFlight2 = new CommercialPlane("Tester103", "Albert Minestein", "Blimper64", 1300.00, 50,"GROUNDED", 500, 250);
        testFlight2.setVector2(TaxiWayNode4.getXPos()-100, TaxiWayNode4.getYPos());
        flightPath = airControl.calculateRoute("A1", TestGate2);
        testFlight2.setFlightPath(flightPath);
        testFlight2.setColor(Color.CYAN);
        aircraftsOnSite.add(testFlight2);

        Aircraft testFlight3 = new CommercialPlane("12345", "Mr Joel", "AirDuck302",1400.00, 100, "GROUNDED", 1000, 779);
        flightPath = airControl.calculateRoute("E3", miniRoadNode1);
        testFlight3.setVector2(miniRoadNode1.getXPos(), miniRoadNode1.getYPos()-50);
        testFlight3.setFlightPath(flightPath);
        testFlight3.setColor(Color.MAGENTA);
        aircraftsOnSite.add(testFlight3);
        

        // pre-generate scattered grass tuft positions (as fractions of panel size) once,
        // so the texture stays put across the 100fps repaint loop instead of shimmering
        java.util.Random grassRandom = new java.util.Random(42);
        for (int i = 0; i < 500; i++) {
            grassTufts.add(new double[]{grassRandom.nextDouble(), grassRandom.nextDouble(), grassRandom.nextDouble()});
        }

        // pre-generate scattered water sparkle positions the same way, so they stay put
        // and only their twinkle brightness (driven by frameCount) animates
        java.util.Random waterRandom = new java.util.Random(7);
        for (int i = 0; i < 150; i++) {
            waterSparkles.add(new double[]{waterRandom.nextDouble(), waterRandom.nextDouble(), waterRandom.nextDouble()});
        }

        timer = new Timer(secondsPerFrame, this); // every secondsPerFrame time, = 1 frame
        timer.start(); // starts the timer
    }

    // draws a mowed-lawn stripe pattern plus scattered tufts instead of a flat green rectangle
    private void drawGrass(Graphics g, int width, int height) {
        g.setColor(new Color(30, 110, 40));
        g.fillRect(0, 0, width, height);

        int stripeWidth = 45;
        for (int sx = 0; sx * stripeWidth < width; sx++) {
            g.setColor(sx % 2 == 0 ? new Color(36, 122, 48) : new Color(24, 98, 34));
            g.fillRect(sx * stripeWidth, 0, stripeWidth, height);
        }

        for (double[] tuft : grassTufts) {
            int tx = (int) (tuft[0] * width);
            int ty = (int) (tuft[1] * height);
            int shadeVariance = (int) (tuft[2] * 40) - 20;
            int green = Math.min(255, Math.max(0, 112 + shadeVariance));
            g.setColor(new Color(18, green, 28));
            g.fillOval(tx, ty, 3, 3);
        }
    }

    // draws a gradient body of water with rippling wave bands, twinkling sparkle
    // highlights and a foam line along the shore, instead of a flat blue rectangle
    private void drawWater(Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g;
        Object oldHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // base gradient: brighter near the shore, deeper toward the bottom
        g2.setPaint(new GradientPaint(x, y, new Color(35, 110, 175), x, y + height, new Color(8, 35, 80)));
        g2.fillRect(x, y, width, height);

        // rippling wave bands, phase-shifted over time for a subtle animated shimmer
        double phase = frameCount * 0.03;
        int step = 6;
        int bandSpacing = Math.max(16, height / 9);
        for (int bandIndex = 0; bandIndex * bandSpacing < height; bandIndex++) {
            int baseY = y + bandIndex * bandSpacing + bandSpacing / 2;
            int points = width / step + 2;
            int[] xPoints = new int[points];
            int[] yPoints = new int[points];
            double amplitude = 3 + (bandIndex % 3);
            double wavelength = 70 + bandIndex * 5;
            for (int i = 0; i < points; i++) {
                int px = x + i * step;
                double wave = Math.sin(px / wavelength + phase + bandIndex) * amplitude;
                xPoints[i] = px;
                yPoints[i] = (int) (baseY + wave);
            }
            int shade = 90 + (bandIndex % 3) * 25;
            g2.setColor(new Color(60, shade + 60, shade + 110, 130));
            g2.drawPolyline(xPoints, yPoints, points);
        }

        // scattered sparkle highlights at fixed spots, twinkling in and out over time
        for (double[] sparkle : waterSparkles) {
            int sx = x + (int) (sparkle[0] * width);
            int sy = y + (int) (sparkle[1] * height);
            double twinkle = (Math.sin(phase * 2 + sparkle[2] * Math.PI * 2) + 1) / 2; // 0..1
            int alpha = (int) (40 + twinkle * 140);
            g2.setColor(new Color(220, 240, 255, alpha));
            g2.fillOval(sx, sy, 2, 2);
        }

        // foam line marking the shoreline where the water meets the tarmac above
        g2.setColor(new Color(230, 240, 245, 200));
        for (int fx = x; fx < x + width; fx += 14) {
            int fy = y + (int) (Math.sin(fx / 40.0 + phase) * 2);
            g2.drawLine(fx, fy, fx + 8, fy);
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint != null ? oldHint : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // this entire function is used to update this element every frame
        // key note: increase in xPos = more to right, increase in Y makes it go down
        frameCount++;

            for (int i = 0; i < aircraftsOnSite.size(); i++) {
                Aircraft selectedAircraft = aircraftsOnSite.get(i);
                // checks if the airfield is clear
                if (airControl.getOccupiedAirfield() == true) {
                    airControl.checkIfAirfieldIsFree(JframeRef.getWidth(), runway);
                } else {
                airControl.checkIfAAircraftOnAirfield(runway, selectedAircraft);
                }
                // checks if aircraft at gate
                if (selectedAircraft.isAtLastNode() && selectedAircraft.getCurrentNode().getNodeTileRepresentation().equalsIgnoreCase("GATE") && selectedAircraft.getReachedTarget()) {
                    if (selectedAircraft.getAssignedGate() == null) {
                    for (int c = 0; c < allGates.size(); c++) {
                            allGates.get(c).PlaneAtGate(selectedAircraft);
                        }
                    }
                }

                selectedAircraft.influenceFuel(); // either gain fuel or spend fuel
                
                // checks if plane is flying
                if (!selectedAircraft.getFlying()) {
                selectedAircraft.CheckIfNextPathIsBlocked(); // checks if the path is blocked or not
            if (selectedAircraft.canFly() == true) {
                // if plane can fly, then it starts to fly
                selectedAircraft.warmUpEngines();
                selectedAircraft.decreaseCountdown();
                if (selectedAircraft.CooldownOver()) {
                    selectedAircraft.getFlightPath().get(selectedAircraft.getFlightPath().size()-1).setOccupied(false);
                    airControl.ClearAircraftForTakeOff(selectedAircraft, outsideLoop, runway); // changes path to outside route
                }
            } // if on ground
            else if (selectedAircraft.canFly() == false && selectedAircraft.getStatus().equalsIgnoreCase("GROUNDED") && selectedAircraft.isBlocked() == false) {
                // moves through the airport
                selectedAircraft.MoveThroughFlightPath(1); 
            } // if blocked while moving 
            else if(selectedAircraft.isBlocked() == true && !selectedAircraft.getFlying()) { 
                // if a aircraft path is being blocked, it regenerates a new path or goes back 1 node
                int chosenAction = (int)(Math.random() * 3);
                selectedAircraft.decreaseCountdown();
                if (chosenAction == 0) {
                    selectedAircraft.reverseAircraft();
                } else if (chosenAction == 1) { // generates new path
                    String NodeID = selectedAircraft.getFlightPath().get(selectedAircraft.getFlightPath().size()-1).getNodeID();
                    Node currentNode = selectedAircraft.getCurrentNode();
                    flightPath = airControl.calculateRoute(NodeID, currentNode);
                    selectedAircraft.decreaseCountdown();
                    if (flightPath == null && selectedAircraft.CooldownOver()) { // if path is null, it waits until it can find node or unoccupied
                        selectedAircraft.setCountdown(100);
                    }
                    if (flightPath != null && selectedAircraft.CooldownOver()) {
                        selectedAircraft.setFlightPath(flightPath); // creates new path so it doesn't collide with other aircrafts
                    }
                } else if (selectedAircraft.CooldownOver()) {
                    selectedAircraft.setCountdown(100); // waits for 100 frames to see if other aircrafts will do something
                }
            } 
            else if (selectedAircraft.isAtGate()) {
                // if aircraft is at gate, it countsdown until 0
                selectedAircraft.decreaseCountdown();
                if (selectedAircraft.CooldownOver()) {
                    AirwayGate currentGate = selectedAircraft.getAssignedGate();
                    // if plane can depart
                    if (selectedAircraft.getAssignedGate() != null && selectedAircraft.getCurrentNode().getPosition().compareVectors(currentGate.getGateNode().getPosition()) && currentGate.getStatus() == true) {
                        String selectedNodeID = airControl.getRandomNodeID(runway);
                        currentGate.departingPlane(airControl, selectedNodeID);
                    }
                }
            }
             else {
                selectedAircraft.moveTowards(1); // once in flight, moves to target
            }

            // if aircraft is flying
        } else if (selectedAircraft.getFlying()) {
                selectedAircraft.MoveThroughFlightPath(1); 
                // upon looping, it flies back into the airport
                if (selectedAircraft.compareVectors(outsideLoop.get(outsideLoop.size()-1).getPosition())) {
                    // upon raching the airport, finds a gate to go to
                    ArrayList<Node> gateNodes = new ArrayList<>();
                    for (int c = 0; c < allGates.size(); c++) {
                        if (allGates.get(c).isFree()) {
                         gateNodes.add(allGates.get(c).getGateNode());
                        }
                    }
                    String selectedGate = airControl.getRandomNodeID(gateNodes);
                    flightPath = airControl.calculateRoute(selectedGate, runway.get(0));
                    airControl.clearForLanding(selectedAircraft, runway, flightPath);
                }
            }
            else { // otherwise moves towards target
            selectedAircraft.moveTowards(1);
            }

            airControl.AirfieldNodeChanger(runway);
        }
        
        // updates the panel
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        // put anything you want to redraw, like images or shapes here, otherwise they won't be redrawn
        super.paintComponent(g);// put anything drawn after this line
        // background
        drawGrass(g, JframeRef.getWidth(), JframeRef.getHeight());
        // draw airfield
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, JframeRef.getWidth(), 150);
        g.setColor(Color.white);
        for (int i = 120; i < JframeRef.getWidth() - 130; i+=100) {
            g.drawLine(i, 75, i+50, 75);
        }
        for (int i = 10; i < 120; i+=20) {
            g.fillRect(10, i+10, 100, 10);
            g.fillRect(JframeRef.getWidth() - 130, i+10, 100, 10);
        }
        g.drawLine(0, 10, JframeRef.getWidth(), 10);
        g.drawLine(0, 140, JframeRef.getWidth(), 140);
        // making road to airfield
        g.setColor(Color.GRAY);
        for (int i = 0; i < 8; i++) {
            if (i%2 == 0) {
                g.fillRect(JframeRef.getWidth()/7 * i, 150, JframeRef.getWidth()/7-10 , 150);
            }
        }
        g.setColor(Color.YELLOW);
        for (int i = 0; i < 8; i++) {
            if (i%2 == 0) {
                g.drawLine(JframeRef.getWidth()/7 * i + 5, 150, JframeRef.getWidth()/7 * i + 5, 300);
                g.drawLine(JframeRef.getWidth()/7 * i + JframeRef.getWidth()/7-16, 150, JframeRef.getWidth()/7 * i + JframeRef.getWidth()/7-16, 300);
                g.drawLine(JframeRef.getWidth()/7 * i + JframeRef.getWidth()/7/2, 150, JframeRef.getWidth()/7 * i + JframeRef.getWidth()/7/2, 300);
            }
        }
        // making taxiway
        g.setColor(Color.GRAY);
        g.fillRect(0, 300, JframeRef.getWidth(), 150);
        g.setColor(Color.YELLOW);
        for (int i = 0; i < JframeRef.getWidth(); i+=100) {
            g.drawLine(i, 375, i+50, 375);
        }
        g.drawLine(0, 310, JframeRef.getWidth(), 310);
        g.drawLine(0, 440, JframeRef.getWidth(), 440);
        // making road to gate
        g.setColor(Color.GRAY);
        for (int i = 0; i < 5; i++) {
            if (i%2 == 0) {
                g.fillRect(JframeRef.getWidth()/5 * i, 450, JframeRef.getWidth()/5 , 150);
            }
        }
        // making gates
        for (int i = 0; i < allGates.size(); i++) {
            allGates.get(i).visualRepresentation(g, JframeRef.getWidth()/9 , 200);
            // g.fillRect(JframeRef.getWidth()/9 * i, 600, JframeRef.getWidth()/9 , 200);
        }
        // water beyond the terminal apron
        drawWater(g, 0, JframeRef.getHeight()-150, JframeRef.getWidth(), 200);
        // air traffic control
        airControl.visualRepresentation(g, 50,50);
        // visualize nodes as flashing airfield lights: brightness pulses over time, phase
        // offset per node so the flash appears to travel sequentially down the path, like
        // real runway/taxiway lead-in lighting
        Graphics2D g2Nodes = (Graphics2D) g;
        Object nodeLightHint = g2Nodes.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2Nodes.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < airportNav.size(); i++) {
            Node navNode = airportNav.get(i);
            int cx = navNode.getXPos() + 5;
            int cy = navNode.getYPos() + 5;

            double brightness = (Math.sin(frameCount * 0.05 - i * 0.6) + 1) / 2; // 0..1
            int coreAlpha = (int) (100 + brightness * 155);
            int glowRadius = (int) (9 + brightness * 5);

            // soft outer glow
            g2Nodes.setColor(new Color(80, 255, 120, coreAlpha / 4));
            g2Nodes.fillOval(cx - glowRadius, cy - glowRadius, glowRadius * 2, glowRadius * 2);

            // bright light core
            g2Nodes.setColor(new Color(60, 255, 90, coreAlpha));
            g2Nodes.fillOval(cx - 5, cy - 5, 10, 10);
        }
        g2Nodes.setRenderingHint(RenderingHints.KEY_ANTIALIASING, nodeLightHint != null ? nodeLightHint : RenderingHints.VALUE_ANTIALIAS_DEFAULT);

        // visualize planes
       for (int i = 0; i < aircraftsOnSite.size(); i++) {
        aircraftsOnSite.get(i).visualRepresentation(g, 50, 50);
       }

        // flight board, drawn last so it sits on top of everything else
        // uses this panel's own size (not the JFrame's) so it isn't pushed past the
        // visible area by the title bar/borders, which would clip off the bottom rows
        flightBoard.visualRepresentation(g, getWidth(), getHeight());
    }
}
