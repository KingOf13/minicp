package minicp.search;


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
public class LastConflictBis implements Supplier<Procedure[]> {

    private IntVar lastConflict;
    private Stack<IntVar> lastConflicts;
    private final Supplier<IntVar> variableSelector;
    private final Function<IntVar, Integer> valueSelector;


    public LastConflictBis(Supplier<IntVar> variableSelector, Function<IntVar, Integer> valueSelector) {
        this.variableSelector = variableSelector;
        this.valueSelector = valueSelector;
        this.lastConflict = null;
        this.lastConflicts = new Stack<IntVar>();
    }

    @Override
    public Procedure[] get() {

        IntVar xs;
        if (lastConflict != null && !lastConflict.isBound()){
            xs = lastConflict;
        }
        else{
            xs = variableSelector.get();
        }

        if (xs == null) return EMPTY;
        if (xs.size() < 1) {
            lastConflict = xs;
            throw new InconsistencyException();
        }
        int v = valueSelector.apply(xs);
        return branch(
                () -> equal(xs, v),
                () -> notEqual(xs, v));

    }

}