package minicp.util;

import minicp.engine.core.IntVar;
import org.xcsp.common.IVar;

public class StoreValue {
    private int finalSpan;
    private int[] finalStarts;

    public StoreValue (int len) {
        this.finalSpan = -1;
        this.finalStarts = new int[len];
    }

    public void update(int span, IntVar[] start) {
        finalSpan = span;
        for (int i=0; i<start.length; i++) {
            finalStarts[i] = start[i].min();
        }
        System.out.println("Final value updated");
    }

    public String toString () {
        if (finalSpan==-1)
            return "NO SOLUTION FOUND";
        else {
            String ts = "Final Span : " + finalSpan+"\nFinal start : ";
            for (int i : finalStarts) {
                ts += i + " ";
            }
            return ts;
        }
    }
}
