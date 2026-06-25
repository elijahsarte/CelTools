package com.elijahsarte.celtools.mainex;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionManager;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionWindow;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.geomex.Line;
import com.elijahsarte.celtools.main.util.geomex.Polygon;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

import static com.elijahsarte.celtools.main.util.GraphicsEx.*;

// todo: make it only run when debug mode is on (and not debug java)
public class DebuggerEx {

    private static final List<Color> colorIncMap = List.of(
            new Color(255, 0, 0),
            new Color(255, 127, 0),
            new Color(255, 255, 0),
            new Color(197, 255, 0),
            new Color(0, 255, 0),
            new Color(0, 255, 193),
            new Color(0, 255, 255),
            new Color(0, 157, 255),
            new Color(0, 0, 255),
            new Color(187, 0, 255),
            new Color(255, 0, 255),
            new Color(255, 0, 217),
            new Color(156, 0, 0),
            new Color(156, 83, 0),
            new Color(156, 156, 0),
            new Color(26, 156, 0),
            new Color(0, 156, 150),
            new Color(0, 16, 156),
            new Color(132, 0, 156),
            new Color(156, 0, 117)
    );

    private static final Color DEFAULT_POINT_COLOR = new Color(255, 0, 0);
    private static final Color CONTOUR_FILL_COLOR = new Color(33, 150, 243, 51);
    private static final Color CONTOUR_OUTLINE_COLOR = new Color(25, 118, 210);
    private static final Color CONTOUR_INFO_COLOR = Color.DARK_GRAY;
    private static final Color GUIDE_BOX_COLOR = new Color(189, 189, 189);
    private static final int DEFAULT_PADDING = 40;

    // === Sparse point labeling config ===
    private static final int LABEL_THRESHOLD = 2; // number of points below which labels appear
    private static final Font LABEL_FONT = new Font("Monospaced", Font.PLAIN, 11);
    private static final Color LABEL_COLOR = Color.BLACK;

    // Specific classes
    private static final double POLYGON_SCALE = 10d;
    private static final String EXPORT_DIR_PROPERTY = "C:\\Users\\Elijah\\Documents\\celtoolsandbox\\celautoink\\debug_output";
    private static final String DEFAULT_EXPORT_BASENAME = "debug";
    private static final String DEFAULT_VIEW_NAME = "Debugger View";

    @FunctionalInterface
    private interface LayerApplier {
        BufferedImage apply(String name, BufferedImage base, Object item);
    }

    private record LayerHandler(String key, Predicate<Object> predicate, LayerApplier applier) {
        boolean supports(Object obj) {
            return predicate.test(obj);
        }

        BufferedImage apply(String name, BufferedImage base, Object obj) {
            return applier.apply(name, base, obj);
        }
    }

    private static final List<LayerHandler> LAYER_ORDER = List.of(
            new LayerHandler("fastrgb", obj -> obj instanceof FastRGB, DebuggerEx::layerFastRGB),
            new LayerHandler("bufferedImage", obj -> obj instanceof BufferedImage, DebuggerEx::layerBufferedImage),
            new LayerHandler("imageHandler", obj -> obj instanceof ImageHandler, DebuggerEx::layerImageHandler),
            new LayerHandler("shapeBounds", obj -> obj instanceof ShapeBounds, DebuggerEx::layerShapeBounds),

            new LayerHandler("shapeContourList",
                    obj -> obj instanceof List<?> list && list.stream().allMatch(ShapeContour.class::isInstance),
                    (name, base, obj) -> layerShapeContours(name, base, castShapeContourList(obj))),
            new LayerHandler("shapeContourArray",
                    obj -> obj instanceof ShapeContour[],
                    (name, base, obj) -> layerShapeContours(name, base, Arrays.asList((ShapeContour[]) obj))),
            new LayerHandler("shapeContourIterable",
                    obj -> obj instanceof Iterable<?> it && iterableContains(it, ShapeContour.class),
                    (name, base, obj) -> layerShapeContours(name, base, iterableToShapeContours((Iterable<?>) obj))),
            new LayerHandler("shapeContour", obj -> obj instanceof ShapeContour, DebuggerEx::layerShapeContour),

            new LayerHandler("polygon", obj -> obj instanceof Polygon, DebuggerEx::layerPolygon),
            new LayerHandler("rectangle", obj -> obj instanceof Rectangle, DebuggerEx::layerRectangle),

            new LayerHandler("pointCollectionList",
                    obj -> obj instanceof List<?> list && list.stream().allMatch(PointCollection.class::isInstance),
                    (name, base, obj) -> layerPointCollections(name, base, castPointCollectionList(obj))),
            new LayerHandler("pointCollection",
                    obj -> obj instanceof PointCollection,
                    (name, base, obj) -> layerPointCollection(base, (PointCollection) obj)),
            new LayerHandler("pointCollectionArray",
                    obj -> obj instanceof PointCollection[],
                    (name, base, obj) -> layerPointCollections(name, base, Arrays.asList((PointCollection[]) obj))),
            new LayerHandler("pointCollectionIterable",
                    obj -> obj instanceof Iterable<?> it && iterableContains(it, PointCollection.class),
                    (name, base, obj) -> layerPointCollections(name, base, iterableToPointCollections((Iterable<?>) obj))),
            new LayerHandler("pointList",
                    obj -> obj instanceof Collection<?> col && !col.isEmpty() && col.iterator().next() instanceof Point,
                    (name, base, obj) -> layerPointCollection(base, new PointCollection(collectionToPoints((Collection<Point>) obj)))),
            new LayerHandler("singlePoint",
                    obj -> obj instanceof Point,
                    (name, base, obj) -> layerPointCollection(base, new PointCollection((Point) obj))),
            new LayerHandler("fallback",
                    Objects::nonNull,
                    (name, base, obj) -> base) // ignore unsupported types gracefully
    );

    public static void vis(String name, Object... items) {
        show(name, render(name, items));
    }

    public static BufferedImage render(String name, Object... items) {
        if (items == null || items.length == 0) return null;
        Object[] normalizedItems = normalizePointArguments(items);
        if (normalizedItems.length == 0) return null;

        BufferedImage canvas = null;
        boolean[] processed = new boolean[normalizedItems.length];

        for (LayerHandler handler : LAYER_ORDER) {
            for (int i = 0; i < normalizedItems.length; i++) {
                if (processed[i]) continue;
                Object item = normalizedItems[i];
                if (item == null) {
                    processed[i] = true;
                    continue;
                }
                if (handler.supports(item)) {
                    canvas = handler.apply(name, canvas, item);
                    processed[i] = true;
                }
            }
        }

        // Label after all drawing is done
        if (canvas != null) {
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (Object item : normalizedItems) {
                annotateIfSparse(g, item, null);
            }
            g.dispose();
        }

        return canvas;
    }

    public static BufferedImage render(String name, FastRGB hand, Object pts) {
        Object normalized = normalizePointItem(pts);
        return normalized == null
                ? render(name, hand)
                : render(name, new Object[]{hand, normalized});
    }

    public static BufferedImage render(String name, BufferedImage image, Object pts) {
        Object normalized = normalizePointItem(pts);
        return normalized == null
                ? render(name, image)
                : render(name, new Object[]{image, normalized});
    }

    public static BufferedImage render(String name, FastRGB hand) {
        if (hand == null) return null;
        return render(name, new Object[]{hand});
    }

    public static BufferedImage render(String name, BufferedImage image) {
        if (image == null) return null;
        return render(name, new Object[]{image});
    }

    public static BufferedImage render(String name, ImageHandler image) {
        if (image == null) return null;
        image.loadGuiImage();
        return render(name, image.getGuiImage());
    }

    public static BufferedImage render(String name, Polygon polygon) {
        return render(name, new Object[]{polygon});
    }

    public static BufferedImage render(String name, Rectangle rect) {
        return render(name, new Object[]{rect});
    }

    public static BufferedImage render(String name, PointCollection collection) {
        return render(name, new Object[]{collection});
    }

    public static BufferedImage render(String name, ShapeBounds bounds) {
        return render(name, new Object[]{bounds});
    }

    public static BufferedImage render(String name, ShapeContour contour) {
        return render(name, new Object[]{contour});
    }

    public static BufferedImage renderContours(String name, List<ShapeContour> contours) {
        if (contours == null) return null;
        return render(name, new Object[]{contours});
    }

    public static BufferedImage renderContours(List<ShapeContour> contours) {
        return renderContours(DEFAULT_VIEW_NAME, contours);
    }

    public record SelectionResult(
            String name,
            BufferedImage image,
            List<Rectangle> screenRectangles,
            List<Rectangle> modelRectangles,
            List<SelectedItem> items
    ) {
        public SelectionResult {
            screenRectangles = copyRectangles(screenRectangles);
            modelRectangles = copyRectangles(modelRectangles);
            items = items == null ? List.of() : List.copyOf(items);
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }

        public List<Object> data() {
            return items.stream().map(SelectedItem::data).toList();
        }

        public <T> List<T> data(Class<T> type) {
            if (type == null) return List.of();
            return data().stream().filter(type::isInstance).map(type::cast).toList();
        }

        public List<PointCollection> pointCollections() {
            return data(PointCollection.class);
        }

        public List<SelectedShapeBounds> shapeBounds() {
            return data(SelectedShapeBounds.class);
        }

        public List<SelectedContour> contours() {
            return data(SelectedContour.class);
        }

        public List<SelectedPolygon> polygons() {
            return data(SelectedPolygon.class);
        }
    }

    public record SelectedItem(Object source, Object data) {}

    public record ContourVertexSelection(
            int index,
            Point point,
            double rawAngle,
            double angleFromTop,
            double radius,
            double arcLength,
            double normalizedArcLength
    ) {
        public ContourVertexSelection {
            point = point == null ? null : new Point(point);
        }
    }

    public record SelectedContour(ShapeContour source, List<ContourVertexSelection> vertices) {
        public SelectedContour {
            vertices = vertices == null ? List.of() : List.copyOf(vertices);
        }

        public List<Point> points() {
            return vertices.stream().map(ContourVertexSelection::point).map(Point::new).toList();
        }

        public PointCollection toPointCollection() {
            return new PointCollection(points());
        }
    }

    public record SelectedShapeBounds(
            ShapeBounds source,
            Map<Integer, List<IntegerBounds>> bounds,
            PointCollection points
    ) {
        public SelectedShapeBounds {
            bounds = copyBounds(bounds);
            points = points == null ? new PointCollection() : new PointCollection(points);
        }
    }

    public record SelectedPolygon(
            Polygon source,
            List<Point> vertices,
            List<Line> topSegments,
            List<Line> bottomSegments
    ) {
        public SelectedPolygon {
            vertices = copyPoints(vertices);
            topSegments = topSegments == null ? List.of() : List.copyOf(topSegments);
            bottomSegments = bottomSegments == null ? List.of() : List.copyOf(bottomSegments);
        }
    }

    private record SelectionTransform(double scale, double shiftX, double shiftY) {
        private static final SelectionTransform IDENTITY = new SelectionTransform(1.0, 0.0, 0.0);

        Rectangle toModel(Rectangle screenRect) {
            if (screenRect == null) return new Rectangle();
            Rectangle r = normalizeRect(screenRect);
            if (scale == 1.0 && shiftX == 0.0 && shiftY == 0.0) return new Rectangle(r);

            int x1 = (int) Math.floor((r.x - shiftX) / scale);
            int y1 = (int) Math.floor((r.y - shiftY) / scale);
            int x2 = (int) Math.ceil((r.x + r.width - shiftX) / scale);
            int y2 = (int) Math.ceil((r.y + r.height - shiftY) / scale);

            int minX = Math.min(x1, x2);
            int minY = Math.min(y1, y2);
            return new Rectangle(minX, minY, Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
    }

    public static SelectionResult Select(String name, Object... items) {
        return select(name, items);
    }

    public static SelectionResult Select(String name, FastRGB hand, Object pts) {
        return select(name, hand, pts);
    }

    public static SelectionResult Select(String name, BufferedImage image, Object pts) {
        return select(name, image, pts);
    }

    public static SelectionResult Select(String name, FastRGB hand) {
        return select(name, hand);
    }

    public static SelectionResult Select(String name, BufferedImage image) {
        return select(name, image);
    }

    public static SelectionResult Select(String name, ImageHandler image) {
        return select(name, image);
    }

    public static SelectionResult Select(String name, Polygon poly) {
        return select(name, poly);
    }

    public static SelectionResult Select(String name, Rectangle rect) {
        return select(name, rect);
    }

    public static SelectionResult Select(String name, PointCollection collection) {
        return select(name, collection);
    }

    public static SelectionResult Select(String name, ShapeBounds bounds) {
        return select(name, bounds);
    }

    public static SelectionResult Select(String name, ShapeContour contour) {
        return select(name, contour);
    }

    public static SelectionResult Select(Object... items) {
        return Select(DEFAULT_VIEW_NAME, items);
    }

    public static SelectionResult Select(FastRGB hand, Object pts) {
        return Select(DEFAULT_VIEW_NAME, hand, pts);
    }

    public static SelectionResult Select(BufferedImage image, Object pts) {
        return Select(DEFAULT_VIEW_NAME, image, pts);
    }

    public static SelectionResult Select(FastRGB hand) {
        return Select(DEFAULT_VIEW_NAME, hand);
    }

    public static SelectionResult Select(BufferedImage image) {
        return Select(DEFAULT_VIEW_NAME, image);
    }

    public static SelectionResult Select(ImageHandler image) {
        return Select(DEFAULT_VIEW_NAME, image);
    }

    public static SelectionResult Select(Polygon poly) {
        return Select(DEFAULT_VIEW_NAME, poly);
    }

    public static SelectionResult Select(Rectangle rect) {
        return Select(DEFAULT_VIEW_NAME, rect);
    }

    public static SelectionResult Select(PointCollection collection) {
        return Select(DEFAULT_VIEW_NAME, collection);
    }

    public static SelectionResult Select(ShapeBounds bounds) {
        return Select(DEFAULT_VIEW_NAME, bounds);
    }

    public static SelectionResult Select(ShapeContour contour) {
        return Select(DEFAULT_VIEW_NAME, contour);
    }

    public static SelectionResult SelectM(String name, FastRGB hand, Object givenPts) {
        return Select(name, hand, givenPts);
    }

    public static SelectionResult SelectM(String name, BufferedImage image, Object pts) {
        return Select(name, image, pts);
    }

    public static SelectionResult SelectM(FastRGB hand, Object givenPts) {
        return SelectM(DEFAULT_VIEW_NAME, hand, givenPts);
    }

    public static SelectionResult SelectM(BufferedImage image, Object pts) {
        return SelectM(DEFAULT_VIEW_NAME, image, pts);
    }

    public static SelectionResult SelectM(String name, List<PointCollection> collections) {
        return Select(name, collections);
    }

    public static SelectionResult SelectM(List<PointCollection> collections) {
        return SelectM(DEFAULT_VIEW_NAME, collections);
    }

    public static SelectionResult SelectContours(String name, List<ShapeContour> contours) {
        return contours == null ? new SelectionResult(name, null, List.of(), List.of(), List.of()) : Select(name, contours);
    }

    public static SelectionResult SelectContours(List<ShapeContour> contours) {
        return SelectContours(DEFAULT_VIEW_NAME, contours);
    }

    private static Object[] normalizePointArguments(Object[] items) {
        List<Object> normalized = new ArrayList<>(items.length);
        List<PointCollection> bufferedCollections = new ArrayList<>();

        for (Object item : items) {
            List<PointCollection> grouped = asPointCollectionList(item);
            if (grouped != null) {
                flushBufferedPointCollections(bufferedCollections, normalized);
                normalized.add(grouped);
                continue;
            }

            PointCollection single = asPointCollection(item);
            if (single != null) {
                bufferedCollections.add(single);
                continue;
            }

            flushBufferedPointCollections(bufferedCollections, normalized);
            normalized.add(item);
        }

        flushBufferedPointCollections(bufferedCollections, normalized);
        return normalized.toArray(new Object[0]);
    }

    private static void flushBufferedPointCollections(List<PointCollection> buffer, List<Object> normalized) {
        if (buffer.isEmpty()) return;
        normalized.add(new ArrayList<>(buffer));
        buffer.clear();
    }

    @SuppressWarnings("unchecked")
    private static List<PointCollection> asPointCollectionList(Object obj) {
        if (obj instanceof List<?> list && list.stream().allMatch(PointCollection.class::isInstance)) {
            return (List<PointCollection>) list;
        }
        if (obj instanceof Collection<?> collection && collectionContainsOnlyPointCollections(collection)) {
            return copyToPointCollectionList(collection);
        }
        if (obj instanceof PointCollection[] array) {
            return Arrays.asList(array);
        }
        return null;
    }

    private static boolean collectionContainsOnlyPointCollections(Collection<?> collection) {
        if (collection == null) return false;
        for (Object element : collection) {
            if (!(element instanceof PointCollection)) {
                return false;
            }
        }
        return true;
    }

    private static List<PointCollection> copyToPointCollectionList(Collection<?> collection) {
        List<PointCollection> copy = new ArrayList<>(collection.size());
        for (Object element : collection) {
            copy.add((PointCollection) element);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<ShapeContour> asShapeContourList(Object obj) {
        if (obj instanceof List<?> list && list.stream().allMatch(ShapeContour.class::isInstance)) {
            return (List<ShapeContour>) list;
        }
        if (obj instanceof Collection<?> collection && collectionContainsOnlyShapeContours(collection)) {
            return copyToShapeContourList(collection);
        }
        if (obj instanceof ShapeContour[] array) {
            return Arrays.asList(array);
        }
        return null;
    }

    private static boolean collectionContainsOnlyShapeContours(Collection<?> collection) {
        if (collection == null) return false;
        for (Object element : collection) {
            if (!(element instanceof ShapeContour)) {
                return false;
            }
        }
        return true;
    }

    private static List<ShapeContour> copyToShapeContourList(Collection<?> collection) {
        List<ShapeContour> copy = new ArrayList<>(collection.size());
        for (Object element : collection) {
            copy.add((ShapeContour) element);
        }
        return copy;
    }

    private static PointCollection asPointCollection(Object obj) {
        if (obj instanceof PointCollection pc) return pc;
        if (obj instanceof Collection<?> collection) {
            PointCollection fromCollection = pointCollectionFromPoints(collection);
            if (fromCollection != null) return fromCollection;
        }
        if (obj instanceof Point[] array) {
            return new PointCollection(Arrays.asList(array));
        }
        if (obj instanceof Point point) {
            return new PointCollection(point);
        }
        return null;
    }

    private static PointCollection pointCollectionFromPoints(Collection<?> collection) {
        if (collection == null) return null;
        List<Point> points = new ArrayList<>(collection.size());
        for (Object element : collection) {
            if (!(element instanceof Point point)) return null;
            points.add(point);
        }
        return new PointCollection(points);
    }

    private static Object normalizePointItem(Object pts) {
        if (pts == null) return null;

        if (pts instanceof PointCollection || pts instanceof ShapeContour || pts instanceof ShapeBounds) {
            return pts;
        }
        if (pts instanceof PointCollection[] array) {
            return Arrays.asList(array);
        }
        if (pts instanceof ShapeContour[] array) {
            return Arrays.asList(array);
        }
        if (pts instanceof Point[] pointsArray) {
            return new PointCollection(Arrays.asList(pointsArray));
        }
        if (pts instanceof Collection<?> collection) {
            if (collection.isEmpty()) return List.of();
            Object first = collection.iterator().next();
            if (first instanceof PointCollection) {
                List<PointCollection> list = new ArrayList<>(collection.size());
                for (Object obj : collection) {
                    if (obj instanceof PointCollection pc) list.add(pc);
                }
                return list;
            }
            if (first instanceof ShapeContour) {
                List<ShapeContour> list = new ArrayList<>(collection.size());
                for (Object obj : collection) {
                    if (obj instanceof ShapeContour contour) list.add(contour);
                }
                return list;
            }
            if (first instanceof Point) {
                return new PointCollection(collectionToPoints((Collection<Point>) collection));
            }
        }
        if (pts instanceof Iterable<?> iterable && !(pts instanceof Collection<?>)) {
            List<Object> copy = new ArrayList<>();
            for (Object obj : iterable) copy.add(obj);
            return normalizePointItem(copy);
        }
        if (pts instanceof Point point) {
            return new PointCollection(point);
        }
        return pts;
    }

    public static void vis(String name, FastRGB hand, Object pts) {
        show(name, render(name, hand, pts));
    }

    public static void vis(String name, BufferedImage image, Object pts) {
        show(name, render(name, image, pts));
    }

    public static void vis(String name, FastRGB hand) {
        show(name, render(name, hand));
    }

    public static void vis(String name, BufferedImage image) {
        show(name, render(name, image));
    }

    public static void vis(String name, ImageHandler image) {
        show(name, render(name, image));
    }

    public static void visM(String name, FastRGB hand, Object givenPts) {
        show(name, render(name, hand, givenPts));
    }

    public static void visM(String name, BufferedImage image, Object pts) {
        show(name, render(name, image, pts));
    }

    public static void visM(FastRGB hand, Object givenPts) {
        visM(DEFAULT_VIEW_NAME, hand, givenPts);
    }

    public static void visM(BufferedImage image, Object pts) {
        visM(DEFAULT_VIEW_NAME, image, pts);
    }

    // For specific classes
    public static void vis(String name, Polygon poly) {
        show(name, render(name, poly));
    }

    public static void vis(String name, Rectangle rect) {
        show(name, render(name, rect));
    }

    public static void vis(String name, PointCollection collection) {
        show(name, render(name, collection));
    }

    public static void vis(String name, ShapeBounds bounds) {
        show(name, render(name, bounds));
    }

    public static void vis(String name, ShapeContour contour) {
        show(name, render(name, contour));
    }

    public static void vis(Object... items) {
        vis(DEFAULT_VIEW_NAME, items);
    }

    public static void vis(FastRGB hand, Object pts) {
        vis(DEFAULT_VIEW_NAME, hand, pts);
    }

    public static void vis(BufferedImage image, Object pts) {
        vis(DEFAULT_VIEW_NAME, image, pts);
    }

    public static void vis(FastRGB hand) {
        vis(DEFAULT_VIEW_NAME, hand);
    }

    public static void vis(BufferedImage image) {
        vis(DEFAULT_VIEW_NAME, image);
    }

    public static void vis(ImageHandler image) {
        vis(DEFAULT_VIEW_NAME, image);
    }

    public static void vis(Polygon poly) {
        vis(DEFAULT_VIEW_NAME, poly);
    }

    public static void vis(Rectangle rect) {
        vis(DEFAULT_VIEW_NAME, rect);
    }

    public static void vis(PointCollection collection) {
        vis(DEFAULT_VIEW_NAME, collection);
    }

    public static void vis(ShapeBounds bounds) {
        vis(DEFAULT_VIEW_NAME, bounds);
    }

    public static void vis(ShapeContour contour) {
        vis(DEFAULT_VIEW_NAME, contour);
    }

    public static void visContours(String name, List<ShapeContour> contours) {
        show(name, renderContours(name, contours));
    }

    public static void visContours(List<ShapeContour> contours) {
        visContours(DEFAULT_VIEW_NAME, contours);
    }

    public static void visN(String name, Object... items) {
        showNonBlocking(name, render(name, items));
    }

    public static void visN(String name, FastRGB hand, Object pts) {
        showNonBlocking(name, render(name, hand, pts));
    }

    public static void visN(String name, BufferedImage image, Object pts) {
        showNonBlocking(name, render(name, image, pts));
    }

    public static void visN(String name, FastRGB hand) {
        showNonBlocking(name, render(name, hand));
    }

    public static void visN(String name, BufferedImage image) {
        showNonBlocking(name, render(name, image));
    }

    public static void visN(String name, ImageHandler image) {
        showNonBlocking(name, render(name, image));
    }

    public static void visN(String name, Polygon poly) {
        showNonBlocking(name, render(name, poly));
    }

    public static void visN(String name, Rectangle rect) {
        showNonBlocking(name, render(name, rect));
    }

    public static void visN(String name, PointCollection collection) {
        showNonBlocking(name, render(name, collection));
    }

    public static void visN(String name, ShapeBounds bounds) {
        showNonBlocking(name, render(name, bounds));
    }

    public static void visN(String name, ShapeContour contour) {
        showNonBlocking(name, render(name, contour));
    }

    public static void visN(Object... items) {
        visN(DEFAULT_VIEW_NAME, items);
    }

    public static void visN(FastRGB hand, Object pts) {
        visN(DEFAULT_VIEW_NAME, hand, pts);
    }

    public static void visN(BufferedImage image, Object pts) {
        visN(DEFAULT_VIEW_NAME, image, pts);
    }

    public static void visN(FastRGB hand) {
        visN(DEFAULT_VIEW_NAME, hand);
    }

    public static void visN(BufferedImage image) {
        visN(DEFAULT_VIEW_NAME, image);
    }

    public static void visN(ImageHandler image) {
        visN(DEFAULT_VIEW_NAME, image);
    }

    public static void visN(Polygon poly) {
        visN(DEFAULT_VIEW_NAME, poly);
    }

    public static void visN(Rectangle rect) {
        visN(DEFAULT_VIEW_NAME, rect);
    }

    public static void visN(PointCollection collection) {
        visN(DEFAULT_VIEW_NAME, collection);
    }

    public static void visN(ShapeBounds bounds) {
        visN(DEFAULT_VIEW_NAME, bounds);
    }

    public static void visN(ShapeContour contour) {
        visN(DEFAULT_VIEW_NAME, contour);
    }

    public static void visContoursN(String name, List<ShapeContour> contours) {
        showNonBlocking(name, renderContours(name, contours));
    }

    public static void visContoursN(List<ShapeContour> contours) {
        visContoursN(DEFAULT_VIEW_NAME, contours);
    }

    public static void export(String name, Object... items) {
        exportImage(name, render(name, items), null);
    }

    public static void export(String name, FastRGB hand, Object pts) {
        exportImage(name, render(name, hand, pts), null);
    }

    public static void export(String name, BufferedImage image, Object pts) {
        exportImage(name, render(name, image, pts), null);
    }

    public static void export(String name, FastRGB hand) {
        exportImage(name, render(name, hand), null);
    }

    public static void export(String name, BufferedImage image) {
        exportImage(name, render(name, image), null);
    }

    public static void export(String name, ImageHandler image) {
        exportImage(name, render(name, image), null);
    }

    public static void export(String name, Polygon poly) {
        exportImage(name, render(name, poly), null);
    }

    public static void export(String name, Rectangle rect) {
        exportImage(name, render(name, rect), null);
    }

    public static void export(String name, PointCollection collection) {
        exportImage(name, render(name, collection), null);
    }

    public static void export(String name, ShapeBounds bounds) {
        exportImage(name, render(name, bounds), null);
    }

    public static void export(String name, ShapeContour contour) {
        exportImage(name, render(name, contour), null);
    }

    public static void export(Object... items) {
        export(DEFAULT_VIEW_NAME, items);
    }

    public static void export(FastRGB hand, Object pts) {
        export(DEFAULT_VIEW_NAME, hand, pts);
    }

    public static void export(BufferedImage image, Object pts) {
        export(DEFAULT_VIEW_NAME, image, pts);
    }

    public static void export(FastRGB hand) {
        export(DEFAULT_VIEW_NAME, hand);
    }

    public static void export(BufferedImage image) {
        export(DEFAULT_VIEW_NAME, image);
    }

    public static void export(ImageHandler image) {
        export(DEFAULT_VIEW_NAME, image);
    }

    public static void export(Polygon poly) {
        export(DEFAULT_VIEW_NAME, poly);
    }

    public static void export(Rectangle rect) {
        export(DEFAULT_VIEW_NAME, rect);
    }

    public static void export(PointCollection collection) {
        export(DEFAULT_VIEW_NAME, collection);
    }

    public static void export(ShapeBounds bounds) {
        export(DEFAULT_VIEW_NAME, bounds);
    }

    public static void export(ShapeContour contour) {
        export(DEFAULT_VIEW_NAME, contour);
    }

    public static void exportContours(String name, List<ShapeContour> contours) {
        exportImage(name, renderContours(name, contours), null);
    }

    public static void exportContours(List<ShapeContour> contours) {
        exportContours(DEFAULT_VIEW_NAME, contours);
    }

    private static SelectionResult select(String name, Object... items) {
        if (items == null || items.length == 0) {
            return new SelectionResult(name, null, List.of(), List.of(), List.of());
        }

        BufferedImage image = render(name, items);
        if (image == null) {
            return new SelectionResult(name, null, List.of(), List.of(), List.of());
        }

        List<Rectangle> screenRects = showSelection(name, image);
        SelectionTransform transform = selectionTransformFor(image, items);
        List<Rectangle> modelRects = screenRects.stream().map(transform::toModel).toList();
        List<SelectedItem> selectedItems = collectSelectedItems(items, modelRects);

        return new SelectionResult(name, image, screenRects, modelRects, selectedItems);
    }

    private static List<Rectangle> showSelection(String name, BufferedImage image) {
        if (image == null || GraphicsEnvironment.isHeadless()) return List.of();

        final String windowName = (name == null || name.isBlank()) ? DEFAULT_VIEW_NAME : name;
        final List<Rectangle>[] result = new List[]{List.<Rectangle>of()};

        TaskTracker.pause();
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();

                FreeformSelectionManager manager = new FreeformSelectionManager(
                        image,
                        windowName,
                        com.elijahsarte.celtools.main.selectionui.SelectionTransform.empty(),
                        FreeformSelectionWindow.SelectionMode.MULTI_RECTANGLE
                );

                Thread waiter = new Thread(() -> {
                    try {
                        List<Rectangle> rectangles = manager.getSelectedRectangles();
                        result[0] = rectangles == null ? List.of() : rectangles;
                    } catch (Exception ignored) {
                        result[0] = List.of();
                    } finally {
                        loop.exit();
                    }
                }, "debuggerex-multi-rectangle-selection");
                waiter.setDaemon(true);
                waiter.start();
                loop.enter();
            } else {
                final FreeformSelectionManager[] managerRef = new FreeformSelectionManager[1];

                SwingUtilities.invokeAndWait(() -> managerRef[0] = new FreeformSelectionManager(
                        image,
                        windowName,
                        com.elijahsarte.celtools.main.selectionui.SelectionTransform.empty(),
                        FreeformSelectionWindow.SelectionMode.MULTI_RECTANGLE
                ));

                List<Rectangle> rectangles = managerRef[0].getSelectedRectangles();
                result[0] = rectangles == null ? List.of() : rectangles;
            }
        } catch (Exception ignored) {
            result[0] = List.of();
        } finally {
            TaskTracker.resume();
        }

        return copyRectangles(result[0]);
    }

    private static SelectionTransform selectionTransformFor(BufferedImage image, Object... items) {
        if (image == null || items == null || items.length == 0) return SelectionTransform.IDENTITY;
        Object[] normalized = normalizePointArguments(items);

        for (Object item : normalized) {
            if (item instanceof FastRGB || item instanceof BufferedImage || item instanceof ImageHandler) {
                return SelectionTransform.IDENTITY;
            }
        }

        for (Object item : normalized) {
            if (item instanceof ShapeBounds bounds) {
                return transformForShapeBounds(bounds);
            }
        }

        for (Object item : normalized) {
            List<ShapeContour> contours = asShapeContourList(item);
            if (contours != null) {
                return transformForShapeContours(contours);
            }
        }

        for (Object item : normalized) {
            if (item instanceof ShapeContour contour) {
                return transformForShapeContour(contour);
            }
        }

        for (Object item : normalized) {
            if (item instanceof Polygon poly) {
                return transformForPolygon(poly);
            }
            if (item instanceof Rectangle rect) {
                Polygon poly = Polygon.of(
                        new Line(new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y)),
                        new Line(new Point(rect.x, rect.y + rect.height), new Point(rect.x + rect.width, rect.y + rect.height))
                );
                return transformForPolygon(poly);
            }
        }

        for (Object item : normalized) {
            List<PointCollection> collections = asPointCollectionList(item);
            if (collections != null) return transformForPointCollections(collections);
            PointCollection collection = asPointCollection(item);
            if (collection != null) return transformForPointCollections(List.of(collection));
        }

        return SelectionTransform.IDENTITY;
    }

    private static SelectionTransform transformForShapeBounds(ShapeBounds bounds) {
        TreeMap<Integer, IntegerBounds> boundsMap = ensureBoundsMap(bounds);
        if (boundsMap == null || boundsMap.isEmpty()) return SelectionTransform.IDENTITY;

        int minX = boundsMap.firstKey();
        int maxX = boundsMap.lastKey();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (IntegerBounds b : boundsMap.values()) {
            if (b == null) continue;
            minY = Math.min(minY, b.getLowerBound());
            maxY = Math.max(maxY, b.getUpperBound());
        }
        if (minY == Integer.MAX_VALUE || maxY == Integer.MIN_VALUE) return SelectionTransform.IDENTITY;

        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);
        double scale = standaloneShapeScale(spanX, spanY);
        return new SelectionTransform(scale, DEFAULT_PADDING - minX * scale, DEFAULT_PADDING - minY * scale);
    }

    private static SelectionTransform transformForShapeContour(ShapeContour contour) {
        if (contour == null || contour.isEmpty()) return SelectionTransform.IDENTITY;
        List<Point> points = contour.toList();
        if (points.isEmpty()) return SelectionTransform.IDENTITY;

        int minX = points.stream().mapToInt(p -> p.x).min().orElse(0);
        int maxX = points.stream().mapToInt(p -> p.x).max().orElse(minX);
        int minY = points.stream().mapToInt(p -> p.y).min().orElse(0);
        int maxY = points.stream().mapToInt(p -> p.y).max().orElse(minY);

        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);
        double scale = standaloneShapeScale(spanX, spanY);
        return new SelectionTransform(scale, DEFAULT_PADDING - minX * scale, DEFAULT_PADDING - minY * scale);
    }

    private static SelectionTransform transformForShapeContours(List<ShapeContour> contours) {
        if (contours == null || contours.isEmpty()) return SelectionTransform.IDENTITY;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean hasPoint = false;

        for (ShapeContour contour : contours) {
            if (contour == null || contour.isEmpty()) continue;
            for (Point point : contour.toList()) {
                hasPoint = true;
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
            }
        }

        if (!hasPoint) return SelectionTransform.IDENTITY;

        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);
        double scale = standaloneShapeScale(spanX, spanY);
        return new SelectionTransform(scale, DEFAULT_PADDING - minX * scale, DEFAULT_PADDING - minY * scale);
    }

    private static double standaloneShapeScale(int spanX, int spanY) {
        final int MAX_CANVAS = 760;
        final double DEFAULT_SCALE = 8.0;
        final double MAX_SCALE = 32.0;
        double availableX = MAX_CANVAS - DEFAULT_PADDING * 2.0;
        double availableY = MAX_CANVAS - DEFAULT_PADDING * 2.0;
        double scale = Math.min(availableX / Math.max(1, spanX), availableY / Math.max(1, spanY));
        if (!Double.isFinite(scale) || scale <= 0.0) return DEFAULT_SCALE;
        return Math.min(MAX_SCALE, scale);
    }

    private static SelectionTransform transformForPolygon(Polygon poly) {
        if (poly == null) return SelectionTransform.IDENTITY;
        poly.cache();
        int pad = (int) Math.max(Math.max(poly.width(), poly.height()) * POLYGON_SCALE / 10d, 20 * POLYGON_SCALE);
        return new SelectionTransform(
                POLYGON_SCALE,
                pad - poly.leftX() * POLYGON_SCALE,
                pad - poly.bottomY() * POLYGON_SCALE
        );
    }

    private static SelectionTransform transformForPointCollections(List<PointCollection> collections) {
        if (collections == null || collections.isEmpty()) return SelectionTransform.IDENTITY;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        boolean hasPoint = false;

        for (PointCollection pc : collections) {
            if (pc == null) continue;
            for (Point p : pc) {
                hasPoint = true;
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }
        }
        if (!hasPoint) return SelectionTransform.IDENTITY;

        int rangeX = maxX - minX;
        int rangeY = maxY - minY;
        if (rangeX == 0 && rangeY == 0) return SelectionTransform.IDENTITY;

        final int MAX_IMG_SIZE = 1000;
        int limX = Math.max(1, rangeX);
        int limY = Math.max(1, rangeY);
        double scale = Math.min(
                (double) (MAX_IMG_SIZE - 2 * 4 - 2 * 10) / limX,
                (double) (MAX_IMG_SIZE - 2 * 4 - 2 * 10) / limY) * 0.9;
        return new SelectionTransform(scale, 4 - minX * scale, 4 - minY * scale);
    }

    private static List<SelectedItem> collectSelectedItems(Object[] items, List<Rectangle> rects) {
        if (items == null || rects == null || rects.isEmpty()) return List.of();
        Object[] normalized = normalizePointArguments(items);
        List<SelectedItem> selected = new ArrayList<>();

        for (Object item : normalized) {
            if (item == null || item instanceof FastRGB || item instanceof BufferedImage || item instanceof ImageHandler) {
                continue;
            }

            if (item instanceof ShapeBounds bounds) {
                SelectedShapeBounds data = selectShapeBounds(bounds, rects);
                if (!data.points().isEmpty()) selected.add(new SelectedItem(bounds, data));
                continue;
            }

            List<ShapeContour> contourList = asShapeContourList(item);
            if (contourList != null) {
                for (ShapeContour contour : contourList) {
                    SelectedContour data = selectShapeContour(contour, rects);
                    if (!data.vertices().isEmpty()) selected.add(new SelectedItem(contour, data));
                }
                continue;
            }

            if (item instanceof ShapeContour contour) {
                SelectedContour data = selectShapeContour(contour, rects);
                if (!data.vertices().isEmpty()) selected.add(new SelectedItem(contour, data));
                continue;
            }

            if (item instanceof Polygon poly) {
                SelectedPolygon data = selectPolygon(poly, rects);
                if (!data.vertices().isEmpty() || !data.topSegments().isEmpty() || !data.bottomSegments().isEmpty()) {
                    selected.add(new SelectedItem(poly, data));
                }
                continue;
            }

            if (item instanceof Rectangle rect) {
                List<Rectangle> intersections = selectRectangle(rect, rects);
                if (!intersections.isEmpty()) selected.add(new SelectedItem(rect, intersections));
                continue;
            }

            List<PointCollection> collections = asPointCollectionList(item);
            if (collections != null) {
                for (PointCollection pc : collections) {
                    PointCollection selectedPoints = selectPointCollection(pc, rects);
                    if (!selectedPoints.isEmpty()) selected.add(new SelectedItem(pc, selectedPoints));
                }
                continue;
            }

            PointCollection pc = asPointCollection(item);
            if (pc != null) {
                PointCollection selectedPoints = selectPointCollection(pc, rects);
                if (!selectedPoints.isEmpty()) selected.add(new SelectedItem(item, selectedPoints));
            }
        }

        return selected;
    }

    private static PointCollection selectPointCollection(PointCollection collection, List<Rectangle> rects) {
        PointCollection selected = new PointCollection();
        if (collection == null || rects == null || rects.isEmpty()) return selected;
        for (Point p : collection) {
            if (insideAny(rects, p)) selected.add(p);
        }
        return selected;
    }

    private static SelectedContour selectShapeContour(ShapeContour contour, List<Rectangle> rects) {
        if (contour == null || contour.isEmpty() || rects == null || rects.isEmpty()) {
            return new SelectedContour(contour, List.of());
        }

        List<ContourVertexSelection> vertices = new ArrayList<>();
        for (int i = 0; i < contour.size(); i++) {
            Point p = contour.get(i);
            if (!insideAny(rects, p)) continue;
            vertices.add(new ContourVertexSelection(
                    i,
                    p,
                    contour.rawAngle(i),
                    contour.angleFromTop(i),
                    contour.radius(i),
                    contour.arcLengthTo(i),
                    contour.normalizedArcLengthTo(i)
            ));
        }
        return new SelectedContour(contour, vertices);
    }

    private static SelectedShapeBounds selectShapeBounds(ShapeBounds bounds, List<Rectangle> rects) {
        TreeMap<Integer, IntegerBounds> boundsMap = ensureBoundsMap(bounds);
        PointCollection points = new PointCollection();
        Map<Integer, List<IntegerBounds>> clipped = new TreeMap<>();
        if (boundsMap == null || boundsMap.isEmpty() || rects == null || rects.isEmpty()) {
            return new SelectedShapeBounds(bounds, clipped, points);
        }

        for (Map.Entry<Integer, IntegerBounds> entry : boundsMap.entrySet()) {
            int x = entry.getKey();
            IntegerBounds original = entry.getValue();
            if (original == null) continue;

            List<IntegerBounds> spans = new ArrayList<>();
            for (Rectangle rect : rects) {
                Rectangle r = normalizeRect(rect);
                if (x < r.x || x > r.x + r.width) continue;

                int low = Math.max(original.getLowerBound(), r.y);
                int high = Math.min(original.getUpperBound(), r.y + r.height);
                if (low > high) continue;

                IntegerBounds span = new IntegerBounds(low, high);
                spans.add(span);
                for (int y = low; y <= high; y++) points.add(new Point(x, y));
            }
            if (!spans.isEmpty()) clipped.put(x, mergeBounds(spans));
        }

        return new SelectedShapeBounds(bounds, clipped, points);
    }

    private static SelectedPolygon selectPolygon(Polygon poly, List<Rectangle> rects) {
        if (poly == null || rects == null || rects.isEmpty()) {
            return new SelectedPolygon(poly, List.of(), List.of(), List.of());
        }

        List<Point> vertices = uniquePoints(poly.pts().stream().filter(p -> insideAny(rects, p)).toList());
        List<Line> top = selectPolygonLines(poly.top(), rects);
        List<Line> bottom = selectPolygonLines(poly.bottom(), rects);
        return new SelectedPolygon(poly, vertices, top, bottom);
    }

    private static List<Line> selectPolygonLines(List<Line> lines, List<Rectangle> rects) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<Line> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Line line : lines) {
            for (Rectangle rect : rects) {
                Line clipped = clipLine(line, normalizeRect(rect));
                if (clipped == null) continue;
                String key = clipped.start().x + "," + clipped.start().y + ":" + clipped.end().x + "," + clipped.end().y;
                if (seen.add(key)) selected.add(clipped);
            }
        }
        return selected;
    }

    private static List<Rectangle> selectRectangle(Rectangle source, List<Rectangle> rects) {
        if (source == null || rects == null || rects.isEmpty()) return List.of();
        Rectangle sourceNorm = normalizeRect(source);
        List<Rectangle> out = new ArrayList<>();
        for (Rectangle rect : rects) {
            Rectangle intersection = sourceNorm.intersection(normalizeRect(rect));
            if (intersection.width >= 0 && intersection.height >= 0 && !intersection.isEmpty()) {
                out.add(intersection);
            }
        }
        return copyRectangles(out);
    }

    private static Line clipLine(Line line, Rectangle rect) {
        if (line == null || rect == null) return null;
        double x0 = line.start().x;
        double y0 = line.start().y;
        double x1 = line.end().x;
        double y1 = line.end().y;
        double minX = rect.x;
        double maxX = rect.x + rect.width;
        double minY = rect.y;
        double maxY = rect.y + rect.height;

        if (!new Rectangle(rect).intersectsLine(new Line2D.Double(x0, y0, x1, y1))
                && !containsInclusive(rect, line.start())
                && !containsInclusive(rect, line.end())) {
            return null;
        }

        double dx = x1 - x0;
        double dy = y1 - y0;
        double[] t = {0.0, 1.0};

        if (!clipParam(-dx, x0 - minX, t)) return null;
        if (!clipParam(dx, maxX - x0, t)) return null;
        if (!clipParam(-dy, y0 - minY, t)) return null;
        if (!clipParam(dy, maxY - y0, t)) return null;

        Point start = new Point((int) Math.round(x0 + t[0] * dx), (int) Math.round(y0 + t[0] * dy));
        Point end = new Point((int) Math.round(x0 + t[1] * dx), (int) Math.round(y0 + t[1] * dy));
        if (start.equals(end) && !containsInclusive(rect, start)) return null;
        return new Line(start, end);
    }

    private static boolean clipParam(double p, double q, double[] t) {
        if (p == 0.0) return q >= 0.0;
        double r = q / p;
        if (p < 0.0) {
            if (r > t[1]) return false;
            if (r > t[0]) t[0] = r;
        } else {
            if (r < t[0]) return false;
            if (r < t[1]) t[1] = r;
        }
        return true;
    }

    private static boolean insideAny(List<Rectangle> rects, Point p) {
        if (p == null || rects == null) return false;
        for (Rectangle rect : rects) {
            if (containsInclusive(rect, p)) return true;
        }
        return false;
    }

    private static boolean containsInclusive(Rectangle rect, Point p) {
        if (rect == null || p == null) return false;
        Rectangle r = normalizeRect(rect);
        return p.x >= r.x && p.x <= r.x + r.width && p.y >= r.y && p.y <= r.y + r.height;
    }

    private static Rectangle normalizeRect(Rectangle rect) {
        if (rect == null) return new Rectangle();
        int x1 = Math.min(rect.x, rect.x + rect.width);
        int y1 = Math.min(rect.y, rect.y + rect.height);
        int x2 = Math.max(rect.x, rect.x + rect.width);
        int y2 = Math.max(rect.y, rect.y + rect.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    private static List<Rectangle> copyRectangles(List<Rectangle> rects) {
        if (rects == null || rects.isEmpty()) return List.of();
        return rects.stream().filter(Objects::nonNull).map(Rectangle::new).toList();
    }

    private static List<Point> copyPoints(List<Point> points) {
        if (points == null || points.isEmpty()) return List.of();
        return points.stream().filter(Objects::nonNull).map(Point::new).toList();
    }

    private static List<Point> uniquePoints(List<Point> points) {
        if (points == null || points.isEmpty()) return List.of();
        List<Point> out = new ArrayList<>();
        Set<Point> seen = new HashSet<>();
        for (Point point : points) {
            if (point == null) continue;
            Point copy = new Point(point);
            if (seen.add(copy)) out.add(copy);
        }
        return out;
    }

    private static Map<Integer, List<IntegerBounds>> copyBounds(Map<Integer, List<IntegerBounds>> bounds) {
        if (bounds == null || bounds.isEmpty()) return Map.of();
        Map<Integer, List<IntegerBounds>> out = new TreeMap<>();
        bounds.forEach((x, spans) -> {
            if (x == null || spans == null || spans.isEmpty()) return;
            out.put(x, spans.stream()
                    .filter(Objects::nonNull)
                    .map(b -> new IntegerBounds(b.getLowerBound(), b.getUpperBound()))
                    .toList());
        });
        return Collections.unmodifiableMap(out);
    }

    private static List<IntegerBounds> mergeBounds(List<IntegerBounds> bounds) {
        if (bounds == null || bounds.isEmpty()) return List.of();
        List<IntegerBounds> sorted = bounds.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(IntegerBounds::getLowerBound))
                .toList();
        if (sorted.isEmpty()) return List.of();

        List<IntegerBounds> merged = new ArrayList<>();
        int low = sorted.get(0).getLowerBound();
        int high = sorted.get(0).getUpperBound();
        for (int i = 1; i < sorted.size(); i++) {
            IntegerBounds next = sorted.get(i);
            if (next.getLowerBound() <= high + 1) {
                high = Math.max(high, next.getUpperBound());
            } else {
                merged.add(new IntegerBounds(low, high));
                low = next.getLowerBound();
                high = next.getUpperBound();
            }
        }
        merged.add(new IntegerBounds(low, high));
        return merged;
    }

    private static final class RectangleSelectionDialog extends JDialog {
        private final SelectionPanel panel;
        private boolean saved;

        private RectangleSelectionDialog(String name, BufferedImage image) {
            super((Frame) null, (name == null || name.isBlank() ? DEFAULT_VIEW_NAME : name) + " Selection", true);
            this.panel = new SelectionPanel(image);

            JButton save = new JButton("Save Selection");
            JButton undo = new JButton("Undo");
            JButton clear = new JButton("Clear");
            JButton cancel = new JButton("Cancel");

            save.addActionListener(e -> {
                saved = true;
                dispose();
            });
            undo.addActionListener(e -> panel.undo());
            clear.addActionListener(e -> panel.clearSelections());
            cancel.addActionListener(e -> {
                saved = false;
                dispose();
            });

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.add(undo);
            buttons.add(clear);
            buttons.add(cancel);
            buttons.add(save);

            setLayout(new BorderLayout());
            add(new JScrollPane(panel), BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setSize(Math.min(1100, image.getWidth() + 48), Math.min(850, image.getHeight() + 96));
            setLocationRelativeTo(null);
        }

        private List<Rectangle> savedRectangles() {
            return saved ? panel.rectangles() : List.of();
        }
    }

    private static final class SelectionPanel extends JPanel {
        private final BufferedImage image;
        private final List<Rectangle> rectangles = new ArrayList<>();
        private Point dragStart;
        private Rectangle activeRect;

        private SelectionPanel(BufferedImage image) {
            this.image = image;
            setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
            setBackground(Color.DARK_GRAY);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = clampPoint(e.getPoint());
                    activeRect = new Rectangle(dragStart);
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart == null) return;
                    Point current = clampPoint(e.getPoint());
                    activeRect = rectangleBetween(dragStart, current);
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragStart == null) return;
                    Point current = clampPoint(e.getPoint());
                    Rectangle finished = rectangleBetween(dragStart, current);
                    if (finished.width > 0 && finished.height > 0) rectangles.add(finished);
                    dragStart = null;
                    activeRect = null;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.drawImage(image, 0, 0, null);

            g.setStroke(new BasicStroke(1.5f));
            for (Rectangle rect : rectangles) drawSelectionRect(g, rect, false);
            if (activeRect != null) drawSelectionRect(g, activeRect, true);
            g.dispose();
        }

        private void drawSelectionRect(Graphics2D g, Rectangle rect, boolean active) {
            Rectangle r = normalizeRect(rect);
            Color fill = active ? new Color(255, 193, 7, 64) : new Color(33, 150, 243, 54);
            Color outline = active ? new Color(255, 143, 0) : new Color(25, 118, 210);
            g.setColor(fill);
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(outline);
            g.drawRect(r.x, r.y, r.width, r.height);
        }

        private Point clampPoint(Point point) {
            int x = Math.max(0, Math.min(point.x, image.getWidth() - 1));
            int y = Math.max(0, Math.min(point.y, image.getHeight() - 1));
            return new Point(x, y);
        }

        private Rectangle rectangleBetween(Point a, Point b) {
            int x = Math.min(a.x, b.x);
            int y = Math.min(a.y, b.y);
            return new Rectangle(x, y, Math.abs(a.x - b.x), Math.abs(a.y - b.y));
        }

        private List<Rectangle> rectangles() {
            return copyRectangles(rectangles);
        }

        private void undo() {
            if (!rectangles.isEmpty()) {
                rectangles.remove(rectangles.size() - 1);
                repaint();
            }
        }

        private void clearSelections() {
            rectangles.clear();
            repaint();
        }
    }

    private static void show(String name, BufferedImage image) {
        if (image == null) return;
        TaskTracker.pause();
        ProgrammingEx.noExcept(() -> new FreeformSelectionManager(image, name).getDrawingAxis());
        TaskTracker.resume();
    }

    private static void showNonBlocking(String name, BufferedImage image) {
        if (image == null) return;
        TaskTracker.pause();
        ProgrammingEx.noExcept(() -> new FreeformSelectionManager(image, name));
        TaskTracker.resume();
    }

    private static void exportImage(String name, BufferedImage image, Path exportPath) {
        if (image == null) return;
        Path target = exportPath != null ? exportPath : defaultExportPath(name);
        ProgrammingEx.noExcept(() -> {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            ImageIO.write(image, "png", target.toFile());
        });
    }

    private static Path defaultExportPath(String name) {
        String exportDir = EXPORT_DIR_PROPERTY;
        if (exportDir == null || exportDir.isBlank()) {
            exportDir = System.getProperty("java.io.tmpdir");
        }
        if (exportDir == null || exportDir.isBlank()) {
            exportDir = ".";
        }
        return Paths.get(exportDir, sanitizeFileName(name) + ".png");
    }

    private static String sanitizeFileName(String rawName) {
        String fallback = DEFAULT_EXPORT_BASENAME;
        if (rawName == null || rawName.isBlank()) return fallback;
        StringBuilder builder = new StringBuilder(rawName.length());
        for (char ch : rawName.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == '.') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        return builder.length() == 0 ? fallback : builder.toString();
    }

    private static BufferedImage layerFastRGB(String name, BufferedImage base, Object item) {
        FastRGB src = ((FastRGB) item).clone();
        return merge(base, src.getImage());
    }

    private static BufferedImage layerBufferedImage(String name, BufferedImage base, Object item) {
        return merge(base, cloneImage((BufferedImage) item));
    }

    private static BufferedImage layerImageHandler(String name, BufferedImage base, Object item) {
        ImageHandler ih = (ImageHandler) item;
        ih.loadGuiImage();
        return layerBufferedImage(name, base, ih.getGuiImage());
    }

    private static BufferedImage layerShapeBounds(String name, BufferedImage base, Object item) {
        ShapeBounds bounds = (ShapeBounds) item;
        if (bounds == null) return base;

        TreeMap<Integer, IntegerBounds> boundsMap = ensureBoundsMap(bounds);
        if (boundsMap == null || boundsMap.isEmpty()) return base;

        if (base == null) {
            return renderShapeBounds(boundsMap);
        }

        BufferedImage canvas = ensureEditable(base);
        Graphics2D g = canvas.createGraphics();
        drawShapeBoundsOutline(g, boundsMap, 1.0, 0.0, 0.0);
        g.dispose();
        return canvas;
    }

    private static BufferedImage layerShapeContour(String name, BufferedImage base, Object item) {
        ShapeContour contour = (ShapeContour) item;
        if (contour == null || contour.isEmpty()) return base;

        if (base == null) {
            return renderShapeContour(name, contour);
        }

        BufferedImage overlay = renderShapeContourOverlay(contour, base.getWidth(), base.getHeight());
        if (overlay == null) return base;
        return merge(base, overlay);
    }

    private static BufferedImage layerShapeContours(String name, BufferedImage base, List<ShapeContour> contours) {
        if (contours == null || contours.isEmpty()) return base;

        if (base == null) {
            return renderShapeContours(name, contours);
        }

        BufferedImage overlay = renderShapeContoursOverlay(contours, base.getWidth(), base.getHeight());
        if (overlay == null) return base;
        return merge(base, overlay);
    }

    private static BufferedImage layerPolygon(String name, BufferedImage base, Object item) {
        Polygon poly = (Polygon) item;
        if (poly == null) return base;

        if (base == null) {
            return renderPolygon(poly);
        }

        poly.cache();
        BufferedImage canvas = ensureEditable(base);
        Graphics2D g = canvas.createGraphics();
        drawPolygon(g, poly, 1.0, 0.0, 0.0);
        g.dispose();
        return canvas;
    }

    private static BufferedImage layerRectangle(String name, BufferedImage base, Object item) {
        Rectangle rect = (Rectangle) item;
        Polygon poly = Polygon.of(
                new Line(new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y)),
                new Line(new Point(rect.x, rect.y + rect.height), new Point(rect.x + rect.width, rect.y + rect.height))
        );
        return layerPolygon(name, base, poly);
    }

    private static TreeMap<Integer, IntegerBounds> ensureBoundsMap(ShapeBounds bounds) {
        if (bounds == null) return null;
        TreeMap<Integer, IntegerBounds> map = bounds.getBounds();
        if (map == null || map.isEmpty()) {
            bounds.indexBounds();
            map = bounds.getBounds();
        }
        return map;
    }

    private static BufferedImage renderShapeBounds(TreeMap<Integer, IntegerBounds> boundsMap) {
        if (boundsMap == null || boundsMap.isEmpty()) return null;

        int minX = boundsMap.firstKey();
        int maxX = boundsMap.lastKey();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (IntegerBounds b : boundsMap.values()) {
            if (b == null) continue;
            if (b.getLowerBound() < minY) minY = b.getLowerBound();
            if (b.getUpperBound() > maxY) maxY = b.getUpperBound();
        }

        if (minY == Integer.MAX_VALUE || maxY == Integer.MIN_VALUE) return null;

        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);

        final int MAX_CANVAS = 760;
        final double DEFAULT_SCALE = 8.0;
        final double MAX_SCALE = 32.0;

        double availableX = MAX_CANVAS - DEFAULT_PADDING * 2.0;
        double availableY = MAX_CANVAS - DEFAULT_PADDING * 2.0;
        double scale = Math.min(availableX / spanX, availableY / spanY);
        if (!Double.isFinite(scale) || scale <= 0.0) scale = DEFAULT_SCALE;
        else scale = Math.min(MAX_SCALE, scale);

        int imgW = Math.max(1, (int) Math.round(spanX * scale) + DEFAULT_PADDING * 2);
        int imgH = Math.max(1, (int) Math.round(spanY * scale) + DEFAULT_PADDING * 2);

        BufferedImage bi = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, imgW, imgH);
        g.setComposite(AlphaComposite.SrcOver);

        double shiftX = DEFAULT_PADDING - minX * scale;
        double shiftY = DEFAULT_PADDING - minY * scale;

        drawShapeBoundsOutline(g, boundsMap, scale, shiftX, shiftY);

        g.dispose();
        return bi;
    }

    private static void drawShapeBoundsOutline(
            Graphics2D g,
            TreeMap<Integer, IntegerBounds> boundsMap,
            double scale,
            double shiftX,
            double shiftY
    ) {
        if (g == null || boundsMap == null || boundsMap.isEmpty()) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Stroke originalStroke = g.getStroke();
        Color previousColor = g.getColor();

        float strokeWidth = (float) Math.max(1f, scale / 6.0);
        g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(CONTOUR_OUTLINE_COLOR);

        List<Map.Entry<Integer, IntegerBounds>> entries = new ArrayList<>(boundsMap.entrySet());

        Point prevTop = null;
        Integer prevX = null;
        for (Map.Entry<Integer, IntegerBounds> entry : entries) {
            IntegerBounds b = entry.getValue();
            if (b == null) continue;
            int x = entry.getKey();
            Point currentTop = transformPoint(new Point(x, b.getLowerBound()), scale, shiftX, shiftY);
            if (prevTop != null && prevX != null && (x - prevX) <= 1) {
                g.drawLine(prevTop.x, prevTop.y, currentTop.x, currentTop.y);
            }
            prevTop = currentTop;
            prevX = x;
        }

        Point prevBottom = null;
        prevX = null;
        for (Map.Entry<Integer, IntegerBounds> entry : entries) {
            IntegerBounds b = entry.getValue();
            if (b == null) continue;
            int x = entry.getKey();
            Point currentBottom = transformPoint(new Point(x, b.getUpperBound()), scale, shiftX, shiftY);
            if (prevBottom != null && prevX != null && (x - prevX) <= 1) {
                g.drawLine(prevBottom.x, prevBottom.y, currentBottom.x, currentBottom.y);
            }
            prevBottom = currentBottom;
            prevX = x;
        }

        Set<Integer> verticalColumns = new HashSet<>();
        prevX = null;
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<Integer, IntegerBounds> entry = entries.get(i);
            IntegerBounds b = entry.getValue();
            if (b == null) continue;
            int x = entry.getKey();

            boolean isSegmentStart = prevX == null || (x - prevX) > 1;
            boolean isSegmentEnd = (i == entries.size() - 1);
            if (!isSegmentEnd && entries.get(i + 1).getValue() != null) {
                int nextX = entries.get(i + 1).getKey();
                isSegmentEnd = (nextX - x) > 1;
            }

            if (isSegmentStart) verticalColumns.add(x);
            if (isSegmentEnd) verticalColumns.add(x);

            prevX = x;
        }

        for (Integer column : verticalColumns) {
            IntegerBounds b = boundsMap.get(column);
            if (b == null) continue;
            Point top = transformPoint(new Point(column, b.getLowerBound()), scale, shiftX, shiftY);
            Point bottom = transformPoint(new Point(column, b.getUpperBound()), scale, shiftX, shiftY);
            g.drawLine(top.x, top.y, bottom.x, bottom.y);
        }

        g.setColor(previousColor);
        g.setStroke(originalStroke);
    }

    private static BufferedImage layerPointCollections(String name, BufferedImage base, List<PointCollection> collections) {
        if (collections == null || collections.isEmpty()) return base;
        if (base == null) return renderPointCollections(name, collections);

        BufferedImage canvas = ensureEditable(base);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < collections.size(); i++) {
            PointCollection pc = collections.get(i);
            if (pc == null) continue;
            Color c = colorIncMap.get(i % colorIncMap.size());
            g.setColor(c);
            for (Point p : pc) {
                if (inside(canvas, p)) g.fillRect(p.x, p.y, 1, 1);
            }
        }
        g.dispose();
        return canvas;
    }

    private static BufferedImage layerPointCollection(BufferedImage base, PointCollection collection) {
        if (collection == null || collection.isEmpty()) return base;
        if (base == null) return renderPointCollections("points", List.of(collection));

        BufferedImage canvas = ensureEditable(base);
        Graphics2D g = canvas.createGraphics();
        g.setColor(DEFAULT_POINT_COLOR);
        for (Point p : collection) {
            if (inside(canvas, p)) g.fillRect(p.x, p.y, 1, 1);
        }
        g.dispose();
        return canvas;
    }

    private static BufferedImage renderShapeContour(String name, ShapeContour contour) {
        return renderShapeContourInternal(name, contour, true, 0, 0);
    }

    private static BufferedImage renderShapeContourOverlay(ShapeContour contour, int width, int height) {
        return renderShapeContourInternal(null, contour, false, width, height);
    }

    private static BufferedImage renderShapeContourInternal(
            String name,
            ShapeContour contour,
            boolean standalone,
            int targetWidth,
            int targetHeight
    ) {
        if (contour == null || contour.isEmpty()) return null;

        List<Point> points = contour.toList();
        if (points.isEmpty()) return null;

        String displayName = (name == null || name.isEmpty()) ? "shape_contour" : name;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Point p : points) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
        }

        int width = maxX - minX;
        int height = maxY - minY;
        int spanX = Math.max(1, width);
        int spanY = Math.max(1, height);

        double scale;
        int pad;
        int imgW;
        int imgH;

        if (standalone) {
            final int MAX_CANVAS = 760;
            final double DEFAULT_SCALE = 8.0;
            final double MAX_SCALE = 32.0;

            double availableX = MAX_CANVAS - DEFAULT_PADDING * 2.0;
            double availableY = MAX_CANVAS - DEFAULT_PADDING * 2.0;
            scale = Math.min(availableX / spanX, availableY / spanY);
            if (!Double.isFinite(scale) || scale <= 0.0) scale = DEFAULT_SCALE;
            else scale = Math.min(MAX_SCALE, scale);

            pad = DEFAULT_PADDING;
            imgW = Math.max(1, (int) Math.round(spanX * scale) + pad * 2);
            imgH = Math.max(1, (int) Math.round(spanY * scale) + pad * 2);
        } else {
            if (targetWidth <= 0 || targetHeight <= 0) return null;
            scale = 1.0;
            pad = 0;
            imgW = targetWidth;
            imgH = targetHeight;
        }

        int renderMinX = standalone ? minX : 0;
        int renderMinY = standalone ? minY : 0;

        BufferedImage bi = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g;
        if (standalone) {
            g = populateImage(bi, Color.WHITE);
        } else {
            g = bi.createGraphics();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, imgW, imgH);
            g.setComposite(AlphaComposite.SrcOver);
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Arial", Font.PLAIN, 12));

        int canvasW = bi.getWidth();
        int canvasH = bi.getHeight();

        List<Point2D.Double> mapped = new ArrayList<>(points.size());
        for (Point p : points) {
            double mx = pad + (p.x - renderMinX) * scale;
            double my = pad + (p.y - renderMinY) * scale;
            mapped.add(new Point2D.Double(mx, my));
        }

        Stroke baseStroke = g.getStroke();
        Composite baseComposite = g.getComposite();

        Path2D.Double outline = new Path2D.Double();
        outline.moveTo(mapped.get(0).x, mapped.get(0).y);
        for (int i = 1; i < mapped.size(); i++) {
            Point2D.Double mp = mapped.get(i);
            outline.lineTo(mp.x, mp.y);
        }
        outline.closePath();

        g.setComposite(AlphaComposite.SrcOver.derive(0.20f));
        g.setColor(CONTOUR_FILL_COLOR);
        g.fill(outline);
        g.setComposite(baseComposite);

        float edgeStroke = (float) Math.max(1.0, scale * 0.18);
        g.setStroke(new BasicStroke(edgeStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(CONTOUR_OUTLINE_COLOR);
        g.draw(outline);
        g.setStroke(baseStroke);

        int boxW = (int) Math.round(width * scale);
        int boxH = (int) Math.round(height * scale);
        int boxX = (int) Math.round(pad + (minX - renderMinX) * scale);
        int boxY = (int) Math.round(pad + (minY - renderMinY) * scale);
        g.setColor(GUIDE_BOX_COLOR);
        if (boxW > 0 && boxH > 0) {
            g.drawRect(boxX, boxY, boxW, boxH);
        } else if (boxW == 0 && boxH > 0) {
            g.drawLine(boxX, boxY, boxX, boxY + boxH);
        } else if (boxH == 0 && boxW > 0) {
            g.drawLine(boxX, boxY, boxX + boxW, boxY);
        }

        int pointRadius = Math.max(2, (int) Math.round(scale * 0.20));
        Color nodeColor = new Color(21, 101, 192);
        for (Point2D.Double mp : mapped) {
            int cx = (int) Math.round(mp.x);
            int cy = (int) Math.round(mp.y);
            g.setColor(nodeColor);
            g.fillOval(cx - pointRadius, cy - pointRadius, pointRadius * 2, pointRadius * 2);
        }

        FontMetrics fm = g.getFontMetrics();
        Set<Point> labeled = new HashSet<>();

        Point startPoint = points.get(0);
        int startRadius = Math.max(pointRadius + 1, (int) Math.round(scale * 0.28));
        drawContourMarker(g, startPoint, renderMinX, renderMinY, scale, pad, startRadius, new Color(198, 40, 40), "start", canvasW, canvasH, fm);
        labeled.add(new Point(startPoint.x, startPoint.y));

        Point2D.Double centroidPoint = contour.centroid();
        if (centroidPoint != null) {
            Point centroidPt = new Point((int) Math.round(centroidPoint.x), (int) Math.round(centroidPoint.y));
            int centroidRadius = Math.max(pointRadius, (int) Math.round(scale * 0.26));
            if (labeled.add(new Point(centroidPt.x, centroidPt.y))) {
                drawContourMarker(g, centroidPt, renderMinX, renderMinY, scale, pad, centroidRadius, new Color(255, 87, 34), "centroid", canvasW, canvasH, fm);
            }
        }

        int markerRadius = Math.max(3, (int) Math.round(scale * 0.22));
        Point top = contour.topPoint();
        if (top != null && labeled.add(new Point(top.x, top.y))) {
            drawContourMarker(g, top, renderMinX, renderMinY, scale, pad, markerRadius, new Color(139, 195, 74), "top", canvasW, canvasH, fm);
        }
        Point bottom = contour.bottomPoint();
        if (bottom != null && labeled.add(new Point(bottom.x, bottom.y))) {
            drawContourMarker(g, bottom, renderMinX, renderMinY, scale, pad, markerRadius, new Color(244, 67, 54), "bottom", canvasW, canvasH, fm);
        }
        Point left = contour.leftPoint();
        if (left != null && labeled.add(new Point(left.x, left.y))) {
            drawContourMarker(g, left, renderMinX, renderMinY, scale, pad, markerRadius, new Color(255, 193, 7), "left", canvasW, canvasH, fm);
        }
        Point right = contour.rightPoint();
        if (right != null && labeled.add(new Point(right.x, right.y))) {
            drawContourMarker(g, right, renderMinX, renderMinY, scale, pad, markerRadius, new Color(255, 152, 0), "right", canvasW, canvasH, fm);
        }

        if (standalone) {
            g.setColor(CONTOUR_INFO_COLOR);
            int textY = 18;
            g.drawString("Contour: " + displayName, 12, textY);
            textY += 14;
            g.drawString(String.format(Locale.ROOT, "vertices=%d  perimeter=%.2f  area=%.2f",
                    contour.size(), contour.perimeter(), contour.area()), 12, textY);
            textY += 14;
            g.drawString(String.format(Locale.ROOT, "bounds: x[%d,%d]  y[%d,%d]", minX, maxX, minY, maxY), 12, textY);
        }

        g.dispose();
        return bi;
    }

    private static BufferedImage renderShapeContours(String name, List<ShapeContour> contours) {
        return renderShapeContoursInternal(name, contours, true, 0, 0);
    }

    private static BufferedImage renderShapeContoursOverlay(List<ShapeContour> contours, int width, int height) {
        return renderShapeContoursInternal(null, contours, false, width, height);
    }

    private static BufferedImage renderShapeContoursInternal(
            String name,
            List<ShapeContour> contours,
            boolean standalone,
            int targetWidth,
            int targetHeight
    ) {
        if (contours == null || contours.isEmpty()) return null;

        List<ShapeContour> validContours = contours.stream()
                .filter(Objects::nonNull)
                .filter(c -> !c.isEmpty())
                .toList();
        if (validContours.isEmpty()) return null;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (ShapeContour contour : validContours) {
            for (Point p : contour.toList()) {
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }
        }

        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);

        double scale;
        double shiftX;
        double shiftY;
        int imgW;
        int imgH;

        if (standalone) {
            scale = standaloneShapeScale(spanX, spanY);
            shiftX = DEFAULT_PADDING - minX * scale;
            shiftY = DEFAULT_PADDING - minY * scale;
            imgW = Math.max(1, (int) Math.round(spanX * scale) + DEFAULT_PADDING * 2);
            imgH = Math.max(1, (int) Math.round(spanY * scale) + DEFAULT_PADDING * 2);
        } else {
            if (targetWidth <= 0 || targetHeight <= 0) return null;
            scale = 1.0;
            shiftX = 0.0;
            shiftY = 0.0;
            imgW = targetWidth;
            imgH = targetHeight;
        }

        BufferedImage bi = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g;
        if (standalone) {
            g = populateImage(bi, Color.WHITE);
        } else {
            g = bi.createGraphics();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, imgW, imgH);
            g.setComposite(AlphaComposite.SrcOver);
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Arial", Font.PLAIN, 12));

        for (int i = 0; i < validContours.size(); i++) {
            ShapeContour contour = validContours.get(i);
            Color color = colorIncMap.get(i % colorIncMap.size());
            drawColoredContour(g, contour, scale, shiftX, shiftY, color, standalone);
        }

        if (standalone) {
            g.setColor(CONTOUR_INFO_COLOR);
            int textY = 18;
            String displayName = (name == null || name.isBlank()) ? "shape_contours" : name;
            g.drawString("Contours: " + displayName, 12, textY);
            textY += 14;
            g.drawString(String.format(Locale.ROOT, "count=%d  bounds: x[%d,%d]  y[%d,%d]",
                    validContours.size(), minX, maxX, minY, maxY), 12, textY);
        }

        g.dispose();
        return bi;
    }

    private static void drawColoredContour(
            Graphics2D g,
            ShapeContour contour,
            double scale,
            double shiftX,
            double shiftY,
            Color baseColor,
            boolean drawGuideBox
    ) {
        if (g == null || contour == null || contour.isEmpty()) return;

        List<Point> points = contour.toList();
        if (points.isEmpty()) return;

        List<Point2D.Double> mapped = new ArrayList<>(points.size());
        for (Point p : points) {
            mapped.add(new Point2D.Double(
                    p.x * scale + shiftX,
                    p.y * scale + shiftY
            ));
        }

        Path2D.Double outline = new Path2D.Double();
        outline.moveTo(mapped.get(0).x, mapped.get(0).y);
        for (int i = 1; i < mapped.size(); i++) {
            Point2D.Double mp = mapped.get(i);
            outline.lineTo(mp.x, mp.y);
        }
        outline.closePath();

        Stroke previousStroke = g.getStroke();
        Composite previousComposite = g.getComposite();
        Color previousColor = g.getColor();

        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(colorWithAlpha(baseColor, 51));
        g.fill(outline);

        float edgeStroke = (float) Math.max(1.0, scale * 0.18);
        g.setStroke(new BasicStroke(edgeStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(baseColor);
        g.draw(outline);

        int pointRadius = Math.max(2, (int) Math.round(scale * 0.20));
        Color nodeColor = baseColor.darker();
        g.setColor(nodeColor);
        for (Point2D.Double mp : mapped) {
            int cx = (int) Math.round(mp.x);
            int cy = (int) Math.round(mp.y);
            g.fillOval(cx - pointRadius, cy - pointRadius, pointRadius * 2, pointRadius * 2);
        }

        if (drawGuideBox) {
            int minX = points.stream().mapToInt(p -> p.x).min().orElse(0);
            int maxX = points.stream().mapToInt(p -> p.x).max().orElse(minX);
            int minY = points.stream().mapToInt(p -> p.y).min().orElse(0);
            int maxY = points.stream().mapToInt(p -> p.y).max().orElse(minY);

            int boxX = (int) Math.round(minX * scale + shiftX);
            int boxY = (int) Math.round(minY * scale + shiftY);
            int boxW = (int) Math.round((maxX - minX) * scale);
            int boxH = (int) Math.round((maxY - minY) * scale);

            g.setColor(colorWithAlpha(baseColor.darker(), 110));
            if (boxW > 0 && boxH > 0) {
                g.drawRect(boxX, boxY, boxW, boxH);
            } else if (boxW == 0 && boxH > 0) {
                g.drawLine(boxX, boxY, boxX, boxY + boxH);
            } else if (boxH == 0 && boxW > 0) {
                g.drawLine(boxX, boxY, boxX + boxW, boxY);
            }
        }

        g.setColor(previousColor);
        g.setStroke(previousStroke);
        g.setComposite(previousComposite);
    }

    private static Color colorWithAlpha(Color color, int alpha) {
        if (color == null) return new Color(0, 0, 0, Math.max(0, Math.min(255, alpha)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static BufferedImage renderPolygon(Polygon poly) {
        if (poly == null) return null;

        poly.cache();

        int pad = (int) Math.max(Math.max(poly.width(), poly.height()) * POLYGON_SCALE / 10d, 20 * POLYGON_SCALE);
        int imgW = (int) (poly.width() * POLYGON_SCALE) + pad * 2;
        int imgH = (int) (poly.height() * POLYGON_SCALE) + pad * 2;

        BufferedImage bi = new BufferedImage(Math.max(1, imgW), Math.max(1, imgH), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
        g.setComposite(AlphaComposite.SrcOver);

        int shiftX = pad - (int) Math.round(poly.leftX() * POLYGON_SCALE);
        int shiftY = pad - (int) Math.round(poly.bottomY() * POLYGON_SCALE);
        drawPolygon(g, poly, POLYGON_SCALE, shiftX, shiftY);

        g.dispose();
        return bi;
    }

    private static void drawPolygon(Graphics2D g, Polygon poly, double scale, double shiftX, double shiftY) {
        if (g == null || poly == null) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Stroke originalStroke = g.getStroke();
        BasicStroke mainStroke = new BasicStroke(Math.max(1f, (float) (scale / 6.0)));
        g.setStroke(mainStroke);

        List<Line> top = poly.top();
        List<Line> bottom = poly.bottom();
        List<Line> intersectSegments = new ArrayList<>();
        List<Point> intersectPts = new ArrayList<>();

        for (Line t : top) {
            for (Line b : bottom) {
                if (!t.intersects(b)) continue;

                Point ip = t.intersectsAt(b);
                if (ip != null) {
                    if (!intersectPts.contains(ip)) intersectPts.add(ip);
                    continue;
                }

                if (t.onlyVert() && b.onlyVert() && t.start().x == b.start().x) {
                    int oy1 = Math.max(t.lowest().y, b.lowest().y);
                    int oy2 = Math.min(t.highest().y, b.highest().y);
                    if (oy1 <= oy2)
                        intersectSegments.add(new Line(new Point(t.start().x, oy1), new Point(t.start().x, oy2)));
                    continue;
                }

                if (Math.abs(com.elijahsarte.celtools.main.util.MathEx.cross(t, b)) > 1e-9) continue;

                int ox1 = Math.max(t.startX(), b.startX());
                int ox2 = Math.min(t.endX(), b.endX());
                if (ox1 <= ox2) {
                    intersectSegments.add(new Line(t.yAt(ox1), t.yAt(ox2)));
                    continue;
                }

                int oy1 = Math.max(t.lowest().y, b.lowest().y);
                int oy2 = Math.min(t.highest().y, b.highest().y);
                if (oy1 <= oy2) {
                    intersectSegments.add(new Line(t.xAt(oy1), t.xAt(oy2)));
                }
            }
        }

        for (Line l : top)
            drawLine(g, transformLine(l, scale, shiftX, shiftY), Color.GREEN);
        for (Line l : bottom)
            drawLine(g, transformLine(l, scale, shiftX, shiftY), Color.RED);

        if (!top.isEmpty() && !bottom.isEmpty()) {
            drawLine(
                    g,
                    transformLine(new Line(top.get(0).start(), bottom.get(0).start()), scale, shiftX, shiftY),
                    Color.BLUE);
            drawLine(
                    g,
                    transformLine(
                            new Line(
                                    com.elijahsarte.celtools.main.util.CollectionsEx.lastElem(top).end(),
                                    com.elijahsarte.celtools.main.util.CollectionsEx.lastElem(bottom).end()),
                            scale,
                            shiftX,
                            shiftY),
                    Color.BLUE);
        }

        if (!intersectSegments.isEmpty()) {
            Stroke previousStroke = g.getStroke();
            float thickerWidth = Math.max(1f, mainStroke.getLineWidth() + 1f);
            g.setStroke(new BasicStroke(thickerWidth));
            for (Line seg : intersectSegments)
                drawLine(g, transformLine(seg, scale, shiftX, shiftY), BROWN);
            g.setStroke(previousStroke);
        }

        if (!intersectPts.isEmpty()) {
            Color previousColor = g.getColor();
            g.setColor(BROWN);
            int markerRadius = Math.max(1, (int) Math.round(scale / 4.0));
            int d = markerRadius * 2;
            for (Point point : intersectPts) {
                Point scaled = transformPoint(point, scale, shiftX, shiftY);
                g.fillOval(scaled.x - markerRadius, scaled.y - markerRadius, d, d);
            }
            g.setColor(previousColor);
        }

        g.setStroke(originalStroke);
    }

    private static Line transformLine(Line line, double scale, double shiftX, double shiftY) {
        Line transformed = line;
        if (scale != 1.0d) transformed = transformed.scale(scale, scale);
        if (shiftX != 0.0d || shiftY != 0.0d) transformed = transformed.translate(shiftX, shiftY);
        return transformed;
    }

    private static Point transformPoint(Point point, double scale, double shiftX, double shiftY) {
        int x = (int) Math.round(point.x * scale + shiftX);
        int y = (int) Math.round(point.y * scale + shiftY);
        return new Point(x, y);
    }

    private static BufferedImage merge(BufferedImage base, BufferedImage overlay) {
        if (overlay == null) return cloneImage(base);
        if (base == null) return cloneImage(overlay);
        int w = Math.max(base.getWidth(), overlay.getWidth());
        int h = Math.max(base.getHeight(), overlay.getHeight());
        BufferedImage merged = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = merged.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(base, 0, 0, null);
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(overlay, 0, 0, null);
        g.dispose();
        return merged;
    }

    private static BufferedImage cloneImage(BufferedImage src) {
        if (src == null) return null;
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static BufferedImage ensureEditable(BufferedImage src) {
        return cloneImage(src);
    }

    private static boolean inside(BufferedImage img, Point p) {
        return img != null && p.x >= 0 && p.x < img.getWidth() && p.y >= 0 && p.y < img.getHeight();
    }

    /**
     * Annotate small point sets with labels in pixel coordinates.
     * Works for java.awt.Point, Point[], Collection<Point>, PointCollection, or ShapeContour.
     */
    @SuppressWarnings("unchecked")
    private static void annotateIfSparse(Graphics2D g, Object obj, AffineTransform worldToScreen) {
        List<Point> pts = extractPoints(obj);
        if (pts == null) return;
        if (pts.size() >= LABEL_THRESHOLD) return;

        g.setFont(LABEL_FONT);
        g.setColor(LABEL_COLOR);

        final int dx = 4, dy = -4;
        for (int i = 0; i < pts.size(); i++) {
            Point p = pts.get(i);
            Point2D sp = worldToScreen == null ? p : worldToScreen.transform(p, null);
            int sx = (int) Math.round(sp.getX());
            int sy = (int) Math.round(sp.getY());

            g.fillOval(sx - 2, sy - 2, 4, 4);
            String label = (pts.size() == 1) ? "point" : ("point " + i);
            g.drawString(label, sx + dx, sy + dy);
        }
    }

    /**
     * Try to extract a list of java.awt.Point from many object types.
     */
    @SuppressWarnings("unchecked")
    private static List<Point> extractPoints(Object obj) {
        if (obj == null) return null;

        if (obj instanceof Point p) return List.of(p);

        if (obj instanceof Point[] arr) return Arrays.asList(arr);

        if (obj instanceof ShapeContour contour && !contour.isEmpty()) {
            return contour.toList();
        }

        if (obj instanceof Collection<?> c && !c.isEmpty() && c.iterator().next() instanceof Point)
            return ((Collection<Point>) c).stream().toList();

        try {
            if (obj.getClass().getSimpleName().equals("PointCollection")) {
                Iterable<?> it = (Iterable<?>) obj;
                List<Point> out = new ArrayList<>();
                for (Object e : it) if (e instanceof Point p) out.add(p);
                if (!out.isEmpty()) return out;
            }
        } catch (Throwable ignored) {}

        for (String m : new String[]{"points", "toPoints", "asPoints"}) {
            try {
                var mm = obj.getClass().getMethod(m);
                Object r = mm.invoke(obj);
                if (r instanceof Collection<?> rc && !rc.isEmpty() && rc.iterator().next() instanceof Point)
                    return new ArrayList<>((Collection<Point>) rc);
                if (r instanceof Point[] parr) return Arrays.asList(parr);
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static List<PointCollection> castPointCollectionList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().filter(PointCollection.class::isInstance).map(PointCollection.class::cast).toList();
        }
        return List.of();
    }

    private static List<ShapeContour> castShapeContourList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().filter(ShapeContour.class::isInstance).map(ShapeContour.class::cast).toList();
        }
        return List.of();
    }

    private static boolean iterableContains(Iterable<?> iterable, Class<?> type) {
        Iterator<?> it = iterable.iterator();
        if (!it.hasNext()) return false;
        while (it.hasNext()) {
            Object next = it.next();
            if (!type.isInstance(next)) return false;
        }
        return true;
    }

    private static List<PointCollection> iterableToPointCollections(Iterable<?> iterable) {
        List<PointCollection> list = new ArrayList<>();
        for (Object obj : iterable) {
            if (obj instanceof PointCollection pc) {
                list.add(pc);
            }
        }
        return list;
    }

    private static List<ShapeContour> iterableToShapeContours(Iterable<?> iterable) {
        List<ShapeContour> list = new ArrayList<>();
        for (Object obj : iterable) {
            if (obj instanceof ShapeContour contour) {
                list.add(contour);
            }
        }
        return list;
    }

    private static List<Point> collectionToPoints(Collection<Point> collection) {
        return collection == null ? List.of() : new ArrayList<>(collection);
    }

    private static BufferedImage renderPointCollections(String name, List<PointCollection> collections) {
        if (collections == null || collections.isEmpty()) return null;

        final int MAX_IMG_SIZE = 1000;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        boolean hasPoint = false;
        for (PointCollection pc : collections) {
            if (pc == null) continue;
            for (Point p : pc) {
                hasPoint = true;
                if (p.x < minX) minX = p.x;
                if (p.x > maxX) maxX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.y > maxY) maxY = p.y;
            }
        }
        if (!hasPoint) return null;

        int rangeX = maxX - minX, rangeY = maxY - minY;

        Graphics2D textMetricsG = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        textMetricsG.setFont(new Font("Arial", Font.PLAIN, 10));
        int maxLabelWidth = 0;
        for (PointCollection pc : collections) {
            if (pc == null) continue;
            for (Point p : pc) {
                maxLabelWidth = Math.max(maxLabelWidth,
                        textMetricsG.getFontMetrics().stringWidth("(" + p.x + ", " + p.y + ")"));
            }
        }
        textMetricsG.dispose();

        if (rangeX == 0 && rangeY == 0) {
            int imgW = 100, imgH = 100;
            BufferedImage bi = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = populateImage(bi, Color.WHITE);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("Arial", Font.PLAIN, 10));

            int cx = imgW / 2, cy = imgH / 2, n = collections.size();
            for (int i = 0; i < n; i++) {
                PointCollection pc = collections.get(i);
                if (pc == null) continue;
                Iterator<Point> it = pc.iterator();
                if (!it.hasNext()) continue;
                Point only = it.next();

                Color c = colorIncMap.get(i % colorIncMap.size());
                g.setColor(c);
                g.fillOval(cx - 4 + (i - n / 2) * 6, cy - 4 + ((i % 2 == 0) ? 0 : 4), 8, 8);

                String label = "#" + i + " (" + only.x + ", " + only.y + ")";
                int textWidth = g.getFontMetrics().stringWidth(label);
                g.setColor(Color.BLACK);
                g.drawString(label, Math.max(0, Math.min(cx - textWidth / 2 + (i - n / 2) * 6, imgW - textWidth)),
                        Math.max(10, cy - 4 + ((i % 2 == 0) ? 0 : 4) - 2));
            }

            g.dispose();
            return bi;
        }

        int limX = Math.max(1, rangeX);
        int limY = Math.max(1, rangeY);
        double scale = Math.min(
                (double) (MAX_IMG_SIZE - 2 * 4 - 2 * 10) / limX,
                (double) (MAX_IMG_SIZE - 2 * 4 - 2 * 10) / limY) * 0.9;

        BufferedImage bi = new BufferedImage(
                Math.max(1, (int) (limX * scale) + 2 * 4 + maxLabelWidth),
                Math.max(1, (int) (limY * scale) + 2 * 4 + 10 * 2),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = populateImage(bi, Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Arial", Font.PLAIN, 10));

        int r = 4;
        for (int i = 0; i < collections.size(); i++) {
            PointCollection pc = collections.get(i);
            if (pc == null) continue;
            Color c = colorIncMap.get(i % colorIncMap.size());
            g.setColor(c);

            for (Point p : pc) {
                int x = (int) ((p.x - minX) * scale) + 4;
                int y = (int) ((p.y - minY) * scale) + 4;
                g.fillOval(x - r, y - r, r * 2, r * 2);

                String label = "(" + p.x + ", " + p.y + ")";
                int textWidth = g.getFontMetrics().stringWidth(label);
                g.setColor(Color.BLACK);
                g.drawString(label, Math.max(0, Math.min(x - textWidth / 2, bi.getWidth() - textWidth)),
                        Math.max(10, y - r - 2));
                g.setColor(c);
            }
        }

        g.dispose();
        return bi;
    }

    private static void drawContourMarker(
            Graphics2D g,
            Point point,
            int minX,
            int minY,
            double scale,
            int pad,
            int radius,
            Color fill,
            String label,
            int canvasW,
            int canvasH,
            FontMetrics fm
    ) {
        if (point == null) return;
        int cx = (int) Math.round(pad + (point.x - minX) * scale);
        int cy = (int) Math.round(pad + (point.y - minY) * scale);

        Stroke previous = g.getStroke();
        g.setColor(fill);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        g.setColor(fill.darker());
        g.setStroke(new BasicStroke(Math.max(1f, radius / 3f)));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setStroke(previous);

        int textWidth = fm.stringWidth(label);
        int textX = cx + radius + 4;
        if (textX + textWidth > canvasW - 4) textX = cx - radius - 4 - textWidth;
        textX = Math.max(4, textX);

        int ascent = fm.getAscent();
        int textY = cy - radius - 4;
        if (textY < ascent) textY = Math.min(canvasH - 4, cy + radius + ascent);

        g.setColor(Color.DARK_GRAY);
        g.drawString(label, textX, textY);
    }

    public static void visM(String name, List<PointCollection> collections) {
        show(name, renderPointCollections(name, collections));
    }

    public static void visM(List<PointCollection> collections) {
        visM(DEFAULT_VIEW_NAME, collections);
    }
}