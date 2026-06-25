package com.elijahsarte.celtools.main.util.structures.bounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class IntegerBounds {

    private int lowerBound;
    private int upperBound;

    public IntegerBounds(int bd1, int bd2) {
        this.lowerBound = Math.min(bd1, bd2);
        this.upperBound = Math.max(bd1, bd2);
    }

    public IntegerBounds(List<Integer> vals) {
        Collections.sort(vals);
        this.lowerBound = vals.get(0);
        this.upperBound = vals.get(vals.size() - 1);
    }

    public boolean inBetween(double val) {
        return val > lowerBound && val < upperBound;
    }

    public boolean inBetweenClosed(double val) {
        return val >= lowerBound && val <= upperBound;
    }

    public int gap(IntegerBounds other) {
        if (this.getUpperBound() < other.getLowerBound()) return other.getLowerBound() - this.getUpperBound();
        if (other.getUpperBound() < this.getLowerBound()) return this.getLowerBound() - other.getUpperBound();
        return 0;
    }
    public IntegerBounds shift(int dx) {
        return new IntegerBounds(lowerBound + dx, upperBound + dx);
    }


    public int getLength() {
        return this.upperBound - this.lowerBound;
    }

    public int getLengthInc() {
        return getLength() + 1;
    }


    public List<Integer> getSequence() {
        List<Integer> assembledSeq = new ArrayList<>();
        IntStream.rangeClosed(this.lowerBound, this.upperBound).forEach(assembledSeq::add);
        return assembledSeq;
    }


    public int getLowerBound() {
        return this.lowerBound;
    }
    public int getUpperBound() {
        return this.upperBound;
    }

    public void setLowerBound(int lowerBound) {
        if (upperBound < lowerBound) {
            this.lowerBound = upperBound;
            this.upperBound = lowerBound;
        } else {
            this.lowerBound = lowerBound;
        }
    }

    public void setUpperBound(int upperBound) {
        if (lowerBound > upperBound) {
            this.lowerBound = upperBound;
            this.upperBound = lowerBound;
        } else {
            this.upperBound = upperBound;
        }
    }

    public void setBounds(int lowerBound, int upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public void introduceBound(int bd) {
        if (bd < this.lowerBound) this.lowerBound = bd;
        if (bd > this.upperBound) this.upperBound = bd;
    }

    public int incLowerBound() {
        this.lowerBound++;
        return this.lowerBound;
    }
    public int incUpperBound() {
        this.upperBound++;
        return this.upperBound;
    }
    public int decLowerBound() {
        this.lowerBound--;
        return this.lowerBound;
    }
    public int decUpperBound() {
        this.upperBound--;
        return this.upperBound;
    }

    public void forEach(Consumer<? super Integer> fn) {
        for (int c = lowerBound; c <= upperBound; c++) fn.accept(c);
    }

    public Stream<Integer> stream() {
        return IntStream.rangeClosed(getLowerBound(), getUpperBound()).boxed();
    }
    public Stream<Integer> streamBds() {
        return Stream.of(getLowerBound(), getUpperBound());
    }

    @Override
    public Object clone() {
        return new IntegerBounds(lowerBound, upperBound);
    }
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IntegerBounds bds)) return false;
        return bds.getLowerBound() == getLowerBound() && bds.getUpperBound() == getUpperBound();
    }
    public boolean equals(int l, int u) {
        return getLowerBound() == l && getUpperBound() == u;
    }

    @Override
    public String toString() {
        return "[" + lowerBound + ", " + upperBound + "]";
    }

    // Newly added methods
    public List<Integer> asList() {
        return List.of(lowerBound, upperBound);
    }

}
