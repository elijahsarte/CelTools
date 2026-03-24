package com.elijahsarte.celtools.main.util.structures.collections.point;

import com.elijahsarte.celtools.main.util.structures.collections.IntList;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class PointIterator implements Iterator<Point> {

    private final PointCollection ptColl;
    private int startIndex, index = 0;
    private final Iterator<Map.Entry<Integer, IntList>> ptIt;
    private Map.Entry<Integer, IntList> currEntry;

    public PointIterator(PointCollection ptColl) {
        this.ptColl = ptColl;
        this.ptIt = ptColl.pointMap().entrySet().iterator();
    }

    @Override
    public boolean hasNext() {
        return index < ptColl.size();
    }

    @Override
    public Point next() {
        if (!hasNext()) throw new NoSuchElementException();
        if (this.currEntry == null || ((index - startIndex) >= currEntry.getValue().size())) {
            this.currEntry = ptIt.next();
            this.startIndex = index;
        }
        return new Point(currEntry.getKey(), currEntry.getValue().get(index++ - startIndex));
    }

    @Override
    public void remove() {
        currEntry.getValue().remove(--index - startIndex);
        ptColl.decSize();
        if (currEntry.getValue().isEmpty()) {
            ptIt.remove();
            this.currEntry = null;
        }
    }
    public void removeX() {
        index = startIndex;
        ptColl.addSize(-currEntry.getValue().size());
        ptIt.remove();
        this.currEntry = null;
    }


    @Override
    public void forEachRemaining(Consumer<? super Point> action) {
        Iterator.super.forEachRemaining(action);
    }
}

