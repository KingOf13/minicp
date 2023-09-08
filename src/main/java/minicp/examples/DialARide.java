package minicp.examples;

import static minicp.cp.BranchingScheme.*;
import static minicp.cp.Factory.*;

import minicp.cp.Factory;
import minicp.engine.constraints.*;
import minicp.engine.core.IntVar;
import minicp.engine.core.Solver;
import minicp.search.DFSearch;
import minicp.search.Objective;
import minicp.search.SearchStatistics;
import minicp.util.io.InputReader;

import java.lang.management.ManagementFactory;
import java.util.*;

import static minicp.cp.Factory.makeIntVarArray;

public class DialARide {
    public static String filePath = "data/dialaride/custom3";
    public static int acc;
    public static int[] bestPath;
    public static int[] bestRideID;
    public static int bestCost;
    public static final long onesec = (long) 1e+9;
    public static final long onemin = 60*onesec;
    public static final long maxTime = 4*onemin - 1*onesec;
    public static boolean debug = false;
    private static int initFailLimit = 25;
    private static int maxFailLimit = 60;
    private static int minFailLimit = 10;
    private static int remplissage = 85;
    private static long timeLimitClever = 2*onesec;

    private static DialARideSolution createSolution(int nVehicles, ArrayList<RideStop> pickupRideStops, ArrayList<RideStop> dropRideStops, RideStop depot,
                                                    int vehicleCapacity, int maxRideTime, int maxRouteDuration) {
        DialARideSolution d = new DialARideSolution(nVehicles, pickupRideStops, dropRideStops, depot, vehicleCapacity, maxRideTime, maxRouteDuration);
        int nextStep = bestPath[0];
        if (debug) System.out.println("Found "+ ((acc == 1) ? "1 solution" : (acc + " solutions")));
        int n = pickupRideStops.size();
        int size = 2*n + 2*nVehicles;

        for (int i = 0; i < size; i ++) {
            //if (nextStep >= nVehicles && nextStep < nVehicles+2*n) System.out.println("Vehicles n° " + bestRideID[nextStep] + ", client : " + (nextStep-nVehicles)%n + ", "  + (nextStep < nVehicles+ n ? "prise" : "depot"));
            if (nextStep < nVehicles) {}
            else if (nextStep < nVehicles + n ) d.addStop(bestRideID[nextStep], nextStep-nVehicles, true);
            else if (nextStep < nVehicles + 2*n ) d.addStop(bestRideID[nextStep], nextStep-nVehicles-n, false);
            nextStep = bestPath[nextStep];
        }
        return d;
    }

    public static int getNextWindowEnd(IntVar xs, ArrayList<RideStop> pickup, ArrayList<RideStop> drop, int nV, Integer bestSuccVal) {
        int n = pickup.size();
        int toEnd = 2*n + nV;
        int[] succ = new int[xs.size()];
        xs.fillArray(succ);
        int bestSuccIdx = -1;
        for (int i: succ) {
            int time = Integer.MAX_VALUE;
            if (i < nV) {}
            else if (i < n + nV) time = pickup.get(i-nV).window_end;
            else if (i < toEnd) time = drop.get(i-nV-n).window_end;
            else {}
            if (time <= bestSuccVal) {
                bestSuccVal = time;
                bestSuccIdx = i;
            }
        }
        return bestSuccIdx;

    }

    public static int[][] distToSuccessors(ArrayList<RideStop> pickup, ArrayList<RideStop> drop, RideStop depot, int nVehicles) {

        int n = pickup.size();

        int size = 2*n+2*nVehicles;

        int [][] dist = new int[size][size];

        for (int i = 0; i < size; i ++) {
            if (i < nVehicles) {
                for (int j = 0; j < size; j++) {
                    if (j < nVehicles) dist[i][j] = 0;
                    else if (j < nVehicles+n) dist[i][j] = distance(depot, pickup.get(j-nVehicles));
                    else if (j < nVehicles+2*n) dist[i][j] = distance(depot, drop.get(j-n-nVehicles));
                    else dist[i][j] = 0;
                }
            }
            else if (i < nVehicles+n) {
                for (int j = 0; j < size; j++) {
                    if (j < nVehicles) dist[i][j] = distance(pickup.get(i-nVehicles), depot);
                    else if (j < nVehicles+n) dist[i][j] = distance(pickup.get(i-nVehicles), pickup.get(j-nVehicles));
                    else  if (j < nVehicles+2*n) dist[i][j] = distance(pickup.get(i-nVehicles), drop.get(j-n-nVehicles));
                    else dist[i][j] = distance(pickup.get(i-nVehicles), depot);
                }
            }
            else if (i < nVehicles+2*n){
                for (int j = 0; j < size; j++) {
                    if (j < nVehicles) dist[i][j] = distance(drop.get(i-n-nVehicles), depot);
                    else if (j < nVehicles+n) dist[i][j] = distance(drop.get(i-n-nVehicles), pickup.get(j-nVehicles));
                    else  if (j < nVehicles+2*n) dist[i][j] = distance(drop.get(i-n-nVehicles), drop.get(j-n-nVehicles));
                    else dist[i][j] = distance(drop.get(i-n-nVehicles), depot);
                }
            }
            else {
                for (int j = 0; j < 2*n+2; j++) {
                    if (j < nVehicles) dist[i][j] = 0;
                    else if (j < nVehicles+n) dist[i][j] = distance(depot, pickup.get(j-nVehicles));
                    else if (j < nVehicles+2*n) dist[i][j] = distance(depot, drop.get(j-n-nVehicles));
                    else dist[i][j] = 0;
                }
            }
        }

        return dist;
    }

    private static void lns(Solver cp, IntVar[] succ, IntVar totalDist, IntVar[] rideID, int size, int n, int nVehicles, int vehicleCapacity, int maxRideTime,
                            int maxRouteDuration, ArrayList<RideStop> pickupRideStops, ArrayList<RideStop> dropRideStops, RideStop depot) {
        if (debug) System.out.print("Starting LNS - ");
        if (debug) printDuration();
        acc = 0;

        Objective obj = cp.minimize(totalDist);

        //------LNS------

        DFSearch dfs = makeDfs(cp, () -> {

            IntVar[] xss = new IntVar[nVehicles];

            for (int i = 0; i < nVehicles; i++) {
                xss[i] = succ[i];
                while (xss[i].isBound() && xss[i].max() >= nVehicles) xss[i] = succ[xss[i].min()];
                if (xss[i].max() < nVehicles) xss[i] = null;
            }

            boolean allnull = true;
            for (IntVar iv : xss) allnull &= iv == null;
            if (allnull) return EMPTY;

            IntVar xs1 = null;
            int bestIDX = -1;
            for (IntVar xs : xss) {
                if (xs == null) {}
                else {
                    Integer time = Integer.MAX_VALUE;
                    int nextIDX = getNextWindowEnd(xs, pickupRideStops, dropRideStops, nVehicles, time);
                    if (nextIDX != -1) {
                        xs1 = xs;
                        bestIDX = nextIDX;
                    }
                }
            }
            IntVar xs = xs1;

            if (xs == null)
            {
                if (debug) System.out.println("LEAF FOUND");
                return EMPTY;
            }
            else {
                if(!xs.contains(bestIDX)) throw new RuntimeException();
                int finalBestIDX = bestIDX;
                return branch(() -> {
                    cp.post(equal(xs, finalBestIDX));
                }, () -> {
                    cp.post(notEqual(xs,finalBestIDX));
                });
            }
        });

        dfs.onSolution(() -> {
            acc++;
            bestCost = totalDist.min();
            if (debug) System.out.println("Found " + (acc == 1 ? "1 solution": acc + " solutions") + " so far, best is " + bestCost);
            for (int i = 0; i < size; i ++) bestPath[i] = succ[i].min();
            for (int i = 0; i < size; i ++) bestRideID[i] = rideID[i].min();
        });


        int failureLimit = initFailLimit;
        Random rand = new java.util.Random(0);

        for (int i = 0; ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() < maxTime; i++) {
            if (i % 100 == 0 && i > 0) {
                if (debug) System.out.print("Already tried " +  i + " times, failureLimit =  " + failureLimit + " ");
                if (debug) printDuration();
            }

            int finalFailureLimit = failureLimit;
            SearchStatistics stats = dfs.optimizeSubjectTo(obj, statistics -> (((statistics.numberOfFailures() + statistics.numberOfSolutions() - acc) >= finalFailureLimit)
                    || ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() > maxTime
                    ), () -> {
                        // Assign the fragment 5% of the variables randomly chosen
                        for (int j = 0; j < size; j++) {
                            if (rand.nextInt(100) < remplissage) {
                                // after the solveSubjectTo those constraints are removed
                                cp.post(equal(succ[j], bestPath[j]));
                            }
                        }
                    }
            );////
            if (!stats.isCompleted()) failureLimit = Integer.min(maxFailLimit, failureLimit + (1*failureLimit)/5);
            if (stats.isCompleted()) failureLimit = Integer.max(minFailLimit, failureLimit - (1*failureLimit)/10);
        }
        if (debug) System.out.println("Found "+ ((acc <= 1) ? (acc + " solution") : (acc + " solutions")) + " in LNS");
        if (debug) System.out.println(createSolution(nVehicles, pickupRideStops, dropRideStops, depot, vehicleCapacity, maxRideTime, maxRouteDuration));
    }

    private static void cleverSolve(Solver cp, int[][] distMatrix,
                                            int vehicleCapacity, int maxRideTime, int maxRouteDuration, int nVehicles, int n, ArrayList<RideStop> pickupRideStops, ArrayList<RideStop> dropRideStops, RideStop depot) {

        acc = 0;
        int size = 2*n + 2*nVehicles;
        int toEnd = nVehicles+2*n;
        int max = Integer.MIN_VALUE;
        bestPath = new int[size];
        bestRideID = new int[size];
        for (int[] row : distMatrix) for (int e : row) if (e > max) max = e;
        IntVar[] succ = makeIntVarArray(cp, size, size); // Successeurs
        IntVar[] pred = makeIntVarArray(cp, size, size); // Predecesseur
        IntVar[] distSucc = makeIntVarArray(cp, size, max+1); // Distance jusqu'aux successeurs
        IntVar[] distFromStart = new IntVar[size]; // Distance parcourure depuis un depot
        IntVar[] peopleOnBoard = makeIntVarArray(cp, size, vehicleCapacity+1); // Nombre de personnes dans un vehicule
        IntVar[] rideID = makeIntVarArray(cp, size, nVehicles); // ID de la course



        IntVar[] allValue = new IntVar[size];
        for(int i = 0; i<size; i++){
            allValue[i] = makeIntVar(cp, i, i+1);
            cp.post(equal(allValue[i], i));
        }

        for (int i = 0; i < nVehicles; i ++) {
            distFromStart[i] = makeIntVar(cp, 0, 1);
            cp.post(equal(distFromStart[i], 0));
            distFromStart[toEnd+i] = makeIntVar(cp, 0, maxRouteDuration+1);
        }

        for (int i =0; i < n; i++) {
            int p = nVehicles + i; // Point de prise du client i
            int d = nVehicles + n + i; // Point de depot du client i

            distFromStart[p] = makeIntVar(cp, 0, pickupRideStops.get(i).window_end+1);
            distFromStart[d] = makeIntVar(cp, 0, dropRideStops.get(i).window_end+1);
        }

        cp.post(new Circuit(succ));
        cp.post(new Circuit(pred));


        for (int i = 0; i < nVehicles; i++) {
            cp.post(equal(rideID[i], i));
            cp.post(equal(distFromStart[i], 0)); // Imposer que l'on commence au depot
            cp.post(equal(peopleOnBoard[i], 0)); // On a personne au debut de la course
            cp.post(equal(peopleOnBoard[toEnd+i], 0)); // On a plus personne à la fin de la course
            cp.post(isLargerOrEqual(pred[i], toEnd)); // on boucle bien comme il faut
            cp.post(equal(succ[toEnd+i], i)); // on boucle bien comme il faut
            cp.post(isLess(succ[i],toEnd));

            cp.post(new Element1D(distMatrix[i], succ[i], distSucc[i])); // calcul des distances des successeurs
            cp.post(new Element1DVar(distFromStart, succ[i], sum(distFromStart[i],distSucc[i]))); // calcul des temps de passage à chaque point
            cp.post(equal(distSucc[toEnd+i], 0));

            cp.post(new Element1DVar(peopleOnBoard, succ[i], peopleOnBoard[i])); //
            cp.post(new Element1DVar(rideID, pred[toEnd+i], rideID[toEnd+i]));
        }

        for (int i = 0; i < size; i++) {
            cp.post(new Element1DVar(succ, pred[i], allValue[i]));
            cp.post(new Element1DVar(pred, succ[i], allValue[i]));

        }


        for (int i = 0; i < n; i ++) {
            int p = nVehicles + i; // Point de prise du client i
            int d = nVehicles + n + i; // Point de depot du client i

            cp.post(notEqual(succ[d], p));

            cp.post(notEqual(succ[p], p)); // On ne se succede pas a soi-meme
            cp.post(notEqual(succ[d], d)); // On ne se succede pas a soi-meme

            cp.post(new Element1DVar(peopleOnBoard, succ[p], plus(peopleOnBoard[p],1))); // Aux points de prise on prend quelqu'un dans le vehicule
            cp.post(new Element1DVar(peopleOnBoard, succ[d], minus(peopleOnBoard[d],1))); // Aux points de depot on depose quelqu'un du vehicule

            cp.post(equal(rideID[p], rideID[d]));
            cp.post(new Element1DVar(rideID, pred[p], rideID[p])); // On a la meme rideID que ses predecesseurs
            cp.post(new Element1DVar(rideID, pred[d], rideID[d])); // On a la meme rideID que ses predecesseurs

            cp.post(lessOrEqual(distFromStart[p], distFromStart[d])); // on passe d abord prendre un client avant de le deposer
            cp.post(lessOrEqual(distFromStart[d], plus(distFromStart[p],maxRideTime))); // on depose un client en moins de maxRideTime

            cp.post(new Element1D(distMatrix[p], succ[p], distSucc[p])); // Calcul des distance des successeurs
            cp.post(new Element1DVar(distFromStart, succ[p], sum(distFromStart[p],distSucc[p]))); // Calcul des temps de passage à chaque point

            cp.post(new Element1D(distMatrix[d], succ[d], distSucc[d])); // Calcul des distance des successeurs
            cp.post(new Element1DVar(distFromStart, succ[d], sum(distFromStart[d],distSucc[d]))); // Calcul des temps de passage à chaque point
        }


        IntVar totalDist = sum(distSucc);

        Objective obj = cp.minimize(totalDist);

        DFSearch dfs = makeDfs(cp, () -> {
            IntVar[] xss = new IntVar[nVehicles];
            for (int i = 0; i < nVehicles; i++) {
                xss[i] = succ[i];
                while (xss[i].isBound() && xss[i].max() >= nVehicles) xss[i] = succ[xss[i].min()];
                if (xss[i].max() < nVehicles) xss[i] = null;
            }
            boolean allnull = true;
            for (IntVar iv : xss) allnull &= iv == null;
            if (allnull) return EMPTY;
            IntVar xs1 = null;
            int bestIDX = -1;
            for (IntVar xs : xss) {
                if (xs == null) {}
                else {
                    Integer time = Integer.MAX_VALUE;
                    int nextIDX = getNextWindowEnd(xs, pickupRideStops, dropRideStops, nVehicles, time);
                    if (nextIDX != -1) {
                        xs1 = xs;
                        bestIDX = nextIDX;
                    }
                }
            }
            IntVar xs = xs1;

            if (xs == null)
            {
                if (debug) System.out.println("LEAF FOUND");
                return EMPTY;
            }
            else {
                if(!xs.contains(bestIDX)) throw new RuntimeException();
                int finalBestIDX = bestIDX;
                return branch(() -> {
                    cp.post(equal(xs, finalBestIDX));
                }, () -> {
                    cp.post(notEqual(xs,finalBestIDX));
                });
            }
        });


        dfs.onSolution(() -> {
            acc++;
            bestCost = totalDist.min();
            for (int i = 0; i < size; i ++) bestPath[i] = succ[i].min();
            for (int i = 0; i < size; i ++) bestRideID[i] = rideID[i].min();
        });

        SearchStatistics stats = dfs.optimizeSubjectTo(obj, s -> (s.numberOfSolutions() >= 1 && ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() >= timeLimitClever), () -> {
            for (int i = 0; i < n; i ++) {
                int p = nVehicles + i; // Point de prise du client i
                int d = nVehicles + n + i; // Point de depot du client i
                cp.post(equal(succ[p], d));
                cp.post(equal(pred[d], p));
                cp.post(equal(distFromStart[d], plus(distFromStart[p], distMatrix[p][d])));
            }
        });

        if (debug) System.out.println(stats);
        if (debug) System.out.println(createSolution(nVehicles, pickupRideStops, dropRideStops, depot, vehicleCapacity, maxRideTime, maxRouteDuration));

        lns(cp, succ, totalDist, rideID, size, n, nVehicles, vehicleCapacity, maxRideTime, maxRouteDuration, pickupRideStops, dropRideStops, depot);

    }


    public static DialARideSolution solve(int nVehicles, int maxRouteDuration, int vehicleCapacity,
                                          int maxRideTime, ArrayList<RideStop> pickupRideStops, ArrayList<RideStop> dropRideStops,
                                          RideStop depot) {


        int n = pickupRideStops.size();
        Solver cp = makeSolver(false);




         int[][] distMatrix = distToSuccessors(pickupRideStops, dropRideStops, depot, nVehicles);
        cleverSolve(cp, distMatrix, vehicleCapacity, maxRideTime, maxRouteDuration, nVehicles, n, pickupRideStops, dropRideStops, depot);




        // TODO
        //successor array
        //predecessor array
        //an array that give at a given node wich time it was reach
        //an array that give at each node the number of people that were at the vehicle when it reach the given node
        // Given a series of dial-a-ride request made by single persons (for request i, pickupRideStops[i] gives the spot
        // where the person wants to be taken, and dropRideStops[i] the spot where (s)he would like to be dropped),
        // minimize the total ride time of all the vehicles.
        // You have nVehicles vehicles, each of them can take at most vehicleCapacity person inside at any time.
        // The maximum time a single person can remain in the vehicle is maxRideTime, and the maximum time a single
        // vehicle can be on the road for a single day is maxRouteDuration.
        // all vehicles start at the depot, and end their day at the depot.
        // Each ride stop must be reached before a given time (window_end) by a vehicle.
        // use distance() to compute the distance between two points.
        return createSolution(nVehicles, pickupRideStops, dropRideStops, depot, vehicleCapacity, maxRideTime, maxRouteDuration);
    }

    /**
     * Returns the distance between two ride stops
     */
    public static int distance(RideStop a, RideStop b) {
        return (int) (Math.sqrt((a.pos_x - b.pos_x) * (a.pos_x - b.pos_x) + (a.pos_y - b.pos_y) * (a.pos_y - b.pos_y)) * 100);
    }

    /**
     * A solution. To create one, first do new DialARideSolution, then
     * add, for each vehicle, in order, the pickup/drops with addStop(vehicleIdx, rideIdx, isPickup), where
     * vehicleIdx is an integer beginning at 0 and ending at nVehicles - 1, rideIdx is the id of the ride you (partly)
     * fullfill with this stop (from 0 to pickupRideStops.size()-1) and isPickup a boolean indicate if you are beginning
     * or ending the ride. Do not add the last stop to the depot, it is implicit.
     * <p>
     * You can check the validity of your solution with compute(), which returns the total distance, and raises an
     * exception if something is invalid.
     * <p>
     * DO NOT MODIFY THIS CLASS.
     */
    public static class DialARideSolution {
        public ArrayList<Integer>[] stops;
        public ArrayList<RideStop> pickupRideStops;
        public ArrayList<RideStop> dropRideStops;
        public RideStop depot;
        public int capacity;
        public int maxRideTime;
        public int maxRouteDuration;

        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("Length: ");
            b.append(compute());
            b.append("\n");
            for (int i = 0; i < stops.length; i++) {
                b.append("- ");
                for (int s : stops[i]) {
                    if (s >= pickupRideStops.size()) {
                        b.append(s - pickupRideStops.size());
                        b.append("d, ");
                    } else {
                        b.append(s);
                        b.append("p, ");
                    }
                }
                b.append("\n");
            }
            return b.toString();
        }

        public DialARideSolution(int nVehicles, ArrayList<RideStop> pickupRideStops, ArrayList<RideStop> dropRideStops,
                                 RideStop depot, int vehicleCapacity, int maxRideTime, int maxRouteDuration) {
            stops = new ArrayList[nVehicles];
            for (int i = 0; i < nVehicles; i++)
                stops[i] = new ArrayList<>();

            this.pickupRideStops = pickupRideStops;
            this.dropRideStops = dropRideStops;
            this.depot = depot;
            this.capacity = vehicleCapacity;
            this.maxRideTime = maxRideTime;
            this.maxRouteDuration = maxRouteDuration;
        }

        public void addStop(int vehicleId, int rideId, boolean isPickup) {
            stops[vehicleId].add(rideId + (isPickup ? 0 : pickupRideStops.size()));
        }

        public int compute() {
            int totalLength = 0;
            HashSet<Integer> seenRides = new HashSet<>();

            for (int vehicleId = 0; vehicleId < stops.length; vehicleId++) {
                HashMap<Integer, Integer> inside = new HashMap<>();
                RideStop current = depot;
                int currentLength = 0;
                for (int next : stops[vehicleId]) {
                    RideStop nextStop;
                    if (next < pickupRideStops.size())
                        nextStop = pickupRideStops.get(next);
                    else
                        nextStop = dropRideStops.get(next - pickupRideStops.size());

                    currentLength += distance(current, nextStop);

                    if (next < pickupRideStops.size()) {
                        if (seenRides.contains(next))
                            throw new RuntimeException("Ride stop visited twice");
                        seenRides.add(next);
                        inside.put(next, currentLength);
                    } else {
                        if (!inside.containsKey(next - pickupRideStops.size()))
                            throw new RuntimeException("Drop before pickup");
                        if (inside.get(next - pickupRideStops.size()) + maxRideTime < currentLength)
                            throw new RuntimeException("Ride time too long");
                        inside.remove(next - pickupRideStops.size());
                    }

                    if (currentLength > nextStop.window_end)
                        throw new RuntimeException("Ride stop visited too late");
                    if (inside.size() > capacity)
                        throw new RuntimeException("Above maximum capacity");

                    current = nextStop;
                }

                currentLength += distance(current, depot);

                if (inside.size() > 0)
                    throw new RuntimeException("Passenger never dropped");
                if (currentLength > maxRouteDuration)
                    throw new RuntimeException("Route too long");

                totalLength += currentLength;
            }

            if (seenRides.size() != pickupRideStops.size())
                throw new RuntimeException("Some rides never fulfilled");

            return totalLength;
        }
    }

    static class RideStop {
        public float pos_x;
        public float pos_y;
        public int type; //0 == depot, 1 == pickup, -1 == drop
        public int window_end;
    }

    public static RideStop readRide(InputReader reader) {
        try {
            RideStop r = new RideStop();
            reader.getInt(); //ignored
            r.pos_x = Float.parseFloat(reader.getString());
            r.pos_y = Float.parseFloat(reader.getString());
            reader.getInt(); //ignored
            r.type = reader.getInt();
            reader.getInt(); //ignored
            r.window_end = reader.getInt() * 100;
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        // Reading the data


        InputReader reader;
        if (args.length == 0) {
            reader = new InputReader(filePath);
            debug = true;
        }
        else reader = new InputReader(args[0]);

        int nVehicles = reader.getInt();
        reader.getInt(); //ignore
        int maxRouteDuration = reader.getInt() * 100;
        int vehicleCapacity = reader.getInt();
        int maxRideTime = reader.getInt() * 100;


        RideStop depot = null;
        ArrayList<RideStop> pickupRideStops = new ArrayList<>();
        ArrayList<RideStop> dropRideStops = new ArrayList<>();
        boolean lastWasNotDrop = true;

        while (true) {
            RideStop r = readRide(reader);
            if (r == null)
                break;
            if (r.type == 0) {
                assert depot == null;
                depot = r;
            } else if (r.type == 1) {
                assert lastWasNotDrop;
                pickupRideStops.add(r);
            } else { //r.type == -1
                lastWasNotDrop = false;
                dropRideStops.add(r);
            }
        }
        assert depot != null;
        assert pickupRideStops.size() == dropRideStops.size();

        long startTime = System.currentTimeMillis();

        DialARideSolution sol = solve(nVehicles, maxRouteDuration, vehicleCapacity, maxRideTime, pickupRideStops, dropRideStops, depot);

        long endTime = System.currentTimeMillis();
        if (debug) printDuration(startTime, endTime);
        if (debug) printDuration();
        System.out.println(sol);
    }

    public static void printDuration(long start, long end) {
        long duration = end - start;
        int msec = (int) duration%1000;
        duration = duration/1000;
        int sec = (int) duration%60;
        duration = duration/60;
        int minutes = (int) duration%60;
        duration = duration/60;
        int hours = (int) duration%24;
        int days = (int) duration/24;
        String toPrint = "COMPUTATION TOOK ";
        if (days >0) toPrint+=days + " day(s) ";
        if (hours >0) toPrint+=hours + " hours(s) ";
        if (minutes >0) toPrint+=minutes + " minute(s) ";
        if (sec >0) toPrint+=sec + " second(s) ";
        if (msec >0) toPrint+=msec + " millisecond(s) ";
        toPrint = toPrint.trim();
        System.out.println(toPrint);
    }

    public static void printDuration()  {
        long duration = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        int nsec = (int) duration%1000;
        duration = duration/1000;
        int musec = (int) duration%1000;
        duration = duration/1000;
        int msec = (int) duration%1000;
        duration = duration/1000;
        int sec = (int) duration%60;
        duration = duration/60;
        int minutes = (int) duration%60;
        duration = duration/60;
        int hours = (int) duration%24;
        int days = (int) duration/24;
        String toPrint = "COMPUTATION TOOK ";
        if (days >0) toPrint+=days + " day(s) ";
        if (hours >0) toPrint+=hours + " hours(s) ";
        if (minutes >0) toPrint+=minutes + " minute(s) ";
        if (sec >0) toPrint+=sec + " second(s) ";
        if (msec >0) toPrint+=msec + " millisecond(s) ";
        if (musec >0) toPrint+=musec + " microsecond(s) ";
        if (nsec >0) toPrint+=nsec + " nanosecond(s) ";
        toPrint = toPrint.trim();
        toPrint += " of CPU time";
        toPrint = toPrint.trim();
        System.out.println(toPrint);
    }
}
