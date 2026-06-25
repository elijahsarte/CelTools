package com.elijahsarte.celtools.mainex.sandbox;

import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public class ShapeContourTest {

    public static void main(String[] args) {

        class Layer {
            final String name;
            final ShapeContour contour;
            final PointCollection pixels;
            final Color color;
            final boolean drawVertices;
            final Stroke stroke;

            Layer(String name, ShapeContour contour, Color color, boolean drawVertices, Stroke stroke) {
                this.name = name;
                this.contour = contour;
                this.pixels = null;
                this.color = color;
                this.drawVertices = drawVertices;
                this.stroke = stroke == null ? new BasicStroke(2f) : stroke;
            }

            Layer(String name, ShapeContour contour, Color color) {
                this(name, contour, color, true, new BasicStroke(2f));
            }

            Layer(String name, PointCollection pixels, Color color) {
                this.name = name;
                this.contour = null;
                this.pixels = pixels;
                this.color = color;
                this.drawVertices = false;
                this.stroke = new BasicStroke(1.25f);
            }
        }

        class Marker {
            final String name;
            final Point2D point;
            final Color color;
            final int size;

            Marker(String name, Point2D point, Color color, int size) {
                this.name = name;
                this.point = point;
                this.color = color;
                this.size = size;
            }
        }

        class Outcome {
            final String name;
            boolean passed;
            boolean skipped;
            String summary;
            final java.util.List<Layer> layers = new ArrayList<>();
            final java.util.List<Marker> markers = new ArrayList<>();

            Outcome(String name) {
                this.name = name;
                this.summary = "Not run.";
            }
        }

        class ViewportPanel extends JPanel {
            private Outcome outcome;

            ViewportPanel() {
                setPreferredSize(new Dimension(300, 185));
                setBackground(Color.WHITE);
                setOpaque(true);
            }

            void setOutcome(Outcome outcome) {
                this.outcome = outcome;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                int w = getWidth();
                int h = getHeight();

                g.setColor(new Color(250, 250, 250));
                g.fillRect(0, 0, w, h);

                if (outcome == null) {
                    g.setColor(new Color(110, 110, 110));
                    g.drawString("No result yet.", 10, 20);
                    g.dispose();
                    return;
                }

                double minX = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;

                for (Layer layer : outcome.layers) {
                    if (layer.contour != null) {
                        for (Point p : layer.contour) {
                            minX = Math.min(minX, p.x);
                            maxX = Math.max(maxX, p.x);
                            minY = Math.min(minY, p.y);
                            maxY = Math.max(maxY, p.y);
                        }
                    }
                    if (layer.pixels != null) {
                        for (Point p : layer.pixels) {
                            minX = Math.min(minX, p.x);
                            maxX = Math.max(maxX, p.x);
                            minY = Math.min(minY, p.y);
                            maxY = Math.max(maxY, p.y);
                        }
                    }
                }

                for (Marker marker : outcome.markers) {
                    if (marker.point != null) {
                        minX = Math.min(minX, marker.point.getX());
                        maxX = Math.max(maxX, marker.point.getX());
                        minY = Math.min(minY, marker.point.getY());
                        maxY = Math.max(maxY, marker.point.getY());
                    }
                }

                if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minY) || !Double.isFinite(maxY)) {
                    g.setColor(new Color(110, 110, 110));
                    g.drawString("No drawable output.", 10, 20);
                    String msg = outcome.summary == null ? "" : outcome.summary;
                    g.drawString(msg, 10, h - 8);
                    g.dispose();
                    return;
                }

                if (minX == maxX) {
                    minX -= 1.0;
                    maxX += 1.0;
                }
                if (minY == maxY) {
                    minY -= 1.0;
                    maxY += 1.0;
                }

                minX -= 1.5;
                maxX += 1.5;
                minY -= 1.5;
                maxY += 1.5;

                final int leftPad = 10;
                final int rightPad = 10;
                final int topPad = 18;
                final int bottomPad = 20;

                final double fMinX = minX;
                final double fMaxX = maxX;
                final double fMinY = minY;
                final double fMaxY = maxY;

                double spanX = Math.max(1.0, fMaxX - fMinX);
                double spanY = Math.max(1.0, fMaxY - fMinY);
                double scale = Math.min(
                        (w - leftPad - rightPad) / spanX,
                        (h - topPad - bottomPad) / spanY
                );
                if (!Double.isFinite(scale) || scale <= 0.0) scale = 1.0;

                final double fScale = scale;

                java.util.function.DoubleFunction<Integer> sx = x ->
                        leftPad + (int) Math.round((x - fMinX) * fScale);
                java.util.function.DoubleFunction<Integer> sy = y ->
                        h - bottomPad - (int) Math.round((y - fMinY) * fScale);

                g.setColor(new Color(238, 238, 238));
                if (fMinX <= 0 && fMaxX >= 0) {
                    int ax = sx.apply(0.0);
                    g.drawLine(ax, topPad, ax, h - bottomPad);
                }
                if (fMinY <= 0 && fMaxY >= 0) {
                    int ay = sy.apply(0.0);
                    g.drawLine(leftPad, ay, w - rightPad, ay);
                }

                for (Layer layer : outcome.layers) {
                    if (layer.pixels != null) {
                        int pixelSide = Math.max(2, (int) Math.round(Math.max(2.0, fScale * 0.72)));
                        int half = pixelSide / 2;
                        g.setColor(layer.color);
                        for (Point p : layer.pixels) {
                            int px = sx.apply(p.x) - half;
                            int py = sy.apply(p.y) - half;
                            g.fillRect(px, py, pixelSide, pixelSide);
                        }
                    }
                }

                for (Layer layer : outcome.layers) {
                    if (layer.contour != null && !layer.contour.isEmpty()) {
                        Path2D path = new Path2D.Double();
                        boolean first = true;
                        for (Point p : layer.contour) {
                            int px = sx.apply(p.x);
                            int py = sy.apply(p.y);
                            if (first) {
                                path.moveTo(px, py);
                                first = false;
                            } else {
                                path.lineTo(px, py);
                            }
                        }
                        path.closePath();
                        g.setColor(layer.color);
                        g.setStroke(layer.stroke);
                        g.draw(path);

                        if (layer.drawVertices) {
                            int size = Math.max(4, (int) Math.round(Math.min(7.0, 1.0 + (fScale * 0.16))));
                            for (Point p : layer.contour) {
                                int px = sx.apply(p.x);
                                int py = sy.apply(p.y);
                                g.fillOval(px - (size / 2), py - (size / 2), size, size);
                            }
                        }
                    }
                }

                for (Marker marker : outcome.markers) {
                    if (marker.point == null) continue;
                    int px = sx.apply(marker.point.getX());
                    int py = sy.apply(marker.point.getY());
                    int size = Math.max(5, marker.size);
                    g.setColor(marker.color);
                    g.fillOval(px - (size / 2), py - (size / 2), size, size);
                    g.setColor(new Color(20, 20, 20));
                    g.drawOval(px - (size / 2), py - (size / 2), size, size);
                }

                int legendY = 14;
                int legendX = 8;
                int legendCount = 0;

                for (Layer layer : outcome.layers) {
                    if (legendCount >= 6) break;
                    g.setColor(layer.color);
                    g.fillRect(legendX, legendY - 8, 12, 8);
                    g.setColor(new Color(40, 40, 40));
                    g.drawString(layer.name, legendX + 18, legendY);
                    legendY += 14;
                    legendCount++;
                }
                for (Marker marker : outcome.markers) {
                    if (legendCount >= 8) break;
                    g.setColor(marker.color);
                    g.fillOval(legendX, legendY - 9, 9, 9);
                    g.setColor(new Color(40, 40, 40));
                    g.drawString(marker.name, legendX + 18, legendY);
                    legendY += 14;
                    legendCount++;
                }

                String status = outcome.skipped ? "SKIPPED" : (outcome.passed ? "PASS" : "FAIL");
                Color statusColor = outcome.skipped ? new Color(180, 120, 0) :
                        outcome.passed ? new Color(0, 125, 40) : new Color(180, 30, 30);

                g.setColor(statusColor);
                g.drawString(status, 8, h - 6);

                String msg = outcome.summary == null ? "" : outcome.summary;
                if (!msg.isEmpty()) {
                    g.setColor(new Color(70, 70, 70));
                    FontMetrics fm = g.getFontMetrics();
                    int maxWidth = w - 70;
                    while (fm.stringWidth(msg) > maxWidth && msg.length() > 4) {
                        msg = msg.substring(0, msg.length() - 4) + "...";
                    }
                    g.drawString(msg, 60, h - 6);
                }

                g.dispose();
            }
        }

        class DrawingCanvas extends JPanel {
            private PointCollection pixels = new PointCollection();
            private final int cols = 78;
            private final int rows = 78;
            private final int cell = 8;
            private final int brushRadius = 1;

            DrawingCanvas() {
                setPreferredSize(new Dimension(cols * cell, rows * cell));
                setBackground(Color.WHITE);
                setOpaque(true);

                MouseAdapter mouse = new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        paintAt(e);
                    }

                    @Override
                    public void mouseDragged(MouseEvent e) {
                        paintAt(e);
                    }
                };
                addMouseListener(mouse);
                addMouseMotionListener(mouse);
            }

            PointCollection getPixelsCopy() {
                return new PointCollection(pixels);
            }

            void setPixels(PointCollection source) {
                this.pixels = source == null ? new PointCollection() : new PointCollection(source);
                repaint();
            }

            void clearPixels() {
                this.pixels = new PointCollection();
                repaint();
            }

            private Point toShapePoint(int mx, int my) {
                int col = mx / cell;
                int row = my / cell;
                if (col < 0 || col >= cols || row < 0 || row >= rows) return null;
                int x = col - (cols / 2);
                int y = (rows / 2) - row;
                return new Point(x, y);
            }

            private void paintAt(MouseEvent e) {
                Point center = toShapePoint(e.getX(), e.getY());
                if (center == null) return;

                boolean erase = SwingUtilities.isRightMouseButton(e)
                        || (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;

                for (int dx = -brushRadius; dx <= brushRadius; dx++) {
                    for (int dy = -brushRadius; dy <= brushRadius; dy++) {
                        if ((dx * dx) + (dy * dy) > (brushRadius * brushRadius) + 1) continue;
                        Point p = new Point(center.x + dx, center.y + dy);
                        if (erase) {
                            pixels.remove(p);
                        } else {
                            pixels.add(p);
                        }
                    }
                }
                repaint();
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);

                g.setColor(new Color(242, 242, 242));
                for (int x = 0; x <= cols; x++) {
                    int px = x * cell;
                    g.drawLine(px, 0, px, rows * cell);
                }
                for (int y = 0; y <= rows; y++) {
                    int py = y * cell;
                    g.drawLine(0, py, cols * cell, py);
                }

                int axisCol = cols / 2;
                int axisRow = rows / 2;
                g.setColor(new Color(210, 225, 245));
                g.drawLine(axisCol * cell, 0, axisCol * cell, rows * cell);
                g.drawLine(0, axisRow * cell, cols * cell, axisRow * cell);

                g.setColor(new Color(80, 150, 90));
                for (Point p : pixels) {
                    int col = p.x + (cols / 2);
                    int row = (rows / 2) - p.y;
                    int px = col * cell;
                    int py = row * cell;
                    g.fillRect(px + 1, py + 1, cell - 1, cell - 1);
                }

                try {
                    if (!pixels.isEmpty()) {
                        ShapeContour contour = new ShapeContour(new PointCollection(pixels));
                        if (!contour.isEmpty()) {
                            g.setColor(new Color(205, 50, 50));
                            g.setStroke(new BasicStroke(2f));
                            Point prev = null;
                            Point first = null;
                            for (Point p : contour) {
                                int col = p.x + (cols / 2);
                                int row = (rows / 2) - p.y;
                                int cx = (col * cell) + (cell / 2);
                                int cy = (row * cell) + (cell / 2);
                                if (first == null) first = new Point(cx, cy);
                                if (prev != null) g.drawLine(prev.x, prev.y, cx, cy);
                                prev = new Point(cx, cy);
                            }
                            if (prev != null && first != null) {
                                g.drawLine(prev.x, prev.y, first.x, first.y);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }

                g.setColor(new Color(60, 60, 60));
                g.drawString("Left drag = draw, Right drag / Shift drag = erase", 8, h - 8);

                g.dispose();
            }
        }

        interface TestRunner {
            Outcome run(ShapeContour source, PointCollection filledSource);
        }

        class TestSpec {
            final String name;
            final TestRunner runner;
            final JCheckBox enabled = new JCheckBox("Include", true);
            final JButton runButton = new JButton("Run");
            final JLabel status = new JLabel("Not run");
            final ViewportPanel viewport = new ViewportPanel();
            final JPanel panel = new JPanel(new BorderLayout(6, 6));

            TestSpec(String name, TestRunner runner) {
                this.name = name;
                this.runner = runner;

                enabled.setOpaque(false);
                status.setFont(status.getFont().deriveFont(Font.PLAIN, 12f));
                status.setForeground(new Color(80, 80, 80));

                panel.setOpaque(true);
                panel.setBackground(Color.WHITE);
                panel.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(new Color(190, 190, 190), 1, true),
                        new EmptyBorder(6, 6, 6, 6)
                ));
            }
        }

        final BiPredicate<Point, Point> samePoint = (a, b) ->
                a != null && b != null && a.x == b.x && a.y == b.y;

        final BiPredicate<Point, Point> nearPoint = (a, b) ->
                a != null && b != null && a.distance(b) <= 1.5;

        final Function<Point, String> fmtPoint = p ->
                p == null ? "null" : "(" + p.x + "," + p.y + ")";

        final Function<Point2D, String> fmtPoint2 = p ->
                p == null ? "null" : String.format("(%.2f,%.2f)", p.getX(), p.getY());

        final Function<Double, Boolean> finite = v ->
                v != null && !Double.isNaN(v) && !Double.isInfinite(v);

        final Function<ShapeContour, Integer> absWidth = sc -> sc == null ? 0 : Math.abs(sc.width());
        final Function<ShapeContour, Integer> absHeight = sc -> sc == null ? 0 : Math.abs(sc.height());

        final Function<ShapeContour, Set<Long>> pointSet = sc -> {
            Set<Long> out = new HashSet<>();
            if (sc == null) return out;
            for (Point p : sc) {
                long code = (((long) p.x) << 32) ^ (p.y & 0xffffffffL);
                out.add(code);
            }
            return out;
        };

        final Function<ShapeContour, PointCollection> collectInside = sc -> {
            PointCollection inside = new PointCollection();
            if (sc == null || sc.isEmpty()) return inside;
            Iterator<Point> it = sc.insideIterator();
            while (it.hasNext()) inside.add(it.next());
            return inside;
        };

        final Color baseFill = new Color(170, 170, 170, 110);
        final Color baseContour = new Color(45, 95, 255);
        final Color translatedColor = new Color(0, 150, 90);
        final Color expandColor = new Color(220, 50, 50);
        final Color contractColor = new Color(40, 160, 70);
        final Color halfwayColor = new Color(140, 50, 190);
        final Color oldHalfwayColor = new Color(180, 120, 30);
        final Color insideColor = new Color(120, 180, 240, 110);

        final ViewportPanel[] sourceViewport = new ViewportPanel[1];
        final JLabel[] sourceInfo = new JLabel[1];
        final DrawingCanvas[] drawingCanvas = new DrawingCanvas[1];
        final PointCollection[] sourcePixels = { new PointCollection() };
        final ShapeContour[] sourceContour = { ShapeContour.empty() };
        final String[] sourceLabel = { "Ellipse" };

        final Function<PointCollection, ShapeContour> buildContour = pixels -> {
            if (pixels == null || pixels.isEmpty()) return ShapeContour.empty();
            return new ShapeContour(new PointCollection(pixels));
        };

        final Function<String, PointCollection> presetBuilder = name -> {
            PointCollection pts = new PointCollection();

            switch (name) {
                case "Rectangle" -> {
                    int w = 56;
                    int h = 34;
                    for (int x = -w / 2; x <= w / 2; x++) {
                        for (int y = -h / 2; y <= h / 2; y++) {
                            pts.add(new Point(x, y));
                        }
                    }
                }
                case "Ellipse" -> {
                    int rx = 28;
                    int ry = 20;
                    for (int x = -rx - 1; x <= rx + 1; x++) {
                        for (int y = -ry - 1; y <= ry + 1; y++) {
                            double nx = x / (double) rx;
                            double ny = y / (double) ry;
                            if ((nx * nx) + (ny * ny) <= 1.0) {
                                pts.add(new Point(x, y));
                            }
                        }
                    }
                }
                case "Diamond" -> {
                    int rx = 30;
                    int ry = 22;
                    for (int x = -rx; x <= rx; x++) {
                        for (int y = -ry; y <= ry; y++) {
                            double d = Math.abs(x) / (double) rx + Math.abs(y) / (double) ry;
                            if (d <= 1.0) {
                                pts.add(new Point(x, y));
                            }
                        }
                    }
                }
                case "Star Blob" -> {
                    int limit = 34;
                    for (int x = -limit; x <= limit; x++) {
                        for (int y = -limit; y <= limit; y++) {
                            double theta = Math.atan2(y, x);
                            double r = Math.hypot(x, y);
                            double shell = 19.0
                                    + (6.5 * Math.cos(5.0 * theta))
                                    + (3.5 * Math.sin(3.0 * theta + 0.55))
                                    + (2.0 * Math.cos(theta - 0.4));
                            if (r <= shell) {
                                pts.add(new Point(x, y));
                            }
                        }
                    }
                }
                default -> {
                    int limit = 35;
                    for (int x = -limit; x <= limit; x++) {
                        for (int y = -limit; y <= limit; y++) {
                            double theta = Math.atan2(y + 2.0, x - 3.0);
                            double r = Math.hypot(x - 3.0, y + 2.0);
                            double shell = 23.0
                                    + (5.0 * Math.sin(2.0 * theta))
                                    + (4.0 * Math.cos(3.0 * theta + 0.9))
                                    + (2.0 * Math.sin(theta - 1.1));
                            if (r <= shell) {
                                pts.add(new Point(x, y));
                            }
                        }
                    }
                }
            }

            return pts;
        };

        final Consumer<PointCollection> applySource = pixels -> {
            sourcePixels[0] = pixels == null ? new PointCollection() : new PointCollection(pixels);
            sourceContour[0] = buildContour.apply(sourcePixels[0]);

            if (sourceViewport[0] != null) {
                Outcome preview = new Outcome("Current Source");
                preview.passed = !sourceContour[0].isEmpty();
                preview.summary = sourceContour[0].isEmpty()
                        ? "No valid contour could be extracted."
                        : String.format(
                        "%s | vertices=%d area=%.1f perim=%.1f | w=%d h=%d",
                        sourceLabel[0],
                        sourceContour[0].size(),
                        sourceContour[0].area(),
                        sourceContour[0].perimeter(),
                        Math.abs(sourceContour[0].width()),
                        Math.abs(sourceContour[0].height())
                );

                if (!sourcePixels[0].isEmpty()) {
                    preview.layers.add(new Layer("pixels", sourcePixels[0], baseFill));
                }
                if (!sourceContour[0].isEmpty()) {
                    preview.layers.add(new Layer("contour", sourceContour[0], baseContour, true, new BasicStroke(2.2f)));
                    preview.markers.add(new Marker("centroid", sourceContour[0].centroid(), Color.RED, 8));
                    preview.markers.add(new Marker("first", sourceContour[0].get(0), Color.BLACK, 7));
                }

                sourceViewport[0].setOutcome(preview);
                sourceViewport[0].setToolTipText(preview.summary);
            }

            if (sourceInfo[0] != null) {
                String text = sourceContour[0].isEmpty()
                        ? "<html><b>Source:</b> " + sourceLabel[0] + " &nbsp; <span style='color:#b00020'>No valid contour</span></html>"
                        : String.format(
                        "<html><b>Source:</b> %s &nbsp; <b>Vertices:</b> %d &nbsp; <b>Area:</b> %.1f &nbsp; <b>Perimeter:</b> %.1f</html>",
                        sourceLabel[0],
                        sourceContour[0].size(),
                        sourceContour[0].area(),
                        sourceContour[0].perimeter()
                );
                sourceInfo[0].setText(text);
            }
        };

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Throwable ignored) {
            }

            JFrame frame = new JFrame("ShapeContour Test Harness");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout(8, 8));

            JPanel leftPane = new JPanel(new BorderLayout(8, 8));
            leftPane.setBorder(new EmptyBorder(8, 8, 8, 8));

            JPanel controls = new JPanel(new GridBagLayout());
            controls.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(205, 205, 205), 1, true),
                    new EmptyBorder(8, 8, 8, 8)
            ));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.weightx = 1.0;

            JComboBox<String> presetCombo = new JComboBox<>(new String[] {
                    "Ellipse", "Rectangle", "Diamond", "Star Blob", "Lopsided Blob"
            });

            JButton loadPreset = new JButton("Load Preset");
            JButton useDrawing = new JButton("Use Drawing As Source");
            JButton seedDrawing = new JButton("Copy Source To Drawing");
            JButton clearDrawing = new JButton("Clear Drawing");
            JButton runAll = new JButton("Run All Checked Tests");
            JButton clearResults = new JButton("Clear Test Results");

            int row = 0;

            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
            controls.add(new JLabel("Preset shape:"), gbc);
            gbc.gridx = 1; gbc.gridy = row++; gbc.gridwidth = 2;
            controls.add(presetCombo, gbc);

            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
            controls.add(loadPreset, gbc);
            gbc.gridx = 1; gbc.gridy = row;
            controls.add(useDrawing, gbc);
            gbc.gridx = 2; gbc.gridy = row++;
            controls.add(seedDrawing, gbc);

            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
            controls.add(clearDrawing, gbc);
            gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
            controls.add(runAll, gbc);
            row++;

            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
            controls.add(clearResults, gbc);
            row++;

            JLabel instructions = new JLabel(
                    "<html><b>Notes:</b> Left-drag paints pixels, right-drag or Shift-drag erases. " +
                            "The canvas stores pixels; the source contour is extracted from them.</html>"
            );
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
            controls.add(instructions, gbc);

            leftPane.add(controls, BorderLayout.NORTH);

            JPanel sourcePanel = new JPanel(new BorderLayout(4, 4));
            sourcePanel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(205, 205, 205), 1, true),
                    new EmptyBorder(6, 6, 6, 6)
            ));

            JLabel sourceTitle = new JLabel("Current Source");
            sourceTitle.setFont(sourceTitle.getFont().deriveFont(Font.BOLD, 14f));
            sourcePanel.add(sourceTitle, BorderLayout.NORTH);

            sourceViewport[0] = new ViewportPanel();
            sourcePanel.add(sourceViewport[0], BorderLayout.CENTER);

            sourceInfo[0] = new JLabel("No source loaded.");
            sourcePanel.add(sourceInfo[0], BorderLayout.SOUTH);

            JPanel drawingPanel = new JPanel(new BorderLayout(4, 4));
            drawingPanel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(205, 205, 205), 1, true),
                    new EmptyBorder(6, 6, 6, 6)
            ));
            JLabel drawingTitle = new JLabel("Drawing Canvas");
            drawingTitle.setFont(drawingTitle.getFont().deriveFont(Font.BOLD, 14f));
            drawingPanel.add(drawingTitle, BorderLayout.NORTH);

            drawingCanvas[0] = new DrawingCanvas();
            drawingPanel.add(new JScrollPane(drawingCanvas[0]), BorderLayout.CENTER);

            JSplitPane workspaceSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sourcePanel, drawingPanel);
            workspaceSplit.setResizeWeight(0.38);
            workspaceSplit.setContinuousLayout(true);

            leftPane.add(workspaceSplit, BorderLayout.CENTER);

            JPanel testsHost = new JPanel();
            testsHost.setLayout(new BoxLayout(testsHost, BoxLayout.Y_AXIS));
            testsHost.setBackground(new Color(247, 247, 247));

            JScrollPane testsScroll = new JScrollPane(testsHost);
            testsScroll.setBorder(new EmptyBorder(8, 0, 8, 8));
            testsScroll.getVerticalScrollBar().setUnitIncrement(16);
            testsScroll.setPreferredSize(new Dimension(640, 900));

            java.util.List<TestSpec> specs = new ArrayList<>();
            @SuppressWarnings("unchecked")
            final Consumer<TestSpec>[] runOne = new Consumer[1];

            final Consumer<TestSpec> setNotRun = spec -> {
                spec.status.setText("Not run");
                spec.status.setForeground(new Color(80, 80, 80));
                spec.viewport.setOutcome(null);
                spec.viewport.setToolTipText(null);
                spec.panel.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(new Color(190, 190, 190), 1, true),
                        new EmptyBorder(6, 6, 6, 6)
                ));
            };

            Function<Outcome, Color> outcomeColor = outcome -> {
                if (outcome == null) return new Color(190, 190, 190);
                if (outcome.skipped) return new Color(215, 150, 40);
                return outcome.passed ? new Color(40, 150, 75) : new Color(190, 55, 55);
            };

            BiPredicate<ShapeContour, PointCollection> hasUsableSource = (sc, fill) ->
                    sc != null && !sc.isEmpty() && fill != null && !fill.isEmpty();

            java.util.function.BiConsumer<TestSpec, Outcome> pushOutcome = (spec, outcome) -> {
                Color c = outcomeColor.apply(outcome);
                String prefix = outcome == null ? "NONE" :
                        outcome.skipped ? "SKIPPED" :
                        outcome.passed ? "PASS" : "FAIL";

                spec.viewport.setOutcome(outcome);
                spec.viewport.setToolTipText(outcome == null ? null : outcome.summary);
                spec.status.setText("<html><b>" + prefix + "</b> - " + (outcome == null ? "" : outcome.summary) + "</html>");
                spec.status.setForeground(c.darker());
                spec.panel.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(c, 2, true),
                        new EmptyBorder(6, 6, 6, 6)
                ));
            };

            java.util.function.BiFunction<String, TestRunner, TestSpec> addSpec = (name, runner) -> {
                TestSpec spec = new TestSpec(name, runner);
                specs.add(spec);

                JPanel header = new JPanel(new BorderLayout(4, 4));
                header.setOpaque(false);

                JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                leftHeader.setOpaque(false);
                JLabel title = new JLabel(name);
                title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));

                leftHeader.add(title);
                leftHeader.add(Box.createHorizontalStrut(8));
                leftHeader.add(spec.enabled);
                leftHeader.add(spec.runButton);

                header.add(leftHeader, BorderLayout.WEST);
                header.add(spec.status, BorderLayout.CENTER);

                spec.panel.add(header, BorderLayout.NORTH);
                spec.panel.add(spec.viewport, BorderLayout.CENTER);

                spec.runButton.addActionListener(e -> runOne[0].accept(spec));

                testsHost.add(spec.panel);
                testsHost.add(Box.createVerticalStrut(8));

                return spec;
            };

            runOne[0] = spec -> {
                ShapeContour src = sourceContour[0];
                PointCollection fill = new PointCollection(sourcePixels[0]);
                Outcome outcome;

                try {
                    if (!hasUsableSource.test(src, fill)) {
                        outcome = new Outcome(spec.name);
                        outcome.passed = false;
                        outcome.summary = "Source contour is empty or invalid.";
                    } else {
                        outcome = spec.runner.run(src, fill);
                        if (outcome == null) {
                            outcome = new Outcome(spec.name);
                            outcome.passed = false;
                            outcome.summary = "Runner returned no result.";
                        }
                    }
                } catch (Throwable ex) {
                    outcome = new Outcome(spec.name);
                    outcome.passed = false;
                    outcome.summary = "Exception: " + ex.getClass().getSimpleName() +
                            (ex.getMessage() == null ? "" : " - " + ex.getMessage());
                }

                pushOutcome.accept(spec, outcome);
            };

            clearResults.addActionListener(e -> specs.forEach(setNotRun));

            runAll.addActionListener(e -> {
                for (TestSpec spec : specs) {
                    if (spec.enabled.isSelected()) {
                        runOne[0].accept(spec);
                    }
                }
            });

            loadPreset.addActionListener(e -> {
                sourceLabel[0] = String.valueOf(presetCombo.getSelectedItem());
                applySource.accept(presetBuilder.apply(sourceLabel[0]));
            });

            useDrawing.addActionListener(e -> {
                sourceLabel[0] = "User Drawing";
                applySource.accept(drawingCanvas[0].getPixelsCopy());
            });

            seedDrawing.addActionListener(e -> drawingCanvas[0].setPixels(new PointCollection(sourcePixels[0])));

            clearDrawing.addActionListener(e -> drawingCanvas[0].clearPixels());

            addSpec.apply("1. Basics / metrics / arrays", (source, filledSource) -> {
                Outcome o = new Outcome("Basics / metrics / arrays");

                double[] ang = source.copyAnglesFromTop();
                double[] raw = source.copyRawAngles();
                double[] rad = source.copyRadii();
                double[] arc = source.copyCumulativeArcLengths();
                Rectangle rect = source.rect();

                boolean arraysOk =
                        ang.length == source.size() &&
                                raw.length == source.size() &&
                                rad.length == source.size() &&
                                arc.length == source.size();

                boolean extremaOk =
                        source.topPoint() != null &&
                                source.bottomPoint() != null &&
                                source.leftPoint() != null &&
                                source.rightPoint() != null &&
                                source.firstX() == source.leftPoint().x &&
                                source.lastX() == source.rightPoint().x &&
                                source.topY() == source.topPoint().y &&
                                source.bottomY() == source.bottomPoint().y;

                boolean rectOk =
                        rect.x == source.firstX() &&
                                rect.y == source.topY() &&
                                rect.width == source.width() &&
                                rect.height == source.height();

                boolean firstOk = samePoint.test(source.get(0), source.topPoint());
                boolean arcZero = Math.abs(source.arcLengthTo(0)) < 1e-9 &&
                        Math.abs(source.normalizedArcLengthTo(0)) < 1e-9;

                o.passed =
                        source.size() > 2 &&
                                !source.isEmpty() &&
                                arraysOk &&
                                extremaOk &&
                                rectOk &&
                                firstOk &&
                                arcZero &&
                                source.perimeter() > 0.0 &&
                                source.area() > 0.0 &&
                                source.isCounterClockwise();

                o.summary = String.format(
                        "vertices=%d area=%.1f perim=%.1f first=%s centroid=%s",
                        source.size(),
                        source.area(),
                        source.perimeter(),
                        fmtPoint.apply(source.get(0)),
                        fmtPoint2.apply(source.centroid())
                );

                o.layers.add(new Layer("fill", filledSource, baseFill));
                o.layers.add(new Layer("contour", source, baseContour, true, new BasicStroke(2.25f)));
                o.markers.add(new Marker("centroid", source.centroid(), Color.RED, 8));
                o.markers.add(new Marker("top", source.topPoint(), new Color(0, 150, 0), 7));
                o.markers.add(new Marker("bottom", source.bottomPoint(), new Color(220, 120, 0), 7));
                o.markers.add(new Marker("left", source.leftPoint(), new Color(0, 120, 170), 7));
                o.markers.add(new Marker("right", source.rightPoint(), new Color(170, 0, 170), 7));
                return o;
            });

            addSpec.apply("2. Traversal / iterator / edges", (source, filledSource) -> {
                Outcome o = new Outcome("Traversal / iterator / edges");

                int iterCount = 0;
                for (Point ignored : source) iterCount++;

                final int[] forEachCount = {0};
                source.forEach(p -> forEachCount[0]++);

                final int[] indexedCount = {0};
                source.forEachIndexed((p, i) -> indexedCount[0]++);

                long streamCount = source.stream().count();
                long indexStreamCount = source.indices().count();
                long edgeStreamCount = source.edgeStream().count();

                boolean edgesOk = source.edges().size() == source.size();
                boolean edgeAdjacency = true;
                for (int i = 0; i < source.size(); i++) {
                    ShapeContour.Edge edge = source.edge(i);
                    if (!samePoint.test(edge.start(), source.get(i)) ||
                            !samePoint.test(edge.end(), source.get((i + 1) % source.size()))) {
                        edgeAdjacency = false;
                        break;
                    }
                }

                Point sample = source.get(Math.max(0, source.size() / 3));
                Point next = source.nextPoint(sample);
                Point prev = source.previousPoint(sample);
                ShapeContour.Edge wrapped = source.edge(-1);

                boolean wrappedOk =
                        samePoint.test(wrapped.start(), source.get(source.size() - 1)) &&
                                samePoint.test(wrapped.end(), source.get(0));

                boolean cycleOk =
                        samePoint.test(source.previousPoint(next), sample) &&
                                samePoint.test(source.nextPoint(prev), sample);

                o.passed =
                        iterCount == source.size() &&
                                forEachCount[0] == source.size() &&
                                indexedCount[0] == source.size() &&
                                streamCount == source.size() &&
                                indexStreamCount == source.size() &&
                                edgeStreamCount == source.size() &&
                                edgesOk &&
                                edgeAdjacency &&
                                wrappedOk &&
                                cycleOk;

                o.summary = "iter=" + iterCount + ", edges=" + source.edges().size() +
                        ", sample=" + fmtPoint.apply(sample);

                o.layers.add(new Layer("contour", source, baseContour, true, new BasicStroke(2.0f)));
                o.markers.add(new Marker("sample", sample, Color.RED, 8));
                o.markers.add(new Marker("next", next, translatedColor, 8));
                o.markers.add(new Marker("prev", prev, new Color(200, 120, 0), 8));
                o.markers.add(new Marker("last edge mid", wrapped.midpoint(), new Color(110, 0, 150), 7));
                return o;
            });

            addSpec.apply("3. Angle / arc / polar / interpolation", (source, filledSource) -> {
                Outcome o = new Outcome("Angle / arc / polar / interpolation");

                int i = Math.max(0, source.size() / 4);
                Point vertex = source.get(i);

                double angle = source.angleFromTop(i);
                double rawAngle = source.rawAngle(i);
                double radius = source.radius(i);
                double arc = source.arcLengthTo(i);
                double normArc = source.normalizedArcLengthTo(i);

                Point nearestByAngle = source.nearestByAngle(angle);
                int angleIndex = source.indexByAngle(angle);
                double radiusAtAngle = source.radiusAtAngle(angle);

                Point atArc = source.pointAtArcLength(arc);
                int idxAtArc = source.indexByArcLength(arc);
                Point atNormArc = source.pointAtNormalizedArc(normArc);
                int idxAtNormArc = source.indexByNormalizedArc(normArc);

                Point2D.Double polar = source.polarToCartesian(angle);
                Point nearestPolar = source.nearest(polar);

                Point2D.Double perimeterSample = source.pointOnPerimeterNormalized(0.37);
                Point interpolated = source.interpolate(perimeterSample);

                boolean ok =
                        nearestByAngle != null &&
                                angleIndex == source.indexOf(nearestByAngle) &&
                                Math.abs(radius - radiusAtAngle) <= 1.5 &&
                                nearPoint.test(atArc, vertex) &&
                                idxAtArc == i &&
                                nearPoint.test(atNormArc, vertex) &&
                                idxAtNormArc == i &&
                                polar != null &&
                                nearestPolar != null &&
                                perimeterSample != null &&
                                interpolated != null;

                o.passed = ok;
                o.summary = String.format(
                        "i=%d angle=%.3f raw=%.3f r=%.2f normArc=%.3f",
                        i, angle, rawAngle, radius, normArc
                );

                o.layers.add(new Layer("contour", source, baseContour, true, new BasicStroke(2.0f)));
                o.markers.add(new Marker("vertex", vertex, Color.RED, 8));
                o.markers.add(new Marker("byAngle", nearestByAngle, translatedColor, 7));
                o.markers.add(new Marker("atArc", atArc, new Color(215, 130, 0), 7));
                o.markers.add(new Marker("atNormArc", atNormArc, new Color(170, 0, 160), 7));
                o.markers.add(new Marker("polar", polar, Color.BLACK, 7));
                o.markers.add(new Marker("interp", interpolated, new Color(0, 130, 130), 7));
                return o;
            });

            addSpec.apply("4. Contains / inside / nearest", (source, filledSource) -> {
                Outcome o = new Outcome("Contains / inside / nearest");

                Iterator<Point> inIt = source.insideIterator();
                Point insidePoint = inIt.hasNext() ? inIt.next() : source.get(0);
                Point anotherInside = inIt.hasNext() ? inIt.next() : insidePoint;
                Point outsidePoint = new Point(
                        source.lastX() + Math.max(8, absWidth.apply(source) / 2 + 8),
                        source.topY() + Math.max(8, absHeight.apply(source) / 2 + 8)
                );

                PointCollection smallInsideCollection = new PointCollection();
                smallInsideCollection.add(insidePoint);
                smallInsideCollection.add(anotherInside);

                ShapeContour smaller = source.contract(Math.max(1, Math.min(6, Math.max(2, Math.min(absWidth.apply(source), absHeight.apply(source)) / 10))));
                boolean nestedOk = smaller.isEmpty() || source.inside(smaller);

                Point nearestInt = source.nearest(outsidePoint);
                Point nearestDouble = source.nearest(new Point2D.Double(outsidePoint.x + 0.35, outsidePoint.y + 0.65));

                Point a = source.get(0);
                Point b = source.get(1);
                Point interpolated = source.interpolate(new Point2D.Double(
                        ((a.x + b.x) / 2.0) + 0.27,
                        ((a.y + b.y) / 2.0) - 0.18
                ));

                o.passed =
                        source.contains(source.get(0)) &&
                                !source.contains(outsidePoint) &&
                                source.inside(insidePoint) &&
                                !source.inside(outsidePoint) &&
                                source.containsX(source.firstX()) &&
                                source.inside(smallInsideCollection) &&
                                nestedOk &&
                                nearestInt != null &&
                                nearestDouble != null &&
                                interpolated != null;

                o.summary = "inside=" + fmtPoint.apply(insidePoint) +
                        ", outside=" + fmtPoint.apply(outsidePoint);

                o.layers.add(new Layer("contour", source, baseContour, true, new BasicStroke(2.0f)));
                if (!smaller.isEmpty()) o.layers.add(new Layer("smaller", smaller, contractColor, true, new BasicStroke(1.7f)));
                o.markers.add(new Marker("inside", insidePoint, translatedColor, 8));
                o.markers.add(new Marker("outside", outsidePoint, expandColor, 8));
                o.markers.add(new Marker("nearest", nearestInt, new Color(160, 60, 0), 7));
                o.markers.add(new Marker("nearest2D", nearestDouble, new Color(80, 0, 160), 7));
                o.markers.add(new Marker("interp", interpolated, Color.BLACK, 7));
                return o;
            });

            addSpec.apply("5. Translate / equals / hashCode / toString / conversions", (source, filledSource) -> {
                Outcome o = new Outcome("Translate / equals / hashCode / toString / conversions");

                int dx = 7;
                int dy = -5;

                ShapeContour shifted = source.translate(dx, dy);
                ShapeContour exactCopy = source.translate(0, 0);

                boolean moved = true;
                for (Point p : source) {
                    if (!shifted.contains(new Point(p.x + dx, p.y + dy))) {
                        moved = false;
                        break;
                    }
                }

                boolean equalityOk = source.equals(exactCopy) && source.hashCode() == exactCopy.hashCode();
                boolean conversionOk =
                        source.toList().size() == source.size() &&
                                source.toPointCollection().size() == source.size() &&
                                source.stream().count() == source.size() &&
                                source.indices().count() == source.size();

                boolean textOk = source.toString().contains("vertices=") &&
                        source.toString().contains("perimeter=") &&
                        source.toString().contains("area=");

                o.passed = moved && equalityOk && conversionOk && textOk;
                o.summary = "shift=(" + dx + "," + dy + "), copyEquals=" + equalityOk;

                o.layers.add(new Layer("original", source, baseContour, true, new BasicStroke(2.1f)));
                o.layers.add(new Layer("translated", shifted, translatedColor, true, new BasicStroke(1.8f)));
                o.markers.add(new Marker("orig first", source.get(0), Color.RED, 8));
                o.markers.add(new Marker("shift first", shifted.get(0), translatedColor, 8));
                return o;
            });

            addSpec.apply("6. Expand", (source, filledSource) -> {
                Outcome o = new Outcome("Expand");

                int amount = Math.max(2, Math.min(7, Math.max(2, Math.min(absWidth.apply(source), absHeight.apply(source)) / 8)));
                ShapeContour expanded = source.expand(amount);

                o.passed =
                        !expanded.isEmpty() &&
                                expanded.inside(source) &&
                                expanded.area() >= source.area();

                o.summary = "expand(" + amount + ") -> area " +
                        String.format("%.1f", source.area()) + " -> " +
                        String.format("%.1f", expanded.area());

                o.layers.add(new Layer("original", source, baseContour, true, new BasicStroke(2.0f)));
                o.layers.add(new Layer("expanded", expanded, expandColor, true, new BasicStroke(1.8f)));
                return o;
            });

            addSpec.apply("7. Contract", (source, filledSource) -> {
                Outcome o = new Outcome("Contract");

                int amount = Math.max(1, Math.min(6, Math.max(1, Math.min(absWidth.apply(source), absHeight.apply(source)) / 10)));
                ShapeContour contracted = source.contract(amount);

                if (contracted.isEmpty()) {
                    o.skipped = true;
                    o.passed = false;
                    o.summary = "contract(" + amount + ") collapsed this source.";
                    o.layers.add(new Layer("original", source, baseContour, true, new BasicStroke(2.0f)));
                    return o;
                }

                o.passed =
                        source.inside(contracted) &&
                                contracted.area() <= source.area();

                o.summary = "contract(" + amount + ") -> area " +
                        String.format("%.1f", source.area()) + " -> " +
                        String.format("%.1f", contracted.area());

                o.layers.add(new Layer("original", source, baseContour, true, new BasicStroke(2.0f)));
                o.layers.add(new Layer("contracted", contracted, contractColor, true, new BasicStroke(1.9f)));
                return o;
            });

            addSpec.apply("8. Halfway", (source, filledSource) -> {
                Outcome o = new Outcome("Halfway");

                int outerStep = Math.max(2, Math.min(7, Math.max(2, Math.min(absWidth.apply(source), absHeight.apply(source)) / 8)));
                int innerStep = Math.max(1, Math.min(5, Math.max(1, Math.min(absWidth.apply(source), absHeight.apply(source)) / 11)));

                ShapeContour outer = source.expand(outerStep);
                ShapeContour inner = source.contract(innerStep);
                if (inner.isEmpty()) inner = source;

                ShapeContour mid = outer.halfway(inner);

                o.passed =
                        !mid.isEmpty() &&
                                outer.inside(mid) &&
                                mid.inside(inner);

                o.summary = "outer=expand(" + outerStep + "), inner=" +
                        (inner == source ? "source" : ("contract(" + innerStep + ")")) +
                        ", midway vertices=" + mid.size();

                o.layers.add(new Layer("outer", outer, expandColor, false, new BasicStroke(2.0f)));
                o.layers.add(new Layer("mid", mid, halfwayColor, true, new BasicStroke(2.2f)));
                o.layers.add(new Layer("inner", inner, contractColor, false, new BasicStroke(1.8f)));
                return o;
            });

            addSpec.apply("9. HalfwayOld", (source, filledSource) -> {
                Outcome o = new Outcome("HalfwayOld");

                int outerStep = Math.max(2, Math.min(7, Math.max(2, Math.min(absWidth.apply(source), absHeight.apply(source)) / 8)));
                int innerStep = Math.max(1, Math.min(5, Math.max(1, Math.min(absWidth.apply(source), absHeight.apply(source)) / 11)));

                ShapeContour outer = source.expand(outerStep);
                ShapeContour inner = source.contract(innerStep);
                if (inner.isEmpty()) inner = source;

                ShapeContour mid = outer.halfwayOld(inner);

                if (mid.isEmpty()) {
                    o.skipped = true;
                    o.passed = false;
                    o.summary = "halfwayOld returned empty for this source.";
                    o.layers.add(new Layer("outer", outer, expandColor, false, new BasicStroke(2.0f)));
                    o.layers.add(new Layer("inner", inner, contractColor, false, new BasicStroke(1.8f)));
                    return o;
                }

                o.passed = outer.inside(mid) && mid.inside(inner);
                o.summary = "halfwayOld vertices=" + mid.size() + ", area=" + String.format("%.1f", mid.area());

                o.layers.add(new Layer("outer", outer, expandColor, false, new BasicStroke(2.0f)));
                o.layers.add(new Layer("halfwayOld", mid, oldHalfwayColor, true, new BasicStroke(2.2f)));
                o.layers.add(new Layer("inner", inner, contractColor, false, new BasicStroke(1.8f)));
                return o;
            });

            addSpec.apply("10. Mutation: add / remove / addAll / removeAll / indexOf", (source, filledSource) -> {
                Outcome o = new Outcome("Mutation");

                ShapeContour mutated = new ShapeContour(source.toList());
                Set<Long> originalSet = pointSet.apply(source);

                Point addSingle = new Point(source.lastX() + 10, source.topY() + 2);
                while (mutated.contains(addSingle)) {
                    addSingle = new Point(addSingle.x + 2, addSingle.y + 1);
                }

                Point addA = new Point(source.firstX() - 10, source.bottomY() - 2);
                Point addB = new Point(source.lastX() + 12, source.bottomY() - 4);
                while (mutated.contains(addA)) addA = new Point(addA.x - 2, addA.y - 1);
                while (mutated.contains(addB)) addB = new Point(addB.x + 2, addB.y - 1);

                boolean addSingleOk = mutated.add(addSingle.x, addSingle.y);
                boolean containsAfterAdd = mutated.contains(addSingle);
                boolean indexOk = mutated.indexOf(addSingle) >= 0;
                boolean removeSingleOk = mutated.remove(addSingle.x, addSingle.y);
                boolean removedGone = !mutated.contains(addSingle);

                boolean addAllOk = mutated.addAll(Arrays.asList(addA, addB));
                ShapeContour afterAddAllSnapshot = new ShapeContour(mutated.toList());
                boolean removeAllOk = mutated.removeAll(Arrays.asList(addA, addB));
                boolean restored = pointSet.apply(mutated).equals(originalSet);

                o.passed =
                        addSingleOk &&
                                containsAfterAdd &&
                                indexOk &&
                                removeSingleOk &&
                                removedGone &&
                                addAllOk &&
                                removeAllOk &&
                                restored;

                o.summary = "single=" + addSingleOk + ", addAll=" + addAllOk + ", restored=" + restored;

                o.layers.add(new Layer("original", source, baseContour, false, new BasicStroke(2.0f)));
                o.layers.add(new Layer("afterAddAll", afterAddAllSnapshot, halfwayColor, true, new BasicStroke(1.9f)));
                o.markers.add(new Marker("single add", addSingle, expandColor, 8));
                o.markers.add(new Marker("addA", addA, new Color(0, 120, 180), 7));
                o.markers.add(new Marker("addB", addB, new Color(180, 120, 0), 7));
                return o;
            });

            addSpec.apply("11. xes / yes / angle map / arc index methods", (source, filledSource) -> {
                Outcome o = new Outcome("xes / yes / angle map / arc index methods");

                Set<Integer> xes = source.xes();
                Set<Integer> yes = source.yes();
                NavigableMap<Double, Point> angleMap = source.asAngleMap();

                int idxNorm0 = source.indexByNormalizedArc(0.0);
                int idxArc0 = source.indexByArcLength(0.0);
                Point pArc0 = source.pointAtArcLength(0.0);
                Point pNorm0 = source.pointAtNormalizedArc(0.0);

                Point firstAnglePoint = angleMap.isEmpty() ? null : angleMap.firstEntry().getValue();

                o.passed =
                        !xes.isEmpty() &&
                                !yes.isEmpty() &&
                                xes.contains(source.firstX()) &&
                                xes.contains(source.lastX()) &&
                                yes.contains(source.topY()) &&
                                yes.contains(source.bottomY()) &&
                                !angleMap.isEmpty() &&
                                idxNorm0 == 0 &&
                                idxArc0 == 0 &&
                                nearPoint.test(pArc0, source.get(0)) &&
                                nearPoint.test(pNorm0, source.get(0)) &&
                                firstAnglePoint != null;

                o.summary = "xCount=" + xes.size() + ", yCount=" + yes.size() + ", angleMap=" + angleMap.size();

                o.layers.add(new Layer("contour", source, baseContour, true, new BasicStroke(2.0f)));
                o.markers.add(new Marker("start", source.get(0), Color.RED, 8));
                o.markers.add(new Marker("first angle entry", firstAnglePoint, new Color(0, 140, 80), 7));
                return o;
            });

            addSpec.apply("12. insideIterator / forEachInside", (source, filledSource) -> {
                Outcome o = new Outcome("insideIterator / forEachInside");

                PointCollection insideA = new PointCollection();
                Iterator<Point> it = source.insideIterator();
                int iterCount = 0;
                while (it.hasNext()) {
                    Point p = it.next();
                    insideA.add(p);
                    iterCount++;
                }

                final PointCollection insideB = new PointCollection();
                final int[] forEachCount = {0};
                source.forEachInside(p -> {
                    insideB.add(p);
                    forEachCount[0]++;
                });

                boolean allInside = true;
                for (Point p : insideA) {
                    if (!source.inside(p)) {
                        allInside = false;
                        break;
                    }
                }

                o.passed =
                        iterCount > 0 &&
                                iterCount == forEachCount[0] &&
                                insideA.size() == insideB.size() &&
                                allInside;

                o.summary = "inside pixels=" + iterCount;

                o.layers.add(new Layer("inside", insideA, insideColor));
                o.layers.add(new Layer("contour", source, baseContour, false, new BasicStroke(2.0f)));
                return o;
            });

            addSpec.apply("13. Curvature / laplacian / second derivatives", (source, filledSource) -> {
                Outcome o = new Outcome("Curvature / laplacian / second derivatives");

                java.util.List<Double> laps = source.laplacians();
                java.util.List<Double> second = source.secondDerivatives();

                int sampleIndex = Math.max(0, source.size() / 5);
                Point sample = source.get(sampleIndex);
                double sampleLap = source.laplacianAt(sample);

                int maxAbsIndex = 0;
                double maxAbsValue = -1.0;
                for (int i = 0; i < laps.size(); i++) {
                    double v = laps.get(i);
                    if (finite.apply(v) && Math.abs(v) > maxAbsValue) {
                        maxAbsValue = Math.abs(v);
                        maxAbsIndex = i;
                    }
                }

                Point peak = source.get(Math.min(maxAbsIndex, source.size() - 1));
                boolean finiteAny = false;
                for (double v : laps) {
                    if (finite.apply(v)) {
                        finiteAny = true;
                        break;
                    }
                }

                o.passed =
                        laps.size() == source.size() &&
                                second.size() == source.size() &&
                                finiteAny &&
                                finite.apply(sampleLap);

                o.summary = String.format(
                        "laplacians=%d, second=%d, sampleLap=%.5f",
                        laps.size(), second.size(), sampleLap
                );

                o.layers.add(new Layer("contour", source, baseContour, true, new BasicStroke(2.0f)));
                o.markers.add(new Marker("sample", sample, Color.RED, 8));
                o.markers.add(new Marker("peak|lap|", peak, new Color(120, 0, 170), 8));
                return o;
            });

            addSpec.apply("14. Optional ShapeBounds overloads via reflection", (source, filledSource) -> {
                Outcome o = new Outcome("Optional ShapeBounds overloads via reflection");

                try {
                    Class<?> boundsClass = Class.forName("com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds");

                    Constructor<?> boundsCtor = null;
                    for (Constructor<?> ctor : boundsClass.getConstructors()) {
                        if (ctor.getParameterCount() == 1 &&
                                ctor.getParameterTypes()[0].isAssignableFrom(PointCollection.class)) {
                            boundsCtor = ctor;
                            break;
                        }
                    }

                    if (boundsCtor == null) {
                        try {
                            boundsCtor = boundsClass.getConstructor(PointCollection.class);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }

                    if (boundsCtor == null) {
                        o.skipped = true;
                        o.passed = false;
                        o.summary = "No ShapeBounds(PointCollection) constructor found.";
                        o.layers.add(new Layer("contour", source, baseContour, true, new BasicStroke(2.0f)));
                        return o;
                    }

                    Object bounds = boundsCtor.newInstance(new PointCollection(filledSource));

                    ShapeContour fromBoundsCtor = null;
                    for (Constructor<?> ctor : ShapeContour.class.getConstructors()) {
                        if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0] == boundsClass) {
                            Object built = ctor.newInstance(bounds);
                            if (built instanceof ShapeContour sc) {
                                fromBoundsCtor = sc;
                                break;
                            }
                        }
                    }

                    java.util.List<String> invoked = new ArrayList<>();
                    ShapeContour reflectedContour = null;
                    boolean sawBoolean = false;
                    boolean booleanOk = true;

                    for (Method method : ShapeContour.class.getMethods()) {
                        if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == boundsClass) {
                            Object target = java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? null : source;
                            Object result = method.invoke(target, bounds);
                            invoked.add(method.getName());

                            if (result instanceof ShapeContour sc && reflectedContour == null) {
                                reflectedContour = sc;
                            }
                            if (result instanceof Boolean b) {
                                sawBoolean = true;
                                booleanOk &= b;
                            }
                        }
                    }

                    if (fromBoundsCtor == null && reflectedContour == null && invoked.isEmpty()) {
                        o.skipped = true;
                        o.passed = false;
                        o.summary = "No ShapeBounds overloads were found on ShapeContour.";
                        o.layers.add(new Layer("contour", source, baseContour, true, new BasicStroke(2.0f)));
                        return o;
                    }

                    boolean ctorOk = fromBoundsCtor == null || !fromBoundsCtor.isEmpty();
                    boolean reflectedOk = reflectedContour == null || !reflectedContour.isEmpty();

                    o.passed = ctorOk && reflectedOk && (!sawBoolean || booleanOk);
                    o.summary = "ctor=" + (fromBoundsCtor != null) +
                            ", methods=" + invoked.size() +
                            (invoked.isEmpty() ? "" : ", names=" + invoked);

                    o.layers.add(new Layer("original", source, baseContour, true, new BasicStroke(2.0f)));
                    if (fromBoundsCtor != null) {
                        o.layers.add(new Layer("ctor(bounds)", fromBoundsCtor, new Color(0, 140, 90), false, new BasicStroke(1.8f)));
                    }
                    if (reflectedContour != null) {
                        o.layers.add(new Layer("method(bounds)", reflectedContour, new Color(170, 70, 190), true, new BasicStroke(1.8f)));
                    }
                    return o;

                } catch (Throwable ex) {
                    o.skipped = true;
                    o.passed = false;
                    o.summary = "ShapeBounds reflection unavailable: " + ex.getClass().getSimpleName() +
                            (ex.getMessage() == null ? "" : " - " + ex.getMessage());
                    o.layers.add(new Layer("contour", source, baseContour, true, new BasicStroke(2.0f)));
                    return o;
                }
            });

            specs.forEach(setNotRun);

            leftPane.setPreferredSize(new Dimension(700, 980));

            JPanel testsPane = new JPanel(new BorderLayout(6, 6));
            testsPane.setBorder(new EmptyBorder(8, 0, 8, 8));
            testsPane.setBackground(new Color(247, 247, 247));

            JLabel testsTitle = new JLabel("ShapeContour Tests");
            testsTitle.setFont(testsTitle.getFont().deriveFont(Font.BOLD, 16f));
            testsTitle.setBorder(new EmptyBorder(0, 0, 4, 0));
            testsPane.add(testsTitle, BorderLayout.NORTH);
            testsPane.add(testsScroll, BorderLayout.CENTER);

            frame.add(leftPane, BorderLayout.WEST);
            frame.add(testsPane, BorderLayout.CENTER);

            applySource.accept(presetBuilder.apply(sourceLabel[0]));

            frame.pack();
            frame.setMinimumSize(new Dimension(1380, 900));
            frame.setSize(new Dimension(1520, 980));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}