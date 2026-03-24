package com.elijahsarte.celtools.gui.windows;

import com.elijahsarte.celtools.main.util.ProgrammingEx;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;


public class ColorPicker extends JDialog {
    private final CompletableFuture<Color> pickedColor = new CompletableFuture<>();

    private ColorPicker(Frame owner) {
        super(owner, "Color Picker", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);


        JColorChooser chooser = new JColorChooser(Color.WHITE);
        Arrays.stream(chooser.getChooserPanels()).filter(p -> !ProgrammingEx.anyEquals(p.getDisplayName().toLowerCase(), "hsv", "hsv")).forEach(chooser::removeChooserPanel);


        JButton pickBtn = new JButton("Pick");
        pickBtn.addActionListener(e -> {
            pickedColor.complete(chooser.getColor());
            dispose();
        });

        setLayout(new BorderLayout(10,10));
        add(chooser, BorderLayout.CENTER);
        add(ProgrammingEx.varMutate(new JPanel(), b -> b.add(pickBtn)), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    public static CompletableFuture<Color> pickColor(Component parent) {
        return ProgrammingEx.varMutate(new ColorPicker((JFrame) SwingUtilities.getWindowAncestor(parent)), c -> c.setVisible(true)).pickedColor;
    }
}


