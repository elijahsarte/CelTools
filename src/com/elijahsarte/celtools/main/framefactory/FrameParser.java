package com.elijahsarte.celtools.main.framefactory;

import com.elijahsarte.celtools.main.colormodel.HSVBounds;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.function.blocks.forloop.ForIncrement;
import com.elijahsarte.celtools.main.util.function.fntypes.OctaConsumer;
import com.elijahsarte.celtools.main.util.iterators.PixelIterator;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class FrameParser {

    public static final int NO_PIXEL = -16777215;
    public static final int FILTERED_PIXEL = -1;



    public static <T extends Number> double normalize(T value, double min, double max) {
        return MathEx.divide(MathEx.subtract(value, min), (max - min));
    }
    public static double denormalize(double normalized, double min, double max) {
        return (normalized * (max - min) + min);
    }

    public static <T extends Number> double convertScale(T value, double oldMin, double oldMax, double newMin, double newMax) {
        return denormalize(normalize(value, oldMin, oldMax), newMin, newMax);
    }

    public static <T extends Number> double[] conv360HSV(T[] hsv) {
        return new double[] {
                convertScale(hsv[0], 0, 1, 0, 360),
                convertScale(hsv[1], 0, 1, 0, 100),
                convertScale(hsv[2], 0, 1, 0, 100),
        };
    }
    public static double[] conv360HSV(int[] hsv) {
        return new double[] {
                convertScale(hsv[0], 0, 1, 0, 360),
                convertScale(hsv[1], 0, 1, 0, 100),
                convertScale(hsv[2], 0, 1, 0, 100),
        };
    }
    public static double[] conv360HSV(double[] hsv) {
        return new double[] {
                convertScale(hsv[0], 0, 1, 0, 360),
                convertScale(hsv[1], 0, 1, 0, 100),
                convertScale(hsv[2], 0, 1, 0, 100),
        };
    }
    public static double[] conv360HSV(float[] hsv) {
        return new double[] {
                convertScale(hsv[0], 0, 1, 0, 360),
                convertScale(hsv[1], 0, 1, 0, 100),
                convertScale(hsv[2], 0, 1, 0, 100),
        };
    }
    public static <T extends Number> double[] convPercentHSV(T[] hsv) {
        return new double[] {
                convertScale(hsv[0], 0, 360, 0, 1),
                convertScale(hsv[1], 0, 100, 0, 1),
                convertScale(hsv[2], 0, 100, 0, 1)
        };
    }
    public static double[] convPercentHSV(double[] hsv) {
        return new double[] {
                convertScale(hsv[0], 0, 360, 0, 1),
                convertScale(hsv[1], 0, 100, 0, 1),
                convertScale(hsv[2], 0, 100, 0, 1)
        };
    }
    public static double[] convPercentHSV(int[] hsv) {
        return new double[] {
                convertScale(hsv[0], 0, 360, 0, 1),
                convertScale(hsv[1], 0, 100, 0, 1),
                convertScale(hsv[2], 0, 100, 0, 1)
        };
    }
    public static double[] convPercentHSV(float[] hsv) {
        return new double[] {
                convertScale(hsv[0], 0, 360, 0, 1),
                convertScale(hsv[1], 0, 100, 0, 1),
                convertScale(hsv[2], 0, 100, 0, 1)
        };
    }



    public static <T extends Number, U extends Number, V extends Number> boolean inBetweenClosed(T[] given, U[] min, V[] max) {
        return MathEx.gEqu(given[0], min[0]) && MathEx.lEqu(given[0], max[0])
                && MathEx.gEqu(given[1], min[1]) && MathEx.lEqu(given[1], max[1])
                && MathEx.gEqu(given[2], min[2]) && MathEx.lEqu(given[2], max[2]);
    }
    public static boolean inBetweenClosed(int[] given, int[] min, int[] max) {
        boolean inBetween = false;
        for (int i = 0; i < given.length; i++) inBetween = given[i] >= min[i] && given[i] <= max[i];
        return inBetween;
    }
    public static boolean inBetweenClosed(double[] given, int[] min, int[] max) {
        boolean inBetween = false;
        for (int i = 0; i < given.length; i++) inBetween = given[i] >= min[i] && given[i] <= max[i];
        return inBetween;
    }
    public static boolean inBetweenClosed(int[] given, double[] min, int[] max) {
        boolean inBetween = false;
        for (int i = 0; i < given.length; i++) inBetween = given[i] >= min[i] && given[i] <= max[i];
        return inBetween;
    }
    public static boolean inBetweenClosed(int[] given, int[] min, double[] max) {
        boolean inBetween = false;
        for (int i = 0; i < given.length; i++) inBetween = given[i] >= min[i] && given[i] <= max[i];
        return inBetween;
    }
    public static boolean inBetweenClosed(double[] given, double[] min, int[] max) {
        boolean inBetween = false;
        for (int i = 0; i < given.length; i++) inBetween = given[i] >= min[i] && given[i] <= max[i];
        return inBetween;
    }
    public static boolean inBetweenClosed(double[] given, int[] min, double[] max) {
        boolean inBetween = false;
        for (int i = 0; i < given.length; i++) inBetween = given[i] >= min[i] && given[i] <= max[i];
        return inBetween;
    }
    public static boolean inBetweenClosed(int[] given, double[] min, double[] max) {
        boolean inBetween = false;
        for (int i = 0; i < given.length; i++) inBetween = given[i] >= min[i] && given[i] <= max[i];
        return inBetween;
    }
    public static boolean inBetweenClosed(double[] given, double[] min, double[] max) {
        boolean inBetween = false;
        for (int i = 0; i < given.length; i++) inBetween = given[i] >= min[i] && given[i] <= max[i];
        return inBetween;
    }


    public static double[] RGBtoHSV(int[] rgb) {
        return conv360HSV(Color.RGBtoHSB(rgb[0], rgb[1], rgb[2], null));
    }

    public static <T extends Number> int[] HSVtoRGB(T[] hsv) {
        return FastRGB.getRGB(ProgrammingEx.varOper(convPercentHSV(hsv), f -> Color.HSBtoRGB((float) f[0], (float) f[1], (float) f[2])));
    }
    public static int[] HSVtoRGB(int[] hsv) {
        return FastRGB.getRGB(ProgrammingEx.varOper(convPercentHSV(hsv), f -> Color.HSBtoRGB((float) f[0], (float) f[1], (float) f[2])));
    }
    public static int[] HSVtoRGB(double[] hsv) {
        return FastRGB.getRGB(ProgrammingEx.varOper(convPercentHSV(hsv), f -> Color.HSBtoRGB((float) f[0], (float) f[1], (float) f[2])));
    }
    public static int[] HSVtoRGB(float[] hsv) {
        return FastRGB.getRGB(ProgrammingEx.varOper(convPercentHSV(hsv), f -> Color.HSBtoRGB((float) f[0], (float) f[1], (float) f[2])));
    }



    public static BufferedImage filterHSV(BufferedImage input, HSVBounds hsvBounds) {
        return pixelsToImage(ProgrammingEx.varMutate(new ImageHandler(input).getImgPixels().clone(), p -> filterHSV(p, input.getWidth(), input.getHeight(), hsvBounds)), input.getWidth(), input.getHeight());
    }
    // only takes hsv scales where h is 0 to 360, s is 0 to 100, and v as well
    public static void filterHSV(int[] pixels, int width, int height, HSVBounds hsvBounds, OctaConsumer<int[], Integer, Integer, Integer, Integer, int[], ForIncrement, ForIncrement> filterFn, OctaConsumer<int[], Integer, Integer, Integer, Integer, int[], ForIncrement, ForIncrement> elseFn) {
        new PixelIterator(pixels, width, height).execute((hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> {
            if (hsvBounds.within(conv360HSV(Color.RGBtoHSB(rgb[0], rgb[1], rgb[2], null)))) {
                filterFn.accept(hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop);
                pixels[index] = FILTERED_PIXEL;
            } else {
                elseFn.accept(hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop);
                pixels[index] = NO_PIXEL;
            }
        });
//        return pixels;
    }
    public static void filterHSV(int[] pixels, int width, int height, HSVBounds hsvBounds, BiConsumer<Integer, Integer> filterFn, BiConsumer<Integer, Integer> otherFn) {
        filterHSV(pixels, width, height, hsvBounds,
                (hPixels, col, row, index, rawRGB, rgb, colLoop, rawLoop) -> filterFn.accept(col, row),
                (hPixels, col, row, index, rawRGB, rgb, colLoop, rawLoop) -> otherFn.accept(col, row));
    }
    public static void filterHSV(int[] pixels, int width, int height, HSVBounds hsvBounds, BiConsumer<Integer, Integer> fn) {
        filterHSV(pixels, width, height, hsvBounds, fn, (col, row) -> {});
    }
    public static void filterHSV(int[] pixels, int width, int height, HSVBounds hsvBounds) {
        filterHSV(pixels, width, height, hsvBounds,
                (hPixels, col, row, index, rawRGB, rgb, colLoop, rawLoop) -> pixels[index] = FILTERED_PIXEL,
                (hPixels, col, row, index, rawRGB, rgb, colLoop, rawLoop) -> pixels[index] = NO_PIXEL);
    }

    public static void filterHSV(int[] pixels, int width, int height, Predicate<double[]> hsvQualifier, OctaConsumer<int[], Integer, Integer, Integer, Integer, int[], ForIncrement, ForIncrement> filterFn, OctaConsumer<int[], Integer, Integer, Integer, Integer, int[], ForIncrement, ForIncrement> elseFn) {
        new PixelIterator(pixels, width, height).execute((hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> {
            double[] hsv = conv360HSV(Color.RGBtoHSB(rgb[0], rgb[1], rgb[2], null));
            if (hsvQualifier.test(hsv)) {
                filterFn.accept(hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop);
                pixels[index] = FILTERED_PIXEL;
            } else {
                elseFn.accept(hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop);
                pixels[index] = NO_PIXEL;
            }
        });
    }
    public static void filterHSV(int[] pixels, int width, int height, Predicate<double[]> hsvQualifier, BiConsumer<Integer, Integer> filterFn, BiConsumer<Integer, Integer> elseFn) {
        filterHSV(pixels, width, height, hsvQualifier,
                (hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> filterFn.accept(col, row),
                (hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> elseFn.accept(col, row));
    }
    public static void filterHSV(int[] pixels, int width, int height, Predicate<double[]> hsvQualifier, BiConsumer<Integer, Integer> filterFn) {
        filterHSV(pixels, width, height, hsvQualifier, filterFn, (col, row) -> {});
    }
    public static void filterHSV(int[] pixels, int width, int height, Predicate<double[]> hsvQualifier) {
        filterHSV(pixels, width, height, hsvQualifier,
                (hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> pixels[index] = FILTERED_PIXEL,
                (hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> pixels[index] = NO_PIXEL);
    }



    public static BufferedImage pixelsToImage(int[] pixels, int rgbType, int width, int height) {
        return ProgrammingEx.varMutate(new BufferedImage(width, height, rgbType),
                outputCel -> outputCel.setData(Raster.createRaster(outputCel.getSampleModel(), new DataBufferInt(pixels, pixels.length), new Point()))
        );
    }
    public static BufferedImage pixelsToImage(int[] pixels, int width, int height) {
        return pixelsToImage(pixels, BufferedImage.TYPE_INT_RGB, width, height);
    }

}

