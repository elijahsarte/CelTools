package com.elijahsarte.celtools.main.util.geomex.path;

import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.geomex.Line;
import com.elijahsarte.celtools.main.util.structures.collections.IntList;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;

import java.awt.*;
import java.util.List;
import java.util.TreeSet;

import static com.elijahsarte.celtools.main.util.CollectionsEx.idxWithin;
import static com.elijahsarte.celtools.main.util.CollectionsEx.lastElem;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.anyEquals;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.fnCall;
import static com.elijahsarte.celtools.main.util.geomex.path.PathPoint.Location.BOTTOM_RIGHT;

public class LinePath {

    public enum VertDirection {
        UP, DOWN
    }
    public enum HorizDirection {
        LEFT, RIGHT
    }

    private final PointCollection pts, flippedPts;
    private final TreeSet<Integer> ptYes;
    private VertDirection vertDir;
    private HorizDirection horizDir;
    private PathPoint start;


    // traverse runtime variables
    private Point lastPt;
    // for small performance boost
    private IntList lastYes;
    private boolean yLimited = false;
    private boolean traversing;


    public LinePath(PointCollection pts, VertDirection vertDir, HorizDirection horizDir, PathPoint start) {
//        this.lines = lines.stream().sorted(Comparator.comparingInt((Line l) -> l.start().x)
//                .thenComparingInt(l -> l.start().y)).filter(Predicate.not(Line::onlyVert)).toList();
        this.pts = pts;
        this.traversing = !pts.isEmpty();
        this.flippedPts = pts.flip();
        this.ptYes = new TreeSet<>(flippedPts.xes());
        this.vertDir = vertDir;
        this.horizDir = horizDir;
        this.start = start;
    }
    public LinePath(PointCollection pts, VertDirection vertDir, HorizDirection horizDir, PathPoint.Location startLoc) {
        this(pts, vertDir, horizDir, new PathPoint(startLoc));
    }
    public LinePath(PointCollection pts) {
        this(pts, VertDirection.UP, HorizDirection.LEFT, BOTTOM_RIGHT);
    }

    public void setVertDir(VertDirection vertDir) {
        this.vertDir = vertDir;
    }
    public void setHorizDir(HorizDirection horizDir) {
        this.horizDir = horizDir;
    }
    public void setStart(PathPoint start) {
        this.start = start;
    }


    public boolean traversing() {
        return this.traversing;
    }
    public Line traverse() {

        if (this.lastPt == null) {
            PathPoint.Location loc = start.getLocation();
            pts.onRaw();
            List<Point> horizPts = pts.getAtX(loc.left() ? pts.firstX() : pts.lastX());
            List<Point> vertPts = flippedPts.getAtX(loc.bottom() ? flippedPts.firstX() : flippedPts.lastX());

            this.lastPt = switch (start.getLocation()) {
                case BOTTOM_LEFT, BOTTOM_RIGHT -> horizPts.get(0).y > vertPts.get(0).x ? horizPts.get(0) : MathEx.flip(vertPts.get(0));
                case TOP_LEFT, TOP_RIGHT -> lastElem(horizPts).y < lastElem(vertPts).x ? lastElem(horizPts) : MathEx.flip(lastElem(vertPts));
                case MID -> pts.get(pts.size() / 2);
                case INDEX -> pts.get(start.index());
            };
            this.lastYes = flippedPts.getYesAtX(lastPt.y);
            if (pts.size() == 1) {
                this.traversing = false;
                return new Line(lastPt, lastPt);
            }
        }
        int toX = 0, toY = lastPt.y;

        boolean iterVert;

        int xIdx = lastYes.indexOf(lastPt.x);
        int sXIdx = horizDir == HorizDirection.LEFT ? -1 : 1;
        if (!(iterVert = (!idxWithin(lastYes, xIdx + sXIdx)))) toX = lastYes.get(xIdx + sXIdx);
        if (this.yLimited && !idxWithin(lastYes, xIdx + (2*sXIdx))) this.traversing = false;

        if (iterVert && this.traversing) {
            toY = fnCall(vertDir == VertDirection.UP ? ptYes::higher : ptYes::lower, lastPt.y);
            this.lastYes = flippedPts.getYesAtX(toY);
            toX = this.lastYes.get(horizDir == HorizDirection.LEFT ? 0 : this.lastYes.size() - 1);
        }

        Point toPt = new Point(toX, toY);
        Line line = new Line(this.lastPt, toPt);
        this.lastPt = toPt;
        this.yLimited = anyEquals(toY, ptYes.first(), ptYes.last());
        if (this.yLimited && this.lastYes.size() == 1) this.traversing = false;
        return line;

    }

}

