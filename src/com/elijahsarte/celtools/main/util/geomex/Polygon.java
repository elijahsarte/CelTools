package com.elijahsarte.celtools.main.util.geomex;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.function.fnvals.BiTuple;
import com.elijahsarte.celtools.main.util.structures.collections.lazy.LazyList;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.typeex.nullable.NullableBoolean;
import com.elijahsarte.celtools.main.util.typeex.nullable.NullableInt;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.elijahsarte.celtools.main.util.CollectionsEx.lastElem;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;

public class Polygon {

    // note: guaranteed to be sorted due to pointcollection
    private List<Line> bottom, top;
    // information initially generated but then cached for later use
    private final NullableInt topY = new NullableInt(), bottomY = new NullableInt(),
            leftX = new NullableInt(), rightX = new NullableInt();

    public Polygon(List<Line> bottom, List<Line> top) {
        this.bottom = bottom;
        this.top = top;
    }

    public static Polygon of(PointCollection pts) {
        List<Line> bottom = new ArrayList<>(), top = new ArrayList<>();
        AtomicReference<Point> lastTop = new AtomicReference<>(), lastBottom = new AtomicReference<>();
        pts.onRaw();
        pts.forEachRaw((x, yes) -> {
            Point topPt = new Point(x, yes.first());
            if (x == pts.firstX()) {
                lastTop.set(topPt);
                lastBottom.set(new Point(x, yes.last()));
                return;
            }

            Line topLine = new Line(lastTop.get(), topPt);
            boolean last = x == pts.lastX();
            if (yes.size() == 1) {
                if (last) {
                    top.add(topLine);
                    bottom.add(new Line(lastBottom.get(), topPt));
                    return;
                }
                if (lastTop.get().y == lastBottom.get().y) {
                    if (topPt.y < lastTop.get().y) {
                        top.add(topLine);
                        lastTop.set(topPt);
                    } else {
                        bottom.add(topLine);
                        lastBottom.set(topPt);
                    }
                }
                else if (MathEx.closerB(yes.first(), lastTop.get().y, lastBottom.get().y)) {
                    top.add(topLine);
                    lastTop.set(topPt);
                } else {
                    bottom.add(new Line(lastBottom.get(), topPt));
                    lastBottom.set(topPt);
                }
                return;
            }

            Point hereBottom = new Point(x, yes.last());
            top.add(topLine);
            bottom.add(new Line(lastBottom.get(), hereBottom));
            lastTop.set(topPt);
            lastBottom.set(hereBottom);
        });
        return new Polygon(top, bottom);

    }
    // inside Polygon.java
/*
    public static Polygon of(com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection pts) {
        if (pts == null || pts.size() < 2) {
            throw new IllegalArgumentException("PointCollection too small");
        }

        java.util.List<Line> top = new java.util.ArrayList<Line>();
        java.util.List<Line> bottom = new java.util.ArrayList<Line>();

        java.awt.Point prevTop = null;
        java.awt.Point prevBottom = null;
        boolean giveSingletonToTop = true;

        for (int x : pts.xes()) { // xes() is sorted by contract
            com.elijahsarte.celtools.main.util.structures.collections.IntList ys = pts.getYesAtX(x);
            if (ys == null || ys.size() == 0) continue;

            int yMin = ys.first(); // least element by your definition
            int yMax = ys.last();  // greatest element

            if (yMin == yMax) {
                java.awt.Point p = new java.awt.Point(x, yMin);

                if (giveSingletonToTop) {
                    if (prevTop != null && !prevTop.equals(p)) {
                        top.add(new Line(prevTop, p));
                    }
                    prevTop = p;
                } else {
                    if (prevBottom != null && !prevBottom.equals(p)) {
                        bottom.add(new Line(prevBottom, p));
                    }
                    prevBottom = p;
                }
                giveSingletonToTop = !giveSingletonToTop; // prevent identical first/last edges
            } else {
                java.awt.Point pTop = new java.awt.Point(x, yMin);
                java.awt.Point pBottom = new java.awt.Point(x, yMax);

                if (prevTop != null && !prevTop.equals(pTop)) {
                    top.add(new Line(prevTop, pTop));
                }
                prevTop = pTop;

                if (prevBottom != null && !prevBottom.equals(pBottom)) {
                    bottom.add(new Line(prevBottom, pBottom));
                }
                prevBottom = pBottom;
            }
        }

        return new Polygon(top, bottom);
//        this.top = top;
        this.bottom = bottom;
    }*/


    public static Polygon of(List<Line> lines) {
        
        List<Line> top = new ArrayList<>(), bottom = new ArrayList<>();
        List<Line> currGrp = new ArrayList<>(lines.size());
        
        AtomicInteger lowestY = new AtomicInteger(Integer.MAX_VALUE), highestY = new AtomicInteger(Integer.MIN_VALUE);
        lines.stream().sorted(Comparator.comparingInt((Line l) -> l.start().x)
                .thenComparingInt(l -> l.start().y)).filter(Predicate.not(Line::onlyVert)).forEach(l -> {
            if (currGrp.isEmpty()) {
                currGrp.add(l);
                lowestY.set(l.lowest().y);
                highestY.set(l.highest().y);
                return;
            }
            if (top.isEmpty() && bottom.isEmpty()) {
                if (l.lowest().y < lowestY.get()) lowestY.set(l.lowest().y);
                if (l.highest().y > highestY.get()) highestY.set(l.highest().y);
                
                if (lastElem(currGrp).end().equals(l.start())) {
                    currGrp.add(l);
                    return;
                }
                currGrp.stream().filter(lL -> lL.xBounded(l)).findFirst().ifPresentOrElse(
                        lL -> {
                            if (lL.below(l)) {
                                top.addAll(currGrp);
                                bottom.add(l);
                            }
                            else {
                                bottom.addAll(currGrp);
                                top.add(l);
                            }
                            currGrp.clear();
                        },
                        () -> {
                            if (l.highest().y > highestY.get() || l.lowest().y > lowestY.get()) {
                                bottom.addAll(currGrp);
                                top.add(l);
                                currGrp.clear();
                            } else if (l.lowest().y < lowestY.get() || l.highest().y < highestY.get()) {
                                top.addAll(currGrp);
                                bottom.add(l);
                                currGrp.clear();
                            } else {
                                currGrp.add(l);
                            }
                        }
                );
                return;
            }

            if (lastElem(top).end().equals(l.start())) {
                top.add(l);
                return;
            }
            if (lastElem(bottom).end().equals(l.start())) {
                bottom.add(l);
            }
            
        });
        if (!currGrp.isEmpty()) {
            if (currGrp.size() != 1) {
                throw new RuntimeException("Debug this later");
            }
            Line startLine = currGrp.get(0);
            if (lastElem(bottom).endX() == startLine.startX()) bottom.add(startLine);
            else top.add(startLine);
//            throw new RuntimeException("Debug this later");
        }
        return new Polygon(top, bottom);
    }
    public static Polygon of(Line... lines) {
        return of(List.of(lines));
    }

    public static Polygon example() {
        // Nine-sided simple, irregular polygon (clockwise order)
        return Polygon.of(new PointCollection(Arrays.asList(
                new java.awt.Point(10, 40),   // start (left)
                new java.awt.Point(35, 22),   // top 1
                new java.awt.Point(70, 28),   // top 2
                new java.awt.Point(105, 24),  // top 3
                new java.awt.Point(130, 38),  // top 4 (right)
                new java.awt.Point(110, 82),  // bottom 1
                new java.awt.Point(70, 96),   // bottom 2
                new java.awt.Point(25, 75)    // bottom 3, then closes back to start = bottom 4
        )));
    }


    public void expand(int expX, int expY) {
        if (expX != 0) {
            varExec(bottom.get(0), l0 -> this.bottom.set(0, new Line(varMutate(l0.start(), p -> p.translate(-expX, 0)), l0.end())));
            varExec(lastElem(bottom), lf -> this.bottom.set(bottom.size() - 1, new Line(lf.start(), varMutate(lf.end(), p -> p.translate(expX, 0)))));
            varExec(top.get(0), l0 -> this.top.set(0, new Line(varMutate(l0.start(), p -> p.translate(-expX, 0)), l0.end())));
            varExec(lastElem(top), lf -> this.top.set(top.size() - 1, new Line(lf.start(), varMutate(lf.end(), p -> p.translate(expX, 0)))));
        }
        this.bottom = new LazyList<>(this.bottom, (l -> new Line(new Point(l.start().x, l.start().y - expY), new Point(l.end().x, l.end().y - expY))));
        this.top = new LazyList<>(this.top, (l -> new Line(new Point(l.start().x, l.start().y + expX), new Point(l.end().x, l.end().y + expY))));
    }
    public void expand(int expFactor) {
        expand(expFactor, expFactor);
    }


    public List<Point> pts() {
        return varMutate(new ArrayList<>(), pts -> multiExec(
                    list -> list.stream().map(l -> new BiTuple<>(l.start(), l.end())).flatMap(b -> Stream.of(b.first(), b.second())).forEach(pts::add),
                    top, bottom
        ));
    }
    public int leftX() {
        if (!leftX.isSet()) leftX.set(Math.min(bottom.get(0).start().x, top.get(0).start().x));
        return leftX.get();
    }
    public int midX() {
        return MathEx.midpoint(leftX(), rightX());
    }
    public int rightX() {
        if (!rightX.isSet()) rightX.set(Math.max(lastElem(bottom).end().x, lastElem(top).end().x));
        return rightX.get();
    }
    public int width() {
        return rightX() - leftX();
    }
    public int height() {
        return topY() - bottomY();
    }
    public int topY() {
        if (!topY.isSet()) topY.set(top.stream().map(l -> l.highest().y).max(Comparator.naturalOrder()).orElseThrow());
        return topY.get();
    }
    public int bottomY() {
        if (!bottomY.isSet()) bottomY.set(bottom.stream().map(l -> l.lowest().y).min(Comparator.naturalOrder()).orElseThrow());
        return bottomY.get();
    }
    public int midY() {
        return MathEx.midpoint(topY(), bottomY());
    }
    public boolean containsX(int x) {
        return x >= leftX() && x <= rightX();
    }
    public boolean containsX(Point pt) {
        return containsX(pt.x);
    }

    public void cache() {
        leftX(); rightX(); topY(); bottomY();
    }

    private int probableIndex(List<Line> list, int x) {
        return MathEx.roundInt(MathEx.divide(x, MathEx.divide(leftX() - rightX(), list.size() - 1)));
    }
    public boolean contains(Point pt, boolean inclusive) {
        if (!containsX(pt)) return false;

        NullableBoolean aboveBottom = new NullableBoolean(), belowTop = new NullableBoolean();
        OptionalEx.ofCond(CollectionsEx.binarySearchLine(bottom, pt.x, probableIndex(bottom, pt.x)), i -> i != -1)
                .then(bottom::get)
                .thenRun(b -> aboveBottom.set(b.above(pt) || (inclusive && b.on(pt))));
        OptionalEx.ofCond(CollectionsEx.binarySearchLine(top, pt.x, probableIndex(top, pt.x)), i -> i != -1)
                .then(top::get)
                .thenRun(b -> belowTop.set(b.below(pt) || (inclusive && b.on(pt))));
        return aboveBottom.isTrue() && belowTop.isTrue();
    }
    public boolean contains(Point pt) {
        return contains(pt, true);
    }
    public boolean inside(Point pt) {
        return contains(pt, false);
    }



    public List<Line> top() {
        return Collections.unmodifiableList(top);
    }
    public List<Line> bottom() {
        return Collections.unmodifiableList(bottom);
    }

    @Override
    public int hashCode() {
        return (top.hashCode() ^ bottom.hashCode()) * (top.size() / bottom.size()) + 3;
    }

}

