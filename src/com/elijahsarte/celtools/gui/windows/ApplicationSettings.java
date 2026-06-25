package com.elijahsarte.celtools.gui.windows;

import com.elijahsarte.celtools.gui.Application;
import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionManager;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionWindow;
import com.elijahsarte.celtools.main.util.function.fnvals.BiTuple;
import com.elijahsarte.celtools.mainex.DebuggerEx;
import com.elijahsarte.celtools.mainex.sandbox.DebugDumpViewer;
import com.elijahsarte.celtools.mainex.sandbox.HSVLineEditor;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public class ApplicationSettings extends JFrame {

    /** Set to false to show the selections UI directly without tabs. */
    public static boolean TABBED_APPROACH_ENABLED = true;
    private static final String CUSTOM_INK_QUALIFIER_OPTION = "Custom...";
    private static final String SAVED_SELECTIONS_CHOOSER_KEY = "savedSelections";
    private static final String SAVED_SELECTIONS_FILE_EXTENSION = "ctss";
    private static final byte[] SAVED_SELECTIONS_MAGIC = new byte[] { 'C', 'T', 'S', 'S' };
    private static final int SAVED_SELECTIONS_FILE_VERSION = 1;

    private static final DateTimeFormatter SETTINGS_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SavedSelectionsTableModel tableModel = new SavedSelectionsTableModel();
    private final JTable table = new JTable(tableModel);

    private final JTextField imageHashField = new JTextField(16);
    private final JTextField frameNameField = new JTextField(20);
    private final JComboBox<FreeformSelectionWindow.SelectionMode> selectionModeCombo =
            new JComboBox<>(FreeformSelectionWindow.SelectionMode.values());
    private final JTextArea argArea = new JTextArea(8, 60);
    private final JLabel formatHintLabel = new JLabel(" ");

    private String currentStorageKey = null;
    private JCheckBox previewShapeDataCheckBox;

    public ApplicationSettings() {
        super("Settings");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(true);

        JPanel selectionsPanel = buildSelectionsPanel();
        if (TABBED_APPROACH_ENABLED) {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Main", buildMainPanel());
            tabs.addTab("Selections", selectionsPanel);
            tabs.setSelectedIndex(0);
            setContentPane(tabs);
        } else {
            setContentPane(selectionsPanel);
        }

        selectionModeCombo.addActionListener(e -> refreshFormatHint());
        refreshFormatHint();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                if (!TABBED_APPROACH_ENABLED) {
                    table.requestFocusInWindow();
                }
            }
        });

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(
                Math.min(1100, Math.max(920, (int) (screen.width * 0.8))),
                Math.min(800, Math.max(680, (int) (screen.height * 0.8)))
        );
        setLocationRelativeTo(null);
    }

    private JPanel buildSelectionsPanel() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        content.add(buildTopPanel(), BorderLayout.NORTH);
        content.add(buildCenterPanel(), BorderLayout.CENTER);
        content.add(buildBottomButtons(), BorderLayout.SOUTH);

        JScrollPane outerScroll = new JScrollPane(
                content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        outerScroll.setWheelScrollingEnabled(true);
        outerScroll.getVerticalScrollBar().setUnitIncrement(24);
        outerScroll.getHorizontalScrollBar().setUnitIncrement(24);
        outerScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(outerScroll, BorderLayout.CENTER);
        return wrapper;
    }

    public static void open() {
        SwingUtilities.invokeLater(() -> {
            ApplicationSettings window = new ApplicationSettings();
            window.setVisible(true);
        });
    }

    private JPanel buildMainPanel() {
        int[] savedMemoryGb = {Application.getConfiguredMemoryGigabytes()};
        int safeMaximumGb = Application.getSafeMemoryLimitGigabytes();
        int sliderValue = Math.max(2, Math.min(savedMemoryGb[0], safeMaximumGb));

        JSlider memorySlider = new JSlider(2, safeMaximumGb, sliderValue);
        memorySlider.setMajorTickSpacing(Math.max(1, (safeMaximumGb - 2) / 5));
        memorySlider.setMinorTickSpacing(1);
        memorySlider.setPaintTicks(true);
        memorySlider.setPaintLabels(true);
        memorySlider.setSnapToTicks(true);

        JLabel valueLabel = new JLabel(sliderValue + " GB", SwingConstants.CENTER);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 16f));

        JPanel memoryControl = new JPanel(new BorderLayout(8, 12));
        memoryControl.setBorder(BorderFactory.createTitledBorder("Memory Allocation"));
        memoryControl.add(new JLabel(
                "Maximum Java memory. The safe limit for this computer is " + safeMaximumGb + " GB."
        ), BorderLayout.NORTH);
        memoryControl.add(memorySlider, BorderLayout.CENTER);
        memoryControl.add(valueLabel, BorderLayout.SOUTH);

        JTextField maximumOutputThreadsField = new JTextField(6);
        maximumOutputThreadsField.setColumns(6);
        maximumOutputThreadsField.setText(Integer.toString(Main.getMaximumOutputThreads()));
        ((AbstractDocument) maximumOutputThreadsField.getDocument()).setDocumentFilter(new DocumentFilter() {
            private boolean isValid(String text) {
                return text.isEmpty() || text.chars().allMatch(Character::isDigit);
            }

            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                replace(fb, offset, 0, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String insertedText = text == null ? "" : text;
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String replacement = current.substring(0, offset)
                        + insertedText
                        + current.substring(offset + length);
                if (isValid(replacement)) {
                    fb.replace(offset, length, insertedText, attrs);
                }
            }
        });

        JPanel outputThreadControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        outputThreadControl.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        outputThreadControl.add(new JLabel("Maximum output threads:"));
        outputThreadControl.add(maximumOutputThreadsField);

        JPanel performanceSection = new JPanel();
        performanceSection.setLayout(new BoxLayout(performanceSection, BoxLayout.Y_AXIS));
        performanceSection.setBorder(BorderFactory.createTitledBorder("Performance"));
        memoryControl.setAlignmentX(Component.LEFT_ALIGNMENT);
        outputThreadControl.setAlignmentX(Component.LEFT_ALIGNMENT);
        performanceSection.add(memoryControl);
        performanceSection.add(outputThreadControl);

        JPanel celSettingsSection = buildCelSettingsPanel();
        JPanel programSettingsSection = buildProgramSettingsPanel();
        JPanel extraUtilitiesSection = buildExtraUtilitiesPanel();
        celSettingsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        performanceSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        programSettingsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        extraUtilitiesSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel mainControls = new JPanel();
        mainControls.setLayout(new BoxLayout(mainControls, BoxLayout.Y_AXIS));
        mainControls.add(celSettingsSection);
        mainControls.add(Box.createVerticalStrut(10));
        mainControls.add(performanceSection);
        mainControls.add(Box.createVerticalStrut(10));
        mainControls.add(programSettingsSection);
        mainControls.add(Box.createVerticalStrut(10));
        mainControls.add(extraUtilitiesSection);

        JButton saveButton = new JButton("Save");
        JButton saveAndRestartButton = new JButton("Save and Restart");
        saveAndRestartButton.setVisible(false);

        Supplier<Boolean> saveMainSettings = () -> {
            String outputThreadsText = maximumOutputThreadsField.getText().trim();
            if (outputThreadsText.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "Maximum output threads must be a positive number.",
                        "Invalid Setting",
                        JOptionPane.WARNING_MESSAGE
                );
                maximumOutputThreadsField.requestFocusInWindow();
                return false;
            }

            int maximumOutputThreads;
            try {
                maximumOutputThreads = Integer.parseInt(outputThreadsText);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Maximum output threads is too large.",
                        "Invalid Setting",
                        JOptionPane.WARNING_MESSAGE
                );
                maximumOutputThreadsField.requestFocusInWindow();
                return false;
            }
            if (maximumOutputThreads < 1) {
                JOptionPane.showMessageDialog(
                        this,
                        "Maximum output threads must be at least 1.",
                        "Invalid Setting",
                        JOptionPane.WARNING_MESSAGE
                );
                maximumOutputThreadsField.requestFocusInWindow();
                return false;
            }

            Application.setConfiguredMemoryGigabytes(memorySlider.getValue());
            Main.setMaximumOutputThreads(maximumOutputThreads);
            if (previewShapeDataCheckBox != null) {
                Main.setPreviewShapeData(previewShapeDataCheckBox.isSelected());
            }
            savedMemoryGb[0] = Application.getConfiguredMemoryGigabytes();
            saveAndRestartButton.setVisible(memorySlider.getValue() != savedMemoryGb[0]);
            return true;
        };

        saveButton.addActionListener(e -> saveMainSettings.get());
        saveAndRestartButton.addActionListener(e -> {
            int selectedMemoryGb = memorySlider.getValue();
            if (!saveMainSettings.get()) return;
            saveAndRestartButton.setEnabled(false);
            Application.restartWithMemoryAllocation(selectedMemoryGb);
        });

        memorySlider.addChangeListener(e -> {
            int selectedMemoryGb = memorySlider.getValue();
            valueLabel.setText(selectedMemoryGb + " GB");
            saveAndRestartButton.setVisible(selectedMemoryGb != savedMemoryGb[0]);
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(saveButton);
        buttonPanel.add(saveAndRestartButton);

        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        main.add(mainControls, BorderLayout.NORTH);
        main.add(buttonPanel, BorderLayout.SOUTH);
        return main;
    }


    private JPanel buildProgramSettingsPanel() {
        previewShapeDataCheckBox = new JCheckBox("Preview shape/point data", Main.previewShapeData());

        JButton openFreeformSelectionManagerButton = new JButton("Open FreeformSelectionManager");
        openFreeformSelectionManagerButton.addActionListener(e -> openStandaloneFreeformSelectionManager());

        JButton createDebugDumpButton = new JButton("Create Debug Dump");
        createDebugDumpButton.addActionListener(e -> Main.createDebugDump(this));

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionRow.add(openFreeformSelectionManagerButton);
        actionRow.add(createDebugDumpButton);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewShapeDataCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(actionRow);
        controls.add(Box.createVerticalStrut(8));
        controls.add(previewShapeDataCheckBox);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Program settings"));
        panel.add(controls, BorderLayout.WEST);
        return panel;
    }

    private JPanel buildExtraUtilitiesPanel() {
        JButton openDebugDumpViewerButton = new JButton("Open Debug Dump Viewer");
        openDebugDumpViewerButton.addActionListener(e -> DebugDumpViewer.openWithChooser(this));

        JButton openHSVLineEditorButton = new JButton("Open HSV Line Editor");
        openHSVLineEditorButton.addActionListener(e -> openHSVLineEditorUtility());

        JButton openShapeContourTestButton = new JButton("Open Shape Contour Test");
        openShapeContourTestButton.addActionListener(e -> openShapeContourTestUtility());

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionRow.add(openDebugDumpViewerButton);
        actionRow.add(openHSVLineEditorButton);
        actionRow.add(openShapeContourTestButton);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Extra Utilities"));
        panel.add(actionRow, BorderLayout.WEST);
        return panel;
    }

    private void openHSVLineEditorUtility() {
        if (openUtilityClass(HSVLineEditor.class)) return;
        JOptionPane.showMessageDialog(
                this,
                "Unable to open HSV Line Editor. No open(), show(), main(String[]), or no-arg window constructor was available.",
                "Open HSV Line Editor",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private void openShapeContourTestUtility() {
        String[] candidates = {
                "com.elijahsarte.celtools.mainex.sandbox.ShapeContourTest",
                "com.elijahsarte.celtools.mainex.sandbox.ShapeContourTester",
                "com.elijahsarte.celtools.mainex.sandbox.ShapeContourTestWindow",
                "com.elijahsarte.celtools.mainex.ShapeContourTest",
                "com.elijahsarte.celtools.mainex.ShapeContourTester",
                "com.elijahsarte.celtools.mainex.ShapeContourTestWindow"
        };

        for (String className : candidates) {
            try {
                Class<?> cls = Class.forName(className);
                if (openUtilityClass(cls)) return;
            } catch (ClassNotFoundException ignored) {
            }
        }

        JOptionPane.showMessageDialog(
                this,
                "Unable to find/open a Shape Contour Test utility class. Tried common ShapeContourTest/ShapeContourTester class names.",
                "Open Shape Contour Test",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private boolean openUtilityClass(Class<?> cls) {
        if (tryStaticNoArgUtilityMethod(cls, "open")) return true;
        if (tryStaticNoArgUtilityMethod(cls, "show")) return true;
        if (tryStaticMainMethod(cls)) return true;
        return tryNoArgWindowConstructor(cls);
    }

    private boolean tryStaticNoArgUtilityMethod(Class<?> cls, String methodName) {
        try {
            Method method = cls.getDeclaredMethod(methodName);
            if (!Modifier.isStatic(method.getModifiers())) return false;
            method.setAccessible(true);
            Object result = method.invoke(null);
            showReturnedWindow(result);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "Unable to invoke " + cls.getName() + "." + methodName + "():\n" + t.getMessage(), "Open Utility", JOptionPane.ERROR_MESSAGE);
            return true;
        }
    }

    private boolean tryStaticMainMethod(Class<?> cls) {
        try {
            Method method = cls.getDeclaredMethod("main", String[].class);
            if (!Modifier.isStatic(method.getModifiers())) return false;
            method.setAccessible(true);
            method.invoke(null, (Object) new String[0]);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "Unable to invoke " + cls.getName() + ".main(String[]):\n" + t.getMessage(), "Open Utility", JOptionPane.ERROR_MESSAGE);
            return true;
        }
    }

    private boolean tryNoArgWindowConstructor(Class<?> cls) {
        try {
            Constructor<?> constructor = cls.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object result = constructor.newInstance();
            showReturnedWindow(result);
            return result instanceof Window || result instanceof JComponent;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "Unable to construct " + cls.getName() + ":\n" + t.getMessage(), "Open Utility", JOptionPane.ERROR_MESSAGE);
            return true;
        }
    }

    private void showReturnedWindow(Object result) {
        if (result instanceof Window window) {
            window.setLocationRelativeTo(this);
            window.setVisible(true);
        } else if (result instanceof JComponent component) {
            JFrame frame = new JFrame(component.getClass().getSimpleName());
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(component);
            frame.pack();
            frame.setLocationRelativeTo(this);
            frame.setVisible(true);
        }
    }

    private void openStandaloneFreeformSelectionManager() {
        JFileChooser imageChooser = Main.chooserFor("standaloneFreeformSelectionManagerImage");
        imageChooser.setDialogTitle("Choose Image for FreeformSelectionManager");
        imageChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (imageChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File imageFile = imageChooser.getSelectedFile();
        Main.rememberChooserDirectory("standaloneFreeformSelectionManagerImage", imageFile);

        try {
            var image = Main.handlerFromFile(imageFile);
            FreeformSelectionManager.openStandalone(
                    image.getImage(),
                    "FreeformSelectionManager - " + imageFile.getName()
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Unable to open FreeformSelectionManager:\n" + ex.getMessage(),
                    "Open Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private JPanel buildCelSettingsPanel() {
        JComboBox<String> inkQualifierCombo = new JComboBox<>();
        String[] lastCommittedInkQualifier = {Main.getSelectedInkQualifierName()};
        refreshInkQualifierCombo(inkQualifierCombo, lastCommittedInkQualifier[0]);

        JButton importButton = new JButton("Import...");
        JButton buildButton = new JButton("Build...");
        JButton editButton = new JButton("Edit...");
        JButton testButton = new JButton("Test...");

        importButton.addActionListener(e -> importInkQualifier(inkQualifierCombo, lastCommittedInkQualifier));
        buildButton.addActionListener(e -> buildInkQualifier(inkQualifierCombo, lastCommittedInkQualifier, buildButton));
        editButton.addActionListener(e -> editInkQualifier(inkQualifierCombo, lastCommittedInkQualifier));
        testButton.addActionListener(e -> testInkQualifier(inkQualifierCombo, lastCommittedInkQualifier, testButton));

        inkQualifierCombo.addActionListener(e -> {
            if (Boolean.TRUE.equals(inkQualifierCombo.getClientProperty("refreshing"))) return;

            String selected = (String) inkQualifierCombo.getSelectedItem();
            if (selected == null) return;

            if (CUSTOM_INK_QUALIFIER_OPTION.equals(selected)) {
                Object[] choices = {"Import...", "Build...", "Cancel"};
                int choice = JOptionPane.showOptionDialog(
                        this,
                        "Create or load a custom Cel Tools ink qualifier.",
                        "Custom Ink Qualifier",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        choices,
                        choices[0]
                );
                if (choice == 0) {
                    importInkQualifier(inkQualifierCombo, lastCommittedInkQualifier);
                } else if (choice == 1) {
                    buildInkQualifier(inkQualifierCombo, lastCommittedInkQualifier, buildButton);
                } else {
                    refreshInkQualifierCombo(inkQualifierCombo, lastCommittedInkQualifier[0]);
                }
                return;
            }

            try {
                Main.setCurrentInkQualifierByName(selected);
                lastCommittedInkQualifier[0] = selected;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Unable to load ink qualifier:\n" + ex.getMessage(),
                        "Ink Qualifier Failed",
                        JOptionPane.ERROR_MESSAGE
                );
                refreshInkQualifierCombo(inkQualifierCombo, Main.DEFAULT_INK_QUALIFIER_NAME);
                lastCommittedInkQualifier[0] = Main.DEFAULT_INK_QUALIFIER_NAME;
            }
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.add(new JLabel("Ink qualifier:"));
        row.add(inkQualifierCombo);
        row.add(importButton);
        row.add(buildButton);
        row.add(editButton);
        row.add(testButton);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Cel Settings"));
        panel.add(row, BorderLayout.WEST);
        return panel;
    }

    private void refreshInkQualifierCombo(JComboBox<String> combo, String selectedName) {
        combo.putClientProperty("refreshing", Boolean.TRUE);
        combo.removeAllItems();
        List<String> names = Main.getAvailableInkQualifierNames();
        for (String name : names) {
            combo.addItem(name);
        }
        combo.addItem(CUSTOM_INK_QUALIFIER_OPTION);

        String safeSelectedName = names.contains(selectedName) ? selectedName : Main.DEFAULT_INK_QUALIFIER_NAME;
        combo.setSelectedItem(safeSelectedName);
        combo.putClientProperty("refreshing", Boolean.FALSE);
    }

    private void importInkQualifier(JComboBox<String> combo, String[] lastCommittedInkQualifier) {
        JFileChooser chooser = Main.chooserFor("inkQualifier");
        chooser.setDialogTitle("Import CTINKQUAL");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter("Cel Tools Ink Qualifier (*.ctinkqual)", Main.INK_QUALIFIER_FILE_EXTENSION));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
            return;
        }

        File file = chooser.getSelectedFile();
        Main.rememberChooserDirectory("inkQualifier", file);
        try {
            String qualifierName = Main.importInkQualifier(file);
            Main.setCurrentInkQualifierByName(qualifierName);
            lastCommittedInkQualifier[0] = qualifierName;
            refreshInkQualifierCombo(combo, qualifierName);
            JOptionPane.showMessageDialog(
                    this,
                    "Imported ink qualifier: " + qualifierName,
                    "Ink Qualifier Imported",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Unable to import ink qualifier:\n" + ex.getMessage(),
                    "Import Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
        }
    }

    private void buildInkQualifier(
            JComboBox<String> combo,
            String[] lastCommittedInkQualifier,
            JButton buildButton
    ) {
        JFileChooser imageChooser = Main.chooserFor("inkQualifierSourceImage");
        imageChooser.setDialogTitle("Choose Inked Drawing Image");
        imageChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (imageChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
            return;
        }
        File imageFile = imageChooser.getSelectedFile();
        Main.rememberChooserDirectory("inkQualifierSourceImage", imageFile);

        JFileChooser saveChooser = Main.chooserFor("inkQualifier");
        saveChooser.setDialogTitle("Save CTINKQUAL");
        saveChooser.setFileFilter(new FileNameExtensionFilter("Cel Tools Ink Qualifier (*.ctinkqual)", Main.INK_QUALIFIER_FILE_EXTENSION));
        saveChooser.setSelectedFile(new File(stripExtension(imageFile.getName()) + "." + Main.INK_QUALIFIER_FILE_EXTENSION));
        if (saveChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
            return;
        }
        File outputFile = ensureExtension(saveChooser.getSelectedFile(), Main.INK_QUALIFIER_FILE_EXTENSION);
        Main.rememberChooserDirectory("inkQualifier", outputFile);

        buildButton.setEnabled(false);
        combo.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        CompletableFuture.supplyAsync(() -> {
            try {
                return Main.buildAndSaveInkQualifier(imageFile, outputFile);
            } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
            }
        }).whenComplete((qualifierName, error) -> SwingUtilities.invokeLater(() -> {
            buildButton.setEnabled(true);
            combo.setEnabled(true);
            setCursor(Cursor.getDefaultCursor());

            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                        ? error.getCause()
                        : error;
                JOptionPane.showMessageDialog(
                        this,
                        "Unable to build ink qualifier:\n" + cause.getMessage(),
                        "Build Failed",
                        JOptionPane.ERROR_MESSAGE
                );
                refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
                return;
            }

            try {
                Main.setCurrentInkQualifierByName(qualifierName);
                lastCommittedInkQualifier[0] = qualifierName;
                refreshInkQualifierCombo(combo, qualifierName);
                JOptionPane.showMessageDialog(
                        this,
                        "Built ink qualifier: " + qualifierName,
                        "Ink Qualifier Built",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Built the file, but could not select it:\n" + ex.getMessage(),
                        "Selection Failed",
                        JOptionPane.ERROR_MESSAGE
                );
                refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
            }
        }));
    }


    private void editInkQualifier(
            JComboBox<String> combo,
            String[] lastCommittedInkQualifier
    ) {
        String selected = (String) combo.getSelectedItem();
        if (selected == null || CUSTOM_INK_QUALIFIER_OPTION.equals(selected) || Main.DEFAULT_INK_QUALIFIER_NAME.equals(selected)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select a saved custom ink qualifier before editing.",
                    "No Custom Ink Qualifier Selected",
                    JOptionPane.WARNING_MESSAGE
            );
            refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
            return;
        }

        try {
            Main.setCurrentInkQualifierByName(selected);
            lastCommittedInkQualifier[0] = selected;
            File qualifierFile = Main.getCurrentInkQualifierFile();
            if (qualifierFile == null || !qualifierFile.isFile()) {
                throw new IOException("Could not locate cached qualifier file for " + selected);
            }
            HSVLineEditor.openCtInkQualifier(qualifierFile);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Unable to edit ink qualifier:\n" + ex.getMessage(),
                    "Edit Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
        }
    }

    private void testInkQualifier(
            JComboBox<String> combo,
            String[] lastCommittedInkQualifier,
            JButton testButton
    ) {
        String selected = (String) combo.getSelectedItem();
        if (selected == null || CUSTOM_INK_QUALIFIER_OPTION.equals(selected)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select a real ink qualifier before testing.",
                    "No Ink Qualifier Selected",
                    JOptionPane.WARNING_MESSAGE
            );
            refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
            return;
        }

        try {
            Main.setCurrentInkQualifierByName(selected);
            lastCommittedInkQualifier[0] = selected;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Unable to load selected ink qualifier:\n" + ex.getMessage(),
                    "Ink Qualifier Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            refreshInkQualifierCombo(combo, lastCommittedInkQualifier[0]);
            return;
        }

        JFileChooser imageChooser = Main.chooserFor("inkQualifierTestImage");
        imageChooser.setDialogTitle("Choose Image to Test Ink Qualifier");
        imageChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (imageChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File imageFile = imageChooser.getSelectedFile();
        Main.rememberChooserDirectory("inkQualifierTestImage", imageFile);

        testButton.setEnabled(false);
        combo.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        CompletableFuture.supplyAsync(() -> {
            try {
                var image = Main.handlerFromFile(imageFile);
                return new BiTuple<>(image, Main.filterPixelsToPtCollection(image, Main.inkQual));
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        }).whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            testButton.setEnabled(true);
            combo.setEnabled(true);
            setCursor(Cursor.getDefaultCursor());

            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause()
                        : error;
                JOptionPane.showMessageDialog(
                        this,
                        "Unable to test ink qualifier:\n" + cause.getMessage(),
                        "Test Failed",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            DebuggerEx.visN(
                    "Ink Qualifier Test - " + selected,
                    result.first().getImage(),
                    result.second()
            );
        }));
    }

    private static File ensureExtension(File file, String extension) {
        String suffix = "." + extension.toLowerCase();
        return file.getName().toLowerCase().endsWith(suffix)
                ? file
                : new File(file.getAbsolutePath() + suffix);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename : filename.substring(0, dot);
    }

    private JPanel buildTopPanel() {
        JComboBox<FreeformSelectionManager.AutoRestoreMode> restoreModeCombo =
                new JComboBox<>(FreeformSelectionManager.AutoRestoreMode.values());
        restoreModeCombo.setSelectedItem(FreeformSelectionManager.getAutoRestoreMode());

        JButton saveRestoreBehaviorButton = new JButton("Save Restore Behavior");
        saveRestoreBehaviorButton.addActionListener(e -> {
            FreeformSelectionManager.setAutoRestoreMode(
                    (FreeformSelectionManager.AutoRestoreMode) restoreModeCombo.getSelectedItem()
            );
            JOptionPane.showMessageDialog(
                    this,
                    "Restore behavior updated.",
                    "Saved",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(new JLabel("Saved selection restore behavior:"));
        left.add(restoreModeCombo);
        left.add(saveRestoreBehaviorButton);

        JPanel top = new JPanel(new BorderLayout());
        top.add(left, BorderLayout.WEST);

        return top;
    }

    private JPanel buildCenterPanel() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(900, 280));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedRowIntoEditor();
            }
        });

        JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Refresh");
        JButton loadButton = new JButton("Load Selected");
        JButton removeSelectedButton = new JButton("Remove Selected");
        JButton exportSavedSelectionsButton = new JButton("Export Saved Selections");
        JButton importSavedSelectionsButton = new JButton("Import Saved Selections");
        JButton clearAllButton = new JButton("Clear All Saved Selections");

        refreshButton.addActionListener(e -> {
            tableModel.refresh();
            clearEditor();
        });

        loadButton.addActionListener(e -> loadSelectedRowIntoEditor());
        removeSelectedButton.addActionListener(e -> removeSelected());
        exportSavedSelectionsButton.addActionListener(e -> exportSavedSelections());
        importSavedSelectionsButton.addActionListener(e -> importSavedSelections());

        clearAllButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Remove all saved selections?",
                    "Confirm Clear All",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) return;

            FreeformSelectionManager.clearStoredSelections();
            tableModel.refresh();
            clearEditor();
        });

        tableButtons.add(refreshButton);
        tableButtons.add(loadButton);
        tableButtons.add(removeSelectedButton);
        tableButtons.add(exportSavedSelectionsButton);
        tableButtons.add(importSavedSelectionsButton);
        tableButtons.add(clearAllButton);

        argArea.setLineWrap(true);
        argArea.setWrapStyleWord(true);

        JPanel editorForm = new JPanel(new GridBagLayout());
        editorForm.setBorder(BorderFactory.createTitledBorder("Edit Saved Selection"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0;
        editorForm.add(new JLabel("Image hash:"), gc);

        gc.gridx = 1;
        gc.weightx = 1;
        editorForm.add(imageHashField, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        gc.weightx = 0;
        editorForm.add(new JLabel("Frame name:"), gc);

        gc.gridx = 1;
        gc.weightx = 1;
        editorForm.add(frameNameField, gc);

        gc.gridx = 0;
        gc.gridy = 2;
        gc.weightx = 0;
        editorForm.add(new JLabel("Selection mode:"), gc);

        gc.gridx = 1;
        gc.weightx = 1;
        editorForm.add(selectionModeCombo, gc);

        gc.gridx = 0;
        gc.gridy = 3;
        gc.weightx = 0;
        gc.weighty = 0;
        gc.anchor = GridBagConstraints.NORTHWEST;
        editorForm.add(new JLabel("Selection arg:"), gc);

        gc.gridx = 1;
        gc.weightx = 1;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.BOTH;
        editorForm.add(new JScrollPane(argArea), gc);

        gc.gridx = 1;
        gc.gridy = 4;
        gc.weightx = 1;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        editorForm.add(formatHintLabel, gc);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(tableScroll, BorderLayout.NORTH);
        center.add(tableButtons, BorderLayout.CENTER);
        center.add(editorForm, BorderLayout.SOUTH);

        return center;
    }

    private JPanel buildBottomButtons() {
        JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton saveChangesButton = new JButton("Save Selection Changes");
        JButton closeButton = new JButton("Close");

        saveChangesButton.addActionListener(e -> saveSelectionChanges());
        closeButton.addActionListener(e -> dispose());

        bottomButtons.add(saveChangesButton);
        bottomButtons.add(closeButton);

        return bottomButtons;
    }

    private void refreshFormatHint() {
        formatHintLabel.setText(
                FreeformSelectionManager.getSelectionArgumentFormatHint(
                        (FreeformSelectionWindow.SelectionMode) selectionModeCombo.getSelectedItem()
                )
        );
    }

    private void clearEditor() {
        currentStorageKey = null;
        imageHashField.setText("");
        frameNameField.setText("");
        selectionModeCombo.setSelectedItem(FreeformSelectionWindow.SelectionMode.CROP_BOX);
        argArea.setText("");
        refreshFormatHint();
    }

    private void loadSelectedRowIntoEditor() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            clearEditor();
            return;
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        FreeformSelectionManager.SavedSelectionInfo info = tableModel.getRow(modelRow);

        FreeformSelectionManager.StoredSelectionEditorData editorData =
                FreeformSelectionManager.getStoredSelectionEditorData(info.storageKey());

        if (editorData == null) {
            tableModel.refresh();
            clearEditor();
            return;
        }

        currentStorageKey = editorData.storageKey();
        imageHashField.setText(Integer.toUnsignedString(editorData.imageHash()));
        frameNameField.setText(editorData.frameName());
        selectionModeCombo.setSelectedItem(editorData.selectionMode());
        argArea.setText(editorData.argText());
        formatHintLabel.setText(
                FreeformSelectionManager.getSelectionArgumentFormatHint(editorData.selectionMode())
        );
    }


    private void exportSavedSelections() {
        tableModel.refresh();

        List<SavedSelectionExportRecord> records = new ArrayList<>();
        for (FreeformSelectionManager.SavedSelectionInfo info : tableModel.rows) {
            FreeformSelectionManager.StoredSelectionEditorData editorData =
                    FreeformSelectionManager.getStoredSelectionEditorData(info.storageKey());
            if (editorData == null) continue;

            records.add(new SavedSelectionExportRecord(
                    editorData.storageKey(),
                    editorData.imageHash(),
                    editorData.frameName(),
                    editorData.selectionMode(),
                    editorData.argText()
            ));
        }

        if (records.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "There are no saved selections to export.",
                    "Nothing to Export",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        JFileChooser chooser = Main.chooserFor(SAVED_SELECTIONS_CHOOSER_KEY);
        chooser.setDialogTitle("Export Saved Selections");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Cel Tools Saved Selections (*." + SAVED_SELECTIONS_FILE_EXTENSION + ")",
                SAVED_SELECTIONS_FILE_EXTENSION
        ));
        chooser.setSelectedFile(new File("celtools-saved-selections." + SAVED_SELECTIONS_FILE_EXTENSION));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File outputFile = ensureExtension(chooser.getSelectedFile(), SAVED_SELECTIONS_FILE_EXTENSION);
        Main.rememberChooserDirectory(SAVED_SELECTIONS_CHOOSER_KEY, outputFile);

        if (outputFile.exists()) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Replace existing file?\n" + outputFile.getAbsolutePath(),
                    "Confirm Export",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        try {
            writeSavedSelectionsFile(outputFile, records);
            JOptionPane.showMessageDialog(
                    this,
                    "Exported " + records.size() + " saved selection" + (records.size() == 1 ? "." : "s."),
                    "Saved Selections Exported",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Unable to export saved selections:\n" + ex.getMessage(),
                    "Export Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void importSavedSelections() {
        JFileChooser chooser = Main.chooserFor(SAVED_SELECTIONS_CHOOSER_KEY);
        chooser.setDialogTitle("Import Saved Selections");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Cel Tools Saved Selections (*." + SAVED_SELECTIONS_FILE_EXTENSION + ")",
                SAVED_SELECTIONS_FILE_EXTENSION
        ));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File inputFile = chooser.getSelectedFile();
        Main.rememberChooserDirectory(SAVED_SELECTIONS_CHOOSER_KEY, inputFile);

        List<SavedSelectionExportRecord> records;
        try {
            records = readSavedSelectionsFile(inputFile);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Unable to import saved selections:\n" + ex.getMessage(),
                    "Import Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        if (records.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "The selected CTSS file does not contain any saved selections.",
                    "Nothing to Import",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Import " + records.size() + " saved selection" + (records.size() == 1 ? "?" : "s?")
                        + "\nExisting entries with matching storage keys will be updated.",
                "Confirm Import",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        int imported = 0;
        StringBuilder failed = new StringBuilder();
        for (SavedSelectionExportRecord record : records) {
            try {
                FreeformSelectionManager.upsertStoredSelection(
                        record.storageKey(),
                        record.imageHash(),
                        record.frameName(),
                        record.selectionMode(),
                        record.argText()
                );
                imported++;
            } catch (Exception ex) {
                if (!failed.isEmpty()) failed.append('\n');
                failed.append(record.storageKey()).append(": ").append(ex.getMessage());
            }
        }

        tableModel.refresh();
        clearEditor();

        if (failed.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Imported " + imported + " saved selection" + (imported == 1 ? "." : "s."),
                    "Saved Selections Imported",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else {
            JOptionPane.showMessageDialog(
                    this,
                    "Imported " + imported + " saved selection" + (imported == 1 ? "" : "s")
                            + ", but some entries failed:\n" + failed,
                    "Import Partially Failed",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private static void writeSavedSelectionsFile(
            File file,
            List<SavedSelectionExportRecord> records
    ) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.write(SAVED_SELECTIONS_MAGIC);
            out.writeInt(SAVED_SELECTIONS_FILE_VERSION);
            out.writeInt(records.size());
            for (SavedSelectionExportRecord record : records) {
                writeUtf8(out, record.storageKey());
                out.writeInt(record.imageHash());
                writeUtf8(out, record.frameName());
                writeUtf8(out, record.selectionMode().name());
                writeUtf8(out, record.argText());
            }
        }
    }

    private static List<SavedSelectionExportRecord> readSavedSelectionsFile(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            for (byte expected : SAVED_SELECTIONS_MAGIC) {
                int actual = in.read();
                if (actual != (expected & 0xff)) {
                    throw new IOException("Invalid CTSS file header.");
                }
            }

            int version = in.readInt();
            if (version != SAVED_SELECTIONS_FILE_VERSION) {
                throw new IOException("Unsupported CTSS version: " + version);
            }

            int count = in.readInt();
            if (count < 0) {
                throw new IOException("Invalid saved-selection count: " + count);
            }

            List<SavedSelectionExportRecord> records = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String storageKey = readUtf8(in);
                int imageHash = in.readInt();
                String frameName = readUtf8(in);
                String selectionModeName = readUtf8(in);
                String argText = readUtf8(in);

                FreeformSelectionWindow.SelectionMode selectionMode;
                try {
                    selectionMode = FreeformSelectionWindow.SelectionMode.valueOf(selectionModeName);
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Unknown selection mode in CTSS file: " + selectionModeName, ex);
                }

                records.add(new SavedSelectionExportRecord(
                        storageKey,
                        imageHash,
                        frameName,
                        selectionMode,
                        argText
                ));
            }
            return records;
        } catch (EOFException ex) {
            throw new IOException("The CTSS file ended unexpectedly.", ex);
        }
    }

    private static void writeUtf8(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readUtf8(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("Invalid UTF-8 string length: " + length);
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Unable to read complete UTF-8 string.");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void removeSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select a saved selection first.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        FreeformSelectionManager.SavedSelectionInfo info = tableModel.getRow(modelRow);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Remove saved selection for \"" + info.displayName() + "\"?",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        FreeformSelectionManager.removeStoredSelection(info.storageKey());
        tableModel.refresh();
        clearEditor();
    }

    private void saveSelectionChanges() {
        String hashText = imageHashField.getText() == null ? "" : imageHashField.getText().trim();
        String frameName = frameNameField.getText() == null ? "" : frameNameField.getText().trim();
        String argText = argArea.getText() == null ? "" : argArea.getText().trim();
        FreeformSelectionWindow.SelectionMode selectionMode =
                (FreeformSelectionWindow.SelectionMode) selectionModeCombo.getSelectedItem();

        if (hashText.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Image hash is required to save a selection edit.",
                    "Invalid Input",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        if (argText.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Selection arg is required.",
                    "Invalid Input",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int imageHash;
        try {
            imageHash = (int) Long.parseUnsignedLong(hashText);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Image hash must be an unsigned integer.",
                    "Invalid Input",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            FreeformSelectionManager.upsertStoredSelection(
                    currentStorageKey,
                    imageHash,
                    frameName,
                    selectionMode,
                    argText
            );

            tableModel.refresh();

            FreeformSelectionManager.StoredSelectionEditorData refreshed = findMatchingEditorData(
                    imageHash,
                    frameName,
                    selectionMode
            );
            if (refreshed != null) {
                currentStorageKey = refreshed.storageKey();
            }

            JOptionPane.showMessageDialog(
                    this,
                    "Saved selection updated.",
                    "Saved",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Unable to save selection:\n" + ex.getMessage(),
                    "Save Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private FreeformSelectionManager.StoredSelectionEditorData findMatchingEditorData(
            int imageHash,
            String frameName,
            FreeformSelectionWindow.SelectionMode selectionMode
    ) {
        String safeFrameName = frameName == null ? "" : frameName.trim();

        for (FreeformSelectionManager.SavedSelectionInfo info : tableModel.rows) {
            if (info.imageHash() != imageHash) continue;
            if (info.selectionMode() != selectionMode) continue;

            FreeformSelectionManager.StoredSelectionEditorData editorData =
                    FreeformSelectionManager.getStoredSelectionEditorData(info.storageKey());
            if (editorData == null) continue;

            String candidateFrameName = editorData.frameName() == null ? "" : editorData.frameName().trim();
            if (candidateFrameName.equals(safeFrameName)) {
                return editorData;
            }
        }

        return null;
    }

    private record SavedSelectionExportRecord(
            String storageKey,
            int imageHash,
            String frameName,
            FreeformSelectionWindow.SelectionMode selectionMode,
            String argText
    ) {}

    private static final class SavedSelectionsTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Image",
                "Hash",
                "Type",
                "Summary",
                "Size",
                "Saved At"
        };

        private List<FreeformSelectionManager.SavedSelectionInfo> rows = new ArrayList<>();

        private SavedSelectionsTableModel() {
            refresh();
        }

        public void refresh() {
            rows = new ArrayList<>(FreeformSelectionManager.listStoredSelections());
            fireTableDataChanged();
        }

        public FreeformSelectionManager.SavedSelectionInfo getRow(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FreeformSelectionManager.SavedSelectionInfo row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.displayName();
                case 1 -> row.imageHashText();
                case 2 -> row.selectionKind();
                case 3 -> row.summary();
                case 4 -> row.imageSizeText();
                case 5 -> row.savedAtMillis() > 0
                        ? SETTINGS_TIME_FORMAT.format(Instant.ofEpochMilli(row.savedAtMillis()))
                        : "--";
                default -> "";
            };
        }
    }
}
