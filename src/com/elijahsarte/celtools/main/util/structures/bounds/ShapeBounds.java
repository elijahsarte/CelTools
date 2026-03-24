package com.elijahsarte.celtools.main.util.structures.bounds;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.geomex.path.ShapePath;
import com.elijahsarte.celtools.main.util.geomex.plane.Space2D;
import com.elijahsarte.celtools.main.util.geomex.plane.Vector2D;
import com.elijahsarte.celtools.main.util.iterators.CoordIterator;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.typeex.nullable.NullableInt;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.varExec;

public class ShapeBounds {

    private PointCollection bounds = new PointCollection();
    private TreeMap<Double, List<Double>> boundsDbl;
    private TreeMap<Integer, IntegerBounds> boundsBds;

    private Map<Point, Integer> boundColors;

    public ShapeBounds(Space2D plane, List<Vector2D> shapeVecs) {
        shapeVecs.forEach(vec -> varExec(plane.getPoint(vec), bounds::add));
    }
    public ShapeBounds(Map<Integer, List<Integer>> pointMap) {
        this.bounds = new PointCollection(pointMap);
    }
    public ShapeBounds(Supplier<Map<Integer, IntegerBounds>> mapSupplier) {
        Map<Integer, IntegerBounds> map = mapSupplier.get();
        if (map instanceof TreeMap<Integer, IntegerBounds> tree) this.boundsBds = tree;
        else this.boundsBds = new TreeMap<>(map);
        this.bounds = new PointCollection();
        this.boundsBds.forEach((x, bds) -> {
            this.bounds.add(new Point(x, bds.getLowerBound()));
            this.bounds.add(new Point(x, bds.getUpperBound()));
        });
    }
    public ShapeBounds(PointCollection ptColl) {
        this.bounds = ptColl;
    }
    /** Constructs ShapeBounds from a java.awt.Rectangle.
     *  All integer points within the rectangle are filled into the bounds. */
    public ShapeBounds(Rectangle rect) {
        this.bounds = new PointCollection();
        new CoordIterator(rect).execute((x, y) ->
            this.bounds.add(new Point(x, y))
        );
        this.bounds.onRaw();
        this.boundsBds = this.bounds.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> new IntegerBounds(e.getValue().first(), e.getValue().last()),
                (a, b) -> a,
                TreeMap::new
        ));
    }




    public void translate(int transX, int transY) {
        if (this.bounds != null) {
            this.bounds.translate(transX, transY);
            this.indexBounds();
        }
        else this.boundsBds = this.boundsBds.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey() + transX,
                e -> e.getValue().shift(transX),
                (a, b) -> a,
                TreeMap::new
        ));
    }

    public void interpolate() {
//        this.indexDbl();
//        Main.interpolateMissingCoords(StreamEx.toMap(boundsDbl.entrySet().stream().filter(e -> e.getValue().size() >= 2), TreeMap::new), 1).forEach((col, rows) -> rows.forEach(row -> bounds.add(new Point(col.intValue(), row.intValue()))));
        Main.interpolateMissingCoords(bounds);
    }


    public void indexBounds() {
        this.bounds.onRaw();
        this.boundsBds = this.bounds.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> ProgrammingEx.varOper(e.getValue(), iL -> new IntegerBounds(iL.first(), iL.last())),
                (a, b) -> a,
                TreeMap::new
        ));
    }

// todo: indicate that this is taking it from boudns
    public void indexColors(FastRGB pixelHandler) {
        this.boundColors = new HashMap<>();
        this.bounds.onRaw();
        this.bounds.forEachRaw((x, i) -> IntStream.rangeClosed(i.first(), i.last()).forEach(n -> boundColors.put(new Point(x, n), pixelHandler.getRGBRaw(x, n))));
//        this.bounds.forEach(pt -> boundColors.put(pt, pixelHandler.getRGBRaw(pt.x, pt.y)));
    }

    public void forEachInner(BiConsumer<Integer, Integer> fn) {
        if (this.boundsBds != null) {
            this.boundsBds.forEach((x, bds) -> bds.getSequence().forEach(y -> fn.accept(x, y)));
            return;
        }
        this.bounds.onRaw();
        this.bounds.forEachRaw((x, l) -> IntStream.rangeClosed(l.first(), l.last()).forEach(y -> fn.accept(x, y)));
    }
    public void forEachBounds(BiConsumer<Integer, IntegerBounds> fn) {
        if (this.boundsBds != null) {
            this.boundsBds.forEach(fn::accept);
            return;
        }
        this.indexBounds();
        forEachBounds(fn);
    }

    public int firstX() {
        return boundsBds != null ? boundsBds.firstKey() : bounds.firstX();
    }
    public int lastX() {
        return boundsBds != null ? boundsBds.lastKey() : bounds.lastX();
    }
    public int topY() {
        return bounds != null ? bounds.topY() : (boundsBds.values().stream().map(IntegerBounds::getUpperBound).max(Comparator.naturalOrder()).orElse(-1));
    }
    public int bottomY() {
        return bounds != null ? bounds.bottomY() : boundsBds.values().stream().map(IntegerBounds::getLowerBound).min(Comparator.naturalOrder()).orElse(-1);
    }


    private NullableInt lowerX(int x) {
        return new NullableInt(boundsBds.higherKey(x));
    }
    private NullableInt higherX(int x) {
        return new NullableInt(boundsBds.lowerKey(x));
    }
    public int width() {
        return lastX() - firstX();
    }

    public IntegerBounds get(int x) {
        return boundsBds == null ? null : boundsBds.get(x);
    }
    public boolean containsX(int x) {
        return boundsBds != null && boundsBds.containsKey(x);
    }
    public boolean inside(Point pt) {
        return containsX(pt.x) && get(pt.x).inBetweenClosed(pt.y);
    }



    /** Curvature κ for a boundary point; picks UP/DOWN by y match within tol. */
    public double laplacianAt(Point p, double tol) {
        ensureIndexed();
        IntegerBounds b = boundsBds.get(p.x);
        if (b == null) return 0.0;
        ShapePath.VertSide side = pickSide(p.y, b, tol);
        if (side == null) return 0.0;
        return curvatureAtX(p.x, side);
    }
    public double laplacianAt(Point p) {
        return laplacianAt(p, 1);
    }

    /** Curvature κ at arclength s (pixels) measured along the chosen vertical side. */
    public double laplacianAt(double s, ShapePath.VertSide side) {
        ensureIndexed();
        SideProfile prof = buildSideProfile(side);
        if (prof.xs.length < 3) return 0.0;
        // clamp s into [0, L]
        double S = Math.max(0, Math.min(s, prof.sCum[prof.sCum.length - 1]));
        int i = Arrays.binarySearch(prof.sCum, S);
        if (i < 0) i = -i - 2;              // i is left index
        i = Math.max(1, Math.min(i, prof.xs.length - 2)); // need i-1, i, i+1
        return curvatureAtIndex(prof, i);
    }
    /**
     * Returns Laplacian curvature κ at the specified x-coordinate and side.
     * Uses neighboring x positions to approximate the local curvature.
     */
    public double laplacianAt(int x, ShapePath.VertSide side) {
        ensureIndexed();

        Integer xL = boundsBds.lowerKey(x);
        Integer xR = boundsBds.higherKey(x);
        if (xL == null || xR == null) return 0.0; // edges

        double yL = sideY(xL, side);
        double y  = sideY(x, side);
        double yR = sideY(xR, side);

        double hL = x - xL;
        double hR = xR - x;
        if (hL <= 0 || hR <= 0) return 0.0;

        // nonuniform finite-difference curvature: κ = y'' / (1 + y'^2)^(3/2)
        double dy_dx =
                (-hR / (hL * (hL + hR))) * yL +
                        ((hR - hL) / (hL * hR))   * y +
                        (hL / (hR * (hL + hR)))   * yR;

        double d2y_dx2 =
                2.0 * ( yL / (hL * (hL + hR))
                        - y / (hL * hR)
                        + yR / (hR * (hL + hR)) );

        double denom = Math.pow(1.0 + dy_dx * dy_dx, 1.5);
        return denom == 0.0 ? 0.0 : d2y_dx2 / denom;
    }


// ---------- helpers ----------

    private void ensureIndexed() { if (this.boundsBds == null) indexBounds(); }

    private ShapePath.VertSide pickSide(int y, IntegerBounds b, double tol) {
        int yTop = b.getUpperBound(), yBot = b.getLowerBound();
        if (Math.abs(y - yTop) <= tol) return ShapePath.VertSide.UP;
        if (Math.abs(y - yBot) <= tol) return ShapePath.VertSide.DOWN;
        return null;
    }

    private int sideY(int x, ShapePath.VertSide side) {
        IntegerBounds b = boundsBds.get(x);
        return side == ShapePath.VertSide.UP ? b.getUpperBound() : b.getLowerBound();
    }

    /** κ at integer column x using non-uniform finite differences on y(x). */
    private double curvatureAtX(int xMid, ShapePath.VertSide side) {
        // find neighbors with same side available
        NullableInt x1 = lowerX(xMid),
            x2 = higherX(xMid);
        if (!x1.isSet() || !x2.isSet()) return 0.0;
        double x0 = x1.get(), xP = x2.get();
        double y0 = sideY(x1.get(), side), y = sideY(xMid, side), yP = sideY(x2.get(), side);

        double h0 = xMid - x0;
        double h1 = xP - xMid;
        if (h0 <= 0 || h1 <= 0) return 0.0;

        // nonuniform central first derivative at x
        double dy_dx =
                (-h1/(h0*(h0+h1)))*y0 + ((h1 - h0)/(h0*h1))*y + (h0/(h1*(h0+h1)))*yP;

        // nonuniform second derivative at x
        double d2y_dx2 = 2.0 * ( y0/(h0*(h0+h1)) - y/(h0*h1) + yP/(h1*(h0+h1)) );

        double denom = Math.pow(1.0 + dy_dx*dy_dx, 1.5);
        return denom == 0.0 ? 0.0 : d2y_dx2 / denom;
    }


    /** Pack side samples and cumulative arclength. */
    private static final class SideProfile {
        final int[] xs;
        final double[] ys;
        final double[] sCum;
        SideProfile(int[] xs, double[] ys, double[] sCum) {
            this.xs = xs; this.ys = ys; this.sCum = sCum;
        }
    }

    private SideProfile buildSideProfile(ShapePath.VertSide side) {
        int n = boundsBds.size();
        int[] xs = new int[n];
        double[] ys = new double[n];
        int k = 0;
        for (Map.Entry<Integer, IntegerBounds> e : boundsBds.entrySet()) {
            xs[k] = e.getKey();
            ys[k] = side == ShapePath.VertSide.UP ? e.getValue().getUpperBound() : e.getValue().getLowerBound();
            k++;
        }
        double[] s = new double[n];
        s[0] = 0.0;
        for (int i = 1; i < n; i++) {
            double dx = xs[i] - xs[i-1];
            double dy = ys[i] - ys[i-1];
            s[i] = s[i-1] + Math.hypot(dx, dy);
        }
        return new SideProfile(xs, ys, s);
    }

    /** κ at index i using three-point nonuniform stencil on (xs, ys). */
    private double curvatureAtIndex(SideProfile prof, int i) {
        int i0 = i - 1, i2 = i + 1;
        double x0 = prof.xs[i0], x = prof.xs[i], xP = prof.xs[i2];
        double y0 = prof.ys[i0], y = prof.ys[i], yP = prof.ys[i2];
        double h0 = x - x0, h1 = xP - x;
        if (h0 <= 0 || h1 <= 0) return 0.0;

        double dy_dx =
                (-h1/(h0*(h0+h1)))*y0 + ((h1 - h0)/(h0*h1))*y + (h0/(h1*(h0+h1)))*yP;

        double d2y_dx2 = 2.0 * ( y0/(h0*(h0+h1)) - y/(h0*h1) + yP/(h1*(h0+h1)) );

        double denom = Math.pow(1.0 + dy_dx*dy_dx, 1.5);
        return denom == 0.0 ? 0.0 : d2y_dx2 / denom;
    }



    /** Returns the geometric origin (center) of these bounds. */
    public Point center() {
        SortedMap<Integer, IntegerBounds> mapX = getBounds();
        if (mapX == null || mapX.isEmpty())
            return new Point(0, 0);

        int minX = mapX.firstKey(), maxX = mapX.lastKey(),
                minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (IntegerBounds b : mapX.values()) {
            if (b == null) continue;
            if (b.getLowerBound() < minY) minY = b.getLowerBound();
            if (b.getUpperBound() > maxY) maxY = b.getUpperBound();
        }

        return new Point((minX + maxX) / 2, (minY + maxY) / 2);
    }

    public double distance(ShapeBounds other) {
        if (other == null) return 0.0;

        this.indexBounds();
        other.indexBounds();

        TreeMap<Integer, IntegerBounds> aMap = this.getBounds();
        TreeMap<Integer, IntegerBounds> bMap = other.getBounds();

        if (aMap == null || bMap == null || aMap.isEmpty() || bMap.isEmpty()) return 0.0;

        List<Integer> bx = new ArrayList<>(bMap.keySet());
        double bestSq = Double.POSITIVE_INFINITY;

        for (Map.Entry<Integer, IntegerBounds> ae : aMap.entrySet()) {
            int xA = ae.getKey();
            IntegerBounds aB = ae.getValue();
            if (aB == null) continue;

            int pos = Collections.binarySearch(bx, xA);
            int idx = pos >= 0 ? pos : -pos - 1;

            int left = idx - 1, right = idx;

            while (left >= 0 || right < bx.size()) {
                int xB;
                if (left >= 0 && right < bx.size()) {
                    int dxL = Math.abs(xA - bx.get(left));
                    int dxR = Math.abs(bx.get(right) - xA);
                    xB = dxL <= dxR ? bx.get(left--) : bx.get(right++);
                } else if (left >= 0) {
                    xB = bx.get(left--);
                } else {
                    xB = bx.get(right++);
                }

                long dx = (long) xB - xA;
                double dxSq = (double) dx * dx;
                if (dxSq > bestSq) break;

                IntegerBounds bB = bMap.get(xB);
                if (bB == null) continue;

                int aLo = aB.getLowerBound(), aHi = aB.getUpperBound();
                int bLo = bB.getLowerBound(), bHi = bB.getUpperBound();

                int dyInt;
                if (aHi < bLo) dyInt = bLo - aHi;
                else if (bHi < aLo) dyInt = aLo - bHi;
                else dyInt = 0;

                double distSq = dxSq + (double) dyInt * dyInt;
                if (distSq < bestSq) {
                    bestSq = distSq;
                    if (bestSq == 0.0) return 0.0;
                }
            }
        }

        return Math.sqrt(bestSq);
    }

    public double distance(PointCollection pc) {
        if (pc == null) return 0.0;

        this.indexBounds();
        TreeMap<Integer, IntegerBounds> aMap = this.getBounds();
        if (aMap == null || aMap.isEmpty()) return 0.0;

        List<Integer> px = new ArrayList<>();
        for (int x : pc.xes()) px.add(x);
        if (px.isEmpty()) return 0.0;

        double bestSq = Double.POSITIVE_INFINITY;

        for (Map.Entry<Integer, IntegerBounds> ae : aMap.entrySet()) {
            int xA = ae.getKey();
            IntegerBounds aB = ae.getValue();
            if (aB == null) continue;

            int pos = Collections.binarySearch(px, Integer.valueOf(xA));
            int idx = pos >= 0 ? pos : -pos - 1;

            int left = idx - 1, right = idx;

            while (left >= 0 || right < px.size()) {
                int xB;
                if (left >= 0 && right < px.size()) {
                    int dxL = Math.abs(xA - px.get(left));
                    int dxR = Math.abs(px.get(right) - xA);
                    xB = dxL <= dxR ? px.get(left--) : px.get(right++);
                } else if (left >= 0) {
                    xB = px.get(left--);
                } else {
                    xB = px.get(right++);
                }

                long dx = (long) xB - xA;
                double dxSq = (double) dx * dx;
                if (dxSq > bestSq) break;

                // TODO: CREATE NAVIGABLESET FOR THIS
                NavigableSet<Integer> ys = new TreeSet<>(pc.getYesAtX(xB).toList());
                if (ys == null || ys.isEmpty()) continue;

                int aLo = aB.getLowerBound(), aHi = aB.getUpperBound();

                Integer yIn = ys.ceiling(aLo);
                int dyInt;
                if (yIn != null && yIn <= aHi) {
                    dyInt = 0;
                } else {
                    Integer yBelow = ys.floor(aLo);
                    Integer yAbove = ys.ceiling(aHi);
                    int bestDy = Integer.MAX_VALUE;
                    if (yBelow != null) bestDy = Math.min(bestDy, aLo - yBelow);
                    if (yAbove != null) bestDy = Math.min(bestDy, yAbove - aHi);
                    if (bestDy == Integer.MAX_VALUE) continue;
                    dyInt = bestDy;
                }

                double distSq = dxSq + (double) dyInt * dyInt;
                if (distSq < bestSq) {
                    bestSq = distSq;
                    if (bestSq == 0.0) return 0.0;
                }
            }
        }

        return Math.sqrt(bestSq);
    }


    public Point closest(Point pt) {
        NavigableMap<Integer, IntegerBounds> b = getBounds();
        if (b == null || b.isEmpty()) return null;

        IntegerBounds at = b.get(pt.x);
        if (at != null) {
            int lo = at.getLowerBound(), hi = at.getUpperBound();
            if (pt.y >= lo && pt.y <= hi) return new Point(pt.x, pt.y);
            int yCand = pt.y < lo ? lo : hi;
            return new Point(pt.x, yCand);
        }

        double bestSq = Double.POSITIVE_INFINITY;
        Point best = null;

        Map.Entry<Integer, IntegerBounds> L = b.lowerEntry(pt.x), R = b.higherEntry(pt.x);
        while (L != null || R != null) {
            int dxL = L != null ? pt.x - L.getKey() : Integer.MAX_VALUE;
            int dxR = R != null ? R.getKey() - pt.x : Integer.MAX_VALUE;
            boolean takeLeft = dxL <= dxR;
            Map.Entry<Integer, IntegerBounds> e = takeLeft ? L : R;

            long dx = (long) e.getKey() - pt.x;
            double dxSq = (double) dx * dx;
            if (dxSq >= bestSq) break;

            IntegerBounds span = e.getValue();
            int lo = span.getLowerBound(), hi = span.getUpperBound();
            int yCand = pt.y < lo ? lo : pt.y > hi ? hi : pt.y;
            long dy = (long) yCand - pt.y;
            double d2 = dxSq + (double) dy * dy;
            if (d2 < bestSq) {
                bestSq = d2;
                best = new Point(e.getKey(), yCand);
                if (bestSq == 0.0) return best;
            }

            if (takeLeft) L = b.lowerEntry(e.getKey()); else R = b.higherEntry(e.getKey());
        }
        return best;
    }

    public Point closestY(Point pt) {
        NavigableMap<Integer, IntegerBounds> b = getBounds();
        if (b == null || b.isEmpty()) return null;
        IntegerBounds span = b.get(pt.x);
        if (span == null) return null;
        int lo = span.getLowerBound(), hi = span.getUpperBound();
        int yCand = pt.y < lo ? lo : pt.y > hi ? hi : pt.y;
        return new Point(pt.x, yCand);
    }

    public Point closestX(Point pt) {
        NavigableMap<Integer, IntegerBounds> b = getBounds();
        if (b == null || b.isEmpty()) return null;

        IntegerBounds at = b.get(pt.x);
        if (at != null) {
            int lo = at.getLowerBound(), hi = at.getUpperBound();
            if (pt.y >= lo && pt.y <= hi) return new Point(pt.x, pt.y);
        }

        Map.Entry<Integer, IntegerBounds> L = b.lowerEntry(pt.x), R = b.higherEntry(pt.x);
        while (L != null || R != null) {
            int dxL = L != null ? pt.x - L.getKey() : Integer.MAX_VALUE;
            int dxR = R != null ? R.getKey() - pt.x : Integer.MAX_VALUE;
            boolean takeLeft = dxL <= dxR;
            Map.Entry<Integer, IntegerBounds> e = takeLeft ? L : R;

            IntegerBounds span = e.getValue();
            int lo = span.getLowerBound(), hi = span.getUpperBound();
            if (pt.y >= lo && pt.y <= hi) return new Point(e.getKey(), pt.y);

            if (takeLeft) L = b.lowerEntry(e.getKey()); else R = b.higherEntry(e.getKey());
        }
        return null;
    }

    public double distance(Point pt) {
        Point q = closest(pt);
        if (q == null) return Double.POSITIVE_INFINITY;
        long dx = (long) q.x - pt.x, dy = (long) q.y - pt.y;
        return Math.sqrt((double) dx * dx + (double) dy * dy);
    }





    public ShapeBounds rotate(double angleRad, int px, int py) {
        Map<Integer, IntegerBounds> mapX = getBounds();
        if (mapX == null || mapX.isEmpty()) return new ShapeBounds(new PointCollection());

        double cos = MathEx.cos(angleRad), sin = MathEx.sin(angleRad);
        PointCollection pc = new PointCollection();

        mapX.forEach((x, b) -> {
            if (b == null) return;
            int y0 = b.getLowerBound(), y1 = b.getUpperBound();
            for (int y = y0; y <= y1; y++) {
                pc.add(new Point(
                        MathEx.roundInt(px + cos * (x - px) - sin * (y - py)),
                        MathEx.roundInt(py + sin * (x - px) + cos * (y - py))
                ));
            }
        });
        return new ShapeBounds(pc);
    }


    public ShapeBounds rotate(double angleRad) {
        Point o = center();
        return rotate(angleRad, o.x, o.y);
    }



    public Stream<Map.Entry<Integer, IntegerBounds>> entryStream() {
        return boundsBds.entrySet().stream();
    }





    public PointCollection get() {
        return this.bounds;
    }
    public TreeMap<Integer, IntegerBounds> getBounds() {
        return this.boundsBds;
    }
    public IntegerBounds getBound(int x) {
        return this.boundsBds.get(x);
    }
    public Map<Point, Integer> getColors() {
        return this.boundColors;
    }
}

