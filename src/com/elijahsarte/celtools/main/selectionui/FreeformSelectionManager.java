package com.elijahsarte.celtools.main.selectionui;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FreeformSelectionManager {

    private static boolean instantiated = false;
    public static boolean saving = true;
    private static final Preferences SELECTION_PREFS = Preferences.userNodeForPackage(FreeformSelectionManager.class);
    private static final String PREF_KEY_PREFIX = "ff_selection_";
    private static final String PREF_KEY_AUTO_RESTORE_MODE = "ff_selection_restore_mode";
    private static AutoRestoreMode autoRestoreMode = loadAutoRestoreMode();

    public enum AutoRestoreMode {
        ASK_EVERY_TIME("Ask every time"),
        RESTORE_TO_UI("Restore into selection window"),
        RESTORE_AND_COMPLETE("Use saved selection automatically"),
        SKIP("Do not restore"),
        SKIP_AND_DISCARD("Discard saved selections");

        private final String label;

        AutoRestoreMode(String label) {
            this.label = label;
        }

        public static AutoRestoreMode fromPreference(String value) {
            if (value != null) {
                for (AutoRestoreMode mode : values()) {
                    if (mode.name().equalsIgnoreCase(value.trim())) {
                        return mode;
                    }
                }
            }
            return ASK_EVERY_TIME;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record SavedSelectionInfo(
            String storageKey,
            int imageHash,
            String displayName,
            FreeformSelectionWindow.SelectionMode selectionMode,
            String selectionKind,
            String summary,
            int imageWidth,
            int imageHeight,
            long savedAtMillis
    ) {
        public String imageHashText() {
            return Integer.toUnsignedString(imageHash);
        }

        public String imageSizeText() {
            return imageWidth > 0 && imageHeight > 0 ? imageWidth + "x" + imageHeight : "--";
        }
    }

    public record StoredSelectionEditorData(
            String storageKey,
            int imageHash,
            String frameName,
            FreeformSelectionWindow.SelectionMode selectionMode,
            String argText
    ) {}

    private record LoadedSelectionCandidate(String storageKey, StoredSelection selection) {}

    public static AutoRestoreMode getAutoRestoreMode() {
        return autoRestoreMode;
    }

    public static void setAutoRestoreMode(AutoRestoreMode mode) {
        autoRestoreMode = mode == null ? AutoRestoreMode.ASK_EVERY_TIME : mode;
        try {
            SELECTION_PREFS.put(PREF_KEY_AUTO_RESTORE_MODE, autoRestoreMode.name());
            SELECTION_PREFS.flush();
        } catch (BackingStoreException | RuntimeException ignored) {
        }
    }

    public static List<SavedSelectionInfo> listStoredSelections() {
        List<SavedSelectionInfo> result = new ArrayList<>();
        List<String> keys;

        try {
            keys = SelectionStorage.keys();
        } catch (Exception ex) {
            ex.printStackTrace();
            return result;
        }

        for (String key : keys) {
            Integer imageHash = parseImageHashFromKey(key);
            if (imageHash == null) continue;

            StoredSelection selection;
            try {
                selection = SelectionStorage.load(key);
            } catch (Exception ex) {
                ex.printStackTrace();
                continue;
            }

            if (selection == null) continue;

            FreeformSelectionWindow.SelectionMode mode = toSelectionMode(selection.kind());
            String displayName = selection.sourceName();
            if (displayName == null || displayName.isBlank()) {
                displayName = parseFrameNameFromKey(key);
            }
            if (displayName == null || displayName.isBlank()) {
                displayName = "Image " + Integer.toUnsignedString(imageHash);
            }

            result.add(new SavedSelectionInfo(
                    key,
                    imageHash,
                    displayName,
                    mode,
                    humanizeSelectionKind(selection.kind()),
                    summarizeSelection(selection),
                    selection.imageWidth(),
                    selection.imageHeight(),
                    selection.savedAtEpochMillis()
            ));
        }

        result.sort((a, b) -> {
            int byTime = Long.compare(b.savedAtMillis(), a.savedAtMillis());
            if (byTime != 0) return byTime;

            int byHash = a.imageHashText().compareToIgnoreCase(b.imageHashText());
            if (byHash != 0) return byHash;

            int byMode = String.valueOf(a.selectionMode()).compareToIgnoreCase(String.valueOf(b.selectionMode()));
            if (byMode != 0) return byMode;

            return a.storageKey().compareToIgnoreCase(b.storageKey());
        });

        return result;
    }

    public static StoredSelectionEditorData getStoredSelectionEditorData(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }

        StoredSelection selection = SelectionStorage.load(storageKey);
        if (selection == null) {
            return null;
        }

        Integer imageHash = parseImageHashFromKey(storageKey);
        if (imageHash == null) {
            imageHash = 0;
        }

        String frameName = selection.sourceName();
        if (frameName == null || frameName.isBlank()) {
            frameName = parseFrameNameFromKey(storageKey);
        }
        if (frameName == null) {
            frameName = "";
        }

        return new StoredSelectionEditorData(
                storageKey,
                imageHash,
                frameName,
                toSelectionMode(selection.kind()),
                encodeSelectionForEditor(selection)
        );
    }

    public static StoredSelectionEditorData getStoredSelectionEditorData(int imageHash) {
        SavedSelectionInfo newest = null;
        for (SavedSelectionInfo info : listStoredSelections()) {
            if (info.imageHash() != imageHash) continue;
            if (newest == null || info.savedAtMillis() > newest.savedAtMillis()) {
                newest = info;
            }
        }
        return newest == null ? null : getStoredSelectionEditorData(newest.storageKey());
    }

    public static void removeStoredSelection(String storageKey) {
        SelectionStorage.discard(storageKey);
    }

    public static void removeStoredSelection(int imageHash) {
        for (String key : SelectionStorage.keys()) {
            Integer parsed = parseImageHashFromKey(key);
            if (parsed != null && parsed == imageHash) {
                SelectionStorage.discard(key);
            }
        }
    }

    public static void clearStoredSelections() {
        for (String key : SelectionStorage.keys()) {
            SelectionStorage.discard(key);
        }
    }

    public static String getSelectionArgumentFormatHint(FreeformSelectionWindow.SelectionMode mode) {
        if (mode == null) return "";
        return switch (mode) {
            case FREEFORM_DRAW -> "Example: 10.0=[12.0, 13.0], 11.0=[14.0, 15.0]";
            case CROP_BOX -> "Example: [x=10,y=20,width=30,height=40]";
            case MULTI_RECTANGLE -> "Example: [x=10,y=20,width=30,height=40]; [x=60,y=80,width=25,height=35]";
            case POINT -> "Example: [x=10,y=20]";
            case MULTI_POINT -> "Example: [x=10,y=20]; [x=30,y=40]";
            case LABELED_MULTI_POINT -> "Example: [x=10,y=20,label=Head]; [x=30,y=40,label=Tail]";
        };
    }

    public static void upsertStoredSelection(
            String originalStorageKey,
            int imageHash,
            String frameName,
            FreeformSelectionWindow.SelectionMode selectionMode,
            String argText
    ) {
        if (selectionMode == null) {
            throw new IllegalArgumentException("Selection mode is required.");
        }
        if (argText == null || argText.trim().isEmpty()) {
            throw new IllegalArgumentException("Selection argument data cannot be empty.");
        }

        String safeName = frameName == null || frameName.isBlank() ? "Imported selection" : frameName.trim();
        String newStorageKey = buildStorageKey(imageHash, selectionMode, safeName);
        StoredSelection selection;

        switch (selectionMode) {
            case FREEFORM_DRAW -> {
                DrawingArgData data = parseDrawingArgInternal(argText);
                List<Point> orderedPoints = new ArrayList<>(data.orderedPoints.size());
                for (Point2D.Double point : data.orderedPoints) {
                    orderedPoints.add(new Point((int) Math.round(point.getX()), (int) Math.round(point.getY())));
                }
                selection = StoredSelection.forFreeform(safeName, -1, -1, data.axisData, orderedPoints);
            }
            case CROP_BOX -> selection = StoredSelection.forCrop(safeName, -1, -1, parseRectangleArgInternal(argText));
            case MULTI_RECTANGLE -> {
                List<Rectangle> rectangles = parseRectangleListArgInternal(argText);
                if (rectangles.isEmpty()) {
                    throw new IllegalArgumentException("Rectangle list argument data is empty.");
                }
                selection = StoredSelection.forRectangles(safeName, -1, -1, rectangles);
            }
            case POINT -> selection = StoredSelection.forPoint(safeName, -1, -1, parsePointArgInternal(argText));
            case MULTI_POINT -> {
                List<Point> points = parsePointListArgInternal(argText);
                if (points.isEmpty()) {
                    throw new IllegalArgumentException("Point list argument data is empty.");
                }
                selection = StoredSelection.forPoints(safeName, -1, -1, points);
            }
            case LABELED_MULTI_POINT -> {
                Map<Point, String> points = parseLabeledPointMapArgInternal(argText, null);
                if (points.isEmpty()) {
                    throw new IllegalArgumentException("Labeled point argument data is empty.");
                }
                selection = StoredSelection.forLabeledPoints(safeName, -1, -1, points);
            }
            default -> throw new IllegalStateException("Unhandled selection mode: " + selectionMode);
        }

        if (originalStorageKey != null && !originalStorageKey.isBlank() && !originalStorageKey.equals(newStorageKey)) {
            SelectionStorage.discard(originalStorageKey);
        }

        SelectionStorage.save(newStorageKey, selection);
    }

    public static void upsertStoredSelection(
            int imageHash,
            String frameName,
            FreeformSelectionWindow.SelectionMode selectionMode,
            String argText
    ) {
        upsertStoredSelection(null, imageHash, frameName, selectionMode, argText);
    }

    private static AutoRestoreMode loadAutoRestoreMode() {
        try {
            return AutoRestoreMode.fromPreference(
                    SELECTION_PREFS.get(PREF_KEY_AUTO_RESTORE_MODE, AutoRestoreMode.ASK_EVERY_TIME.name())
            );
        } catch (RuntimeException ex) {
            return AutoRestoreMode.ASK_EVERY_TIME;
        }
    }

    private static String encodeKeyPart(String value) {
        String raw = value == null ? "" : value.trim();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeKeyPart(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static String buildLegacyKey(int imageHash) {
        return PREF_KEY_PREFIX + Integer.toUnsignedString(imageHash);
    }

    private static String buildStorageKey(
            int imageHash,
            FreeformSelectionWindow.SelectionMode selectionMode,
            String frameName
    ) {
        String modePart = selectionMode == null
                ? FreeformSelectionWindow.SelectionMode.CROP_BOX.name()
                : selectionMode.name();
        String framePart = encodeKeyPart(frameName);
        return buildLegacyKey(imageHash) + "__" + modePart + "__" + framePart;
    }

    private static boolean isLegacyStorageKey(String key) {
        if (key == null || !key.startsWith(PREF_KEY_PREFIX)) {
            return false;
        }
        String suffix = key.substring(PREF_KEY_PREFIX.length());
        return !suffix.contains("__");
    }

    private static Integer parseImageHashFromKey(String key) {
        if (key == null || !key.startsWith(PREF_KEY_PREFIX)) {
            return null;
        }

        String suffix = key.substring(PREF_KEY_PREFIX.length());
        int sep = suffix.indexOf("__");
        String hashPart = sep >= 0 ? suffix.substring(0, sep).trim() : suffix.trim();
        if (hashPart.isEmpty()) {
            return null;
        }

        try {
            return (int) Long.parseUnsignedLong(hashPart);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String parseFrameNameFromKey(String key) {
        if (key == null || !key.startsWith(PREF_KEY_PREFIX)) {
            return "";
        }

        String suffix = key.substring(PREF_KEY_PREFIX.length());
        String[] parts = suffix.split("__", 3);
        if (parts.length < 3) {
            return "";
        }
        return decodeKeyPart(parts[2]);
    }

    private static FreeformSelectionWindow.SelectionMode parseSelectionModeFromKey(String key) {
        if (key == null || !key.startsWith(PREF_KEY_PREFIX)) {
            return null;
        }

        String suffix = key.substring(PREF_KEY_PREFIX.length());
        String[] parts = suffix.split("__", 3);
        if (parts.length < 2) {
            return null;
        }

        try {
            return FreeformSelectionWindow.SelectionMode.valueOf(parts[1]);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static FreeformSelectionWindow.SelectionMode toSelectionMode(SelectionKind kind) {
        return switch (kind) {
            case FREEFORM_DRAW -> FreeformSelectionWindow.SelectionMode.FREEFORM_DRAW;
            case CROP_BOX -> FreeformSelectionWindow.SelectionMode.CROP_BOX;
            case MULTI_RECTANGLE -> FreeformSelectionWindow.SelectionMode.MULTI_RECTANGLE;
            case POINT -> FreeformSelectionWindow.SelectionMode.POINT;
            case MULTI_POINT -> FreeformSelectionWindow.SelectionMode.MULTI_POINT;
            case LABELED_MULTI_POINT -> FreeformSelectionWindow.SelectionMode.LABELED_MULTI_POINT;
        };
    }

    private static String humanizeSelectionKind(SelectionKind kind) {
        if (kind == null) return "Unknown";
        return switch (kind) {
            case FREEFORM_DRAW -> "Freeform Draw";
            case CROP_BOX -> "Crop Box";
            case MULTI_RECTANGLE -> "Multi Rectangle";
            case POINT -> "Point";
            case MULTI_POINT -> "Multi Point";
            case LABELED_MULTI_POINT -> "Labeled Multi Point";
        };
    }

    private static String summarizeSelection(StoredSelection selection) {
        if (selection == null) return "--";

        return switch (selection.kind()) {
            case FREEFORM_DRAW -> selection.orderedPoints().size() + " point(s)";
            case CROP_BOX -> formatRectangle(selection.appliedCropBox());
            case MULTI_RECTANGLE -> selection.appliedRectangles().size() + " rectangle(s)";
            case POINT -> formatPoint(selection.appliedPoint());
            case MULTI_POINT -> selection.appliedPoints().size() + " point(s)";
            case LABELED_MULTI_POINT -> selection.appliedLabeledPoints().size() + " labeled point(s)";
        };
    }

    private static String encodeSelectionForEditor(StoredSelection selection) {
        if (selection == null) {
            return "";
        }

        return switch (selection.kind()) {
            case FREEFORM_DRAW -> Main.encodeFreeformData(selection.axisData());
            case CROP_BOX -> encodeRectangle(selection.appliedCropBox());
            case MULTI_RECTANGLE -> encodeRectangleListInternal(selection.appliedRectangles());
            case POINT -> encodePoint(selection.appliedPoint());
            case MULTI_POINT -> encodePointListInternal(selection.appliedPoints());
            case LABELED_MULTI_POINT -> encodeLabeledPointMapInternal(selection.appliedLabeledPoints());
        };
    }

    private static String formatRectangle(Rectangle rect) {
        return rect == null ? "--" : "[x=" + rect.x + ", y=" + rect.y + ", w=" + rect.width + ", h=" + rect.height + "]";
    }

    private static String formatPoint(Point point) {
        return point == null ? "--" : "[x=" + point.x + ", y=" + point.y + "]";
    }

    private static String encodeRectangle(Rectangle rect) {
        return rect == null ? "" : "[x=" + rect.x + ",y=" + rect.y + ",width=" + rect.width + ",height=" + rect.height + "]";
    }

    private static String encodeRectangleListInternal(List<Rectangle> rectangles) {
        if (rectangles == null || rectangles.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner("; ");
        for (Rectangle rect : rectangles) {
            if (rect == null) continue;
            joiner.add(encodeRectangle(rect));
        }
        return joiner.toString();
    }

    private static String encodePoint(Point point) {
        return point == null ? "" : "[x=" + point.x + ",y=" + point.y + "]";
    }

    private static String encodePointListInternal(List<Point> points) {
        if (points == null || points.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner("; ");
        for (Point point : points) {
            if (point == null) continue;
            joiner.add(encodePoint(point));
        }
        return joiner.toString();
    }

    private static String encodeLabeledPointMapInternal(Map<Point, String> points) {
        if (points == null || points.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner("; ");
        for (Map.Entry<Point, String> entry : points.entrySet()) {
            Point point = entry.getKey();
            String label = entry.getValue();
            if (point == null || label == null) continue;
            joiner.add("[x=" + point.x + ",y=" + point.y + ",label=" + label + "]");
        }
        return joiner.toString();
    }

    private final SelectionTransform transform;
    private final String frameName;
    private final boolean freeformDraw;
    private final FreeformSelectionWindow.SelectionMode selectionMode;
    private final List<String> pointLabels;
    private final int imageHashCode;
    private final int imageWidth;
    private final int imageHeight;
    private final String storageKey;

    private JFrame cropperFrame = null;
    private FreeformSelectionWindow cropper;
    private volatile boolean colorSelectionRequested;
    private final CompletableFuture<Map<Double, List<Double>>> drawingAxis = new CompletableFuture<>();
    private final CompletableFuture<Rectangle> cropBox = new CompletableFuture<>();
    private final CompletableFuture<List<Rectangle>> selectedRectangles = new CompletableFuture<>();
    private final CompletableFuture<Point> selectedPoint = new CompletableFuture<>();
    private final CompletableFuture<List<Point>> selectedPoints = new CompletableFuture<>();
    private final CompletableFuture<Map<Point, String>> labeledSelectedPoints = new CompletableFuture<>();
    private final CompletableFuture<Color> selectedColor = new CompletableFuture<>();

    private StoredSelection deferredSelection = null;
    private String resolvedStoredSelectionKey = null;
    private FreeformSelectionWindow.ToolMode activeTool = FreeformSelectionWindow.ToolMode.NORMAL;
    private JDialog colorPickerDialog;
    private JLabel colorPickerRgbLabel;
    private JLabel colorPickerHsvLabel;
    private JPanel colorPickerSwatch;


    public static void openStandalone(BufferedImage image, String frameName) {
        Objects.requireNonNull(image, "image");
        SwingUtilities.invokeLater(() -> {
            boolean previousSaving = FreeformSelectionManager.saving;
            try {
                FreeformSelectionManager.saving = false;
                new FreeformSelectionManager(
                        image,
                        frameName == null || frameName.isBlank() ? "FreeformSelectionManager" : frameName,
                        SelectionTransform.empty(),
                        FreeformSelectionWindow.SelectionMode.CROP_BOX
                );
            } finally {
                FreeformSelectionManager.saving = previousSaving;
            }
        });
    }

    public static void main(String[] args) {
        try {
            ImageHandler testImgHandler = new ImageHandler(new File("C:\\Users\\Other user\\Documents\\celautofill_sandbox\\base_cel.png"));
            testImgHandler.loadImage();
            testImgHandler.loadGuiImage();
            new FreeformSelectionManager(testImgHandler.getGuiImage(), SelectionTransform.empty());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public FreeformSelectionManager(BufferedImage image, String frameName, SelectionTransform transform, boolean freeformDraw) {
        this(image, frameName, transform,
                freeformDraw ? FreeformSelectionWindow.SelectionMode.FREEFORM_DRAW : FreeformSelectionWindow.SelectionMode.CROP_BOX);
    }

    public FreeformSelectionManager(BufferedImage image, String frameName, SelectionTransform transform, FreeformSelectionWindow.SelectionMode selectionMode) {
        this(image, frameName, transform, selectionMode, null);
    }

    public FreeformSelectionManager(BufferedImage image, String frameName, SelectionTransform transform,
                                    FreeformSelectionWindow.SelectionMode selectionMode, List<String> pointLabels) {
        this.frameName = frameName == null ? "Cropper" : frameName;
        this.transform = transform == null ? SelectionTransform.empty() : transform;
        this.selectionMode = selectionMode == null ? FreeformSelectionWindow.SelectionMode.CROP_BOX : selectionMode;
        this.freeformDraw = this.selectionMode == FreeformSelectionWindow.SelectionMode.FREEFORM_DRAW;
        this.pointLabels = pointLabels == null ? new ArrayList<>() : new ArrayList<>(pointLabels);
        if (this.selectionMode == FreeformSelectionWindow.SelectionMode.LABELED_MULTI_POINT && this.pointLabels.isEmpty()) {
            throw new IllegalArgumentException("Labeled multi-point selection requires at least one label.");
        }

        this.imageWidth = image == null ? -1 : image.getWidth();
        this.imageHeight = image == null ? -1 : image.getHeight();
        this.imageHashCode = computeImageHashCode(image);
        this.storageKey = buildStorageKey(this.imageHashCode, this.selectionMode, this.frameName);

        if (FreeformSelectionManager.instantiated) {
            // intentionally allowed
        }
        FreeformSelectionManager.instantiated = true;

        if (FreeformSelectionManager.saving) {
            LoadedSelectionCandidate candidate = loadStoredSelectionCandidate();
            StoredSelection storedSelection = candidate == null ? null : candidate.selection();
            this.resolvedStoredSelectionKey = candidate == null ? null : candidate.storageKey();

            RestoreAction restoreAction = determineRestoreAction(storedSelection);
            if (restoreAction == RestoreAction.APPLY_AND_COMPLETE) {
                completeWithStoredSelection(storedSelection);
                FreeformSelectionManager.instantiated = false;
                return;
            } else if (restoreAction == RestoreAction.APPLY_TO_UI) {
                this.deferredSelection = storedSelection;
            }
        }

        createGUI(image);
    }

    public FreeformSelectionManager(BufferedImage image, SelectionTransform transform, String frameName,
                                    FreeformSelectionWindow.SelectionMode selectionMode) {
        this(image, frameName, transform, selectionMode);
    }

    public FreeformSelectionManager(BufferedImage image, SelectionTransform transform, String frameName,
                                    FreeformSelectionWindow.SelectionMode selectionMode, List<String> pointLabels) {
        this(image, frameName, transform, selectionMode, pointLabels);
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

    public FreeformSelectionManager(BufferedImage image, SelectionTransform transform,
                                    FreeformSelectionWindow.SelectionMode selectionMode) {
        this(image, "Cropper", transform, selectionMode);
    }

    public FreeformSelectionManager(BufferedImage image, SelectionTransform transform,
                                    FreeformSelectionWindow.SelectionMode selectionMode, List<String> pointLabels) {
        this(image, "Cropper", transform, selectionMode, pointLabels);
    }

    public FreeformSelectionManager(BufferedImage image, boolean freeformDraw) {
        this(image, SelectionTransform.empty(), freeformDraw);
    }

    public FreeformSelectionManager(BufferedImage image, String frameName, FreeformSelectionWindow.SelectionMode selectionMode) {
        this(image, frameName, SelectionTransform.empty(), selectionMode);
    }

    public FreeformSelectionManager(BufferedImage image, FreeformSelectionWindow.SelectionMode selectionMode) {
        this(image, "Cropper", selectionMode);
    }

    public FreeformSelectionManager(BufferedImage image, FreeformSelectionWindow.SelectionMode selectionMode, List<String> pointLabels) {
        this(image, SelectionTransform.empty(), selectionMode, pointLabels);
    }

    public FreeformSelectionManager(BufferedImage image, SelectionTransform transform) {
        this(image, transform, true);
    }

    public FreeformSelectionManager(BufferedImage image) {
        this(image, SelectionTransform.empty());
    }

    private LoadedSelectionCandidate loadStoredSelectionCandidate() {
        try {
            StoredSelection exact = SelectionStorage.load(storageKey);
            if (exact != null) {
                return new LoadedSelectionCandidate(storageKey, exact);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        String legacyKey = buildLegacyKey(imageHashCode);
        if (!legacyKey.equals(storageKey)) {
            try {
                StoredSelection legacy = SelectionStorage.load(legacyKey);
                if (legacy != null) {
                    FreeformSelectionWindow.SelectionMode legacyMode = parseSelectionModeFromKey(legacyKey);
                    if (legacyMode == null || legacy.matches(selectionMode)) {
                        return new LoadedSelectionCandidate(legacyKey, legacy);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return null;
    }

    private void createGUI(BufferedImage image) {
        FreeformSelectionWindow cropper = new FreeformSelectionWindow(image, transform, selectionMode, pointLabels);
        this.cropper = cropper;
        applyDeferredSelectionIfNeeded(cropper);
        cropper.setColorPickListener(color -> SwingUtilities.invokeLater(() -> {
            showColorPickerInfo(color);
            if (colorSelectionRequested && selectedColor.complete(color)) destroy();
        }));
        cropper.setToolMode(activeTool);

        cropper.setAutoFit(false);
        cropper.setAutoscrolls(true);

        cropperFrame = new JFrame(frameName);
        cropperFrame.setName(frameName);
        cropperFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        cropperFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                completeCurrentSelection(cropper);
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

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int horizDef = image.getWidth() >= screenSize.getWidth()
                ? JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
                : JScrollPane.HORIZONTAL_SCROLLBAR_NEVER;
        int vertDef = (image.getHeight() + 20) >= screenSize.getHeight()
                ? JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
                : JScrollPane.VERTICAL_SCROLLBAR_NEVER;

        {
            JScrollPane scrollPane = new JScrollPane(cropper);
            scrollPane.setPreferredSize(new Dimension(
                    (int) Math.min(image.getWidth(), (screenSize.getWidth() / 1.1) - 50),
                    (int) Math.min(image.getHeight(), ((screenSize.getHeight() - 20) / 1.1) - 50)
            ));

            JLabel statusLabel = new JLabel("Mouse: (0, 0) | Image: -- | Zoom: 1.00x");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            statusLabel.putClientProperty("update", (Consumer<MouseEvent>) e -> statusLabel.setText(
                    "Mouse: ("
                            + MathEx.floorInt(MathEx.divide(e.getX() - cropper.getImageOffsetX(), cropper.getZoomFactor()))
                            + ", "
                            + MathEx.floorInt(MathEx.divide(e.getY() - cropper.getImageOffsetY(), cropper.getZoomFactor()))
                            + ") | Image: "
                            + image.getWidth() + "x" + image.getHeight()
                            + " | Zoom: "
                            + MathEx.round(cropper.getZoomFactor(), 0.01) + "x"
            ));

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
                    barInQuestion.getAdjustmentListeners()[0]
                            .adjustmentValueChanged(new AdjustmentEvent(barInQuestion, 0, 0, 0));
                    refreshStatusLabel(statusLabel, cropper);
                    return;
                }

                if (cropper.getZoomFactor() > 1) {
                    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                } else {
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

            if (selectionMode == FreeformSelectionWindow.SelectionMode.MULTI_RECTANGLE) {
                JPanel multiRectangleControls = new JPanel(new FlowLayout(FlowLayout.LEFT));

                JButton saveSelectionButton = new JButton("Save Selection");
                JButton undoButton = new JButton("Undo");
                JButton removeActiveButton = new JButton("Remove Active");
                JButton clearAllButton = new JButton("Clear All");
                JButton cancelButton = new JButton("Cancel");

                saveSelectionButton.addActionListener(e -> {
                    saveSelectionForImage(cropper);
                    completeCurrentSelection(cropper);
                    this.destroy();
                });

                undoButton.addActionListener(e -> cropper.undoLastRectangle());
                removeActiveButton.addActionListener(e -> cropper.removeActiveRectangle());
                clearAllButton.addActionListener(e -> cropper.clearSelectedRectangles());
                cancelButton.addActionListener(e -> {
                    cropper.clearSelectedRectangles();
                    this.destroy();
                });

                multiRectangleControls.add(saveSelectionButton);
                multiRectangleControls.add(undoButton);
                multiRectangleControls.add(removeActiveButton);
                multiRectangleControls.add(clearAllButton);
                multiRectangleControls.add(cancelButton);

                containerPanel.add(multiRectangleControls, BorderLayout.NORTH);
            }

            cropperFrame.setContentPane(containerPanel);
        }

        {
            JMenuBar bar = new JMenuBar();
            bar.setOpaque(true);
            bar.setBackground(Color.WHITE);

            JMenu utils = new JMenu("File");
            JMenu info = new JMenu("Info");
            JMenu tools = new JMenu("Tools");
            JMenu exit = new JMenu("Exit");

            JMenuItem saveBtn = new JMenuItem("Save selection");
            saveBtn.addActionListener((ActionEvent e) -> {
                saveSelectionForImage(cropper);
                completeCurrentSelection(cropper);
                this.destroy();
            });
            saveBtn.setMnemonic(KeyEvent.VK_S);
            saveBtn.setDisplayedMnemonicIndex(0);
            saveBtn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));

            JMenuItem clearBtn = new JMenuItem(
                    selectionMode == FreeformSelectionWindow.SelectionMode.MULTI_RECTANGLE
                            ? "Clear all rectangles"
                            : "Clear selection"
            );
            clearBtn.addActionListener((ActionEvent e) -> {
                if (selectionMode == FreeformSelectionWindow.SelectionMode.MULTI_RECTANGLE) {
                    cropper.clearSelectedRectangles();
                } else {
                    cropper.clearDrawing();
                }
            });
            clearBtn.setMnemonic(KeyEvent.VK_L);
            clearBtn.setDisplayedMnemonicIndex(1);
            clearBtn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));

            JMenuItem exportImageBtn = new JMenuItem("Export image");
            exportImageBtn.addActionListener(e -> exportImage(cropper, false));

            JMenuItem exportImageWithComponentsBtn = new JMenuItem("Export image with components");
            exportImageWithComponentsBtn.addActionListener(e -> exportImage(cropper, true));

            JMenuItem cropBoxBtn = new JMenuItem("Get selection data as arg");
            cropBoxBtn.addActionListener((ActionEvent e) -> {
                JPanel drawingAxisArgInfo = new JPanel();
                drawingAxisArgInfo.setLayout(new BoxLayout(drawingAxisArgInfo, BoxLayout.Y_AXIS));
                if (freeformDraw) {
                    drawingAxisArgInfo.add(new JLabel(
                            "NOTE: This arg data will ONLY work if the previously selected crop box data is the same (if any)."
                    ));
                }

                String drawingAxisArg = selectionDataAsArgument(cropper);
                drawingAxisArgInfo.add(new JTextField(drawingAxisArg, 10));
                JOptionPane.showMessageDialog(
                        null,
                        drawingAxisArgInfo,
                        "Selection data as command line arg",
                        JOptionPane.INFORMATION_MESSAGE
                );
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

            if (selectionMode == FreeformSelectionWindow.SelectionMode.MULTI_RECTANGLE) {
                JMenuItem undoRectangleBtn = new JMenuItem("Undo last rectangle");
                undoRectangleBtn.addActionListener(e -> cropper.undoLastRectangle());
                undoRectangleBtn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK));

                JMenuItem removeActiveRectangleBtn = new JMenuItem("Remove active rectangle");
                removeActiveRectangleBtn.addActionListener(e -> cropper.removeActiveRectangle());

                utils.add(undoRectangleBtn);
                utils.add(removeActiveRectangleBtn);
            }

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

            if (isPointSelectionMode()) {
                tools.addSeparator();

                ButtonGroup pointEditGroup = new ButtonGroup();
                JRadioButtonMenuItem addPointTool = new JRadioButtonMenuItem("Add point", true);
                JRadioButtonMenuItem removePointTool = new JRadioButtonMenuItem("Remove point");
                pointEditGroup.add(addPointTool);
                pointEditGroup.add(removePointTool);

                addPointTool.addActionListener(e -> cropper.setPointEditMode(FreeformSelectionWindow.PointEditMode.ADD));
                removePointTool.addActionListener(e -> cropper.setPointEditMode(FreeformSelectionWindow.PointEditMode.REMOVE));

                tools.add(addPointTool);
                tools.add(removePointTool);
            }

            JMenuItem closeWindowBtn = new JMenuItem("Close selection window");
            closeWindowBtn.addActionListener(e -> {
                completeCurrentSelection(cropper);
                this.destroy();
            });

            JMenuItem exitProgramBtn = new JMenuItem("Exit program");
            exitProgramBtn.addActionListener(e -> System.exit(0));

            exit.add(closeWindowBtn);
            exit.add(exitProgramBtn);

            bar.add(utils);
            bar.add(info);
            bar.add(tools);
            bar.add(exit);
            cropperFrame.setJMenuBar(bar);
        }

        cropper.addPropertyChangeListener("zoom", evt -> cropper.repaint());

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

        MouseEvent synthetic = new MouseEvent(
                cropper,
                MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(),
                0,
                mouse.x,
                mouse.y,
                0,
                false
        );
        updater.accept(synthetic);
    }

    private void setToolMode(FreeformSelectionWindow cropper, FreeformSelectionWindow.ToolMode mode) {
        if (cropper == null) return;

        FreeformSelectionWindow.ToolMode resolved = mode == null
                ? FreeformSelectionWindow.ToolMode.NORMAL
                : mode;
        this.activeTool = resolved;
        cropper.setToolMode(resolved);

        if (resolved != FreeformSelectionWindow.ToolMode.COLOR_PICKER) {
            hideColorPickerWindow();
        }
    }

    private void showColorPickerInfo(Color color) {
        if (color == null) return;
        if (activeTool != FreeformSelectionWindow.ToolMode.COLOR_PICKER) return;

        ensureColorPickerDialog();

        if (colorPickerSwatch != null) {
            colorPickerSwatch.setBackground(color);
        }
        if (colorPickerRgbLabel != null) {
            String alphaText = color.getAlpha() < 255 ? " (A=" + color.getAlpha() + ")" : "";
            colorPickerRgbLabel.setText(String.format(
                    "RGB: %d, %d, %d%s",
                    color.getRed(), color.getGreen(), color.getBlue(), alphaText
            ));
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

    private void completeWithStoredSelection(StoredSelection selection) {
        if (selection == null) {
            return;
        }

        switch (selectionMode) {
            case FREEFORM_DRAW -> {
                if (selection.isFreeform()) this.drawingAxis.complete(selection.axisData());
                else discardResolvedSelection();
            }
            case CROP_BOX -> {
                if (selection.isCropBox()) this.cropBox.complete(selection.appliedCropBox());
                else discardResolvedSelection();
            }
            case MULTI_RECTANGLE -> {
                if (selection.isMultiRectangle()) this.selectedRectangles.complete(selection.appliedRectangles());
                else discardResolvedSelection();
            }
            case POINT -> {
                if (selection.isPoint()) this.selectedPoint.complete(selection.appliedPoint());
                else discardResolvedSelection();
            }
            case MULTI_POINT -> {
                if (selection.isMultiPoint()) this.selectedPoints.complete(selection.appliedPoints());
                else discardResolvedSelection();
            }
            case LABELED_MULTI_POINT -> {
                if (selection.isLabeledMultiPoint()) this.labeledSelectedPoints.complete(selection.appliedLabeledPoints());
                else discardResolvedSelection();
            }
        }
    }

    private String selectionArgumentPrompt() {
        return switch (selectionMode) {
            case FREEFORM_DRAW -> "Paste the drawing argument data to reconstruct it.";
            case CROP_BOX -> "Paste the rectangle argument data to reconstruct it.";
            case MULTI_RECTANGLE -> "Paste the rectangle list argument data to reconstruct it.";
            case POINT -> "Paste the point argument data to reconstruct it.";
            case MULTI_POINT -> "Paste the point list argument data to reconstruct it.";
            case LABELED_MULTI_POINT -> "Paste the labeled point argument data to reconstruct it.";
        };
    }

    private void completeCurrentSelection(FreeformSelectionWindow cropper) {
        if (cropper == null) return;

        switch (selectionMode) {
            case FREEFORM_DRAW -> this.drawingAxis.complete(cropper.getDrawingAxis());
            case CROP_BOX -> this.cropBox.complete(cropper.getCropBox());
            case MULTI_RECTANGLE -> this.selectedRectangles.complete(cropper.getSelectedRectangles());
            case POINT -> this.selectedPoint.complete(cropper.getSelectedPoint());
            case MULTI_POINT -> this.selectedPoints.complete(cropper.getSelectedPoints());
            case LABELED_MULTI_POINT -> this.labeledSelectedPoints.complete(cropper.getLabeledSelectedPoints());
        }
    }

    private String selectionDataAsArgument(FreeformSelectionWindow cropper) {
        return switch (selectionMode) {
            case FREEFORM_DRAW -> Main.encodeFreeformData(cropper.getDrawingAxis());
            case CROP_BOX -> ProgrammingEx.varOper(
                    cropper.getCropBox(),
                    r -> "[x=" + r.x + ",y=" + r.y + ",width=" + r.width + ",height=" + r.height + "]"
            );
            case MULTI_RECTANGLE -> encodeRectangleList(cropper.getSelectedRectangles());
            case POINT -> ProgrammingEx.varOper(
                    cropper.getSelectedPoint(),
                    p -> "[x=" + p.x + ",y=" + p.y + "]"
            );
            case MULTI_POINT -> encodePointList(cropper.getSelectedPoints());
            case LABELED_MULTI_POINT -> encodeLabeledPointMap(cropper.getLabeledSelectedPoints());
        };
    }

    private void saveSelectionForImage(FreeformSelectionWindow cropper) {
        if (FreeformSelectionManager.saving) return;
        if (cropper == null) return;

        try {
            StoredSelection selection;
            switch (selectionMode) {
                case FREEFORM_DRAW -> {
                    Map<Double, List<Double>> axisData = cropper.getDrawingAxis();
                    List<Point> orderedPoints = cropper.getOrderedDrawingPoints();
                    selection = StoredSelection.forFreeform(frameName, imageWidth, imageHeight, axisData, orderedPoints);
                }
                case CROP_BOX -> {
                    Rectangle rect = cropper.getCropBox();
                    if (rect == null) {
                        SelectionStorage.discard(storageKey);
                        return;
                    }
                    selection = StoredSelection.forCrop(frameName, imageWidth, imageHeight, rect);
                }
                case MULTI_RECTANGLE -> {
                    List<Rectangle> rectangles = cropper.getSelectedRectangles();
                    if (rectangles == null || rectangles.isEmpty()) {
                        SelectionStorage.discard(storageKey);
                        return;
                    }
                    selection = StoredSelection.forRectangles(frameName, imageWidth, imageHeight, rectangles);
                }
                case POINT -> {
                    Point point = cropper.getSelectedPoint();
                    if (point == null) {
                        SelectionStorage.discard(storageKey);
                        return;
                    }
                    selection = StoredSelection.forPoint(frameName, imageWidth, imageHeight, point);
                }
                case MULTI_POINT -> {
                    List<Point> points = cropper.getSelectedPoints();
                    if (points.isEmpty()) {
                        SelectionStorage.discard(storageKey);
                        return;
                    }
                    selection = StoredSelection.forPoints(frameName, imageWidth, imageHeight, points);
                }
                case LABELED_MULTI_POINT -> {
                    Map<Point, String> points = cropper.getLabeledSelectedPoints();
                    if (points.isEmpty()) {
                        SelectionStorage.discard(storageKey);
                        return;
                    }
                    selection = StoredSelection.forLabeledPoints(frameName, imageWidth, imageHeight, points);
                }
                default -> throw new IllegalStateException("Unhandled selection mode: " + selectionMode);
            }

            SelectionStorage.save(storageKey, selection);

            String legacyKey = buildLegacyKey(imageHashCode);
            if (!legacyKey.equals(storageKey)) {
                try {
                    SelectionStorage.discard(legacyKey);
                } catch (Exception ignored) {
                }
            }

            if (resolvedStoredSelectionKey != null
                    && !resolvedStoredSelectionKey.equals(storageKey)
                    && isLegacyStorageKey(resolvedStoredSelectionKey)) {
                try {
                    SelectionStorage.discard(resolvedStoredSelectionKey);
                } catch (Exception ignored) {
                }
            }

            resolvedStoredSelectionKey = storageKey;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    cropperFrame,
                    "Unable to store selection information:\n" + ex.getMessage(),
                    "Selection storage failed",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void applyDeferredSelectionIfNeeded(FreeformSelectionWindow cropper) {
        if (cropper == null || deferredSelection == null) {
            return;
        }

        try {
            switch (selectionMode) {
                case FREEFORM_DRAW -> {
                    if (deferredSelection.isFreeform()) {
                        Map<Double, List<Double>> axisCopy = deferredSelection.axisData();
                        List<Point> points = deferredSelection.orderedPoints();
                        cropper.loadDrawingFromArgument(axisCopy, points);
                    }
                }
                case CROP_BOX -> {
                    if (deferredSelection.isCropBox()) {
                        Rectangle appliedRect = deferredSelection.appliedCropBox();
                        Rectangle originalRect = appliedRect == null
                                ? null
                                : (transform == null ? appliedRect : transform.unapply(appliedRect));
                        cropper.loadCropBox(originalRect);
                    }
                }
                case MULTI_RECTANGLE -> {
                    if (deferredSelection.isMultiRectangle()) {
                        cropper.loadSelectedRectangles(toOriginalRectangles(deferredSelection.appliedRectangles()));
                    }
                }
                case POINT -> {
                    if (deferredSelection.isPoint()) {
                        Point appliedPoint = deferredSelection.appliedPoint();
                        Point originalPoint = appliedPoint == null
                                ? null
                                : (transform == null ? appliedPoint : transform.unapply(appliedPoint));
                        cropper.loadSelectedPoint(originalPoint);
                    }
                }
                case MULTI_POINT -> {
                    if (deferredSelection.isMultiPoint()) {
                        cropper.loadSelectedPoints(toOriginalPoints(deferredSelection.appliedPoints()));
                    }
                }
                case LABELED_MULTI_POINT -> {
                    if (deferredSelection.isLabeledMultiPoint()) {
                        cropper.loadLabeledSelectedPoints(toOriginalLabeledPoints(deferredSelection.appliedLabeledPoints()));
                    }
                }
            }
        } finally {
            deferredSelection = null;
        }
    }

    private RestoreAction determineRestoreAction(StoredSelection storedSelection) {
        if (storedSelection == null) {
            return RestoreAction.NONE;
        }

        if (!storedSelection.matches(selectionMode)) {
            if (autoRestoreMode == AutoRestoreMode.SKIP_AND_DISCARD) {
                discardResolvedSelection();
            }
            return RestoreAction.NONE;
        }

        return switch (autoRestoreMode) {
            case RESTORE_TO_UI -> RestoreAction.APPLY_TO_UI;
            case RESTORE_AND_COMPLETE -> RestoreAction.APPLY_AND_COMPLETE;
            case SKIP -> RestoreAction.NONE;
            case SKIP_AND_DISCARD -> {
                discardResolvedSelection();
                yield RestoreAction.NONE;
            }
            case ASK_EVERY_TIME -> promptForRestoreDecision(storedSelection);
        };
    }

    private RestoreAction promptForRestoreDecision(StoredSelection storedSelection) {
        if (storedSelection == null) {
            return RestoreAction.NONE;
        }

        JCheckBox dontAskAgain = new JCheckBox("Don't ask again");
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("A saved selection already exists for this image (" + frameName + ")."));
        panel.add(Box.createVerticalStrut(4));
        panel.add(new JLabel("Choose how you want to handle it."));
        panel.add(Box.createVerticalStrut(10));
        panel.add(dontAskAgain);

        Object[] options = new Object[] {
                "Restore",
                "Use Automatically",
                "Skip",
                "Discard Saved"
        };

        int choice = JOptionPane.showOptionDialog(
                null,
                panel,
                "Restore selection",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        AutoRestoreMode rememberedMode = null;
        RestoreAction action = RestoreAction.NONE;

        switch (choice) {
            case 0 -> {
                rememberedMode = AutoRestoreMode.RESTORE_TO_UI;
                action = RestoreAction.APPLY_TO_UI;
            }
            case 1 -> {
                rememberedMode = AutoRestoreMode.RESTORE_AND_COMPLETE;
                action = RestoreAction.APPLY_AND_COMPLETE;
            }
            case 2 -> {
                rememberedMode = AutoRestoreMode.SKIP;
                action = RestoreAction.NONE;
            }
            case 3 -> {
                rememberedMode = AutoRestoreMode.SKIP_AND_DISCARD;
                discardResolvedSelection();
                action = RestoreAction.NONE;
            }
            default -> action = RestoreAction.NONE;
        }

        if (dontAskAgain.isSelected() && rememberedMode != null) {
            setAutoRestoreMode(rememberedMode);
        }

        return action;
    }

    private void discardResolvedSelection() {
        String keyToDiscard = resolvedStoredSelectionKey == null || resolvedStoredSelectionKey.isBlank()
                ? storageKey
                : resolvedStoredSelectionKey;
        SelectionStorage.discard(keyToDiscard);
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

    private static Map<Point, String> copyLabeledPointMap(Map<Point, String> source) {
        Map<Point, String> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<Point, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            copy.put(new Point(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static List<Rectangle> copyRectangleList(List<Rectangle> source) {
        List<Rectangle> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (Rectangle rect : source) {
            if (rect == null) continue;
            copy.add(new Rectangle(rect));
        }
        return copy;
    }

    private List<Rectangle> toOriginalRectangles(List<Rectangle> appliedRectangles) {
        List<Rectangle> rectangles = new ArrayList<>();
        if (appliedRectangles == null) return rectangles;

        for (Rectangle applied : appliedRectangles) {
            if (applied == null) continue;
            rectangles.add(transform == null ? new Rectangle(applied) : transform.unapply(applied));
        }
        return rectangles;
    }

    private List<Rectangle> parseRectangleListArg(String argText) {
        return parseRectangleListArgInternal(argText);
    }

    private static List<Rectangle> parseRectangleListArgInternal(String argText) {
        String trimmed = argText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Rectangle list argument data is empty.");
        }

        List<Rectangle> rectangles = new ArrayList<>();
        for (String segment : trimmed.split(";")) {
            String rectText = segment.trim();
            if (rectText.isEmpty()) continue;
            rectangles.add(parseRectangleArgInternal(rectText));
        }
        return rectangles;
    }

    private String encodeRectangleList(List<Rectangle> rectangles) {
        if (rectangles == null || rectangles.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner("; ");
        for (Rectangle rect : rectangles) {
            if (rect == null) continue;
            joiner.add("[x=" + rect.x + ",y=" + rect.y + ",width=" + rect.width + ",height=" + rect.height + "]");
        }
        return joiner.toString();
    }

    private List<Point> toOriginalPoints(List<Point> appliedPoints) {
        List<Point> points = new ArrayList<>();
        if (appliedPoints == null) return points;

        for (Point applied : appliedPoints) {
            if (applied == null) continue;
            points.add(transform == null ? new Point(applied) : transform.unapply(applied));
        }
        return points;
    }

    private Map<Point, String> toOriginalLabeledPoints(Map<Point, String> appliedPoints) {
        Map<Point, String> points = new LinkedHashMap<>();
        if (appliedPoints == null) return points;

        for (Map.Entry<Point, String> entry : appliedPoints.entrySet()) {
            Point applied = entry.getKey();
            String label = entry.getValue();
            if (applied == null || label == null) continue;
            points.put(transform == null ? new Point(applied) : transform.unapply(applied), label);
        }
        return points;
    }

    private boolean isPointSelectionMode() {
        return selectionMode == FreeformSelectionWindow.SelectionMode.POINT
                || selectionMode == FreeformSelectionWindow.SelectionMode.MULTI_POINT
                || selectionMode == FreeformSelectionWindow.SelectionMode.LABELED_MULTI_POINT;
    }

    private enum RestoreAction {
        NONE,
        APPLY_TO_UI,
        APPLY_AND_COMPLETE
    }

    private enum SelectionKind {
        FREEFORM_DRAW,
        CROP_BOX,
        MULTI_RECTANGLE,
        POINT,
        MULTI_POINT,
        LABELED_MULTI_POINT
    }

    private static final class StoredSelection implements Serializable {
        private static final long serialVersionUID = 1L;

        private final SelectionKind selectionKind;
        private final boolean freeform;
        private final String sourceName;
        private final int imageWidth;
        private final int imageHeight;
        private final long savedAtEpochMillis;
        private final Map<Double, List<Double>> axisData;
        private final List<Point> orderedPoints;
        private final Rectangle appliedCropBox;
        private final List<Rectangle> appliedRectangles;
        private final Point appliedPoint;
        private final List<Point> appliedPoints;
        private final Map<Point, String> appliedLabeledPoints;

        private StoredSelection(
                SelectionKind selectionKind,
                boolean freeform,
                String sourceName,
                int imageWidth,
                int imageHeight,
                long savedAtEpochMillis,
                Map<Double, List<Double>> axisData,
                List<Point> orderedPoints,
                Rectangle appliedCropBox,
                List<Rectangle> appliedRectangles,
                Point appliedPoint,
                List<Point> appliedPoints,
                Map<Point, String> appliedLabeledPoints
        ) {
            this.selectionKind = selectionKind;
            this.freeform = freeform;
            this.sourceName = sourceName;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.savedAtEpochMillis = savedAtEpochMillis;
            this.axisData = axisData;
            this.orderedPoints = orderedPoints;
            this.appliedCropBox = appliedCropBox == null ? null : new Rectangle(appliedCropBox);
            this.appliedRectangles = copyRectangleList(appliedRectangles);
            this.appliedPoint = appliedPoint == null ? null : new Point(appliedPoint);
            this.appliedPoints = copyPointList(appliedPoints);
            this.appliedLabeledPoints = copyLabeledPointMap(appliedLabeledPoints);
        }

        static StoredSelection forFreeform(Map<Double, List<Double>> axisData, List<Point> orderedPoints) {
            return forFreeform(null, -1, -1, axisData, orderedPoints);
        }

        static StoredSelection forFreeform(String sourceName, int imageWidth, int imageHeight,
                                           Map<Double, List<Double>> axisData, List<Point> orderedPoints) {
            return new StoredSelection(
                    SelectionKind.FREEFORM_DRAW,
                    true,
                    sourceName,
                    imageWidth,
                    imageHeight,
                    System.currentTimeMillis(),
                    copyAxisData(axisData),
                    copyPointList(orderedPoints),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        static StoredSelection forCrop(Rectangle rect) {
            return forCrop(null, -1, -1, rect);
        }

        static StoredSelection forCrop(String sourceName, int imageWidth, int imageHeight, Rectangle rect) {
            return new StoredSelection(
                    SelectionKind.CROP_BOX,
                    false,
                    sourceName,
                    imageWidth,
                    imageHeight,
                    System.currentTimeMillis(),
                    null,
                    null,
                    rect,
                    null,
                    null,
                    null,
                    null
            );
        }

        static StoredSelection forRectangles(List<Rectangle> rectangles) {
            return forRectangles(null, -1, -1, rectangles);
        }

        static StoredSelection forRectangles(String sourceName, int imageWidth, int imageHeight, List<Rectangle> rectangles) {
            return new StoredSelection(
                    SelectionKind.MULTI_RECTANGLE,
                    false,
                    sourceName,
                    imageWidth,
                    imageHeight,
                    System.currentTimeMillis(),
                    null,
                    null,
                    null,
                    rectangles,
                    null,
                    null,
                    null
            );
        }

        static StoredSelection forPoint(Point point) {
            return forPoint(null, -1, -1, point);
        }

        static StoredSelection forPoint(String sourceName, int imageWidth, int imageHeight, Point point) {
            return new StoredSelection(
                    SelectionKind.POINT,
                    false,
                    sourceName,
                    imageWidth,
                    imageHeight,
                    System.currentTimeMillis(),
                    null,
                    null,
                    null,
                    null,
                    point,
                    null,
                    null
            );
        }

        static StoredSelection forPoints(List<Point> points) {
            return forPoints(null, -1, -1, points);
        }

        static StoredSelection forPoints(String sourceName, int imageWidth, int imageHeight, List<Point> points) {
            return new StoredSelection(
                    SelectionKind.MULTI_POINT,
                    false,
                    sourceName,
                    imageWidth,
                    imageHeight,
                    System.currentTimeMillis(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    points,
                    null
            );
        }

        static StoredSelection forLabeledPoints(Map<Point, String> points) {
            return forLabeledPoints(null, -1, -1, points);
        }

        static StoredSelection forLabeledPoints(String sourceName, int imageWidth, int imageHeight, Map<Point, String> points) {
            return new StoredSelection(
                    SelectionKind.LABELED_MULTI_POINT,
                    false,
                    sourceName,
                    imageWidth,
                    imageHeight,
                    System.currentTimeMillis(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    points
            );
        }

        private SelectionKind kind() {
            if (selectionKind != null) {
                return selectionKind;
            }
            return freeform ? SelectionKind.FREEFORM_DRAW : SelectionKind.CROP_BOX;
        }

        boolean isFreeform() {
            return kind() == SelectionKind.FREEFORM_DRAW;
        }

        boolean isCropBox() {
            return kind() == SelectionKind.CROP_BOX;
        }

        boolean isMultiRectangle() {
            return kind() == SelectionKind.MULTI_RECTANGLE;
        }

        boolean isPoint() {
            return kind() == SelectionKind.POINT;
        }

        boolean isMultiPoint() {
            return kind() == SelectionKind.MULTI_POINT;
        }

        boolean isLabeledMultiPoint() {
            return kind() == SelectionKind.LABELED_MULTI_POINT;
        }

        boolean matches(FreeformSelectionWindow.SelectionMode mode) {
            return switch (mode) {
                case FREEFORM_DRAW -> isFreeform();
                case CROP_BOX -> isCropBox();
                case MULTI_RECTANGLE -> isMultiRectangle();
                case POINT -> isPoint();
                case MULTI_POINT -> isMultiPoint();
                case LABELED_MULTI_POINT -> isLabeledMultiPoint();
            };
        }

        String sourceName() {
            return sourceName;
        }

        int imageWidth() {
            return imageWidth;
        }

        int imageHeight() {
            return imageHeight;
        }

        long savedAtEpochMillis() {
            return savedAtEpochMillis;
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

        List<Rectangle> appliedRectangles() {
            return copyRectangleList(appliedRectangles);
        }

        Point appliedPoint() {
            return appliedPoint == null ? null : new Point(appliedPoint);
        }

        List<Point> appliedPoints() {
            return copyPointList(appliedPoints);
        }

        Map<Point, String> appliedLabeledPoints() {
            return copyLabeledPointMap(appliedLabeledPoints);
        }
    }

    private static final class SelectionStorage {

        private static void save(String storageKey, StoredSelection selection) {
            if (selection == null || storageKey == null || storageKey.isBlank()) {
                return;
            }

            try {
                byte[] payload = serialize(selection);
                SELECTION_PREFS.putByteArray(storageKey, payload);
                SELECTION_PREFS.flush();
            } catch (IOException | BackingStoreException | RuntimeException ex) {
                throw new IllegalStateException("Unable to persist selection data for key: " + storageKey, ex);
            }
        }

        private static StoredSelection load(String storageKey) {
            if (storageKey == null || storageKey.isBlank()) {
                return null;
            }

            byte[] payload;
            try {
                payload = SELECTION_PREFS.getByteArray(storageKey, null);
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Unable to read selection data for key: " + storageKey, ex);
            }

            if (payload == null || payload.length == 0) {
                return null;
            }

            try {
                StoredSelection selection = deserialize(payload);
                if (selection == null) {
                    discard(storageKey);
                }
                return selection;
            } catch (IOException | ClassNotFoundException ex) {
                discard(storageKey);
                throw new IllegalStateException("Unable to restore selection data for key: " + storageKey, ex);
            }
        }

        private static void discard(String storageKey) {
            if (storageKey == null || storageKey.isBlank()) {
                return;
            }

            try {
                SELECTION_PREFS.remove(storageKey);
                SELECTION_PREFS.flush();
            } catch (BackingStoreException | RuntimeException ex) {
                throw new IllegalStateException("Unable to discard selection data for key: " + storageKey, ex);
            }
        }

        private static List<String> keys() {
            try {
                SELECTION_PREFS.sync();
                return Arrays.stream(SELECTION_PREFS.keys())
                        .filter(key -> key != null && key.startsWith(PREF_KEY_PREFIX))
                        .filter(key -> !PREF_KEY_AUTO_RESTORE_MODE.equals(key))
                        .sorted()
                        .toList();
            } catch (BackingStoreException | RuntimeException ex) {
                throw new IllegalStateException("Unable to enumerate selection data.", ex);
            }
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
                if (obj instanceof StoredSelection selection) {
                    return selection;
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
/*
        int[] rgbBuffer = new int[width];
        for (int y = 0; y < height; y++) {
            rgbBuffer = image.getRGB(0, y, width, 1, rgbBuffer, 0, width);
            hash = 31 * hash + Arrays.hashCode(rgbBuffer);
        }*/
        hash = Arrays.hashCode(FastRGB.extractPixels(image));

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
            JOptionPane.showMessageDialog(
                    cropperFrame,
                    "Unable to export image:\n" + ex.getMessage(),
                    "Export failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void insertComponentFromArg(FreeformSelectionWindow cropper) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel(selectionArgumentPrompt()));
        JTextField argField = new JTextField(25);
        panel.add(argField);

        int response = JOptionPane.showConfirmDialog(
                cropperFrame,
                panel,
                "Insert component under arg",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (response != JOptionPane.OK_OPTION) {
            return;
        }

        String argText = argField.getText();
        if (argText == null || argText.trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                    cropperFrame,
                    "Argument data cannot be empty.",
                    "Invalid argument data",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        try {
            switch (selectionMode) {
                case FREEFORM_DRAW -> {
                    DrawingArgData data = parseDrawingArg(argText);
                    List<Point> points = toImagePoints(data.orderedPoints);
                    cropper.loadDrawingFromArgument(data.axisData, points);
                }
                case CROP_BOX -> {
                    Rectangle appliedRect = parseRectangleArg(argText);
                    Rectangle originalRect = transform == null ? appliedRect : transform.unapply(appliedRect);
                    cropper.loadCropBox(originalRect);
                }
                case MULTI_RECTANGLE -> cropper.loadSelectedRectangles(toOriginalRectangles(parseRectangleListArg(argText)));
                case POINT -> {
                    Point appliedPoint = parsePointArg(argText);
                    Point originalPoint = transform == null ? appliedPoint : transform.unapply(appliedPoint);
                    cropper.loadSelectedPoint(originalPoint);
                }
                case MULTI_POINT -> cropper.loadSelectedPoints(toOriginalPoints(parsePointListArg(argText)));
                case LABELED_MULTI_POINT -> cropper.loadLabeledSelectedPoints(toOriginalLabeledPoints(parseLabeledPointMapArg(argText)));
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                    cropperFrame,
                    "Unable to parse component data:\n" + ex.getMessage(),
                    "Invalid argument data",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private DrawingArgData parseDrawingArg(String argText) {
        return parseDrawingArgInternal(argText);
    }

    private static DrawingArgData parseDrawingArgInternal(String argText) {
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
                        throw new IllegalArgumentException(
                                "Invalid y value '" + valueText + "' for x value " + keyText + "."
                        );
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
        return parseRectangleArgInternal(argText);
    }

    private static Rectangle parseRectangleArgInternal(String argText) {
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

    private Point parsePointArg(String argText) {
        return parsePointArgInternal(argText);
    }

    private static Point parsePointArgInternal(String argText) {
        String trimmed = argText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Point argument data is empty.");
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Point argument data is empty.");
        }

        Map<String, Integer> values = new LinkedHashMap<>();
        String[] parts = trimmed.split(",");
        for (String part : parts) {
            String piece = part.trim();
            if (piece.isEmpty()) continue;

            int equalsIdx = piece.indexOf('=');
            if (equalsIdx == -1) {
                throw new IllegalArgumentException("Missing '=' in point segment '" + piece + "'.");
            }

            String key = piece.substring(0, equalsIdx).trim().toLowerCase();
            String valueText = piece.substring(equalsIdx + 1).trim();
            if (key.isEmpty() || valueText.isEmpty()) {
                throw new IllegalArgumentException("Invalid point segment '" + piece + "'.");
            }
            if (!key.equals("x") && !key.equals("y")) {
                throw new IllegalArgumentException("Unknown point attribute '" + key + "'.");
            }

            int numericValue;
            try {
                numericValue = Integer.parseInt(valueText);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric value '" + valueText + "' for " + key + ".");
            }
            values.put(key, numericValue);
        }

        for (String required : new String[] { "x", "y" }) {
            if (!values.containsKey(required)) {
                throw new IllegalArgumentException("Missing '" + required + "' in point argument data.");
            }
        }

        return new Point(values.get("x"), values.get("y"));
    }

    private List<Point> parsePointListArg(String argText) {
        return parsePointListArgInternal(argText);
    }

    private static List<Point> parsePointListArgInternal(String argText) {
        String trimmed = argText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Point list argument data is empty.");
        }

        List<Point> points = new ArrayList<>();
        for (String segment : trimmed.split(";")) {
            String pointText = segment.trim();
            if (pointText.isEmpty()) continue;
            points.add(parsePointArgInternal(pointText));
        }
        return points;
    }

    private Map<Point, String> parseLabeledPointMapArg(String argText) {
        return parseLabeledPointMapArgInternal(argText, pointLabels);
    }

    private static Map<Point, String> parseLabeledPointMapArgInternal(String argText, List<String> allowedPointLabels) {
        String trimmed = argText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Labeled point argument data is empty.");
        }

        Map<Point, String> points = new LinkedHashMap<>();
        for (String segment : trimmed.split(";")) {
            String pointText = segment.trim();
            if (pointText.isEmpty()) continue;
            if (pointText.startsWith("[") && pointText.endsWith("]")) {
                pointText = pointText.substring(1, pointText.length() - 1).trim();
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (String part : pointText.split(",")) {
                String piece = part.trim();
                if (piece.isEmpty()) continue;

                int equalsIdx = piece.indexOf('=');
                if (equalsIdx == -1) {
                    throw new IllegalArgumentException("Missing '=' in labeled point segment '" + piece + "'.");
                }

                values.put(
                        piece.substring(0, equalsIdx).trim().toLowerCase(),
                        piece.substring(equalsIdx + 1).trim()
                );
            }

            if (!values.containsKey("x") || !values.containsKey("y") || !values.containsKey("label")) {
                throw new IllegalArgumentException("Labeled point data must include x, y, and label.");
            }

            Point point;
            try {
                point = new Point(Integer.parseInt(values.get("x")), Integer.parseInt(values.get("y")));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid x or y value in labeled point data.");
            }

            String label = values.get("label");
            if (allowedPointLabels != null && !allowedPointLabels.isEmpty() && !allowedPointLabels.contains(label)) {
                throw new IllegalArgumentException("Unknown point label '" + label + "'.");
            }
            points.put(point, label);
        }

        return points;
    }

    private String encodePointList(List<Point> points) {
        if (points == null || points.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner("; ");
        for (Point point : points) {
            if (point == null) continue;
            joiner.add("[x=" + point.x + ",y=" + point.y + "]");
        }
        return joiner.toString();
    }

    private String encodeLabeledPointMap(Map<Point, String> points) {
        if (points == null || points.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner("; ");
        for (Map.Entry<Point, String> entry : points.entrySet()) {
            Point point = entry.getKey();
            String label = entry.getValue();
            if (point == null || label == null) continue;
            joiner.add("[x=" + point.x + ",y=" + point.y + ",label=" + label + "]");
        }
        return joiner.toString();
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
        this.cropBox.complete(null);
        this.selectedRectangles.complete(null);
        this.selectedPoint.complete(null);
        this.selectedPoints.complete(null);
        this.labeledSelectedPoints.complete(null);
        this.selectedColor.complete(null);
        hideColorPickerWindow();
        this.closeGUI();
        FreeformSelectionManager.instantiated = false;
    }

    public Map<Double, List<Double>> getDrawingAxis() throws ExecutionException, InterruptedException {
        if (!freeformDraw) throw new IllegalCallerException("Cannot call getDrawingAxis when freeformDraw is false");
        return this.drawingAxis.get();
    }

    public Rectangle getCropBox() throws ExecutionException, InterruptedException {
        if (freeformDraw) throw new IllegalCallerException("Cannot call getCropBox when freeformDraw is true");
        if (selectionMode == FreeformSelectionWindow.SelectionMode.MULTI_RECTANGLE) {
            throw new IllegalCallerException("Cannot call getCropBox when multi-rectangle selection is true");
        }
        if (isPointSelectionMode()) {
            throw new IllegalCallerException("Cannot call getCropBox when point selection is true");
        }
        return this.cropBox.get();
    }

    public List<Rectangle> getSelectedRectangles() throws ExecutionException, InterruptedException {
        if (selectionMode != FreeformSelectionWindow.SelectionMode.MULTI_RECTANGLE) {
            throw new IllegalCallerException("Cannot call getSelectedRectangles when multi-rectangle selection is false");
        }
        List<Rectangle> rectangles = this.selectedRectangles.get();
        return rectangles == null ? null : copyRectangleList(rectangles);
    }

    public Point getSelectedPoint() throws ExecutionException, InterruptedException {
        if (selectionMode != FreeformSelectionWindow.SelectionMode.POINT) {
            throw new IllegalCallerException("Cannot call getSelectedPoint when point selection is false");
        }
        return this.selectedPoint.get();
    }

    public Color getSelectedColor() throws ExecutionException, InterruptedException {
        colorSelectionRequested = true;
        SwingUtilities.invokeLater(() -> {
            if (cropper != null) {
                activeTool = FreeformSelectionWindow.ToolMode.COLOR_PICKER;
                setToolMode(cropper, activeTool);
            }
        });
        return selectedColor.get();
    }

    public List<Point> getSelectedPoints() throws ExecutionException, InterruptedException {
        if (selectionMode != FreeformSelectionWindow.SelectionMode.MULTI_POINT) {
            throw new IllegalCallerException("Cannot call getSelectedPoints when multi-point selection is false");
        }
        return this.selectedPoints.get();
    }

    public Map<Point, String> getLabeledSelectedPoints() throws ExecutionException, InterruptedException {
        if (selectionMode != FreeformSelectionWindow.SelectionMode.LABELED_MULTI_POINT) {
            throw new IllegalCallerException("Cannot call getLabeledSelectedPoints when labeled multi-point selection is false");
        }
        return this.labeledSelectedPoints.get();
    }
}
