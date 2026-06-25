package com.elijahsarte.celtools.mainex.sandbox;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.FrameParser;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.geomex.Line;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.elijahsarte.celtools.main.colormodel.HSVBounds.buildColumnsFromBoundary;
import static com.elijahsarte.celtools.main.colormodel.HSVBounds.normalizeBoundary;

public final class HSVLineEditor extends JFrame {

    private static final int HSV_EDITOR_SIZE = 256;
    private static final int HSV_LOGICAL_MAX = 100;
    private static final int CTINKQUAL_VERSION = 3;
    private static final int CTINKQUAL_HUE_BUCKETS = 360;
    private static final int CTINKQUAL_SATURATION_BUCKETS = 101;
    private static final int CTINKQUAL_CELL_COUNT = CTINKQUAL_HUE_BUCKETS * CTINKQUAL_SATURATION_BUCKETS;
    private static final int CTINKQUAL_VALUE_PADDING = 1;
    private static final byte CTINKQUAL_NO_VALUE = -1;
    private static final String CTINKQUAL_MAGIC = "CelTools Ink Qualifier Lambda";
    private static final String CTINKQUAL_SPEC_MARKER = "CTINKQUAL-BAND-TABLE";

    private static final double EXPORT_SIMPLIFY_ERROR = 1.5;
    private static final int ANCHOR_HIT_RADIUS = 8;
    private static final int MIN_ANCHOR_GAP = 2;

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "new\\s+(?:[\\w$.]+\\.)?Line\\s*\\(" +
                    "\\s*new\\s+(?:[\\w$.]+\\.)?Point\\s*\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)" +
                    "\\s*,\\s*new\\s+(?:[\\w$.]+\\.)?Point\\s*\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)" +
                    "\\s*\\)"
    );

    private static final Pattern LEGACY_LAMBDA_GROUP_HEADER = Pattern.compile(
            "\\(\\(h\\s*>=\\s*(\\d+)\\s*&&\\s*h\\s*<=\\s*(\\d+)\\s*\\)\\s*&&\\s*\\("
    );

    private static final Pattern LEGACY_LAMBDA_CLAUSE_HEADER = Pattern.compile(
            "\\(s\\s*>=\\s*(\\d+)\\s*&&\\s*s\\s*<=\\s*(\\d+)\\s*&&\\s*v\\s*(<=|>=)\\s*"
    );

    private static final Pattern LEGACY_LAMBDA_EXPR = Pattern.compile(
            ".*?\\((-?\\d+)\\)\\s*/\\s*\\(double\\)\\s*\\((-?\\d+)\\).*?" +
                    "Math\\.abs\\s*\\(\\s*s\\s*-\\s*(-?\\d+)\\s*\\)\\s*\\+\\s*(-?\\d+).*"
    );

    private final TreeMap<Integer, LineSliceDefinition> definitions = new TreeMap<>();
    private final TreeMap<Integer, List<Point>> lowerDefinitions = new TreeMap<>();
    private final BitSet[] activeSaturationsByHue = new BitSet[CTINKQUAL_HUE_BUCKETS];
    private File targetCtInkQualifierFile;
    private boolean suppressModeDefinitionWrite = false;

    private final JSlider hueSlider = new JSlider(0, 359, 0);
    private final JLabel hueLabel = new JLabel("Hue: 0");
    private final JComboBox<String> modeBox =
            new JComboBox<>(new String[]{"Include under line", "Exclude under line"});
    private final JToggleButton warpToggle = new JToggleButton("Warp anchors");
    private final JButton rebuildAnchorsButton = new JButton("Rebuild anchors");
    private final JLabel statusLabel = new JLabel("Draw a boundary, then save the current hue.");

    private final JButton exportPoints = new JButton("Export Java (Points)");
    private final JButton exportLines = new JButton("Export Java (Lines)");
    private final JButton exportLambda = new JButton("Export Java (Lambda)");
    private final JButton loadSnippet = new JButton("Load Java/Lambda");
    private final JButton exportCtInkQualifier = new JButton("Export to ctinkqual");
    private final JButton copy = new JButton("Copy export");

    private final JTextArea exportArea = new JTextArea(12, 70);
    private final GradientPanel gradientPanel = new GradientPanel();

    public HSVLineEditor() {
        this(null);
    }

    public HSVLineEditor(File ctInkQualifierFile) {
        super("HSVBounds Line Editor");
        this.targetCtInkQualifierFile = ctInkQualifierFile;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        exportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        exportArea.setLineWrap(false);
        exportArea.setWrapStyleWord(false);

        hueSlider.setMajorTickSpacing(60);
        hueSlider.setMinorTickSpacing(10);
        hueSlider.setPaintTicks(true);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new javax.swing.border.EmptyBorder(8, 8, 8, 8));

        JPanel controlsRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel controlsRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton saveSlice = new JButton("Save current hue");
        JButton clearSlice = new JButton("Clear current hue");
        JButton clearAll = new JButton("Clear all");

        controlsRow1.add(hueLabel);
        controlsRow1.add(hueSlider);
        controlsRow1.add(modeBox);
        controlsRow1.add(warpToggle);
        controlsRow1.add(rebuildAnchorsButton);

        controlsRow2.add(saveSlice);
        controlsRow2.add(clearSlice);
        controlsRow2.add(clearAll);
        controlsRow2.add(loadSnippet);
        controlsRow2.add(exportCtInkQualifier);
        controlsRow2.add(exportPoints);
        controlsRow2.add(exportLines);
        controlsRow2.add(exportLambda);
        controlsRow2.add(copy);

        top.add(controlsRow1);
        top.add(controlsRow2);
        top.add(statusLabel);

        gradientPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(new javax.swing.border.EmptyBorder(0, 8, 8, 8));
        center.add(gradientPanel, BorderLayout.WEST);
        center.add(new JScrollPane(exportArea), BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(center, BorderLayout.CENTER);

        hueSlider.addChangeListener(e -> refreshCurrentHueView());

        modeBox.addActionListener(e -> {
            boolean includeUnder = modeBox.getSelectedIndex() == 0;
            gradientPanel.setIncludeUnder(includeUnder);

            if (!suppressModeDefinitionWrite) {
                updateCurrentHueIncludeUnder(includeUnder);
            }

            if (!warpToggle.isSelected()) {
                statusLabel.setText("Draw mode enabled. Current hue uses "
                        + (includeUnder ? "include under line." : "exclude under line."));
            } else {
                statusLabel.setText("Warp mode: drag anchors, Shift-click to add, right-click an anchor to remove. Current hue uses "
                        + (includeUnder ? "include under line." : "exclude under line."));
            }
        });

        warpToggle.addActionListener(e -> {
            gradientPanel.setWarpMode(warpToggle.isSelected());
            if (warpToggle.isSelected()) {
                statusLabel.setText("Warp mode: drag anchors, Shift-click to add, right-click an anchor to remove.");
            } else {
                statusLabel.setText("Draw mode enabled.");
            }
        });

        rebuildAnchorsButton.addActionListener(e -> {
            if (gradientPanel.rebuildAnchorsFromCurrentLine()) {
                warpToggle.setSelected(true);
                gradientPanel.setWarpMode(true);
                statusLabel.setText("Anchor handles rebuilt from the current line.");
            } else {
                statusLabel.setText("Draw or load a line first.");
            }
        });

        saveSlice.addActionListener(e -> {
            List<Point> line = gradientPanel.getLine();
            if (line.size() < 2) {
                statusLabel.setText("Draw or warp a line first.");
                return;
            }

            int hue = hueSlider.getValue();
            boolean includeUnder = modeBox.getSelectedIndex() == 0;
            definitions.put(hue, new LineSliceDefinition(hue, includeUnder, line));
            ensureActiveSaturationMaskForHue(hue, line);
            statusLabel.setText("Saved hue " + hue + ". Total saved hues: " + definitions.size());
        });

        clearSlice.addActionListener(e -> {
            int hue = hueSlider.getValue();
            definitions.remove(hue);
            lowerDefinitions.remove(hue);
            activeSaturationsByHue[hue] = null;
            gradientPanel.clearLine();
            gradientPanel.clearLowerLine();
            statusLabel.setText("Cleared hue " + hue + ".");
        });

        clearAll.addActionListener(e -> {
            definitions.clear();
            lowerDefinitions.clear();
            Arrays.fill(activeSaturationsByHue, null);
            gradientPanel.clearLine();
            gradientPanel.clearLowerLine();
            exportArea.setText("");
            statusLabel.setText("Cleared all saved hue slices.");
        });

        loadSnippet.addActionListener(e -> loadFromText());

        exportCtInkQualifier.addActionListener(e -> exportToCtInkQualifier());

        exportPoints.addActionListener(e -> {
            String text = serializeLineSlicesAsPointsJava(definitions);
            exportArea.setText(text);
            exportArea.setCaretPosition(0);
            statusLabel.setText("Exported point-based Java for " + definitions.size() + " hue slice(s).");
        });

        exportLines.addActionListener(e -> {
            String text = serializeLineSlicesAsLinesJava(definitions);
            exportArea.setText(text);
            exportArea.setCaretPosition(0);
            statusLabel.setText("Exported line-based Java for " + definitions.size() + " hue slice(s).");
        });

        exportLambda.addActionListener(e -> {
            String text = serializeLineSlicesAsLambdaJava(definitions, "lambda");
            exportArea.setText(text);
            exportArea.setCaretPosition(0);
            statusLabel.setText("Exported standalone lambda Java.");
        });

        copy.addActionListener(e -> {
            String text = exportArea.getText();
            if (text == null || text.isBlank()) {
                text = serializeAllAsJava(definitions);
                exportArea.setText(text);
            }

            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);

            statusLabel.setText("Export copied to clipboard.");
        });

        gradientPanel.setHue(0);
        gradientPanel.setIncludeUnder(true);

        if (this.targetCtInkQualifierFile != null) {
            loadCtInkQualifierFile(this.targetCtInkQualifierFile);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HSVLineEditor().showEditor());
    }

    public static void openCtInkQualifier(File file) {
        SwingUtilities.invokeLater(() -> new HSVLineEditor(file).showEditor());
    }

    public void showEditor() {
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void refreshCurrentHueView() {
        int hue = hueSlider.getValue();
        hueLabel.setText("Hue: " + hue);
        gradientPanel.setHue(hue);

        LineSliceDefinition def = definitions.get(hue);
        if (def != null) {
            suppressModeDefinitionWrite = true;
            try {
                modeBox.setSelectedIndex(def.includeUnder ? 0 : 1);
            } finally {
                suppressModeDefinitionWrite = false;
            }

            gradientPanel.setIncludeUnder(def.includeUnder);
            gradientPanel.setLine(def.linePoints);
            gradientPanel.setLowerLine(lowerDefinitions.get(hue));
            statusLabel.setText("Loaded saved boundary for hue " + hue + ".");
        } else {
            gradientPanel.setIncludeUnder(modeBox.getSelectedIndex() == 0);
            gradientPanel.clearLine();
            gradientPanel.clearLowerLine();
            statusLabel.setText("Hue " + hue + " has no saved boundary.");
        }

        gradientPanel.setWarpMode(warpToggle.isSelected());
    }

    private void updateCurrentHueIncludeUnder(boolean includeUnder) {
        int hue = hueSlider.getValue();
        LineSliceDefinition existing = definitions.get(hue);
        if (existing == null) {
            return;
        }

        List<Point> line = gradientPanel.getLine();
        if (line.size() < 2) {
            line = existing.linePoints;
        }

        definitions.put(hue, new LineSliceDefinition(hue, includeUnder, line));
        ensureActiveSaturationMaskForHue(hue, line);
    }

    private void loadFromText() {
        String text = exportArea.getText();
        if (text == null || text.isBlank()) {
            statusLabel.setText("Paste a line-based HSVBounds snippet or an exported lambda first.");
            return;
        }

        List<ImportedGroup> groups = parseImportedGroups(text);
        if (groups.isEmpty()) {
            statusLabel.setText("Could not parse any line-based HSVBounds or lambda constructions.");
            return;
        }

        definitions.clear();
        lowerDefinitions.clear();
        Arrays.fill(activeSaturationsByHue, null);

        for (ImportedGroup group : groups) {
            List<Line> normalized = normalizeBoundary(group.lines);
            if (normalized.isEmpty()) {
                continue;
            }

            List<Point> panelPoints = panelPointsFromLogicalLines(normalized);
            if (panelPoints.size() < 2) {
                continue;
            }

            int hueMin = MathEx.bound(Math.min(group.hueMin, group.hueMax), 0, 359);
            int hueMax = MathEx.bound(Math.max(group.hueMin, group.hueMax), 0, 359);

            for (int hue = hueMin; hue <= hueMax; hue++) {
                definitions.put(hue, new LineSliceDefinition(hue, group.includeUnder, panelPoints));
                ensureActiveSaturationMaskForHue(hue, panelPoints);
            }
        }

        if (definitions.isEmpty()) {
            statusLabel.setText("Parsed input, but no usable line ranges were found.");
            return;
        }

        int firstHue = definitions.firstKey();
        hueSlider.setValue(firstHue);
        refreshCurrentHueView();
        statusLabel.setText("Loaded " + groups.size() + " range(s) across " + definitions.size() + " hue slice(s).");
    }


    private void loadCtInkQualifierFile(File file) {
        if (file == null) {
            return;
        }

        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!text.startsWith(CTINKQUAL_MAGIC)) {
                throw new IOException("Not a Cel Tools ink qualifier file.");
            }

            int version = readCtInkQualifierHeaderInt(text, "Version", -1);
            if (version != CTINKQUAL_VERSION) {
                List<ImportedGroup> legacyGroups = parseImportedGroups(text);
                if (legacyGroups.isEmpty()) {
                    throw new IOException("Unsupported ctinkqual version: " + version);
                }
                loadImportedGroups(legacyGroups);
                targetCtInkQualifierFile = file;
                statusLabel.setText("Loaded legacy ctinkqual as editable HSV line groups: " + file.getName());
                return;
            }

            byte[] minValues = decodeCtInkQualifierTable(readCtInkQualifierHeaderString(text, "MinValuesBase64"), "MinValuesBase64");
            byte[] maxValues = decodeCtInkQualifierTable(readCtInkQualifierHeaderString(text, "MaxValuesBase64"), "MaxValuesBase64");

            definitions.clear();
            lowerDefinitions.clear();
            Arrays.fill(activeSaturationsByHue, null);

            for (int h = 0; h < CTINKQUAL_HUE_BUCKETS; h++) {
                ArrayList<Point> lower = new ArrayList<>();
                ArrayList<Point> upper = new ArrayList<>();
                BitSet active = new BitSet(CTINKQUAL_SATURATION_BUCKETS);

                for (int s = 0; s < CTINKQUAL_SATURATION_BUCKETS; s++) {
                    int index = ctInkQualifierTableIndex(h, s);
                    int min = minValues[index];
                    int max = maxValues[index];
                    if (min < 0 || max < 0) {
                        continue;
                    }

                    active.set(s);
                    lower.add(new Point(sToPanelX(s), vToPanelY(MathEx.bound(min, 0, 100))));
                    upper.add(new Point(sToPanelX(s), vToPanelY(MathEx.bound(max, 0, 100))));
                }

                if (upper.size() == 1) {
                    Point u = upper.get(0);
                    Point l = lower.get(0);
                    upper.add(new Point(Math.min(HSV_EDITOR_SIZE - 1, u.x + 1), u.y));
                    lower.add(new Point(Math.min(HSV_EDITOR_SIZE - 1, l.x + 1), l.y));
                }

                if (upper.size() >= 2) {
                    definitions.put(h, new LineSliceDefinition(h, true, upper));
                    lowerDefinitions.put(h, lower);
                    activeSaturationsByHue[h] = active;
                }
            }

            targetCtInkQualifierFile = file;
            if (!definitions.isEmpty()) {
                hueSlider.setValue(definitions.firstKey());
                refreshCurrentHueView();
            }
            statusLabel.setText("Loaded ctinkqual band table: " + file.getName());
        } catch (Exception ex) {
            statusLabel.setText("Unable to load ctinkqual: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Unable to load ctinkqual:\n" + ex.getMessage(), "Load Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadImportedGroups(List<ImportedGroup> groups) {
        definitions.clear();
        lowerDefinitions.clear();
        Arrays.fill(activeSaturationsByHue, null);

        for (ImportedGroup group : groups) {
            List<Line> normalized = normalizeBoundary(group.lines);
            if (normalized.isEmpty()) continue;
            List<Point> panelPoints = panelPointsFromLogicalLines(normalized);
            if (panelPoints.size() < 2) continue;
            int hueMin = MathEx.bound(Math.min(group.hueMin, group.hueMax), 0, 359);
            int hueMax = MathEx.bound(Math.max(group.hueMin, group.hueMax), 0, 359);
            for (int hue = hueMin; hue <= hueMax; hue++) {
                definitions.put(hue, new LineSliceDefinition(hue, group.includeUnder, panelPoints));
                ensureActiveSaturationMaskForHue(hue, panelPoints);
            }
        }

        if (!definitions.isEmpty()) {
            hueSlider.setValue(definitions.firstKey());
            refreshCurrentHueView();
        }
    }

    private void exportToCtInkQualifier() {
        File output = targetCtInkQualifierFile;
        if (output == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save CTINKQUAL");
            chooser.setSelectedFile(new File("customink.ctinkqual"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            output = ensureCtInkQualifierExtension(chooser.getSelectedFile());
        }

        try {
            Files.writeString(output.toPath(), serializeCtInkQualifier(), StandardCharsets.UTF_8);
            targetCtInkQualifierFile = output;
            statusLabel.setText("Exported ctinkqual to " + output.getName());
        } catch (Exception ex) {
            statusLabel.setText("Unable to export ctinkqual: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Unable to export ctinkqual:\n" + ex.getMessage(), "Export Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String serializeCtInkQualifier() {
        byte[] minValues = new byte[CTINKQUAL_CELL_COUNT];
        byte[] maxValues = new byte[CTINKQUAL_CELL_COUNT];
        Arrays.fill(minValues, CTINKQUAL_NO_VALUE);
        Arrays.fill(maxValues, CTINKQUAL_NO_VALUE);

        int samples = 0;
        for (LineSliceDefinition def : definitions.values()) {
            if (def == null || def.linePoints == null || def.linePoints.size() < 2) {
                continue;
            }

            int hue = MathEx.bound(def.hue, 0, CTINKQUAL_HUE_BUCKETS - 1);
            BitSet active = activeSaturationsByHue[hue];
            if (active == null || active.isEmpty()) {
                active = buildActiveSaturationMask(def.linePoints);
            }

            int[] upperY = rasterizeBoundary(def.linePoints, HSV_EDITOR_SIZE, HSV_EDITOR_SIZE);
            List<Point> lowerLine = lowerDefinitions.get(hue);
            int[] lowerY = lowerLine != null && lowerLine.size() >= 2
                    ? rasterizeBoundary(lowerLine, HSV_EDITOR_SIZE, HSV_EDITOR_SIZE)
                    : upperY;

            for (int s = active.nextSetBit(0); s >= 0 && s < CTINKQUAL_SATURATION_BUCKETS; s = active.nextSetBit(s + 1)) {
                int panelX = sToPanelX(s);
                int min = panelYToV(lowerY[panelX]);
                int max = panelYToV(upperY[panelX]);
                if (min > max) {
                    int tmp = min;
                    min = max;
                    max = tmp;
                }
                int index = ctInkQualifierTableIndex(hue, s);
                minValues[index] = (byte) MathEx.bound(min, 0, 100);
                maxValues[index] = (byte) MathEx.bound(max, 0, 100);
                samples++;
            }
        }

        String minBase64 = Base64.getEncoder().encodeToString(minValues);
        String maxBase64 = Base64.getEncoder().encodeToString(maxValues);
        StringBuilder sb = new StringBuilder();
        sb.append(CTINKQUAL_MAGIC).append('\n');
        sb.append("Version: ").append(CTINKQUAL_VERSION).append('\n');
        sb.append("Samples: ").append(samples).append('\n');
        sb.append("ValuePadding: ").append(CTINKQUAL_VALUE_PADDING).append('\n');
        sb.append("MinValuesBase64: ").append(minBase64).append('\n');
        sb.append("MaxValuesBase64: ").append(maxBase64).append('\n');
        sb.append('\n');
        sb.append("/* ").append(CTINKQUAL_SPEC_MARKER).append('\n');
        sb.append("Exported from HSVLineEditor. Each H/S bucket stores editable min and max V values.\n");
        sb.append("*/\n\n");
        sb.append(serializeCtInkQualifierPredicate(minBase64, maxBase64, "inkQual"));
        return sb.toString();
    }

    private static String serializeCtInkQualifierPredicate(String minBase64, String maxBase64, String predicateName) {
        String safeName = sanitizeJavaIdentifier(predicateName, "inkQual");
        StringBuilder sb = new StringBuilder();
        sb.append("protected static final java.util.function.Predicate<double[]> ").append(safeName).append(" = new java.util.function.Predicate<>() {\n");
        sb.append("    private final int HUE_BUCKETS = ").append(CTINKQUAL_HUE_BUCKETS).append(";\n");
        sb.append("    private final int SATURATION_BUCKETS = ").append(CTINKQUAL_SATURATION_BUCKETS).append(";\n");
        sb.append("    private final int VALUE_PADDING = ").append(CTINKQUAL_VALUE_PADDING).append(";\n");
        sb.append("    private final byte[] MIN_VALUES = java.util.Base64.getDecoder().decode(\"").append(minBase64).append("\");\n");
        sb.append("    private final byte[] MAX_VALUES = java.util.Base64.getDecoder().decode(\"").append(maxBase64).append("\");\n\n");
        sb.append("    @Override\n");
        sb.append("    public boolean test(double[] hsv) {\n");
        sb.append("        if (hsv == null || hsv.length < 3) return false;\n");
        sb.append("        int h = (int) Math.floor(hsv[0]);\n");
        sb.append("        h %= HUE_BUCKETS;\n");
        sb.append("        if (h < 0) h += HUE_BUCKETS;\n");
        sb.append("        int s = (int) hsv[1];\n");
        sb.append("        int v = (int) hsv[2];\n");
        sb.append("        if (s < 0 || s >= SATURATION_BUCKETS || v < 0 || v > 100) return false;\n");
        sb.append("        int index = h * SATURATION_BUCKETS + s;\n");
        sb.append("        int min = MIN_VALUES[index];\n");
        sb.append("        if (min < 0) return false;\n");
        sb.append("        int max = MAX_VALUES[index];\n");
        sb.append("        return v >= Math.max(0, min - VALUE_PADDING) && v <= Math.min(100, max + VALUE_PADDING);\n");
        sb.append("    }\n");
        sb.append("};\n");
        return sb.toString();
    }

    private void ensureActiveSaturationMaskForHue(int hue, List<Point> line) {
        hue = MathEx.bound(hue, 0, CTINKQUAL_HUE_BUCKETS - 1);
        if (activeSaturationsByHue[hue] == null || activeSaturationsByHue[hue].isEmpty()) {
            activeSaturationsByHue[hue] = buildActiveSaturationMask(line);
        }
    }

    private static BitSet buildActiveSaturationMask(List<Point> line) {
        BitSet active = new BitSet(CTINKQUAL_SATURATION_BUCKETS);
        List<Point> logical = logicalPointsFromPanelLine(line);
        if (logical.isEmpty()) {
            return active;
        }
        int min = CTINKQUAL_SATURATION_BUCKETS - 1;
        int max = 0;
        for (Point p : logical) {
            min = Math.min(min, MathEx.bound(p.x, 0, 100));
            max = Math.max(max, MathEx.bound(p.x, 0, 100));
        }
        active.set(min, max + 1);
        return active;
    }

    private static List<Point> logicalPointsFromPanelLine(List<Point> panelLine) {
        if (panelLine == null || panelLine.isEmpty()) return Collections.emptyList();
        TreeMap<Integer, Point> byS = new TreeMap<>();
        for (Point p : panelLine) {
            if (p == null) continue;
            int s = panelXToS(p.x);
            int v = panelYToV(p.y);
            byS.put(s, new Point(s, v));
        }
        return new ArrayList<>(byS.values());
    }

    private static int ctInkQualifierTableIndex(int h, int s) {
        return h * CTINKQUAL_SATURATION_BUCKETS + s;
    }

    private static int readCtInkQualifierHeaderInt(String text, String key, int fallback) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:\\s*(-?\\d+)\\s*$").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static String readCtInkQualifierHeaderString(String text, String key) throws IOException {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:\\s*(.*?)\\s*$").matcher(text);
        if (!matcher.find()) {
            throw new IOException("Missing ctinkqual header: " + key);
        }
        return matcher.group(1).trim();
    }

    private static byte[] decodeCtInkQualifierTable(String encoded, String key) throws IOException {
        try {
            byte[] table = Base64.getDecoder().decode(encoded);
            if (table.length != CTINKQUAL_CELL_COUNT) {
                throw new IOException(key + " has invalid length: " + table.length + ", expected " + CTINKQUAL_CELL_COUNT);
            }
            return table;
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid Base64 data for " + key, ex);
        }
    }

    private static File ensureCtInkQualifierExtension(File file) {
        if (file == null) return null;
        String suffix = ".ctinkqual";
        return file.getName().toLowerCase(Locale.ROOT).endsWith(suffix)
                ? file
                : new File(file.getAbsolutePath() + suffix);
    }

    private static String serializeAllAsJava(TreeMap<Integer, LineSliceDefinition> defs) {
        return "// ===== POINT VERSION =====\n"
                + serializeLineSlicesAsPointsJava(defs)
                + "\n\n// ===== LINE VERSION =====\n"
                + serializeLineSlicesAsLinesJava(defs)
                + "\n\n// ===== LAMBDA VERSION =====\n"
                + serializeLineSlicesAsLambdaJava(defs, "lambda");
    }

    private static String serializeLineSlicesAsPointsJava(TreeMap<Integer, LineSliceDefinition> defs) {
        StringBuilder sb = new StringBuilder();

        sb.append("public static HSVBounds buildExportedBoundsPoints() {\n");
        sb.append("    java.util.TreeMap<Integer, PointCollection> hsvPts = new java.util.TreeMap<>();\n");

        for (LineSliceDefinition def : defs.values()) {
            List<Line> lines = normalizeBoundary(simplifyToLines(def.linePoints));
            if (lines.isEmpty()) {
                continue;
            }

            PointCollection cols = buildColumnsFromBoundary(lines, def.includeUnder);

            sb.append("    {\n");
            sb.append("        PointCollection pts = new PointCollection();\n");

            for (int s : cols.xes()) {
                List<Integer> vs = cols.getYesAtX(s);

                sb.append("        pts.addAtX(")
                        .append(s)
                        .append(", java.util.Arrays.asList(");

                for (int i = 0; i < vs.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(vs.get(i));
                }

                sb.append("));\n");
            }

            sb.append("        hsvPts.put(").append(def.hue).append(", pts);\n");
            sb.append("    }\n");
        }

        sb.append("    return new HSVBounds(hsvPts);\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String serializeLineSlicesAsLinesJava(TreeMap<Integer, LineSliceDefinition> defs) {
        List<LineGroup> groups = coalesceLineGroups(defs);
        StringBuilder sb = new StringBuilder();

        sb.append("public static java.util.List<HSVBounds> buildExportedBoundsLines() {\n");

        if (groups.isEmpty()) {
            sb.append("    return java.util.Collections.emptyList();\n");
            sb.append("}\n");
            return sb.toString();
        }

        sb.append("    return java.util.Arrays.asList(\n");

        for (int i = 0; i < groups.size(); i++) {
            appendHSVBoundsConstruction(sb, groups.get(i), "        ");
            if (i < groups.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("    );\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendHSVBoundsConstruction(StringBuilder sb, LineGroup g, String indent) {
        sb.append(indent)
                .append("new HSVBounds(")
                .append(g.hueMin)
                .append(", ")
                .append(g.hueMax)
                .append(", ")
                .append(g.includeUnder)
                .append(", java.util.Arrays.asList(\n");

        for (int i = 0; i < g.lines.size(); i++) {
            Line line = g.lines.get(i);
            Point a = line.start();
            Point b = line.end();

            sb.append(indent)
                    .append("    new Line(new Point(")
                    .append(a.x).append(", ").append(a.y)
                    .append("), new Point(")
                    .append(b.x).append(", ").append(b.y)
                    .append("))");

            if (i < g.lines.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append(indent).append("))");
    }

    private static String serializeLineSlicesAsLambdaJava(
            TreeMap<Integer, LineSliceDefinition> defs,
            String predicateName
    ) {
        List<LineGroup> groups = coalesceLineGroups(defs);
        String safeName = sanitizeJavaIdentifier(predicateName, "lambda");

        StringBuilder sb = new StringBuilder();

        sb.append("/* HSVLINEEDITOR-LAMBDA-SPEC\n");
        for (LineGroup g : groups) {
            sb.append(g.hueMin)
                    .append(",")
                    .append(g.hueMax)
                    .append(",")
                    .append(g.includeUnder)
                    .append(":");

            for (int i = 0; i < g.lines.size(); i++) {
                Line line = g.lines.get(i);
                Point a = line.start();
                Point b = line.end();

                if (i > 0) {
                    sb.append(";");
                }

                sb.append(a.x).append(",").append(a.y)
                        .append(",")
                        .append(b.x).append(",").append(b.y);
            }
            sb.append("\n");
        }
        sb.append("*/\n");

        sb.append("protected static final java.util.function.Predicate<double[]> ")
                .append(safeName)
                .append(" = (hsv) ->\n");

        if (groups.isEmpty()) {
            sb.append("        false;\n");
            return sb.toString();
        }

        for (int i = 0; i < groups.size(); i++) {
            if (i == 0) {
                sb.append("        ");
            } else {
                sb.append("\n        || ");
            }
            appendLambdaGroupExpression(sb, groups.get(i));
        }

        sb.append(";\n");
        return sb.toString();
    }

    private static void appendLambdaGroupExpression(StringBuilder sb, LineGroup g) {
        sb.append("((((int) Math.floor(hsv[0])) >= ")
                .append(g.hueMin)
                .append(" && ((int) Math.floor(hsv[0])) <= ")
                .append(g.hueMax)
                .append(") && (");

        for (int i = 0; i < g.lines.size(); i++) {
            if (i > 0) {
                sb.append(" || ");
            }

            Line line = g.lines.get(i);
            int x1 = line.start().x;
            int y1 = line.start().y;
            int x2 = line.end().x;
            int y2 = line.end().y;

            sb.append("((((int) hsv[1]) >= ")
                    .append(x1)
                    .append(" && ((int) hsv[1]) <= ")
                    .append(x2)
                    .append(") && (((int) hsv[2]) ")
                    .append(lambdaUnderOperator(g.includeUnder))
                    .append(lambdaLineExpr(x1, y1, x2, y2))
                    .append("))");
        }

        sb.append("))");
    }

    private static String lambdaUnderOperator(boolean includeUnder) {
        // Panel Y grows downward, but HSV value grows upward.
        // Therefore, under the drawn line means value <= boundary value.
        return includeUnder ? "<= " : ">= ";
    }

    private static String lambdaLineExpr(int x1, int y1, int x2, int y2) {
        if (x1 == x2 || y1 == y2) {
            return Integer.toString(y1);
        }

        return "(int) Math.floor(((" +
                (y2 - y1) +
                ") / (double) (" +
                (x2 - x1) +
                ")) * Math.abs(((int) hsv[1]) - " +
                x1 +
                ") + " +
                y1 +
                ")";
    }

    private static String sanitizeJavaIdentifier(String raw, String fallback) {
        String value = (raw == null || raw.isBlank()) ? fallback : raw.trim();
        value = value.replaceAll("[^A-Za-z0-9_$]", "_");
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            value = "_" + value;
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }

        return out.toString();
    }

    private static List<LineGroup> coalesceLineGroups(TreeMap<Integer, LineSliceDefinition> defs) {
        ArrayList<LineGroup> out = new ArrayList<>();
        LineGroup curr = null;

        for (LineSliceDefinition def : defs.values()) {
            if (def == null || def.linePoints == null || def.linePoints.size() < 2) {
                continue;
            }

            List<Line> lines = normalizeBoundary(simplifyToLines(def.linePoints));
            if (lines.isEmpty()) {
                continue;
            }

            if (curr != null
                    && def.hue == curr.hueMax + 1
                    && curr.includeUnder == def.includeUnder
                    && sameExportLines(curr.lines, lines)) {
                curr.hueMax = def.hue;
                continue;
            }

            curr = new LineGroup(def.hue, def.hue, def.includeUnder, lines);
            out.add(curr);
        }

        return out;
    }

    private static boolean sameExportLines(List<Line> a, List<Line> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            Line la = a.get(i);
            Line lb = b.get(i);

            if (la.start().x != lb.start().x || la.start().y != lb.start().y
                    || la.end().x != lb.end().x || la.end().y != lb.end().y) {
                return false;
            }
        }

        return true;
    }

    private static List<Line> simplifyToLines(List<Point> rawPoints) {
        List<Point> simplified = simplifyPolyline(rawPoints, EXPORT_SIMPLIFY_ERROR);
        ArrayList<Line> out = new ArrayList<>();

        for (int i = 1; i < simplified.size(); i++) {
            Point a = simplified.get(i - 1);
            Point b = simplified.get(i);

            if (a.x == b.x && a.y == b.y) {
                continue;
            }

            out.add(new Line(new Point(a), new Point(b)));
        }

        return out;
    }

    private static List<Point> simplifyPolyline(List<Point> rawPoints, double maxError) {
        List<Point> pts = normalizeDrawnPoints(rawPoints);
        if (pts.size() <= 2) {
            return pts;
        }

        ArrayList<Point> out = new ArrayList<>();
        out.add(new Point(pts.get(0)));

        int anchor = 0;
        while (anchor < pts.size() - 1) {
            int best = anchor + 1;

            for (int candidate = anchor + 2; candidate < pts.size(); candidate++) {
                if (maxDeviationFromSegment(pts, anchor, candidate) <= maxError) {
                    best = candidate;
                } else {
                    break;
                }
            }

            out.add(new Point(pts.get(best)));
            anchor = best;
        }

        return out;
    }

    private static double maxDeviationFromSegment(List<Point> pts, int startIdx, int endIdx) {
        Point a = pts.get(startIdx);
        Point b = pts.get(endIdx);
        double max = 0.0;

        for (int i = startIdx + 1; i < endIdx; i++) {
            max = Math.max(max, distanceToSegment(pts.get(i), a, b));
        }

        return max;
    }

    private static double distanceToSegment(Point p, Point a, Point b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;

        if (dx == 0.0 && dy == 0.0) {
            return Point.distance(p.x, p.y, a.x, a.y);
        }

        double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / ((dx * dx) + (dy * dy));
        t = MathEx.bound(t, 0.0, 1.0);

        double px = a.x + dx * t;
        double py = a.y + dy * t;
        return Point.distance(p.x, p.y, px, py);
    }

    private static int panelXToS(int x) {
        x = MathEx.bound(x, 0, HSV_EDITOR_SIZE - 1);
        return MathEx.bound(
                MathEx.roundInt(FrameParser.convertScale(x, 0, HSV_EDITOR_SIZE - 1, 0, 100)),
                0,
                100
        );
    }

    private static int panelYToV(int y) {
        y = MathEx.bound(y, 0, HSV_EDITOR_SIZE - 1);
        return MathEx.bound(
                MathEx.roundInt(FrameParser.convertScale((HSV_EDITOR_SIZE - 1) - y, 0, HSV_EDITOR_SIZE - 1, 0, 100)),
                0,
                100
        );
    }

    private static int sToPanelX(int s) {
        return MathEx.bound(
                MathEx.roundInt((s * (HSV_EDITOR_SIZE - 1)) / (double) HSV_LOGICAL_MAX),
                0,
                HSV_EDITOR_SIZE - 1
        );
    }

    private static int vToPanelY(int v) {
        return MathEx.bound(
                MathEx.roundInt(((HSV_LOGICAL_MAX - v) * (HSV_EDITOR_SIZE - 1)) / (double) HSV_LOGICAL_MAX),
                0,
                HSV_EDITOR_SIZE - 1
        );
    }

    private static List<Point> normalizeDrawnPoints(List<Point> rawPoints) {
        if (rawPoints == null || rawPoints.isEmpty()) {
            return Collections.emptyList();
        }

        TreeMap<Integer, Point> byS = new TreeMap<>();

        for (Point p : rawPoints) {
            if (p == null) {
                continue;
            }

            int s = panelXToS(p.x);
            int v = panelYToV(p.y);
            byS.put(s, new Point(s, v));
        }

        if (byS.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<Point> pts = new ArrayList<>(byS.values());

        Point first = pts.get(0);
        Point last = pts.get(pts.size() - 1);

        if (first.x > 0) {
            pts.add(0, new Point(0, first.y));
        }
        if (last.x < 100) {
            pts.add(new Point(100, last.y));
        }

        return pts;
    }

    private static List<Point> panelPointsFromLogicalLines(List<Line> lines) {
        List<Point> logical = logicalPointsFromLines(lines);
        ArrayList<Point> out = new ArrayList<>(logical.size());

        for (Point p : logical) {
            out.add(new Point(sToPanelX(p.x), vToPanelY(p.y)));
        }

        return out;
    }

    private static List<Point> logicalPointsFromLines(List<Line> lines) {
        List<Line> normalized = normalizeBoundary(lines);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<Point> pts = new ArrayList<>();
        pts.add(new Point(normalized.get(0).start()));

        for (Line line : normalized) {
            Point end = line.end();
            Point last = pts.get(pts.size() - 1);
            if (last.x != end.x || last.y != end.y) {
                pts.add(new Point(end));
            }
        }

        return pts;
    }

    private static List<Point> buildWarpAnchorPoints(List<Point> rawPanelPoints) {
        List<Line> lines = normalizeBoundary(simplifyToLines(rawPanelPoints));
        ArrayList<Point> logical = new ArrayList<>(logicalPointsFromLines(lines));

        if (logical.size() < 4) {
            logical = new ArrayList<>(sampleFallbackLogicalAnchors(rawPanelPoints, 5));
        }

        ArrayList<Point> out = new ArrayList<>(logical.size());
        for (Point p : logical) {
            out.add(new Point(sToPanelX(p.x), vToPanelY(p.y)));
        }
        return out;
    }

    private static List<Point> sampleFallbackLogicalAnchors(List<Point> rawPanelPoints, int count) {
        if (rawPanelPoints == null || rawPanelPoints.size() < 2 || count < 2) {
            return Collections.emptyList();
        }

        int[] yByX = rasterizeBoundary(rawPanelPoints, HSV_EDITOR_SIZE, HSV_EDITOR_SIZE);
        TreeMap<Integer, Point> byS = new TreeMap<>();

        for (int i = 0; i < count; i++) {
            int x = MathEx.roundInt((i * (HSV_EDITOR_SIZE - 1)) / (double) (count - 1));
            int s = panelXToS(x);
            int v = panelYToV(yByX[x]);
            byS.put(s, new Point(s, v));
        }

        return new ArrayList<>(byS.values());
    }

    private static List<Point> deepCopyPoints(List<Point> points) {
        ArrayList<Point> copy = new ArrayList<>();
        if (points == null) {
            return copy;
        }

        for (Point p : points) {
            if (p != null) {
                copy.add(new Point(p));
            }
        }

        return copy;
    }

    private static List<Point> canonicalizeLine(List<Point> rawPoints, int width, int height) {
        if (rawPoints == null || rawPoints.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<Point> pts = new ArrayList<>(rawPoints.size());
        for (Point p : rawPoints) {
            if (p == null) {
                continue;
            }

            pts.add(new Point(
                    MathEx.bound(p.x, 0, width - 1),
                    MathEx.bound(p.y, 0, height - 1)
            ));
        }

        if (pts.isEmpty()) {
            return Collections.emptyList();
        }

        pts.sort(Comparator.comparingInt((Point p) -> p.x).thenComparingInt(p -> p.y));

        ArrayList<Point> dedup = new ArrayList<>(pts.size());
        for (Point p : pts) {
            if (dedup.isEmpty()) {
                dedup.add(new Point(p));
                continue;
            }

            Point last = dedup.get(dedup.size() - 1);
            if (last.x == p.x) {
                dedup.set(dedup.size() - 1, new Point(p));
            } else {
                dedup.add(new Point(p));
            }
        }

        Point first = dedup.get(0);
        Point last = dedup.get(dedup.size() - 1);

        if (first.x > 0) {
            dedup.add(0, new Point(0, first.y));
        }
        if (last.x < width - 1) {
            dedup.add(new Point(width - 1, last.y));
        }

        return dedup;
    }

    private static int[] rasterizeBoundary(List<Point> rawPoints, int width, int height) {
        List<Point> pts = canonicalizeLine(rawPoints, width, height);
        int[] yByX = new int[width];

        if (pts.isEmpty()) {
            Arrays.fill(yByX, height / 2);
            return yByX;
        }

        if (pts.size() == 1) {
            Arrays.fill(yByX, pts.get(0).y);
            return yByX;
        }

        int seg = 0;
        for (int x = 0; x < width; x++) {
            while (seg < pts.size() - 2 && x > pts.get(seg + 1).x) {
                seg++;
            }

            Point a = pts.get(seg);
            Point b = pts.get(seg + 1);

            int y;
            if (a.x == b.x) {
                y = b.y;
            } else {
                double t = (x - a.x) / (double) (b.x - a.x);
                y = MathEx.roundInt(a.y + ((b.y - a.y) * t));
            }

            yByX[x] = MathEx.bound(y, 0, height - 1);
        }

        return yByX;
    }

    private static java.awt.Polygon buildFillPolygon(List<Point> rawLine, boolean includeUnder) {
        int[] yByX = rasterizeBoundary(rawLine, HSV_EDITOR_SIZE, HSV_EDITOR_SIZE);
        java.awt.Polygon poly = new java.awt.Polygon();

        if (includeUnder) {
            poly.addPoint(0, HSV_EDITOR_SIZE - 1);
            for (int x = 0; x < HSV_EDITOR_SIZE; x++) {
                poly.addPoint(x, yByX[x]);
            }
            poly.addPoint(HSV_EDITOR_SIZE - 1, HSV_EDITOR_SIZE - 1);
        } else {
            poly.addPoint(0, 0);
            for (int x = 0; x < HSV_EDITOR_SIZE; x++) {
                poly.addPoint(x, yByX[x]);
            }
            poly.addPoint(HSV_EDITOR_SIZE - 1, 0);
        }

        return poly;
    }

    private static java.awt.Polygon buildBandPolygon(List<Point> lowerLine, List<Point> upperLine) {
        int[] lowerY = rasterizeBoundary(lowerLine, HSV_EDITOR_SIZE, HSV_EDITOR_SIZE);
        int[] upperY = rasterizeBoundary(upperLine, HSV_EDITOR_SIZE, HSV_EDITOR_SIZE);
        java.awt.Polygon poly = new java.awt.Polygon();
        for (int x = 0; x < HSV_EDITOR_SIZE; x++) {
            poly.addPoint(x, Math.min(lowerY[x], upperY[x]));
        }
        for (int x = HSV_EDITOR_SIZE - 1; x >= 0; x--) {
            poly.addPoint(x, Math.max(lowerY[x], upperY[x]));
        }
        return poly;
    }

    private static List<ImportedGroup> parseImportedGroups(String text) {
        List<ImportedGroup> groups = parseHSVBoundsConstructors(text);
        if (!groups.isEmpty()) {
            return groups;
        }

        groups = parsePutHueRangeCalls(text);
        if (!groups.isEmpty()) {
            return groups;
        }

        groups = parseLambdaMetadata(text);
        if (!groups.isEmpty()) {
            return groups;
        }

        return parseLegacyLambda(text);
    }

    private static List<ImportedGroup> parseHSVBoundsConstructors(String text) {
        ArrayList<ImportedGroup> out = new ArrayList<>();

        for (String call : extractCalls(text, "new HSVBounds(")) {
            ImportedGroup group = parseHSVBoundsConstructorCall(call);
            if (group != null) {
                out.add(group);
            }
        }

        return out;
    }

    private static ImportedGroup parseHSVBoundsConstructorCall(String call) {
        int open = call.indexOf('(');
        int close = call.lastIndexOf(')');
        if (open < 0 || close < 0 || close <= open) {
            return null;
        }

        String args = call.substring(open + 1, close);
        List<String> parts = splitTopLevel(args, ',');

        if (parts.size() != 3 && parts.size() != 4) {
            return null;
        }

        Integer hueMin = tryParseInt(parts.get(0));
        Integer hueMax = tryParseInt(parts.get(1));
        if (hueMin == null || hueMax == null) {
            return null;
        }

        boolean includeUnder = true;
        String linePart;

        if (parts.size() == 4) {
            String boolStr = parts.get(2).trim();
            if (!"true".equals(boolStr) && !"false".equals(boolStr)) {
                return null;
            }
            includeUnder = Boolean.parseBoolean(boolStr);
            linePart = parts.get(3);
        } else {
            linePart = parts.get(2);
        }

        List<Line> lines = parseLineList(linePart);
        if (lines.isEmpty()) {
            return null;
        }

        return new ImportedGroup(hueMin, hueMax, includeUnder, lines);
    }

    private static List<ImportedGroup> parsePutHueRangeCalls(String text) {
        ArrayList<ImportedGroup> out = new ArrayList<>();

        for (String call : extractCalls(text, "HSVBounds.putHueRange(")) {
            ImportedGroup group = parsePutHueRangeCall(call);
            if (group != null) {
                out.add(group);
            }
        }

        return out;
    }

    private static ImportedGroup parsePutHueRangeCall(String call) {
        int open = call.indexOf('(');
        int close = call.lastIndexOf(')');
        if (open < 0 || close < 0 || close <= open) {
            return null;
        }

        String args = call.substring(open + 1, close);
        List<String> parts = splitTopLevel(args, ',');

        if (parts.size() != 4 && parts.size() != 5) {
            return null;
        }

        Integer hueMin = tryParseInt(parts.get(1));
        Integer hueMax = tryParseInt(parts.get(2));
        if (hueMin == null || hueMax == null) {
            return null;
        }

        boolean includeUnder = true;
        String linePart;

        if (parts.size() == 5) {
            String boolStr = parts.get(3).trim();
            if (!"true".equals(boolStr) && !"false".equals(boolStr)) {
                return null;
            }
            includeUnder = Boolean.parseBoolean(boolStr);
            linePart = parts.get(4);
        } else {
            linePart = parts.get(3);
        }

        List<Line> lines = parseLineList(linePart);
        if (lines.isEmpty()) {
            return null;
        }

        return new ImportedGroup(hueMin, hueMax, includeUnder, lines);
    }

    private static List<ImportedGroup> parseLambdaMetadata(String text) {
        ArrayList<ImportedGroup> out = new ArrayList<>();

        int specStart = text.indexOf("HSVLINEEDITOR-LAMBDA-SPEC");
        if (specStart < 0) {
            return out;
        }

        int bodyStart = specStart + "HSVLINEEDITOR-LAMBDA-SPEC".length();
        int specEnd = text.indexOf("*/", bodyStart);
        if (specEnd < 0) {
            return out;
        }

        String body = text.substring(bodyStart, specEnd);
        String[] lines = body.split("\\R");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }

            String header = trimmed.substring(0, colon).trim();
            String payload = trimmed.substring(colon + 1).trim();

            String[] headerParts = header.split(",");
            if (headerParts.length != 3) {
                continue;
            }

            Integer hueMin = tryParseInt(headerParts[0]);
            Integer hueMax = tryParseInt(headerParts[1]);
            Boolean includeUnder = tryParseBoolean(headerParts[2]);

            if (hueMin == null || hueMax == null || includeUnder == null) {
                continue;
            }

            ArrayList<Line> segs = new ArrayList<>();
            if (!payload.isBlank()) {
                String[] segParts = payload.split(";");
                for (String seg : segParts) {
                    String[] nums = seg.trim().split(",");
                    if (nums.length != 4) {
                        segs.clear();
                        break;
                    }

                    Integer x1 = tryParseInt(nums[0]);
                    Integer y1 = tryParseInt(nums[1]);
                    Integer x2 = tryParseInt(nums[2]);
                    Integer y2 = tryParseInt(nums[3]);

                    if (x1 == null || y1 == null || x2 == null || y2 == null) {
                        segs.clear();
                        break;
                    }

                    segs.add(new Line(new Point(x1, y1), new Point(x2, y2)));
                }
            }

            if (!segs.isEmpty()) {
                out.add(new ImportedGroup(hueMin, hueMax, includeUnder, segs));
            }
        }

        return out;
    }

    private static List<ImportedGroup> parseLegacyLambda(String text) {
        ArrayList<ImportedGroup> out = new ArrayList<>();

        Matcher headerMatcher = LEGACY_LAMBDA_GROUP_HEADER.matcher(text);
        int searchFrom = 0;

        while (headerMatcher.find(searchFrom)) {
            Integer hueMin = tryParseInt(headerMatcher.group(1));
            Integer hueMax = tryParseInt(headerMatcher.group(2));
            if (hueMin == null || hueMax == null) {
                searchFrom = headerMatcher.end();
                continue;
            }

            int bodyStart = headerMatcher.end();
            int bodyEnd = findMatchingParen(text, bodyStart - 1);
            if (bodyEnd < 0) {
                break;
            }

            String body = text.substring(bodyStart, bodyEnd);
            ImportedGroup group = parseLegacyLambdaBody(hueMin, hueMax, body);
            if (group != null) {
                out.add(group);
            }

            searchFrom = bodyEnd + 1;
        }

        return out;
    }

    private static ImportedGroup parseLegacyLambdaBody(int hueMin, int hueMax, String body) {
        ArrayList<Line> lines = new ArrayList<>();
        Boolean includeUnder = null;

        Matcher clauseMatcher = LEGACY_LAMBDA_CLAUSE_HEADER.matcher(body);
        while (clauseMatcher.find()) {
            Integer x1 = tryParseInt(clauseMatcher.group(1));
            Integer x2 = tryParseInt(clauseMatcher.group(2));
            String op = clauseMatcher.group(3);

            if (x1 == null || x2 == null) {
                continue;
            }

            int exprStart = clauseMatcher.end();
            int exprEnd = findClauseEnd(body, exprStart);
            if (exprEnd < exprStart) {
                continue;
            }

            String expr = body.substring(exprStart, exprEnd).trim();
            Integer y1 = null;
            Integer y2 = null;

            Matcher exprMatcher = LEGACY_LAMBDA_EXPR.matcher(expr);
            if (exprMatcher.matches()) {
                Integer dy = tryParseInt(exprMatcher.group(1));
                Integer dx = tryParseInt(exprMatcher.group(2));
                Integer xBase = tryParseInt(exprMatcher.group(3));
                Integer baseY = tryParseInt(exprMatcher.group(4));

                if (dy != null && dx != null && xBase != null && baseY != null && xBase.equals(x1) && dx != 0) {
                    y1 = baseY;
                    y2 = baseY + dy;
                }
            } else {
                y1 = tryParseInt(expr);
                y2 = y1;
            }

            if (y1 == null || y2 == null) {
                continue;
            }

            boolean clauseInclude = "<=".equals(op);
            if (includeUnder == null) {
                includeUnder = clauseInclude;
            }

            lines.add(new Line(new Point(x1, y1), new Point(x2, y2)));
        }

        if (lines.isEmpty() || includeUnder == null) {
            return null;
        }

        return new ImportedGroup(hueMin, hueMax, includeUnder, lines);
    }

    private static int findClauseEnd(String text, int from) {
        int level = 0;

        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '(') {
                level++;
            } else if (c == ')') {
                if (level == 0) {
                    return i;
                }
                level--;
            }
        }

        return text.length();
    }

    private static List<String> extractCalls(String text, String token) {
        ArrayList<String> out = new ArrayList<>();
        int from = 0;

        while (true) {
            int idx = text.indexOf(token, from);
            if (idx < 0) {
                break;
            }

            int open = text.indexOf('(', idx);
            if (open < 0) {
                break;
            }

            int close = findMatchingParen(text, open);
            if (close < 0) {
                break;
            }

            out.add(text.substring(idx, close + 1));
            from = close + 1;
        }

        return out;
    }

    private static int findMatchingParen(String text, int openIndex) {
        int level = 0;
        boolean inString = false;
        char stringQuote = 0;

        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (c == stringQuote && text.charAt(i - 1) != '\\') {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                stringQuote = c;
                continue;
            }

            if (c == '(') {
                level++;
            } else if (c == ')') {
                level--;
                if (level == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static List<String> splitTopLevel(String text, char separator) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder curr = new StringBuilder();

        int paren = 0;
        int brace = 0;
        int bracket = 0;
        boolean inString = false;
        char quote = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                curr.append(c);
                if (c == quote && text.charAt(i - 1) != '\\') {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                curr.append(c);
                continue;
            }

            if (c == '(') paren++;
            else if (c == ')') paren--;
            else if (c == '{') brace++;
            else if (c == '}') brace--;
            else if (c == '[') bracket++;
            else if (c == ']') bracket--;

            if (c == separator && paren == 0 && brace == 0 && bracket == 0) {
                out.add(curr.toString().trim());
                curr.setLength(0);
            } else {
                curr.append(c);
            }
        }

        if (!curr.isEmpty()) {
            out.add(curr.toString().trim());
        }

        return out;
    }

    private static List<Line> parseLineList(String text) {
        ArrayList<Line> out = new ArrayList<>();
        Matcher matcher = LINE_PATTERN.matcher(text);

        while (matcher.find()) {
            Integer x1 = tryParseInt(matcher.group(1));
            Integer y1 = tryParseInt(matcher.group(2));
            Integer x2 = tryParseInt(matcher.group(3));
            Integer y2 = tryParseInt(matcher.group(4));

            if (x1 == null || y1 == null || x2 == null || y2 == null) {
                continue;
            }

            out.add(new Line(new Point(x1, y1), new Point(x2, y2)));
        }

        return out;
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Boolean tryParseBoolean(String s) {
        String t = s == null ? "" : s.trim();
        if ("true".equalsIgnoreCase(t)) {
            return true;
        }
        if ("false".equalsIgnoreCase(t)) {
            return false;
        }
        return null;
    }

    private static final class ImportedGroup {
        final int hueMin;
        final int hueMax;
        final boolean includeUnder;
        final List<Line> lines;

        private ImportedGroup(int hueMin, int hueMax, boolean includeUnder, List<Line> lines) {
            this.hueMin = hueMin;
            this.hueMax = hueMax;
            this.includeUnder = includeUnder;
            this.lines = lines;
        }
    }

    private static final class LineGroup {
        final int hueMin;
        int hueMax;
        final boolean includeUnder;
        final List<Line> lines;

        private LineGroup(int hueMin, int hueMax, boolean includeUnder, List<Line> lines) {
            this.hueMin = hueMin;
            this.hueMax = hueMax;
            this.includeUnder = includeUnder;
            this.lines = lines;
        }
    }

    private static final class LineSliceDefinition {
        final int hue;
        final boolean includeUnder;
        final List<Point> linePoints;

        private LineSliceDefinition(int hue, boolean includeUnder, List<Point> linePoints) {
            this.hue = MathEx.bound(hue, 0, 359);
            this.includeUnder = includeUnder;
            this.linePoints = deepCopyPoints(linePoints);
        }
    }

    private final class GradientPanel extends JPanel {
        private int hue;
        private boolean includeUnder = true;
        private boolean warpMode = false;

        private BufferedImage gradient;

        private final ArrayList<Point> drawn = new ArrayList<>();
        private final ArrayList<Point> lowerDrawn = new ArrayList<>();
        private final ArrayList<Point> anchors = new ArrayList<>();

        private boolean drawingStroke = false;
        private int draggedAnchor = -1;

        private GradientPanel() {
            setPreferredSize(new Dimension(HSV_EDITOR_SIZE, HSV_EDITOR_SIZE));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (warpMode) {
                        handleWarpPressed(e);
                    } else {
                        handleDrawPressed(e);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (warpMode) {
                        handleWarpDragged(e);
                    } else {
                        handleDrawDragged(e);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (warpMode) {
                        handleWarpReleased(e);
                    } else {
                        handleDrawReleased(e);
                    }
                }
            };

            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private void setHue(int hue) {
            this.hue = MathEx.bound(hue, 0, 359);
            this.gradient = null;
            repaint();
        }

        private void setIncludeUnder(boolean includeUnder) {
            this.includeUnder = includeUnder;
            repaint();
        }

        private void setWarpMode(boolean warpMode) {
            this.warpMode = warpMode;
            if (warpMode && anchors.isEmpty() && drawn.size() >= 2) {
                rebuildAnchorsFromCurrentLine();
            }
            repaint();
        }

        private boolean rebuildAnchorsFromCurrentLine() {
            if (drawn.size() < 2) {
                return false;
            }

            List<Point> rebuilt = buildWarpAnchorPoints(drawn);
            if (rebuilt.size() < 2) {
                return false;
            }

            anchors.clear();
            anchors.addAll(rebuilt);
            rebuildDrawnFromAnchors();
            repaint();
            return true;
        }

        private void setLine(List<Point> points) {
            drawn.clear();
            drawn.addAll(deepCopyPoints(points));
            anchors.clear();
            if (drawn.size() >= 2) {
                anchors.addAll(buildWarpAnchorPoints(drawn));
            }
            repaint();
        }

        private void setLowerLine(List<Point> points) {
            lowerDrawn.clear();
            lowerDrawn.addAll(deepCopyPoints(points));
            repaint();
        }

        private void clearLowerLine() {
            lowerDrawn.clear();
            repaint();
        }

        private void clearLine() {
            drawn.clear();
            lowerDrawn.clear();
            anchors.clear();
            repaint();
        }

        private List<Point> getLine() {
            return deepCopyPoints(drawn);
        }

        private Point clampPoint(Point p) {
            return new Point(
                    MathEx.bound(p.x, 0, HSV_EDITOR_SIZE - 1),
                    MathEx.bound(p.y, 0, HSV_EDITOR_SIZE - 1)
            );
        }

        private void handleDrawPressed(MouseEvent e) {
            drawingStroke = true;
            drawn.clear();
            anchors.clear();
            drawn.add(clampPoint(e.getPoint()));
            repaint();
        }

        private void handleDrawDragged(MouseEvent e) {
            if (!drawingStroke) {
                return;
            }

            Point p = clampPoint(e.getPoint());
            if (drawn.isEmpty() || !drawn.get(drawn.size() - 1).equals(p)) {
                drawn.add(p);
                repaint();
            }
        }

        private void handleDrawReleased(MouseEvent e) {
            if (!drawingStroke) {
                return;
            }

            drawingStroke = false;
            Point p = clampPoint(e.getPoint());
            if (drawn.isEmpty() || !drawn.get(drawn.size() - 1).equals(p)) {
                drawn.add(p);
            }

            anchors.clear();
            if (drawn.size() >= 2) {
                anchors.addAll(buildWarpAnchorPoints(drawn));
            }
            repaint();
        }

        private void handleWarpPressed(MouseEvent e) {
            Point p = clampPoint(e.getPoint());

            if (SwingUtilities.isRightMouseButton(e)) {
                int hit = findAnchorAt(p);
                if (hit > 0 && hit < anchors.size() - 1) {
                    anchors.remove(hit);
                    rebuildDrawnFromAnchors();
                    repaint();
                }
                return;
            }

            if (e.isShiftDown()) {
                addAnchorAt(p);
                repaint();
                return;
            }

            draggedAnchor = findAnchorAt(p);
            if (draggedAnchor < 0 && !anchors.isEmpty()) {
                int segIdx = findNearestAnchorSegment(p);
                if (segIdx >= 0) {
                    anchors.add(segIdx + 1, p);
                    draggedAnchor = segIdx + 1;
                    enforceAnchorOrder(draggedAnchor);
                    rebuildDrawnFromAnchors();
                }
            }
            repaint();
        }

        private void handleWarpDragged(MouseEvent e) {
            if (draggedAnchor < 0 || draggedAnchor >= anchors.size()) {
                return;
            }

            anchors.set(draggedAnchor, clampPoint(e.getPoint()));
            enforceAnchorOrder(draggedAnchor);
            rebuildDrawnFromAnchors();
            repaint();
        }

        private void handleWarpReleased(MouseEvent e) {
            if (draggedAnchor >= 0) {
                anchors.set(draggedAnchor, clampPoint(e.getPoint()));
                enforceAnchorOrder(draggedAnchor);
                rebuildDrawnFromAnchors();
                draggedAnchor = -1;
                repaint();
            }
        }

        private int findAnchorAt(Point p) {
            for (int i = 0; i < anchors.size(); i++) {
                Point a = anchors.get(i);
                if (Point.distance(p.x, p.y, a.x, a.y) <= ANCHOR_HIT_RADIUS) {
                    return i;
                }
            }
            return -1;
        }

        private void addAnchorAt(Point p) {
            if (anchors.size() < 2) {
                anchors.add(p);
                anchors.sort(Comparator.comparingInt(pt -> pt.x));
                rebuildDrawnFromAnchors();
                return;
            }

            int segIdx = findNearestAnchorSegment(p);
            if (segIdx < 0) {
                anchors.add(p);
                anchors.sort(Comparator.comparingInt(pt -> pt.x));
            } else {
                anchors.add(segIdx + 1, p);
            }

            enforceAllAnchorOrder();
            rebuildDrawnFromAnchors();
        }

        private int findNearestAnchorSegment(Point p) {
            if (anchors.size() < 2) {
                return -1;
            }

            double best = Double.MAX_VALUE;
            int bestIdx = -1;

            for (int i = 1; i < anchors.size(); i++) {
                Point a = anchors.get(i - 1);
                Point b = anchors.get(i);

                double d = distanceToSegment(p, a, b);
                if (d < best) {
                    best = d;
                    bestIdx = i - 1;
                }
            }

            return bestIdx;
        }

        private void enforceAnchorOrder(int idx) {
            if (idx < 0 || idx >= anchors.size()) {
                return;
            }

            Point p = anchors.get(idx);

            if (idx == 0) {
                p.x = 0;
            } else if (idx == anchors.size() - 1) {
                p.x = HSV_EDITOR_SIZE - 1;
            } else {
                int minX = anchors.get(idx - 1).x + MIN_ANCHOR_GAP;
                int maxX = anchors.get(idx + 1).x - MIN_ANCHOR_GAP;
                p.x = MathEx.bound(p.x, minX, maxX);
            }

            p.y = MathEx.bound(p.y, 0, HSV_EDITOR_SIZE - 1);
        }

        private void enforceAllAnchorOrder() {
            if (anchors.isEmpty()) {
                return;
            }

            anchors.sort(Comparator.comparingInt(pt -> pt.x));

            anchors.get(0).x = 0;
            anchors.get(anchors.size() - 1).x = HSV_EDITOR_SIZE - 1;

            for (int i = 1; i < anchors.size() - 1; i++) {
                Point prev = anchors.get(i - 1);
                Point curr = anchors.get(i);
                Point next = anchors.get(i + 1);

                int minX = prev.x + MIN_ANCHOR_GAP;
                int maxX = next.x - MIN_ANCHOR_GAP;
                curr.x = MathEx.bound(curr.x, minX, maxX);
                curr.y = MathEx.bound(curr.y, 0, HSV_EDITOR_SIZE - 1);
            }
        }

        private void rebuildDrawnFromAnchors() {
            if (anchors.size() < 2) {
                return;
            }

            ArrayList<Point> canonicalAnchors = new ArrayList<>();
            for (Point p : anchors) {
                canonicalAnchors.add(clampPoint(new Point(p)));
            }

            canonicalAnchors.sort(Comparator.comparingInt(pt -> pt.x));
            canonicalAnchors.get(0).x = 0;
            canonicalAnchors.get(canonicalAnchors.size() - 1).x = HSV_EDITOR_SIZE - 1;

            int[] yByX = rasterizeBoundary(canonicalAnchors, HSV_EDITOR_SIZE, HSV_EDITOR_SIZE);
            drawn.clear();

            for (int x = 0; x < yByX.length; x++) {
                drawn.add(new Point(x, yByX[x]));
            }
        }

        private void ensureGradient() {
            if (gradient != null) {
                return;
            }

            gradient = new BufferedImage(
                    HSV_EDITOR_SIZE,
                    HSV_EDITOR_SIZE,
                    BufferedImage.TYPE_INT_ARGB
            );

            for (int y = 0; y < HSV_EDITOR_SIZE; y++) {
                double v = ((HSV_EDITOR_SIZE - 1 - y) * HSV_LOGICAL_MAX) / (double) (HSV_EDITOR_SIZE - 1);

                for (int x = 0; x < HSV_EDITOR_SIZE; x++) {
                    double s = (x * HSV_LOGICAL_MAX) / (double) (HSV_EDITOR_SIZE - 1);
                    int[] rgb = FrameParser.HSVtoRGB(new double[]{hue, s, v});
                    gradient.setRGB(x, y, FastRGB.getRGBRaw(rgb));
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            ensureGradient();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.drawImage(gradient, 0, 0, null);

            if (drawn.size() >= 2) {
                if (lowerDrawn.size() >= 2) {
                    java.awt.Polygon band = buildBandPolygon(lowerDrawn, drawn);
                    g2.setColor(new Color(0, 120, 255, 70));
                    g2.fillPolygon(band);
                } else {
                    java.awt.Polygon fill = buildFillPolygon(drawn, includeUnder);
                    g2.setColor(includeUnder ? new Color(0, 255, 0, 70) : new Color(255, 0, 0, 70));
                    g2.fillPolygon(fill);
                }

                if (lowerDrawn.size() >= 2) {
                    List<Point> lowerCanonical = canonicalizeLine(lowerDrawn, HSV_EDITOR_SIZE, HSV_EDITOR_SIZE);
                    g2.setStroke(new BasicStroke(3f));
                    g2.setColor(new Color(20, 20, 20));
                    for (int i = 1; i < lowerCanonical.size(); i++) {
                        Point a = lowerCanonical.get(i - 1);
                        Point b = lowerCanonical.get(i);
                        g2.drawLine(a.x, a.y, b.x, b.y);
                    }
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(new Color(0, 220, 255));
                    for (int i = 1; i < lowerCanonical.size(); i++) {
                        Point a = lowerCanonical.get(i - 1);
                        Point b = lowerCanonical.get(i);
                        g2.drawLine(a.x, a.y, b.x, b.y);
                    }
                }

                List<Point> canonical = canonicalizeLine(drawn, HSV_EDITOR_SIZE, HSV_EDITOR_SIZE);

                g2.setStroke(new BasicStroke(3f));
                g2.setColor(Color.BLACK);
                for (int i = 1; i < canonical.size(); i++) {
                    Point a = canonical.get(i - 1);
                    Point b = canonical.get(i);
                    g2.drawLine(a.x, a.y, b.x, b.y);
                }

                g2.setStroke(new BasicStroke(2f));
                g2.setColor(Color.WHITE);
                for (int i = 1; i < canonical.size(); i++) {
                    Point a = canonical.get(i - 1);
                    Point b = canonical.get(i);
                    g2.drawLine(a.x, a.y, b.x, b.y);
                }
            }

            if (warpMode && !anchors.isEmpty()) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(40, 40, 40, 180));

                for (int i = 1; i < anchors.size(); i++) {
                    Point a = anchors.get(i - 1);
                    Point b = anchors.get(i);
                    g2.drawLine(a.x, a.y, b.x, b.y);
                }

                for (int i = 0; i < anchors.size(); i++) {
                    Point a = anchors.get(i);
                    boolean endpoint = i == 0 || i == anchors.size() - 1;

                    g2.setColor(endpoint ? new Color(30, 144, 255) : new Color(255, 215, 0));
                    g2.fillOval(a.x - 4, a.y - 4, 8, 8);
                    g2.setColor(Color.BLACK);
                    g2.drawOval(a.x - 4, a.y - 4, 8, 8);
                }
            }

            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(0, 0, HSV_EDITOR_SIZE - 1, HSV_EDITOR_SIZE - 1);
            g2.dispose();
        }
    }
}