package com.elijahsarte.celtools.main.util.structures.shape;

import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.elijahsarte.celtools.main.util.MathEx.*;

public class ShapeContour implements Iterable<Point> {

    private static final int[][] NEIGHBOR_OFFSETS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };

    private static final ShapeContour EMPTY = new ShapeContour(Collections.emptyList());

    private List<Point> contour;
    private double[] anglesFromTop;
    private double[] rawAngles;
    private double[] radii;
    private double[] cumulativeArcLengths;
    private double perimeter;

    private Map<Long, Integer> indexLookup;
    private Point2D.Double centroid;

    private Point topPoint;
    private Point bottomPoint;
    private Point leftPoint;
    private Point rightPoint;

    private List<Edge> edges;
    private NavigableSet<Integer> xSet;

    /**
    public ShapeContour(PointCollection pointCollection) {
        this(collectPoints(pointCollection));
    }*/

    public ShapeContour(PointCollection pts) {
        if (pts == null || pts.isEmpty()) {
            initEmpty();
            return;
        }

        Set<Long> all = new HashSet<>((int) (pts.size() / 0.75) + 1);
        pts.onRaw();
        pts.forEachRaw((x, ys) -> {
            for (int i = 0, n = ys.size(); i < n; i++) {
                all.add(MathEx.encode(x, ys.get(i)));
            }
        });

        Set<Long> boundary = new HashSet<>((int) (all.size() / 0.75) + 1);
        pts.forEachRaw((x, ys) -> {
            for (int i = 0, n = ys.size(); i < n; i++) {
                int y = ys.get(i);
                long code = MathEx.encode(x, y);
                boolean isBoundary =
                        !all.contains(MathEx.encode(x - 1, y)) ||
                                !all.contains(MathEx.encode(x + 1, y)) ||
                                !all.contains(MathEx.encode(x, y - 1)) ||
                                !all.contains(MathEx.encode(x, y + 1));
                if (isBoundary) boundary.add(code);
            }
        });

        if (boundary.isEmpty()) {
            initEmpty();
            return;
        }

        List<Point> ordered = traceLargestLoop(boundary);
        if (ordered.isEmpty()) {
            initEmpty();
            return;
        }

        // Remove duplicated closing vertex if present
        if (ordered.size() >= 2) {
            Point first = ordered.get(0);
            Point last = ordered.get(ordered.size() - 1);
            if (first.x == last.x && first.y == last.y) {
                ordered.remove(ordered.size() - 1);
            }
        }

        if (signedArea(ordered) < 0.0) {
            Collections.reverse(ordered);
        }

        int idx = 0;
        for (int i = 1; i < ordered.size(); i++) {
            Point a = ordered.get(idx);
            Point b = ordered.get(i);
            if (b.y > a.y || (b.y == a.y && b.x < a.x)) idx = i;
        }
        if (idx > 0) {
            Collections.rotate(ordered, -idx);
        }

        finalizeFromOrdered(ordered);
    }


    private List<Point> traceBestLoop(Set<Long> boundary) {
        if (boundary.isEmpty()) return Collections.emptyList();

        List<Point> best = Collections.emptyList();
        double bestArea = -1.0;

        Set<Long> seen = new HashSet<>((int) (boundary.size() / 0.75) + 1);
        ArrayDeque<Long> q = new ArrayDeque<>();

        for (long start : boundary) {
            if (seen.contains(start)) continue;

            List<Long> comp = new ArrayList<>();
            q.clear();
            q.add(start);
            seen.add(start);

            while (!q.isEmpty()) {
                long c = q.removeFirst();
                comp.add(c);
                int cx = (int) (c >>> 32), cy = (int) c;
                for (int i = 0; i < 8; i++) {
                    int nx = cx + NEIGHBOR_OFFSETS[i][0];
                    int ny = cy + NEIGHBOR_OFFSETS[i][1];
                    long nc = MathEx.encode(nx, ny);
                    if (boundary.contains(nc) && !seen.contains(nc)) {
                        seen.add(nc);
                        q.addLast(nc);
                    }
                }
            }

            List<Point> loop = mooreTraceComponent(comp);
            if (!loop.isEmpty()) {
                double area = Math.abs(signedArea(loop));
                if (area > bestArea || (area == bestArea && loop.size() > best.size())) {
                    bestArea = area;
                    best = loop;
                }
            }
        }
        return best;
    }
    private List<Point> followExternal(Set<Long> boundary) {
        if (boundary.isEmpty()) return Collections.emptyList();

        // pick start = bottom-most, then left-most
        int sx = 0, sy = Integer.MIN_VALUE;
        for (long code : boundary) {
            int x = (int) (code >>> 32);
            int y = (int) code;
            if (y > sy || (y == sy && x < sx)) { sx = x; sy = y; }
        }

        // pretend we came from outside to the left of start -> backtrack is West->East, so current dir is East
        int bx = sx - 1, by = sy;
        int dir = 0; // CHAIN8 index 0 == East

        List<Point> out = new ArrayList<>(boundary.size());
        Set<Long> visited = new HashSet<>((int) (boundary.size() / 0.75) + 1);
        int cx = sx, cy = sy;
        int startBx = bx, startBy = by;

        // walk until we return to start with same backtrack; no “seen” early exit
        int guard = Math.max(64, boundary.size() * 12);
        while (guard-- > 0) {
            long code = enc(cx, cy);
            if (visited.add(code)) {
                out.add(new Point(cx, cy));
            }

            if (cx == sx && cy == sy && bx == startBx && by == startBy && out.size() > 1) break;

            // scan clockwise starting one step to the right of backtrack
            int scan = (dir + 6) & 7;
            boolean advanced = false;
            for (int k = 0; k < 8; k++) {
                int nd = (scan + k) & 7;
                int nx = cx + CHAIN8[nd][0];
                int ny = cy + CHAIN8[nd][1];
                long nc = enc(nx, ny);
                if (boundary.contains(nc)) {
                    bx = cx; by = cy;
                    cx = nx; cy = ny;
                    dir = nd;
                    advanced = true;
                    break;
                }
            }
            if (!advanced) break; // nothing found -> broken boundary
        }

        dedupeSpikes(out);
        return out;
    }

    private List<Point> traceLargestLoop(Set<Long> boundary) {
        if (boundary.isEmpty()) return Collections.emptyList();

        // BFS components on 8-connectivity
        List<List<Long>> comps = new ArrayList<>();
        Set<Long> seen = new HashSet<>((int) (boundary.size() / 0.75) + 1);
        ArrayDeque<Long> q = new ArrayDeque<>();

        for (long s : boundary) {
            if (!seen.add(s)) continue;
            List<Long> comp = new ArrayList<>();
            q.clear(); q.add(s);
            while (!q.isEmpty()) {
                long c = q.removeFirst();
                comp.add(c);
                int x = (int) (c >>> 32), y = (int) c;
                for (int i = 0; i < 8; i++) {
                    int nx = x + CHAIN8[i][0], ny = y + CHAIN8[i][1];
                    long nc = enc(nx, ny);
                    if (boundary.contains(nc) && seen.add(nc)) q.addLast(nc);
                }
            }
            comps.add(comp);
        }

        List<Point> best = Collections.emptyList();
        double bestA = -1.0;
        for (int t = 0; t < comps.size(); t++) {
            // project component to a set again
            Set<Long> compSet = new HashSet<>((int) (comps.get(t).size() / 0.75) + 1);
            for (int i = 0; i < comps.get(t).size(); i++) compSet.add(comps.get(t).get(i));
            List<Point> loop = followExternal(compSet);
            if (!loop.isEmpty()) {
                double a = Math.abs(signedArea(loop));
                if (a > bestA || (a == bestA && loop.size() > best.size())) { bestA = a; best = loop; }
            }
        }
        return best;
    }


    private List<Point> mooreTraceComponent(List<Long> comp) {
        if (comp.isEmpty()) return Collections.emptyList();

        Set<Long> compSet = new HashSet<>((int) (comp.size() / 0.75) + 1);
        for (int i = 0, n = comp.size(); i < n; i++) compSet.add(comp.get(i));

        int sx = 0, sy = Integer.MIN_VALUE;
        for (int i = 0, n = comp.size(); i < n; i++) {
            long code = comp.get(i);
            int x = (int) (code >>> 32), y = (int) code;
            if (y > sy || (y == sy && x < sx)) { sx = x; sy = y; }
        }

        int bx = sx - 1, by = sy; // backtrack seed: left of start
        int dir = dirIndex(sx - bx, sy - by); // should be east

        List<Point> out = new ArrayList<>(comp.size() + 8);
        Set<Long> seenEdges = new HashSet<>((int) (compSet.size() * 2 / 0.75) + 1);

        int cx = sx, cy = sy;
        int startBx = bx, startBy = by;

        int guard = Math.max(64, compSet.size() * 8);
        while (guard-- > 0) {
            if (out.isEmpty() || out.get(out.size() - 1).x != cx || out.get(out.size() - 1).y != cy) {
                out.add(new Point(cx, cy));
            }

            if (cx == sx && cy == sy && bx == startBx && by == startBy && out.size() > 1) break;

            int scan = (dir + 6) & 7; // start search one step clockwise from the backtrack direction
            boolean advanced = false;
            for (int k = 0; k < 8; k++) {
                int nd = (scan + k) & 7;
                int nx = cx + NEIGHBOR_OFFSETS[nd][0];
                int ny = cy + NEIGHBOR_OFFSETS[nd][1];
                long nc = MathEx.encode(nx, ny);
                if (compSet.contains(nc)) {
                    long e = encodeEdge(cx, cy, nx, ny);   // directed edge
                    if (seenEdges.contains(e)) {           // cycle on the same edge: stop
                        guard = 0;
                    }
                    seenEdges.add(e);
                    bx = cx; by = cy;
                    cx = nx; cy = ny;
                    dir = nd;
                    advanced = true;
                    break;
                }
            }

            if (!advanced) {
                int bestDx = 0, bestDy = 0, best = Integer.MAX_VALUE;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = cx + dx, ny = cy + dy;
                        long nc = MathEx.encode(nx, ny);
                        if (compSet.contains(nc)) {
                            int d2 = dx * dx + dy * dy;
                            if (d2 < best) { best = d2; bestDx = dx; bestDy = dy; }
                        }
                    }
                }
                if (best != Integer.MAX_VALUE) {
                    long e = encodeEdge(cx, cy, cx + bestDx, cy + bestDy);
                    if (seenEdges.contains(e)) break;
                    seenEdges.add(e);
                    bx = cx; by = cy;
                    cx += bestDx; cy += bestDy;
                    dir = dirIndex(bestDx, bestDy);
                    continue;
                }
                break;
            }
        }

        // de-dup and remove spikes A,B,A
        List<Point> clean = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) {
            Point p = out.get(i);
            int m = clean.size();
            if (m > 0) {
                Point last = clean.get(m - 1);
                if (last.x == p.x && last.y == p.y) continue;
                if (m >= 2) {
                    Point prev = clean.get(m - 2);
                    if (prev.x == p.x && prev.y == p.y) { clean.remove(m - 1); continue; }
                }
            }
            clean.add(p);
        }
        return clean;
    }

    private static long encodeEdge(int x1, int y1, int x2, int y2) {
        // directed edge key
        long a = MathEx.encode(x1, y1);
        long b = MathEx.encode(x2, y2);
        return (a << 32) ^ (b & 0xffffffffL);
    }

    // Clockwise 8-neighborhood for chain-code walking: E, NE, N, NW, W, SW, S, SE
    private static final int[][] CHAIN8 = {
            { 1, 0}, { 1,-1}, { 0,-1}, {-1,-1},
            {-1, 0}, {-1, 1}, { 0, 1}, { 1, 1}
    };
    private static int dirIndexFromDelta(int dx, int dy) {
        if (dx ==  1 && dy ==  0) return 0;
        if (dx ==  1 && dy == -1) return 1;
        if (dx ==  0 && dy == -1) return 2;
        if (dx == -1 && dy == -1) return 3;
        if (dx == -1 && dy ==  0) return 4;
        if (dx == -1 && dy ==  1) return 5;
        if (dx ==  0 && dy ==  1) return 6;
        return 7; // (1,1)
    }

    private static long enc(int x, int y) { return ( (long) x << 32 ) ^ (y & 0xffffffffL); }

    private static void dedupeSpikes(List<Point> pts) {
        int w = 0;
        for (int i = 0; i < pts.size(); i++) {
            Point p = pts.get(i);
            if (w > 0) {
                Point last = pts.get(w - 1);
                if (last.x == p.x && last.y == p.y) continue;
                if (w >= 2) {
                    Point prev = pts.get(w - 2);
                    if (prev.x == p.x && prev.y == p.y) { w--; continue; }
                }
            }
            pts.set(w++, p);
        }
        while (pts.size() > w) pts.remove(pts.size() - 1);
    }

    private static int dirIndex(int dx, int dy) {
        if (dx == 1 && dy == 0) return 0;      // E
        if (dx == 1 && dy == -1) return 1;     // NE
        if (dx == 0 && dy == -1) return 2;     // N
        if (dx == -1 && dy == -1) return 3;    // NW
        if (dx == -1 && dy == 0) return 4;     // W
        if (dx == -1 && dy == 1) return 5;     // SW
        if (dx == 0 && dy == 1) return 6;      // S
        return 7;                              // SE
    }

    // Shoelace signed area. Positive => CCW. Works with java.awt.Point
    private static double signedArea(List<Point> poly) {
        int n = poly == null ? 0 : poly.size();
        if (n < 3) return 0.0;
        long sum = 0L;
        for (int i = 0; i < n; i++) {
            Point a = poly.get(i);
            Point b = poly.get((i + 1) % n);
            sum += (long) a.x * b.y - (long) b.x * a.y;
        }
        return 0.5 * (double) sum;
    }

    // Initialize empty object state
    private void initEmpty() {
        this.contour = Collections.emptyList();
        this.anglesFromTop = new double[0];
        this.rawAngles = new double[0];
        this.radii = new double[0];
        this.cumulativeArcLengths = new double[0];
        this.perimeter = 0.0;
        this.indexLookup = Collections.emptyMap();
        this.centroid = new java.awt.geom.Point2D.Double();
        this.topPoint = null;
        this.bottomPoint = null;
        this.leftPoint = null;
        this.rightPoint = null;
        this.edges = Collections.emptyList();
        this.xSet = Collections.<Integer>emptyNavigableSet();
    }



    void finalizeFromOrdered(List<Point> ordered) {
        // Empty case
        if (ordered == null || ordered.isEmpty()) {
            this.contour = Collections.emptyList();
            this.anglesFromTop = new double[0];
            this.rawAngles = new double[0];
            this.radii = new double[0];
            this.cumulativeArcLengths = new double[0];
            this.perimeter = 0.0;
            this.indexLookup = Collections.emptyMap();
            this.centroid = new Point2D.Double();
            this.topPoint = null;
            this.bottomPoint = null;
            this.leftPoint = null;
            this.rightPoint = null;
            this.edges = Collections.emptyList();
            this.xSet = Collections.<Integer>emptyNavigableSet();
            return;
        }

        // Centroid (polygon centroid over the boundary vertices)
        double cx = 0.0, cy = 0.0;
        for (Point p : ordered) { cx += p.x; cy += p.y; }
        cx /= ordered.size();
        cy /= ordered.size();
        this.centroid = new Point2D.Double(cx, cy);

        final int n = ordered.size();
        double[] angTop = new double[n];
        double[] angRaw = new double[n];
        double[] rad = new double[n];
        double[] arc = new double[n];
        Map<Long, Integer> idx = new HashMap<>((int) (n / 0.75) + 1);

        // Angle offset so that "top" aligns with 0 relative to +Y
        // First vertex is already the topmost-leftmost per constructor
        double dx0 = ordered.get(0).x - cx;
        double dy0 = ordered.get(0).y - cy;
        double offset = MathEx.normalizeAngle(Math.atan2(dy0, dx0) - Math.PI / 2.0);

        // Extremal points
        Point top = ordered.get(0);
        Point bottom = ordered.get(0);
        Point left = ordered.get(0);
        Point right = ordered.get(0);

        double cum = 0.0;
        arc[0] = 0.0;

        for (int i = 0; i < n; i++) {
            Point p = ordered.get(i);
            double dx = p.x - cx, dy = p.y - cy;

            double r = Math.hypot(dx, dy);
            double raw = MathEx.normalizeAngle(Math.atan2(dy, dx));
            double aTop = MathEx.normalizeAngle(raw - Math.PI / 2.0 - offset);

            rad[i] = r;
            angRaw[i] = raw;
            angTop[i] = aTop;

            if (i > 0) {
                cum += ordered.get(i - 1).distance(p);
                arc[i] = cum;
            }

            idx.put(encode(p.x, p.y), i);

            if (p.y > top.y || (p.y == top.y && p.x < top.x)) top = p;
            if (p.y < bottom.y || (p.y == bottom.y && p.x > bottom.x)) bottom = p;
            if (p.x < left.x || (p.x == left.x && p.y < left.y)) left = p;
            if (p.x > right.x || (p.x == right.x && p.y < right.y)) right = p;
        }

        double perim = n > 1 ? cum + ordered.get(n - 1).distance(ordered.get(0)) : 0.0;

        this.contour = Collections.unmodifiableList(ordered);
        this.anglesFromTop = angTop;
        this.rawAngles = angRaw;
        this.radii = rad;
        this.cumulativeArcLengths = arc;
        this.perimeter = perim;
        this.indexLookup = Collections.unmodifiableMap(idx);
        this.topPoint = new Point(top);
        this.bottomPoint = new Point(bottom);
        this.leftPoint = new Point(left);
        this.rightPoint = new Point(right);

        NavigableSet<Integer> xs = new TreeSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            xs.add(ordered.get(i).x);
        }
        this.xSet = Collections.unmodifiableNavigableSet(xs);

        // Uses your existing edge builder, if present
        this.edges = buildEdges(ordered);
    }



/*
public ShapeContour(PointCollection points) {
    if (points == null || points.isEmpty()) {
        this.contour = Collections.emptyList();
        this.anglesFromTop = new double[0];
        this.rawAngles = new double[0];
        this.radii = new double[0];
        this.cumulativeArcLengths = new double[0];
        this.perimeter = 0.0;
        this.indexLookup = Collections.emptyMap();
        this.centroid = new Point2D.Double();
        this.topPoint = null;
        this.bottomPoint = null;
        this.leftPoint = null;
        this.rightPoint = null;
        this.edges = Collections.emptyList();
        return;
    }

    // Build O(1) membership from PointCollection
    Set<Long> all = new HashSet<>((int) (points.size() / 0.75) + 1);
    for (Point p : points) all.add(encode(p.x, p.y));

    // Find start: topmost, then leftmost at that Y
    int startY = points.topY();
    Integer startX = null;
    for (Integer x : points.xes()) {
        if (points.getYesAtX(x).contains(startY)) { startX = x; break; }
    }
    if (startX == null) { // fallback scan (should not happen)
        int bestY = Integer.MIN_VALUE;
        int bestX = Integer.MAX_VALUE;
        for (Point p : points) {
            if (p.y > bestY || (p.y == bestY && p.x < bestX)) { bestY = p.y; bestX = p.x; }
        }
        startX = bestX;
        startY = bestY;
    }
    Point start = new Point(startX, startY);

    // Moore-neighbor tracing (8-connected), clockwise neighborhood
    final int[][] DIR = new int[][]{
            { 1,  0}, { 1,  1}, { 0,  1}, {-1,  1},
            {-1,  0}, {-1, -1}, { 0, -1}, { 1, -1}
    };

    // Initial back direction: pick any background neighbor around start
    int backDir = 0;
    for (int d = 0; d < 8; d++) {
        int nx = start.x + DIR[d][0];
        int ny = start.y + DIR[d][1];
        if (!all.contains(encode(nx, ny))) { backDir = d; break; }
    }

    List<Point> ordered = new ArrayList<>();
    int cx = start.x, cy = start.y;
    int initBack = backDir;
    int guard = Math.max(8, all.size() * 8); // safety
    boolean firstStep = true;

    while (guard-- > 0) {
        ordered.add(new Point(cx, cy));

        boolean advanced = false;
        for (int k = 1; k <= 8; k++) {
            int nd = (backDir + k) & 7;
            int nx = cx + DIR[nd][0];
            int ny = cy + DIR[nd][1];
            if (all.contains(encode(nx, ny))) {
                // move to neighbor; new back is opposite of nd
                cx = nx; cy = ny;
                backDir = (nd + 4) & 7;
                advanced = true;
                break;
            }
        }
        if (!advanced) break; // isolated pixel or degenerate

        if (!firstStep && cx == start.x && cy == start.y && backDir == initBack) {
            break; // closed the loop returning to start with same back
        }
        firstStep = false;
    }

    // Remove duplicated closing vertex if present
    if (ordered.size() >= 2) {
        Point last = ordered.get(ordered.size() - 1);
        if (last.x == start.x && last.y == start.y) ordered.remove(ordered.size() - 1);
    }

    // Ensure CCW orientation
    double signedArea = 0.0;
    for (int i = 0, n = ordered.size(); i < n; i++) {
        Point a = ordered.get(i);
        Point b = ordered.get((i + 1) % n);
        signedArea += (double) a.x * b.y - (double) b.x * a.y;
    }
    if (signedArea < 0) Collections.reverse(ordered);

    // Rotate so topmost then leftmost is index 0
    int topIdx = 0;
    for (int i = 1; i < ordered.size(); i++) {
        Point p0 = ordered.get(topIdx), pi = ordered.get(i);
        if (pi.y > p0.y || (pi.y == p0.y && pi.x < p0.x)) topIdx = i;
    }
    if (topIdx > 0) Collections.rotate(ordered, -topIdx);

    // Build fields (mirrors the original private ctor’s tail)
    this.centroid = computeCentroid(ordered);
    int n = ordered.size();
    double[] angles = new double[n];
    double[] raw = new double[n];
    double[] radii = new double[n];
    double[] arc = new double[n];
    Map<Long, Integer> lookup = new HashMap<>((int) (n / 0.75) + 1);

    double offset = 0.0;
    if (n > 0) {
        double dx0 = ordered.get(0).x - centroid.x;
        double dy0 = ordered.get(0).y - centroid.y;
        offset = normalizeAngle(Math.atan2(dy0, dx0) - Math.PI / 2.0);
        arc[0] = 0.0;
    }

    Point top = null, bottom = null, left = null, right = null;
    double cumulative = 0.0;

    for (int i = 0; i < n; i++) {
        Point p = ordered.get(i);
        double dx = p.x - centroid.x;
        double dy = p.y - centroid.y;

        double rawAng = Math.atan2(dy, dx);
        double angTop = normalizeAngle(rawAng - Math.PI / 2.0);

        raw[i] = normalizeAngle(rawAng);
        angles[i] = normalizeAngle(angTop - offset);
        radii[i] = Math.hypot(dx, dy);

        lookup.put(encode(p.x, p.y), i);

        if (top == null || p.y > top.y || (p.y == top.y && p.x < top.x)) top = new Point(p);
        if (bottom == null || p.y < bottom.y || (p.y == bottom.y && p.x > bottom.x)) bottom = new Point(p);
        if (left == null || p.x < left.x || (p.x == left.x && p.y < left.y)) left = new Point(p);
        if (right == null || p.x > right.x || (p.x == right.x && p.y < right.y)) right = new Point(p);

        if (i > 0) {
            cumulative += p.distance(ordered.get(i - 1));
            arc[i] = cumulative;
        }
    }

    double perim = n > 1 ? cumulative + ordered.get(n - 1).distance(ordered.get(0)) : 0.0;

    this.contour = Collections.unmodifiableList(ordered);
    this.anglesFromTop = angles;
    this.rawAngles = raw;
    this.radii = radii;
    this.cumulativeArcLengths = arc;
    this.perimeter = perim;
    this.indexLookup = Collections.unmodifiableMap(lookup);
    this.topPoint = top;
    this.bottomPoint = bottom;
    this.leftPoint = left;
    this.rightPoint = right;
    this.edges = buildEdges(ordered);
}*/

    public <T extends Collection<Point>> ShapeContour(T points) {
        this(points == null ? new PointCollection() :
                (points instanceof List ? new PointCollection((List<Point>) points)
                        : new PointCollection(new ArrayList<>(points))));
    }


    public ShapeContour(ShapeBounds bounds) {
        this(bounds == null ? Collections.emptyList() : collectPoints(bounds.get()));
    }
/*
    public ShapeContour(Collection<Point> points) {
        this(points == null ? Collections.emptyList() : points.stream().map(Point::new).collect(Collectors.toList()));
    }*/

    public static ShapeContour empty() {
        return EMPTY;
    }

    public int size() {
        return contour.size();
    }

    public boolean isEmpty() {
        return contour.isEmpty();
    }

    public Point get(int index) {
        requireIndex(index);
        return new Point(contour.get(index));
    }

    public double angleFromTop(int index) {
        requireIndex(index);
        return anglesFromTop[index];
    }

    public double rawAngle(int index) {
        requireIndex(index);
        return rawAngles[index];
    }

    public double radius(int index) {
        requireIndex(index);
        return radii[index];
    }

    public double arcLengthTo(int index) {
        requireIndex(index);
        return cumulativeArcLengths[index];
    }

    public double normalizedArcLengthTo(int index) {
        requireIndex(index);
        if (perimeter <= 0.0) return 0.0;
        return cumulativeArcLengths[index] / perimeter;
    }

    public double perimeter() {
        return perimeter;
    }

    public double area() {
        if (contour.size() < 3) return 0.0;
        double sum = 0.0;
        int n = contour.size();
        for (int i = 0; i < n; i++) {
            Point a = contour.get(i);
            Point b = contour.get((i + 1) % n);
            sum += (double) a.x * b.y - (double) b.x * a.y;
        }
        return Math.abs(sum) * 0.5;
    }

    public boolean isCounterClockwise() {
        double sum = 0.0;
        int n = contour.size();
        for (int i = 0; i < n; i++) {
            Point a = contour.get(i);
            Point b = contour.get((i + 1) % n);
            sum += (double) a.x * b.y - (double) b.x * a.y;
        }
        return sum > 0.0;
    }

    public Point topPoint() {
        return topPoint == null ? null : new Point(topPoint);
    }

    public Point bottomPoint() {
        return bottomPoint == null ? null : new Point(bottomPoint);
    }

    public Point leftPoint() {
        return leftPoint == null ? null : new Point(leftPoint);
    }

    public Point rightPoint() {
        return rightPoint == null ? null : new Point(rightPoint);
    }

    public int firstX() {
        return leftPoint == null ? 0 : leftPoint.x;
    }

    public int lastX() {
        return rightPoint == null ? 0 : rightPoint.x;
    }

    public int topY() {
        return topPoint == null ? 0 : topPoint.y;
    }

    public int bottomY() {
        return bottomPoint == null ? 0 : bottomPoint.y;
    }

    public int width() {
        return lastX() - firstX();
    }

    public int height() {
        return bottomY() - topY();
    }

    public Rectangle rect() {
        if (isEmpty()) return new Rectangle();
        return new Rectangle(firstX(), topY(), width(), height());
    }

    public Point2D.Double centroid() {
        return new Point2D.Double(centroid.x, centroid.y);
    }

    public Point nearestByAngle(double angleFromTop) {
        if (isEmpty()) return null;
        double normalized = normalizeAngle(angleFromTop);
        int idx = Arrays.binarySearch(anglesFromTop, normalized);
        if (idx >= 0) {
            return get(idx);
        }
        int insertion = -idx - 1;
        int next = insertion >= anglesFromTop.length ? 0 : insertion;
        int prev = insertion == 0 ? anglesFromTop.length - 1 : insertion - 1;

        double prevDelta = angleDelta(normalized, anglesFromTop[prev]);
        double nextDelta = angleDelta(normalized, anglesFromTop[next]);

        return prevDelta <= nextDelta ? get(prev) : get(next);
    }

    public Point nextPoint(Point point) {
        int idx = indexOf(point);
        if (idx == -1) return null;
        return get((idx + 1) % size());
    }

    public Point previousPoint(Point point) {
        int idx = indexOf(point);
        if (idx == -1) return null;
        return get((idx - 1 + size()) % size());
    }

    public double[] copyAnglesFromTop() {
        return Arrays.copyOf(anglesFromTop, anglesFromTop.length);
    }

    public double[] copyRawAngles() {
        return Arrays.copyOf(rawAngles, rawAngles.length);
    }

    public double[] copyRadii() {
        return Arrays.copyOf(radii, radii.length);
    }

    public double[] copyCumulativeArcLengths() {
        return Arrays.copyOf(cumulativeArcLengths, cumulativeArcLengths.length);
    }

    public ShapeContour translate(int dx, int dy) {
        if (isEmpty()) {
            return EMPTY;
        }
        List<Point> shifted = contour.stream()
                .map(pt -> new Point(pt.x + dx, pt.y + dy))
                .collect(Collectors.toList());
        return new ShapeContour(shifted);
    }

    public PointCollection toPointCollection() {
        return new PointCollection(toList());
    }

    public List<Point> toList() {
        return contour.stream().map(Point::new).collect(Collectors.toList());
    }

    public Stream<Point> stream() {
        return contour.stream().map(Point::new);
    }

    public IntStream indices() {
        return IntStream.range(0, size());
    }

    public List<Edge> edges() {
        return edges;
    }

    public Edge edge(int index) {
        if (edges.isEmpty()) {
            throw new IndexOutOfBoundsException("Contour does not contain edges.");
        }
        int normalized = ((index % edges.size()) + edges.size()) % edges.size();
        return edges.get(normalized);
    }

    public Stream<Edge> edgeStream() {
        return edges.stream();
    }

    public void forEachIndexed(BiConsumer<Point, Integer> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (int i = 0; i < contour.size(); i++) {
            consumer.accept(get(i), i);
        }
    }

    public void forEach(Consumer<? super Point> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (Point point : contour) {
            consumer.accept(new Point(point));
        }
    }
    public void forEachInside(Consumer<? super Point> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        Iterator<Point> it = insideIterator();
        while (it.hasNext()) {
            consumer.accept(new Point(it.next()));
        }
    }

    @Override
    public Iterator<Point> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return !contour.isEmpty() && index < contour.size();
            }

            @Override
            public Point next() {
                if (!hasNext()) throw new NoSuchElementException();
                return get(index++);
            }
        };
    }
    public Iterator<Point> insideIterator() {
        if (isEmpty()) return Collections.emptyIterator();

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point p : contour) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
        }
        final int minXf = minX;
        final int maxXf = maxX;
        final int minYf = minY;
        final int maxYf = maxY;

        return new Iterator<Point>() {
            private int x = minXf;
            private int y = minYf;
            private Point next = findNext();

            private Point findNext() {
                while (y <= maxYf) {
                    while (x <= maxXf) {
                        Point candidate = new Point(x, y);
                        x++;
                        if (inside(candidate)) {
                            return candidate;
                        }
                    }
                    x = minXf;
                    y++;
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Point next() {
                if (next == null) throw new NoSuchElementException();
                Point result = next;
                next = findNext();
                return result;
            }
        };
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ShapeContour)) return false;
        ShapeContour other = (ShapeContour) obj;
        return contour.equals(other.contour);
    }

    @Override
    public int hashCode() {
        return contour.hashCode();
    }

    @Override
    public String toString() {
        return "ShapeContour{vertices=" + size() + ", perimeter=" + perimeter + ", area=" + area() + "}";
    }

    private static List<Point> collectPoints(PointCollection pointCollection) {
        if (pointCollection == null || pointCollection.isEmpty()) {
            return Collections.emptyList();
        }
        pointCollection.onRaw();
        List<Point> collected = new ArrayList<>();
        pointCollection.forEachRaw((x, interval) -> {
            for (int y = interval.first(); y <= interval.last(); y++) {
                collected.add(new Point(x, y));
            }
        });
        return collected;
    }

    private static boolean isBoundary(Point point, Set<Long> allPoints) {
        for (int[] offset : NEIGHBOR_OFFSETS) {
            int nx = point.x + offset[0];
            int ny = point.y + offset[1];
            if (!allPoints.contains(encode(nx, ny))) {
                return true;
            }
        }
        return false;
    }

    private static Point2D.Double computeCentroid(List<Point> points) {
        double sumX = 0.0;
        double sumY = 0.0;
        int n = points.size();
        for (Point point : points) {
            sumX += point.x;
            sumY += point.y;
        }
        return new Point2D.Double(sumX / n, sumY / n);
    }

    private static ContourVertex toVertex(Point point, Point2D.Double centroid) {
        double dx = point.x - centroid.x;
        double dy = point.y - centroid.y;
        double rawAngle = Math.atan2(dy, dx);
        double angleFromTop = normalizeAngle(rawAngle - Math.PI / 2.0);
        double radius = Math.hypot(dx, dy);
        return new ContourVertex(point, rawAngle, angleFromTop, radius);
    }

    private static int locateTopVertexIndex(List<ContourVertex> vertices) {
        if (vertices.isEmpty()) return 0;
        ContourVertex topVertex = vertices.get(0);
        int topIndex = 0;
        for (int i = 1; i < vertices.size(); i++) {
            ContourVertex vertex = vertices.get(i);
            if (vertex.point.y > topVertex.point.y ||
                    (vertex.point.y == topVertex.point.y && vertex.point.x < topVertex.point.x)) {
                topVertex = vertex;
                topIndex = i;
            }
        }
        return topIndex;
    }

    private Point2D.Double pointOnPerimeter(double arcLength) {
        if (contour.isEmpty()) return null;
        double length = perimeter;
        double target = normalizeArcLength(arcLength, length);
        int n = contour.size();
        for (int i = 0; i < n; i++) {
            double start = cumulativeArcLengths[i];
            double end = (i == n - 1) ? length : cumulativeArcLengths[i + 1];
            if (target <= end || i == n - 1) {
                double segmentLength = end - start;
                if (segmentLength <= 0.0) {
                    Point snap = contour.get((i + 1) % n);
                    return new Point2D.Double(snap.x, snap.y);
                }
                double t = (target - start) / segmentLength;
                Point a = contour.get(i);
                Point b = contour.get((i + 1) % n);
                double x = a.x + (b.x - a.x) * t;
                double y = a.y + (b.y - a.y) * t;
                return new Point2D.Double(x, y);
            }
        }
        Point fallback = contour.get(0);
        return new Point2D.Double(fallback.x, fallback.y);
    }

    public Point2D.Double pointOnPerimeterNormalized(double normalized) {
        if (isEmpty()) return null;
        double norm01 = normalize(normalized);
        return pointOnPerimeter(norm01 * perimeter);
    }

    public Point2D.Double polarToCartesian(double angleFromTop) {
        if (isEmpty()) return null;
        double normalized = normalizeAngle(angleFromTop);
        double radius = radiusAtAngle(normalized);
        double raw = normalizeAngle(normalized + Math.PI / 2.0);
        double x = centroid.x + Math.cos(raw) * radius;
        double y = centroid.y + Math.sin(raw) * radius;
        return new Point2D.Double(x, y);
    }


    // --- public mutators ---
    public boolean add(Point p) { return add(p.x, p.y); }

    public boolean add(int x, int y) {
        long key = encode(x, y);
        if (indexLookup.containsKey(key)) return false;
        ArrayList<Point> pts = new ArrayList<>(contour);
        pts.add(new Point(x, y));
        rebuildFromVertices(pts);
        return true;
    }

    public boolean addAll(Collection<Point> points) {
        if (points == null || points.isEmpty()) return false;
        ArrayList<Point> pts = new ArrayList<>(contour);
        boolean changed = false;
        for (Point q : points) {
            long k = encode(q.x, q.y);
            if (!indexLookup.containsKey(k)) { pts.add(new Point(q)); changed = true; }
        }
        if (!changed) return false;
        rebuildFromVertices(pts);
        return true;
    }

    public boolean remove(Point p) { return remove(p.x, p.y); }

    public boolean remove(int x, int y) {
        long key = encode(x, y);
        Integer idx = indexLookup.get(key);
        if (idx == null) return false;
        ArrayList<Point> pts = new ArrayList<>(contour);
        // remove first occurrence matching coordinates
        for (int i = 0; i < pts.size(); i++) {
            Point t = pts.get(i);
            if (t.x == x && t.y == y) { pts.remove(i); break; }
        }
        rebuildFromVertices(pts);
        return true;
    }

    public boolean removeAll(Collection<Point> points) {
        if (points == null || points.isEmpty()) return false;
        if (contour.isEmpty()) return false;
        HashSet<Long> toRemove = new HashSet<>();
        for (Point q : points) toRemove.add(encode(q.x, q.y));
        boolean changed = false;
        ArrayList<Point> pts = new ArrayList<>(contour.size());
        for (Point t : contour) {
            if (toRemove.contains(encode(t.x, t.y))) { changed = true; continue; }
            pts.add(new Point(t));
        }
        if (!changed) return false;
        rebuildFromVertices(pts);
        return true;
    }



    public boolean contains(Point point) {
        if (point == null) return false;
        return indexLookup.containsKey(encode(point.x, point.y));
    }

    public boolean containsX(int x) {
        if (isEmpty()) return false;
        return xSet.contains(x);
    }

public boolean inside(Point point) {
    if (point == null || isEmpty()) return false;

    // On the contour itself counts as inside
    if (contains(point)) return true;

    // Fast reject via bounding box if we have extremal points
    if (leftPoint != null && rightPoint != null && topPoint != null && bottomPoint != null) {
        if (point.x < leftPoint.x || point.x > rightPoint.x ||
                point.y < bottomPoint.y || point.y > topPoint.y) {
            return false;
        }
    }

    int x = point.x;
    int y = point.y;
    int n = contour.size();
    if (n < 3) return false;

    boolean inside = false;

    // Even–odd ray casting to the +X direction
    for (int i = 0, j = n - 1; i < n; j = i++) {
        Point pi = contour.get(i);
        Point pj = contour.get(j);

        // Point lies exactly on this edge
        if (pointOnSegment(point, pj, pi)) {
            return true;
        }

        // Does the horizontal ray at y intersect edge (pj -> pi)?
        boolean intersects = ((pi.y > y) != (pj.y > y)) &&
                (x < (pj.x - pi.x) * (double) (y - pi.y) / (double) (pj.y - pi.y) + pi.x);
        if (intersects) {
            inside = !inside;
        }
    }

    return inside;
}

    public boolean inside(PointCollection points) {
        if (points == null || points.isEmpty()) return false;
        final boolean[] allInside = new boolean[] { true };
        points.forEach(p -> {
            if (!allInside[0]) return;
            if (!inside(p)) allInside[0] = false;
        });
        return allInside[0];
    }

    public boolean inside(ShapeBounds bounds) {
        if (bounds == null) return false;
        PointCollection points = bounds.get();
        if (points == null || points.isEmpty()) return false;
        return inside(points);
    }

    public boolean inside(ShapeContour other) {
        if (other == null || other.isEmpty()) return false;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point p : other.contour) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                Point candidate = new Point(x, y);
                if (other.inside(candidate) && !inside(candidate)) {
                    return false;
                }
            }
        }
        return true;
    }


    private static boolean pointOnSegment(Point p, Point a, Point b) {
        int minX = Math.min(a.x, b.x);
        int maxX = Math.max(a.x, b.x);
        int minY = Math.min(a.y, b.y);
        int maxY = Math.max(a.y, b.y);
        if (p.x < minX || p.x > maxX || p.y < minY || p.y > maxY) {
            return false;
        }
        long dx1 = (long) b.x - (long) a.x;
        long dy1 = (long) b.y - (long) a.y;
        long dx2 = (long) p.x - (long) a.x;
        long dy2 = (long) p.y - (long) a.y;
        long cross = dx1 * dy2 - dy1 * dx2;
        return cross == 0L;
    }




    public int indexOf(Point point) {
        if (point == null) return -1;
        return indexLookup.getOrDefault(encode(point.x, point.y), -1);
    }

    public Set<Integer> xes() {
        return contour.stream().map(p -> p.x).collect(Collectors.toCollection(TreeSet::new));
    }

    public Set<Integer> yes() {
        return contour.stream().map(p -> p.y).collect(Collectors.toCollection(TreeSet::new));
    }
    // boundary if any of these (W,E,N,S) missing
    private static final int[][] N4 = {{-1,0},{1,0},{0,-1},{0,1}};


    // --- private rebuild using current vertex set (treats given points as boundary vertices) ---
    private void rebuildFromVertices(List<Point> verticesIn) {
        if (verticesIn == null || verticesIn.isEmpty()) {
            // reset to empty state
            contour = Collections.emptyList();
            anglesFromTop = new double[0];
            rawAngles = new double[0];
            radii = new double[0];
            cumulativeArcLengths = new double[0];
            perimeter = 0;
            indexLookup = Collections.emptyMap();
            centroid = new Point2D.Double();
            topPoint = bottomPoint = leftPoint = rightPoint = null;
            edges = Collections.emptyList();
            xSet = Collections.<Integer>emptyNavigableSet();
            return;
        }

        Point2D.Double cen = computeCentroid(verticesIn);

        List<ContourVertex> verts = verticesIn.stream()
                .map(pt -> toVertex(pt, cen))
                .sorted(Comparator
                        .comparingDouble((ContourVertex v) -> v.angleFromTop)
                        .thenComparing((ContourVertex v) -> -v.radius)
                        .thenComparingInt(v -> v.point.y)
                        .thenComparingInt(v -> v.point.x))
                .collect(Collectors.toCollection(ArrayList::new));

        int startIndex = locateTopVertexIndex(verts);
        if (startIndex > 0) Collections.rotate(verts, -startIndex);

        double offset = verts.get(0).angleFromTop;
        int n = verts.size();

        List<Point> ordered = new ArrayList<>(n);
        double[] ang = new double[n], raw = new double[n], rad = new double[n], arc = new double[n];
        Map<Long, Integer> lookup = new HashMap<>((int) (n / 0.75f) + 1);

        Point top = null, bottom = null, left = null, right = null;
        double cumulative = 0;
        if (n > 0) arc[0] = 0;

        for (int i = 0; i < n; i++) {
            ContourVertex v = verts.get(i);
            Point p = new Point(v.point);

            double aFromTop = normalizeAngle(v.angleFromTop - offset);
            double rawA = normalizeAngle(v.rawAngle);

            ordered.add(p);
            ang[i] = aFromTop;
            raw[i] = rawA;
            rad[i] = v.radius;
            lookup.put(encode(p.x, p.y), i);

            if (top == null || p.y > top.y || (p.y == top.y && p.x < top.x)) top = new Point(p);
            if (bottom == null || p.y < bottom.y || (p.y == bottom.y && p.x > bottom.x)) bottom = new Point(p);
            if (left == null || p.x < left.x || (p.x == left.x && p.y < left.y)) left = new Point(p);
            if (right == null || p.x > right.x || (p.x == right.x && p.y < right.y)) right = new Point(p);

            if (i > 0) {
                cumulative += p.distance(ordered.get(i - 1));
                arc[i] = cumulative;
            }
        }

        double peri = n > 1 ? cumulative + ordered.get(n - 1).distance(ordered.get(0)) : 0;

        contour = Collections.unmodifiableList(ordered);
        anglesFromTop = ang;
        rawAngles = raw;
        radii = rad;
        cumulativeArcLengths = arc;
        perimeter = peri;
        indexLookup = Collections.unmodifiableMap(lookup);
        centroid = cen;
        topPoint = top;
        bottomPoint = bottom;
        leftPoint = left;
        rightPoint = right;

        NavigableSet<Integer> xs = new TreeSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            xs.add(ordered.get(i).x);
        }
        xSet = Collections.unmodifiableNavigableSet(xs);

        edges = buildEdges(ordered);
    }




    public NavigableMap<Double, Point> asAngleMap() {
        NavigableMap<Double, Point> map = new TreeMap<>();
        for (int i = 0; i < anglesFromTop.length; i++) {
            map.put(anglesFromTop[i], get(i));
        }
        return Collections.unmodifiableNavigableMap(map);
    }

    public int indexByAngle(double angleFromTop) {
        Point point = nearestByAngle(angleFromTop);
        return point == null ? -1 : indexOf(point);
    }

    public double radiusAtAngle(double angleFromTop) {
        if (isEmpty()) return 0.0;
        double normalized = normalizeAngle(angleFromTop);
        int idx = indexByAngle(normalized);
        if (idx >= 0) return radii[idx];
        Point nearest = nearestByAngle(normalized);
        if (nearest == null) return 0.0;
        double dx = nearest.x - centroid.x;
        double dy = nearest.y - centroid.y;
        return Math.hypot(dx, dy);
    }

    public Point pointAtArcLength(double arcLength) {
        if (isEmpty()) return null;
        if (perimeter <= 0.0) return get(0);
        double normalized = normalizeArcLength(arcLength, perimeter);
        return get(indexAtArcLength(normalized));
    }

    public int indexByArcLength(double arcLength) {
        if (isEmpty()) return -1;
        if (perimeter <= 0.0) return 0;
        double normalized = normalizeArcLength(arcLength, perimeter);
        return indexAtArcLength(normalized);
    }

    public Point pointAtNormalizedArc(double normalized) {
        if (isEmpty()) return null;
        return get(indexByNormalizedArc(normalize(normalized)));
    }

    public int indexByNormalizedArc(double normalized) {
        if (isEmpty()) throw new IllegalArgumentException("Contour is empty.");
        double norm01 = normalize(normalized);
        return indexAtArcLength(norm01 * perimeter);
    }

    public Point nearest(Point point) {
        if (point == null || contour.isEmpty()) return null;
        double bestDistance = Double.MAX_VALUE;
        Point bestPoint = null;
        for (Point candidate : contour) {
            double distance = point.distance(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = candidate;
            }
        }
        return bestPoint == null ? null : new Point(bestPoint);
    }

    public Point nearest(Point2D point) {
        if (point == null || contour.isEmpty()) return null;
        double bestDistance = Double.MAX_VALUE;
        Point bestPoint = null;
        for (Point candidate : contour) {
            double distance = point.distance(candidate.x, candidate.y);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = candidate;
            }
        }
        return bestPoint == null ? null : new Point(bestPoint);
    }

    public Point interpolate(Point2D generalizedPoint) {
        if (generalizedPoint == null || contour.isEmpty()) return null;
        double x = generalizedPoint.getX();
        double y = generalizedPoint.getY();

        if (indexLookup.containsKey(encode((int) Math.round(x), (int) Math.round(y)))) {
            return new Point((int) Math.round(x), (int) Math.round(y));
        }

        double bestDistance = Double.MAX_VALUE;
        Point bestPoint = null;
        for (int i = 0; i < contour.size(); i++) {
            Point a = contour.get(i);
            Point b = contour.get((i + 1) % contour.size());
            double distance = distanceToSegment(generalizedPoint, a, b);
            if (distance < bestDistance) {
                bestDistance = distance;
                double t = projectionFactor(generalizedPoint, a, b);
                if (t <= 0.0) {
                    bestPoint = new Point(a);
                } else if (t >= 1.0) {
                    bestPoint = new Point(b);
                } else {
                    double interpX = a.x + (b.x - a.x) * t;
                    double interpY = a.y + (b.y - a.y) * t;
                    bestPoint = new Point((int) Math.round(interpX), (int) Math.round(interpY));
                }
            }
        }
        return bestPoint == null ? null : new Point(bestPoint);
    }

    private static double distanceToSegment(Point2D p, Point a, Point b) {
        double lineMag = a.distance(b);
        if (lineMag == 0.0) return p.distance(a);
        double t = projectionFactor(p, a, b);
        if (t <= 0.0) return p.distance(a);
        if (t >= 1.0) return p.distance(b);
        double projX = a.x + t * (b.x - a.x);
        double projY = a.y + t * (b.y - a.y);
        return p.distance(projX, projY);
    }


    // In ShapeContour.java
    public double laplacianAt(Point p) {
        if (isEmpty()) return Double.NaN;

        // 1) exact vertex -> use neighbors
        Integer hit = indexLookup.get(encode(p.x, p.y));
        if (hit != null) {
            int n = contour.size();
            Point a = contour.get((hit - 1 + n) % n);
            Point b = contour.get(hit);
            Point c = contour.get((hit + 1) % n);
            return curvature(a.x, a.y, b.x, b.y, c.x, c.y);
        }

        // 2) otherwise, find nearest edge by projection
        int n = contour.size();
        double bestD2 = Double.POSITIVE_INFINITY;
        int bestI = 0;
        double bestT = 0.0;

        Point2D.Double q = new Point2D.Double(p.x, p.y);
        for (int i = 0; i < n; i++) {
            Point a = contour.get(i);
            Point b = contour.get((i + 1) % n);
            double t = projectionFactor(q, a, b);
            if (t < 0) t = 0; else if (t > 1) t = 1;
            double dx = a.x + t * (b.x - a.x) - p.x, dy = a.y + t * (b.y - a.y) - p.y;
            double d2 = dx * dx + dy * dy;
            if (d2 < bestD2) { bestD2 = d2; bestI = i; bestT = t; }
        }

        Point a = contour.get(bestI);
        Point b = contour.get((bestI + 1) % n);
        double segLen = a.distance(b);
        double s0 = cumulativeArcLengths[bestI] + bestT * segLen;

        // adaptive arc step using local neighborhood
        double lenPrev = contour.get((bestI - 1 + n) % n).distance(a);
        double lenNext = b.distance(contour.get((bestI + 2) % n));
        double delta = Math.max(1.0, 0.25 * (lenPrev + segLen + lenNext) / 3.0);

        Point2D.Double pm = pointOnPerimeter(s0 - delta);
        Point2D.Double p0 = new Point2D.Double(a.x + bestT * (b.x - a.x), a.y + bestT * (b.y - a.y));
        Point2D.Double pp = pointOnPerimeter(s0 + delta);

        return curvature(pm.x, pm.y, p0.x, p0.y, pp.x, pp.y);
    }

    // keep private inside ShapeContour

    public List<Double> laplacians() {
        int n = this.size();
        List<Double> seq = new ArrayList<>(n);
        if (n < 3) return seq;

        for (int i = 0; i < n; i++) {
            double v = this.laplacianAt(this.get(i));
            seq.add(Double.isFinite(v) ? v : 0);
        }
        return seq;
    }

    public List<Double> secondDerivatives() {
        int n = this.size();
        java.util.List<Double> out = new java.util.ArrayList<>(n);
        if (n < 3) {
            for (int i = 0; i < n; i++) out.add(0.0);
            return out;
        }

        for (int i = 0; i < n; i++) {
            java.awt.Point pm = this.get((i - 1 + n) % n);
            java.awt.Point p0 = this.get(i);
            java.awt.Point pp = this.get((i + 1) % n);

            double h1 = p0.x - pm.x;
            double h2 = pp.x - p0.x;

            if (h1 == 0.0 || h2 == 0.0 || (h1 + h2) == 0.0) {
                out.add(0.0);
                continue;
            }

            double denomL = h1 * (h1 + h2);
            double denomC = h1 * h2;
            double denomR = h2 * (h1 + h2);

            double ypp = 2.0 * (divide(pm.y, denomL) - divide(p0.y, denomC) + divide(pp.y, denomR));
            out.add(ypp);
        }
        return out;
    }






    private static double projectionFactor(Point2D p, Point a, Point b) {
        double ax = a.x;
        double ay = a.y;
        double bx = b.x;
        double by = b.y;

        double dx = bx - ax;
        double dy = by - ay;

        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0.0) return 0.0;

        return ((p.getX() - ax) * dx + (p.getY() - ay) * dy) / lengthSquared;
    }

    private static List<Edge> buildEdges(List<Point> orderedPoints) {
        int n = orderedPoints.size();
        if (n < 2) {
            return Collections.emptyList();
        }
        List<Edge> edges = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Point start = orderedPoints.get(i);
            Point end = orderedPoints.get((i + 1) % n);
            edges.add(new Edge(start, end));
        }
        return Collections.unmodifiableList(edges);
    }


    private static double ccwDelta(double start, double end) {
        return end >= start ? end - start : (Math.PI * 2.0 - start + end);
    }

    private static double normalizeArcLength(double arcLength, double perimeter) {
        if (perimeter <= 0.0) {
            return 0.0;
        }
        double normalized = arcLength % perimeter;
        if (normalized < 0.0) normalized += perimeter;
        return normalized;
    }

    private int indexAtArcLength(double arcLength) {
        if (contour.isEmpty()) return -1;
        if (perimeter <= 0.0) return 0;
        double norm = normalizeArcLength(arcLength, perimeter);
        int idx = Arrays.binarySearch(cumulativeArcLengths, norm);
        if (idx >= 0) return idx;
        int insertion = -idx - 1;
        if (insertion >= cumulativeArcLengths.length) {
            return cumulativeArcLengths.length - 1;
        }
        return insertion;
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= contour.size()) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + contour.size());
        }
    }

    private static final class ContourVertex {
        final Point point;
        final double rawAngle;
        final double angleFromTop;
        final double radius;

        ContourVertex(Point point, double rawAngle, double angleFromTop, double radius) {
            this.point = new Point(point);
            this.rawAngle = rawAngle;
            this.angleFromTop = angleFromTop;
            this.radius = radius;
        }
    }

    public static final class Edge {
        private final Point start;
        private final Point end;
        private final double length;
        private final double heading;
        private final double angleFromTop;

        private Edge(Point start, Point end) {
            this.start = new Point(start);
            this.end = new Point(end);
            this.length = start.distance(end);
            this.heading = Math.atan2(end.y - start.y, end.x - start.x);
            this.angleFromTop = normalizeAngle(this.heading - Math.PI / 2.0);
        }

        public Point start() {
            return new Point(start);
        }

        public Point end() {
            return new Point(end);
        }

        public double length() {
            return length;
        }

        public double heading() {
            return heading;
        }

        public double angleFromTop() {
            return angleFromTop;
        }

        public Point2D.Double midpoint() {
            return new Point2D.Double((start.x + end.x) / 2.0, (start.y + end.y) / 2.0);
        }
    }




    public static void main(String[] args) {
        Random rnd = new Random(7122222); // fixed seed for repeatability

        // === ShapeBalance (ShapeBounds) curvature tests ===
        System.out.println("=== ShapeBalance (ShapeBounds) Curvature Tests ===");

        // random rectangular cluster
        PointCollection rectA = new PointCollection();
        int w = 80, h = 40, nA = 800;
        for (int i = 0; i < nA; i++) {
            int x = rnd.nextInt(w) - w / 2;
            int y = rnd.nextInt(h) - h / 2;
            rectA.add(new Point(x, y));
        }
        ShapeBounds shapeA = new ShapeBounds(rectA);

        // rotate each point by random angle around origin
        double angle = Math.toRadians(30);
        PointCollection rectB = new PointCollection();
        for (Point p : rectA) {
            int xr = (int)Math.round(p.x * Math.cos(angle) - p.y * Math.sin(angle));
            int yr = (int)Math.round(p.x * Math.sin(angle) + p.y * Math.cos(angle));
            rectB.add(new Point(xr, yr));
        }
        ShapeBounds shapeB = new ShapeBounds(rectB);

        Point testA = new Point(0, h / 4);
        Point testB = new Point(10, 5);
        double kA = shapeA.laplacianAt(testA);
        double kB = shapeB.laplacianAt(testB);

        System.out.printf("ShapeBalance A curvature at %s = %.6f%n", testA, kA);
        System.out.printf("ShapeBalance B curvature at %s = %.6f%n", testB, kB);
        if (!Double.isNaN(kA) && !Double.isNaN(kB))
            System.out.printf("Difference (B - A) = %.6f%n", kB - kA);

        // === ShapeContour curvature tests ===
        // === ShapeContour curvature tests ===
        System.out.println("\n=== ShapeContour Curvature Tests ===");

// random blob A
        PointCollection blobA = new PointCollection();
        int numPts = 300;
        double radius = 50;
        for (int i = 0; i < numPts; i++) {
            double theta = 2 * Math.PI * rnd.nextDouble();
            double r = radius * (0.6 + 0.4 * rnd.nextDouble());
            int x = (int)Math.round(r * Math.cos(theta));
            int y = (int)Math.round(r * Math.sin(theta));
            blobA.add(new Point(x, y));
        }
        ShapeContour contourA = new ShapeContour(blobA);

// rotated blob B
        double rot = Math.toRadians(245);
        PointCollection blobB = new PointCollection();
        for (Point p : blobA) {
            int xr = (int)Math.round(p.x * Math.cos(rot) - p.y * Math.sin(rot));
            int yr = (int)Math.round(p.x * Math.sin(rot) + p.y * Math.cos(rot));
            blobB.add(new Point(xr, yr));
        }
        ShapeContour contourB = new ShapeContour(blobB);

// choose a reproducible test point from blobA and rotate it for blobB

        Iterator<Point> ag = blobA.iterator();
        ag.next();ag.next();ag.next();ag.next();ag.next();ag.next();
        Point ptCA = new Point(ag.next()); // arbitrary but fixed test location
        int xRot = (int)Math.round(ptCA.x * Math.cos(rot) - ptCA.y * Math.sin(rot));
        int yRot = (int)Math.round(ptCA.x * Math.sin(rot) + ptCA.y * Math.cos(rot));
        Point ptCB = new Point(xRot, yRot);

        double kcA = contourA.laplacianAt(ptCA);
        double kcB = contourB.laplacianAt(ptCB);

        System.out.printf("ShapeContour A curvature at %s = %.6f%n", ptCA, kcA);
        System.out.printf("ShapeContour B curvature at %s = %.6f%n", ptCB, kcB);
        if (!Double.isNaN(kcA) && !Double.isNaN(kcB))
            System.out.printf("Difference (B - A) = %.6f%n", kcB - kcA);

    }






//    public static void main(String[] args) {
///*
//        int r = 50;
//        Map<Integer, IntegerBounds> m1 = new TreeMap<>();
//        for (int x = -r; x <= r; x++) {
//            int y = (int)Math.round(Math.sqrt(r * r - x * x));
//            m1.put(x, new IntegerBounds(-y, y));
//        }
//        ShapeBounds sb1 = new ShapeBounds(() -> m1);*/
//        int w = 80; // horizontal span
//        Map<Integer, IntegerBounds> m1 = new TreeMap<>();
//
//// Define an irregular, asymmetric "blob" function
////   yTop(x) = base + A*sin(x/scale) + B*sin(2x/scale) + C*cos(3x/scale)
////   yBot(x) = -base + smaller amplitude mix
//        double base = 25.0;
//        double A = 6.5, B = 4.0, C = 2.5;
//        double scale = 8.0;
//
//        for (int x = -w / 2; x <= w / 2; x++) {
//            double fx = x / scale;
//            int yTop = (int) Math.round(base
//                    + A * Math.sin(fx)
//                    + B * Math.sin(2 * fx)
//                    + C * Math.cos(3 * fx));
//
//            int yBot = (int) Math.round(-base
//                    + 0.4 * A * Math.sin(fx + 0.6)
//                    + 0.6 * B * Math.sin(2 * fx + 1.1)
//                    + 0.5 * C * Math.cos(3 * fx - 0.4));
//
//            if (yBot > yTop) { int t = yTop; yTop = yBot; yBot = t; }
//
//            m1.put(x, new IntegerBounds(yBot, yTop));
//        }
//
//        ShapeBounds sb1 = new ShapeBounds(() -> m1);
//
///*
//        double theta = Math.toRadians(35);
//        Map<Integer, IntegerBounds> m2 = new TreeMap<>();
//
//// helper: add/merge a single (x,y) into bounds map
//        final java.util.function.BiConsumer<Integer,Integer> put = (xx, yy) -> {
//            m2.compute(xx, (k, b) -> {
//                if (b == null) return new IntegerBounds(yy, yy);
//                int lo = Math.min(b.getLowerBound(), yy);
//                int hi = Math.max(b.getUpperBound(), yy);
//                return new IntegerBounds(lo, hi);
//            });
//        };*/
//        int w2 = 80; // horizontal span similar to sb1
//        Map<Integer, IntegerBounds> m2 = new TreeMap<>();
//
//// Define a different function — a tilted, asymmetric "valley" shape
//// yTop(x) and yBot(x) each have different phase and slope trends
//        double theta = Math.toRadians(35);
//
//        double base2 = 20.0;
//        double A2 = 10.0, B2 = 6.0, C2 = 3.0;
//        double scale2 = 10.0;
//
//        for (int x = -w2 / 2; x <= w2 / 2; x++) {
//            double fx = x / scale2;
//
//            // Top boundary: tilted downward to the right, multiple frequencies
//            int yTop = (int) Math.round(base2
//                    + A2 * Math.sin(fx + 0.8)
//                    + B2 * Math.cos(2.5 * fx)
//                    + C2 * Math.sin(3.5 * fx)
//                    - 0.25 * x); // tilt term
//
//            // Bottom boundary: tilted upward, smaller amplitude and opposite phase
//            int yBot = (int) Math.round(-base2
//                    + 0.6 * A2 * Math.sin(fx - 0.5)
//                    + 0.5 * B2 * Math.cos(2 * fx + 1.1)
//                    + 0.4 * C2 * Math.sin(3 * fx + 0.7)
//                    + 0.15 * x); // opposite tilt
//
//            if (yBot > yTop) { int t = yTop; yTop = yBot; yBot = t; }
//
//            m2.put(x, new IntegerBounds(yBot, yTop));
//        }
//
//        ShapeBounds sb2 = new ShapeBounds(() -> m2);
//
///*
//        for (int x = -r; x <= r; x++) {
//            int y = (int) Math.round(Math.sqrt(r * r - x * x));
//
//            Point pTop = rotate(new Point(x,  y), theta);
//            Point pBot = rotate(new Point(x, -y), theta);
//
//            // write each rotated point to its own x-key
//            put.accept(pTop.x, pTop.y);
//            put.accept(pBot.x, pBot.y);
//
//            // optional densification to fill columns between pTop and pBot
//            // avoids gaps when the rotated vertical span crosses many x's
//            int steps = Math.max(Math.abs(pTop.x - pBot.x), Math.abs(pTop.y - pBot.y));
//            for (int s = 1; s < steps; s++) {
//                double t = s / (double) steps;
//                int xr = (int) Math.round(pTop.x + t * (pBot.x - pTop.x));
//                int yr = (int) Math.round(pTop.y + t * (pBot.y - pTop.y));
//                put.accept(xr, yr);
//            }
//        }*/
//
//        /*
//        // Sample and rotate boundary points from sb1
//        for (Map.Entry<Integer,IntegerBounds> e : m1.entrySet()) {
//            int x = e.getKey();
//            int yTop = e.getValue().getUpperBound();
//            int yBot = e.getValue().getLowerBound();
//
//            // rotate both top and bottom endpoints, fill intermediate
//            Point pTop = rotate(new Point(x, yTop), theta);
//            Point pBot = rotate(new Point(x, yBot), theta);
//
//            int steps = Math.max(Math.abs(pTop.x - pBot.x), Math.abs(pTop.y - pBot.y));
//            for (int s = 0; s <= steps; s++) {
//                double t = s / (double) steps;
//                int xr = (int) Math.round(pTop.x + t * (pBot.x - pTop.x));
//                int yr = (int) Math.round(pTop.y + t * (pBot.y - pTop.y));
//                put.accept(xr, yr);
//            }
//        }
//
//        ShapeBounds sb2 = new ShapeBounds(() -> m2);*/
//
//
//
////        Point p1 = new Point(20, (int)Math.round(Math.sqrt(r * r - 20 * 20)));
//        List<Map.Entry<Integer,IntegerBounds>> entries =
//                new ArrayList<>(sb1.getBounds().entrySet());
//        Map.Entry<Integer,IntegerBounds> e = entries.get(
//                new java.util.Random().nextInt(entries.size()));
//        int xSel = e.getKey();
//        IntegerBounds col = e.getValue();
//        int ySel = new java.util.Random().nextBoolean()
//                ? col.getUpperBound() : col.getLowerBound();
//        Point p1 = new Point(xSel, ySel);
//
//        Point p2 = rotate(p1, theta);
//
//        double k1 = sb1.laplacianAt(p1);
//        double k2 = sb2.laplacianAt(p2);
//
//        System.out.println("Original point: " + p1 + "  curvature=" + k1);
//        System.out.println("Rotated point:  " + p2 + "  curvature=" + k2);
//        System.out.println("Equal within tol? " + (Math.abs(k1 - k2) < 1e-6));
//    }
/*
    public static void main(String[] args) {

//        int r = 50;
//        Map<Integer, IntegerBounds> m1 = new TreeMap<>();
//        for (int x = -r; x <= r; x++) {
//            int y = (int)Math.round(Math.sqrt(r * r - x * x));
//            m1.put(x, new IntegerBounds(-y, y));
//        }
//        ShapeBounds sb1 = new ShapeBounds(() -> m1);
        int w = 80; // horizontal span
        Map<Integer, IntegerBounds> m1 = new TreeMap<>();

// Define an irregular, asymmetric "blob" function
//   yTop(x) = base + A*sin(x/scale) + B*sin(2x/scale) + C*cos(3x/scale)
//   yBot(x) = -base + smaller amplitude mix
        double base = 25.0;
        double A = 6.5, B = 4.0, C = 2.5;
        double scale = 8.0;

        for (int x = -w / 2; x <= w / 2; x++) {
            double fx = x / scale;
            int yTop = (int) Math.round(base
                    + A * Math.sin(fx)
                    + B * Math.sin(2 * fx)
                    + C * Math.cos(3 * fx));

            int yBot = (int) Math.round(-base
                    + 0.4 * A * Math.sin(fx + 0.6)
                    + 0.6 * B * Math.sin(2 * fx + 1.1)
                    + 0.5 * C * Math.cos(3 * fx - 0.4));

            if (yBot > yTop) { int t = yTop; yTop = yBot; yBot = t; }

            m1.put(x, new IntegerBounds(yBot, yTop));
        }

        ShapeBounds sb1 = new ShapeBounds(() -> m1);


//        double theta = Math.toRadians(35);
//        Map<Integer, IntegerBounds> m2 = new TreeMap<>();
//
//// helper: add/merge a single (x,y) into bounds map
//        final java.util.function.BiConsumer<Integer,Integer> put = (xx, yy) -> {
//            m2.compute(xx, (k, b) -> {
//                if (b == null) return new IntegerBounds(yy, yy);
//                int lo = Math.min(b.getLowerBound(), yy);
//                int hi = Math.max(b.getUpperBound(), yy);
//                return new IntegerBounds(lo, hi);
//            });
        };
        int w2 = 80; // horizontal span similar to sb1
        Map<Integer, IntegerBounds> m2 = new TreeMap<>();

// Define a different function �?" a tilted, asymmetric "valley" shape
// yTop(x) and yBot(x) each have different phase and slope trends
        double theta = Math.toRadians(35);

        double base2 = 20.0;
        double A2 = 10.0, B2 = 6.0, C2 = 3.0;
        double scale2 = 10.0;

        for (int x = -w2 / 2; x <= w2 / 2; x++) {
            double fx = x / scale2;

            // Top boundary: tilted downward to the right, multiple frequencies
            int yTop = (int) Math.round(base2
                    + A2 * Math.sin(fx + 0.8)
                    + B2 * Math.cos(2.5 * fx)
                    + C2 * Math.sin(3.5 * fx)
                    - 0.25 * x); // tilt term

            // Bottom boundary: tilted upward, smaller amplitude and opposite phase
            int yBot = (int) Math.round(-base2
                    + 0.6 * A2 * Math.sin(fx - 0.5)
                    + 0.5 * B2 * Math.cos(2 * fx + 1.1)
                    + 0.4 * C2 * Math.sin(3 * fx + 0.7)
                    + 0.15 * x); // opposite tilt

            if (yBot > yTop) { int t = yTop; yTop = yBot; yBot = t; }

            m2.put(x, new IntegerBounds(yBot, yTop));
        }

        ShapeBounds sb2 = new ShapeBounds(() -> m2);


//        for (int x = -r; x <= r; x++) {
//            int y = (int) Math.round(Math.sqrt(r * r - x * x));
//
//            Point pTop = rotate(new Point(x,  y), theta);
//            Point pBot = rotate(new Point(x, -y), theta);
//
//            // write each rotated point to its own x-key
//            put.accept(pTop.x, pTop.y);
//            put.accept(pBot.x, pBot.y);
//
//            // optional densification to fill columns between pTop and pBot
//            // avoids gaps when the rotated vertical span crosses many x's
//            int steps = Math.max(Math.abs(pTop.x - pBot.x), Math.abs(pTop.y - pBot.y));
//            for (int s = 1; s < steps; s++) {
//                double t = s / (double) steps;
//                int xr = (int) Math.round(pTop.x + t * (pBot.x - pTop.x));
//                int yr = (int) Math.round(pTop.y + t * (pBot.y - pTop.y));
//                put.accept(xr, yr);
//            }
//        }

        
        // Sample and rotate boundary points from sb1
//        for (Map.Entry<Integer,IntegerBounds> e : m1.entrySet()) {
//            int x = e.getKey();
//            int yTop = e.getValue().getUpperBound();
//            int yBot = e.getValue().getLowerBound();
//
//            // rotate both top and bottom endpoints, fill intermediate
//            Point pTop = rotate(new Point(x, yTop), theta);
//            Point pBot = rotate(new Point(x, yBot), theta);
//
//            int steps = Math.max(Math.abs(pTop.x - pBot.x), Math.abs(pTop.y - pBot.y));
//            for (int s = 0; s <= steps; s++) {
//                double t = s / (double) steps;
//                int xr = (int) Math.round(pTop.x + t * (pBot.x - pTop.x));
//                int yr = (int) Math.round(pTop.y + t * (pBot.y - pTop.y));
//                put.accept(xr, yr);
//            }
//        }
//
//        ShapeBounds sb2 = new ShapeBounds(() -> m2);



//        Point p1 = new Point(20, (int)Math.round(Math.sqrt(r * r - 20 * 20)));
        List<Map.Entry<Integer,IntegerBounds>> entries =
                new ArrayList<>(sb1.getBounds().entrySet());
        Map.Entry<Integer,IntegerBounds> e = entries.get(
                new java.util.Random().nextInt(entries.size()));
        int xSel = e.getKey();
        IntegerBounds col = e.getValue();
        int ySel = new java.util.Random().nextBoolean()
                ? col.getUpperBound() : col.getLowerBound();
        Point p1 = new Point(xSel, ySel);

        Point p2 = rotate(p1, theta);

        double k1 = InvariantLaplacian.laplacianAt(sb1, p1);
        double k2 = InvariantLaplacian.laplacianAt(sb2, p2);

        System.out.println("Original point: " + p1 + "  curvature=" + k1);
        System.out.println("Rotated point:  " + p2 + "  curvature=" + k2);
        System.out.println("Equal within tol? " + (Math.abs(k1 - k2) < 1e-6));
    }*/
    /*
public static void main(String[] args) {

    int w = 60, h = 40; // rectangle width and height

    // --- Rectangle 1 (axis-aligned) ---
    Map<Integer, IntegerBounds> m1 = new TreeMap<>();
    for (int x = -w / 2; x <= w / 2; x++) {
        // top = +h/2, bottom = -h/2
        m1.put(x, new IntegerBounds(-h / 2, h / 2));
    }
    ShapeBounds sb1 = new ShapeBounds(() -> m1);

    // --- Rectangle 2 (rotated by theta) ---
    double theta = Math.toRadians(9534128);
    Map<Integer, IntegerBounds> m2 = new TreeMap<>();

    // four corners of rectangle before rotation
    Point[] corners = {
            new Point(-w / 2, -h / 2),
            new Point(w / 2, -h / 2),
            new Point(w / 2, h / 2),
            new Point(-w / 2, h / 2)
    };

    // sample perimeter densely so rotation fills columns properly
    int samplesPerEdge = 200;
    for (int i = 0; i < 4; i++) {
        Point a = corners[i];
        Point b = corners[(i + 1) % 4];
        for (int j = 0; j <= samplesPerEdge; j++) {
            double t = (double) j / samplesPerEdge;
            double x = a.x + t * (b.x - a.x);
            double y = a.y + t * (b.y - a.y);

            Point p = rotate(new Point((int) Math.round(x), (int) Math.round(y)), theta);

            // merge multiple hits per column
            m2.compute(p.x, (k, bds) -> {
                if (bds == null) return new IntegerBounds(p.y, p.y);
                int newLow = Math.min(bds.getLowerBound(), p.y);
                int newHigh = Math.max(bds.getUpperBound(), p.y);
                return new IntegerBounds(newLow, newHigh);
            });
        }
    }
    ShapeBounds sb2 = new ShapeBounds(() -> m2);

    // Pick a point on top edge and its rotated counterpart
    Point p1 = new Point(15, h / 2);
    Point p2 = rotate(p1, theta);

    double k1 = InvariantLaplacian.laplacianAt(sb1, p1);
    double k2 = InvariantLaplacian.laplacianAt(sb2, p2);

    System.out.println("Original point: " + p1 + "  curvature=" + k1);
    System.out.println("Rotated point:  " + p2 + "  curvature=" + k2);
    System.out.println("Equal within tol? " + (Math.abs(k1 - k2) < 1e-6));
}*/



}

