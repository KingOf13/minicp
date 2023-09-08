/*
 * mini-cp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License  v3
 * as published by the Free Software Foundation.
 *
 * mini-cp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY.
 * See the GNU Lesser General Public License  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mini-cp. If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 *
 * Copyright (c)  2018. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 */

package minicp.examples;

import minicp.engine.constraints.Circuit;
import minicp.engine.constraints.Element1D;
import minicp.engine.core.IntVar;
import minicp.engine.core.Solver;
import minicp.search.DFSearch;
import minicp.search.Objective;
import minicp.util.Procedure;
import minicp.util.io.InputReader;
import minicp.search.SearchStatistics;

import java.util.concurrent.atomic.AtomicReference;

import static minicp.cp.BranchingScheme.*;
import static minicp.cp.Factory.*;

/**
 * Traveling salesman problem.
 * <a href="https://en.wikipedia.org/wiki/Travelling_salesman_problem">Wikipedia</a>.
 */
public class TSP {
    public static int bestValue = Integer.MAX_VALUE;
    public static int[] bestPath;
    public static int nbSolutionsFound = 0;
    public static int nbBetterSolutionsFound = 0;

    private static int getMin(int[] tab, int index){
        int min = Integer.MAX_VALUE;
        for(int i = 0; i< tab.length; i++){
            if (tab[i] < min && i != index){
                min = tab[i];
            }
        }
        return min;
    }

    private static int getMinIntVar(int[][] distancesTable, IntVar indexes) {
        int[] idx = new int[indexes.size()];
        indexes.fillArray(idx);
        int minValue = Integer.MAX_VALUE;
        for (int i: idx) {
            int currentMin = getMin(distancesTable[i], i);
            if (currentMin < minValue) {
                minValue = currentMin;
            }
        }
        return minValue;
    }

    private static int getMinIntVarIDX(int[][] distancesTable, IntVar indexes) {
        int[] idx = new int[indexes.size()];
        indexes.fillArray(idx);
        int minIDX = -1;
        int minValue = Integer.MAX_VALUE;
        for (int i: idx) {
            int currentMin = getMin(distancesTable[i], i);
            if (currentMin < minValue) {
                minIDX = i;
                minValue = currentMin;
            }
        }
        return minIDX;
    }


    public static void main(String[] args) {

        // instance gr17 https://people.sc.fsu.edu/~jburkardt/datasets/tsp/gr17_d.txt
        InputReader reader = new InputReader("data/tsp.txt");

        int n = reader.getInt();

        bestPath = new int[n];

        int[][] distanceMatrix = reader.getMatrix(n, n);

        Solver cp = makeSolver(false);
        IntVar[] succ = makeIntVarArray(cp, n, n);
        IntVar[] distSucc = makeIntVarArray(cp, n, 1000);

        cp.post(new Circuit(succ));

        for (int i = 0; i < n; i++) {
            succ[i].remove(i);
            cp.post(new Element1D(distanceMatrix[i], succ[i], distSucc[i]));

        }

        IntVar totalDist = sum(distSucc);

        Objective obj = cp.minimize(totalDist);


        DFSearch dfs = makeDfs(cp, () -> {
            IntVar xs = selectMin(succ,
                    xi -> xi.size() > 1,
                    xi -> getMinIntVar(distanceMatrix, xi));

            if (xs == null)
                {
                    return EMPTY;
                }
            else {
                int idx = getMinIntVarIDX(distanceMatrix, xs);
                return branch(() -> cp.post(equal(xs, idx)),
                        () -> cp.post(notEqual(xs,idx)));
            }
        });

        dfs.onFailure(new CountingFailureProcedure(1, true));

        dfs.onSolution(() -> {
            nbSolutionsFound++;
            if (bestValue>totalDist.min() && totalDist.size() == 1) {
                nbBetterSolutionsFound ++;
                System.out.println("Found : " + nbSolutionsFound + ", Better : " + nbBetterSolutionsFound);
                bestValue = totalDist.min();
                for (int i = 0; i < n; i ++) {
                    System.out.println("succ[" + i + "] : " + succ[i]);
                    bestPath[i] = succ[i].min();
                }
                System.out.println("Best solution : " + bestValue);
            }
            else if (totalDist.size() != 1) System.err.println("totalDist not consistent");

        });

        SearchStatistics stats = dfs.optimize(obj);
//        SearchStatistics stats = dfs.optimize(obj, s -> s.numberOfSolutions() == 1);
        System.out.println(stats);
        for (int i = 0; i < succ.length; i++) System.out.println("best[" + i + "] : " + bestPath[i]);

    }

    public static class CountingFailureProcedure implements Procedure {
        public long fail = 0;
        private long mod = 1000;
        private boolean affiche = false;

        public CountingFailureProcedure(long mod, boolean affiche) {
            super();
            this.mod = mod;
        }

        public CountingFailureProcedure(long mod) {
            super();
            this.mod = mod;
        }

        public CountingFailureProcedure(boolean affiche) {
            super();
            this.affiche = affiche;
        }

        public CountingFailureProcedure() {
            super();
        }
        @Override
        public void call() {
            fail++;
            if (affiche) System.out.println("Failure detected " + fail);
        }
    }


}
