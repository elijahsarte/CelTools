package com.elijahsarte.celtools.main.util.structures.collections.point;

import com.elijahsarte.celtools.main.util.datastructures.EnhancedTreeMap;
import com.elijahsarte.celtools.main.util.structures.collections.IntList;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class PointIterator implements Iterator<Point> {

    private final PointCollection ptColl;
    private int startIndex, index = 0;
    private boolean raw = false;
    private final EnhancedTreeMap<Integer, IntList>.BidirectionalEntryIterator ptIt;
    private Map.Entry<Integer, IntList> currEntry;
    private Point previous;
    private Map.Entry<Integer, IntList> previousEntry;

    public PointIterator(PointCollection ptColl) {
        this.ptColl = ptColl;
        this.ptIt = ptColl.pointMap().entrySet().iterator();
    }

    @Override
    public boolean hasNext() {
        return index < ptColl.size();
    }

    public boolean hasPrevious() {
        return previous != null || previousEntry != null;
    }

    public Point peekNext() {
        if (!hasNext()) {
            throw new NoSuchElementException("No next point exists.");
        }

        if (currEntry != null) {
            int offset = index - startIndex;
            if (offset < currEntry.getValue().size()) {
                return new Point(currEntry.getKey(), currEntry.getValue().get(offset));
            }
        }

        Map.Entry<Integer, IntList> nextEntry = ptIt.peekNext();
        return new Point(nextEntry.getKey(), nextEntry.getValue().get(0));
    }

    public Point peekPrevious() {
        if (previous == null) {
            throw new NoSuchElementException("No previous point exists.");
        }
        return previous;
    }

    public Map.Entry<Integer, IntList> peekNextEntry() {
        if (!raw) {
            ptColl.rawCheck();
        }
        return ptIt.peekNext();
    }

    public Map.Entry<Integer, IntList> peekPreviousEntry() {
        if (previousEntry == null) {
            throw new NoSuchElementException("No previous entry exists.");
        }
        return previousEntry;
    }

    @Override
    public Point next() {
        if (!hasNext()) throw new NoSuchElementException();

        if (this.currEntry == null || ((index - startIndex) >= currEntry.getValue().size())) {
            this.currEntry = ptIt.next();
            this.startIndex = index;
            this.previousEntry = currEntry;
        }

        return (this.previous = new Point(
                currEntry.getKey(),
                currEntry.getValue().get(index++ - startIndex)
        ));
    }

    public Map.Entry<Integer, IntList> nextEntry() {
        if (!this.raw) ptColl.rawCheck();
        this.raw = true;
        this.currEntry = ptIt.next();
        this.startIndex = index;
        this.index += currEntry.getValue().size();
        return (this.previousEntry = currEntry);
    }

    public Point previous() {
        if (previous == null)
            throw new NoSuchElementException("No previous point exists.");

        return previous;
    }

    public Map.Entry<Integer, IntList> previousEntry() {
        if (previousEntry == null)
            throw new NoSuchElementException("No previous entry exists.");

        return previousEntry;
    }

    @Override
    public void remove() {
        currEntry.getValue().remove(--index - startIndex);
        ptColl.recordRemoval(previous);
        if (currEntry.getValue().isEmpty()) {
            ptIt.remove();
            this.currEntry = null;
        }
    }

    public void removeX() {
        index = startIndex;
        ptColl.recordColumnRemoval(currEntry.getKey(), currEntry.getValue());
        ptIt.remove();
        this.currEntry = null;
    }

    @Override
    public void forEachRemaining(Consumer<? super Point> action) {
        Iterator.super.forEachRemaining(action);
    }
}
