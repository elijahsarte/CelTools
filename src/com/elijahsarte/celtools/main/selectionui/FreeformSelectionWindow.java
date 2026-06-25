package com.elijahsarte.celtools.main.selectionui;

// https://stackoverflow.com/questions/20177596/freehand-drawing-java

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.function.blocks.switchloop.ConditionalCase;
import com.elijahsarte.celtools.main.util.function.blocks.switchloop.SwitchIf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// cropping from: https://github.com/lewiswhitaker1/ImageCropToSquare/blob/main/src/me/lewis/cropper/ImageCropper.java
public class FreeformSelectionWindow extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    private final BufferedImage image;
    private final SelectionTransform transform;
    private final SelectionMode selectionMode;
    private final boolean freeformDraw;
    private final boolean multiRectangleSelection;
    private final boolean pointSelection;

    public enum SelectionMode {
        FREEFORM_DRAW,
        CROP_BOX,
        MULTI_RECTANGLE,
        POINT,
        MULTI_POINT,
        LABELED_MULTI_POINT
    }

    public enum ToolMode {
        NORMAL,
        COLOR_PICKER
    }

    public enum PointEditMode {
        ADD,
        REMOVE
    }

    private ToolMode toolMode = ToolMode.NORMAL;
    private PointEditMode pointEditMode = PointEditMode.ADD;
    private Point colorPickerPixel = null;
    private Point hoveredPoint = null;
    private Point selectedPoint = null;
    private final List<Point> selectedPoints = new ArrayList<>();
    private final Map<Point, String> labeledSelectedPoints = new LinkedHashMap<>();
    private final List<String> pointLabels;
    private Cursor colorPickerCursor = null;
    private Consumer<Color> colorPickListener;

    // Freeform drawing fields
    // drawingAxis is for tracking object borders, drawingPoints is for actually drawing the shape correctly
    private static final int DEFAULT_DRAWING_POINT_CAPACITY = 100000;

    private final Map<Double, List<Double>> drawingAxis = new HashMap<>();
    private int drawingIndex = 0;
    private Point[] drawingPoints = new Point[DEFAULT_DRAWING_POINT_CAPACITY];


    // Cropbox drawing fields
    private Rectangle cropBox;
    private final List<Rectangle> selectedRectangles = new ArrayList<>();
    private int activeRectangleIndex = -1;
    private boolean drawingNewRectangle = false;
    private Point startPoint, draggingPoint;

    private boolean mouseInWindow = true;
    private boolean isDragging = false;
    private boolean isResizing = false;
    private String currentResizerHandle = "";

    private enum ResizeDir {
        NW(true,  false,  true,  false),
        NE(true,  false, false, true),
        SW(false, true,  true,  false),
        SE(false, true,  false, true),
        N(true,  false, false, false),
        S(false, true,  false, false),
        E(false, false, false, true),
        W(false, false,  true,  false);

        final boolean north, south, west, east;
        ResizeDir(boolean north, boolean south, boolean west, boolean east) {
            this.north = north;
            this.south = south;
            this.west  = west;
            this.east  = east;
        }
    }

    private final Map<String, Rectangle> resizerHandles = new HashMap<>();
    private final Map<String, MouseMotionListener> resizerHandleListeners = new HashMap<>();

    private final Color cropBoxColor = Color.WHITE;
    private final Color resizerSquareColor = Color.BLACK;
    private final int resizerHandleSize = 10;



    private double zoomFactor = 1.0;
    private boolean autoFit = false;
    private boolean userHasZoomed = false;

    private int imageDrawX = 0;
    private int imageDrawY = 0;


    // zoom anchor latch state
    // The first wheel tick of a zoom gesture records an image-space anchor and viewport-space cursor point.
    // All further ticks in that same gesture keep using that same anchor instead of re-centering over and over.
    private static final long ZOOM_GESTURE_RESET_NANOS = 350_000_000L;
    private boolean zoomAnchorActive = false;
    private double zoomAnchorImageX;
    private double zoomAnchorImageY;
    private Point zoomAnchorViewportPoint = null;
    private long lastWheelNanos = 0L;


    public FreeformSelectionWindow(BufferedImage image, SelectionTransform transform, SelectionMode selectionMode, List<String> pointLabels) {
        this.image = image;
        this.transform = transform;
        SelectionMode resolvedMode = selectionMode == null ? SelectionMode.CROP_BOX : selectionMode;
        this.selectionMode = resolvedMode;
        this.freeformDraw = resolvedMode == SelectionMode.FREEFORM_DRAW;
        this.multiRectangleSelection = resolvedMode == SelectionMode.MULTI_RECTANGLE;
        this.pointSelection = isPointSelectionMode(resolvedMode);
        this.pointLabels = pointLabels == null ? new ArrayList<>() : new ArrayList<>(pointLabels);

        this.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        this.setOpaque(true);
        this.setFocusable(true);
        this.setBackground(Color.WHITE);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addMouseWheelListener(this);

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateSizeForZoom();
            }
        });

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_RELEASED &&
                        (e.getKeyCode() == KeyEvent.VK_ALT || e.getKeyCode() == KeyEvent.VK_ALT_GRAPH)) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (FreeformSelectionWindow.this.isShowing()) {
                                FreeformSelectionWindow.this.requestFocusInWindow();
                            }
                        }
                    });
                }
                return false;
            }
        });
    }
    public FreeformSelectionWindow(BufferedImage image, SelectionTransform transform, SelectionMode selectionMode) {
        this(image, transform, selectionMode, null);
    }
    public FreeformSelectionWindow(BufferedImage image, SelectionTransform transform, boolean freeformDraw) {
        this(image, transform, freeformDraw ? SelectionMode.FREEFORM_DRAW : SelectionMode.CROP_BOX);
    }
    public FreeformSelectionWindow(BufferedImage image, SelectionTransform transform) {
        this(image, transform, false);
    }

    private void computeDrawOffsets() {
        imageDrawX = 0;
        imageDrawY = 0;
    }

    private void drawPoints(Graphics2D g2, double zoom, int offsetX, int offsetY) {
        for (int i = 0; i < this.drawingIndex - 1; i++) {
            Point a = drawingPoints[i];
            Point b = drawingPoints[i + 1];
            if (a == null || b == null) continue;
            int ax = offsetX + (int)Math.round(a.x * zoom);
            int ay = offsetY + (int)Math.round(a.y * zoom);
            int bx = offsetX + (int)Math.round(b.x * zoom);
            int by = offsetY + (int)Math.round(b.y * zoom);
            g2.drawLine(ax, ay, bx, by);
        }
    }

    private void drawBaseImage(Graphics2D g2, double zoom, int offsetX, int offsetY) {
        int scaledW = (int)Math.round(image.getWidth() * zoom);
        int scaledH = (int)Math.round(image.getHeight() * zoom);
        g2.drawImage(image, offsetX, offsetY, scaledW, scaledH, this);
    }

    private void drawComponents(Graphics2D g2, double zoom, int offsetX, int offsetY, int canvasWidth, int canvasHeight) {
        if (freeformDraw) {
            this.drawPoints(g2, zoom, offsetX, offsetY);
            return;
        }
        if (multiRectangleSelection) {
            drawMultiRectangleComponents(g2, zoom, offsetX, offsetY);
            return;
        }
        if (cropBox == null) {
            return;
        }

        g2.setColor(new Color(0, 0, 0, 150));

        int cx = offsetX + (int)Math.round(cropBox.x * zoom);
        int cy = offsetY + (int)Math.round(cropBox.y * zoom);
        int cwidth = (int)Math.round(cropBox.width * zoom);
        int cheight = (int)Math.round(cropBox.height * zoom);

        g2.fillRect(0, 0, cx, canvasHeight);
        g2.fillRect(cx + cwidth, 0, canvasWidth - cx - cwidth, canvasHeight);
        g2.fillRect(cx, 0, cwidth, cy);
        g2.fillRect(cx, cy + cheight, cwidth, canvasHeight - cy - cheight);

        g2.setColor(cropBoxColor);
        g2.drawRect(cx, cy, cwidth, cheight);

        g2.setColor(resizerSquareColor);
        makeResizerSquares();
        for (Rectangle handle : resizerHandles.values()) {
            int hw = Math.max(1, (int)Math.round(handle.width * zoom));
            int hh = Math.max(1, (int)Math.round(handle.height * zoom));
            int hx = offsetX + (int)Math.round(handle.x * zoom);
            int hy = offsetY + (int)Math.round(handle.y * zoom);
            g2.fillRect(hx, hy, hw, hh);
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        g.clearRect(0, 0, getWidth(), getHeight());
        Graphics2D g2 = (Graphics2D) g;

        computeDrawOffsets();
        drawBaseImage(g2, zoomFactor, imageDrawX, imageDrawY);
        drawComponents(g2, zoomFactor, imageDrawX, imageDrawY, getWidth(), getHeight());
        drawPointSelectionOverlay(g2, zoomFactor, imageDrawX, imageDrawY);
        drawColorPickerOverlay(g2, zoomFactor, imageDrawX, imageDrawY);
    }

    public void mouseDragged(MouseEvent e) {

        int ex = e.getX();
        int ey = e.getY();

        if (toolMode == ToolMode.COLOR_PICKER) {
            updateColorPickerHover(e);
            return;
        }
        if (pointSelection) {
            updatePointSelectionHover(e);
            return;
        }

        double newXd = (double)(ex - imageDrawX) / zoomFactor;
        double newYd = (double)(ey - imageDrawY) / zoomFactor;

        if (freeformDraw) {
            double newX = newXd, newY = newYd;
            if (newX < 0 || newY < 0 || newX >= image.getWidth() || newY >= image.getHeight()) return;
            if (transform != null && transform.bounded()) {
                if (!transform.included(new Point((int)newX, (int)newY))) {
                    return;
                }
            }

            CollectionsEx.listPut(drawingAxis, transform.applyX(newX), transform.applyY(newY));

            drawingPoints[drawingIndex] = new Point((int) newX, (int) newY);
            drawingIndex++;
            repaint();
            return;
        }

        if (multiRectangleSelection) {
            handleMultiRectangleDrag(e, newXd, newYd);
            return;
        }

        if (!mouseInWindow) return;
        if (isResizing) {
            MouseMotionListener l = resizerHandleListeners.get(currentResizerHandle);
            if (l != null) {
                l.mouseDragged(e);
            }
            if (transform != null && transform.bounded() && cropBox != null) {
                Rectangle clamped = transform.clamp(cropBox);
                cropBox.setBounds(clamped);
            }
            revalidate();
            repaint();
            return;
        }
        if (isDragging) {

            double offsetX = newXd - draggingPoint.getX();
            double offsetY = newYd - draggingPoint.getY();
            int newX = (int) (cropBox.getX() + offsetX);
            int newY = (int) (cropBox.getY() + offsetY);

            newX = Math.max(0, Math.min(newX, image.getWidth() - cropBox.width));
            newY = Math.max(0, Math.min(newY, image.getHeight() - cropBox.height));

            Rectangle candidate = new Rectangle(newX, newY, cropBox.width, cropBox.height);
            if (transform != null && transform.bounded()) {
                candidate = transform.clamp(candidate);
            }

            cropBox.setBounds(candidate);

            draggingPoint = new Point((int)(newXd), (int)(newYd));

            revalidate();
            repaint();
            return;

        }

        int x = (int)Math.min(startPoint.x, newXd), y = (int)Math.min(startPoint.y, newYd);
        int width = (int)Math.abs(startPoint.x - newXd), height = (int)Math.abs(startPoint.y - newYd);

        cropBox.setBounds(x, y, width, height);

        if (transform != null && transform.bounded()) {
            Rectangle clamped = transform.clamp(cropBox);
            cropBox.setBounds(clamped);
        }

        revalidate();
        repaint();

    }

    public void mousePressed(MouseEvent e) {
//        requestFocusInWindow();
//        int ex = e.getX();
//        int ey = e.getY();
//
//        double mx = (double)(ex - imageDrawX) / zoomFactor;
//        double my = (double)(ey - imageDrawY) / zoomFactor;
//
//        // Treat touch events as pan/zoom only. Do not start drawing or box selection.
//        if (isTouchLikeInput(e) && initTouchPanIfPossible(e)) {
//            return;
//        }
        requestFocusInWindow();
        if (toolMode == ToolMode.COLOR_PICKER) {
            updateColorPickerHover(e);
            emitColorPick(e);
            return;
        }
        if (pointSelection) {
            updatePointSelectionHover(e);
            handlePointSelectionClick(e);
            return;
        }
        int ex = e.getX();
        int ey = e.getY();

        double mx = (double)(ex - imageDrawX) / zoomFactor;
        double my = (double)(ey - imageDrawY) / zoomFactor;

        if (freeformDraw) {
            mouseDragged(e);
            return;
        }

        if (mx < 0 || my < 0 || mx >= image.getWidth() || my >= image.getHeight()) {
            return;
        }

        if (multiRectangleSelection) {
            handleMultiRectanglePress(mx, my);
            return;
        }

        if (cropBox != null) {

            if (cropBox.contains(mx, my)) {
                isDragging = true;
                draggingPoint = new Point((int)mx, (int)my);
                return;
            }
            for (Map.Entry<String, Rectangle> entry : resizerHandles.entrySet()) {
                Rectangle handleRect = resizerHandles.get(entry.getKey());
                if (handleRect != null && handleRect.contains(mx, my)) {
                    currentResizerHandle = entry.getKey();
                    isResizing = true;
                    return;
                }
            }

        }

        isResizing = false;
        isDragging = false;

        int x = (int)mx, y = (int)my;
        this.startPoint = new Point(x, y);
        if (toolMode == ToolMode.NORMAL) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }

        if (cropBox == null) {
            cropBox = new Rectangle(x, y, 0, 0);
        } else {
            cropBox.setRect(x, y, 0, 0);
        }

        revalidate();
        repaint();

    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (isZoomGesture(e)) {
            double rotation = e.getPreciseWheelRotation();
            if (rotation == 0) rotation = e.getWheelRotation();
            if (rotation != 0) {
                double oldZoom = zoomFactor;
                double scale = Math.pow(1.1, -rotation);
                double targetZoom = oldZoom * scale;

                Container anc = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                JViewport viewport = anc instanceof JScrollPane sp ? sp.getViewport() : null;

                long now = System.nanoTime();
                boolean anchorExpired = !zoomAnchorActive || (now - lastWheelNanos) > ZOOM_GESTURE_RESET_NANOS;
                if (anchorExpired) {
                    if (!captureZoomAnchor(e, viewport)) {
                        resetZoomAnchor();
                    }
                }
                lastWheelNanos = now;

                setZoomFactor(targetZoom);   // updates preferred size + revalidate internally

                // ensure scrollbars update, as in the old file
                if (anc instanceof JScrollPane sp) {
                    sp.revalidate();
                    sp.repaint();
                }

                if (zoomAnchorActive) {
                    adjustViewportToAnchor(zoomAnchorImageX, zoomAnchorImageY, zoomAnchorViewportPoint);
                }

                requestFocusInWindow();
            }
            e.consume();
            return;
        }
        resetZoomAnchor();
        // let non-zoom gestures scroll normally
    }



    public void updateSizeForZoom() {
        int w = Math.max(1, (int) Math.round(image.getWidth() * zoomFactor));
        int h = Math.max(1, (int) Math.round(image.getHeight() * zoomFactor));
        this.setPreferredSize(new Dimension(w, h));
        this.revalidate();
        computeDrawOffsets();
    }
    public void adjustZoom(int wheelRotation) {
        double factor = getZoomFactor();
        if (wheelRotation < 0) {
            factor *= 1.1; // zoom in
        } else if (wheelRotation > 0) {
            factor /= 1.1; // zoom out
        }
        setZoomFactor(factor);
    }
    private void setZoomFactor(double z) {
        double clamped = Math.max(0.001, z);
        double old = zoomFactor;
        if (Math.abs(clamped - old) < 1e-12) {
            return;
        }
        zoomFactor = clamped;
        userHasZoomed = true;
        firePropertyChange("zoom", old, zoomFactor);
        updateSizeForZoom();
        repaint();
    }

    private boolean isZoomGesture(MouseWheelEvent e) {
        if (e.isControlDown() || e.isMetaDown()) {
            return true;
        }
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        if ((e.getModifiersEx() & shortcutMask) != 0) {
            return true;
        }
        if (e.isAltDown()) {
            return true;
        }
        if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
            try {
                if (e.getScrollAmount() == 0 && e.getWheelRotation() == 0 && Math.abs(e.getPreciseWheelRotation()) > 0) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    private void adjustViewportToAnchor(double imageX, double imageY, Point viewportMouse) {
        Container anc = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (!(anc instanceof JScrollPane)) {
            return;
        }
        JScrollPane sp = (JScrollPane) anc;
        JViewport viewport = sp.getViewport();
        if (viewport == null || viewportMouse == null) {
            return;
        }

        double zoom = this.zoomFactor;
        double componentX = imageDrawX + imageX * zoom;
        double componentY = imageDrawY + imageY * zoom;

        int newViewX = (int) Math.round(componentX - viewportMouse.x);
        int newViewY = (int) Math.round(componentY - viewportMouse.y);

        Dimension extent = viewport.getExtentSize();
        Dimension viewSize = getPreferredSize();
        int maxX = Math.max(0, viewSize.width - extent.width);
        int maxY = Math.max(0, viewSize.height - extent.height);
        newViewX = Math.max(0, Math.min(newViewX, maxX));
        newViewY = Math.max(0, Math.min(newViewY, maxY));

        viewport.setViewPosition(new Point(newViewX, newViewY));
    }

    private boolean captureZoomAnchor(MouseWheelEvent e, JViewport viewport) {
        if (viewport == null) {
            return false;
        }
        Point viewportMouse = SwingUtilities.convertPoint(this, e.getPoint(), viewport);
        if (viewportMouse == null) {
            return false;
        }
        double mx = (double)(e.getX() - imageDrawX) / zoomFactor;
        double my = (double)(e.getY() - imageDrawY) / zoomFactor;
        zoomAnchorImageX = Math.max(0, Math.min(mx, image.getWidth()));
        zoomAnchorImageY = Math.max(0, Math.min(my, image.getHeight()));
        zoomAnchorViewportPoint = viewportMouse;
        zoomAnchorActive = true;
        return true;
    }

    private void resetZoomAnchor() {
        zoomAnchorActive = false;
        zoomAnchorViewportPoint = null;
        lastWheelNanos = 0L;
    }



    @Override
    public void mouseReleased(MouseEvent e) {
        if (multiRectangleSelection) {
            handleMultiRectangleRelease(e);
        }

        this.isDragging = false;
        this.isResizing = false;
        this.draggingPoint = null;
        this.startPoint = null;
        this.currentResizerHandle = "";

        if (toolMode == ToolMode.NORMAL && !pointSelection) {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private Rectangle normalizeRectangle(Rectangle rect) {
        if (rect == null) return null;
        int x1 = Math.min(rect.x, rect.x + rect.width);
        int y1 = Math.min(rect.y, rect.y + rect.height);
        int x2 = Math.max(rect.x, rect.x + rect.width);
        int y2 = Math.max(rect.y, rect.y + rect.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    private Rectangle clampRectangleToImage(Rectangle rect) {
        Rectangle normalized = normalizeRectangle(rect);
        if (normalized == null) return null;

        int x = Math.max(0, Math.min(normalized.x, image.getWidth()));
        int y = Math.max(0, Math.min(normalized.y, image.getHeight()));
        int maxWidth = Math.max(0, image.getWidth() - x);
        int maxHeight = Math.max(0, image.getHeight() - y);

        Rectangle result = new Rectangle(
                x,
                y,
                Math.max(0, Math.min(normalized.width, maxWidth)),
                Math.max(0, Math.min(normalized.height, maxHeight))
        );

        if (transform != null && transform.bounded()) {
            result = transform.clamp(result);
        }
        return normalizeRectangle(result);
    }

    private int findRectangleIndexAt(double x, double y) {
        for (int i = selectedRectangles.size() - 1; i >= 0; i--) {
            Rectangle rect = normalizeRectangle(selectedRectangles.get(i));
            if (rect != null && rect.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private void setActiveRectangleIndex(int index) {
        if (index < 0 || index >= selectedRectangles.size()) {
            activeRectangleIndex = -1;
            if (!drawingNewRectangle) {
                cropBox = null;
            }
            revalidate();
            repaint();
            return;
        }

        activeRectangleIndex = index;
        Rectangle active = normalizeRectangle(selectedRectangles.get(index));
        cropBox = active == null ? null : new Rectangle(active);
        revalidate();
        repaint();
    }

    private void syncActiveRectangleFromCropBox() {
        if (!multiRectangleSelection) return;
        if (activeRectangleIndex < 0 || activeRectangleIndex >= selectedRectangles.size()) return;
        if (cropBox == null) return;

        Rectangle normalized = clampRectangleToImage(cropBox);
        cropBox = normalized == null ? null : new Rectangle(normalized);
        if (normalized != null) {
            selectedRectangles.set(activeRectangleIndex, new Rectangle(normalized));
        }
    }

    private void handleMultiRectanglePress(double mx, double my) {
        isDragging = false;
        isResizing = false;
        drawingNewRectangle = false;
        currentResizerHandle = "";

        int ix = (int) Math.floor(mx);
        int iy = (int) Math.floor(my);

        if (cropBox != null && activeRectangleIndex >= 0) {
            makeResizerSquares();
            for (Map.Entry<String, Rectangle> entry : resizerHandles.entrySet()) {
                Rectangle handleRect = entry.getValue();
                if (handleRect != null && handleRect.contains(mx, my)) {
                    currentResizerHandle = entry.getKey();
                    isResizing = true;
                    return;
                }
            }
        }

        int hitIndex = findRectangleIndexAt(mx, my);
        if (hitIndex >= 0) {
            setActiveRectangleIndex(hitIndex);

            if (cropBox != null) {
                makeResizerSquares();
                for (Map.Entry<String, Rectangle> entry : resizerHandles.entrySet()) {
                    Rectangle handleRect = entry.getValue();
                    if (handleRect != null && handleRect.contains(mx, my)) {
                        currentResizerHandle = entry.getKey();
                        isResizing = true;
                        return;
                    }
                }
            }

            isDragging = true;
            draggingPoint = new Point(ix, iy);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            return;
        }

        activeRectangleIndex = -1;
        drawingNewRectangle = true;
        startPoint = new Point(ix, iy);
        cropBox = new Rectangle(ix, iy, 0, 0);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        revalidate();
        repaint();
    }

    private void handleMultiRectangleDrag(MouseEvent e, double newXd, double newYd) {
        if (!mouseInWindow) return;

        if (isResizing) {
            MouseMotionListener listener = resizerHandleListeners.get(currentResizerHandle);
            if (listener != null) {
                listener.mouseDragged(e);
            }

            Rectangle clamped = clampRectangleToImage(cropBox);
            if (clamped != null) {
                cropBox.setBounds(clamped);
                syncActiveRectangleFromCropBox();
            }

            revalidate();
            repaint();
            return;
        }

        if (isDragging && cropBox != null) {
            double offsetX = newXd - draggingPoint.getX();
            double offsetY = newYd - draggingPoint.getY();

            int newX = (int) (cropBox.getX() + offsetX);
            int newY = (int) (cropBox.getY() + offsetY);

            newX = Math.max(0, Math.min(newX, image.getWidth() - cropBox.width));
            newY = Math.max(0, Math.min(newY, image.getHeight() - cropBox.height));

            Rectangle moved = new Rectangle(newX, newY, cropBox.width, cropBox.height);
            if (transform != null && transform.bounded()) {
                moved = transform.clamp(moved);
            }

            cropBox.setBounds(normalizeRectangle(moved));
            syncActiveRectangleFromCropBox();
            draggingPoint = new Point((int) newXd, (int) newYd);

            revalidate();
            repaint();
            return;
        }

        if (drawingNewRectangle && startPoint != null) {
            int x = (int) Math.min(startPoint.x, newXd);
            int y = (int) Math.min(startPoint.y, newYd);
            int width = (int) Math.abs(startPoint.x - newXd);
            int height = (int) Math.abs(startPoint.y - newYd);

            Rectangle candidate = clampRectangleToImage(new Rectangle(x, y, width, height));
            cropBox = candidate == null ? null : new Rectangle(candidate);

            revalidate();
            repaint();
        }
    }

    private void handleMultiRectangleRelease(MouseEvent e) {
        if (isDragging || isResizing) {
            Rectangle finalized = clampRectangleToImage(cropBox);
            if (finalized != null && finalized.width > 0 && finalized.height > 0) {
                cropBox = new Rectangle(finalized);
                syncActiveRectangleFromCropBox();
            } else if (activeRectangleIndex >= 0 && activeRectangleIndex < selectedRectangles.size()) {
                selectedRectangles.remove(activeRectangleIndex);
                if (selectedRectangles.isEmpty()) {
                    activeRectangleIndex = -1;
                    cropBox = null;
                } else {
                    setActiveRectangleIndex(Math.min(activeRectangleIndex, selectedRectangles.size() - 1));
                }
            }
        } else if (drawingNewRectangle) {
            Rectangle finalized = clampRectangleToImage(cropBox);
            if (finalized != null && finalized.width > 0 && finalized.height > 0) {
                selectedRectangles.add(new Rectangle(finalized));
                activeRectangleIndex = selectedRectangles.size() - 1;
                cropBox = new Rectangle(finalized);
            } else if (selectedRectangles.isEmpty()) {
                activeRectangleIndex = -1;
                cropBox = null;
            } else {
                setActiveRectangleIndex(selectedRectangles.size() - 1);
            }
        }

        drawingNewRectangle = false;
        startPoint = null;
        draggingPoint = null;
        currentResizerHandle = "";
        revalidate();
        repaint();
    }

    public void loadSelectedRectangles(List<Rectangle> rectangles) {
        if (selectionMode != SelectionMode.MULTI_RECTANGLE) {
            throw new IllegalCallerException("Cannot load rectangle list data when multi-rectangle selection is false");
        }

        this.selectedRectangles.clear();
        this.activeRectangleIndex = -1;
        this.cropBox = null;
        this.drawingNewRectangle = false;
        this.startPoint = null;
        this.draggingPoint = null;

        if (rectangles != null) {
            for (Rectangle rect : rectangles) {
                Rectangle clamped = clampRectangleToImage(rect);
                if (clamped != null && clamped.width > 0 && clamped.height > 0) {
                    this.selectedRectangles.add(new Rectangle(clamped));
                }
            }
        }

        if (!this.selectedRectangles.isEmpty()) {
            setActiveRectangleIndex(this.selectedRectangles.size() - 1);
        } else {
            revalidate();
            repaint();
        }
    }

    public List<Rectangle> getSelectedRectangles() {
        if (selectionMode != SelectionMode.MULTI_RECTANGLE) {
            throw new IllegalCallerException("Cannot call getSelectedRectangles when multi-rectangle selection is false");
        }

        List<Rectangle> result = new ArrayList<>(selectedRectangles.size());
        for (Rectangle rect : selectedRectangles) {
            Rectangle normalized = normalizeRectangle(rect);
            Rectangle applied = normalized == null ? null : transform.apply(normalized);
            if (applied != null && applied.width > 0 && applied.height > 0) {
                result.add(applied);
            }
        }
        return result;
    }

    public boolean undoLastRectangle() {
        if (selectionMode != SelectionMode.MULTI_RECTANGLE) {
            throw new IllegalCallerException("Cannot undo rectangles when multi-rectangle selection is false");
        }

        if (drawingNewRectangle) {
            drawingNewRectangle = false;
            cropBox = activeRectangleIndex >= 0 && activeRectangleIndex < selectedRectangles.size()
                    ? new Rectangle(selectedRectangles.get(activeRectangleIndex))
                    : null;
            repaint();
            return true;
        }

        if (selectedRectangles.isEmpty()) {
            return false;
        }

        selectedRectangles.remove(selectedRectangles.size() - 1);
        if (selectedRectangles.isEmpty()) {
            activeRectangleIndex = -1;
            cropBox = null;
        } else {
            setActiveRectangleIndex(selectedRectangles.size() - 1);
        }

        revalidate();
        repaint();
        return true;
    }

    public boolean removeActiveRectangle() {
        if (selectionMode != SelectionMode.MULTI_RECTANGLE) {
            throw new IllegalCallerException("Cannot remove active rectangle when multi-rectangle selection is false");
        }

        if (activeRectangleIndex < 0 || activeRectangleIndex >= selectedRectangles.size()) {
            return false;
        }

        selectedRectangles.remove(activeRectangleIndex);
        if (selectedRectangles.isEmpty()) {
            activeRectangleIndex = -1;
            cropBox = null;
        } else {
            setActiveRectangleIndex(Math.min(activeRectangleIndex, selectedRectangles.size() - 1));
        }

        revalidate();
        repaint();
        return true;
    }

    public void clearSelectedRectangles() {
        if (selectionMode != SelectionMode.MULTI_RECTANGLE) {
            throw new IllegalCallerException("Cannot clear rectangle list when multi-rectangle selection is false");
        }

        selectedRectangles.clear();
        activeRectangleIndex = -1;
        drawingNewRectangle = false;
        cropBox = null;
        startPoint = null;
        draggingPoint = null;
        isDragging = false;
        isResizing = false;
        currentResizerHandle = "";
        revalidate();
        repaint();
    }

    public int getSelectedRectangleCount() {
        if (selectionMode != SelectionMode.MULTI_RECTANGLE) {
            throw new IllegalCallerException("Cannot query rectangle count when multi-rectangle selection is false");
        }
        return selectedRectangles.size();
    }


    public void mouseExited(MouseEvent e) {
        this.mouseInWindow = false;
        if (toolMode == ToolMode.COLOR_PICKER) {
            this.colorPickerPixel = null;
            repaint();
        }
        if (pointSelection) {
            this.hoveredPoint = null;
            repaint();
        }
    }

    private void drawMultiRectangleComponents(Graphics2D g2, double zoom, int offsetX, int offsetY) {
        Stroke originalStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f));

        for (int i = 0; i < selectedRectangles.size(); i++) {
            Rectangle rect = selectedRectangles.get(i);
            if (rect == null) continue;
            drawSelectionRectangle(g2, rect, zoom, offsetX, offsetY, i == activeRectangleIndex);
        }

        if (drawingNewRectangle && cropBox != null) {
            drawSelectionRectangle(g2, cropBox, zoom, offsetX, offsetY, true);
        }

        if (cropBox != null && activeRectangleIndex >= 0) {
            g2.setColor(resizerSquareColor);
            makeResizerSquares();
            for (Rectangle handle : resizerHandles.values()) {
                int hw = Math.max(1, (int)Math.round(handle.width * zoom));
                int hh = Math.max(1, (int)Math.round(handle.height * zoom));
                int hx = offsetX + (int)Math.round(handle.x * zoom);
                int hy = offsetY + (int)Math.round(handle.y * zoom);
                g2.fillRect(hx, hy, hw, hh);
            }
        }

        g2.setStroke(originalStroke);
    }

    private void drawSelectionRectangle(Graphics2D g2, Rectangle rect, double zoom, int offsetX, int offsetY, boolean active) {
        Rectangle normalized = normalizeRectangle(rect);
        int x = offsetX + (int)Math.round(normalized.x * zoom);
        int y = offsetY + (int)Math.round(normalized.y * zoom);
        int width = (int)Math.round(normalized.width * zoom);
        int height = (int)Math.round(normalized.height * zoom);

        Color fill = active ? new Color(255, 193, 7, 64) : new Color(33, 150, 243, 54);
        Color outline = active ? new Color(255, 143, 0) : new Color(25, 118, 210);
        g2.setColor(fill);
        g2.fillRect(x, y, width, height);
        g2.setColor(outline);
        g2.drawRect(x, y, width, height);
    }
    public void mouseClicked(MouseEvent e) {
        if (toolMode == ToolMode.COLOR_PICKER) {
            updateColorPickerHover(e);
        }
        if (pointSelection) {
            updatePointSelectionHover(e);
        }
    }
    public void mouseEntered(MouseEvent e) {
        this.mouseInWindow = true;
        requestFocusInWindow();
        if (toolMode == ToolMode.COLOR_PICKER) {
            setCursor(ensureColorPickerCursor());
        } else if (pointSelection) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }
    }
    public void mouseMoved(MouseEvent e) {
        if (toolMode == ToolMode.COLOR_PICKER) {
            updateColorPickerHover(e);
            return;
        }
        if (pointSelection) {
            updatePointSelectionHover(e);
            return;
        }
        if (freeformDraw || cropBox == null) return;

        double mx = (double)(e.getX() - imageDrawX) / zoomFactor;
        double my = (double)(e.getY() - imageDrawY) / zoomFactor;

        boolean anyHandle = false;
        for (Map.Entry<String, Rectangle> entry : resizerHandles.entrySet()) {
            String handleLoc = entry.getKey();
            Rectangle handle = entry.getValue();

            int hx = imageDrawX + (int)Math.round(handle.x * zoomFactor);
            int hy = imageDrawY + (int)Math.round(handle.y * zoomFactor);
            int hw = Math.max(1, (int)Math.round(handle.width * zoomFactor));
            int hh = Math.max(1, (int)Math.round(handle.height * zoomFactor));

            int exx = e.getX();
            int eyy = e.getY();
            if (exx < hx || exx > hx + hw || eyy < hy || eyy > hy + hh) continue;

            setCursor(Cursor.getPredefinedCursor(switch (handleLoc.toLowerCase()) {
                case "nw" -> Cursor.NW_RESIZE_CURSOR;
                case "n"  -> Cursor.N_RESIZE_CURSOR;
                case "ne" -> Cursor.NE_RESIZE_CURSOR;
                case "w"  -> Cursor.W_RESIZE_CURSOR;
                case "e"  -> Cursor.E_RESIZE_CURSOR;
                case "sw" -> Cursor.SW_RESIZE_CURSOR;
                case "s"  -> Cursor.S_RESIZE_CURSOR;
                case "se" -> Cursor.SE_RESIZE_CURSOR;
                default   -> Cursor.DEFAULT_CURSOR;
            }));
            currentResizerHandle = handleLoc;
            anyHandle = true;
            break;
        }
        isResizing = anyHandle;

        if (!anyHandle) {
            setCursor(Cursor.getPredefinedCursor(cropBox.contains(mx, my) ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }


    private void makeResizerSquares() {
        for (ResizeDir dir : ResizeDir.values()) {
            int cx = SwitchIf.ofDefaultVal(
                    cropBox.x + cropBox.width / 2,
                    ConditionalCase.of(dir.west, cropBox.x),
                    ConditionalCase.of(dir.east, cropBox.x + cropBox.width)).evaluate();
            int cy = SwitchIf.ofDefaultVal(
                    cropBox.y + cropBox.height / 2,
                    ConditionalCase.of(dir.north, cropBox.y),
                    ConditionalCase.of(dir.south, cropBox.y + cropBox.height)).evaluate();

            Rectangle r = new Rectangle(
                    cx - resizerHandleSize / 2,
                    cy - resizerHandleSize / 2,
                    resizerHandleSize,
                    resizerHandleSize
            );
            resizerHandles.put(dir.name(), r);

            resizerHandleListeners.put(dir.name(),
                    new MouseMotionListener() {
                        @Override
                        public void mouseDragged(MouseEvent e) {
                            int mx = (int)((e.getX() - imageDrawX) / zoomFactor),
                                    my = (int)((e.getY() - imageDrawY) / zoomFactor);
                            int x = cropBox.x, y = cropBox.y, w = cropBox.width, h = cropBox.height;

                            int dx = mx - cropBox.x, dy = my - cropBox.y;
                            if (dir.west && mx >= 0 && mx <= (x + w - 5)) {
                                x += dx;
                                w -= dx;
                            } else if (dir.east && dx >= 0 && dx <= (x + w - 5)) {
                                w = dx;
                            }
                            if (dir.north && my >= 0 && my <= (y + h - 5)) {
                                y += dy;
                                h -= dy;
                            } else if (dir.south && dy >= 0 && dy <= (y + h - 5)) {
                                h = dy;
                            }

                            x = Math.max(0, Math.min(x, image.getWidth()));
                            y = Math.max(0, Math.min(y, image.getHeight()));
                            w = Math.max(0, Math.min(w, image.getWidth() - x));
                            h = Math.max(0, Math.min(h, image.getHeight() - y));

                            cropBox.setBounds(x, y, w, h);

                            if (transform != null && transform.bounded()) {
                                Rectangle clamped = transform.clamp(cropBox);
                                cropBox.setBounds(clamped);
                            }
                        }
                        @Override public void mouseMoved(MouseEvent e) {}
                    }
            );
        }
    }


    public void setToolMode(ToolMode mode) {
        ToolMode resolved = mode == null ? ToolMode.NORMAL : mode;
        if (this.toolMode == resolved) {
            return;
        }
        this.toolMode = resolved;
        if (resolved == ToolMode.COLOR_PICKER) {
            setCursor(ensureColorPickerCursor());
        } else {
            setCursor(Cursor.getDefaultCursor());
            this.colorPickerPixel = null;
        }
        if (pointSelection && resolved == ToolMode.NORMAL) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }
        repaint();
    }

    public ToolMode getToolMode() {
        return this.toolMode;
    }

    public void setColorPickListener(Consumer<Color> listener) {
        this.colorPickListener = listener;
    }

    public void setPointEditMode(PointEditMode mode) {
        this.pointEditMode = mode == null ? PointEditMode.ADD : mode;
        repaint();
    }

    public PointEditMode getPointEditMode() {
        return this.pointEditMode;
    }

    private void updateColorPickerHover(MouseEvent e) {
        if (toolMode != ToolMode.COLOR_PICKER) return;
        Point point = toImagePoint(e);
        if (point != null) {
            this.colorPickerPixel = point;
        } else {
            this.colorPickerPixel = null;
        }
        repaint();
    }

    private void updatePointSelectionHover(MouseEvent e) {
        if (!pointSelection || toolMode == ToolMode.COLOR_PICKER) return;
        this.hoveredPoint = toImagePoint(e);
        repaint();
    }

    private void handlePointSelectionClick(MouseEvent e) {
        if (!pointSelection || !SwingUtilities.isLeftMouseButton(e)) return;
        Point point = toImagePoint(e);
        if (point == null) return;
        if (pointEditMode == PointEditMode.REMOVE) {
            removePoint(point);
        } else {
            addPoint(point);
        }
        repaint();
    }

    private void addPoint(Point point) {
        switch (selectionMode) {
            case POINT -> this.selectedPoint = point;
            case MULTI_POINT -> {
                if (!selectedPoints.contains(point)) {
                    selectedPoints.add(new Point(point));
                }
            }
            case LABELED_MULTI_POINT -> {
                String label = choosePointLabel();
                if (label != null) {
                    labeledSelectedPoints.put(new Point(point), label);
                }
            }
        }
    }

    private void removePoint(Point point) {
        switch (selectionMode) {
            case POINT -> {
                if (point.equals(selectedPoint)) {
                    selectedPoint = null;
                }
            }
            case MULTI_POINT -> selectedPoints.remove(point);
            case LABELED_MULTI_POINT -> labeledSelectedPoints.remove(point);
        }
    }

    private String choosePointLabel() {
        if (pointLabels.isEmpty()) {
            return null;
        }
        Object selected = JOptionPane.showInputDialog(
                this,
                "Select label for point:",
                "Point label",
                JOptionPane.PLAIN_MESSAGE,
                null,
                pointLabels.toArray(new String[0]),
                pointLabels.get(0)
        );
        return selected instanceof String ? (String) selected : null;
    }

    private void emitColorPick(MouseEvent e) {
        if (toolMode != ToolMode.COLOR_PICKER || colorPickListener == null) return;
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        Point point = toImagePoint(e);
        if (point == null) return;
        int rgb = image.getRGB(point.x, point.y);
        colorPickListener.accept(new Color(rgb, true));
    }

    private Point toImagePoint(MouseEvent e) {
        double mx = (double)(e.getX() - imageDrawX) / zoomFactor;
        double my = (double)(e.getY() - imageDrawY) / zoomFactor;
        int px = (int)Math.floor(mx);
        int py = (int)Math.floor(my);
        if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
            return null;
        }
        Point point = new Point(px, py);
        if (transform != null && transform.bounded() && !transform.included(point)) {
            return null;
        }
        return point;
    }

    private void drawPointSelectionOverlay(Graphics2D g2, double zoom, int offsetX, int offsetY) {
        if (!pointSelection) return;
        if (selectedPoint != null) {
            drawPixelMarker(g2, selectedPoint, zoom, offsetX, offsetY, new Color(255, 210, 0, 120), new Color(255, 245, 120, 230));
        }
        for (Point point : selectedPoints) {
            drawPixelMarker(g2, point, zoom, offsetX, offsetY, new Color(255, 210, 0, 120), new Color(255, 245, 120, 230));
        }
        for (Map.Entry<Point, String> entry : labeledSelectedPoints.entrySet()) {
            Point point = entry.getKey();
            drawPixelMarker(g2, point, zoom, offsetX, offsetY, new Color(80, 190, 255, 125), new Color(150, 225, 255, 230));
            drawPointLabel(g2, point, entry.getValue(), zoom, offsetX, offsetY);
        }
        if (hoveredPoint != null && !isSelectedPoint(hoveredPoint)) {
            drawPixelMarker(g2, hoveredPoint, zoom, offsetX, offsetY, new Color(255, 255, 255, 80), new Color(255, 255, 255, 230));
        }
    }

    private void drawPixelMarker(Graphics2D g2, Point point, double zoom, int offsetX, int offsetY, Color fillColor, Color lightColor) {
        int px = offsetX + (int)Math.round(point.x * zoom);
        int py = offsetY + (int)Math.round(point.y * zoom);
        int pixelSize = Math.max(1, (int)Math.round(zoom));
        Stroke original = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(fillColor);
        g2.fillRect(px, py, pixelSize, pixelSize);
        g2.setColor(lightColor);
        g2.drawRect(px - 1, py - 1, pixelSize + 1, pixelSize + 1);
        g2.setColor(new Color(0, 0, 0, 190));
        g2.drawRect(px - 2, py - 2, pixelSize + 3, pixelSize + 3);
        g2.setStroke(original);
    }

    private void drawPointLabel(Graphics2D g2, Point point, String label, double zoom, int offsetX, int offsetY) {
        if (label == null || label.isEmpty()) return;
        int px = offsetX + (int)Math.round(point.x * zoom) + Math.max(4, (int)Math.round(zoom));
        int py = offsetY + (int)Math.round(point.y * zoom) - 4;
        FontMetrics metrics = g2.getFontMetrics();
        int textWidth = metrics.stringWidth(label);
        int textHeight = metrics.getHeight();
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(px - 2, py - textHeight + metrics.getDescent(), textWidth + 4, textHeight);
        g2.setColor(Color.WHITE);
        g2.drawString(label, px, py);
    }

    private boolean isSelectedPoint(Point point) {
        return point != null && (point.equals(selectedPoint) || selectedPoints.contains(point) || labeledSelectedPoints.containsKey(point));
    }

    private static boolean isPointSelectionMode(SelectionMode mode) {
        return mode == SelectionMode.POINT || mode == SelectionMode.MULTI_POINT || mode == SelectionMode.LABELED_MULTI_POINT;
    }

    private void drawColorPickerOverlay(Graphics2D g2, double zoom, int offsetX, int offsetY) {
        if (toolMode != ToolMode.COLOR_PICKER || colorPickerPixel == null) return;
        int px = offsetX + (int)Math.round(colorPickerPixel.x * zoom);
        int py = offsetY + (int)Math.round(colorPickerPixel.y * zoom);
        int pixelSize = Math.max(1, (int)Math.round(zoom));
        Stroke original = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.drawRect(px - 1, py - 1, pixelSize + 1, pixelSize + 1);
        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawRect(px - 2, py - 2, pixelSize + 3, pixelSize + 3);
        g2.setStroke(original);
    }

    private Cursor ensureColorPickerCursor() {
        if (colorPickerCursor != null) {
            return colorPickerCursor;
        }
        int size = 24;
        BufferedImage cursorImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = cursorImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.Src);
        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(0, 0, size, size);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(30, 30, 30));
        g2.drawLine(4, size - 5, size - 6, 5);
        g2.setColor(new Color(200, 200, 200));
        g2.drawLine(5, size - 5, size - 5, 6);
        g2.dispose();
        try {
            colorPickerCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImage, new Point(4, size - 5), "color-picker");
        } catch (Exception ex) {
            colorPickerCursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
        return colorPickerCursor;
    }

    public void clearDrawing() {
        this.drawingAxis.clear();
        this.drawingIndex = 0;
        this.drawingPoints = new Point[DEFAULT_DRAWING_POINT_CAPACITY];
        this.cropBox = null;
        this.selectedRectangles.clear();
        this.activeRectangleIndex = -1;
        this.drawingNewRectangle = false;
        this.startPoint = null;
        this.draggingPoint = null;
        this.isDragging = false;
        this.isResizing = false;
        this.currentResizerHandle = "";
        this.hoveredPoint = null;
        this.selectedPoint = null;
        this.selectedPoints.clear();
        this.labeledSelectedPoints.clear();
        this.repaint();
    }

    public void loadDrawingFromArgument(Map<Double, List<Double>> axisData, List<Point> orderedPoints) {
        if (!freeformDraw) throw new IllegalCallerException("Cannot load drawing data when freeform draw is false");

        this.drawingAxis.clear();
        if (axisData != null) {
            axisData.forEach((key, values) -> {
                List<Double> copy = values == null ? new ArrayList<>() : new ArrayList<>(values);
                this.drawingAxis.put(key, copy);
            });
        }

        int required = orderedPoints == null ? 0 : orderedPoints.size();
        int capacity = Math.max(DEFAULT_DRAWING_POINT_CAPACITY, required + 10);
        this.drawingPoints = new Point[capacity];
        this.drawingIndex = 0;

        if (orderedPoints != null) {
            for (Point point : orderedPoints) {
                if (point == null) continue;
                if (this.drawingIndex >= this.drawingPoints.length) break;
                this.drawingPoints[this.drawingIndex++] = new Point(point);
            }
        }

        revalidate();
        repaint();
    }


    public void loadCropBox(Rectangle rect) {
        if (freeformDraw) throw new IllegalCallerException("Cannot load crop box data when freeform draw is true");
        if (pointSelection) throw new IllegalCallerException("Cannot load crop box data when point selection is true");
        if (multiRectangleSelection) throw new IllegalCallerException("Cannot load crop box data when multi-rectangle selection is true");

        this.cropBox = rect == null ? null : new Rectangle(rect);
        if (this.cropBox != null && transform != null && transform.bounded()) {
            this.cropBox = transform.clamp(this.cropBox);
        }
        revalidate();
        repaint();
    }

    public void loadSelectedPoint(Point point) {
        if (selectionMode != SelectionMode.POINT) throw new IllegalCallerException("Cannot load point data when single point selection is false");
        this.selectedPoint = clampPoint(point);
        revalidate();
        repaint();
    }

    public void loadSelectedPoints(List<Point> points) {
        if (selectionMode != SelectionMode.MULTI_POINT) throw new IllegalCallerException("Cannot load point data when multi-point selection is false");
        this.selectedPoints.clear();
        if (points != null) {
            for (Point point : points) {
                Point clamped = clampPoint(point);
                if (clamped != null && !this.selectedPoints.contains(clamped)) {
                    this.selectedPoints.add(clamped);
                }
            }
        }
        revalidate();
        repaint();
    }

    public void loadLabeledSelectedPoints(Map<Point, String> points) {
        if (selectionMode != SelectionMode.LABELED_MULTI_POINT) throw new IllegalCallerException("Cannot load labeled point data when labeled multi-point selection is false");
        this.labeledSelectedPoints.clear();
        if (points != null) {
            for (Map.Entry<Point, String> entry : points.entrySet()) {
                Point clamped = clampPoint(entry.getKey());
                String label = entry.getValue();
                if (clamped != null && label != null) {
                    this.labeledSelectedPoints.put(clamped, label);
                }
            }
        }
        revalidate();
        repaint();
    }

    private Point clampPoint(Point point) {
        if (point == null) return null;
        Point result = new Point(point);
        result.x = Math.max(0, Math.min(result.x, image.getWidth() - 1));
        result.y = Math.max(0, Math.min(result.y, image.getHeight() - 1));
        if (transform != null && transform.bounded()) {
            result = transform.clamp(result);
            result.x = Math.max(0, Math.min(result.x, image.getWidth() - 1));
            result.y = Math.max(0, Math.min(result.y, image.getHeight() - 1));
        }
        return result;
    }

    public BufferedImage createExportImage(boolean includeComponents) {
        BufferedImage exportImg = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = exportImg.createGraphics();
        g2.setColor(getBackground());
        g2.fillRect(0, 0, exportImg.getWidth(), exportImg.getHeight());
        g2.drawImage(image, 0, 0, image.getWidth(), image.getHeight(), null);
        if (includeComponents) {
            drawComponents(g2, 1.0, 0, 0, exportImg.getWidth(), exportImg.getHeight());
            drawPointSelectionOverlay(g2, 1.0, 0, 0);
        }
        g2.dispose();
        return exportImg;
    }

    public List<Point> getOrderedDrawingPoints() {
        if (!freeformDraw) throw new IllegalCallerException("Cannot call getOrderedDrawingPoints when freeform draw is false");
        List<Point> ordered = new ArrayList<>(drawingIndex);
        for (int i = 0; i < drawingIndex; i++) {
            Point point = drawingPoints[i];
            if (point != null) {
                ordered.add(new Point(point));
            }
        }
        return ordered;
    }

    public double getZoomFactor() {
        return this.zoomFactor;
    }

    public int getImageOffsetX() {
        return this.imageDrawX;
    }

    public int getImageOffsetY() {
        return this.imageDrawY;
    }

    public Map<Double, List<Double>> getDrawingAxis() {
        if (!freeformDraw) throw new IllegalCallerException("Cannot call getDrawingAxis when freeform draw is false");
        return this.drawingAxis;
    }
    public Rectangle getCropBox() {
        if (freeformDraw) throw new IllegalCallerException("Cannot call getCropBox when freeform draw is true");
        if (pointSelection) throw new IllegalCallerException("Cannot call getCropBox when point selection is true");
        if (multiRectangleSelection) throw new IllegalCallerException("Cannot call getCropBox when multi-rectangle selection is true");
        return transform.apply(this.cropBox);
    }
    public Point getSelectedPoint() {
        if (selectionMode != SelectionMode.POINT) throw new IllegalCallerException("Cannot call getSelectedPoint when single point selection is false");
        return transform.apply(this.selectedPoint);
    }
    public List<Point> getSelectedPoints() {
        if (selectionMode != SelectionMode.MULTI_POINT) throw new IllegalCallerException("Cannot call getSelectedPoints when multi-point selection is false");
        List<Point> result = new ArrayList<>(selectedPoints.size());
        for (Point point : selectedPoints) {
            result.add(transform.apply(point));
        }
        return result;
    }
    public Map<Point, String> getLabeledSelectedPoints() {
        if (selectionMode != SelectionMode.LABELED_MULTI_POINT) throw new IllegalCallerException("Cannot call getLabeledSelectedPoints when labeled multi-point selection is false");
        Map<Point, String> result = new LinkedHashMap<>();
        for (Map.Entry<Point, String> entry : labeledSelectedPoints.entrySet()) {
            result.put(transform.apply(entry.getKey()), entry.getValue());
        }
        return result;
    }
    public Object getUserSelection() {
        if (freeformDraw) return getDrawingAxis();
        if (selectionMode == SelectionMode.MULTI_RECTANGLE) return getSelectedRectangles();
        if (selectionMode == SelectionMode.POINT) return getSelectedPoint();
        if (selectionMode == SelectionMode.MULTI_POINT) return getSelectedPoints();
        if (selectionMode == SelectionMode.LABELED_MULTI_POINT) return getLabeledSelectedPoints();
        return getCropBox();
    }

    public void setAutoFit(boolean val) {
        this.autoFit = val;
    }


}

