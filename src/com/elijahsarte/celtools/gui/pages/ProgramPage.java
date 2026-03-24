package com.elijahsarte.celtools.gui.pages;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;

import com.elijahsarte.celtools.gui.windows.ColorPicker;
import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.ConstructionEx;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.StreamEx;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.stream.IntStream;

import static com.elijahsarte.celtools.gui.Application.prefs;

public abstract class ProgramPage extends JPanel {

    private final Map<String, Supplier<String>> argFn = new HashMap<>();


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
            String stored = prefs.get(id, "");
            boolean storedMult = prefs.getBoolean(id + "_mult", false);

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
                        JFileChooser chooser = varMutate(new JFileChooser(prefs.get(name() + "_lastDir", null)), c -> c.setFileSelectionMode(arg.type().equals("directory") ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES));
                        if (chooser.showOpenDialog(ProgramPage.this) == JFileChooser.APPROVE_OPTION) {
                            prefs.put(name() + "_lastDir", chooser.getSelectedFile().getParentFile().getAbsolutePath());
                            complField.setText(chooser.getSelectedFile().getAbsolutePath());
                        }
                    })), gbc);
                    break;

                case "rgb":
                    gbc.gridwidth = 1;
                    add(complField, gbc);
                    gbc.gridx = 2;
                    add(varMutate(new JButton("Pick"), b -> b.addActionListener(e ->
                            ColorPicker.pickColor(b).thenAccept(c -> complField.setText("{" + c.getRed() + "," + c.getBlue() + "," + c.getGreen() + "}"))
                    )), gbc);
                    break;
                default:
                    gbc.gridwidth = 2;
                    if (storedMult) {
                        StringBuilder stitched = new StringBuilder();
                        String currPiece;
                        int pI = 0;
                        while ((currPiece = prefs.get(id + "_piece_" + pI, null)) != null) {
                            stitched.append(currPiece);
                            pI++;
                        }
                        complField.setText(stitched.toString());
                    }
                    add(complField, gbc);
                    break;
            }
            if (!argFn.containsKey(name)) argFn.put(name, complField::getText);

            gbc.gridwidth = 1;
            gbc.weightx = 0;
        });

    }


    protected Map<String, String> programArgs() {
        // Can't use toMap since null return values are not supported
        return varMutate(new HashMap<>(), m -> argFn.forEach((k, v) -> m.put(k, OptionalEx.ofCond(v.get(), s -> !s.isEmpty()).orElseVal(null))));
    }

    public abstract String name();
    public abstract List<ProgramArg> args();
    public abstract Supplier<? extends Main> program();

}

