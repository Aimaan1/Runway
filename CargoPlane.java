import java.awt.Color;
import java.awt.Graphics;

public class CargoPlane extends Aircraft implements flyable {
    private final double maxWeight; // in KG
    private double currentWeight;

    public CargoPlane(String aircraftID, String operator, String model, double fuelLevel, int capacity, String status, double maxWeight, double currentWeight) {
        super(aircraftID, operator, model, fuelLevel, capacity, status);
        this.maxWeight = maxWeight;
        setCurrentWeight(currentWeight); //call setter from constructor so it would check setter first 
    }

    //setter
    public void setCurrentWeight(double currentWeight){
        requireInRange(currentWeight, 0.0, maxWeight, "Weight");
        this.currentWeight = currentWeight;
    }
    
    /* No setter for maxWeight because it is fixed when the plane is created */

    //getters
    public double getCurrentWeight(){
        return currentWeight;
    }

    public double getMaxWeight(){
        return maxWeight;
    }

    @Override
    public void displayInfo(){
        super.displayInfo();
        
        System.out.println("This is a Cargo Plane.");
        System.out.println("Max Weight: " + maxWeight);
        System.out.println("Current Weight: " + currentWeight);
    }

    // for flyable implement
    @Override
    public boolean flying() {
        return "Flying".equals(this.getStatus());
    }

    @Override
    public Vector2 getDestinationPostion() {
        return this.getTarget();
    }
    
    @Override
    public void setLocation(Vector2 newPos) {
        this.setTarget(newPos);
    }

    @Override
    public boolean isReadyForLanding() {
        // Airborne and still has fuel to complete an approach.
        return flying() && getFuelLevel() > 0;
    }

    @Override
    public boolean isReadyForTakeoff() {
        // On the ground, fuelled, and not loaded beyond the max weight.
        return !flying() && getFuelLevel() > 0 && currentWeight <= maxWeight;
    }

    @Override
    public void visualRepresentation(Graphics drawer, int width, int height) {
        drawPlaneShape(drawer, width, height, getColor() != null ? getColor() : Color.RED);
        String[] lines = {
            "CargoPlane: " + this.getAircraftID(),
            "Operator: " + this.getOperator(),
            "Model: " + this.getModel(),
            "Fuel Level: " + this.getFuelLevel() + "L",
            "Capacity: " + this.getCapacity(),
            "Current status: " + this.getStatus(),
            "Current target is: " + getCurrentNode().getNodeID()
        };
        drawTextWindow(drawer, xPos + width / 2 + 10, yPos, lines);
    }

    // allows you to built up the status for future board
    @Override 
    public String getOverallStatus() {
        String status = "Flight number " + getAircraftID();
        return status;
    }
}