package com.elijahsarte.celtools.main.selectionui;

import com.elijahsarte.celtools.main.framefactory.ImageHandler;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.noExcept;

public class ImageEyedropperFrame extends JFrame {
    private final BufferedImage image;
    private final ImagePanel imagePanel;
    private final JLabel hexLabel;
    private final JLabel rgbLabel;
    private final JPanel colorPreview;
    private final ColorSpectrumPanel spectrumPanel;
    private final JButton okButton;

    private Color selectedColor = Color.WHITE;
    private Point selectedPixel = new Point(0, 0);

    private final CompletableFuture<String> resultFuture = new CompletableFuture<>();

    public ImageEyedropperFrame(BufferedImage image) {
        super("Image Eyedropper");
        this.image = image;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        imagePanel = new ImagePanel();
        JScrollPane scrollPane = new JScrollPane(imagePanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        rightPanel.setPreferredSize(new Dimension(260, 500));

        JLabel titleLabel = new JLabel("Selected Color");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(120, 60));
        colorPreview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        colorPreview.setBackground(selectedColor);
        colorPreview.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        hexLabel = new JLabel("Hex: " + toHex(selectedColor));
        hexLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        rgbLabel = new JLabel("RGB: " + selectedColor.getRed() + ", " + selectedColor.getGreen() + ", " + selectedColor.getBlue());
        rgbLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel posLabel = new JLabel("Pixel: (0, 0)");
        posLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        spectrumPanel = new ColorSpectrumPanel();
        spectrumPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        spectrumPanel.setPreferredSize(new Dimension(220, 220));
        spectrumPanel.setMaximumSize(new Dimension(220, 220));

        okButton = new JButton("OK");
        okButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        okButton.addActionListener(e -> {
            if (!resultFuture.isDone()) {
                resultFuture.complete(toHex(selectedColor));
            }
            dispose();
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (!resultFuture.isDone()) {
                    resultFuture.complete(null);
                }
            }
        });

        imagePanel.setSelectionListener((x, y, color) -> {
            selectedPixel = new Point(x, y);
            selectedColor = color;
            colorPreview.setBackground(color);
            hexLabel.setText("Hex: " + toHex(color));
            rgbLabel.setText("RGB: " + color.getRed() + ", " + color.getGreen() + ", " + color.getBlue());
            posLabel.setText("Pixel: (" + x + ", " + y + ")");
            spectrumPanel.setSelectedColor(color);
        });

        rightPanel.add(titleLabel);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(colorPreview);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(hexLabel);
        rightPanel.add(Box.createVerticalStrut(6));
        rightPanel.add(rgbLabel);
        rightPanel.add(Box.createVerticalStrut(6));
        rightPanel.add(posLabel);
        rightPanel.add(Box.createVerticalStrut(16));
        rightPanel.add(new JLabel("Color Position"));
        rightPanel.add(Box.createVerticalStrut(8));
        rightPanel.add(spectrumPanel);
        rightPanel.add(Box.createVerticalGlue());
        rightPanel.add(okButton);

        add(scrollPane, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        setSize(1000, 700);
        setLocationRelativeTo(null);

        if (image.getWidth() > 0 && image.getHeight() > 0) {
            Color initial = new Color(image.getRGB(0, 0), true);
            selectedColor = initial;
            colorPreview.setBackground(initial);
            hexLabel.setText("Hex: " + toHex(initial));
            rgbLabel.setText("RGB: " + initial.getRed() + ", " + initial.getGreen() + ", " + initial.getBlue());
            spectrumPanel.setSelectedColor(initial);
        }
    }

    public CompletableFuture<String> waitForSelection() {
        SwingUtilities.invokeLater(() -> setVisible(true));
        return resultFuture;
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private interface SelectionListener {
        void colorSelected(int x, int y, Color color);
    }

    private class ImagePanel extends JPanel {
        private double zoom = 1.0;
        private final double minZoom = 0.1;
        private final double maxZoom = 32.0;

        private Point dragStartScreen;
        private Point dragStartView;

        private SelectionListener selectionListener;

        public ImagePanel() {
            setBackground(Color.DARK_GRAY);
            updatePreferredSize();

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        selectPixel(e.getPoint());
                    } else if (SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isRightMouseButton(e)) {
                        startPan(e);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        selectPixel(e.getPoint());
                    } else if (dragStartScreen != null) {
                        pan(e);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragStartScreen = null;
                    dragStartView = null;
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    zoomAtPoint(e);
                }
            };

            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
            addMouseWheelListener(mouseHandler);
        }

        public void setSelectionListener(SelectionListener selectionListener) {
            this.selectionListener = selectionListener;
        }

        private void selectPixel(Point point) {
            int imgX = (int) Math.floor(point.x / zoom);
            int imgY = (int) Math.floor(point.y / zoom);

            if (imgX >= 0 && imgX < image.getWidth() && imgY >= 0 && imgY < image.getHeight()) {
                Color color = new Color(image.getRGB(imgX, imgY), true);
                if (selectionListener != null) {
                    selectionListener.colorSelected(imgX, imgY, color);
                }
                repaint();
            }
        }

        private void startPan(MouseEvent e) {
            JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
            if (viewport != null) {
                dragStartScreen = e.getPoint();
                dragStartView = viewport.getViewPosition();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
        }

        private void pan(MouseEvent e) {
            JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
            if (viewport == null || dragStartScreen == null || dragStartView == null) {
                return;
            }

            int dx = e.getX() - dragStartScreen.x;
            int dy = e.getY() - dragStartScreen.y;

            Point newPos = new Point(dragStartView.x - dx, dragStartView.y - dy);

            newPos.x = Math.max(0, Math.min(newPos.x, getWidth() - viewport.getWidth()));
            newPos.y = Math.max(0, Math.min(newPos.y, getHeight() - viewport.getHeight()));

            viewport.setViewPosition(newPos);
        }

        private void zoomAtPoint(MouseWheelEvent e) {
            JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
            if (viewport == null) {
                return;
            }

            Point mousePoint = e.getPoint();
            double oldZoom = zoom;

            if (e.getPreciseWheelRotation() < 0) {
                zoom *= 1.1;
            } else {
                zoom /= 1.1;
            }

            zoom = Math.max(minZoom, Math.min(maxZoom, zoom));
            if (zoom == oldZoom) {
                return;
            }

            double scale = zoom / oldZoom;

            Point viewPos = viewport.getViewPosition();
            int newX = (int) ((mousePoint.x + viewPos.x) * scale - mousePoint.x);
            int newY = (int) ((mousePoint.y + viewPos.y) * scale - mousePoint.y);

            updatePreferredSize();

            SwingUtilities.invokeLater(() -> {
                viewport.setViewPosition(new Point(
                        Math.max(0, Math.min(newX, getWidth() - viewport.getWidth())),
                        Math.max(0, Math.min(newY, getHeight() - viewport.getHeight()))
                ));
                revalidate();
                repaint();
            });
        }

        private void updatePreferredSize() {
            int w = Math.max(1, (int) Math.round(image.getWidth() * zoom));
            int h = Math.max(1, (int) Math.round(image.getHeight() * zoom));
            setPreferredSize(new Dimension(w, h));
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            AffineTransform at = AffineTransform.getScaleInstance(zoom, zoom);
            g2.drawRenderedImage(image, at);

            int x = (int) Math.round(selectedPixel.x * zoom);
            int y = (int) Math.round(selectedPixel.y * zoom);
            int size = Math.max(6, (int) Math.round(zoom));

            g2.setColor(Color.WHITE);
            g2.drawRect(x - size / 2, y - size / 2, size, size);
            g2.setColor(Color.BLACK);
            g2.drawRect(x - size / 2 - 1, y - size / 2 - 1, size + 2, size + 2);

            g2.dispose();
        }
    }

    private static class ColorSpectrumPanel extends JPanel {
        private Color selectedColor = Color.WHITE;

        public void setSelectedColor(Color selectedColor) {
            this.selectedColor = selectedColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();

            Graphics2D g2 = (Graphics2D) g.create();

            for (int y = 0; y < h; y++) {
                float brightness = 1.0f - (float) y / Math.max(1, h - 1);
                for (int x = 0; x < w; x++) {
                    float hue = (float) x / Math.max(1, w - 1);
                    g2.setColor(Color.getHSBColor(hue, 1.0f, brightness));
                    g2.drawLine(x, y, x, y);
                }
            }

            float[] hsb = Color.RGBtoHSB(selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue(), null);
            int markerX = (int) (hsb[0] * (w - 1));
            int markerY = (int) ((1.0f - hsb[2]) * (h - 1));

            g2.setColor(Color.WHITE);
            g2.drawOval(markerX - 5, markerY - 5, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawOval(markerX - 6, markerY - 6, 12, 12);

            g2.dispose();
        }
    }

    public static CompletableFuture<String> pickColorAsync(BufferedImage image) {
        ImageEyedropperFrame frame = new ImageEyedropperFrame(image);
        return frame.waitForSelection();
    }
    public static CompletableFuture<String> pickColorAsync(ImageHandler image) {
        return pickColorAsync(image.getImage());
    }

    public static String pickColor(BufferedImage image) throws ExecutionException, InterruptedException {
        return pickColorAsync(image).get();
    }
    public static String pickColor(ImageHandler image) throws ExecutionException, InterruptedException {
        return pickColor(image.getImage());
    }

    public static Point pickPoint(BufferedImage image) {
        ImageEyedropperFrame frame = new ImageEyedropperFrame(image);
        noExcept(() -> frame.waitForSelection().get());
        return frame.selectedPixel;
    }
    public static Point pickPoint(ImageHandler image) {
        return pickPoint(image.getImage());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BufferedImage testImage = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < testImage.getHeight(); y++) {
                for (int x = 0; x < testImage.getWidth(); x++) {
                    int r = (x * 255) / (testImage.getWidth() - 1);
                    int g = (y * 255) / (testImage.getHeight() - 1);
                    int b = 128;
                    int rgb = (r << 16) | (g << 8) | b;
                    testImage.setRGB(x, y, rgb);
                }
            }
            pickColorAsync(testImage).thenAccept(hex -> {
                System.out.println("Selected hex: " + hex);
            });
        });
    }
}