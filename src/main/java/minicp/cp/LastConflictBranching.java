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

package minicp.cp;


import minicp.engine.core.IntVar;
import minicp.util.Procedure;
import minicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.Stack;
import java.util.function.Function;
import java.util.function.Supplier;

import static minicp.cp.BranchingScheme.EMPTY;
import static minicp.cp.BranchingScheme.branch;
import static minicp.cp.Factory.equal;
import static minicp.cp.Factory.notEqual;

/**
 * Branching combinator
 * that ensures that that the alternatives created are always within the
 * discrepancy limit.
 * The discrepancy of an alternative generated
 * for a given node is the distance from the left most alternative.
 * The discrepancy of a node is the sum of the discrepancy of its ancestors.
 */
public class LastConflictBranching implements Supplier<Procedure[]> {

    private IntVar lastConflict;
    private Stack<IntVar> lastConflicts;
    private final Function<IntVar, Integer> valSelect;
    private final Supplier<IntVar> varSelect;

    public LastConflictBranching(Supplier<IntVar> variableSelector, Function<IntVar, Integer> valueSelector) {
        this.varSelect = variableSelector;
        this.valSelect = valueSelector;
        this.lastConflict = null;
        this.lastConflicts = new Stack<IntVar>();
    }

    @Override
    public Procedure[] get() {

        IntVar xs = nextVar(varSelect);
        if (xs == null) return EMPTY;
        if (xs.size() < 1) {
//            lastConflicts.push(xs);
            lastConflict = xs;
            throw new InconsistencyException();
        }
        int v = valSelect.apply(xs);
        return branch(
                () -> equal(xs, v),
                () -> notEqual(xs, v));

    }

    public IntVar nextVar(Supplier<IntVar> variableSelector){
//        IntVar last = null;
//        while (!lastConflicts.empty()){
//            last = lastConflicts.pop();
//            if (last != null) return last;
//        }
        if (lastConflict != null && !lastConflict.isBound()){
            return lastConflict;
        }
        return variableSelector.get();
    }
}