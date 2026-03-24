package com.elijahsarte.celtools.main.selectionui;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public class FreeformSelectionManager {

    private static boolean instantiated = false;
    private static final Preferences SELECTION_PREFS = Preferences.userNodeForPackage(FreeformSelectionManager.class);
    private static final String PREF_KEY_PREFIX = "ff_selection_";
    private static AutoRestoreMode autoRestoreMode = AutoRestoreMode.NONE;

    private final SelectionTransform transform;
    private final String frameName;
    private final boolean freeformDraw;
    private final int imageHashCode;

    private JFrame cropperFrame = null;
    private final CompletableFuture<Map<Double, List<Double>>> drawingAxis = new CompletableFuture<>();
    private final CompletableFuture<Rectangle> cropBox = new CompletableFuture<>();
    private StoredSelection deferredSelection = null;
    private FreeformSelectionWindow.ToolMode activeTool = FreeformSelectionWindow.ToolMode.NORMAL;
    private JDialog colorPickerDialog;
    private JLabel colorPickerRgbLabel;
    private JLabel colorPickerHsvLabel;
    private JPanel colorPickerSwatch;


    public static void main(String[] args) {
        try {
//            ImageHandler testImgHandler = new ImageHandler(new File("C:/Test/image.jpeg"));
            ImageHandler testImgHandler = new ImageHandler(new File("C:\\Users\\Other user\\Documents\\celautofill_sandbox\\base_cel.png"));
            testImgHandler.loadImage(); testImgHandler.loadGuiImage();
            new FreeformSelectionManager(testImgHandler.getGuiImage(), SelectionTransform.empty());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    public FreeformSelectionManager(BufferedImage image, String frameName, SelectionTransform transform, boolean freeformDraw) {
        this.frameName = frameName;
        this.transform = transform;
        this.freeformDraw = freeformDraw;
        this.imageHashCode = computeImageHashCode(image);
        if (FreeformSelectionManager.instantiated) {
//            throw new ExceptionInInitializerError("Tried to re-instantiate when a singleton instance already exists.");
        }
        FreeformSelectionManager.instantiated = true;

        StoredSelection storedSelection = SelectionStorage.load(this.imageHashCode);
        RestoreAction restoreAction = determineRestoreAction(storedSelection);
        if (restoreAction == RestoreAction.APPLY_AND_COMPLETE) {
            completeWithStoredSelection(storedSelection);
            FreeformSelectionManager.instantiated = false;
            return;
        } else if (restoreAction == RestoreAction.APPLY_TO_UI) {
            this.deferredSelection = storedSelection;
        }

        createGUI(image);
    }
    public FreeformSelectionManager(BufferedImage image, SelectionTransform transform, String frameName) {
        this(image, frameName, transform, true);
    }
    public FreeformSelectionManager(BufferedImage image, String frameName) {
        this(image, SelectionTransform.empty(), frameName);
    }
    public FreeformSelectionManager(BufferedImage image, SelectionTransform transform, boolean freeformDraw) {
        this(image, "Cropper", transform, freeformDraw);
    }
    public FreeformSelectionManager(BufferedImage image, boolean freeformDraw) {
        this(image, SelectionTransform.empty(), freeformDraw);
    }
    public FreeformSelectionManager(BufferedImage image, SelectionTransform transform) {
        this(image, transform, true);
    }
    public FreeformSelectionManager(BufferedImage image) {
        this(image, SelectionTransform.empty());
    }


    private void createGUI(BufferedImage image) {


        FreeformSelectionWindow cropper = new FreeformSelectionWindow(image, transform, freeformDraw);
        applyDeferredSelectionIfNeeded(cropper);
        cropper.setColorPickListener(color -> SwingUtilities.invokeLater(() -> showColorPickerInfo(color)));
        cropper.setToolMode(activeTool);

        cropper.setAutoFit(false);
        cropper.setAutoscrolls(true);

        cropperFrame = new JFrame(frameName);
        cropperFrame.setName(frameName);
        cropperFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        cropperFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (freeformDraw) drawingAxis.complete(cropper.getDrawingAxis());
                else cropBox.complete(cropper.getCropBox());
                destroy();
            }

            public void windowClosed(WindowEvent e) {
                windowClosing(e);
            }
        });
        cropperFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                positionColorPickerDialog();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                positionColorPickerDialog();
            }
        });

        /*
        cropperFrame.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                cropperFrame.setTitle(frameName + "[" + e.getX() + ", " + e.getY() + "]");
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                cropperFrame.setTitle(frameName + "[" + e.getX() + ", " + e.getY() + "]");
            }
        });*/

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int horizDef = image.getWidth() >= screenSize.getWidth() ? JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
                : JScrollPane.HORIZONTAL_SCROLLBAR_NEVER;
        int vertDef = (image.getHeight() + 20) >= screenSize.getHeight() ? JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
                : JScrollPane.VERTICAL_SCROLLBAR_NEVER;
//        cropperFrame.setPreferredSize(new Dimension(
//                (int) Math.min(image.getWidth(), screenSize.getWidth() / 1.1),
//                (int) Math.min(image.getHeight(), (screenSize.getHeight() - 20) / 1.1)));


        {
            JScrollPane scrollPane = new JScrollPane(cropper);
            scrollPane.setPreferredSize(new Dimension(
                    (int) Math.min(image.getWidth(), (screenSize.getWidth() / 1.1) - 50),
                    (int) Math.min(image.getHeight(), ((screenSize.getHeight() - 20) / 1.1) - 50)));

            JLabel statusLabel = new JLabel("Mouse: (0, 0) | Image: -- | Zoom: 1.00x");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            statusLabel.putClientProperty("update", (Consumer<MouseEvent>) e -> statusLabel.setText("Mouse: (" + MathEx.floorInt(MathEx.divide(e.getX() - cropper.getImageOffsetX(), cropper.getZoomFactor())) + ", " + MathEx.floorInt(MathEx.divide(e.getY() - cropper.getImageOffsetY(), cropper.getZoomFactor())) + ") | Image: " + image.getWidth() + "x" + image.getHeight() + " | Zoom: " + MathEx.round(cropper.getZoomFactor(), 0.01) + "x"));


//            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
//            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scrollPane.setHorizontalScrollBarPolicy(horizDef);
            scrollPane.setVerticalScrollBarPolicy(vertDef);

            JScrollBar horizontal = scrollPane.getHorizontalScrollBar();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();

            horizontal.addAdjustmentListener(e -> {
                cropper.repaint();
                refreshStatusLabel(statusLabel, cropper);
            });
            vertical.addAdjustmentListener(e -> {
                cropper.repaint();
                refreshStatusLabel(statusLabel, cropper);
            });

            cropper.addMouseWheelListener(e -> {
                if (!e.isAltDown()) {
                    JScrollBar barInQuestion = e.isShiftDown() ? horizontal : vertical;
                    barInQuestion.setValue(barInQuestion.getValue() + e.getWheelRotation() * 25);
                    barInQuestion.getAdjustmentListeners()[0].adjustmentValueChanged(new AdjustmentEvent(barInQuestion, 0, 0, 0));
                    refreshStatusLabel(statusLabel, cropper);
                    return;
                }
                if (cropper.getZoomFactor() > 1) {
                    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                } else {
//                    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
//                    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
                    scrollPane.setHorizontalScrollBarPolicy(horizDef);
                    scrollPane.setVerticalScrollBarPolicy(vertDef);
                }
                cropper.updateSizeForZoom();
                ((Consumer<MouseEvent>) statusLabel.getClientProperty("update")).accept(e);
                scrollPane.revalidate();
                scrollPane.repaint();
            });


            cropper.addMouseMotionListener(new MouseMotionListener() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    ((Consumer<MouseEvent>) statusLabel.getClientProperty("update")).accept(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    mouseMoved(e);
                }
            });


            JPanel containerPanel = new JPanel(new BorderLayout());
            containerPanel.add(scrollPane, BorderLayout.CENTER);
            containerPanel.add(statusLabel, BorderLayout.SOUTH);
            cropperFrame.setContentPane(containerPanel);
//            cropperFrame.add(scrollPane);

        }


        {

            JMenuBar bar = new JMenuBar();
            bar.setOpaque(true);
            bar.setBackground(Color.WHITE);
            JMenu utils = new JMenu("File");
            JMenu info = new JMenu("Info");
            JMenu tools = new JMenu("Tools");


            JMenuItem saveBtn = new JMenuItem("Save selection");
            saveBtn.addActionListener((ActionEvent e) -> {
                saveSelectionForImage(cropper);
                if (freeformDraw) this.drawingAxis.complete(cropper.getDrawingAxis());
                else this.cropBox.complete(cropper.getCropBox());
                this.destroy();
            });
            saveBtn.setMnemonic(KeyEvent.VK_S);
            saveBtn.setDisplayedMnemonicIndex(0);
            saveBtn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));

            JMenuItem clearBtn = new JMenuItem("Clear selection");
            clearBtn.addActionListener((ActionEvent e) -> cropper.clearDrawing());
            clearBtn.setMnemonic(KeyEvent.VK_L);
            clearBtn.setDisplayedMnemonicIndex(1);
            clearBtn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));


            JMenuItem exportImageBtn = new JMenuItem("Export image");
            exportImageBtn.addActionListener(e -> exportImage(cropper, false));

            JMenuItem exportImageWithComponentsBtn = new JMenuItem("Export image with components");
            exportImageWithComponentsBtn.addActionListener(e -> exportImage(cropper, true));

            JMenuItem cropBoxBtn = new JMenuItem("Get drawing data as arg");
            cropBoxBtn.addActionListener((ActionEvent e) -> {

                JPanel drawingAxisArgInfo = new JPanel();
                drawingAxisArgInfo.setLayout(new BoxLayout(drawingAxisArgInfo, BoxLayout.Y_AXIS));
                drawingAxisArgInfo.add(new JLabel("NOTE: This arg data will ONLY work if the previously selected crop box data is the same (if any). Otherwise, it will screw up due to lack of correct positional awareness."));

                String drawingAxisArg = freeformDraw ? Main.encodeFreeformData(cropper.getDrawingAxis()) : ProgrammingEx.varOper(cropper.getCropBox(), r -> "[x=" + r.x + ",y=" + r.y + ",width=" + r.width + ",height=" + r.height + "]");
                drawingAxisArgInfo.add(new JTextField(drawingAxisArg, 10));
                JOptionPane.showMessageDialog(null, drawingAxisArgInfo, "Drawing data as command line arg", JOptionPane.INFORMATION_MESSAGE);

            });
            cropBoxBtn.setMnemonic(KeyEvent.VK_A);
            cropBoxBtn.setDisplayedMnemonicIndex(2);
            cropBoxBtn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK));

            JMenuItem insertComponentBtn = new JMenuItem("Insert component under arg");
            insertComponentBtn.addActionListener(e -> insertComponentFromArg(cropper));


            utils.add(saveBtn);
            utils.add(clearBtn);
            info.add(cropBoxBtn);
            info.add(insertComponentBtn);
            info.add(exportImageBtn);
            info.add(exportImageWithComponentsBtn);

            ButtonGroup toolGroup = new ButtonGroup();
            JRadioButtonMenuItem normalTool = new JRadioButtonMenuItem("Normal", true);
            JRadioButtonMenuItem colorPickerTool = new JRadioButtonMenuItem("Color picker");
            toolGroup.add(normalTool);
            toolGroup.add(colorPickerTool);
            normalTool.addActionListener(e -> setToolMode(cropper, FreeformSelectionWindow.ToolMode.NORMAL));
            colorPickerTool.addActionListener(e -> setToolMode(cropper, FreeformSelectionWindow.ToolMode.COLOR_PICKER));
            if (activeTool == FreeformSelectionWindow.ToolMode.COLOR_PICKER) {
                colorPickerTool.setSelected(true);
            } else {
                normalTool.setSelected(true);
            }
            tools.add(normalTool);
            tools.add(colorPickerTool);

            bar.add(utils);
            bar.add(info);
            bar.add(tools);
            cropperFrame.setJMenuBar(bar);

        }

        cropper.addPropertyChangeListener("zoom", evt -> {
            cropper.repaint();
        });

        cropperFrame.pack();
        cropperFrame.toFront();
        cropperFrame.requestFocus();
        cropperFrame.setResizable(true);
        cropperFrame.setVisible(true);


    }

    @SuppressWarnings("unchecked")
    private static void refreshStatusLabel(JLabel statusLabel, FreeformSelectionWindow cropper) {
        if (statusLabel == null || cropper == null) return;
        Consumer<MouseEvent> updater = (Consumer<MouseEvent>) statusLabel.getClientProperty("update");
        if (updater == null) return;
        Point mouse = cropper.getMousePosition();
        if (mouse == null) return;
        MouseEvent synthetic = new MouseEvent(cropper,
                MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(),
                0,
                mouse.x,
                mouse.y,
                0,
                false);
        updater.accept(synthetic);
    }

    private void setToolMode(FreeformSelectionWindow cropper, FreeformSelectionWindow.ToolMode mode) {
        if (cropper == null) return;
        FreeformSelectionWindow.ToolMode resolved = mode == null ? FreeformSelectionWindow.ToolMode.NORMAL : mode;
        this.activeTool = resolved;
        cropper.setToolMode(resolved);
        if (resolved != FreeformSelectionWindow.ToolMode.COLOR_PICKER) {
            hideColorPickerWindow();
        }
    }

    private void showColorPickerInfo(Color color) {
        if (color == null) return;
        if (activeTool != FreeformSelectionWindow.ToolMode.COLOR_PICKER) {
            return;
        }
        ensureColorPickerDialog();
        if (colorPickerSwatch != null) {
            colorPickerSwatch.setBackground(color);
        }
        if (colorPickerRgbLabel != null) {
            String alphaText = color.getAlpha() < 255 ? " (A=" + color.getAlpha() + ")" : "";
            colorPickerRgbLabel.setText(String.format("RGB: %d, %d, %d%s",
                    color.getRed(), color.getGreen(), color.getBlue(), alphaText));
        }
        if (colorPickerHsvLabel != null) {
            float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
            int h = Math.round(hsv[0] * 360f);
            int s = Math.round(hsv[1] * 100f);
            int v = Math.round(hsv[2] * 100f);
            colorPickerHsvLabel.setText(String.format("HSV: %d deg, %d%%, %d%%", h, s, v));
        }
        if (colorPickerDialog != null) {
            colorPickerDialog.pack();
            positionColorPickerDialog();
            colorPickerDialog.setVisible(true);
        }
    }

    private void ensureColorPickerDialog() {
        if (colorPickerDialog != null) {
            return;
        }
        colorPickerRgbLabel = new JLabel("RGB: --");
        colorPickerHsvLabel = new JLabel("HSV: --");
        colorPickerSwatch = new JPanel();
        colorPickerSwatch.setPreferredSize(new Dimension(60, 60));
        colorPickerSwatch.setOpaque(true);
        colorPickerSwatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        JPanel container = new JPanel();
        container.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(colorPickerSwatch);
        container.add(Box.createVerticalStrut(8));
        container.add(colorPickerRgbLabel);
        container.add(Box.createVerticalStrut(4));
        container.add(colorPickerHsvLabel);

        colorPickerDialog = new JDialog(cropperFrame, "Color picker", false);
        colorPickerDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        colorPickerDialog.setResizable(false);
        colorPickerDialog.setContentPane(container);
        colorPickerDialog.pack();
        positionColorPickerDialog();
    }

    private void positionColorPickerDialog() {
        if (colorPickerDialog == null || cropperFrame == null) {
            return;
        }
        if (!cropperFrame.isShowing()) {
            return;
        }
        try {
            Point frameLocation = cropperFrame.getLocationOnScreen();
            int x = frameLocation.x;
            int y = frameLocation.y + Math.max(0, cropperFrame.getHeight() - colorPickerDialog.getHeight());
            colorPickerDialog.setLocation(x, y);
        } catch (IllegalComponentStateException ignored) {
        }
    }

    private void hideColorPickerWindow() {
        if (colorPickerDialog != null) {
            colorPickerDialog.setVisible(false);
        }
    }
    private void saveSelectionForImage(FreeformSelectionWindow cropper) {
        if (cropper == null) return;

        try {
            StoredSelection selection;
            if (freeformDraw) {
                Map<Double, List<Double>> axisData = cropper.getDrawingAxis();
                List<Point> orderedPoints = cropper.getOrderedDrawingPoints();
                selection = StoredSelection.forFreeform(axisData, orderedPoints);
            } else {
                Rectangle rect = cropper.getCropBox();
                if (rect == null) {
                    SelectionStorage.discard(imageHashCode);
                    return;
                }
                selection = StoredSelection.forCrop(rect);
            }
            SelectionStorage.save(imageHashCode, selection);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(cropperFrame,
                    "Unable to store selection information:\n" + ex.getMessage(),
                    "Selection storage failed",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void applyDeferredSelectionIfNeeded(FreeformSelectionWindow cropper) {
        if (cropper == null || deferredSelection == null) {
            return;
        }

        try {
            if (freeformDraw && deferredSelection.isFreeform()) {
                Map<Double, List<Double>> axisCopy = deferredSelection.axisData();
                List<Point> points = deferredSelection.orderedPoints();
                cropper.loadDrawingFromArgument(axisCopy, points);
            } else if (!freeformDraw && !deferredSelection.isFreeform()) {
                Rectangle appliedRect = deferredSelection.appliedCropBox();
                Rectangle originalRect = appliedRect == null ? null : (transform == null ? appliedRect : transform.unapply(appliedRect));
                cropper.loadCropBox(originalRect);
            }
        } finally {
            deferredSelection = null;
        }
    }

    private void completeWithStoredSelection(StoredSelection selection) {
        if (selection == null) {
            return;
        }
        if (freeformDraw && selection.isFreeform()) {
            this.drawingAxis.complete(selection.axisData());
        } else if (!freeformDraw && !selection.isFreeform()) {
            Rectangle rect = selection.appliedCropBox();
            this.cropBox.complete(rect);
        } else {
            SelectionStorage.discard(imageHashCode);
        }
    }

    private RestoreAction determineRestoreAction(StoredSelection storedSelection) {
        if (storedSelection == null) {
            return RestoreAction.NONE;
        }

        if (storedSelection.isFreeform() != freeformDraw) {
            if (autoRestoreMode == AutoRestoreMode.NO_TO_ALL_AND_DISCARD_ALL) {
                SelectionStorage.discard(imageHashCode);
            }
            return RestoreAction.NONE;
        }

        return switch (autoRestoreMode) {
            case YES_TO_ALL -> RestoreAction.APPLY_TO_UI;
            case YES_TO_AND_SAVE_ALL -> RestoreAction.APPLY_AND_COMPLETE;
            case NO_TO_ALL -> RestoreAction.NONE;
            case NO_TO_ALL_AND_DISCARD_ALL -> {
                SelectionStorage.discard(imageHashCode);
                yield RestoreAction.NONE;
            }
            case NONE -> promptForRestoreDecision(storedSelection);
        };
    }

    private RestoreAction promptForRestoreDecision(StoredSelection storedSelection) {
        if (storedSelection == null) {
            return RestoreAction.NONE;
        }
        Object[] options = new Object[] {
                "Yes",
                "No",
                "Yes to All",
                "Yes to and Save All",
                "No to All",
                "No to All and Discard All"
        };
        String title = "Restore selection";
        String message = "A saved selection already exists for this image (" + frameName + ").\nWould you like to restore it?";
        int choice = JOptionPane.showOptionDialog(
                null,
                message,
                title,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        return switch (choice) {
            case 0 -> RestoreAction.APPLY_TO_UI;
            case 1, JOptionPane.CLOSED_OPTION -> RestoreAction.NONE;
            case 2 -> {
                autoRestoreMode = AutoRestoreMode.YES_TO_ALL;
                yield RestoreAction.APPLY_TO_UI;
            }
            case 3 -> {
                autoRestoreMode = AutoRestoreMode.YES_TO_AND_SAVE_ALL;
                yield RestoreAction.APPLY_AND_COMPLETE;
            }
            case 4 -> {
                autoRestoreMode = AutoRestoreMode.NO_TO_ALL;
                yield RestoreAction.NONE;
            }
            case 5 -> {
                autoRestoreMode = AutoRestoreMode.NO_TO_ALL_AND_DISCARD_ALL;
                SelectionStorage.discard(imageHashCode);
                yield RestoreAction.NONE;
            }
            default -> RestoreAction.NONE;
        };
    }

    private static Map<Double, List<Double>> copyAxisData(Map<Double, List<Double>> source) {
        Map<Double, List<Double>> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<Double, List<Double>> entry : source.entrySet()) {
            List<Double> values = entry.getValue();
            copy.put(entry.getKey(), values == null ? new ArrayList<>() : new ArrayList<>(values));
        }
        return copy;
    }

    private static List<Point> copyPointList(List<Point> source) {
        List<Point> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (Point point : source) {
            if (point == null) continue;
            copy.add(new Point(point));
        }
        return copy;
    }

    private enum AutoRestoreMode {
        NONE,
        YES_TO_ALL,
        YES_TO_AND_SAVE_ALL,
        NO_TO_ALL,
        NO_TO_ALL_AND_DISCARD_ALL
    }

    private enum RestoreAction {
        NONE,
        APPLY_TO_UI,
        APPLY_AND_COMPLETE
    }

    private static final class StoredSelection implements Serializable {
        private static final long serialVersionUID = 1L;

        private final boolean freeform;
        private final Map<Double, List<Double>> axisData;
        private final List<Point> orderedPoints;
        private final Rectangle appliedCropBox;

        private StoredSelection(boolean freeform, Map<Double, List<Double>> axisData, List<Point> orderedPoints, Rectangle appliedCropBox) {
            this.freeform = freeform;
            this.axisData = axisData;
            this.orderedPoints = orderedPoints;
            this.appliedCropBox = appliedCropBox == null ? null : new Rectangle(appliedCropBox);
        }

        static StoredSelection forFreeform(Map<Double, List<Double>> axisData, List<Point> orderedPoints) {
            return new StoredSelection(true, copyAxisData(axisData), copyPointList(orderedPoints), null);
        }

        static StoredSelection forCrop(Rectangle rect) {
            return new StoredSelection(false, null, null, rect);
        }

        boolean isFreeform() {
            return freeform;
        }

        Map<Double, List<Double>> axisData() {
            return axisData == null ? new LinkedHashMap<>() : copyAxisData(axisData);
        }

        List<Point> orderedPoints() {
            return orderedPoints == null ? new ArrayList<>() : copyPointList(orderedPoints);
        }

        Rectangle appliedCropBox() {
            return appliedCropBox == null ? null : new Rectangle(appliedCropBox);
        }
    }

    private static final class SelectionStorage {

        private static void save(int imageHash, StoredSelection selection) {
            if (selection == null) {
                return;
            }
            try {
                byte[] payload = serialize(selection);
                SELECTION_PREFS.putByteArray(buildKey(imageHash), payload);
            } catch (IOException | RuntimeException ex) {
                System.err.println("Unable to persist selection data: " + ex.getMessage());
            }
        }

        private static StoredSelection load(int imageHash) {
            byte[] payload;
            try {
                payload = SELECTION_PREFS.getByteArray(buildKey(imageHash), null);
            } catch (RuntimeException ex) {
                System.err.println("Unable to read selection data: " + ex.getMessage());
                return null;
            }
            if (payload == null || payload.length == 0) {
                return null;
            }
            try {
                StoredSelection selection = deserialize(payload);
                if (selection == null) {
                    discard(imageHash);
                }
                return selection;
            } catch (IOException | ClassNotFoundException ex) {
                System.err.println("Unable to restore selection data: " + ex.getMessage());
                discard(imageHash);
                return null;
            }
        }

        private static void discard(int imageHash) {
            try {
                SELECTION_PREFS.remove(buildKey(imageHash));
            } catch (RuntimeException ex) {
                System.err.println("Unable to discard selection data: " + ex.getMessage());
            }
        }

        private static String buildKey(int hash) {
            return PREF_KEY_PREFIX + Integer.toUnsignedString(hash);
        }

        private static byte[] serialize(StoredSelection selection) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos);
                 ObjectOutputStream oos = new ObjectOutputStream(gzip)) {
                oos.writeObject(selection);
            }
            return baos.toByteArray();
        }

        private static StoredSelection deserialize(byte[] payload) throws IOException, ClassNotFoundException {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(payload);
                 GZIPInputStream gzip = new GZIPInputStream(bais);
                 ObjectInputStream ois = new ObjectInputStream(gzip)) {
                Object obj = ois.readObject();
                if (obj instanceof StoredSelection) {
                    return (StoredSelection) obj;
                }
                return null;
            }
        }
    }


    private static int computeImageHashCode(BufferedImage image) {
        if (image == null) {
            return 0;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        long hash = 17;
        hash = 31 * hash + width;
        hash = 31 * hash + height;
        hash = 31 * hash + image.getType();
        if (width == 0 || height == 0) {
            return (int) hash;
        }
        int[] rgbBuffer = new int[width];
        for (int y = 0; y < height; y++) {
            rgbBuffer = image.getRGB(0, y, width, 1, rgbBuffer, 0, width);
            hash = 31 * hash + Arrays.hashCode(rgbBuffer);
        }
        return (int) hash;
    }



    private void closeGUI() {
        if (cropperFrame == null) {
            return;
        }
        cropperFrame.setVisible(false);
        cropperFrame.dispose();
        cropperFrame = null;
        if (colorPickerDialog != null) {
            colorPickerDialog.dispose();
            colorPickerDialog = null;
            colorPickerRgbLabel = null;
            colorPickerHsvLabel = null;
            colorPickerSwatch = null;
        }
    }

    private void exportImage(FreeformSelectionWindow cropper, boolean includeComponents) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(includeComponents ? "Export image with components" : "Export image");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
        chooser.setSelectedFile(new File(includeComponents ? "selection_with_components.png" : "selection.png"));

        int result = chooser.showSaveDialog(cropperFrame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = chooser.getSelectedFile();
        if (selectedFile == null) {
            return;
        }
        if (!selectedFile.getName().toLowerCase().endsWith(".png")) {
            File parent = selectedFile.getParentFile();
            if (parent == null) {
                parent = chooser.getCurrentDirectory();
            }
            selectedFile = new File(parent, selectedFile.getName() + ".png");
        }

        try {
            BufferedImage exportImage = cropper.createExportImage(includeComponents);
            ImageIO.write(exportImage, "png", selectedFile);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(cropperFrame, "Unable to export image:\n" + ex.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void insertComponentFromArg(FreeformSelectionWindow cropper) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel(freeformDraw ? "Paste the drawing argument data to reconstruct it." : "Paste the rectangle argument data to reconstruct it."));
        JTextField argField = new JTextField(25);
        panel.add(argField);

        int response = JOptionPane.showConfirmDialog(cropperFrame, panel, "Insert component under arg", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (response != JOptionPane.OK_OPTION) {
            return;
        }

        String argText = argField.getText();
        if (argText == null || argText.trim().isEmpty()) {
            JOptionPane.showMessageDialog(cropperFrame, "Argument data cannot be empty.", "Invalid argument data", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            if (freeformDraw) {
                DrawingArgData data = parseDrawingArg(argText);
                List<Point> points = toImagePoints(data.orderedPoints);
                cropper.loadDrawingFromArgument(data.axisData, points);
            } else {
                Rectangle appliedRect = parseRectangleArg(argText);
                Rectangle originalRect = transform == null ? appliedRect : transform.unapply(appliedRect);
                cropper.loadCropBox(originalRect);
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(cropperFrame, "Unable to parse component data:\n" + ex.getMessage(), "Invalid argument data", JOptionPane.ERROR_MESSAGE);
        }
    }

    private DrawingArgData parseDrawingArg(String argText) {
        String trimmed = argText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Argument data is empty.");
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        Map<Double, List<Double>> axisData = new LinkedHashMap<>();
        List<Point2D.Double> orderedPoints = new ArrayList<>();
        if (trimmed.isEmpty()) {
            return new DrawingArgData(axisData, orderedPoints);
        }

        int index = 0;
        while (index < trimmed.length()) {
            int equalsIndex = trimmed.indexOf('=', index);
            if (equalsIndex == -1) {
                throw new IllegalArgumentException("Missing '=' separator in drawing argument data.");
            }
            String keyText = trimmed.substring(index, equalsIndex).trim();
            if (keyText.isEmpty()) {
                throw new IllegalArgumentException("Missing x value before '='.");
            }
            double xValue;
            try {
                xValue = Double.parseDouble(keyText);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid x value '" + keyText + "'.");
            }

            int listStart = trimmed.indexOf('[', equalsIndex);
            if (listStart == -1) {
                throw new IllegalArgumentException("Missing '[' after x value " + keyText + ".");
            }
            int depth = 1;
            int pos = listStart + 1;
            while (pos < trimmed.length() && depth > 0) {
                char ch = trimmed.charAt(pos);
                if (ch == '[') depth++;
                else if (ch == ']') depth--;
                pos++;
            }
            if (depth != 0) {
                throw new IllegalArgumentException("Unbalanced brackets for x value " + keyText + ".");
            }
            int listEnd = pos - 1;
            String listContent = trimmed.substring(listStart + 1, listEnd).trim();

            List<Double> destList = axisData.computeIfAbsent(xValue, v -> new ArrayList<>());
            if (!listContent.isEmpty()) {
                String[] entries = listContent.split(",");
                for (String entry : entries) {
                    String valueText = entry.trim();
                    if (valueText.isEmpty()) continue;
                    double yValue;
                    try {
                        yValue = Double.parseDouble(valueText);
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Invalid y value '" + valueText + "' for x value " + keyText + ".");
                    }
                    destList.add(yValue);
                    orderedPoints.add(new Point2D.Double(xValue, yValue));
                }
            }

            index = listEnd + 1;
            while (index < trimmed.length() && Character.isWhitespace(trimmed.charAt(index))) index++;
            if (index < trimmed.length() && trimmed.charAt(index) == ',') {
                index++;
                while (index < trimmed.length() && Character.isWhitespace(trimmed.charAt(index))) index++;
            }
        }

        return new DrawingArgData(axisData, orderedPoints);
    }

    private Rectangle parseRectangleArg(String argText) {
        String trimmed = argText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Argument data is empty.");
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Rectangle argument data is empty.");
        }

        Map<String, Integer> values = new LinkedHashMap<>();
        String[] parts = trimmed.split(",");
        for (String part : parts) {
            String piece = part.trim();
            if (piece.isEmpty()) continue;
            int equalsIdx = piece.indexOf('=');
            if (equalsIdx == -1) {
                throw new IllegalArgumentException("Missing '=' in rectangle segment '" + piece + "'.");
            }
            String key = piece.substring(0, equalsIdx).trim().toLowerCase();
            String valueText = piece.substring(equalsIdx + 1).trim();
            if (key.isEmpty() || valueText.isEmpty()) {
                throw new IllegalArgumentException("Invalid rectangle segment '" + piece + "'.");
            }
            if (!key.equals("x") && !key.equals("y") && !key.equals("width") && !key.equals("height")) {
                throw new IllegalArgumentException("Unknown rectangle attribute '" + key + "'.");
            }
            int numericValue;
            try {
                numericValue = Integer.parseInt(valueText);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric value '" + valueText + "' for " + key + ".");
            }
            values.put(key, numericValue);
        }

        for (String required : new String[] { "x", "y", "width", "height" }) {
            if (!values.containsKey(required)) {
                throw new IllegalArgumentException("Missing '" + required + "' in rectangle argument data.");
            }
        }

        return new Rectangle(values.get("x"), values.get("y"), values.get("width"), values.get("height"));
    }

    private List<Point> toImagePoints(List<Point2D.Double> appliedPoints) {
        List<Point> points = new ArrayList<>(appliedPoints.size());
        for (Point2D.Double applied : appliedPoints) {
            double x = transform == null ? applied.getX() : transform.unapplyX(applied.getX());
            double y = transform == null ? applied.getY() : transform.unapplyY(applied.getY());
            points.add(new Point((int) Math.round(x), (int) Math.round(y)));
        }
        return points;
    }

    private static final class DrawingArgData {
        private final Map<Double, List<Double>> axisData;
        private final List<Point2D.Double> orderedPoints;

        private DrawingArgData(Map<Double, List<Double>> axisData, List<Point2D.Double> orderedPoints) {
            this.axisData = axisData;
            this.orderedPoints = orderedPoints;
        }
    }



    public void destroy() {
        this.drawingAxis.complete(null);
        hideColorPickerWindow();
        this.closeGUI();
        FreeformSelectionManager.instantiated = false;
    }


    public Map<Double, List<Double>> getDrawingAxis() throws ExecutionException, InterruptedException {
        if (!freeformDraw) throw new IllegalCallerException("Cannot call getDrawingAxis when freeformDraw is false");
        return this.drawingAxis.get();
    }
    public Rectangle getCropBox() throws ExecutionException, InterruptedException {
        if (freeformDraw) throw new IllegalCallerException("Cannot call getDrawingAxis when freeformDraw is true");
        return this.cropBox.get();
    }


}

