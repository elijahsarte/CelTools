package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.gui.windows.ColorPicker;
import com.elijahsarte.celtools.gui.windows.InputMapEditor;
import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.util.ConstructionEx;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.StreamEx;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.stream.IntStream;

import static com.elijahsarte.celtools.gui.Application.prefs;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.noExcept;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.varMutate;

public abstract class ProgramPage extends JPanel {

    private final Map<String, Supplier<String>> argFn = new HashMap<>();

    protected String chooserScopeKey() {
        return name().replaceAll("\\s*\\(.*\\)$", "").trim();
    }


    public ProgramPage() {

        this.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        StreamEx.forEachInc(args(), (arg, y) -> {
            gbc.gridx = 0;
            gbc.gridy = y;
            add(new JLabel(arg.label() + ":"), gbc);

            gbc.gridy = y;
            gbc.gridx = 1;
            gbc.weightx = 1;

            String name = arg.argName();
            String id = name() + "_" + name;
            String stored = storedValue(id);

            JTextField complField = varMutate(new JTextField(17), f -> f.setText(stored));
            complField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    String text = complField.getText();
                    int mVL = Preferences.MAX_VALUE_LENGTH;
                    if (text.length() > mVL) {
                        prefs.putBoolean(id + "_mult", true);
                        noExcept(prefs::flush);
                        IntStream.range(0, MathEx.ceilDiv(text.length(), mVL)).forEach(i ->
                            prefs.put(id + "_piece_" + i, ConstructionEx.fastToString(text.subSequence(i * mVL, Math.min(text.length(), ((i + 1) * mVL)))))
                        );
                    } else {
                        prefs.putBoolean(id + "_mult", false);
                        prefs.put(id, complField.getText());
                    }
                    noExcept(prefs::flush);
                }
                @Override
                public void removeUpdate(DocumentEvent e) {
                    insertUpdate(e);
                }
                @Override
                public void changedUpdate(DocumentEvent e) {
                    insertUpdate(e);
                }
            });

            switch (arg.type()) {
                case "checkbox":
                    gbc.gridwidth = 2;
                    JCheckBox box = varMutate(new JCheckBox(), b -> b.setSelected(stored.equalsIgnoreCase("true")));
                    box.addChangeListener(l -> prefs.putBoolean(id, box.isSelected()));
                    add(box, gbc);
                    argFn.put(name, () -> Boolean.toString(box.isSelected()));
                    break;
                case "dropdown":
                    gbc.gridwidth = 1;
                    add(new JComboBox<>(), gbc);
                    argFn.put(name, () -> "");
                    break;
                case "file":
                case "directory":
                    gbc.gridwidth = 1;
                    add(complField, gbc);

                    gbc.gridx = 2;
                    gbc.weightx = 0;

                    add(varMutate(new JButton("Browse..."), b -> b.addActionListener(e -> {
                        JFileChooser chooser = varMutate(Main.chooserFor(chooserScopeKey()), c -> c.setFileSelectionMode(arg.type().equals("directory") ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES));
                        if (chooser.showOpenDialog(ProgramPage.this) == JFileChooser.APPROVE_OPTION) {
                            File selectedFile = chooser.getSelectedFile();
                            Main.rememberChooserDirectory(chooserScopeKey(), selectedFile, arg.type().equals("directory"));
                            complField.setText(selectedFile.getAbsolutePath());
                        }
                    })), gbc);
                    break;

                case "rgb":
                    gbc.gridwidth = 1;
                    add(complField, gbc);
                    gbc.gridx = 2;
                    add(varMutate(new JButton("Pick"), b -> b.addActionListener(e ->
                            ColorPicker.pickColor(b).thenAccept(c -> complField.setText("{" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + "}"))
                    )), gbc);
                    break;
                default:
                    if (arg.type().startsWith("map-") && arg.type().substring(4).split("-", -1).length == 2) {
                        String[] mapTypes = arg.type().substring(4).split("-", -1);
                        gbc.gridwidth = 1;
                        JButton editMapButton = new JButton("Edit map...");
                        add(editMapButton, gbc);

                        gbc.gridx = 2;
                        JCheckBox mapEnabled = new JCheckBox("Enabled", prefs.getBoolean(id + "_enabled", false));
                        add(mapEnabled, gbc);
                        editMapButton.setEnabled(mapEnabled.isSelected());
                        mapEnabled.addChangeListener(e -> {
                            prefs.putBoolean(id + "_enabled", mapEnabled.isSelected());
                            editMapButton.setEnabled(mapEnabled.isSelected());
                        });
                        editMapButton.addActionListener(e -> InputMapEditor.edit(
                                editMapButton, complField.getText(), mapTypes[0], mapTypes[1], chooserScopeKey()
                        ).thenAccept(complField::setText));
                        argFn.put(name, () -> mapEnabled.isSelected() ? complField.getText() : "");
                    } else {
                        gbc.gridwidth = 2;
                        add(complField, gbc);
                    }
                    break;
            }
            if (!argFn.containsKey(name)) argFn.put(name, complField::getText);

            gbc.gridwidth = 1;
            gbc.weightx = 0;
        });

    }

    private static String storedValue(String id) {
        if (!prefs.getBoolean(id + "_mult", false)) return prefs.get(id, "");

        StringBuilder stitched = new StringBuilder();
        String piece;
        int pieceIndex = 0;
        while ((piece = prefs.get(id + "_piece_" + pieceIndex, null)) != null) {
            stitched.append(piece);
            pieceIndex++;
        }
        return stitched.toString();
    }


    protected Map<String, String> programArgs() {
        // Can't use toMap since null return values are not supported
        return varMutate(new HashMap<>(), m -> argFn.forEach((k, v) -> m.put(k, OptionalEx.ofCond(v.get(), s -> !s.isEmpty()).orElseVal(null))));
    }

    public abstract String name();
    public abstract List<ProgramArg> args();
    public abstract Supplier<? extends Main> program();

}

