package com.elijahsarte.celtools.main.selectionui;// https://stackoverflow.com/a/20177811/15446511
import com.elijahsarte.celtools.Mouse;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MouseCanvas extends JPanel implements MouseListener, MouseMotionListener {
    private int x1 ;
    private int y1 ;
    private int cx,cy;
    private JFrame fr;
    private CompletableFuture<Boolean> runtime = new CompletableFuture<>();
    private List<int[]> lines = new ArrayList<>();
    public MouseCanvas(String name) {
        super();
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        fr = new JFrame(name);
        fr.add(this);
        fr.setSize(500, 500);
        setBackground(Color.green);
        fr.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        fr.setVisible(true);

    }
    public boolean appear() {
        fr.setVisible(true);
        fr.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                runtime.complete(true);
            }
        });
        try {
            return runtime.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }


    public void paintComponent(Graphics g) {
        super.paintComponents(g);
        g.drawLine(cx, cy, x1, y1);
        lines.forEach(l -> g.drawLine(l[0], l[1], l[2], l[3]));
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
       cx = x1; cy = y1; this.x1 = x2; this.y1 = y2;
       lines.add(new int[] { x1, y1, x2, y2});
       this.repaint();
//       fr.repaint();
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
