import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.*;
import java.lang.Math.*;
import java.util.ArrayList;
import java.util.Random;

// Main code file for CISC 499 Project
// Jacob Clarke-McRae 10132960

// NOTE: Before starting program, make sure that if there are any unity objects representing cities (like red dots)
// in the simulation, the mesh on the objects will need to be disabled in unity so that the rover does not collide with the objects
// This can be done in unity by clicking on the objects in the scene window and unselecting the "Mesh Collider" option

// If this code is reused in the future, the driveTo() and rotate() methods are the most useful (although they use other methods as well)
public class DrivePattern {

    // Object to communicate to unity simulation
    private static SocketCommunicator rover = new SocketCommunicator();
    // Global list of unvisited points or cities for the rover to visit to
    private static ArrayList<Point> Points = new ArrayList<>();
    // The intial city that the rover should visit (closest city to rover starting point)
    private static Point startPoint = new Point(0,6);
    // Specifies the IP address to connect to the unity simulation
    // Use local IP address if simulation is running on local machine
    private static final String IP = "192.168.1.211";

    public static void main(String[] args) throws IOException {

        rover.connectToServer(IP,8886);
        String inputString = "";
        BufferedReader input = new BufferedReader (new InputStreamReader (System.in));

        Point current;

        // Keeps driving the rover around the set of cities using the nearest neighbour algorithm until the program is terminated
        while (true){
            current = new Point(startPoint.x, startPoint.z);
            // Drive to start point
            driveTo(current.x, current.z);
            addPoints();
            // Drives to rest of the cities in the order that the nearest neighbour algorithm decides
            while (!Points.isEmpty()){
                Point newPoint = greedyNextPoint(current);
                System.out.println(current.x + ", " + current.z);
                driveTo(newPoint.x, newPoint.z);
                current = new Point(newPoint.x, newPoint.z);
            }
        }
    }

    // Drives the rover to the provided x, z coordinates
    private static void driveTo(double x, double z){
        String compassString = trimString(rover.send("WALLY,getCompass()"));
        System.out.println("Driving to "+ x +", "+ z);

        double compass = Double.parseDouble(compassString);
        System.out.println("Compass: " + compass);

        String posXString = trimString(rover.send("WALLY,GPSx()"));
        double posX = Double.parseDouble(posXString);

        String posZString = trimString(rover.send("WALLY,GPSz()"));
        double posZ = Double.parseDouble(posZString);

        // Declare relative values of X and Z (how many X and Y units are between the rover and the destination)
        double relativeX = x - posX;
        double relativeZ = z - posZ;
        System.out.println("relativeX: " + relativeX);
        System.out.println("relativeZ: " + relativeZ);

        // Calculate length from rover to destination (hypotenuse)
        double hyp = Math.sqrt(relativeX*relativeX + relativeZ*relativeZ);

        double goalDirection = Math.abs(Math.toDegrees(Math.asin(Math.abs(relativeZ) / hyp)));
        System.out.println("Goal direction before calibration: " + goalDirection);
        goalDirection = calibrateGoalDirection(goalDirection, relativeX, relativeZ);

        goalDirection = goalDirection - compass;

        if (goalDirection > 360)
            goalDirection = goalDirection - 360;
        System.out.println("Goal direction after calibration: " + goalDirection);

        rotate(goalDirection);

        driveFor(hyp);
    }

    // Drives the rover in a straight line for the specified number of units
    private static void driveFor(double distance){
        // Calculates milliseconds program needs to sleep for to drive the specified number of units
        double driveTime = timeToDrive(distance);
        rover.send("WALLY,setLRPower(100,100)");
        try {
            Thread.sleep((int)driveTime);
        }
        catch(java.lang.InterruptedException e){e.printStackTrace();}
        rover.send("WALLY,setLRPower(0,0)");
        try {
            Thread.sleep(10000);
        }
        catch(java.lang.InterruptedException e){e.printStackTrace();}
    }

    // Rotates the rover by a specified number of degrees
    private static void rotate(double goalDirection){
        if (goalDirection < 0){
            rotate(360 - Math.abs(goalDirection));
        }
        else if (goalDirection > 180){
            rotateLeft(360 - goalDirection);
        }
        else{
            rotateRight(goalDirection);
        }
    }

    // Rotates the rover left by a specified number of degrees
    private static void rotateLeft(double degreesLeft){
        // Calculates milliseconds program needs to sleep for to rotate the specified number of degrees
        int rotateTime = timeToRotate(degreesLeft);
        rover.send("WALLY,setLRPower(-500,500)");
        System.out.println("Rotating");
        try {
            Thread.sleep(rotateTime);
        }
        catch(java.lang.InterruptedException e){e.printStackTrace();}
        rover.send("WALLY,setLRPower(0,0)");
        System.out.println("Stopping");
        try {
            Thread.sleep(10000);
        }
        catch(java.lang.InterruptedException e){e.printStackTrace();}
    }

    // Rotates the rover right by a specified number of degrees
    private static void rotateRight(double degreesRight){
        System.out.println("Rotating right by " + degreesRight);
        // Calculates milliseconds program needs to sleep for to rotate the specified number of degrees
        int rotateTime = timeToRotate(degreesRight);
        rover.send("WALLY,setLRPower(500,-500)");
        System.out.println("Rotating");
        try {
            Thread.sleep(rotateTime);
        }
        catch(java.lang.InterruptedException e){e.printStackTrace();}
        rover.send("WALLY,setLRPower(0,0)");
        System.out.println("Stopping");
        try {
            Thread.sleep(10000);
        }
        catch(java.lang.InterruptedException e){e.printStackTrace();}
    }

    // Adjusts direction of goal city based on the quadrant the goal lies in relative to the rover
    private static double calibrateGoalDirection(double goalDirection, double relativeX, double relativeZ){
        // First quadrant
        if (relativeX >= 0 && relativeZ >= 0){
            goalDirection = 90 - goalDirection;
        }
        // Second quadrant
        else if (relativeX < 0 && relativeZ >= 0){
            goalDirection = 270 + goalDirection;
        }
        // Third quadrant
        else if (relativeX < 0 && relativeZ < 0){
            goalDirection = 180 + (90 - goalDirection);
        }
        // Forth quadrant
        else{
            goalDirection = 90 + goalDirection;
        }

        return goalDirection;
    }

    // Calculates the amount of millisecconds the program should sleep for to rotate a certain distance
    private static int timeToRotate(double degrees){
        double rotateTime;
        double a = 38.84913;
        double b = 2.092192;
        double c = 203.7736;
        double d = 44689.91;

        rotateTime = d + ((a - d) / (1 + Math.pow((degrees / c), b)));

        return (int)rotateTime;
    }

    // Calculates the amount of millisecconds the program should sleep for to travel a certain distance
    private static int timeToDrive(double distance){
        double driveTime;
        double a = -54.34189;
        double b = 1.207487;
        double c = 333.2967;
        double d = 167025.8;

        driveTime = d + ((a - d) / (1 + Math.pow((distance / c), b)));

        return (int)driveTime;
    }

    // Adds a set of 19 points to the global list of unvisited points with random x and y values between -35 and 35
    private static void createRandomPoints(){
        Random r = new Random();
        for (int i=0; i<19; i++){
            int Low = -35;
            int High = 35;
            int r1 = r.nextInt(High-Low) + Low;
            int r2 = r.nextInt(High-Low) + Low;
            Points.add(new Point(r1, r2));
        }
        for (int i=0; i<Points.size();i++){
            System.out.print("("+(int)Points.get(i).x+","+(int)Points.get(i).z+"),");
        }
    }

    // Adds a set of points to the global list of unvisited points
    private static void addPoints(){
        Points.add(new Point(-11, 20));
        Points.add(new Point(-11, -8));
        Points.add(new Point(4, 18));
        Points.add(new Point(-15, -20));
        Points.add(new Point(29, -13));
        Points.add(new Point(7, -11));
        Points.add(new Point(22, 14));
    }

    // Calculates the next unvisited point that the Nearest Neighbour algorithm would return
    public static Point greedyNextPoint(Point current){
        if (Points.isEmpty()){
            addPoints();
        }
        double minDistance = 999999;
        Point closest = new Point();
        int closestPos = 0;
        double dist;
        for (int i = 0; i < Points.size(); i++){
            dist = distanceBetweenPoints(current, Points.get(i));
            if (dist < minDistance){
                minDistance = dist;
                closestPos = i;
                closest = new Point(Points.get(i).x, Points.get(i).z);
            }
        }
        Points.remove(closestPos);

        return closest;
    }

    // Calculates the distance between two specified points using th Pythagorean theorem
    public static double distanceBetweenPoints(Point p1, Point p2){
        return Math.sqrt((p2.x - p1.x)*(p2.x - p1.x) + (p2.z - p1.z)*(p2.z - p1.z));
    }

    // Used to quickly figure out the distance of the route that the nearest neighbour algorithm would construct wihtout running the simulation
    public static void greedyDistanceSimulator(){
        // Enter start point coordinates below
        Point startPoint = new Point(0,6);
        Point current = new Point(startPoint.x,startPoint.z);
        System.out.println("Starting at point: 0,6");
        double sumDistance = 0;
        // Enter number of points to test -1 (for starting point)
        int iterations = 19;
        for (int i = 0; i < iterations; i++){
            Point newPoint = greedyNextPoint(current);
            // Add to sum of distance
            sumDistance += distanceBetweenPoints(current, newPoint);
            System.out.println("Travelled to point: " + current.x + ", " + current.z);
            current = new Point(newPoint.x, newPoint.z);
        }
        sumDistance += distanceBetweenPoints(current, startPoint);
        System.out.println("Travelled to point: 0,6");
        System.out.println("Total distance covered = " + sumDistance);
    }

    // Function used to calculate distance required to visit a set of nodes in a particular order
    public static double optimalDistanceSimulator(){
        Points.clear();
        addPoints();
        double sumDistance = 0;
        Point startPoint = new Point(0,6);
        sumDistance += distanceBetweenPoints(startPoint, new Point(-11,-8));
        sumDistance += distanceBetweenPoints(new Point(-11,-8), new Point(-15,-20));
        sumDistance += distanceBetweenPoints(new Point(-15,-20), new Point(7,-11));
        sumDistance += distanceBetweenPoints(new Point(7,-11), new Point(29,-13));
        sumDistance += distanceBetweenPoints(new Point(29,-13), new Point(22,14));
        sumDistance += distanceBetweenPoints(new Point(22,14), new Point(4,18));
        sumDistance += distanceBetweenPoints(new Point(4,18), new Point(-11,20));
        sumDistance += distanceBetweenPoints(new Point(-11,20), new Point(0,6));

        sumDistance += distanceBetweenPoints(Points.get(3), startPoint);
        return sumDistance;
    }

    // Trims "WALLY," and ";" from string that gets returned from rover
    private static String trimString(String s){
        // Trim "WALLY," from start of compass string
        s = s.substring(6);
        // Trim semicolon from end of compass string
        s = s.substring(0,s.length()-1);

        return s;
    }

}
