package com.elijahsarte.celtools.main.colormodel;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.FrameParser;
import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.geomex.Line;
import com.elijahsarte.celtools.main.util.geomex.Polygon;
import com.elijahsarte.celtools.main.util.geomex.path.LinePath;
import com.elijahsarte.celtools.main.util.geomex.path.PathPoint;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.elijahsarte.celtools.main.util.CollectionsEx.lowerValue;
import static com.elijahsarte.celtools.main.util.CollectionsEx.tailQual;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.varExec;

public class HSVBounds {

    private Map<Integer, Polygon> hsvBounds;
    private double[] hsvMin, hsvMax;

    public HSVBounds(Map<Integer, Polygon> polygons) {
        //this.hsvBounds = polygons instanceof TreeMap ? (TreeMap<Integer, Polygon>) polygons : new TreeMap<>(polygons);
        this.hsvBounds = !(polygons instanceof HashMap) ? new HashMap<>(polygons) : polygons;
    }
    public HSVBounds(TreeMap<Integer, PointCollection> hsvPts) {
        TreeMap<Integer, Polygon> hsvBounds = new TreeMap<>();
        for (Map.Entry<Integer, PointCollection> entry : hsvPts.entrySet()) {
            Integer k = entry.getKey();
            PointCollection v = entry.getValue();
// may have been formed from previous nextPoly finds and constructions
            if (hsvBounds.containsKey(k)) continue;
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
        this.hsvBounds = new HashMap<>(hsvBounds);
    }
    public HSVBounds(double[] hsvMin, double[] hsvMax) {
        this.hsvMin = hsvMin;
        this.hsvMax = hsvMax;
    }

    public HSVBounds(int hueMin, int hueMax, boolean includeLowerValues, List<Line> boundary) {
        TreeMap<Integer, Polygon> hsvBounds = new TreeMap<>();

        hueMin = MathEx.bound(Math.min(hueMin, hueMax), 0, 359);
        hueMax = MathEx.bound(Math.max(hueMin, hueMax), 0, 359);

        List<Line> normalized = normalizeBoundary(boundary);
        if (normalized.isEmpty()) {
            return;
        }

        Polygon polygon = Polygon.of(buildColumnsFromBoundary(normalized, includeLowerValues));

        for (int hue = hueMin; hue <= hueMax; hue++) {
            hsvBounds.put(hue, polygon);
        }
        this.hsvBounds = new HashMap<>(hsvBounds);
    }

    public HSVBounds(int hueMin, int hueMax, List<Line> boundary) {
        this(hueMin, hueMax, true, boundary);
    }

    public static PointCollection buildColumnsFromBoundary(List<Line> boundary, boolean includeLowerValues) {
        PointCollection pts = new PointCollection();

        for (int s = 0; s <= 100; s++) {
            int boundaryV = boundaryValueAt(boundary, s);

            int low = includeLowerValues ? 0 : boundaryV;
            int high = includeLowerValues ? boundaryV : 100;

            low = MathEx.bound(low, 0, 100);
            high = MathEx.bound(high, 0, 100);

            if (high < low) continue;

            ArrayList<Integer> values = new ArrayList<>(high - low + 1);
            for (int v = low; v <= high; v++) {
                values.add(v);
            }

            pts.addAtX(s, values);
        }

        return pts;
    }

    public static List<Line> normalizeBoundary(List<Line> boundary) {
        ArrayList<Line> out = new ArrayList<>();
        if (boundary == null) return out;

        for (Line line : boundary) {
            if (line == null) continue;

            Point a = clampHSVPoint(new Point(line.start()));
            Point b = clampHSVPoint(new Point(line.end()));

            if (a.x == b.x) continue;

            out.add(a.x <= b.x ? new Line(a, b) : new Line(b, a));
        }

        out.sort(
                Comparator.comparingInt(Line::startX)
                        .thenComparingInt(l -> l.start().y)
                        .thenComparingInt(l -> l.end().y)
        );

        if (out.isEmpty()) return out;

        ArrayList<Line> stitched = new ArrayList<>();
        stitched.add(out.get(0));

        for (int i = 1; i < out.size(); i++) {
            Line prev = stitched.get(stitched.size() - 1);
            Line curr = out.get(i);

            if (curr.startX() > prev.endX() + 1) {
                stitched.add(new Line(new Point(prev.end()), new Point(curr.start())));
            }

            stitched.add(curr);
        }

        return stitched;
    }

    private static int boundaryValueAt(List<Line> boundary, int s) {
        s = MathEx.bound(s, 0, 100);

        if (boundary == null || boundary.isEmpty()) {
            return 50;
        }

        int idx = CollectionsEx.binarySearchLineIdx(boundary, s);
        if (idx != -1) {
            return MathEx.bound(boundary.get(idx).yAt(s).y, 0, 100);
        }

        Line first = boundary.get(0);
        if (s <= first.startX()) {
            return MathEx.bound(first.start().y, 0, 100);
        }

        Line last = boundary.get(boundary.size() - 1);
        if (s >= last.endX()) {
            return MathEx.bound(last.end().y, 0, 100);
        }

        for (int i = 1; i < boundary.size(); i++) {
            Line left = boundary.get(i - 1);
            Line right = boundary.get(i);

            if (s > left.endX() && s < right.startX()) {
                return interpolateY(left.end(), right.start(), s);
            }
        }

        return MathEx.bound(last.end().y, 0, 100);
    }

    private static int interpolateY(Point a, Point b, int x) {
        a = clampHSVPoint(a);
        b = clampHSVPoint(b);

        if (a.x == b.x) {
            return MathEx.bound(a.y, 0, 100);
        }

        double t = MathEx.divide(x - a.x, b.x - a.x);
        return MathEx.bound(MathEx.roundInt(a.y + ((b.y - a.y) * t)), 0, 100);
    }

    private static Point clampHSVPoint(Point p) {
        return new Point(
                MathEx.bound(p.x, 0, 100),
                MathEx.bound(p.y, 0, 100)
        );
    }

    public static void putHueRange(
            TreeMap<Integer, Polygon> polygons,
            int hueMin,
            int hueMax,
            boolean includeLowerValues,
            List<Line> boundary
    ) {
        if (polygons == null) {
            throw new IllegalArgumentException("polygons cannot be null");
        }

        hueMin = MathEx.bound(Math.min(hueMin, hueMax), 0, 359);
        hueMax = MathEx.bound(Math.max(hueMin, hueMax), 0, 359);

        List<Line> normalized = normalizeBoundary(boundary);
        if (normalized.isEmpty()) {
            return;
        }

        Polygon polygon = Polygon.of(buildColumnsFromBoundary(normalized, includeLowerValues));

        for (int hue = hueMin; hue <= hueMax; hue++) {
            polygons.put(hue, polygon);
        }
    }

    public static void putHueRange(
            TreeMap<Integer, Polygon> polygons,
            int hueMin,
            int hueMax,
            List<Line> boundary
    ) {
        putHueRange(polygons, hueMin, hueMax, true, boundary);
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
        if (hsvBounds == null || hsvBounds.isEmpty()) return FrameParser.inBetweenClosed(hsv, hsvMin, hsvMax);
        Polygon herePoly = hsvBounds.get(MathEx.floorInt(hsv[0]));
        return herePoly != null && herePoly.contains(new Point((int) hsv[1], (int) hsv[2]));
    }
    public boolean within(int rgb) {
        return within(FrameParser.RGBtoHSV(FastRGB.getRGB(rgb)));
    }

}

