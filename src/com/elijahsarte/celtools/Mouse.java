package com.elijahsarte.celtools;// https://stackoverflow.com/a/20177811/15446511
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class Mouse extends JPanel implements MouseListener, MouseMotionListener {
    private int x1 ;
    private int y1 ;
    private int cx,cy;
    public Mouse(String name) {
        super();
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        JFrame fr = new JFrame(name);
        fr.add(this);
        fr.setSize(500, 500);
        setBackground(Color.green);
        fr.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fr.setVisible(true);

    }

    public void paintComponent(Graphics g) {
        super.paintComponents(g);
        g.drawLine(cx, cy, x1, y1);
    }

    public void mouseDragged(MouseEvent e) {
        x1 = e.getX();
        y1 = e.getY();
        cx = x1;
        cy = y1;
        repaint();
    }

    public void mousePressed(MouseEvent e) {

        cx = e.getX();
        cy = e.getY();
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
        cx = e.getX();
        cy = e.getY();
        repaint();
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {

    }

    public void mouseMoved(MouseEvent e) {
    }

    public static void main(String[] args) {
        Mouse mouse = new Mouse("com.elijahsarte.celpaintcrop.Mouse");

    }
}
