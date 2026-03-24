package com.elijahsarte.celtools.main.util.math;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;

import java.io.IOException;
import java.util.*;

import static com.elijahsarte.celtools.main.util.CollectionsEx.*;
import static com.elijahsarte.celtools.main.util.StreamEx.*;

public class SimpsonArea {

    private final int heightDistance;


    public SimpsonArea(int heightDistance) throws IOException {
        if (heightDistance <= 1) {
            throw new IOException("Cannot measure between intervals of 1");
        }
        this.heightDistance = heightDistance;
    }

    public double calculate(PointCollection points) {
        List<Integer> pointCols = new ArrayList<>(points.xes());
        List<Integer> heights = toArrayList(indexFilter(pointCols.stream().skip(1), i -> i % this.heightDistance == 0)
                .map(points::getYesAtX)
                .map(i -> i.last() - i.first()));

        int firstH = heights.get(0), lastH = CollectionsEx.lastElem(heights);
        listSlice(heights, 1); listSlice(heights, -1);

        List<Integer> oddHeights = oddIndices(heights), evenHeights = evenIndices(heights);
        return ProgrammingEx.varOper(
                MathEx.divide(MathEx.divide(
                                pointCols.get(pointCols.size() - 2) - pointCols.get(1),
                                oddHeights.size() + evenHeights.size() + 1),
                        3),
                (ratio) -> ratio * (double) (firstH + lastH + (4 * (unboxInt(oddHeights).sum())) + (2 * (unboxInt(evenHeights).sum())))
        );

    }
}

