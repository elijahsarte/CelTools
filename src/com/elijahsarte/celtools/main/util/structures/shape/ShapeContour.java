package com.elijahsarte.celtools.main.util.structures.shape;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.main.util.structures.collections.IntList;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointIterator;

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

    public ShapeContour(PointCollection pts) {
        if (pts == null || pts.isEmpty()) {
            initEmpty();
            return;
        }

        Set<Long> boundary = new HashSet<>((int) (pts.size() / 0.75) + 1);
//        Set<Long> boundary = new HashSet<>(pts.size());

        pts.onRaw();
        PointIterator it = pts.iterator();

        while (it.hasNext()) {
            Map.Entry<Integer, IntList> prevEntry = it.hasPrevious() ? it.peekPreviousEntry() : null;
            Map.Entry<Integer, IntList> entry = it.nextEntry();
            Map.Entry<Integer, IntList> nextEntry = it.hasNext() ? it.peekNextEntry() : null;

            int x = entry.getKey();
            IntList ys = entry.getValue();
            IntList ysL = (prevEntry != null && prevEntry.getKey() == x - 1) ? prevEntry.getValue() : null;
            IntList ysR = (nextEntry != null && nextEntry.getKey() == x + 1) ? nextEntry.getValue() : null;

            IntList.IntListIterator yIt = ys.iterator();
            IntList.IntListIterator ysLIt = ysL != null ? ysL.iterator() : null;
            IntList.IntListIterator ysRIt = ysR != null ? ysR.iterator() : null;

            while (yIt.hasNext()) {
                boolean missingPrevY = !yIt.hasPrevious()
                        || yIt.peekPrevious() != yIt.peekNext() - 1;

                int y = yIt.next();

                boolean missingLeft = ysLIt == null || !ysLIt.contains(y);
                boolean missingRight = ysRIt == null || !ysRIt.contains(y);
                boolean missingNextY = !yIt.hasNext() || yIt.peekNext() != y + 1;

                if (missingLeft || missingRight || missingPrevY || missingNextY) {
                    boundary.add(MathEx.encode(x, y));
                }
            }
        }

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

    /////////////////
    ///
    public ShapeContour(PointCollection pts, int maxBridgeDistance) {
        this(bridgeByBoundaryBuckets(pts, maxBridgeDistance));
    }

    private static PointCollection bridgeByBoundaryBuckets(PointCollection pts, int maxBridgeDistance) {
        if (pts == null || pts.isEmpty() || maxBridgeDistance <= 0) {
            return pts;
        }

        final int n = pts.size();
        final int[] xs = new int[n];
        final int[] ys = new int[n];
        final HashMap<Long, Integer> indexOf = new HashMap<>((int) (n / 0.75f) + 1);

        final int[] write = {0};
        pts.onRaw();
        pts.forEachRaw((x, col) -> {
            col.onRaw();
            col.forEachRaw(run -> {
                for (int y = run.getLowerBound(); y <= run.getUpperBound(); y++) {
                    int i = write[0]++;
                    xs[i] = x;
                    ys[i] = y;
                    indexOf.put(enc(x, y), i);
                }
            });
        });

        if (write[0] <= 1) {
            return pts;
        }

        final int[] component = new int[n];
        Arrays.fill(component, -1);

        final ArrayList<IntBag> boundaryByComponent = new ArrayList<>();
        final int[] queue = new int[n];
        int componentCount = 0;

        for (int seed = 0; seed < n; seed++) {
            if (component[seed] != -1) {
                continue;
            }

            IntBag boundary = new IntBag(64);
            int head = 0;
            int tail = 0;
            queue[tail++] = seed;
            component[seed] = componentCount;

            while (head < tail) {
                int i = queue[head++];
                int x = xs[i];
                int y = ys[i];

                if (!indexOf.containsKey(enc(x - 1, y)) ||
                        !indexOf.containsKey(enc(x + 1, y)) ||
                        !indexOf.containsKey(enc(x, y - 1)) ||
                        !indexOf.containsKey(enc(x, y + 1))) {
                    boundary.add(i);
                }

                for (int[] d : NEIGHBOR_OFFSETS) {
                    Integer ni = indexOf.get(enc(x + d[0], y + d[1]));
                    if (ni != null && component[ni] == -1) {
                        component[ni] = componentCount;
                        queue[tail++] = ni;
                    }
                }
            }

            boundaryByComponent.add(boundary);
            componentCount++;
        }

        if (componentCount <= 1) {
            return pts;
        }

        final int cellSize = Math.max(1, maxBridgeDistance);
        final int maxDistSq = maxBridgeDistance * maxBridgeDistance;

        final HashMap<Long, IntBag> buckets = new HashMap<>();
        final IntBag allBoundary = new IntBag(Math.max(64, n >>> 2));

        for (IntBag boundary : boundaryByComponent) {
            for (int k = 0; k < boundary.size(); k++) {
                int idx = boundary.get(k);
                allBoundary.add(idx);

                int bx = Math.floorDiv(xs[idx], cellSize);
                int by = Math.floorDiv(ys[idx], cellSize);
                long bucketKey = enc(bx, by);

                IntBag bucket = buckets.get(bucketKey);
                if (bucket == null) {
                    bucket = new IntBag(8);
                    buckets.put(bucketKey, bucket);
                }
                bucket.add(idx);
            }
        }

        final HashMap<Long, GapBridgeEdge> bestByComponentPair = new HashMap<>();

        for (int p = 0; p < allBoundary.size(); p++) {
            int i = allBoundary.get(p);
            int ax = xs[i];
            int ay = ys[i];
            int ca = component[i];

            int bcx = Math.floorDiv(ax, cellSize);
            int bcy = Math.floorDiv(ay, cellSize);

            for (int oy = -1; oy <= 1; oy++) {
                for (int ox = -1; ox <= 1; ox++) {
                    IntBag bucket = buckets.get(enc(bcx + ox, bcy + oy));
                    if (bucket == null) {
                        continue;
                    }

                    for (int q = 0; q < bucket.size(); q++) {
                        int j = bucket.get(q);
                        if (j <= i) {
                            continue;
                        }

                        int cb = component[j];
                        if (ca == cb) {
                            continue;
                        }

                        int dx = xs[j] - ax;
                        if (dx < -maxBridgeDistance || dx > maxBridgeDistance) {
                            continue;
                        }

                        int dy = ys[j] - ay;
                        if (dy < -maxBridgeDistance || dy > maxBridgeDistance) {
                            continue;
                        }

                        int distSq = dx * dx + dy * dy;
                        if (distSq > maxDistSq) {
                            continue;
                        }

                        int c0, c1, x0, y0, x1, y1;
                        if (ca < cb) {
                            c0 = ca;
                            c1 = cb;
                            x0 = ax;
                            y0 = ay;
                            x1 = xs[j];
                            y1 = ys[j];
                        } else {
                            c0 = cb;
                            c1 = ca;
                            x0 = xs[j];
                            y0 = ys[j];
                            x1 = ax;
                            y1 = ay;
                        }

                        long pairKey = (((long) c0) << 32) | (c1 & 0xffffffffL);
                        GapBridgeEdge prev = bestByComponentPair.get(pairKey);
                        if (prev == null || distSq < prev.distSq) {
                            bestByComponentPair.put(pairKey,
                                    new GapBridgeEdge(c0, c1, x0, y0, x1, y1, distSq));
                        }
                    }
                }
            }
        }

        if (bestByComponentPair.isEmpty()) {
            return pts;
        }

        ArrayList<GapBridgeEdge> edges = new ArrayList<>(bestByComponentPair.values());
        edges.sort(Comparator.comparingInt(e -> e.distSq));

        IntDisjointSet dsu = new IntDisjointSet(componentCount);
        HashSet<Long> pixels = new HashSet<>((int) ((n + Math.max(16, edges.size() * cellSize)) / 0.75f) + 1);
        pixels.addAll(indexOf.keySet());

        boolean changed = false;
        for (GapBridgeEdge edge : edges) {
            if (!dsu.union(edge.compA, edge.compB)) {
                continue;
            }
            rasterizeBridge(pixels, edge.ax, edge.ay, edge.bx, edge.by);
            changed = true;
        }

        return changed ? toPointCollection(pixels) : pts;
    }

    private static PointCollection toPointCollection(Set<Long> pixels) {
        TreeMap<Integer, List<Integer>> columns = new TreeMap<>();

        for (long code : pixels) {
            int x = (int) (code >> 32);
            int y = (int) code;
            columns.computeIfAbsent(x, k -> new ArrayList<>()).add(y);
        }

        for (List<Integer> ys : columns.values()) {
            Collections.sort(ys);
        }

        return new PointCollection(columns);
    }

    private static void rasterizeBridge(Set<Long> pixels, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            pixels.add(enc(x0, y0));

            if (x0 == x1 && y0 == y1) {
                break;
            }

            int e2 = err << 1;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static final class GapBridgeEdge {
        private final int compA;
        private final int compB;
        private final int ax;
        private final int ay;
        private final int bx;
        private final int by;
        private final int distSq;

        private GapBridgeEdge(int compA, int compB, int ax, int ay, int bx, int by, int distSq) {
            this.compA = compA;
            this.compB = compB;
            this.ax = ax;
            this.ay = ay;
            this.bx = bx;
            this.by = by;
            this.distSq = distSq;
        }
    }

    private static final class IntDisjointSet {
        private final int[] parent;
        private final byte[] rank;

        private IntDisjointSet(int size) {
            this.parent = new int[size];
            this.rank = new byte[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        private int find(int x) {
            int root = x;
            while (parent[root] != root) {
                root = parent[root];
            }
            while (parent[x] != x) {
                int next = parent[x];
                parent[x] = root;
                x = next;
            }
            return root;
        }

        private boolean union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return false;
            }

            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
            }
            return true;
        }
    }

    private static final class IntBag {
        private int[] data;
        private int size;

        private IntBag(int capacity) {
            this.data = new int[Math.max(1, capacity)];
        }

        private void add(int value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length << 1);
            }
            data[size++] = value;
        }

        private int get(int index) {
            return data[index];
        }

        private int size() {
            return size;
        }
    }
    //////////////////////


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

    private static List<IntegerBounds> rawRuns(IntList ys) {
        if (ys == null || ys.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<IntegerBounds> out = new ArrayList<>();
        ys.onRaw();
        ys.forEachRaw(b -> out.add(new IntegerBounds(b.getLowerBound(), b.getUpperBound())));
        return out;
    }

    private static List<IntegerBounds> intersectRuns(List<IntegerBounds> a, List<IntegerBounds> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<IntegerBounds> out = new ArrayList<>(Math.min(a.size(), b.size()));
        int i = 0, j = 0;

        while (i < a.size() && j < b.size()) {
            IntegerBounds ra = a.get(i);
            IntegerBounds rb = b.get(j);

            int lo = Math.max(ra.getLowerBound(), rb.getLowerBound());
            int hi = Math.min(ra.getUpperBound(), rb.getUpperBound());

            if (lo <= hi) {
                out.add(new IntegerBounds(lo, hi));
            }

            if (ra.getUpperBound() < rb.getUpperBound()) {
                i++;
            } else {
                j++;
            }
        }

        return out;
    }
    /*
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
    }*/
    private List<Point> followExternal(Set<Long> boundary) {
        if (boundary == null || boundary.isEmpty()) {
            return Collections.emptyList();
        }

        BoundaryIndex idx = buildBoundaryIndex(boundary);
        HashMap<Integer, IntList> columns = idx.columns;

        final int sx = idx.startX;
        final int sy = idx.startY;

        int cx = sx;
        int cy = sy;

        // Same seed convention as the current method:
        // pretend we approached the start from the west, so initial dir is East.
        int dir = 0;

        ArrayList<Point> out = new ArrayList<>(Math.max(16, boundary.size()));
        HashSet<Long> seenStates = new HashSet<>(Math.max(16, boundary.size() * 2));

        // Safety guard only; normally the repeated-state check ends the walk.
        int guard = Math.max(64, boundary.size() * 4);

        while (guard-- > 0) {
            if (out.isEmpty()) {
                out.add(new Point(cx, cy));
            } else {
                Point last = out.get(out.size() - 1);
                if (last.x != cx || last.y != cy) {
                    out.add(new Point(cx, cy));
                }
            }

            long stateKey = encodeWalkState(cx, cy, dir);
            if (!seenStates.add(stateKey)) {
                break;
            }

            // Only 3 column fetches per step instead of hashing 8 encoded longs.
            IntList ysLeft  = columns.get(cx - 1);
            IntList ysHere  = columns.get(cx);
            IntList ysRight = columns.get(cx + 1);

            // Start scanning one step clockwise from the backtrack direction.
            int scan = (dir + 6) & 7;
            boolean advanced = false;

            for (int k = 0; k < 8; k++) {
                int nd = (scan + k) & 7;
                int dx = CHAIN8[nd][0];
                int dy = CHAIN8[nd][1];

                int nx = cx + dx;
                int ny = cy + dy;

                IntList col = dx < 0 ? ysLeft : (dx > 0 ? ysRight : ysHere);
                if (col == null || !col.contains(ny)) {
                    continue;
                }

                cx = nx;
                cy = ny;
                dir = nd;
                advanced = true;
                break;
            }

            if (!advanced) {
                break;
            }
        }

        dedupeSpikes(out);

        if (out.size() >= 2) {
            Point first = out.get(0);
            Point last = out.get(out.size() - 1);
            if (first.x == last.x && first.y == last.y) {
                out.remove(out.size() - 1);
            }
        }

        return out;
    }

    private static final class BoundaryIndex {
        private final HashMap<Integer, IntList> columns;
        private final int startX;
        private final int startY;

        private BoundaryIndex(HashMap<Integer, IntList> columns, int startX, int startY) {
            this.columns = columns;
            this.startX = startX;
            this.startY = startY;
        }
    }

    private static BoundaryIndex buildBoundaryIndex(Set<Long> boundary) {
        HashMap<Integer, IntList> columns =
                new HashMap<>(Math.max(16, boundary.size()));

        int sx = 0;
        int sy = Integer.MIN_VALUE;

        for (long code : boundary) {
            int x = (int) (code >>> 32);
            int y = (int) code;

            IntList ys = columns.get(x);
            if (ys == null) {
                ys = new IntList();
                columns.put(x, ys);
            }
            ys.add(y);

            // Keep the same start-point rule as the existing code:
            // highest y, then smallest x.
            if (y > sy || (y == sy && x < sx)) {
                sx = x;
                sy = y;
            }
        }

        return new BoundaryIndex(columns, sx, sy);
    }

    private static long encodeWalkState(int x, int y, int dir) {
        return (enc(x, y) << 3) ^ dir;
    }

    private List<Point> traceLargestLoop(Set<Long> boundary) {
        if (boundary.isEmpty()) return Collections.emptyList();

        // Fastest version: this consumes boundary as the "unvisited" set.
        ArrayDeque<Long> q = new ArrayDeque<>();
        List<Point> best = Collections.emptyList();
        double bestA = -1.0;

        Set<Long> comp = new HashSet<>((boundary.size() * 4 + 2) / 3);
        while (!boundary.isEmpty()) {
            comp.clear();
            long start = boundary.iterator().next();
            boundary.remove(start);

            q.clear();
            q.add(start);

            while (!q.isEmpty()) {
                long c = q.removeFirst();
                comp.add(c);

                int x = (int) (c >>> 32);
                int y = (int) c;

                for (int i = 0; i < 8; i++) {
                    Long nc = enc(x + CHAIN8[i][0], y + CHAIN8[i][1]);
                    if (boundary.remove(nc)) {
                        q.addLast(nc);
                    }
                }
            }

            if (comp.size() < 4) continue;

            List<Point> loop = followExternal(comp);
            if (!loop.isEmpty()) {
                double a = Math.abs(signedArea(loop));
                if (a > bestA || (a == bestA && loop.size() > best.size())) {
                    bestA = a;
                    best = loop;
                }
            }
        }

        return best;
    }

    private List<Point> mooreTraceComponent(List<Long> comp) {
        if (comp.isEmpty()) return Collections.emptyList();

        Set<Long> compSet = new HashSet<>((int) (comp.size() / 0.75) + 1);
        compSet.addAll(comp);

        int sx = 0, sy = Integer.MIN_VALUE;
        for (long code : comp) {
            int x = (int) (code >>> 32), y = (int) code;
            if (y > sy || (y == sy && x < sx)) {
                sx = x;
                sy = y;
            }
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
        for (Point p : out) {
            int m = clean.size();
            if (m > 0) {
                Point last = clean.get(m - 1);
                if (last.x == p.x && last.y == p.y) continue;
                if (m >= 2) {
                    Point prev = clean.get(m - 2);
                    if (prev.x == p.x && prev.y == p.y) {
                        clean.remove(m - 1);
                        continue;
                    }
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
        for (Point point : ordered) {
            xs.add(point.x);
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






    public ShapeContour outerEdge() {
        if (isEmpty()) {
            return ShapeContour.empty();
        }

        if (size() < 3 || perimeter <= 0.0) {
            return new ShapeContour(this.toList());
        }

        int bins = (int) Math.min(
                131_072L,
                Math.max(720L, (long) Math.ceil(perimeter * 1.5))
        );

        return outerEdge(bins);
    }


    public ShapeContour outerEdge(int binCount) {
        if (isEmpty()) {
            return ShapeContour.empty();
        }

        if (size() < 3 || perimeter <= 0.0) {
            return new ShapeContour(this.toList());
        }

        binCount = Math.max(64, binCount);

        final Point2D.Double c = this.centroid != null ? this.centroid : centroid();
        final double cx = c.x;
        final double cy = c.y;

        final double[] bestT = new double[binCount];
        final double[] bestX = new double[binCount];
        final double[] bestY = new double[binCount];
        final boolean[] hit = new boolean[binCount];

        Arrays.fill(bestT, Double.NEGATIVE_INFINITY);

        final int n = contour.size();

        for (int i = 0; i < n; i++) {
            Point a = contour.get(i);
            Point b = contour.get((i + 1) % n);

            outerEdgeAddVertexHit(cx, cy, a.x, a.y, binCount, bestT, bestX, bestY, hit);
            outerEdgeAddVertexHit(cx, cy, b.x, b.y, binCount, bestT, bestX, bestY, hit);

            outerEdgeRasterizeSegmentByAngle(
                    cx, cy,
                    a.x, a.y,
                    b.x, b.y,
                    binCount,
                    bestT, bestX, bestY, hit
            );
        }

        ArrayList<Point> ordered = outerEdgeBuildOrderedPath(bestX, bestY, hit);

        if (ordered.size() < 3) {
            return ShapeContour.empty();
        }

        ordered = outerEdgeNormalizeOrdered(ordered);

        if (ordered.size() < 3) {
            return ShapeContour.empty();
        }

        ShapeContour out = new ShapeContour(Collections.emptyList());
        out.finalizeFromOrdered(ordered);
        return out;
    }

    private static void outerEdgeRasterizeSegmentByAngle(
            double cx, double cy,
            double ax, double ay,
            double bx, double by,
            int binCount,
            double[] bestT,
            double[] bestX,
            double[] bestY,
            boolean[] hit
    ) {
        double dax = ax - cx;
        double day = ay - cy;
        double dbx = bx - cx;
        double dby = by - cy;

        double ra2 = dax * dax + day * day;
        double rb2 = dbx * dbx + dby * dby;

        if (ra2 <= 1.0e-12 && rb2 <= 1.0e-12) {
            return;
        }

        double aa = Math.atan2(day, dax);
        double ab = Math.atan2(dby, dbx);

        if (aa < 0.0) aa += Math.PI * 2.0;
        if (ab < 0.0) ab += Math.PI * 2.0;
        double delta = ab - aa;

        if (delta > Math.PI) {
            delta -= Math.PI * 2.0;
        } else if (delta < -Math.PI) {
            delta += Math.PI * 2.0;
        }

        double start = aa;
        double end = aa + delta;

        if (end < start) {
            double tmp = start;
            start = end;
            end = tmp;
        }

        outerEdgeProcessAngularInterval(
                cx, cy,
                ax, ay,
                bx, by,
                start,
                end,
                binCount,
                bestT,
                bestX,
                bestY,
                hit
        );
    }

    private static void outerEdgeProcessAngularInterval(
            double cx, double cy,
            double ax, double ay,
            double bx, double by,
            double start,
            double end,
            int binCount,
            double[] bestT,
            double[] bestX,
            double[] bestY,
            boolean[] hit
    ) {
        final double twoPi = Math.PI * 2.0;
        final double step = twoPi / binCount;
        final double eps = 1.0e-9;
        int first = (int) Math.ceil((start / step) - 0.5 - eps);
        int last = (int) Math.floor((end / step) - 0.5 + eps);

        if (first > last) {
            double mid = (start + end) * 0.5;
            int k = (int) Math.floor(mid / step);
            outerEdgeTryRaySegmentHit(
                    cx, cy,
                    ax, ay,
                    bx, by,
                    k,
                    binCount,
                    bestT,
                    bestX,
                    bestY,
                    hit
            );
            return;
        }

        for (int k = first; k <= last; k++) {
            outerEdgeTryRaySegmentHit(
                    cx, cy,
                    ax, ay,
                    bx, by,
                    k,
                    binCount,
                    bestT,
                    bestX,
                    bestY,
                    hit
            );
        }
    }

    private static void outerEdgeTryRaySegmentHit(
            double cx, double cy,
            double ax, double ay,
            double bx, double by,
            int rawBin,
            int binCount,
            double[] bestT,
            double[] bestX,
            double[] bestY,
            boolean[] hit
    ) {
        int bin = Math.floorMod(rawBin, binCount);

        final double theta = ((rawBin + 0.5) * (Math.PI * 2.0)) / binCount;
        final double rx = Math.cos(theta);
        final double ry = Math.sin(theta);

        final double sx = bx - ax;
        final double sy = by - ay;

        final double qpx = ax - cx;
        final double qpy = ay - cy;

        final double denom = outerEdgeCross(rx, ry, sx, sy);
        final double eps = 1.0e-9;

        if (Math.abs(denom) < eps) {
            return;
        }

        double t = outerEdgeCross(qpx, qpy, sx, sy) / denom;
        double u = outerEdgeCross(qpx, qpy, rx, ry) / denom;

        if (t >= -eps && u >= -eps && u <= 1.0 + eps) {
            if (t > bestT[bin]) {
                bestT[bin] = t;
                bestX[bin] = cx + rx * t;
                bestY[bin] = cy + ry * t;
                hit[bin] = true;
            }
        }
    }

    private static void outerEdgeAddVertexHit(
            double cx, double cy,
            double x, double y,
            int binCount,
            double[] bestT,
            double[] bestX,
            double[] bestY,
            boolean[] hit
    ) {
        double dx = x - cx;
        double dy = y - cy;
        double t = Math.hypot(dx, dy);

        if (t <= 1.0e-9) {
            return;
        }

        double angle = Math.atan2(dy, dx);
        if (angle < 0.0) {
            angle += Math.PI * 2.0;
        }

        int bin = (int) ((angle / (Math.PI * 2.0)) * binCount);
        if (bin >= binCount) {
            bin = binCount - 1;
        }

        if (t > bestT[bin]) {
            bestT[bin] = t;
            bestX[bin] = x;
            bestY[bin] = y;
            hit[bin] = true;
        }
    }

    private static ArrayList<Point> outerEdgeBuildOrderedPath(
            double[] bestX,
            double[] bestY,
            boolean[] hit
    ) {
        int n = hit.length;

        int firstHit = -1;
        for (int i = 0; i < n; i++) {
            if (hit[i]) {
                firstHit = i;
                break;
            }
        }

        if (firstHit < 0) {
            return new ArrayList<>();
        }

        ArrayList<Point> out = new ArrayList<>(n);

        Point prev = null;

        for (int step = 0; step < n; step++) {
            int i = (firstHit + step) % n;

            if (!hit[i]) {
                continue;
            }

            Point curr = new Point(
                    (int) Math.round(bestX[i]),
                    (int) Math.round(bestY[i])
            );

            if (prev == null) {
                out.add(curr);
                prev = curr;
                continue;
            }

            outerEdgeAppendLine(out, prev, curr);
            prev = curr;
        }

        if (out.size() >= 2) {
            Point first = out.get(0);
            Point last = out.get(out.size() - 1);
            outerEdgeAppendLine(out, last, first);

            if (out.size() >= 2) {
                Point newLast = out.get(out.size() - 1);
                if (newLast.x == first.x && newLast.y == first.y) {
                    out.remove(out.size() - 1);
                }
            }
        }

        return out;
    }

    private static void outerEdgeAppendLine(ArrayList<Point> out, Point from, Point to) {
        if (from == null || to == null) {
            return;
        }

        int x0 = from.x;
        int y0 = from.y;
        int x1 = to.x;
        int y1 = to.y;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;

        int err = dx - dy;

        while (true) {
            outerEdgeAddPointIfDifferent(out, x0, y0);

            if (x0 == x1 && y0 == y1) {
                break;
            }

            int e2 = err << 1;

            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }

            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static void outerEdgeAddPointIfDifferent(ArrayList<Point> out, int x, int y) {
        int n = out.size();

        if (n > 0) {
            Point last = out.get(n - 1);
            if (last.x == x && last.y == y) {
                return;
            }

            if (n >= 2) {
                Point prev = out.get(n - 2);
                if (prev.x == x && prev.y == y) {
                    out.remove(n - 1);
                    return;
                }
            }
        }

        out.add(new Point(x, y));
    }

    private static ArrayList<Point> outerEdgeNormalizeOrdered(ArrayList<Point> pts) {
        if (pts == null || pts.isEmpty()) {
            return new ArrayList<>();
        }

        dedupeSpikes(pts);

        int w = 0;
        for (int i = 0; i < pts.size(); i++) {
            Point p = pts.get(i);

            if (w == 0) {
                pts.set(w++, p);
                continue;
            }

            Point last = pts.get(w - 1);
            if (last.x != p.x || last.y != p.y) {
                pts.set(w++, p);
            }
        }

        while (pts.size() > w) {
            pts.remove(pts.size() - 1);
        }

        if (pts.size() >= 2) {
            Point first = pts.get(0);
            Point last = pts.get(pts.size() - 1);

            if (first.x == last.x && first.y == last.y) {
                pts.remove(pts.size() - 1);
            }
        }

        if (pts.size() < 3) {
            return pts;
        }

        if (signedArea(pts) < 0.0) {
            Collections.reverse(pts);
        }

        int start = 0;

        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(start);
            Point b = pts.get(i);

            if (b.y > a.y || (b.y == a.y && b.x < a.x)) {
                start = i;
            }
        }

        if (start > 0) {
            Collections.rotate(pts, -start);
        }

        return pts;
    }

    private static double outerEdgeCross(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }

    public ShapeContour halfwayOld(ShapeContour other) {
        Objects.requireNonNull(other, "other");

        if (this.isEmpty()) return other;
        if (other.isEmpty()) return this;

        boolean otherInsideThis = this.inside(other);
        boolean thisInsideOther = other.inside(this);

        ShapeContour outer;
        ShapeContour inner;

        if (otherInsideThis && !thisInsideOther) {
            outer = this;
            inner = other;
        } else if (thisInsideOther && !otherInsideThis) {
            outer = other;
            inner = this;
        } else {
            outer = this.area() >= other.area() ? this : other;
            inner = outer == this ? other : this;
        }

        int samples = Math.max(outer.size(), inner.size());
        if (samples <= 0) {
            return ShapeContour.empty();
        }

        PointCollection midway = new PointCollection();

        for (int i = 0; i < samples; i++) {
            double t = (double) i / samples;

            Point2D.Double pOuter = outer.pointOnPerimeterNormalized(t);
            Point2D.Double pInner = inner.pointOnPerimeterNormalized(t);

            if (pOuter == null || pInner == null) {
                continue;
            }

            int midX = (int) Math.round((pOuter.x + pInner.x) / 2.0);
            int midY = (int) Math.round((pOuter.y + pInner.y) / 2.0);
            midway.add(new Point(midX, midY));
        }

        return midway.isEmpty() ? ShapeContour.empty() : new ShapeContour(midway);
    }

    public ShapeContour expand(int pixels) {
        if (pixels <= 0 || isEmpty()) return this;

        PointCollection solid = rasterizeFilledPixels();
        if (solid.isEmpty()) return ShapeContour.empty();

        PointCollection dilated = dilateFilledPixels(solid, pixels);
        if (dilated.isEmpty()) return ShapeContour.empty();

        dilated.fillHoles();
        return new ShapeContour(dilated);
    }

/*
    public PointCollection rasterizeFilledPixels() {
        PointCollection solid = new PointCollection();
        if (isEmpty()) return solid;

        final int n = contour.size();
        if (n < 3) {
            PointCollection boundary = new PointCollection(contour);
            boundary.onRaw();
            PointIterator it = boundary.iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, IntList> entry = it.nextEntry();
                solid.addAtX(entry.getKey(), entry.getValue());
            }
            return solid;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;

        for (Point p : contour) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
        }

        ArrayList<Double> intersections = new ArrayList<>(Math.max(8, n));

        for (int x = minX; x <= maxX; x++) {
            intersections.clear();
            double scanX = x + 0.5d;

            for (int i = 0, j = n - 1; i < n; j = i++) {
                Point a = contour.get(j);
                Point b = contour.get(i);

                if (a.x == b.x) continue;

                double ax = a.x;
                double bx = b.x;

                boolean crosses = (ax <= scanX && bx > scanX) || (bx <= scanX && ax > scanX);
                if (!crosses) continue;

                double t = (scanX - ax) / (bx - ax);
                double y = a.y + (t * (b.y - a.y));
                intersections.add(y);
            }

            if (intersections.isEmpty()) continue;

            intersections.sort(Double::compare);

            int count = 0;
            for (int i = 0; i + 1 < intersections.size(); i += 2) {
                double y0 = intersections.get(i);
                double y1 = intersections.get(i + 1);

                int startY = (int) Math.ceil(Math.min(y0, y1) - 0.5d);
                int endY = (int) Math.floor(Math.max(y0, y1) - 0.5d);

                if (startY <= endY) {
                    count += (endY - startY + 1);
                }
            }

            if (count == 0) continue;

            int[] ys = new int[count];
            int k = 0;

            for (int i = 0; i + 1 < intersections.size(); i += 2) {
                double y0 = intersections.get(i);
                double y1 = intersections.get(i + 1);

                int startY = (int) Math.ceil(Math.min(y0, y1) - 0.5d);
                int endY = (int) Math.floor(Math.max(y0, y1) - 0.5d);

                for (int y = startY; y <= endY; y++) {
                    ys[k++] = y;
                }
            }

            solid.addAtX(x, ys);
        }

        // Merge contour columns in one column-at-a-time pass using PointIterator.
        PointCollection boundary = new PointCollection(contour);
        boundary.onRaw();
        PointIterator it = boundary.iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, IntList> entry = it.nextEntry();
            solid.addAtX(entry.getKey(), entry.getValue());
        }

        return solid;
    }*/

    public PointCollection rasterizeFilledPixels() {
        PointCollection solid = new PointCollection();
        if (isEmpty()) return solid;

        final int n = contour.size();

        if (n < 3) {
            PointCollection boundary = new PointCollection(contour);
            boundary.onRaw();

            PointIterator it = boundary.iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, IntList> entry = it.nextEntry();
                solid.addAtX(entry.getKey(), entry.getValue());
            }

            return solid;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;

        for (Point p : contour) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
        }

        final int width = maxX - minX + 1;
        if (width <= 0) return solid;

        /*
         * CHANGE:
         * Instead of rebuilding an ArrayList<Double> of intersections for every x,
         * build an edge table once. Each edge is inserted into the scan column where
         * it first becomes active.
         */
        class ActiveEdge {
            int endX;
            double y;
            double stepY;
            ActiveEdge next;

            ActiveEdge(int endX, double y, double stepY, ActiveEdge next) {
                this.endX = endX;
                this.y = y;
                this.stepY = stepY;
                this.next = next;
            }
        }

        ActiveEdge[] edgeStarts = new ActiveEdge[width];
        int edgeCount = 0;

        /*
         * CHANGE:
         * Register each non-vertical contour segment once.
         *
         * Original crossing rule:
         *
         *     lowX <= x + 0.5 < highX
         *
         * Since contour coordinates are integer pixel positions, this means an edge
         * from left.x to right.x is active for columns:
         *
         *     left.x through right.x - 1
         */
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point a = contour.get(j);
            Point b = contour.get(i);

            if (a.x == b.x) continue;

            Point left;
            Point right;

            if (a.x < b.x) {
                left = a;
                right = b;
            } else {
                left = b;
                right = a;
            }

            int startX = left.x;
            int endX = right.x - 1;

            if (endX < minX || startX > maxX) continue;

            if (startX < minX) startX = minX;
            if (endX > maxX) endX = maxX;

            double stepY = (right.y - left.y) / (double) (right.x - left.x);

            /*
             * y-intersection at scanX = startX + 0.5
             */
            double y = left.y + ((startX + 0.5d - left.x) * stepY);

            int bucket = startX - minX;
            edgeStarts[bucket] = new ActiveEdge(endX, y, stepY, edgeStarts[bucket]);
            edgeCount++;
        }

        ActiveEdge[] active = new ActiveEdge[Math.max(4, edgeCount)];
        int activeSize = 0;

        for (int x = minX; x <= maxX; x++) {
            int bucket = x - minX;

            /*
             * CHANGE:
             * Add only the edges that begin at this x-column.
             * No full contour scan happens here anymore.
             */
            for (ActiveEdge e = edgeStarts[bucket]; e != null; e = e.next) {
                active[activeSize++] = e;
            }

            /*
             * Remove expired edges.
             */
            int keep = 0;
            for (int i = 0; i < activeSize; i++) {
                ActiveEdge e = active[i];
                if (e.endX >= x) {
                    active[keep++] = e;
                }
            }
            activeSize = keep;

            if (activeSize >= 2) {
                /*
                 * CHANGE:
                 * Active edges are usually almost sorted from one column to the next,
                 * so insertion sort is typically faster than general-purpose sorting here.
                 */
                for (int i = 1; i < activeSize; i++) {
                    ActiveEdge e = active[i];
                    double y = e.y;

                    int j = i - 1;
                    while (j >= 0 && active[j].y > y) {
                        active[j + 1] = active[j];
                        j--;
                    }

                    active[j + 1] = e;
                }

                int count = 0;

                /*
                 * Same even-odd fill rule as before: pair sorted intersections.
                 */
                for (int i = 0; i + 1 < activeSize; i += 2) {
                    double y0 = active[i].y;
                    double y1 = active[i + 1].y;

                    int startY = (int) Math.ceil(y0 - 0.5d);
                    int endY = (int) Math.floor(y1 - 0.5d);

                    if (startY <= endY) {
                        count += endY - startY + 1;
                    }
                }

                if (count > 0) {
                    int[] ys = new int[count];
                    int k = 0;

                    for (int i = 0; i + 1 < activeSize; i += 2) {
                        double y0 = active[i].y;
                        double y1 = active[i + 1].y;

                        int startY = (int) Math.ceil(y0 - 0.5d);
                        int endY = (int) Math.floor(y1 - 0.5d);

                        for (int y = startY; y <= endY; y++) {
                            ys[k++] = y;
                        }
                    }

                    solid.addAtX(x, ys);
                }
            }

            /*
             * CHANGE:
             * Instead of recomputing each intersection from scratch next column,
             * incrementally advance the active edge's y-intersection.
             */
            for (int i = 0; i < activeSize; i++) {
                ActiveEdge e = active[i];
                if (e.endX > x) {
                    e.y += e.stepY;
                }
            }
        }

        /*
         * Merge contour boundary columns.
         */
        PointCollection boundary = new PointCollection(contour);
        boundary.onRaw();

        PointIterator it = boundary.iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, IntList> entry = it.nextEntry();
            solid.addAtX(entry.getKey(), entry.getValue());
        }

        return solid;
    }


    private static PointCollection dilateFilledPixels(PointCollection solid, int radius) {
        if (radius <= 0 || solid == null || solid.isEmpty()) {
            return solid == null ? new PointCollection() : new PointCollection(solid);
        }

        int[] yReachByDx = buildDiskYReach(radius);

        HashMap<Integer, ArrayList<int[]>> spansByX = new HashMap<>(
                Math.max(16, solid.width() + (radius * 2) + 1)
        );

        solid.onRaw();
        solid.forEachRaw((x, ys) -> {
            if (ys == null || ys.isEmpty()) return;

            ys.onRaw();
            ys.forEachRaw(bounds -> {
                int lo = bounds.getLowerBound();
                int hi = bounds.getUpperBound();

                for (int dx = -radius; dx <= radius; dx++) {
                    int reach = yReachByDx[dx + radius];
                    int outX = x + dx;

                    spansByX
                            .computeIfAbsent(outX, k -> new ArrayList<>())
                            .add(new int[] { lo - reach, hi + reach });
                }
            });
        });

        PointCollection out = new PointCollection();

        for (Map.Entry<Integer, ArrayList<int[]>> entry : spansByX.entrySet()) {
            int x = entry.getKey();
            ArrayList<int[]> spans = entry.getValue();
            if (spans.isEmpty()) continue;

            spans.sort(Comparator.comparingInt(a -> a[0]));

            IntList mergedYs = new IntList();

            int currLo = spans.get(0)[0];
            int currHi = spans.get(0)[1];

            for (int i = 1; i < spans.size(); i++) {
                int lo = spans.get(i)[0];
                int hi = spans.get(i)[1];

                if (lo <= currHi + 1) {
                    if (hi > currHi) currHi = hi;
                } else {
                    for (int y = currLo; y <= currHi; y++) {
                        mergedYs.add(y);
                    }
                    currLo = lo;
                    currHi = hi;
                }
            }

            for (int y = currLo; y <= currHi; y++) {
                mergedYs.add(y);
            }

            out.addAtX(x, mergedYs);
        }

        return out;
    }

    private static int[] buildDiskYReach(int radius) {
        int[] out = new int[(radius * 2) + 1];
        long r2 = (long) radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            long dx2 = (long) dx * dx;
            out[dx + radius] = floorInt(Math.sqrt(r2 - dx2));
        }

        return out;
    }

    public ShapeContour contract(int pixels) {
        if (pixels <= 0 || isEmpty()) return this;

        PointCollection solid = rasterizeFilledPixels();
        if (solid.isEmpty()) return ShapeContour.empty();

        PointCollection contracted = solid.contract(pixels);
        if (contracted.isEmpty()) return ShapeContour.empty();

        return new ShapeContour(contracted);
    }

    /**
     * Fast approximate offset using radial scaling about centroid:
     * expands/contracts by shifting each vertex along its centroid ray.
     * This avoids any O(area) raster work and avoids inside()/contains() loops.
     */
    private ShapeContour offsetBy(int deltaPixels) {
        if (isEmpty()) return ShapeContour.empty();

        // Ensure centroid exists
        Point2D.Double c = this.centroid;
        if (c == null) c = this.centroid();

        // Build new vertex set by moving each contour point along the centroid ray.
        // Then rebuild to a clean contour via rebuildFromVertices (sort-by-angle pipeline).
        int n = this.size();
        ArrayList<Point> moved = new ArrayList<>(n);

        // Small optimization: collapse repeated points after rounding
        long lastKey = Long.MIN_VALUE;

        for (int i = 0; i < n; i++) {
            Point p = contour.get(i);
            double vx = p.x - c.x;
            double vy = p.y - c.y;
            double r = Math.hypot(vx, vy);

            // If r==0, arbitrary direction; keep point unchanged
            if (r <= 1e-9) {
                long k = MathEx.encode(p.x, p.y);
                if (k != lastKey) {
                    moved.add(new Point(p));
                    lastKey = k;
                }
                continue;
            }

            double newR = r + deltaPixels;
            // If contracting beyond centroid, clamp to a tiny radius (prevents inversion)
            if (newR < 1.0) newR = 1.0;

            double s = newR / r;
            int nx = (int) Math.round(c.x + vx * s);
            int ny = (int) Math.round(c.y + vy * s);

            long k = MathEx.encode(nx, ny);
            if (k != lastKey) {
                moved.add(new Point(nx, ny));
                lastKey = k;
            }
        }

        if (moved.size() < 3) {
            // contraction could degenerate; return empty instead of pathological contour
            return ShapeContour.empty();
        }

        ShapeContour out = new ShapeContour(Collections.emptyList());
        out.rebuildFromVertices(moved);
        return out;
    }

    /**
     * Much faster halfway:
     * - avoids inside(ShapeContour) which scans bounding boxes and calls contains/inside repeatedly [10]
     * - avoids calling pointOnPerimeterNormalized() in a loop (each call scans cumulativeArcLengths linearly) [10]
     * - pairs points by normalized arc using a single pass over arc arrays (O(n+m))
     */
    /*
    public ShapeContour halfway(ShapeContour other) {
        Objects.requireNonNull(other, "other");

        final boolean debug = true; // turn off when you're done debugging
        final String tag = "[ShapeContour.halfway] ";

        if (this.isEmpty()) {
            if (debug) System.out.println(tag + "this is empty -> returning other");
            return other;
        }
        if (other.isEmpty()) {
            if (debug) System.out.println(tag + "other is empty -> returning this");
            return this;
        }

        boolean otherInsideThis = this.inside(other);
        boolean thisInsideOther = other.inside(this);

        ShapeContour outer;
        ShapeContour inner;

        if (otherInsideThis && !thisInsideOther) {
            outer = this;
            inner = other;
        } else if (thisInsideOther && !otherInsideThis) {
            outer = other;
            inner = this;
        } else {
            outer = this.area() >= other.area() ? this : other;
            inner = outer == this ? other : this;
        }

        int samples = Math.max(64, Math.max(outer.size(), inner.size()) * 2);

        if (debug) {
            System.out.println(tag + "=== DEBUG START ===");
            debugPrintContour(tag + "this", this, 12);
            debugPrintContour(tag + "other", other, 12);
            System.out.printf(tag + "otherInsideThis=%s, thisInsideOther=%s%n", otherInsideThis, thisInsideOther);
            System.out.printf(tag + "chosen outer=%s, inner=%s%n",
                    outer == this ? "this" : "other",
                    inner == this ? "this" : "other");
            System.out.printf(tag + "samples=%d%n", samples);
        }

        List<Point2D.Double> outerSamples = samplePerimeter(outer, samples);
        List<Point2D.Double> innerSamples = samplePerimeter(inner, samples);

        int bestShift = findBestCircularShift(outerSamples, innerSamples);
        double bestRmse            = computeShiftRmse(outerSamples, innerSamples, bestShift);

        List<Point2D.Double> reversedInnerSamples = reverseSamples(innerSamples);
        int reversedShift = findBestCircularShift(outerSamples, reversedInnerSamples);
        double reversedRmse = computeShiftRmse(outerSamples, reversedInnerSamples, reversedShift);

        boolean useReversed = reversedRmse < bestRmse;
        List<Point2D.Double> alignedInnerSamples = useReversed ? reversedInnerSamples : innerSamples;
        int appliedShift = useReversed ? reversedShift : bestShift;
        double appliedRmse = useReversed ? reversedRmse : bestRmse;

        if (debug) {
            System.out.printf(tag + "forwardShift=%d, forwardRmse=%.4f%n", bestShift, bestRmse);
            System.out.printf(tag + "reversedShift=%d, reversedRmse=%.4f%n", reversedShift, reversedRmse);
            System.out.printf(tag + "usingReversed=%s, appliedShift=%d, appliedRmse=%.4f%n",
                    useReversed, appliedShift, appliedRmse);
            debugPrintSamples(tag + "outerSamples", outerSamples, 12);
            debugPrintSamples(tag + "innerSamples", innerSamples, 12);
        }

        List<Point> midwayOrdered = new ArrayList<>(samples);

        for (int i = 0; i < samples; i++) {
            Point2D.Double pOuter = outerSamples.get(i);
            Point2D.Double pInner = alignedInnerSamples.get((i + appliedShift) % samples);

            int midX = roundInt((pOuter.x + pInner.x) / 2.0);
            int midY = roundInt((pOuter.y + pInner.y) / 2.0);
            Point mid = new Point(midX, midY);

            if (midwayOrdered.isEmpty() || !samePoint(midwayOrdered.get(midwayOrdered.size() - 1), mid)) {
                midwayOrdered.add(mid);
            }

            if (debug && (i < 16 || i == samples - 1)) {
                System.out.printf(
                        tag + "i=%d outer=(%.3f, %.3f) inner=(%.3f, %.3f) mid=(%d, %d)%n",
                        i, pOuter.x, pOuter.y, pInner.x, pInner.y, mid.x, mid.y
                );
            }
        }

        midwayOrdered = normalizeOrderedLoop(midwayOrdered);

        if (debug) {
            debugPrintPoints(tag + "midwayOrdered", midwayOrdered, 20);
        }

        if (midwayOrdered.size() < 3) {
            if (debug) {
                System.out.println(tag + "midwayOrdered has fewer than 3 vertices -> returning empty");
                System.out.println(tag + "=== DEBUG END ===");
            }
            return ShapeContour.empty();
        }

        ShapeContour result = new ShapeContour(new PointCollection());
        result.finalizeFromOrdered(midwayOrdered);

        if (debug) {
            debugPrintContour(tag + "result", result, 16);
            System.out.println(tag + "=== DEBUG END ===");
        }

        return result;
    }*/

// The true working one
    /*
    public ShapeContour halfway(ShapeContour other) {
        Objects.requireNonNull(other, "other");

        final boolean debug = false;
        final String tag = "[ShapeContour.halfway] ";

        if (this.isEmpty()) {
            if (debug) System.out.println(tag + "this is empty -> returning other");
            return other;
        }
        if (other.isEmpty()) {
            if (debug) System.out.println(tag + "other is empty -> returning this");
            return this;
        }

        boolean otherInsideThis = this.inside(other);
        boolean thisInsideOther = other.inside(this);

        ShapeContour outer;
        ShapeContour inner;

        if (otherInsideThis && !thisInsideOther) {
            outer = this;
            inner = other;
        } else if (thisInsideOther && !otherInsideThis) {
            outer = other;
            inner = this;
        } else {
            outer = this.area() >= other.area() ? this : other;
            inner = outer == this ? other : this;
        }

        Point2D.Double center = chooseHalfwayCenter(outer, inner);
        Point centerPt = new Point(roundInt(center.x), roundInt(center.y));

        int samples = Math.max(720, Math.max(outer.size(), inner.size()) * 2);

        if (debug) {
            System.out.println(tag + "=== DEBUG START ===");
            debugPrintContour(tag + "this", this, 12);
            debugPrintContour(tag + "other", other, 12);
            System.out.printf(tag + "otherInsideThis=%s, thisInsideOther=%s%n", otherInsideThis, thisInsideOther);
            System.out.printf(tag + "chosen outer=%s, inner=%s%n",
                    outer == this ? "this" : "other",
                    inner == this ? "this" : "other");
            System.out.printf(tag + "center=(%.3f, %.3f) centerRounded=%s%n", center.x, center.y, centerPt);
            System.out.printf(tag + "center inside inner=%s inside outer=%s%n",
                    inner.inside(centerPt), outer.inside(centerPt));
            System.out.printf(tag + "samples=%d%n", samples);
        }

        List<Point> midwayOrdered = new ArrayList<>(samples);
        int hitCount = 0;
        int missCount = 0;

        for (int i = 0; i < samples; i++) {
            double theta = (Math.PI * 2.0 * i) / samples;

            Point2D.Double pInner = rayContourIntersection(center, inner, theta);
            Point2D.Double pOuter = rayContourIntersection(center, outer, theta);

            if (pInner == null || pOuter == null) {
                missCount++;
                if (debug && missCount <= 24) {
                    System.out.printf(tag + "MISS i=%d theta=%.6f innerHit=%s outerHit=%s%n",
                            i, theta, pInner, pOuter);
                }
                continue;
            }

            hitCount++;

            Point2D.Double midD = new Point2D.Double(
                    (pInner.x + pOuter.x) / 2.0,
                    (pInner.y + pOuter.y) / 2.0
            );

            Point mid = clampMidpointToBand(midD, center, inner, outer, theta);

            if (midwayOrdered.isEmpty() || !samePoint(midwayOrdered.get(midwayOrdered.size() - 1), mid)) {
                midwayOrdered.add(mid);
            }

            if (debug && (i < 20 || i == samples - 1)) {
                System.out.printf(
                        tag + "i=%d theta=%.6f inner=(%.3f, %.3f) outer=(%.3f, %.3f) midD=(%.3f, %.3f) mid=(%d, %d)%n",
                        i, theta,
                        pInner.x, pInner.y,
                        pOuter.x, pOuter.y,
                        midD.x, midD.y,
                        mid.x, mid.y
                );
            }
        }

        if (debug) {
            System.out.printf(tag + "hitCount=%d missCount=%d midwayRawCount=%d%n",
                    hitCount, missCount, midwayOrdered.size());
        }

        midwayOrdered = normalizeOrderedLoop(midwayOrdered);

        int insideInnerCount = 0;
        int outsideOuterCount = 0;
        double maxStep = 0.0;
        int maxStepIndex = -1;

        for (int i = 0; i < midwayOrdered.size(); i++) {
            Point a = midwayOrdered.get(i);
            Point b = midwayOrdered.get((i + 1) % midwayOrdered.size());

            if (inner.inside(a)) insideInnerCount++;
            if (!outer.inside(a)) outsideOuterCount++;

            double step = a.distance(b);
            if (step > maxStep) {
                maxStep = step;
                maxStepIndex = i;
            }
        }

        Point stepA = (maxStepIndex >= 0 && !midwayOrdered.isEmpty())
                ? midwayOrdered.get(maxStepIndex)
                : null;
        Point stepB = (maxStepIndex >= 0 && !midwayOrdered.isEmpty())
                ? midwayOrdered.get((maxStepIndex + 1) % midwayOrdered.size())
                : null;

        if (debug) {
            debugPrintPoints(tag + "midwayOrdered", midwayOrdered, 24);
            System.out.printf(tag + "bandCheck insideInnerCount=%d outsideOuterCount=%d%n",
                    insideInnerCount, outsideOuterCount);
            System.out.printf(tag + "maxStep=%.4f at segment %d (%s -> %s)%n",
                    maxStep, maxStepIndex, stepA, stepB);
        }

        if (hitCount < 3 || midwayOrdered.size() < 3) {
            if (debug) {
                System.out.println(tag + "not enough valid midpoint samples -> returning empty");
                System.out.println(tag + "=== DEBUG END ===");
            }
            return ShapeContour.empty();
        }

        ShapeContour result = new ShapeContour(new PointCollection());
        result.finalizeFromOrdered(midwayOrdered);

        if (debug) {
            int resultInsideInnerCount = 0;
            int resultOutsideOuterCount = 0;

            for (Point p : result.contour) {
                if (inner.inside(p)) resultInsideInnerCount++;
                if (!outer.inside(p)) resultOutsideOuterCount++;
            }

            debugPrintContour(tag + "result", result, 16);
            System.out.printf(tag + "resultBandCheck insideInnerCount=%d outsideOuterCount=%d%n",
                    resultInsideInnerCount, resultOutsideOuterCount);
            System.out.println(tag + "=== DEBUG END ===");
        }

        return result;
    }*/
    // Replace the current halfway(...) with this version.

    private static final class RayHitIndex {
        private final Point2D.Double origin;
        private final Point2D.Double[] hits;

        private RayHitIndex(Point2D.Double origin, Point2D.Double[] hits) {
            this.origin = origin == null ? null : new Point2D.Double(origin.x, origin.y);
            this.hits = hits;
        }

        private Point2D.Double hit(int sampleIndex) {
            if (hits == null || hits.length == 0) {
                return null;
            }
            int idx = Math.floorMod(sampleIndex, hits.length);
            Point2D.Double p = hits[idx];
            return p == null ? null : new Point2D.Double(p.x, p.y);
        }

        private int size() {
            return hits == null ? 0 : hits.length;
        }
    }

    public ShapeContour halfway(ShapeContour other) {
        Objects.requireNonNull(other, "other");

        final boolean debug = false;
        final String tag = "[ShapeContour.halfway] ";

        if (this.isEmpty()) {
            if (debug) System.out.println(tag + "this is empty -> returning other");
            return other;
        }
        if (other.isEmpty()) {
            if (debug) System.out.println(tag + "other is empty -> returning this");
            return this;
        }

        boolean otherInsideThis = this.inside(other);
        boolean thisInsideOther = other.inside(this);

        ShapeContour outer;
        ShapeContour inner;

        if (otherInsideThis && !thisInsideOther) {
            outer = this;
            inner = other;
        } else if (thisInsideOther && !otherInsideThis) {
            outer = other;
            inner = this;
        } else {
            outer = this.area() >= other.area() ? this : other;
            inner = outer == this ? other : this;
        }

        Point2D.Double center = chooseHalfwayCenter(outer, inner);
        Point centerPt = new Point(roundInt(center.x), roundInt(center.y));

        int samples = Math.max(720, Math.max(outer.size(), inner.size()) * 2);

        RayHitIndex innerHits = buildRayHitIndex(center, inner, samples);
        RayHitIndex outerHits = buildRayHitIndex(center, outer, samples);

        if (debug) {
            System.out.println(tag + "=== DEBUG START ===");
            debugPrintContour(tag + "this", this, 12);
            debugPrintContour(tag + "other", other, 12);
            System.out.printf(tag + "otherInsideThis=%s, thisInsideOther=%s%n", otherInsideThis, thisInsideOther);
            System.out.printf(tag + "chosen outer=%s, inner=%s%n",
                    outer == this ? "this" : "other",
                    inner == this ? "this" : "other");
            System.out.printf(tag + "center=(%.3f, %.3f) centerRounded=%s%n", center.x, center.y, centerPt);
            System.out.printf(tag + "center inside inner=%s inside outer=%s%n",
                    inner.inside(centerPt), outer.inside(centerPt));
            System.out.printf(tag + "samples=%d%n", samples);
        }

        List<Point> midwayOrdered = new ArrayList<>(samples);
        int hitCount = 0;
        int missCount = 0;

        for (int i = 0; i < samples; i++) {
            double theta = (Math.PI * 2.0 * i) / samples;

            Point2D.Double pInner = innerHits.hit(i);
            Point2D.Double pOuter = outerHits.hit(i);

            if (pInner == null || pOuter == null) {
                missCount++;
                if (debug && missCount <= 24) {
                    System.out.printf(tag + "MISS i=%d theta=%.6f innerHit=%s outerHit=%s%n",
                            i, theta, pInner, pOuter);
                }
                continue;
            }

            hitCount++;

            Point2D.Double midD = new Point2D.Double(
                    (pInner.x + pOuter.x) / 2.0,
                    (pInner.y + pOuter.y) / 2.0
            );

            Point mid = clampMidpointToBand(midD, center, pInner, pOuter, theta, inner, outer);

            if (midwayOrdered.isEmpty() || !samePoint(midwayOrdered.get(midwayOrdered.size() - 1), mid)) {
                midwayOrdered.add(mid);
            }

            if (debug && (i < 20 || i == samples - 1)) {
                System.out.printf(
                        tag + "i=%d theta=%.6f inner=(%.3f, %.3f) outer=(%.3f, %.3f) midD=(%.3f, %.3f) mid=(%d, %d)%n",
                        i, theta,
                        pInner.x, pInner.y,
                        pOuter.x, pOuter.y,
                        midD.x, midD.y,
                        mid.x, mid.y
                );
            }
        }

        if (debug) {
            System.out.printf(tag + "hitCount=%d missCount=%d midwayRawCount=%d%n",
                    hitCount, missCount, midwayOrdered.size());
        }

        midwayOrdered = normalizeOrderedLoop(midwayOrdered);

        int insideInnerCount = 0;
        int outsideOuterCount = 0;
        double maxStep = 0.0;
        int maxStepIndex = -1;

        for (int i = 0; i < midwayOrdered.size(); i++) {
            Point a = midwayOrdered.get(i);
            Point b = midwayOrdered.get((i + 1) % midwayOrdered.size());

            if (inner.inside(a)) insideInnerCount++;
            if (!outer.inside(a)) outsideOuterCount++;

            double step = a.distance(b);
            if (step > maxStep) {
                maxStep = step;
                maxStepIndex = i;
            }
        }

        Point stepA = (maxStepIndex >= 0 && !midwayOrdered.isEmpty())
                ? midwayOrdered.get(maxStepIndex)
                : null;
        Point stepB = (maxStepIndex >= 0 && !midwayOrdered.isEmpty())
                ? midwayOrdered.get((maxStepIndex + 1) % midwayOrdered.size())
                : null;

        if (debug) {
            debugPrintPoints(tag + "midwayOrdered", midwayOrdered, 24);
            System.out.printf(tag + "bandCheck insideInnerCount=%d outsideOuterCount=%d%n",
                    insideInnerCount, outsideOuterCount);
            System.out.printf(tag + "maxStep=%.4f at segment %d (%s -> %s)%n",
                    maxStep, maxStepIndex, stepA, stepB);
        }

        if (hitCount < 3 || midwayOrdered.size() < 3) {
            if (debug) {
                System.out.println(tag + "not enough valid midpoint samples -> returning empty");
                System.out.println(tag + "=== DEBUG END ===");
            }
            return ShapeContour.empty();
        }

        ShapeContour result = new ShapeContour(new PointCollection());
        result.finalizeFromOrdered(midwayOrdered);

        if (debug) {
            int resultInsideInnerCount = 0;
            int resultOutsideOuterCount = 0;

            for (Point p : result.contour) {
                if (inner.inside(p)) resultInsideInnerCount++;
                if (!outer.inside(p)) resultOutsideOuterCount++;
            }

            debugPrintContour(tag + "result", result, 16);
            System.out.printf(tag + "resultBandCheck insideInnerCount=%d outsideOuterCount=%d%n",
                    resultInsideInnerCount, resultOutsideOuterCount);
            System.out.println(tag + "=== DEBUG END ===");
        }

        return result;
    }

    private static RayHitIndex buildRayHitIndex(Point2D.Double origin, ShapeContour contour, int samples) {
        if (origin == null || contour == null || contour.isEmpty() || samples <= 0) {
            return new RayHitIndex(origin, new Point2D.Double[0]);
        }

        List<Edge> contourEdges = contour.edges;
        if (contourEdges == null || contourEdges.isEmpty()) {
            contourEdges = buildEdges(contour.contour);
            if (contourEdges.isEmpty()) {
                return new RayHitIndex(origin, new Point2D.Double[samples]);
            }
        }

        final double ox = origin.x;
        final double oy = origin.y;
        final double twoPi = Math.PI * 2.0;
        final double eps = 1.0e-9;
        final double collinearTol = 1.0e-6;

        final int edgeCount = contourEdges.size();

        double[] ax = new double[edgeCount];
        double[] ay = new double[edgeCount];
        double[] sx = new double[edgeCount];
        double[] sy = new double[edgeCount];

        ArrayList<ArrayList<Integer>> buckets = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            buckets.add(new ArrayList<>());
        }

        for (int i = 0; i < edgeCount; i++) {
            Edge e = contourEdges.get(i);

            Point a = e.start;
            Point b = e.end;

            double axv = a.x;
            double ayv = a.y;
            double bxv = b.x;
            double byv = b.y;

            ax[i] = axv;
            ay[i] = ayv;
            sx[i] = bxv - axv;
            sy[i] = byv - ayv;

            double a0 = normalizeAngle(Math.atan2(ayv - oy, axv - ox));
            double a1 = normalizeAngle(Math.atan2(byv - oy, bxv - ox));

            if (ccwDelta(a0, a1) > Math.PI) {
                double tmp = a0;
                a0 = a1;
                a1 = tmp;
            }

            addEdgeToAngularBuckets(buckets, samples, a0, a1, i, twoPi);
        }

        Point2D.Double[] hits = new Point2D.Double[samples];

        for (int i = 0; i < samples; i++) {
            double theta = (twoPi * i) / samples;
            double rx = Math.cos(theta);
            double ry = Math.sin(theta);

            double bestT = Double.POSITIVE_INFINITY;
            double bestX = 0.0;
            double bestY = 0.0;
            boolean found = false;

            ArrayList<Integer> bucket = buckets.get(i);
            for (int e : bucket) {
                double qpx = ax[e] - ox;
                double qpy = ay[e] - oy;
                double denom = rx * sy[e] - ry * sx[e];

                if (Math.abs(denom) < eps) {
                    double ta = qpx * rx + qpy * ry;
                    double crossA = qpx * ry - qpy * rx;
                    if (Math.abs(crossA) <= collinearTol && ta >= -eps && ta < bestT) {
                        bestT = ta;
                        bestX = ax[e];
                        bestY = ay[e];
                        found = true;
                    }

                    double bx = ax[e] + sx[e];
                    double by = ay[e] + sy[e];
                    double pbx = bx - ox;
                    double pby = by - oy;
                    double tb = pbx * rx + pby * ry;
                    double crossB = pbx * ry - pby * rx;
                    if (Math.abs(crossB) <= collinearTol && tb >= -eps && tb < bestT) {
                        bestT = tb;
                        bestX = bx;
                        bestY = by;
                        found = true;
                    }
                    continue;
                }

                double t = (qpx * sy[e] - qpy * sx[e]) / denom;
                if (t < -eps || t >= bestT) {
                    continue;
                }

                double u = (qpx * ry - qpy * rx) / denom;
                if (u >= -eps && u <= 1.0 + eps) {
                    bestT = t;
                    bestX = ox + rx * t;
                    bestY = oy + ry * t;
                    found = true;
                }
            }

            if (found) {
                hits[i] = new Point2D.Double(bestX, bestY);
            }
        }

        return new RayHitIndex(origin, hits);
    }

    private static void addEdgeToAngularBuckets(
            ArrayList<ArrayList<Integer>> buckets,
            int samples,
            double startAngle,
            double endAngle,
            int edgeIndex,
            double twoPi
    ) {
        startAngle = normalizeAngle(startAngle);
        endAngle = normalizeAngle(endAngle);

        if (endAngle < startAngle) {
            endAngle += twoPi;
        }

        int from = (int) Math.floor((startAngle / twoPi) * samples) - 1;
        int to = (int) Math.ceil((endAngle / twoPi) * samples) + 1;

        for (int k = from; k <= to; k++) {
            buckets.get(Math.floorMod(k, samples)).add(edgeIndex);
        }
    }

    private static Point clampMidpointToBand(
            Point2D.Double midD,
            Point2D.Double center,
            Point2D.Double innerHit,
            Point2D.Double outerHit,
            double theta,
            ShapeContour inner,
            ShapeContour outer
    ) {
        if (midD == null) {
            return null;
        }

        double dirX = Math.cos(theta);
        double dirY = Math.sin(theta);

        if (innerHit == null || outerHit == null) {
            return new Point(roundInt(midD.x), roundInt(midD.y));
        }

        double tInner = projectAlongRay(center, dirX, dirY, innerHit.x, innerHit.y);
        double tOuter = projectAlongRay(center, dirX, dirY, outerHit.x, outerHit.y);
        double tMid = projectAlongRay(center, dirX, dirY, midD.x, midD.y);

        if (tInner > tOuter) {
            double tmp = tInner;
            tInner = tOuter;
            tOuter = tmp;
        }

        tMid = bound(tMid, tInner, tOuter);

        Point direct = pointOnRayRounded(center, dirX, dirY, tMid);
        if (isBandPoint(direct, inner, outer)) {
            return direct;
        }

        double span = tOuter - tInner;
        if (!(span > 0.0)) {
            return direct;
        }

        int probes = Math.max(64, Math.min(1024, ceilInt(span * 4.0)));
        double step = span / probes;

        for (int k = 1; k <= probes; k++) {
            double delta = k * step;

            double tLo = tMid - delta;
            if (tLo >= tInner) {
                Point pLo = pointOnRayRounded(center, dirX, dirY, tLo);
                if (isBandPoint(pLo, inner, outer)) {
                    return pLo;
                }
            }

            double tHi = tMid + delta;
            if (tHi <= tOuter) {
                Point pHi = pointOnRayRounded(center, dirX, dirY, tHi);
                if (isBandPoint(pHi, inner, outer)) {
                    return pHi;
                }
            }
        }

        Point best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int coarse = Math.max(16, Math.min(512, ceilInt(span * 2.0)));

        for (int i = 0; i <= coarse; i++) {
            double t = tInner + (span * i) / coarse;
            Point p = pointOnRayRounded(center, dirX, dirY, t);
            if (!isBandPoint(p, inner, outer)) {
                continue;
            }

            double score = Math.abs(t - tMid);
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }

        return best != null ? best : direct;
    }

    private static boolean rayContourIntersection(
            Point2D.Double origin,
            ShapeContour contour,
            double rx,
            double ry,
            Point2D.Double out
    ) {
        if (origin == null || contour == null || contour.isEmpty() || out == null) {
            return false;
        }

        final double ox = origin.x;
        final double oy = origin.y;
        final double eps = 1.0e-9;

        double bestT = Double.POSITIVE_INFINITY;
        double bestX = 0.0;
        double bestY = 0.0;
        boolean found = false;

        List<Edge> contourEdges = contour.edges;
        if (contourEdges != null && !contourEdges.isEmpty()) {
            for (Edge e : contourEdges) {
                Point a = e.start;
                Point b = e.end;

                double ax = a.x;
                double ay = a.y;
                double sx = b.x - ax;
                double sy = b.y - ay;
                double qpx = ax - ox;
                double qpy = ay - oy;

                double denom = rx * sy - ry * sx;

                if (Math.abs(denom) < eps) {
                    double ta = qpx * rx + qpy * ry;
                    double crossA = qpx * ry - qpy * rx;
                    if (Math.abs(crossA) <= 1.0e-6 && ta >= -eps && ta < bestT) {
                        bestT = ta;
                        bestX = ax;
                        bestY = ay;
                        found = true;
                    }

                    double bx = b.x;
                    double by = b.y;
                    double pbx = bx - ox;
                    double pby = by - oy;
                    double tb = pbx * rx + pby * ry;
                    double crossB = pbx * ry - pby * rx;
                    if (Math.abs(crossB) <= 1.0e-6 && tb >= -eps && tb < bestT) {
                        bestT = tb;
                        bestX = bx;
                        bestY = by;
                        found = true;
                    }
                    continue;
                }

                double t = (qpx * sy - qpy * sx) / denom;
                double u = (qpx * ry - qpy * rx) / denom;

                if (t >= -eps && u >= -eps && u <= 1.0 + eps && t < bestT) {
                    bestT = t;
                    bestX = ox + rx * t;
                    bestY = oy + ry * t;
                    found = true;
                }
            }
        } else {
            List<Point> pts = contour.contour;
            for (int i = 0, n = pts.size(); i < n; i++) {
                Point a = pts.get(i);
                Point b = pts.get((i + 1) % n);

                double ax = a.x;
                double ay = a.y;
                double sx = b.x - ax;
                double sy = b.y - ay;
                double qpx = ax - ox;
                double qpy = ay - oy;

                double denom = rx * sy - ry * sx;

                if (Math.abs(denom) < eps) {
                    double ta = qpx * rx + qpy * ry;
                    double crossA = qpx * ry - qpy * rx;
                    if (Math.abs(crossA) <= 1.0e-6 && ta >= -eps && ta < bestT) {
                        bestT = ta;
                        bestX = ax;
                        bestY = ay;
                        found = true;
                    }

                    double bx = b.x;
                    double by = b.y;
                    double pbx = bx - ox;
                    double pby = by - oy;
                    double tb = pbx * rx + pby * ry;
                    double crossB = pbx * ry - pby * rx;
                    if (Math.abs(crossB) <= 1.0e-6 && tb >= -eps && tb < bestT) {
                        bestT = tb;
                        bestX = bx;
                        bestY = by;
                        found = true;
                    }
                    continue;
                }

                double t = (qpx * sy - qpy * sx) / denom;
                double u = (qpx * ry - qpy * rx) / denom;

                if (t >= -eps && u >= -eps && u <= 1.0 + eps && t < bestT) {
                    bestT = t;
                    bestX = ox + rx * t;
                    bestY = oy + ry * t;
                    found = true;
                }
            }
        }

        if (!found) {
            return false;
        }

        out.x = bestX;
        out.y = bestY;
        return true;
    }


    private static Point clampMidpointToBand(
            double midX,
            double midY,
            Point2D.Double center,
            ShapeContour inner,
            ShapeContour outer,
            double dirX,
            double dirY,
            Point2D.Double innerHit,
            Point2D.Double outerHit
    ) {
        if (innerHit == null || outerHit == null) {
            return new Point(roundInt(midX), roundInt(midY));
        }

        double cx = center.x;
        double cy = center.y;

        double tInner = (innerHit.x - cx) * dirX + (innerHit.y - cy) * dirY;
        double tOuter = (outerHit.x - cx) * dirX + (outerHit.y - cy) * dirY;
        double tMid = (midX - cx) * dirX + (midY - cy) * dirY;

        if (tInner > tOuter) {
            double tmp = tInner;
            tInner = tOuter;
            tOuter = tmp;
        }

        tMid = bound(tMid, tInner, tOuter);

        Point direct = new Point(
                roundInt(cx + dirX * tMid),
                roundInt(cy + dirY * tMid)
        );
        if (isBandPoint(direct, inner, outer)) {
            return direct;
        }

        double span = tOuter - tInner;
        if (!(span > 0.0)) {
            return direct;
        }

        int probes = Math.max(64, Math.min(1024, ceilInt(span * 4.0)));
        double step = span / probes;

        for (int k = 1; k <= probes; k++) {
            double delta = k * step;

            double tLo = tMid - delta;
            if (tLo >= tInner) {
                Point pLo = new Point(
                        roundInt(cx + dirX * tLo),
                        roundInt(cy + dirY * tLo)
                );
                if (isBandPoint(pLo, inner, outer)) {
                    return pLo;
                }
            }

            double tHi = tMid + delta;
            if (tHi <= tOuter) {
                Point pHi = new Point(
                        roundInt(cx + dirX * tHi),
                        roundInt(cy + dirY * tHi)
                );
                if (isBandPoint(pHi, inner, outer)) {
                    return pHi;
                }
            }
        }

        Point best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int coarse = Math.max(16, Math.min(512, ceilInt(span * 2.0)));

        for (int i = 0; i <= coarse; i++) {
            double t = tInner + (span * i) / coarse;
            Point p = new Point(
                    roundInt(cx + dirX * t),
                    roundInt(cy + dirY * t)
            );
            if (!isBandPoint(p, inner, outer)) {
                continue;
            }

            double score = Math.abs(t - tMid);
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }

        return best != null ? best : direct;
    }

    private static final class HalfwayPerimeterCursor {
        private final List<Point> pts;
        private final double[] arc;
        private final int n;
        private final double perimeter;
        private final boolean degenerate;

        private int edgeIndex;
        private double segStart;
        private double segEnd;
        private Point a;
        private Point b;
        private double dx;
        private double dy;
        private double segLen;

        private HalfwayPerimeterCursor(ShapeContour contour) {
            this.pts = contour.contour;
            this.arc = contour.cumulativeArcLengths;
            this.n = pts.size();
            this.perimeter = contour.perimeter;
            this.degenerate = (n == 0 || n == 1 || !(perimeter > 0.0));

            if (!degenerate) {
                loadEdge(0);
            }
        }

        private void loadEdge(int idx) {
            this.edgeIndex = idx;
            this.segStart = arc[idx];
            this.segEnd = (idx == n - 1) ? perimeter : arc[idx + 1];
            this.a = pts.get(idx);
            this.b = pts.get((idx + 1) % n);
            this.dx = b.x - a.x;
            this.dy = b.y - a.y;
            this.segLen = segEnd - segStart;
        }

        private void sampleNormalized(double normalized, double[] out) {
            if (n == 0) {
                out[0] = 0.0;
                out[1] = 0.0;
                return;
            }

            if (degenerate) {
                Point p = pts.get(0);
                out[0] = p.x;
                out[1] = p.y;
                return;
            }

            double target = normalized * perimeter;

            // Match old pointOnPerimeter() behavior:
            // it chooses the first segment where target <= end.
            while (edgeIndex < n - 1 && target > segEnd) {
                loadEdge(edgeIndex + 1);
            }

            if (segLen <= 0.0) {
                out[0] = b.x;
                out[1] = b.y;
                return;
            }

            double u = (target - segStart) / segLen;
            out[0] = a.x + dx * u;
            out[1] = a.y + dy * u;
        }
    }

// Add these helpers inside ShapeContour.

    private ShapeContour halfwayDegenerate(ShapeContour other) {
        final int sizeA = this.contour.size();
        final int sizeB = other.contour.size();
        final int samples = Math.max(sizeA, sizeB);

        if (samples <= 0) return ShapeContour.empty();

        ArrayList<Point> pts = new ArrayList<>(samples);

        for (int i = 0; i < samples; i++) {
            Point a = this.contour.get((int) (((long) i * sizeA) / samples));
            Point b = other.contour.get((int) (((long) i * sizeB) / samples));

            final int mx = roundInt((a.x + b.x) * 0.5);
            final int my = roundInt((a.y + b.y) * 0.5);

            if (pts.isEmpty()) {
                pts.add(new Point(mx, my));
            } else {
                Point last = pts.get(pts.size() - 1);
                if (last.x != mx || last.y != my) {
                    pts.add(new Point(mx, my));
                }
            }
        }

        List<Point> normalized = normalizeOrderedLoop(pts);
        if (normalized.size() < 3) return ShapeContour.empty();

        ShapeContour result = new ShapeContour(new PointCollection());
        result.finalizeFromOrdered(normalized);
        return result;
    }

    private static final class PerimeterCursor {
        private final List<Point> pts;
        private final double[] arc;
        private final int n;
        private final double perim;
        private final boolean degenerate;

        private int edge;
        private double segStart;
        private double segEnd;
        private Point a;
        private Point b;
        private double dx;
        private double dy;
        private double segLen;

        private PerimeterCursor(ShapeContour contour) {
            this.pts = contour.contour;
            this.arc = contour.cumulativeArcLengths;
            this.n = pts.size();
            this.perim = contour.perimeter;
            this.degenerate = n == 0 || n == 1 || !(perim > 0.0);

            if (!degenerate) {
                this.edge = 0;
                this.segStart = 0.0;
                this.segEnd = (n == 1) ? 0.0 : arc[1];
                this.a = pts.get(0);
                this.b = pts.get(1 % n);
                this.dx = b.x - a.x;
                this.dy = b.y - a.y;
                this.segLen = segEnd - segStart;
            }
        }

        private void sample(double target, double[] xy) {
            if (degenerate) {
                Point p = pts.isEmpty() ? new Point() : pts.get(0);
                xy[0] = p.x;
                xy[1] = p.y;
                return;
            }

            while (edge < n - 1 && target > segEnd) {
                edge++;
                segStart = arc[edge];
                segEnd = (edge == n - 1) ? perim : arc[edge + 1];
                a = pts.get(edge);
                b = pts.get((edge + 1) % n);
                dx = b.x - a.x;
                dy = b.y - a.y;
                segLen = segEnd - segStart;
            }

            if (!(segLen > 0.0)) {
                xy[0] = b.x;
                xy[1] = b.y;
                return;
            }

            double t = (target - segStart) / segLen;
            xy[0] = a.x + dx * t;
            xy[1] = a.y + dy * t;
        }
    }

    private static Point2D.Double chooseHalfwayCenter(ShapeContour outer, ShapeContour inner) {
        Point2D.Double c = inner.centroid();
        Point rounded = new Point(roundInt(c.x), roundInt(c.y));
        if (isStrictlyInside(inner, rounded)) {
            return c;
        }

        Iterator<Point> it = inner.insideIterator();
        while (it.hasNext()) {
            Point p = it.next();
            if (isStrictlyInside(inner, p)) {
                return new Point2D.Double(p.x, p.y);
            }
        }

        if (inner.inside(rounded)) {
            return c;
        }

        Point top = inner.topPoint();
        Point bottom = inner.bottomPoint();
        Point left = inner.leftPoint();
        Point right = inner.rightPoint();

        Point[] fallbacks = {
                top, bottom, left, right,
                top != null && bottom != null
                        ? new Point(roundInt((top.x + bottom.x) / 2.0), roundInt((top.y + bottom.y) / 2.0))
                        : null,
                left != null && right != null
                        ? new Point(roundInt((left.x + right.x) / 2.0), roundInt((left.y + right.y) / 2.0))
                        : null
        };

        for (Point p : fallbacks) {
            if (p != null && isStrictlyInside(inner, p)) {
                return new Point2D.Double(p.x, p.y);
            }
        }

        Point2D.Double outerC = outer.centroid();
        return new Point2D.Double(outerC.x, outerC.y);
    }

    private static Point2D.Double rayContourIntersection(Point2D.Double origin, ShapeContour contour, double theta) {
        if (origin == null || contour == null || contour.isEmpty()) {
            return null;
        }

        double rx = Math.cos(theta);
        double ry = Math.sin(theta);
        double bestT = Double.POSITIVE_INFINITY;
        Point2D.Double best = null;
        final double eps = 1.0e-9;

        int n = contour.contour.size();
        for (int i = 0; i < n; i++) {
            Point a = contour.contour.get(i);
            Point b = contour.contour.get((i + 1) % n);

            double sx = b.x - a.x;
            double sy = b.y - a.y;
            double qpx = a.x - origin.x;
            double qpy = a.y - origin.y;

            double denom = cross2(rx, ry, sx, sy);

            if (Math.abs(denom) < eps) {
                double ta = projectAlongRay(origin, rx, ry, a.x, a.y);
                double tb = projectAlongRay(origin, rx, ry, b.x, b.y);

                if (pointNearRay(origin, rx, ry, a.x, a.y, 1.0e-6) && ta >= -eps && ta < bestT) {
                    bestT = ta;
                    best = new Point2D.Double(a.x, a.y);
                }
                if (pointNearRay(origin, rx, ry, b.x, b.y, 1.0e-6) && tb >= -eps && tb < bestT) {
                    bestT = tb;
                    best = new Point2D.Double(b.x, b.y);
                }
                continue;
            }

            double t = cross2(qpx, qpy, sx, sy) / denom;
            double u = cross2(qpx, qpy, rx, ry) / denom;

            if (t >= -eps && u >= -eps && u <= 1.0 + eps) {
                if (t < bestT) {
                    bestT = t;
                    best = new Point2D.Double(origin.x + rx * t, origin.y + ry * t);
                }
            }
        }

        return best;
    }

    private static Point clampMidpointToBand(
            Point2D.Double midD,
            Point2D.Double center,
            ShapeContour inner,
            ShapeContour outer,
            double theta
    ) {
        if (midD == null) {
            return null;
        }

        double dirX = Math.cos(theta);
        double dirY = Math.sin(theta);
        Point2D.Double innerHit = rayContourIntersection(center, inner, theta);
        Point2D.Double outerHit = rayContourIntersection(center, outer, theta);

        if (innerHit == null || outerHit == null) {
            return new Point(roundInt(midD.x), roundInt(midD.y));
        }

        double tInner = projectAlongRay(center, dirX, dirY, innerHit.x, innerHit.y);
        double tOuter = projectAlongRay(center, dirX, dirY, outerHit.x, outerHit.y);
        double tMid = projectAlongRay(center, dirX, dirY, midD.x, midD.y);

        if (tInner > tOuter) {
            double tmp = tInner;
            tInner = tOuter;
            tOuter = tmp;
        }

        tMid = bound(tMid, tInner, tOuter);

        Point direct = pointOnRayRounded(center, dirX, dirY, tMid);
        if (isBandPoint(direct, inner, outer)) {
            return direct;
        }

        double span = tOuter - tInner;
        if (!(span > 0.0)) {
            return direct;
        }

        int probes = Math.max(64, Math.min(1024, ceilInt(span * 4.0)));
        double step = span / probes;

        for (int k = 1; k <= probes; k++) {
            double delta = k * step;

            double tLo = tMid - delta;
            if (tLo >= tInner) {
                Point pLo = pointOnRayRounded(center, dirX, dirY, tLo);
                if (isBandPoint(pLo, inner, outer)) {
                    return pLo;
                }
            }

            double tHi = tMid + delta;
            if (tHi <= tOuter) {
                Point pHi = pointOnRayRounded(center, dirX, dirY, tHi);
                if (isBandPoint(pHi, inner, outer)) {
                    return pHi;
                }
            }
        }

        Point best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int coarse = Math.max(16, Math.min(512, ceilInt(span * 2.0)));

        for (int i = 0; i <= coarse; i++) {
            double t = tInner + (span * i) / coarse;
            Point p = pointOnRayRounded(center, dirX, dirY, t);
            if (!isBandPoint(p, inner, outer)) {
                continue;
            }

            double score = Math.abs(t - tMid);
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }

        return best != null ? best : direct;
    }

    private static boolean isStrictlyInside(ShapeContour contour, Point p) {
        if (contour == null || p == null) {
            return false;
        }
        return contour.inside(p) && !pointOnContour(contour, p);
    }

    private static boolean pointOnContour(ShapeContour contour, Point p) {
        if (contour == null || p == null || contour.contour.isEmpty()) {
            return false;
        }

        int n = contour.contour.size();
        for (int i = 0; i < n; i++) {
            Point a = contour.contour.get(i);
            Point b = contour.contour.get((i + 1) % n);
            if (pointOnSegment(p, a, b)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBandPoint(Point p, ShapeContour inner, ShapeContour outer) {
        return p != null && outer.inside(p) && !inner.inside(p);
    }

    private static Point pointOnRayRounded(Point2D.Double origin, double dirX, double dirY, double t) {
        return new Point(
                roundInt(origin.x + dirX * t),
                roundInt(origin.y + dirY * t)
        );
    }

    private static double cross2(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }

    private static double projectAlongRay(Point2D.Double origin, double dirX, double dirY, double x, double y) {
        return (x - origin.x) * dirX + (y - origin.y) * dirY;
    }

    private static boolean pointNearRay(
            Point2D.Double origin,
            double dirX,
            double dirY,
            double x,
            double y,
            double tolerance
    ) {
        double px = x - origin.x;
        double py = y - origin.y;

        if (Math.abs(cross2(px, py, dirX, dirY)) > tolerance) {
            return false;
        }

        return px * dirX + py * dirY >= -tolerance;
    }

    private static List<Point2D.Double> samplePerimeter(ShapeContour contour, int samples) {
        List<Point2D.Double> out = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            double t = (double) i / samples;
            Point2D.Double p = contour.pointOnPerimeterNormalized(t);
            if (p != null) {
                out.add(new Point2D.Double(p.x, p.y));
            }
        }
        return out;
    }

    private static List<Point2D.Double> reverseSamples(List<Point2D.Double> samples) {
        List<Point2D.Double> out = new ArrayList<>(samples.size());
        for (int i = samples.size() - 1; i >= 0; i--) {
            Point2D.Double p = samples.get(i);
            out.add(new Point2D.Double(p.x, p.y));
        }
        return out;
    }

    private static int findBestCircularShift(List<Point2D.Double> fixed, List<Point2D.Double> moving) {
        int n = Math.min(fixed.size(), moving.size());
        if (n == 0) return 0;

        int bestShift = 0;
        double bestSumSq = Double.POSITIVE_INFINITY;

        for (int shift = 0; shift < n; shift++) {
            double sumSq = 0.0;

            for (int i = 0; i < n; i++) {
                Point2D.Double a = fixed.get(i);
                Point2D.Double b = moving.get((i + shift) % n);

                double dx = a.x - b.x;
                double dy = a.y - b.y;
                sumSq += dx * dx + dy * dy;

                if (sumSq >= bestSumSq) {
                    break;
                }
            }

            if (sumSq < bestSumSq) {
                bestSumSq = sumSq;
                bestShift = shift;
            }
        }

        return bestShift;
    }

    private static double computeShiftRmse(List<Point2D.Double> fixed, List<Point2D.Double> moving, int shift) {
        int n = Math.min(fixed.size(), moving.size());
        if (n == 0) return Double.POSITIVE_INFINITY;

        double sumSq = 0.0;
        for (int i = 0; i < n; i++) {
            Point2D.Double a = fixed.get(i);
            Point2D.Double b = moving.get((i + shift) % n);
            double dx = a.x - b.x;
            double dy = a.y - b.y;
            sumSq += dx * dx + dy * dy;
        }

        return Math.sqrt(sumSq / n);
    }

    private static List<Point> normalizeOrderedLoop(List<Point> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }

        List<Point> out = points.stream()
                .map(Point::new)
                .collect(Collectors.toCollection(ArrayList::new));

        if (out.size() >= 2 && samePoint(out.get(0), out.get(out.size() - 1))) {
            out.remove(out.size() - 1);
        }

        dedupeSpikes(out);

        int w = 0;
        for (int i = 0; i < out.size(); i++) {
            Point p = out.get(i);
            if (w == 0 || !samePoint(out.get(w - 1), p)) {
                out.set(w++, p);
            }
        }
        while (out.size() > w) {
            out.remove(out.size() - 1);
        }

        if (out.size() >= 2 && samePoint(out.get(0), out.get(out.size() - 1))) {
            out.remove(out.size() - 1);
        }

        if (out.size() < 3) {
            return Collections.emptyList();
        }

        if (signedArea(out) < 0.0) {
            Collections.reverse(out);
        }

        int idx = 0;
        for (int i = 1; i < out.size(); i++) {
            Point a = out.get(idx);
            Point b = out.get(i);
            if (b.y > a.y || (b.y == a.y && b.x < a.x)) {
                idx = i;
            }
        }

        if (idx > 0) {
            Collections.rotate(out, -idx);
        }

        return out;
    }

    private static boolean samePoint(Point a, Point b) {
        return a != null && b != null && a.x == b.x && a.y == b.y;
    }

    private static void debugPrintContour(String label, ShapeContour contour, int limit) {
        if (contour == null) {
            System.out.println(label + " = null");
            return;
        }

        System.out.printf(
                "%s size=%d perimeter=%.4f area=%.4f ccw=%s top=%s bottom=%s left=%s right=%s%n",
                label,
                contour.size(),
                contour.perimeter(),
                contour.area(),
                contour.isCounterClockwise(),
                contour.topPoint(),
                contour.bottomPoint(),
                contour.leftPoint(),
                contour.rightPoint()
        );

        int n = Math.min(limit, contour.size());
        for (int i = 0; i < n; i++) {
            Point p = contour.get(i);
            System.out.printf("%s[%d] = (%d, %d) normArc=%.6f%n",
                    label, i, p.x, p.y,                contour.normalizedArcLengthTo(i));
        }
    }

    private static void debugPrintSamples(String label, List<Point2D.Double> samples, int limit) {
        if (samples == null) {
            System.out.println(label + " = null");
            return;
        }

        System.out.printf("%s count=%d%n", label, samples.size());

        int n = Math.min(limit, samples.size());
        for (int i = 0; i < n; i++) {
            Point2D.Double p = samples.get(i);
            System.out.printf("%s[%d] = (%.3f, %.3f)%n", label, i, p.x, p.y);
        }
    }

    private static void debugPrintPoints(String label, List<Point> points, int limit) {
        if (points == null) {
            System.out.println(label + " = null");
            return;
        }

        System.out.printf("%s count=%d%n", label, points.size());

        int n = Math.min(limit, points.size());
        for (int i = 0; i < n; i++) {
            Point p = points.get(i);
            System.out.printf("%s[%d] = (%d, %d)%n", label, i, p.x, p.y);
        }
    }

    /**
     * Sample contour along ray from provided centroid at angle-from-top.
     * Uses contour's radiusAtAngle() but re-centers to 'c' instead of its own centroid.
     */
    private static Point2D.Double polarSampleAt(ShapeContour sc, Point2D.Double c, double angleFromTop) {
        if (sc == null || sc.isEmpty()) return null;

        // sc.radiusAtAngle expects angle-from-top [10]
        double r = sc.radiusAtAngle(angleFromTop);

        // Convert angle-from-top to raw angle (x-axis) like polarToCartesian does [10]
        double raw = MathEx.normalizeAngle(angleFromTop + Math.PI / 2.0);

        double x = c.x + Math.cos(raw) * r;
        double y = c.y + Math.sin(raw) * r;
        return new Point2D.Double(x, y);
    }

    private static Point2D.Double pointOnSegmentByArc(ShapeContour sc, int i, double target, double perimeter) {
        int n = sc.contour.size();
        if (n == 0) return null;

        double start = sc.cumulativeArcLengths[i];
        double end = (i == n - 1) ? perimeter : sc.cumulativeArcLengths[i + 1];

        double segLen = end - start;
        Point a = sc.contour.get(i);
        Point b = sc.contour.get((i + 1) % n);

        if (segLen <= 1e-12) return new Point2D.Double(b.x, b.y);

        double u = (target - start) / segLen;
        double x = a.x + (b.x - a.x) * u;
        double y = a.y + (b.y - a.y) * u;
        return new Point2D.Double(x, y);
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



    public String toDebugDumpString() {
        StringBuilder out = new StringBuilder(Math.max(64, size() * 16));
        out.append("CTSHAPECONTOUR\t1\n");
        out.append("SIZE\t").append(size()).append('\n');
        for (Point point : contour) {
            out.append("P\t").append(point.x).append('\t').append(point.y).append('\n');
        }
        return out.toString();
    }

    public static ShapeContour fromDebugDumpString(String dump) {
        if (dump == null) {
            throw new IllegalArgumentException("ShapeContour debug dump cannot be null");
        }
        String[] lines = dump.split("\\R", -1);
        if (lines.length == 0 || !"CTSHAPECONTOUR\t1".equals(lines[0])) {
            throw new IllegalArgumentException("Not a ShapeContour debug dump");
        }

        int expectedSize = -1;
        ArrayList<Point> points = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isEmpty()) continue;
            String[] parts = line.split("\\t");
            if (parts.length == 0) continue;
            if ("SIZE".equals(parts[0]) && parts.length >= 2) {
                expectedSize = Integer.parseInt(parts[1]);
                points.ensureCapacity(Math.max(0, expectedSize));
                continue;
            }
            if (!"P".equals(parts[0]) || parts.length < 3) {
                throw new IllegalArgumentException("Malformed ShapeContour dump line: " + line);
            }
            points.add(new Point(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }

        if (expectedSize >= 0 && points.size() != expectedSize) {
            throw new IllegalArgumentException("ShapeContour dump declared " + expectedSize + " points but decoded " + points.size());
        }

        ShapeContour contour = new ShapeContour(new PointCollection());
        contour.finalizeFromOrdered(points);
        return contour;
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
        if (leftPoint != null && rightPoint != null && topPoint != null && bottomPoint != null) {
            if (point.x < leftPoint.x || point.x > rightPoint.x ||
                    point.y < bottomPoint.y || point.y > topPoint.y) {
                return false;
            }
        }
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
        if (this.isEmpty()) return false;

        // Quick reject by bounding boxes (if other's bbox exceeds this bbox, can't be inside)
        if (this.leftPoint != null && this.rightPoint != null && this.topPoint != null && this.bottomPoint != null &&
                other.leftPoint != null && other.rightPoint != null && other.topPoint != null && other.bottomPoint != null) {

            if (other.leftPoint.x < this.leftPoint.x || other.rightPoint.x > this.rightPoint.x ||
                    other.bottomPoint.y < this.bottomPoint.y || other.topPoint.y > this.topPoint.y) {
                return false;
            }
        }

        // 1) If any edges intersect, other is not fully inside this.
        // (Touching can be treated as inside by allowing endpoint/collinear touches.)
        if (edgesIntersect(other)) return false;

        // 2) With no intersections, checking one point is enough for containment.
        // Use a vertex of other; inside(Point) counts "on contour" as inside [10].
        Point witness = other.contour.get(0);
        return this.inside(witness);
    }

    private boolean edgesIntersect(ShapeContour other) {
        // Iterate over prebuilt edges [10]
        List<Edge> a = this.edges;
        List<Edge> b = other.edges;
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;

        for (Edge edge : a) {
            Point a1 = edge.start;
            Point a2 = edge.end;
            for (Edge value : b) {
                Point b1 = value.start;
                Point b2 = value.end;
                if (segmentsProperlyIntersectOrCross(a1, a2, b1, b2)) return true;
            }
        }
        return false;
    }

    // Returns true if segments cross in a way that breaks containment.
// Allows touching at endpoints / collinear overlaps to be treated as "not breaking" containment.
    private static boolean segmentsProperlyIntersectOrCross(Point p1, Point p2, Point q1, Point q2) {
        // Fast bounding-box reject
        if (Math.max(p1.x, p2.x) < Math.min(q1.x, q2.x) ||
                Math.max(q1.x, q2.x) < Math.min(p1.x, p2.x) ||
                Math.max(p1.y, p2.y) < Math.min(q1.y, q2.y) ||
                Math.max(q1.y, q2.y) < Math.min(p1.y, p2.y)) {
            return false;
        }

        long o1 = orient(p1, p2, q1);
        long o2 = orient(p1, p2, q2);
        long o3 = orient(q1, q2, p1);
        long o4 = orient(q1, q2, p2);

        // Proper crossing
        if ((o1 > 0 && o2 < 0 || o1 < 0 && o2 > 0) &&
                (o3 > 0 && o4 < 0 || o3 < 0 && o4 > 0)) {
            return true;
        }

        // Collinear/touching cases: treat as NOT an intersection that breaks containment.
        // If you want "touching means not inside", change these to `return true`.
        if (o1 == 0 && onSegment(p1, p2, q1)) return false;
        if (o2 == 0 && onSegment(p1, p2, q2)) return false;
        if (o3 == 0 && onSegment(q1, q2, p1)) return false;
        if (o4 == 0 && onSegment(q1, q2, p2)) return false;

        return false;
    }

    private static long orient(Point a, Point b, Point c) {
        return (long)(b.x - a.x) * (c.y - a.y) - (long)(b.y - a.y) * (c.x - a.x);
    }

    private static boolean onSegment(Point a, Point b, Point p) {
        return p.x >= Math.min(a.x, b.x) && p.x <= Math.max(a.x, b.x) &&
                p.y >= Math.min(a.y, b.y) && p.y <= Math.max(a.y, b.y);
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
        for (Point point : ordered) {
            xs.add(point.x);
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


    private static boolean colContainsY(IntList ys, int y) {
        if (ys == null || ys.isEmpty()) return false;
        return ys.contains(y);
    }
    private static boolean colContainsY(IntList ys, int y, Map<Integer, Boolean> cachedLookups) {
        return cachedLookups.computeIfAbsent(y, key -> colContainsY(ys, y));
    }
    private static boolean[] colContainsY(IntList ys, int... y) {
        if (ys == null || ys.isEmpty()) return CollectionsEx.fill(boolean[]::new, y.length, false);
        return ys.contains(y);
    }
    /*
    private static boolean[] colContainsY(IntList ys, Map<Integer, Boolean> cachedLookups, int... y) {
        if (ys == null || ys.isEmpty()) return CollectionsEx.fill(boolean[]::new, y.length, false);
        boolean[] out = new boolean[y.length];
        Map<Integer, Integer> toLookup = new HashMap<>(y.length);
        for (int i = 0; i < y.length; i++) {
            int y1 = y[i];
            Boolean present;
            if ((present = cachedLookups.get(y1)) != null) {
                out[i] = present;
            } else {
                toLookup.put(i, y1);
            }
        }
        if (toLookup.isEmpty()) return out;
        boolean[] remainingOut = ys.contains(CollectionsEx.toPrimitiveInt(toLookup.values()));
        toLookup.forEach((i, y1) -> {
            out[i] = remainingOut[i];
        });
        return out;
    }*/
    private static boolean[] colContainsY(IntList ys, Map<Integer, Boolean> cachedLookups, int... y) {
        if (ys == null || ys.isEmpty()) return CollectionsEx.fill(boolean[]::new, y.length, false);

        boolean[] out = new boolean[y.length];

        for (int i = 0; i < y.length; i++) {
            int y1 = y[i];
            Boolean present;

            if ((present = cachedLookups.get(y1)) != null) {
                out[i] = present;
            } else {
                boolean[] presentArr = ys.contains(y);

                for (int j = 0; j < y.length; j++) {
                    out[j] = presentArr[j];
                    cachedLookups.put(y[j], presentArr[j]);
                }

                return out;
            }
        }

        return out;
    }
    /*
    private static boolean[] colContainsY(IntList ys, Map<Integer, Boolean> cachedLookups, int... y) {
        boolean[] out = new boolean[y.length];

        if (y.length == 0 || ys == null || ys.isEmpty()) {
            return out;
        }

        int[] missedYs = new int[y.length];
        int[] missedIdx = new int[y.length];
        int missedCount = 0;

        for (int i = 0; i < y.length; i++) {
            int value = y[i];
            Boolean cached = cachedLookups.get(value);

            if (cached != null) {
                out[i] = cached;
            } else {
                missedIdx[missedCount] = i;
                missedYs[missedCount] = value;
                missedCount++;
            }
        }

        if (missedCount == 0) {
            return out;
        }

        int[] queryYs = java.util.Arrays.copyOf(missedYs, missedCount);
        boolean[] results = ys.contains(queryYs);

        for (int i = 0; i < missedCount; i++) {
            boolean present = results[i];
            int index = missedIdx[i];
            int value = queryYs[i];

            out[index] = present;
            cachedLookups.put(value, present);
        }

        return out;
    }*/

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

