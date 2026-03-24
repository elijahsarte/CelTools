package com.elijahsarte.celtools.main.colormodel;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.FrameParser;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.geomex.Line;
import com.elijahsarte.celtools.main.util.geomex.Polygon;
import com.elijahsarte.celtools.main.util.geomex.path.LinePath;
import com.elijahsarte.celtools.main.util.geomex.path.PathPoint;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.elijahsarte.celtools.main.util.CollectionsEx.lowerValue;
import static com.elijahsarte.celtools.main.util.CollectionsEx.tailQual;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.varExec;

public class HSVBounds {

    private TreeMap<Integer, Polygon> hsvBounds;
    private Set<Integer> hsvs;
    private double[] hsvMin, hsvMax;

    public HSVBounds(TreeMap<Integer, PointCollection> hsvPts) {
        this.hsvBounds = new TreeMap<>();
        for (Map.Entry<Integer, PointCollection> entry : hsvPts.entrySet()) {
            Integer k = entry.getKey();
            PointCollection v = entry.getValue();
// may have been formed from previous nextPoly finds and constructions
            if (this.hsvBounds.containsKey(k)) continue;
            switch (v.size()) {
                default:
                    // todo: make it so that it doesnt reconstruct the same polygon for spread hues
                    hsvBounds.put(k, Polygon.of(v));
                    continue;
                case 1:
                    Polygon lastPoly = lowerValue(hsvBounds, k);
                    Polygon nextPoly = tailQual(hsvPts, k, e -> e.getValue().size() >= 3).map(e -> Polygon.of(e.getValue())).orElse(null);
                    Polygon copyFrom = Optional.ofNullable(lowerValue(hsvBounds, k)).orElse(nextPoly);
                    if (copyFrom == null) {
                        throw new RuntimeException("WOrk on this later");
                    }
                    boolean bothPresent = lastPoly != null && nextPoly != null;
                    int pW = copyFrom.width(), pH = copyFrom.height(), pMX = copyFrom.midX(), pMY = copyFrom.midY(), pBY = copyFrom.bottomY();
                    double dX = OptionalEx.ofCond(bothPresent).then(() -> MathEx.midpoint(lastPoly.midX(), nextPoly.midX())).orElseVal(pMX),
                            dY = OptionalEx.ofCond(bothPresent).then(() -> MathEx.midpoint(lastPoly.midY(), nextPoly.midY())).orElseVal(pMY);
                    double cH = OptionalEx.ofCond(lastPoly != null && nextPoly != null).then(() -> MathEx.divide(MathEx.midpoint(lastPoly.height(), nextPoly.height()), lastPoly.height())).orElseVal(MathEx.divide(v.get(0).y, pH)),
                            cW = OptionalEx.ofCond(lastPoly != null && nextPoly != null).then(() -> MathEx.divide(MathEx.midpoint(lastPoly.width(), nextPoly.width()), lastPoly.width())).orElseVal(cH);

                    List<Point> pPts = lastPoly.pts();
                    PointCollection leftPts = new PointCollection(), rightPts = new PointCollection();
                    for (Point pPt : pPts) {
                        if (pPt.x <= pMX) leftPts.add(pPt);
                        if (pPt.x >= pMX) rightPts.add(pPt);
                    }

                    LinePath leftP = new LinePath(leftPts, LinePath.VertDirection.UP, LinePath.HorizDirection.RIGHT, PathPoint.Location.BOTTOM_LEFT),
                            rightP = new LinePath(rightPts);
                    PointCollection newColl = new PointCollection();
                    while (leftP.traversing()) {
                        varExec(leftP.traverse().translate(-copyFrom.leftX(), -pBY).scale(cW, cH).translate(dX, dY), l -> newColl.addAll(l.start(), l.end()));
                    }
                    while (rightP.traversing()) {
                        varExec(rightP.traverse().translate(-pMX, -pBY).scale(cW, cH).translate(dX, dY), l -> newColl.addAll(l.start(), l.end()));
                    }
                    hsvBounds.put(k, Polygon.of(newColl));
                    continue;
                case 2: {
//                    Point p0 = v.get(0), p1 = v.get(1);
//                    Line l0 = new Line(p0, new Point(p1.x, p0.y)), l1 = new Line(new Point(p0.x, p1.y), p1);
//                    hsvBounds.put(k, Polygon.of())
                    Line l0 = new Line(v.get(0)), l1 = new Line(v.get(1));
                    hsvBounds.put(k, Polygon.of(l0, new Line(l0.end(), l1.end()), l1));
                    break;
                }
            }
        }
        this.hsvs = new HashSet<>(hsvBounds.keySet());
    }
    public HSVBounds(double[] hsvMin, double[] hsvMax) {
        this.hsvMin = hsvMin;
        this.hsvMax = hsvMax;
    }

    public void expandAll(int expX, int expY) {
        hsvBounds.values().forEach(poly -> poly.expand(expX, expY));
    }
    public void expandAll(int expFactor) {
        hsvBounds.values().forEach(poly -> poly.expand(expFactor));
    }

    public void cache(boolean async) {
        Runnable cacheAll = () -> hsvBounds.values().forEach(Polygon::cache);
        if (async) Main.async(cacheAll);
        else cacheAll.run();
    }

    public boolean within(double[] hsv) {
        if (hsvBounds == null) return FrameParser.inBetweenClosed(hsv, hsvMin, hsvMax);
        if (!hsvs.contains(MathEx.floorInt(hsv[0]))) return false;
        return hsvBounds.get(MathEx.floorInt(hsv[0])).contains(new Point((int) hsv[1], (int) hsv[2]));
    }
    public boolean within(int rgb) {
        return within(FrameParser.RGBtoHSV(FastRGB.getRGB(rgb)));
    }

}

