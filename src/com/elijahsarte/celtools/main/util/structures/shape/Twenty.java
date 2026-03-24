package com.elijahsarte.celtools.main.util.structures.shape;

import com.elijahsarte.celtools.main.util.structures.collections.IntList;

public class Twenty {

    private final IntList candidates;
    private int fill = 0;
    private boolean marked = false;

    public Twenty(IntList candidates, int fill) {
        this.candidates = candidates;
        this.fill = fill;
    }
    public Twenty(IntList candidates) {
        this(candidates, 0);
    }

    public void fill(int fill) {
        this.fill = fill;
    }
    public void mark() {
        this.marked = true;
    }
    public int fill() {
        return this.fill;
    }
    public boolean marked() {
        return this.marked;
    }
    public IntList get() {
        return this.candidates;
    }

}

