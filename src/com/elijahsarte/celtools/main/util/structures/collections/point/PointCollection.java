package com.elijahsarte.celtools.main.util.structures.collections.point;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.util.*;
import com.elijahsarte.celtools.main.util.datastructures.EnhancedTreeMap;
import com.elijahsarte.celtools.main.util.function.fntypes.TriConsumer;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.main.util.structures.collections.IntList;
import com.elijahsarte.celtools.main.util.structures.collections.lazy.LazyList;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.varExec;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.varMutate;


public class PointCollection implements Iterable<Point> {

    private EnhancedTreeMap<Integer, IntList> pointMap;
    private final AtomicInteger size = new AtomicInteger(0);

    private final AtomicReference<Point> leftPoint = new AtomicReference<>(null);
    private final AtomicReference<Point> rightPoint = new AtomicReference<>(null);
    private final AtomicReference<Point> topPoint = new AtomicReference<>(null);
    private final AtomicReference<Point> bottomPoint = new AtomicReference<>(null);

    private transient boolean raw = false;

    public PointCollection() {
        this.pointMap = new EnhancedTreeMap<>();
    }

    // avoid erasure conflicts
    public PointCollection(Supplier<Map<Integer, IntList>> pointMapSupplier) {
        this.pointMap = (EnhancedTreeMap<Integer, IntList>) ProgrammingEx.varOper(pointMapSupplier.get(), m -> m instanceof EnhancedTreeMap ? m : new EnhancedTreeMap<>(m));
        this.size.set(this.pointMap.values().stream().mapToInt(IntList::size).sum());
        recomputeTrackedPoints();
    }

    public PointCollection(Map<Integer, List<Integer>> pointMap) {
        this(() -> pointMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> new IntList(e.getValue()),
                (a, b) -> a,
                EnhancedTreeMap::new
        )));
    }

    public PointCollection(List<Point> points) {
        this(Main.pointToMap(points));
    }
    public PointCollection(Point... points) {
        this(List.of(points));
    }

    public PointCollection(PointCollection ptCollection) {
        this.pointMap = new EnhancedTreeMap<>(ptCollection.pointMap, k -> k, IntList::new);
        this.size.set(ptCollection.size.get());
        this.leftPoint.set(Optional.ofNullable(ptCollection.leftPoint.get()).map(Point::new).orElse(null));
        this.rightPoint.set(Optional.ofNullable(ptCollection.rightPoint.get()).map(Point::new).orElse(null));
        this.topPoint.set(Optional.ofNullable(ptCollection.topPoint.get()).map(Point::new).orElse(null));
        this.bottomPoint.set(Optional.ofNullable(ptCollection.bottomPoint.get()).map(Point::new).orElse(null));
    }


    EnhancedTreeMap<Integer, IntList> pointMap() {
        return pointMap;
    }
    EnhancedTreeMap.Entry<Integer, IntList> getEntry(int idx) {
        outOfBounds(idx);
        int offset = 0;
        EnhancedTreeMap.Entry<Integer, IntList> foundE = null;
        for (Map.Entry<Integer, IntList> entry : pointMap.entrySet()) {
            int x = entry.getKey(), hereIdx = idx - offset;
            IntList ys = entry.getValue();
            if (hereIdx >= ys.size()) {
                offset += ys.size();
                continue;
            }
            foundE = (EnhancedTreeMap.Entry<Integer, IntList>) entry;
            break;
        }
        if (foundE == null) outOfBounds(idx);
        return foundE;
    }
    int incSize() {
        return size.incrementAndGet();
    }
    int decSize() {
        return size.decrementAndGet();
    }
    int addSize(int delta) {
        return size.addAndGet(delta);
    }

    public Point get(int idx) {
        outOfBounds(idx);
        int offset = 0;
        Point foundPt = null;
        // For loop so that we can break the loop, for performance reasons
        for (Map.Entry<Integer, IntList> entry : pointMap.entrySet()) {
            int x = entry.getKey(), hereIdx = idx - offset;
            IntList ys = entry.getValue();
            if (hereIdx >= ys.size()) {
                offset += ys.size();
                continue;
            }
            foundPt = new Point(x, ys.get(hereIdx));
            break;
        }
        if (foundPt == null) outOfBounds(idx);
        return foundPt;
    }

    public List<Point> getAtX(int x) {
        return new LazyList<>(getYesAtX(x), y -> new Point(x, y));
    }
    public IntList getYesAtX(int x) {
        return pointMap.getOrDefault(x, new IntList());
    }
    public List<IntList> getYesAtXOneFellSwoop(int... xs) {
        return pointMap.getOneFellSwoopOrDefault(new IntList(), CollectionsEx.toBoxedArr(xs));
    }


    // TODO: indexOf does not actually indexOf
    public int indexOf(Point pt) {
        if (!containsX(pt.x)) return -1;
        return Optional.ofNullable(pointMap.getHeldV()).orElse(pointMap.get(pt.x)).indexOf(pt.y);
    }
    public boolean contains(Point pt) {
        return this.indexOf(pt) != -1;
    }
    public boolean containsX(int x) {
        if (leftPoint() != null && rightPoint() != null) {
            return ((x == leftPoint().x || x == rightPoint().x) || (x >= (leftPoint().x) && x <= (rightPoint().x) && pointMap.containsKey(x)));
        } else {
            recomputeTrackedPoints();
            return pointMap.containsKey(x);
        }
    }

    // Helper: recompute tracked points from scratch (call when a removal may have invalidated tracked points).
    private void recomputeTrackedPoints() {
        if (pointMap.isEmpty()) {
            leftPoint.set(null);
            rightPoint.set(null);
            topPoint.set(null);
            bottomPoint.set(null);
            return;
        }

        // leftmost and rightmost X
        Integer leftX = pointMap.firstKey();
        Integer rightX = pointMap.lastKey();

        IntList leftYs = pointMap.get(leftX);
        IntList rightYs = pointMap.get(rightX);

        // Choose representative y for left/right columns.
        // Using IntList.first() (lowest y) as the representative value for left/right columns.
        // If you want highest (last()), adjust here.
        Point leftP = new Point(leftX, leftYs.isEmpty() ? 0 : leftYs.first());
        Point rightP = new Point(rightX, rightYs.isEmpty() ? 0 : rightYs.first());

        // top = global highest y (use IntList.last()); bottom = global lowest y (use IntList.first())
        Point topP = null;
        Point bottomP = null;

        for (Map.Entry<Integer, IntList> e : pointMap.entrySet()) {
            int x = e.getKey();
            IntList ys = e.getValue();
            if (ys.isEmpty()) continue;
            int colTop = ys.first();   // highest in this column
            int colBottom = ys.first(); // lowest in this column

            if (topP == null || colTop > topP.y) topP = new Point(x, colTop);
            if (bottomP == null || colBottom < bottomP.y) bottomP = new Point(x, colBottom);
        }

        leftPoint.set(leftP);
        rightPoint.set(rightP);
        topPoint.set(topP);
        bottomPoint.set(bottomP);
    }

    private void adjustTrackedPointsForNewPoint(Point pt) {
        // Called when adding a single point; update tracked points incrementally.
        // left/right: left is the point with smallest X (choose representative y from that column),
        // top/bottom: update if this point extends the extremes.
        Point lp = leftPoint.get();
        Point rp = rightPoint.get();
        Point tp = topPoint.get();
        Point bp = bottomPoint.get();

        if (lp == null || pt.x < lp.x) {
            // new leftmost column (representative y will be chosen by caller if necessary)
            leftPoint.set(new Point(pt));
        } else if (pt.x == lp.x) {
            // same column as left: keep leftPoint.x the same; choose representative y as smaller of existing representative and pt.y
            // we keep representative semantics similar to earlier choice (we used first() for left/right)
            if (pt.y < lp.y) leftPoint.set(new Point(lp.x, pt.y));
        }

        if (rp == null || pt.x > rp.x) {
            rightPoint.set(new Point(pt));
        } else if (pt.x == rp.x) {
            if (pt.y < rp.y) rightPoint.set(new Point(rp.x, pt.y));
        }

        if (tp == null || pt.y > tp.y) topPoint.set(new Point(pt));
        if (bp == null || pt.y < bp.y) bottomPoint.set(new Point(pt));
    }

    // Convenience to set tracked points using column-level IntList when a whole column is added or replaced
    private void adjustTrackedPointsForColumn(int x, IntList ys) {
        if (ys == null || ys.isEmpty()) {
            recomputeTrackedPoints();
            return;
        }

        // representative y for left/right columns uses first()
        int reprY = ys.first();
        Point colRep = new Point(x, reprY);

        Point lp = leftPoint.get();
        Point rp = rightPoint.get();
        Point tp = topPoint.get();
        Point bp = bottomPoint.get();

        if (lp == null || x < lp.x) leftPoint.set(new Point(colRep));
        else if (x == lp.x) leftPoint.set(new Point(colRep)); // replaced column; update rep

        if (rp == null || x > rp.x) rightPoint.set(new Point(colRep));
        else if (x == rp.x) rightPoint.set(new Point(colRep));

        // column extremes
        int colTop = ys.last();
        int colBottom = ys.first();

        if (tp == null || colTop > tp.y) topPoint.set(new Point(x, colTop));
        if (bp == null || colBottom < bp.y) bottomPoint.set(new Point(x, colBottom));
    }

    private void removeTrackedPointIfMatches(Point removedPt) {
        // If removing a single point could have affected tracked points, recompute fully.
        // Simpler & safer: if the removed point matches any tracked point coordinates, recompute.
        Point lp = leftPoint.get();
        Point rp = rightPoint.get();
        Point tp = topPoint.get();
        Point bp = bottomPoint.get();

        if ((lp != null && lp.x == removedPt.x && lp.y == removedPt.y) ||
                (rp != null && rp.x == removedPt.x && rp.y == removedPt.y) ||
                (tp != null && tp.x == removedPt.x && tp.y == removedPt.y) ||
                (bp != null && bp.x == removedPt.x && bp.y == removedPt.y)) {
            recomputeTrackedPoints();
        }
    }

    private void removeTrackedColumnIfMatches(int x) {
        // If a whole column was removed and it matched left/right columns, recompute
        Point lp = leftPoint.get();
        Point rp = rightPoint.get();
        if ((lp != null && lp.x == x) || (rp != null && rp.x == x)) {
            recomputeTrackedPoints();
        } else {
            // top/bottom could also be affected if their x is the removed column:
            Point tp = topPoint.get();
            Point bp = bottomPoint.get();
            if ((tp != null && tp.x == x) || (bp != null && bp.x == x)) recomputeTrackedPoints();
        }
    }

    private void adjustYBounds(int y) {
        // legacy method: now delegate to adjustTrackedPointsForNewPoint with a dummy X=0 if needed
        adjustTrackedPointsForNewPoint(new Point(0, y));
    }
    private void adjustYBounds(int y1, int y2) {
        adjustTrackedPointsForNewPoint(new Point(0, y1));
        adjustTrackedPointsForNewPoint(new Point(0, y2));
    }
    private void removeYBounds() {
        // reset tracked points (conservative)
        recomputeTrackedPoints();
    }

    public boolean add(Point pt) {
        IntList ys = pointMap.get(pt.x);
        if (ys == null) {
            IntList iL = new IntList(pt.y);
            pointMap.put(pt.x, iL);
            size.incrementAndGet();
            // update tracked points using full column
            adjustTrackedPointsForColumn(pt.x, iL);
            return true;
        }
        else if (!ys.add(pt.y)) return false;
        size.incrementAndGet();
        // after adding a y to an existing column we can incrementally update tracked points
        adjustTrackedPointsForNewPoint(pt);
        return true;
    }

    public boolean remove(Point pt) {
        if (!pointMap.containsKey(pt.x)) return false;
        IntList ys = pointMap.getHeldV();
        if (ys.removeElem(pt.y)) {
            if (ys.isEmpty()) pointMap.remove(pt.x);
            size.decrementAndGet();
            // If removal could affect tracked points, recompute
            removeTrackedPointIfMatches(pt);
            return true;
        }
        return false;
    }


    public boolean addAtX(int x, int... yes) {
        if (yes == null || yes.length == 0) return true;
        if (pointMap.containsKey(x)) {
            boolean res = pointMap.getHeldV().addAll(yes);
            if (res) size.addAndGet(yes.length);
            // column changed; update tracked points for that column
            adjustTrackedPointsForColumn(x, pointMap.getHeldV());
            return res;
        } else {
            IntList iL = new IntList(yes);
            pointMap.put(x, iL);
            size.addAndGet(yes.length);
            adjustTrackedPointsForColumn(x, iL);
            return true;
        }
    }
    public boolean addAtX(int x, IntList yes) {
        if (yes == null || yes.isEmpty()) return true;
        if (pointMap.containsKey(x)) {
            boolean res = pointMap.getHeldV().addAll(yes);
            if (res) size.addAndGet(yes.size());
            adjustTrackedPointsForColumn(x, pointMap.getHeldV());
            return res;
        } else {
            IntList iL = new IntList(yes);
            pointMap.put(x, iL);
            size.addAndGet(yes.size());
            adjustTrackedPointsForColumn(x, iL);
            return true;
        }
    }
    public boolean addAtX(int x, Collection<? extends Integer> yes) {
        if (pointMap.containsKey(x)) {
            boolean res = pointMap.getHeldV().addAll(yes);
            if (res) size.addAndGet(yes.size());
            adjustTrackedPointsForColumn(x, pointMap.getHeldV());
            return res;
        } else {
            IntList iL = new IntList(yes);
            pointMap.put(x, iL);
            size.addAndGet(yes.size());
            adjustTrackedPointsForColumn(x, iL);
            return true;
        }
    }

    public boolean remove(int idx) {
        outOfBounds(idx);
        int offset = 0;
        // For loop so that we can break the loop, for performance reasons
        for (Map.Entry<Integer, IntList> entry : pointMap.entrySet()) {
            int hereIdx = idx - MathEx.posBound(offset);
            IntList ys = entry.getValue();
            if (hereIdx >= ys.size()) {
                offset += ys.size();
                continue;
            }
            boolean res = Objects.nonNull(ys.remove(hereIdx));
            if (res) {
                size.decrementAndGet();
                if (ys.isEmpty()) pointMap.remove(entry.getKey());
                removeYBounds();
            }
            return res;
        }
        return false;
    }

    public IntList removeAtX(int x) {
        if (!pointMap.containsKey(x)) return null;
        IntList removed = pointMap.getHeldV();
        size.addAndGet(-removed.size());
        pointMap.remove(x);
        removeTrackedColumnIfMatches(x);
        return removed;
    }
    void removeEntry(EnhancedTreeMap.Entry<Integer, IntList> entry) {
        pointMap.deleteEntry(entry);
    }


    public void translate(int transX, int transY) {
        this.pointMap = this.pointMap.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey() + transX,
                e -> StreamEx.toIntList(e.getValue().stream().map(y -> y + transY)),
                (a, b) -> a,
                EnhancedTreeMap::new
        ));
        // shift tracked points if present
        Point lp = leftPoint.get(); if (lp != null) leftPoint.set(new Point(lp.x + transX, lp.y + transY));
        Point rp = rightPoint.get(); if (rp != null) rightPoint.set(new Point(rp.x + transX, rp.y + transY));
        Point tp = topPoint.get(); if (tp != null) topPoint.set(new Point(tp.x + transX, tp.y + transY));
        Point bp = bottomPoint.get(); if (bp != null) bottomPoint.set(new Point(bp.x + transX, bp.y + transY));
    }

    public PointCollection rotate(double degrees) {
        double norm = MathEx.normalizeAngle(degrees);
        if (norm == 0) return new PointCollection(this);

        PointCollection out = new PointCollection();

        BiConsumer<Integer, Integer> rot;
        if (norm == 90) {
            rot = (x, y) -> out.addAtX(-y, x);
        } else if (norm == 180) {
            rot = (x, y) -> out.addAtX(-x, -y);
        } else if (norm == 270) {
            rot = (x, y) -> out.addAtX(y, -x);
        } else {
            rot = (x, y) -> varExec(MathEx.rotate(new Point(x, y), degrees), r -> out.addAtX(r.x, r.y));
        }
        pointMap.forEach((x, yes) -> yes.forEach(y -> rot.accept(x, y)));
        return out;
    }

    public PointCollection expand(int pixels) {
        if (pixels <= 0 || isEmpty()) return new PointCollection(this);

        final int radius = pixels;
        final int[] yReachByDx = buildDiskYReach(radius);

        HashMap<Integer, ArrayList<int[]>> spansByX =
                new HashMap<>(Math.max(16, pointMap.size() + (radius << 1) + 1));

        pointMap.forEach((x, ys) -> {
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
            ArrayList<int[]> spans = entry.getValue();
            if (spans == null || spans.isEmpty()) continue;

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

            out.addAtX(entry.getKey(), mergedYs);
        }

        return out;
    }

    public PointCollection contract(int pixels) {
        if (pixels <= 0 || isEmpty()) return new PointCollection(this);

        final int radius = pixels;
        final int[] yReachByDx = buildDiskYReach(radius);

        HashMap<Integer, ArrayList<int[]>[]> shrinkCacheByX =
                new HashMap<>(Math.max(16, pointMap.size()));

        for (Integer x : pointMap.keySet()) {
            @SuppressWarnings("unchecked")
            ArrayList<int[]>[] cache = (ArrayList<int[]>[]) new ArrayList[radius + 1];
            shrinkCacheByX.put(x, cache);
        }

        PointCollection out = new PointCollection();

        final int minCandidateX = firstX() + radius;
        final int maxCandidateX = lastX() - radius;

        for (Integer xObj : pointMap.keySet()) {
            int x = xObj;
            if (x < minCandidateX || x > maxCandidateX) continue;

            List<int[]> intersection = null;
            boolean empty = false;

            for (int dx = -radius; dx <= radius; dx++) {
                int srcX = x + dx;
                IntList ys = pointMap.get(srcX);

                if (ys == null || ys.isEmpty()) {
                    empty = true;
                    break;
                }

                int reach = yReachByDx[dx + radius];
                ArrayList<int[]> allowed =
                        getShrunkIntervals(ys, reach, shrinkCacheByX.get(srcX));

                if (allowed.isEmpty()) {
                    empty = true;
                    break;
                }

                if (intersection == null) {
                    intersection = allowed;
                } else {
                    intersection = intersectIntervals(intersection, allowed);
                    if (intersection.isEmpty()) {
                        empty = true;
                        break;
                    }
                }
            }

            if (empty || intersection == null || intersection.isEmpty()) continue;

            IntList ysOut = new IntList();
            for (int i = 0; i < intersection.size(); i++) {
                int[] span = intersection.get(i);
                for (int y = span[0]; y <= span[1]; y++) {
                    ysOut.add(y);
                }
            }

            if (!ysOut.isEmpty()) {
                out.addAtX(x, ysOut);
            }
        }

        return out;
    }

    private static ArrayList<int[]> getShrunkIntervals(
            IntList ys,
            int reach,
            ArrayList<int[]>[] cache
    ) {
        ArrayList<int[]> cached = cache[reach];
        if (cached != null) return cached;

        ArrayList<int[]> out = new ArrayList<>();
        if (ys == null || ys.isEmpty()) {
            cache[reach] = out;
            return out;
        }

        ys.onRaw();
        ys.forEachRaw(bounds -> {
            int lo = bounds.getLowerBound() + reach;
            int hi = bounds.getUpperBound() - reach;
            if (lo <= hi) {
                out.add(new int[] { lo, hi });
            }
        });

        cache[reach] = out;
        return out;
    }

    private static ArrayList<int[]> intersectIntervals(List<int[]> a, List<int[]> b) {
        ArrayList<int[]> out = new ArrayList<>(Math.min(a.size(), b.size()));

        int i = 0;
        int j = 0;

        while (i < a.size() && j < b.size()) {
            int[] sa = a.get(i);
            int[] sb = b.get(j);

            int lo = Math.max(sa[0], sb[0]);
            int hi = Math.min(sa[1], sb[1]);

            if (lo <= hi) {
                out.add(new int[] { lo, hi });
            }

            if (sa[1] < sb[1]) {
                i++;
            } else {
                j++;
            }
        }

        return out;
    }

    private static int[] buildDiskYReach(int radius) {
        int[] out = new int[(radius * 2) + 1];
        long r2 = (long) radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            long dx2 = (long) dx * dx;
            out[dx + radius] = MathEx.floorInt(Math.sqrt(r2 - dx2));
        }

        return out;
    }


/*
    public void fillHoles() {
        if (isEmpty()) return;

        int minX = firstX();
        int maxX = lastX();
        int minY = bottomY();
        int maxY = topY();

        int margin = 1;
        int startX = minX - margin;
        int endX = maxX + margin;
        int startY = minY - margin;
        int endY = maxY + margin;

        int width = endX - startX + 1;
        int height = endY - startY + 1;

        long cellCountLong = (long) width * (long) height;
        if (cellCountLong <= 0L || cellCountLong > Integer.MAX_VALUE) return;

        int cellCount = (int) cellCountLong;
        boolean[] solid = new boolean[cellCount];
        boolean[] outside = new boolean[cellCount];

        for (Point p : this) {
            int ix = p.x - startX;
            int iy = p.y - startY;
            if (ix < 0 || iy < 0 || ix >= width || iy >= height) continue;
            int idx = ix * height + iy;
            solid[idx] = true;
        }

        Deque<Long> q = new ArrayDeque<Long>();

        for (int x = startX; x <= endX; x++) {
            int ix = x - startX;

            int iy0 = startY - startY;
            int idx0 = ix * height + iy0;
            if (!solid[idx0] && !outside[idx0]) {
                outside[idx0] = true;
                q.addLast(Long.valueOf(MathEx.encode(x, startY)));
            }

            int iy1 = endY - startY;
            int idx1 = ix * height + iy1;
            if (!solid[idx1] && !outside[idx1]) {
                outside[idx1] = true;
                q.addLast(Long.valueOf(MathEx.encode(x, endY)));
            }
        }

        for (int y = startY + 1; y <= endY - 1; y++) {
            int iy = y - startY;

            int ix0 = 0;
            int idx0 = ix0 * height + iy;
            if (!solid[idx0] && !outside[idx0]) {
                outside[idx0] = true;
                q.addLast(Long.valueOf(MathEx.encode(startX, y)));
            }

            int ix1 = endX - startX;
            int idx1 = ix1 * height + iy;
            if (!solid[idx1] && !outside[idx1]) {
                outside[idx1] = true;
                q.addLast(Long.valueOf(MathEx.encode(endX, y)));
            }
        }

        int[][] neighbors = {
                {1, 0}, {-1, 0},
                {0, 1}, {0, -1},
                {1, 1}, {1, -1},
                {-1, 1}, {-1, -1}
        };

        while (!q.isEmpty()) {
            long code = q.removeFirst();
            int cx = (int) (code >>> 32);
            int cy = (int) code;

            for (int i = 0; i < neighbors.length; i++) {
                int nx = cx + neighbors[i][0];
                int ny = cy + neighbors[i][1];

                if (nx < startX || nx > endX || ny < startY || ny > endY) continue;

                int ix = nx - startX;
                int iy = ny - startY;
                int idx = ix * height + iy;

                if (solid[idx] || outside[idx]) continue;

                outside[idx] = true;
                q.addLast(MathEx.encode(nx, ny));
            }
        }

        for (int x = minX; x <= maxX; x++) {
            int ix = x - startX;

            int count = 0;
            for (int y = minY; y <= maxY; y++) {
                int iy = y - startY;
                int idx = ix * height + iy;
                if (!solid[idx] && !outside[idx]) {
                    count++;
                }
            }

            if (count == 0) continue;

            int[] ys = new int[count];
            int k = 0;
            for (int y = minY; y <= maxY; y++) {
                int iy = y - startY;
                int idx = ix * height + iy;
                if (!solid[idx] && !outside[idx]) {
                    ys[k++] = y;
                    solid[idx] = true;
                }
            }

            addAtX(x, ys);
        }
    }*/
public void fillHoles() {
    if (isEmpty()) return;

    int minX = firstX();
    int maxX = lastX();
    int minY = bottomY();
    int maxY = topY();

    int margin = 1;
    int startX = minX - margin;
    int endX = maxX + margin;
    int startY = minY - margin;
    int endY = maxY + margin;

    int width = endX - startX + 1;
    int height = endY - startY + 1;

    long cellCountLong = (long) width * (long) height;
    if (cellCountLong <= 0L || cellCountLong > Integer.MAX_VALUE) return;
    int cellCount = (int) cellCountLong;

    boolean[] solid = new boolean[cellCount];
    boolean[] outside = new boolean[cellCount];

    for (Point p : this) {
        int ix = p.x - startX;
        int iy = p.y - startY;
        if (ix < 0 || ix >= width || iy < 0 || iy >= height) continue;
        solid[ix * height + iy] = true;
    }

    int[] qx = new int[cellCount];
    int[] qy = new int[cellCount];
    int head = 0;
    int tail = 0;

    int maxIx = width - 1;
    int maxIy = height - 1;

    for (int ix = 0; ix < width; ix++) {
        int base = ix * height;

        int idx0 = base;
        if (!solid[idx0] && !outside[idx0]) {
            outside[idx0] = true;
            qx[tail] = ix;
            qy[tail] = 0;
            tail++;
        }

        int idx1 = base + maxIy;
        if (!solid[idx1] && !outside[idx1]) {
            outside[idx1] = true;
            qx[tail] = ix;
            qy[tail] = maxIy;
            tail++;
        }
    }

    for (int iy = 1; iy < maxIy; iy++) {
        int idx0 = iy;
        if (!solid[idx0] && !outside[idx0]) {
            outside[idx0] = true;
            qx[tail] = 0;
            qy[tail] = iy;
            tail++;
        }

        int idx1 = maxIx * height + iy;
        if (!solid[idx1] && !outside[idx1]) {
            outside[idx1] = true;
            qx[tail] = maxIx;
            qy[tail] = iy;
            tail++;
        }
    }

    while (head < tail) {
        int cx = qx[head];
        int cy = qy[head];
        head++;

        int base = cx * height;

        if (cx > 0) {
            int nx = cx - 1;
            int leftBase = base - height;

            int idx = leftBase + cy;
            if (!solid[idx] && !outside[idx]) {
                outside[idx] = true;
                qx[tail] = nx;
                qy[tail] = cy;
                tail++;
            }

            if (cy > 0) {
                int ny = cy - 1;
                idx = leftBase + ny;
                if (!solid[idx] && !outside[idx]) {
                    outside[idx] = true;
                    qx[tail] = nx;
                    qy[tail] = ny;
                    tail++;
                }
            }

            if (cy < maxIy) {
                int ny = cy + 1;
                idx = leftBase + ny;
                if (!solid[idx] && !outside[idx]) {
                    outside[idx] = true;
                    qx[tail] = nx;
                    qy[tail] = ny;
                    tail++;
                }
            }
        }

        if (cx < maxIx) {
            int nx = cx + 1;
            int rightBase = base + height;

            int idx = rightBase + cy;
            if (!solid[idx] && !outside[idx]) {
                outside[idx] = true;
                qx[tail] = nx;
                qy[tail] = cy;
                tail++;
            }

            if (cy > 0) {
                int ny = cy - 1;
                idx = rightBase + ny;
                if (!solid[idx] && !outside[idx]) {
                    outside[idx] = true;
                    qx[tail] = nx;
                    qy[tail] = ny;
                    tail++;
                }
            }

            if (cy < maxIy) {
                int ny = cy + 1;
                idx = rightBase + ny;
                if (!solid[idx] && !outside[idx]) {
                    outside[idx] = true;
                    qx[tail] = nx;
                    qy[tail] = ny;
                    tail++;
                }
            }
        }

        if (cy > 0) {
            int ny = cy - 1;
            int idx = base + ny;
            if (!solid[idx] && !outside[idx]) {
                outside[idx] = true;
                qx[tail] = cx;
                qy[tail] = ny;
                tail++;
            }
        }

        if (cy < maxIy) {
            int ny = cy + 1;
            int idx = base + ny;
            if (!solid[idx] && !outside[idx]) {
                outside[idx] = true;
                qx[tail] = cx;
                qy[tail] = ny;
                tail++;
            }
        }
    }

    int iyMin = minY - startY;
    int iyMax = maxY - startY;

    for (int x = minX, ix = minX - startX; x <= maxX; x++, ix++) {
        int base = ix * height;
        int count = 0;

        for (int iy = iyMin; iy <= iyMax; iy++) {
            int idx = base + iy;
            if (!solid[idx] && !outside[idx]) count++;
        }

        if (count == 0) continue;

        int[] ys = new int[count];
        int k = 0;

        for (int iy = iyMin; iy <= iyMax; iy++) {
            int idx = base + iy;
            if (!solid[idx] && !outside[idx]) {
                ys[k++] = startY + iy;
                solid[idx] = true;
            }
        }

        addAtX(x, ys);
    }
}






    public boolean includes(PointCollection pts, double tolerance) {

        int required = MathEx.ceilInt(pts.size() * tolerance),
                matched = 0, processed = 0;

        for (Map.Entry<Integer, IntList> entry : pts.entrySet()) {
            int x = entry.getKey();
            IntList mine = this.getYesAtX(x);
            for (Integer y : entry.getValue()) {
                processed++;
                if (mine.contains(y)) matched++;
                if (matched >= required) return true;
                if (matched + (pts.size() - processed) < required) return false;
            }
        }

        return matched >= required;
    }
    public boolean includes(PointCollection pts) {
        return includes(pts, 1);
    }

    public boolean xIncludes(PointCollection pts, double tolerance) {
        Set<Integer> otherXes = pts.xes();

        int required = MathEx.ceilInt(otherXes.size() * tolerance),
                matched = 0, processed = 0;

        for (int x : pts.pointMap.keySet()) {
            if (containsX(x)) matched++;
            processed++;
            if (matched >= required) return true;
            if (matched + (otherXes.size() - processed) < required) return false;
        }

        return matched >= required;
    }
    public boolean xIncludes(PointCollection pts) {
        return xIncludes(pts, 1.0);
    }


    public double xIncluded(PointCollection pts) {
        Set<Integer> otherXes = pts.xes();
        return otherXes.isEmpty() ? 1 : MathEx.divide(otherXes.stream().filter(this::containsX).count(), otherXes.size());
    }

    public double included(PointCollection pts) {
        if (pts.size() == 0) return 1;
        int matched = 0;
        for (Map.Entry<Integer, IntList> entry : pts.entrySet()) {
            int x = entry.getKey();
            IntList mine = getYesAtX(x);
            matched += (int) entry.getValue().stream().filter(mine::contains).count();
        }
        return MathEx.divide(matched, pts.size());
    }


    public double distance(PointCollection pts) {
        if (this.isEmpty() || pts.isEmpty()) return 0;
        List<Integer> otherXs = new ArrayList<>(pts.xes());
        double bestSq = Double.POSITIVE_INFINITY;

        // iterate x columns from this collection
        for (Integer xAObj : this.xes()) {
            int xA = xAObj;
            IntList ysA = this.getYesAtX(xA); // use public accessor

            int pos = Collections.binarySearch(otherXs, xA);
            int idx = pos >= 0 ? pos : -pos - 1;

            int left = idx - 1;
            int right = idx;

            // expand outward from the closest x positions in otherXs
            while (left >= 0 || right < otherXs.size()) {
                int xB;
                if (left >= 0 && right < otherXs.size()) {
                    int dxL = Math.abs(xA - otherXs.get(left));
                    int dxR = Math.abs(otherXs.get(right) - xA);
                    if (dxL <= dxR) {
                        xB = otherXs.get(left--);
                    } else {
                        xB = otherXs.get(right++);
                    }
                } else if (left >= 0) {
                    xB = otherXs.get(left--);
                } else {
                    xB = otherXs.get(right++);
                }

                long dx = (long) xB - xA;
                double dxSq = (double) dx * dx;

                // prune by x-distance: further columns will only increase |dx|
                if (dxSq > bestSq) break;

                IntList ysB = pts.getYesAtX(xB); // use public accessor
                double dy = CollectionsEx.minDist(ysA, ysB);
                double distSq = dxSq + dy * dy;
                if (distSq < bestSq) {
                    bestSq = distSq;
                    if (bestSq == 0.0) return 0.0;
                }
            }
        }

        return Math.sqrt(bestSq);
    }

    public double distance(ShapeBounds sBounds) {
        if (sBounds.getBounds() == null) sBounds.indexBounds();
        if (isEmpty() || sBounds.get().isEmpty()) return Double.POSITIVE_INFINITY;

        TreeMap<Integer, IntegerBounds> bounds = sBounds.getBounds();
        List<Integer> boundsXs = new ArrayList<>(bounds.keySet());

        double bestSq = Double.POSITIVE_INFINITY;

        for (Integer xAObj : this.xes()) {
            int xA = xAObj;
            IntList ysA = this.getYesAtX(xA);

            int pos = Collections.binarySearch(boundsXs, xA);
            int idx = pos >= 0 ? pos : -pos - 1;

            int left = idx - 1, right = idx;

            while (left >= 0 || right < boundsXs.size()) {
                int xB;
                if (left >= 0 && right < boundsXs.size()) {
                    int dxL = Math.abs(xA - boundsXs.get(left));
                    int dxR = Math.abs(boundsXs.get(right) - xA);
                    if (dxL <= dxR) {
                        xB = boundsXs.get(left--);
                    } else {
                        xB = boundsXs.get(right++);
                    }
                } else if (left >= 0) {
                    xB = boundsXs.get(left--);
                } else {
                    xB = boundsXs.get(right++);
                }

                long dx = (long) xB - xA;
                double dxSq = MathEx.square((double) Math.max(0, Math.abs(dx) - 1));

                if (dxSq > bestSq) break;

                double distSq = dxSq + MathEx.square(Math.max(0, CollectionsEx.minDist(ysA, IntStream.rangeClosed(bounds.get(xB).getLowerBound(), bounds.get(xB).getUpperBound()).boxed().toList()) - 1));
                if (distSq < bestSq) {
                    bestSq = distSq;
                    if (bestSq == 0) return 0;
                }
            }
        }

        return Math.sqrt(bestSq);
    }

    public Point closest(Point pt) {
        if (isEmpty()) return null;

        IntList here = getYesAtX(pt.x);
        double bestSq = Double.POSITIVE_INFINITY;
        Point best = null;

        if (here != null && !here.isEmpty()) {
            int yCand = closestYValue(here, pt.y);
            long dx = 0L, dy = (long) yCand - pt.y;
            bestSq = (double) dy * dy;
            best = new Point(pt.x, yCand);
            if (bestSq == 0.0) return best;
        }

        Integer lx = lowerX(pt.x), rx = higherX(pt.x);
        while (lx != null || rx != null) {
            int dxL = lx != null ? pt.x - lx : Integer.MAX_VALUE;
            int dxR = rx != null ? rx - pt.x : Integer.MAX_VALUE;
            boolean takeLeft = dxL <= dxR;
            int x = takeLeft ? lx : rx;

            long dx = (long) x - pt.x;
            double dxSq = (double) dx * dx;
            if (dxSq >= bestSq) break;

            IntList ys = getYesAtX(x);
            if (ys != null && !ys.isEmpty()) {
                int yCand = closestYValue(ys, pt.y);
                long dy = (long) yCand - pt.y;
                double d2 = dxSq + (double) dy * dy;
                if (d2 < bestSq) {
                    bestSq = d2;
                    best = new Point(x, yCand);
                    if (bestSq == 0.0) return best;
                }
            }

            if (takeLeft) lx = lowerX(lx); else rx = higherX(rx);
        }

        return best;
    }

    public Point closestY(Point pt) {
        IntList ys = getYesAtX(pt.x);
        if (ys == null || ys.isEmpty()) return null;
        int yCand = closestYValue(ys, pt.y);
        return new Point(pt.x, yCand);
    }

    public Point closestX(Point pt) {
        if (isEmpty()) return null;

        IntList here = getYesAtX(pt.x);
        if (here != null && !here.isEmpty() && columnContainsY(here, pt.y)) return new Point(pt.x, pt.y);

        Integer lx = lowerX(pt.x), rx = higherX(pt.x);
        while (lx != null || rx != null) {
            int dxL = lx != null ? pt.x - lx : Integer.MAX_VALUE;
            int dxR = rx != null ? rx - pt.x : Integer.MAX_VALUE;
            boolean takeLeft = dxL <= dxR;
            int x = takeLeft ? lx : rx;

            IntList ys = getYesAtX(x);
            if (ys != null && !ys.isEmpty() && columnContainsY(ys, pt.y)) return new Point(x, pt.y);

            if (takeLeft) lx = lowerX(lx); else rx = higherX(rx);
        }
        return null;
    }

    public Point probeX(Point p, int direction) {
        int x = p.x;
        int y = p.y;
        int nx = x + direction;

        while (this.contains(new Point(nx, y))) {
            x = nx;
            nx += direction;
        }
        return new Point(x, y);
    }

    public Point probeY(Point p, int direction) {
        int x = p.x;
        int y = p.y;
        int ny = y + direction;

        while (this.contains(new Point(x, ny))) {
            y = ny;
            ny += direction;
        }
        return new Point(x, y);
    }

    public Point probeDegree(Point p, int dirX, int dirY) {
        int x = p.x;
        int y = p.y;
        int nx = x + dirX;
        int ny = y + dirY;

        while (this.contains(new Point(nx, ny))) {
            x = nx;
            y = ny;
            nx += dirX;
            ny += dirY;
        }
        return new Point(x, y);
    }

    public Point probeDegree(Point p, double degrees) {
        double rad = Math.toRadians(degrees);
        int dirX = (int) Math.signum(Math.cos(rad));
        int dirY = (int) Math.signum(Math.sin(rad));
        int x = p.x;
        int y = p.y;
        int nx = x + dirX;
        int ny = y + dirY;

        while (this.contains(new Point(nx, ny))) {
            x = nx;
            y = ny;
            nx += dirX;
            ny += dirY;
        }
        return new Point(x, y);
    }




    public double distance(Point pt) {
        Point q = closest(pt);
        if (q == null) return Double.POSITIVE_INFINITY;
        long dx = (long) q.x - pt.x, dy = (long) q.y - pt.y;
        return Math.sqrt((double) dx * dx + (double) dy * dy);
    }

    private static boolean columnContainsY(IntList ys, int y) {
        int[] idxs = ys.closest(y); // uses CollectionsEx.closestBinarySearch under the hood
        return idxs != null && idxs.length == 1;
    }

    private static int closestYValue(IntList ys, int y) {
        int[] idxs = ys.closest(y);
        if (idxs == null || idxs.length == 0) return y;

        final int[] want0 = new int[] { -1, -1 };
        final int[] want1 = new int[] { -1, -1 };
        final int i0 = idxs[0];
        final int i1 = idxs.length >= 2 ? idxs[1] : -1;

        ys.onRaw();
        final int[] k = new int[] { 0 };
        ys.forEachRaw(b -> {
            int i = k[0]++;
            if (i == i0) { want0[0] = b.getLowerBound(); want0[1] = b.getUpperBound(); }
            if (i == i1) { want1[0] = b.getLowerBound(); want1[1] = b.getUpperBound(); }
        });

        if (idxs.length == 1) {
            int lo = want0[0], hi = want0[1];
            if (y < lo) return lo;
            if (y > hi) return hi;
            return y;
        } else {
            int lo0 = want0[0], hi0 = want0[1];
            int lo1 = want1[0], hi1 = want1[1];

            int y0 = y < lo0 ? lo0 : y > hi0 ? hi0 : y;
            int y1 = y < lo1 ? lo1 : y > hi1 ? hi1 : y;

            int d0 = Math.abs(y0 - y), d1 = Math.abs(y1 - y);
            return d0 <= d1 ? y0 : y1;
        }
    }


    public PointCollection subtract(PointCollection c) {
        PointCollection result = new PointCollection();
        forEachRaw((x, ys) -> {
            if (!c.containsX(x)) {
                result.addAtX(x, ys);
            } else {
                IntList cYs = Optional.ofNullable(c.pointMap.getHeldV()).orElse(c.getYesAtX(x));
                if (cYs != null) {
                    ys.removeAll(cYs);
                    if (!ys.isEmpty()) result.addAtX(x, ys);
                }
            }
        });
//        forEach(pt -> {
//            if (!c.contains(pt)) result.add(pt);
//        });
        return result;
    }

    /**
     * Returns all points within this collection's bounding rectangle that are NOT present in the collection.
     * This is O(area) but avoids allocations used by fillHoles() and avoids HashSet/boxing BFS queues.
     */
    public PointCollection inverse() {
        PointCollection out = new PointCollection();
        if (isEmpty()) return out;

        int minX = firstX(), maxX = lastX();
        int minY = bottomY(), maxY = topY();

        // Iterate columns; membership test uses IntList.closest() binary-search path
        // via columnContainsY(...) (no per-point HashMap/Long boxing).
        for (int x = minX; x <= maxX; x++) {
            IntList ys = getYesAtX(x);
            if (ys == null || ys.isEmpty()) {
                for (int y = minY; y <= maxY; y++) out.addAtX(x, y);
                continue;
            }
            for (int y = minY; y <= maxY; y++) {
                if (!columnContainsY(ys, y)) out.addAtX(x, y);
            }
        }
        return out;
    }

    /**
     * Fast "holes" (interior voids) without flood-fill:
     * For each x-column, fill any gaps between the min and max y that are missing.
     * This approximates "fillHoles().subtract(outline)" for typical solid shapes, but
     * does not require BFS over the entire bounding box like fillHoles() [11].
     */
    public PointCollection hole() {
        PointCollection out = new PointCollection();
        if (isEmpty()) return out;

        int minX = firstX(), maxX = lastX();

        for (int x = minX; x <= maxX; x++) {
            IntList ys = getYesAtX(x);
            if (ys == null || ys.isEmpty()) continue;

            int colMin = ys.first();
            int colMax = ys.last();
            if (colMin >= colMax) continue;

            // Walk y and add missing interior points (gaps) in this column
            for (int y = colMin; y <= colMax; y++) {
                if (!columnContainsY(ys, y)) out.addAtX(x, y);
            }
        }
        return out;
    }




    public PointCollection between(PointCollection inner) {
        PointCollection res = new PointCollection();

        for (Map.Entry<Integer, IntList> e : this.entrySet()) {
            int x = e.getKey();
            IntList outerYs = e.getValue();
            if (outerYs == null || outerYs.isEmpty()) continue;

            int outMin = Integer.MAX_VALUE, outMax = Integer.MIN_VALUE;
            for (int i = 0, n = outerYs.size(); i < n; i++) {
                int y = outerYs.get(i);
                if (y < outMin) outMin = y;
                if (y > outMax) outMax = y;
            }
            if (outMin > outMax) continue;

            IntList innerYs = inner.getYesAtX(x);
            if (innerYs == null || innerYs.isEmpty()) {
                for (int y = outMin; y <= outMax; y++) res.add(new Point(x, y));
                continue;
            }

            int inMin = Integer.MAX_VALUE, inMax = Integer.MIN_VALUE;
            for (int j = 0, m = innerYs.size(); j < m; j++) {
                int iy = innerYs.get(j);
                if (iy < inMin) inMin = iy;
                if (iy > inMax) inMax = iy;
            }
            if (inMin > inMax) {
                for (int y = outMin; y <= outMax; y++) res.add(new Point(x, y));
                continue;
            }

            if (inMin < outMin) inMin = outMin;
            if (inMax > outMax) inMax = outMax;

            int a1 = outMin, b1 = inMin - 1;
            int a2 = inMax + 1, b2 = outMax;

            if (a1 <= b1) for (int y = a1; y <= b1; y++) res.add(new Point(x, y));
            if (a2 <= b2) for (int y = a2; y <= b2; y++) res.add(new Point(x, y));
        }

        return res;
    }






    public boolean containsDist(Point pt, double dist) {
        if (this.isEmpty()) return false;
        if (Double.isInfinite(dist)) return true;

        double distSq = dist * dist;
        int dxBound = (int) Math.floor(dist);

        int xMin = pt.x - dxBound;
        int xMax = pt.x + dxBound;

        // subMap limits columns we must check
        SortedMap<Integer, IntList> cols = pointMap.subMap(xMin, true, xMax, true);
        for (Map.Entry<Integer, IntList> e : cols.entrySet()) {
            int x = e.getKey();
            long dx = (long) x - pt.x;
            double dxSq = MathEx.square(dx);
            if (dxSq > distSq) continue; // prune full column

            double maxDySq = distSq - dxSq;
            double maxDy = Math.sqrt(maxDySq);

            int yMin = MathEx.ceilInt(pt.y - maxDy),
                    yMax = MathEx.floorInt(pt.y + maxDy);

            IntList ys = e.getValue();
            if (ys.isEmpty()) continue;

            // binary search for first index with value >= yMin
            int lo = 0, hi = ys.size() - 1, idx = ys.size();
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int val = ys.get(mid);
                if (val >= yMin) {
                    idx = mid;
                    hi = mid - 1;
                } else {
                    lo = mid + 1;
                }
            }

            if (idx < ys.size()) {
                int found = ys.get(idx);
                if (found <= yMax) return true;
            }
            // optionally check previous element in case insertion point was > yMax but previous inside
            if (idx - 1 >= 0) {
                int prev = ys.get(idx - 1);
                if (prev >= yMin && prev <= yMax) return true;
            }
        }

        return false;
    }





    public void forEach(Consumer<? super Point> fn) {
        pointMap.forEach((x, ys) ->
                ys.forEach(y ->
                        fn.accept(new Point(x, y))
                )
        );
    }
    public void forEach(BiConsumer<Integer, Integer> fn) {
        pointMap.forEach((x, ys) ->
                ys.forEach(y ->
                        fn.accept(x, y)
                )
        );
    }
    public void forEachInc(BiConsumer<Point, Integer> fn) {
        AtomicInteger index = new AtomicInteger(0);
        forEach(pt -> fn.accept(pt, index.getAndIncrement()));
    }
    public void forEachInc(TriConsumer<Integer, Integer, Integer> fn) {
        AtomicInteger index = new AtomicInteger(0);
        forEach((x, y) -> fn.accept(x, y, index.getAndIncrement()));
    }
    public void forEachRaw(BiConsumer<Integer, IntList> fn) {
//        if (!raw) throw new IllegalCallerException("Cannot access pointMap when raw access is not enabled");
        rawCheck();
        pointMap.forEach(fn);
    }

    public Set<Integer> xes() {
        return new TreeSet<>(pointMap.keySet());
    }
    public IntList yes() {
        return pointMap.values().stream().flatMap(IntList::stream).collect(IntList.toIntList());
    }


    public boolean isEmpty() {
        return pointMap.isEmpty();
    }

    public int size() {
        return this.size.get();
    }


    // Public getters preserved but implemented via tracked Points

    public int firstX() {
        if (leftPoint.get() == null) throw new NoSuchElementException("PointCollection is empty");
        return leftPoint.get().x;
    }
    public int lastX() {
        if (rightPoint.get() == null) throw new NoSuchElementException("PointCollection is empty");
        return rightPoint.get().x;
    }
    public Integer lowerX(int x) {
        return pointMap.lowerKey(x);
    }
    public Integer higherX(int x) {
        return pointMap.higherKey(x);
    }
    public int width() {
        return Math.abs(lastX() - firstX());
    }

    public int topY() {
        if (topPoint.get() != null) return topPoint.get().y;
        recomputeTrackedPoints();
        return topPoint.get() != null ? topPoint.get().y : 0;
    }
    public int bottomY() {
        if (bottomPoint.get() != null) return bottomPoint.get().y;
        recomputeTrackedPoints();
        return bottomPoint.get() != null ? bottomPoint.get().y : 0;
    }
    public int height() {
        return Math.abs(bottomY() - topY());
    }

    public Rectangle rect() {
        return new Rectangle(firstX(), topY(), width(), height());
    }
    public Rectangle imgRect() {
        return new Rectangle(firstX(), bottomY(), width(), height());
    }

    // Expose tracked point accessors if callers want points:
    public Point leftPoint() { return leftPoint.get() == null ? null : new Point(leftPoint.get()); }
    public Point rightPoint() { return rightPoint.get() == null ? null : new Point(rightPoint.get()); }
    public Point topPoint() { return topPoint.get() == null ? null : new Point(topPoint.get()); }
    public Point bottomPoint() { return bottomPoint.get() == null ? null : new Point(bottomPoint.get()); }


    public boolean containsAll(Collection<? extends Point> c) {
        for (Point pt : c) {
            if (!this.contains(pt)) return false;
        }
        return true;
    }

    public boolean addAll(Collection<? extends Point> c) {
        for (Point pt : c) {
            if (!this.add(pt)) return false;
        }
        return true;
    }
    public boolean addAll(PointCollection c) {
        c.onRaw();
        return c.entrySet().stream().allMatch(e -> this.addAtX(e.getKey(), e.getValue()));
    }
    public void addAll(Point... pts) {
        Arrays.stream(pts).forEach(this::add);
    }


    public boolean removeAll(Collection<? extends Point> c) {
        for (Point pt : c) {
            if (!this.remove(pt)) return false;
        }
        return true;
    }
    public void removeAll(Point... pts) {
        Arrays.stream(pts).forEach(this::remove);
    }

    public boolean removeIf(Predicate<? super Point> filter) {
        return stream().filter(filter).anyMatch(this::remove);
    }

    public boolean retainAll(Collection<?> c) {
        return retainAll(new HashSet<>(c));
    }
    public boolean retainAll(Set<?> c) {
        Iterator<Point> it = iterator();
        while (it.hasNext()) {
            if (c.contains(it.next())) it.remove();
        }
        return false;
    }

    public void clear() {
        this.pointMap.clear();
        removeYBounds();
        this.size.set(0);
    }


    // Implement all methods from Collections
    public Spliterator<Point> spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED);
    }

    public Stream<Point> stream() {
        return this.pointMap.entrySet().stream().flatMap(e -> e.getValue().stream().map(y -> new Point(e.getKey(), y)));
    }
    public Stream<Point> parallelStream() {
        return this.pointMap.entrySet().parallelStream().flatMap(e -> e.getValue().stream().parallel().map(y -> new Point(e.getKey(), y)));
    }

    public PointIterator iterator() {
        return new PointIterator(this);
    }

    public List<Point> toList() {
        return ProgrammingEx.varMutate(new ArrayList<>(size()), pts -> forEach(pt -> pts.add(pt)));
    }
    public Point[] toArray() {
        return ProgrammingEx.varMutate(new Point[size()], pts -> forEachInc((pt, i) -> pts[i] = pt));
    }

    public Set<Map.Entry<Integer, IntList>> entrySet() {
        return pointMap.entrySet();
    }

    // Methods for external streaming
    public static Collector<Point, ?, PointCollection> toPointCollection() {
        return Collector.of(
                PointCollection::new,
                PointCollection::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                Collector.Characteristics.UNORDERED
        );
    }

    public PointCollection flip() {
        return varMutate(new PointCollection(), c -> forEach(p -> c.addAtX(p.y, p.x)));
    }

    private void outOfBounds(int idx) {
        if (idx < 0 || idx >= size()) throw new ArrayIndexOutOfBoundsException("Index " + idx + " is out of bounds for length " + size());
    }

    @Override
    public Object clone() {
        return new PointCollection(this);
    }
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PointCollection oPC)) return false;
        if (oPC.size() != size()) return false;
        Set<Integer> oXes = oPC.xes(), thisXes = xes();
        if (oXes.size() != thisXes.size()) return false;
        if (!oXes.containsAll(thisXes) || !thisXes.containsAll(oXes)) return false;
        return this.parallelStream().noneMatch(Predicate.not(oPC::contains));
    }

    // Probably rewrite later
    @Override
    public int hashCode() {
        return MathEx.roundInt(ProgrammingEx.varOper(xes(), xes -> (this.size() + CollectionsEx.average(xes())) * (Math.exp(MathEx.divide(xes.stream().map(i -> ProgrammingEx.varOper(pointMap.get(i), iL -> iL.get(0) + iL.size())).mapToInt(i -> i).sum(), (this.size() % 2 == 0 ? 0.9 : -1))))));
    }


    public void onRaw() {
        this.raw = true;
    }
    public void offRaw() {
        this.raw = false;
    }
    public void rawCheck() {
        if (ThreadEx.getCaller().map(StackWalker.StackFrame::getDeclaringClass).map(PointCollection.class::isAssignableFrom).orElse(true)) {
            offRaw();
            return;
        }
        if (!raw) throw new IllegalCallerException("Cannot call raw methods when raw access is not enabled");
        offRaw();
    }

}
