package com.elijahsarte.celtools.gui.windows;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionManager;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionWindow;
import com.elijahsarte.celtools.main.util.ProgrammingEx;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class InputMapEditor extends JDialog {
    private static final int CT_MAGIC = 0x43544D50; // CTMP
    private static final int CT_VERSION = 1;
    private static final int MAX_CT_ASSOCIATIONS = 1_000_000;
    private static final int MAX_CT_STRING_BYTES = 64 * 1024 * 1024;
    private static final String CT_EXTENSION = "ctmap";
    private static final String CT_EXTENSION_WITH_DOT = "." + CT_EXTENSION;

    private final CompletableFuture<String> savedMap = new CompletableFuture<>();
    private final List<Association> associations = new ArrayList<>();
    private final JPanel associationList = new JPanel();
    private final String keyType;
    private final String valueType;
    private final String chooserScopeKey;

    private InputMapEditor(Window owner, String serializedMap, String keyType, String valueType, String chooserScopeKey) {
        super(owner, keyType + " to " + valueType + " Map", ModalityType.APPLICATION_MODAL);
        this.keyType = keyType;
        this.valueType = valueType;
        this.chooserScopeKey = chooserScopeKey;
        Main.mapFromString(serializedMap).forEach((key, value) -> associations.add(new Association(key, value)));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        associationList.setLayout(new BoxLayout(associationList, BoxLayout.Y_AXIS));
        associationList.setBorder(new EmptyBorder(8, 8, 8, 8));
        rebuild();

        JButton addButton = new JButton("Add association");
        addButton.addActionListener(e -> {
            associations.add(new Association(defaultValue(keyType), defaultValue(valueType)));
            rebuild();
        });

        JButton importButton = new JButton("Import...");
        importButton.addActionListener(e -> importCtMap());

        JButton exportButton = new JButton("Export...");
        exportButton.addActionListener(e -> exportCtMap());

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            Map<String, String> map = new LinkedHashMap<>();
            associations.forEach(association -> map.put(association.key, association.value));
            savedMap.complete(Main.mapToString(map));
            dispose();
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JPanel importExportActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        importExportActions.add(addButton);
        importExportActions.add(importButton);
        importExportActions.add(exportButton);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.add(saveButton);
        actions.add(cancelButton);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(0, 8, 8, 8));
        footer.add(importExportActions, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);

        JScrollPane scrollPane = new JScrollPane(associationList);
        scrollPane.setPreferredSize(new Dimension(560, 280));
        setLayout(new BorderLayout(5, 5));
        add(scrollPane, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(saveButton);
        pack();
        setLocationRelativeTo(owner);
    }

    private void rebuild() {
        associationList.removeAll();
        for (Association association : associations) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            ValueEditor keyEditor = new ValueEditor(keyType, association.key, chooserScopeKey);
            ValueEditor valueEditor = new ValueEditor(valueType, association.value, chooserScopeKey);
            keyEditor.onChange(value -> association.key = value);
            valueEditor.onChange(value -> association.value = value);

            JPanel divider = new JPanel();
            divider.setBackground(Color.BLACK);
            divider.setPreferredSize(new Dimension(4, 34));
            JButton removeButton = new JButton("Remove");
            removeButton.addActionListener(e -> {
                associations.remove(association);
                rebuild();
            });
            row.add(keyEditor);
            row.add(divider);
            row.add(valueEditor);
            row.add(removeButton);
            associationList.add(row);
        }
        associationList.revalidate();
        associationList.repaint();
    }

    private void exportCtMap() {
        JFileChooser chooser = ctFileChooser();
        chooser.setSelectedFile(new File(safeFileName(keyType + "-to-" + valueType + "-map") + CT_EXTENSION_WITH_DOT));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = ensureCtExtension(chooser.getSelectedFile());
        if (file.exists()) {
            int replace = JOptionPane.showConfirmDialog(
                    this,
                    "Replace existing file?\n" + file.getAbsolutePath(),
                    "Export CT Map",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (replace != JOptionPane.YES_OPTION) return;
        }

        try {
            writeCtMap(file, keyType, valueType, associations);
            Main.rememberChooserDirectory(chooserScopeKey, file);
            JOptionPane.showMessageDialog(
                    this,
                    "Exported " + associations.size() + " association" + (associations.size() == 1 ? "" : "s") + ".",
                    "Export CT Map",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (IOException exception) {
            showFileError("Could not export CT map.", exception);
        }
    }

    private void importCtMap() {
        JFileChooser chooser = ctFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            CtMapFile imported = readCtMap(file);
            if (!keyType.equals(imported.keyType) || !valueType.equals(imported.valueType)) {
                JOptionPane.showMessageDialog(
                        this,
                        "This CT map is for a " + imported.keyType + " to " + imported.valueType + " map.\n" +
                                "The current editor is for a " + keyType + " to " + valueType + " map.",
                        "Incompatible CT Map",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            associations.clear();
            associations.addAll(imported.associations);
            Main.rememberChooserDirectory(chooserScopeKey, file);
            rebuild();
        } catch (IOException exception) {
            showFileError("Could not import CT map.", exception);
        }
    }

    private JFileChooser ctFileChooser() {
        JFileChooser chooser = Main.chooserFor(chooserScopeKey);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.setFileFilter(new FileNameExtensionFilter("CelTools map (*" + CT_EXTENSION_WITH_DOT + ")", CT_EXTENSION));
        return chooser;
    }

    private void showFileError(String message, Exception exception) {
        JOptionPane.showMessageDialog(
                this,
                message + "\n" + exception.getMessage(),
                "CT Map Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private static void writeCtMap(File file, String keyType, String valueType, List<Association> associations) throws IOException {
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            output.writeInt(CT_MAGIC);
            output.writeInt(CT_VERSION);
            writeCtString(output, keyType);
            writeCtString(output, valueType);
            output.writeInt(associations.size());
            for (Association association : associations) {
                writeCtString(output, association.key);
                writeCtString(output, association.value);
            }
        }
    }

    private static CtMapFile readCtMap(File file) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int magic = input.readInt();
            if (magic != CT_MAGIC) throw new IOException("Invalid CT map file.");

            int version = input.readInt();
            if (version != CT_VERSION) throw new IOException("Unsupported CT map version: " + version);

            String keyType = readCtString(input);
            String valueType = readCtString(input);
            int associationCount = input.readInt();
            if (associationCount < 0 || associationCount > MAX_CT_ASSOCIATIONS) {
                throw new IOException("Invalid association count: " + associationCount);
            }

            List<Association> associations = new ArrayList<>(associationCount);
            for (int i = 0; i < associationCount; i++) {
                associations.add(new Association(readCtString(input), readCtString(input)));
            }
            return new CtMapFile(keyType, valueType, associations);
        } catch (EOFException exception) {
            throw new IOException("Incomplete CT map file.", exception);
        }
    }

    private static void writeCtString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = nullToEmpty(value).getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readCtString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_CT_STRING_BYTES) throw new IOException("Invalid string byte length: " + length);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static File ensureCtExtension(File file) {
        String path = file.getPath();
        return path.toLowerCase(Locale.ROOT).endsWith(CT_EXTENSION_WITH_DOT) ? file : new File(path + CT_EXTENSION_WITH_DOT);
    }

    private static String safeFileName(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9._-]+", "-");
        safe = safe.replaceAll("-+", "-");
        safe = safe.replaceAll("^-|-$", "");
        return safe.isBlank() ? "map" : safe;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String defaultValue(String type) {
        return type.equals("rgb") ? "{255,255,255}" : type.equals("checkbox") ? "false" : "";
    }

    public static CompletableFuture<String> edit(Component parent, String serializedMap, String keyType, String valueType, String chooserScopeKey) {
        InputMapEditor editor = new InputMapEditor(
                SwingUtilities.getWindowAncestor(parent), serializedMap, keyType, valueType, chooserScopeKey
        );
        editor.setVisible(true);
        return editor.savedMap;
    }

    private static final class CtMapFile {
        private final String keyType;
        private final String valueType;
        private final List<Association> associations;

        private CtMapFile(String keyType, String valueType, List<Association> associations) {
            this.keyType = keyType;
            this.valueType = valueType;
            this.associations = associations;
        }
    }

    private static final class Association {
        private String key;
        private String value;
        private Association(String key, String value) { this.key = key; this.value = value; }
    }

    private static final class ValueEditor extends JPanel {
        private Supplier<String> value;
        private java.util.function.Consumer<String> changeListener = ignored -> {};
        private final String chooserScopeKey;

        private ValueEditor(String type, String initialValue, String chooserScopeKey) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 3, 0));
            this.chooserScopeKey = chooserScopeKey;
            if (type.equals("rgb")) {
                Color initial = parseColor(initialValue);
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(110, 34));
                Color[] selected = { initial };
                styleColorButton(button, initial);
                button.addActionListener(e -> ColorPicker.pickColor(button, selected[0]).thenAccept(color -> {
                    selected[0] = color;
                    styleColorButton(button, color);
                    changeListener.accept(colorString(color));
                }));
                add(button);
                JButton imageButton = new JButton("Image...");
                imageButton.addActionListener(e -> {
                    JFileChooser chooser = Main.chooserFor(chooserScopeKey);
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                    File imageFile = chooser.getSelectedFile();
                    Main.rememberChooserDirectory(chooserScopeKey, imageFile);
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            FreeformSelectionManager.saving = false;
                            return ProgrammingEx.forceReturn(new FreeformSelectionManager(
                                    ImageIO.read(imageFile),
                                    "Pick Map Color " + System.nanoTime(),
                                    FreeformSelectionWindow.SelectionMode.POINT
                            ).getSelectedColor(), () -> FreeformSelectionManager.saving = true);
                        } catch (Exception exception) {
                            throw new CompletionException(exception);
                        }
                    }).thenAccept(color -> SwingUtilities.invokeLater(() -> {
                        if (color == null) return;
                        selected[0] = color;
                        styleColorButton(button, color);
                        changeListener.accept(colorString(color));
                    }));
                });
                add(imageButton);
                value = () -> colorString(selected[0]);
            } else if (type.equals("file") || type.equals("directory")) {
                JTextField field = new JTextField(initialValue, 13);
                JButton browse = new JButton("...");
                browse.addActionListener(e -> {
                    JFileChooser chooser = Main.chooserFor(chooserScopeKey);
                    chooser.setFileSelectionMode(type.equals("directory") ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
                    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = chooser.getSelectedFile();
                        Main.rememberChooserDirectory(chooserScopeKey, selectedFile, type.equals("directory"));
                        field.setText(selectedFile.getAbsolutePath());
                    }
                });
                field.getDocument().addDocumentListener(new SimpleDocumentListener(() -> changeListener.accept(field.getText())));
                add(field);
                add(browse);
                value = field::getText;
            } else if (type.equals("checkbox")) {
                JCheckBox checkbox = new JCheckBox();
                checkbox.setSelected(Boolean.parseBoolean(initialValue));
                checkbox.addChangeListener(e -> changeListener.accept(Boolean.toString(checkbox.isSelected())));
                add(checkbox);
                value = () -> Boolean.toString(checkbox.isSelected());
            } else if (type.equals("dropdown")) {
                JComboBox<String> dropdown = new JComboBox<>();
                dropdown.setEditable(true);
                dropdown.setSelectedItem(initialValue);
                dropdown.addActionListener(e -> changeListener.accept(String.valueOf(dropdown.getEditor().getItem())));
                add(dropdown);
                value = () -> String.valueOf(dropdown.getEditor().getItem());
            } else {
                JTextField field = new JTextField(initialValue, 16);
                field.getDocument().addDocumentListener(new SimpleDocumentListener(() -> changeListener.accept(field.getText())));
                add(field);
                value = field::getText;
            }
        }

        private void onChange(java.util.function.Consumer<String> listener) {
            changeListener = listener;
            listener.accept(value.get());
        }

        private static Color parseColor(String value) {
            try {
                double[] rgb = Main.colorFromString(value);
                return new Color((int) rgb[0], (int) rgb[1], (int) rgb[2]);
            } catch (RuntimeException ignored) {
                return Color.WHITE;
            }
        }

        private static String colorString(Color color) {
            return "{" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "}";
        }

        private static void styleColorButton(JButton button, Color color) {
            button.setBackground(color);
            button.setOpaque(true);
            button.setToolTipText(String.format("RGB (%d, %d, %d)", color.getRed(), color.getGreen(), color.getBlue()));
        }
    }

    private static final class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable action;
        private SimpleDocumentListener(Runnable action) { this.action = action; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
    }
}
