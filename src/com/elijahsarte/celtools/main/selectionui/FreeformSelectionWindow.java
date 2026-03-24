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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// cropping from: https://github.com/lewiswhitaker1/ImageCropToSquare/blob/main/src/me/lewis/cropper/ImageCropper.java
public class FreeformSelectionWindow extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    private final BufferedImage image;
    private final SelectionTransform transform;
    private final boolean freeformDraw;

    public enum ToolMode {
        NORMAL,
        COLOR_PICKER
    }

    private ToolMode toolMode = ToolMode.NORMAL;
    private Point colorPickerPixel = null;
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


    public FreeformSelectionWindow(BufferedImage image, SelectionTransform transform, boolean freeformDraw) {
        this.image = image;
        this.transform = transform;
        this.freeformDraw = freeformDraw;

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
        drawColorPickerOverlay(g2, zoomFactor, imageDrawX, imageDrawY);
    }

    public void mouseDragged(MouseEvent e) {

        int ex = e.getX();
        int ey = e.getY();

        if (toolMode == ToolMode.COLOR_PICKER) {
            updateColorPickerHover(e);
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



    public void mouseReleased(MouseEvent e) {
        this.isDragging = false;
        this.isResizing = false;
    }
    public void mouseExited(MouseEvent e) {
        this.mouseInWindow = false;
        if (toolMode == ToolMode.COLOR_PICKER) {
            this.colorPickerPixel = null;
            repaint();
        }
    }
    public void mouseClicked(MouseEvent e) {
        if (toolMode == ToolMode.COLOR_PICKER) {
            updateColorPickerHover(e);
        }
    }
    public void mouseEntered(MouseEvent e) {
        this.mouseInWindow = true;
        requestFocusInWindow();
        if (toolMode == ToolMode.COLOR_PICKER) {
            setCursor(ensureColorPickerCursor());
        }
    }
    public void mouseMoved(MouseEvent e) {
        if (toolMode == ToolMode.COLOR_PICKER) {
            updateColorPickerHover(e);
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
        repaint();
    }

    public ToolMode getToolMode() {
        return this.toolMode;
    }

    public void setColorPickListener(Consumer<Color> listener) {
        this.colorPickListener = listener;
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
        return new Point(px, py);
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
        this.cropBox = rect == null ? null : new Rectangle(rect);
        if (this.cropBox != null && transform != null && transform.bounded()) {
            this.cropBox = transform.clamp(this.cropBox);
        }
        revalidate();
        repaint();
    }

    public BufferedImage createExportImage(boolean includeComponents) {
        BufferedImage exportImg = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = exportImg.createGraphics();
        g2.setColor(getBackground());
        g2.fillRect(0, 0, exportImg.getWidth(), exportImg.getHeight());
        g2.drawImage(image, 0, 0, image.getWidth(), image.getHeight(), null);
        if (includeComponents) {
            drawComponents(g2, 1.0, 0, 0, exportImg.getWidth(), exportImg.getHeight());
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
        return transform.apply(this.cropBox);
    }
    public Object getUserSelection() {
        return freeformDraw ? getDrawingAxis() : getCropBox();
    }

    public void setAutoFit(boolean val) {
        this.autoFit = val;
    }


}

