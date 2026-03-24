package com.elijahsarte.celtools.main.util.iterators;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.util.function.blocks.forloop.ForIncrement;
import com.elijahsarte.celtools.main.util.function.fntypes.OctaConsumer;
import com.elijahsarte.celtools.main.util.function.fntypes.QuadConsumer;
import com.elijahsarte.celtools.main.util.function.fntypes.QuintConsumer;
import com.elijahsarte.celtools.main.util.function.fntypes.TriConsumer;

import java.util.function.BiConsumer;

public class PixelIterator {

    private final int[] pixels;
    private final int width;
    private final int height;

    private final boolean horiz, async;

    public PixelIterator(int[] pixels, int width, int height, boolean horiz, boolean async) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.horiz = horiz;
        this.async = async;
    }
    public PixelIterator(int[] pixels, int width, int height, boolean horiz) {
        this(pixels, width, height, horiz, false);
    }
    public PixelIterator(int[] pixels, int width, int height) {
        this(pixels, width, height, true);
    }
    public PixelIterator(FastRGB pixels, boolean horiz, boolean async) {
        this(pixels.getPixels(), pixels.getWidth(), pixels.getHeight(), horiz, async);
    }
    public PixelIterator(FastRGB pixels, boolean horiz) {
        this(pixels, horiz, false);
    }
    public PixelIterator(FastRGB pixels) {
        this(pixels, true);
    }


    // pixels, col, row, pixelIndex, rawRGB, double[] rgb, colLoop, rowLoop
    public void execute(OctaConsumer<int[], Integer, Integer, Integer, Integer, int[], ForIncrement, ForIncrement> body) {
        QuadConsumer<Double, Double, ForIncrement, ForIncrement> innerBlock = (col, row, colLoop, rowLoop) -> {
            int index = (int) ((row * width) + col), rgb = pixels[index], r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            body.accept(pixels, col.intValue(), row.intValue(), index, rgb, new int[] { r, g, b }, colLoop, rowLoop);
        };

        if (horiz) {
            new ForIncrement(0, height, 1, this.async).execute((row, rowLoop) -> new ForIncrement(0, width, 1, this.async).execute((col, colLoop) ->
                innerBlock.accept(col, row, colLoop, rowLoop)
            ));
        } else {
            new ForIncrement(0, width, 1, this.async).execute((col, colLoop) -> new ForIncrement(0, height, 1, this.async).execute((row, rowLoop) ->
                innerBlock.accept(col, row, colLoop, rowLoop)
            ));
        }
    }
    public void execute(BiConsumer<Integer, int[]> body) {
        execute((pixels, col, row, pixelIndex, rawRGB, rgb, colLoop, rowLoop) -> body.accept(pixelIndex, rgb));
    }
    // col, row, double[] RGB
    public void execute(TriConsumer<Integer, Integer, int[]> body) {
        execute((pixels, col, row, pixelIndex, rawRGB, rgb, colLoop, rowLoop) -> body.accept(col, row, rgb));
    }
    // col, row, pixelIndex, rawRGB, double[] RGB
    public void execute(QuintConsumer<Integer, Integer, Integer, Integer, int[]> body) {
        execute((pixels, col, row, pixelIndex, rawRGB, rgb, colLoop, rowLoop) -> body.accept(col, row, pixelIndex, rawRGB, rgb));
    }

    public int[] getPixels() {
        return this.pixels;
    }
}

