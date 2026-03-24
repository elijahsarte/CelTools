package com.elijahsarte.celtools.main.util.iterators;

import com.elijahsarte.celtools.main.util.function.blocks.forloop.ForIncrement;
import com.elijahsarte.celtools.main.util.function.fntypes.QuadConsumer;

import java.awt.*;
import java.util.function.BiConsumer;

public record CoordIterator(int startW, int w, int startH, int h) {

    public CoordIterator(double startW, double w, double startH, double h) {
        this((int) startW, (int) w, (int) startH, (int) h);
    }
    public CoordIterator(int w, int h) {
        this(0, w, 0, h);
    }
    public CoordIterator(double w, double h) {
        this((int) w, (int) h);
    }
    public CoordIterator(Rectangle rect) {
        this(rect.getX(), rect.getX() + rect.getWidth(), rect.getY(), rect.getY() + rect.getHeight());
    }
    // x, y, x loop, y loop
    public void execute(QuadConsumer<Integer, Integer, ForIncrement, ForIncrement> body) {
        new ForIncrement(startW, w, 1).execute((x, xLoop) -> new ForIncrement(startH, h, 1).execute((y, yLoop) -> body.accept(x.intValue(), y.intValue(), xLoop, yLoop)));
    }
    public void execute(BiConsumer<Integer, Integer> body) {
        new ForIncrement(startW, w, 1).execute(x -> new ForIncrement(startH, h, 1).execute(y -> body.accept(x.intValue(), y.intValue())));
    }

}

