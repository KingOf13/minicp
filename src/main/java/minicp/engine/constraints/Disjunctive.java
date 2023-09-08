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


package minicp.engine.constraints;

import minicp.cp.Factory;
import minicp.engine.core.AbstractConstraint;
import minicp.engine.core.BoolVar;
import minicp.engine.core.IntVar;
import minicp.util.exception.InconsistencyException;
import minicp.util.exception.NotImplementedException;

import java.util.*;

import static minicp.cp.Factory.*;

/**
 * Disjunctive Scheduling Constraint:
 * Any two pairs of activities cannot overlap in time.
 */
public class Disjunctive extends AbstractConstraint {

    private final IntVar[] start;
    private final int[] duration;
    private final IntVar[] end;
    private final boolean postMirror;
    private final Integer[] IDX;

    

    /**
     * Creates a disjunctive constraint that enforces
     * that for any two pair i,j of activities we have
     * {@code start[i]+duration[i] <= start[j] or start[j]+duration[j] <= start[i]}.
     *
     * @param start the start times of the activities
     * @param duration the durations of the activities
     */
    public Disjunctive(IntVar[] start, int[] duration) {
        this(start, duration, true);
    }


    private Disjunctive(IntVar[] start, int[] duration, boolean postMirror) {
        super(start[0].getSolver());
        this.start = start;
        this.duration = duration;
        this.end = Factory.makeIntVarArray(start.length, i -> plus(start[i], duration[i]));
        this.postMirror = postMirror;
        IDX = new Integer[start.length];
        for (int i = 0; i < start.length; i++) IDX[i] = i;
    }


    @Override
    public void post() {
/*
        int[] demands = new int[start.length];
        for (int i = 0; i < start.length; i++) {
            demands[i] = 1;
        }
        getSolver().post(new Cumulative(start, duration, demands, 1), false);*/

        // TODO 1: replace by  posting  binary decomposition using IsLessOrEqualVar
        BoolVar[][] bij = new BoolVar[start.length][start.length];
        BoolVar[][] bji = new BoolVar[start.length][start.length];

        for (int i = 0; i < start.length; i++) {
            for (int j = i+1; j < start.length; j++) {
                bij[i][j] = makeBoolVar(getSolver());
                bji[i][j] = makeBoolVar(getSolver());
            }
        }

        for (int i = 0; i < start.length; i++) {
            for (int j = i+1; j < start.length; j++) {
                getSolver().post(new IsLessOrEqualVar(bij[i][j], end[i], start[j]));
                getSolver().post(new IsLessOrEqualVar(bji[i][j], end[j], start[i]));
                getSolver().post(new NotEqual(bij[i][j],bji[i][j]));
            }
        }


        // TODO 2: add the mirror filtering as done in the Cumulative Constraint
        if (postMirror) {
            IntVar[] startMirror = Factory.makeIntVarArray(start.length, i -> minus(end[i]));
            getSolver().post(new Disjunctive(startMirror, duration, false), false);
        }

    }

    @Override
    public void propagate() {
        // HINT: for the TODO 1-4 you'll need the ThetaTree data-structure

        // TODO 3: add the OverLoadCheck algorithms


        // TODO 4: add the Detectable Precedences algorithm


        // TODO 5: add the Not-Last algorithm

        // TODO 6 (optional, for a bonus): implement the Lambda-Theta tree and implement the Edge-Finding        overLoadChecker();

        boolean fixed = false;
        while (!fixed) {
            fixed = true;
            overLoadChecker();
            fixed =  fixed && !detectablePrecedence();
            fixed =  fixed && !notLast();
        }

    }

    private ArrayList<Integer> leftCut(int lct) {
        ArrayList<Integer> returnArray = new ArrayList();
        for (int i = 0; i < end.length; i++) {
            if (end[i].max() <= lct) returnArray.add(i);
        }
        return returnArray;
    }


    private void overLoadChecker() {
        for (int j = 0; j < start.length; j ++) {
             int lct = end[j].max();
            ArrayList<Integer> lc = leftCut(lct);
            ThetaTree theta = new ThetaTree(lc.size());
            for (int i : lc) {
                theta.insert(i, end[i].min(), duration[i]);
            }
            if (theta.getECT() > lct) throw new InconsistencyException();
        }
    }

    /**
     * @return true if one domain was changed by the detectable precedence algo
     */
    private boolean detectablePrecedence() {
        Integer[] tLSTArr = IDX.clone();
        Arrays.sort(tLSTArr, new Comparator<Integer>() {
            @Override public int compare(final Integer o1, final Integer o2) {
                return Integer.compare(start[o1].max(), start[o2].max());
            }
        });
        List<Integer> tLST = Arrays.asList(tLSTArr);
        Integer[] tECTArr = IDX.clone();
        Arrays.sort(tECTArr, new Comparator<Integer>() {
            @Override public int compare(final Integer o1, final Integer o2) {
                return Integer.compare(end[o1].min(), end[o2].min());
            }
        });
        List<Integer> tECT = Arrays.asList(tECTArr);

        Iterator<Integer> ite = tLST.iterator();
        int j = ite.next();
        ThetaTree theta = new ThetaTree(start.length);
        int[] estprime = new int[start.length];
        for (int i : tECT) {
            while (end[i].min() > start[j].max()) {
                theta.insert(j, end[j].min(), duration[j]);
                if (ite.hasNext()) j = ite.next();
                else break;
            }
            //estprime[i] =

        }

        throw new NotImplementedException("Disjunctive");
    }

    /**
     * @return true if one domain was changed by the not-last algo
     */
    private boolean notLast() {
         throw new NotImplementedException("Disjunctive");
    }


}
