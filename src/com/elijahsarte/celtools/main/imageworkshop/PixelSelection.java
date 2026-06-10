package com.elijahsarte.celtools.main.imageworkshop;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.iterators.CoordIterator;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Objects;

public class PixelSelection {

    private static final int[][] CARDINAL_NEIGHBORS = {
            { 1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };
    private static final int[][] DIAGONAL_NEIGHBORS = {
            { 1, 0}, {-1, 0}, {0, 1}, {0, -1},
            { 1, 1}, { 1, -1}, {-1, 1}, {-1, -1}
    };

    private final int width;
    private final int height;
    private PointCollection points;

    public PixelSelection(int width, int height) {
        validateDimensions(width, height);
        this.width = width;
        this.height = height;
        this.points = new PointCollection();
    }

    public PixelSelection(int width, int height, PointCollection points) {
        this(width, height);
        include(points);
    }

    public PixelSelection(int width, int height, ShapeContour contour, boolean excludeInside) {
        this(width, height);
        Objects.requireNonNull(contour, "contour");

        Rectangle bounds = contour.rect();
        int startX = Math.max(0, bounds.x);
        int startY = Math.max(0, bounds.y);
        int endX = Math.min(width, bounds.x + bounds.width + 1);
        int endY = Math.min(height, bounds.y + bounds.height + 1);

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                Point p = new Point(x, y);
                boolean inside = contour.inside(p);

                if (excludeInside) {
                    if (!inside) {
                        points.add(p);
                    }
                } else {
                    if (inside) {
                        points.add(p);
                    }
                }
            }
        }
    }

    public PixelSelection(PixelSelection other) {
        this(other.width, other.height, other.points);
    }

    public static PixelSelection empty(int width, int height) {
        return new PixelSelection(width, height);
    }

    public static PixelSelection full(int width, int height) {
        PixelSelection selection = new PixelSelection(width, height);
        new CoordIterator(width, height).execute((x, y) -> selection.points.add(new Point(x, y)));
        return selection;
    }

    public static PixelSelection fromRectangle(int width, int height, Rectangle rectangle) {
        Objects.requireNonNull(rectangle, "rectangle");
        PixelSelection selection = new PixelSelection(width, height);
        int startX = Math.max(0, rectangle.x);
        int startY = Math.max(0, rectangle.y);
        int endX = Math.min(width, rectangle.x + rectangle.width);
        int endY = Math.min(height, rectangle.y + rectangle.height);

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                selection.points.add(new Point(x, y));
            }
        }
        return selection;
    }

    public static PixelSelection magicWand(FastRGB image, int seedX, int seedY, int tolerance, boolean contiguous) {
        return magicWand(image, seedX, seedY, tolerance, contiguous, true);
    }

    public static PixelSelection magicWand(FastRGB image, int seedX, int seedY, int tolerance, boolean contiguous, boolean includeDiagonals) {
        Objects.requireNonNull(image, "image");
        int width = image.getWidth();
        int height = image.getHeight();
        PixelSelection selection = new PixelSelection(width, height);
        int[] pixels = image.getPixels();

        if (seedX < 0 || seedY < 0 || seedX >= width || seedY >= height) {
            return selection;
        }

        int seedArgb = pixels[(seedY * width) + seedX];
        if (!contiguous) {
            new CoordIterator(width, height).execute((x, y) -> {
                if (matches(seedArgb, pixels[(y * width) + x], tolerance)) {
                    selection.points.add(new Point(x, y));
                }
            });
            return selection;
        }

        boolean[] visited = new boolean[width * height];
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.addLast(MathEx.encode(seedX, seedY));
        visited[(seedY * width) + seedX] = true;
        int[][] neighbors = includeDiagonals ? DIAGONAL_NEIGHBORS : CARDINAL_NEIGHBORS;

        while (!queue.isEmpty()) {
            long encoded = queue.removeFirst();
            int x = (int) (encoded >>> 32);
            int y = (int) encoded;

            if (!matches(seedArgb, pixels[(y * width) + x], tolerance)) {
                continue;
            }

            selection.points.add(new Point(x, y));
            for (int[] neighbor : neighbors) {
                int nextX = x + neighbor[0];
                int nextY = y + neighbor[1];
                if (nextX < 0 || nextY < 0 || nextX >= width || nextY >= height) {
                    continue;
                }
                int index = (nextY * width) + nextX;
                if (!visited[index]) {
                    visited[index] = true;
                    queue.addLast(MathEx.encode(nextX, nextY));
                }
            }
        }
        return selection;
    }

    public static PixelSelection magicWand(BufferedImage image, int seedX, int seedY, int tolerance, boolean contiguous) {
        return magicWand(Filter.copyPixels(image), seedX, seedY, tolerance, contiguous, true);
    }

    public static PixelSelection magicWand(BufferedImage image, int seedX, int seedY, int tolerance, boolean contiguous, boolean includeDiagonals) {
        return magicWand(Filter.copyPixels(image), seedX, seedY, tolerance, contiguous, includeDiagonals);
    }

    public static PixelSelection magicWand(ImageHandler imageHandler, int seedX, int seedY, int tolerance, boolean contiguous) {
        return magicWand(Filter.copyPixels(imageHandler), seedX, seedY, tolerance, contiguous);
    }

    public static PixelSelection magicWand(Layer layer, int seedX, int seedY, int tolerance, boolean contiguous) {
        Objects.requireNonNull(layer, "layer");
        return magicWand(layer.getPixels(), seedX, seedY, tolerance, contiguous);
    }

    public PixelSelection include(PixelSelection other) {
        ensureSameSize(other);
        return include(other.points);
    }

    public PixelSelection include(PointCollection zone) {
        Objects.requireNonNull(zone, "zone");
        zone.forEach(point -> {
            if (inside(point.x, point.y)) {
                points.add(new Point(point));
            }
        });
        return this;
    }

    public PixelSelection include(Rectangle zone) {
        return apply(fromRectangle(width, height, zone), SelectionMode.ADD);
    }

    public PixelSelection exclude(PixelSelection other) {
        ensureSameSize(other);
        other.points.forEach(point -> points.remove(point));
        return this;
    }

    public PixelSelection exclude(PointCollection zone) {
        Objects.requireNonNull(zone, "zone");
        zone.forEach(points::remove);
        return this;
    }

    public PixelSelection exclude(Rectangle zone) {
        return apply(fromRectangle(width, height, zone), SelectionMode.SUBTRACT);
    }

    public PixelSelection intersect(PixelSelection other) {
        ensureSameSize(other);
        PointCollection intersection = new PointCollection();
        points.forEach(point -> {
            if (other.contains(point.x, point.y)) {
                intersection.add(new Point(point));
            }
        });
        this.points = intersection;
        return this;
    }

    public PixelSelection apply(PixelSelection other, SelectionMode selectionMode) {
        Objects.requireNonNull(selectionMode, "selectionMode");
        ensureSameSize(other);
        return switch (selectionMode) {
            case REPLACE -> replaceWith(other);
            case ADD -> include(other);
            case SUBTRACT -> exclude(other);
            case INTERSECT -> intersect(other);
        };
    }

    public PixelSelection replaceWith(PixelSelection other) {
        ensureSameSize(other);
        this.points = other.points();
        return this;
    }

    public PixelSelection invert() {
        PointCollection inverted = new PointCollection();
        new CoordIterator(width, height).execute((x, y) -> {
            if (!contains(x, y)) {
                inverted.add(new Point(x, y));
            }
        });
        this.points = inverted;
        return this;
    }

    public PixelSelection removeHoles() {
        if (points.isEmpty()) {
            return this;
        }

        ShapeContour outer = outerEdge();
        ShapeContour inner = innerEdge();

        if (outer.isEmpty() || inner.isEmpty()) {
            return this;
        }

        PointCollection rebuilt = new PointCollection();

        PointCollection outerPts = outer.toPointCollection();
        PointCollection innerPts = inner.toPointCollection();

        rebuilt.addAll(outerPts.between(innerPts));
        rebuilt.addAll(outerPts);
        rebuilt.addAll(innerPts);

        this.points = rebuilt;
        return this;
    }

    public PixelSelection fillAllEnclosedVoids() {
        PointCollection withoutHoles = points();
        withoutHoles.fillHoles();
        this.points = withoutHoles;
        return this;
    }

    private PointCollection enclosedBackgroundPoints() {
        final int w = width;
        final int h = height;
        final int size = w * h;

        // 0 = unknown background, 1 = selected/foreground, 2 = outside background
        final byte[] state = new byte[size];

        points.forEach(p -> state[(p.y * w) + p.x] = 1);

        final int[] qx = new int[size];
        final int[] qy = new int[size];
        int head = 0;
        int tail = 0;

        // Seed border background pixels
        for (int x = 0; x < w; x++) {
            int top = x;
            if (state[top] == 0) {
                state[top] = 2;
                qx[tail] = x;
                qy[tail] = 0;
                tail++;
            }

            int bottom = ((h - 1) * w) + x;
            if (state[bottom] == 0) {
                state[bottom] = 2;
                qx[tail] = x;
                qy[tail] = h - 1;
                tail++;
            }
        }

        for (int y = 1; y < h - 1; y++) {
            int left = y * w;
            if (state[left] == 0) {
                state[left] = 2;
                qx[tail] = 0;
                qy[tail] = y;
                tail++;
            }

            int right = left + (w - 1);
            if (state[right] == 0) {
                state[right] = 2;
                qx[tail] = w - 1;
                qy[tail] = y;
                tail++;
            }
        }

        // Flood-fill reachable background from the border
        while (head < tail) {
            final int x = qx[head];
            final int y = qy[head];
            head++;

            int idx;
            if (x > 0) {
                idx = (y * w) + (x - 1);
                if (state[idx] == 0) {
                    state[idx] = 2;
                    qx[tail] = x - 1;
                    qy[tail] = y;
                    tail++;
                }
            }
            if (x + 1 < w) {
                idx = (y * w) + (x + 1);
                if (state[idx] == 0) {
                    state[idx] = 2;
                    qx[tail] = x + 1;
                    qy[tail] = y;
                    tail++;
                }
            }
            if (y > 0) {
                idx = ((y - 1) * w) + x;
                if (state[idx] == 0) {
                    state[idx] = 2;
                    qx[tail] = x;
                    qy[tail] = y - 1;
                    tail++;
                }
            }
            if (y + 1 < h) {
                idx = ((y + 1) * w) + x;
                if (state[idx] == 0) {
                    state[idx] = 2;
                    qx[tail] = x;
                    qy[tail] = y + 1;
                    tail++;
                }
            }
        }

        final PointCollection enclosed = new PointCollection();
        for (int y = 0, idx = 0; y < h; y++) {
            for (int x = 0; x < w; x++, idx++) {
                if (state[idx] == 0) {
                    enclosed.add(new Point(x, y));
                }
            }
        }

        return enclosed;
    }

    public LayerMask toLayerMask(Rectangle layerBounds, boolean revealSelection) {
        return LayerMask.fromSelection(this, layerBounds, revealSelection);
    }

    public FastRGB toMaskPixels() {
        FastRGB mask = Filter.transparentPixels(width, height);
        points.forEach(point -> mask.setRGB(point.x, point.y, Filter.argb(255, 255, 255, 255)));
        return mask;
    }

    public BufferedImage toMaskImage() {
        return toMaskPixels().getImage();
    }

    public Rectangle bounds() {
        if (points.isEmpty()) {
            return new Rectangle();
        }

        final int[] bounds = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        points.forEach(point -> {
            bounds[0] = Math.min(bounds[0], point.x);
            bounds[1] = Math.min(bounds[1], point.y);
            bounds[2] = Math.max(bounds[2], point.x);
            bounds[3] = Math.max(bounds[3], point.y);
        });
        return new Rectangle(bounds[0], bounds[1], (bounds[2] - bounds[0]) + 1, (bounds[3] - bounds[1]) + 1);
    }

    public ShapeContour outerEdge() {
        if (points.isEmpty()) {
            return ShapeContour.empty();
        }
        return new ShapeContour(points());
    }
    public ShapeContour innerEdge() {
        if (points.isEmpty()) {
            return ShapeContour.empty();
        }

        PointCollection enclosed = enclosedBackgroundPoints();
        if (enclosed.isEmpty()) {
            return ShapeContour.empty();
        }

        return new ShapeContour(enclosed);
    }

    public boolean contains(int x, int y) {
        return inside(x, y) && points.containsX(x) && points.getYesAtX(x).contains(y);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public int size() {
        return points.size();
    }

    public PointCollection points() {
        return new PointCollection(points);
    }

    public PixelSelection copy() {
        return new PixelSelection(this);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private void ensureSameSize(PixelSelection other) {
        Objects.requireNonNull(other, "other");
        if (other.width != width || other.height != height) {
            throw new IllegalArgumentException("Selections must have identical dimensions");
        }
    }


    private void seedOutsideBackground(int x, int y, boolean[] outside, ArrayDeque<Long> queue) {
        if (!inside(x, y) || contains(x, y)) {
            return;
        }

        int idx = y * width + x;
        if (!outside[idx]) {
            outside[idx] = true;
            queue.addLast(MathEx.encode(x, y));
        }
    }

    private static boolean matches(int seedArgb, int candidateArgb, int tolerance) {
        int boundedTolerance = Math.max(0, tolerance);
        if (FastRGB.alpha(seedArgb) == 0 && FastRGB.alpha(candidateArgb) == 0) {
            return true;
        }

        return Math.abs(FastRGB.red(seedArgb) - FastRGB.red(candidateArgb)) <= boundedTolerance &&
                Math.abs(FastRGB.green(seedArgb) - FastRGB.green(candidateArgb)) <= boundedTolerance &&
                Math.abs(FastRGB.blue(seedArgb) - FastRGB.blue(candidateArgb)) <= boundedTolerance &&
                Math.abs(FastRGB.alpha(seedArgb) - FastRGB.alpha(candidateArgb)) <= boundedTolerance;
    }

    private static void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Selection dimensions must be positive");
        }
    }
}
