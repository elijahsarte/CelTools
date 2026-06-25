package com.elijahsarte.celtools.main.util;

import com.elijahsarte.celtools.main.util.geomex.Line;

import java.awt.*;
import java.awt.image.BufferedImage;

public class GraphicsEx {

    private static Color currColor;

    public static final Color BROWN = new Color(150, 75, 0);

    public static Graphics2D populateImage(BufferedImage bi, Color color) {
        Graphics2D g = bi.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
        return g;
    }
    public static Graphics2D populateImage(BufferedImage bi) {
        return populateImage(bi, Color.WHITE);
    }

    private static void storeColor(Graphics2D g) {
        currColor = g.getColor();
    }
    private static void restoreColor(Graphics2D g) {
        g.setColor(currColor);
    }
    public static void drawLine(Graphics2D g, Line l, Color color) {
        storeColor(g);
        g.setColor(color);
        g.drawLine(l.startX(), l.start().y, l.endX(), l.end().y);
        restoreColor(g);
    }
    public static void drawLine(Graphics2D g, Line l) {
        drawLine(g, l, Color.BLACK);
    }

    public static String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

}

