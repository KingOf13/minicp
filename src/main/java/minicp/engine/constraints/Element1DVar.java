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

import minicp.engine.core.AbstractConstraint;
import minicp.engine.core.IntVar;
import static java.lang.Integer.min;
import static java.lang.Math.max;
/**
 *
 * Element Constraint modeling {@code array[y] = z}
 *
 */
public class Element1DVar extends AbstractConstraint {

    private final IntVar[] array;
    private final IntVar y;
    private final IntVar z;


    /**
     * Creates an element constraint {@code array[y] = z}
     * @param array the array to index
     * @param y the index variable
     * @param z the result variable
     */
    public Element1DVar(IntVar[] array, IntVar y, IntVar z) {
        super(y.getSolver());
        this.array = array;
        this.y = y;
        this.z = z;

        
    }

    @Override
    public void post() {
        y.removeBelow(0);
        y.removeAbove(array.length-1);

        y.propagateOnDomainChange(this);
        z.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        int[] yList = new int[y.size()];
        y.fillArray(yList);

        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (int i : yList){
            if (array[i].max() < z.min() || array[i].min() > z.max()){
                y.remove(i);
            }
            max = max(max, array[i].max());
            min = min(min, array[i].min());
            array[i].propagateOnBoundChange(this);
        }
        z.removeBelow(min);
        z.removeAbove(max);

        yList = new int[y.size()];
        y.fillArray(yList);

         max = Integer.MIN_VALUE;
         min = Integer.MAX_VALUE;


        for (int i : yList){
            int j = i;
            if(y.size() == 1) { setActive(false); y.getSolver().post(new Equal(array[y.min()],z)); return; }
            if (array[i].max() < z.min() || array[i].min() > z.max()){
                y.remove(i);
            }
            max = max(max, array[i].max());
            min = min(min, array[i].min());
        }
        z.removeBelow(min);
        z.removeAbove(max);
    }

}