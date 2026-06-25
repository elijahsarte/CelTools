package com.elijahsarte.celtools.main.colormodel;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.StreamEx;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.mainex.DebuggerEx;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.elijahsarte.celtools.main.util.MathEx.limMod;

public class ColorSampleDataModel {

    private static final int HUE_BUCKET_SIZE = 5;

    private final FastRGB image;
    private final int width, height, length;
    private final double[][] cachedHSV;

    private double[] averageHSV;
    private HSVBounds boundsHSV;

    private final Map<Integer, Double> madDeviations = new HashMap<>();
    private double mad;

    public static void main(String[] args) throws IOException {
//        String path = "C:/Users/Other user/Documents/celpaintfill_sandbox/datamodel/figure_output_sample.png";
//        String path = "C:/Users/Other user/Documents/celtoolsandbox/celpaintmergefill/paintmodel_test/regular_sample.png";
//        String path = "C:/Users/Other user/Documents/celtoolsandbox/celpaintmergefill/paintmodel_test/regular_sample_2.png";
//        String path = "C:/Users/Other user/Documents/celtoolsandbox/celpaintmergefill/paintmodel_test/regular_sample_3.png";
        String path = "C:/Users/Other user/Documents/celtoolsandbox/celpaintmergefill/paintmodel_test/spliced_sample.png";
//        String path = "C:/Users/Other user/Documents/celtoolsandbox/celpaintmergefill/color_cel.png";
        BufferedImage img = Main.handlerFromFile(path).getImage();
        ColorSampleDataModel model = new ColorSampleDataModel(img);
        System.out.println("Avg HSV: " + Arrays.toString(model.getAvgHSV()));
        System.out.println("MAD: " + model.getMAD());
//        DebuggerEx.vis("img_temp", img);
        model.showHeatmap();
    }

    public ColorSampleDataModel(BufferedImage image) {
        this.image = new FastRGB(image);
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.length = width * height;

        this.cachedHSV = new double[length][3];
        this.calcHSVMap();
        this.calcMAD();
        this.boundsHSV.expandAll((int) getMAD());
    }

    private void calcHSVMap() {
        double sumSin = 0, sumCos = 0;
        double sumS = 0, sumV = 0;

        TreeMap<Integer, PointCollection> rawHSV = new TreeMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                double[] hsv = image.getHSV(x, y);

                int idx = y * width + x;
                cachedHSV[idx] = hsv;

                double angle = hsv[0] * 2 * Math.PI;
                sumCos += MathEx.cos(angle);
                sumSin += MathEx.sin(angle);
                sumS += hsv[1];
                sumV += hsv[2];
                CollectionsEx.structPut(rawHSV, limMod((int) hsv[0], HUE_BUCKET_SIZE), (coll) ->
                    coll.add(new Point((int) hsv[1], (int) hsv[2]))
                , new PointCollection());

            }
        }
        double avgHueRad = Math.atan2(sumSin, sumCos);
        if (avgHueRad < 0) avgHueRad += 2 * Math.PI;

        this.averageHSV = new double[]{MathEx.divide(avgHueRad, (2 * Math.PI)), MathEx.divide(sumS, length), MathEx.divide(sumV, length)};
//        IntStream.rangeClosed(limMod(rawHSV.firstKey(), HUE_BUCKET_SIZE), nextMod(rawHSV.lastKey(), HUE_BUCKET_SIZE)).filter(i -> )
//        IntStream.rangeClosed(rawHSV.firstKey(), nextMod(rawHSV.lastKey(), HUE_BUCKET_SIZE)).filter(i -> !rawHSV.containsKey(i)/* && (!rawHSVrawHSV.containsKey(MathEx.limMod(i, HUE_BUCKET_SIZE))*//*).forEach(i -> rawHSV.put(i, rawHSV.get(divisible(i, HUE_BUCKET_SIZE) ? i - HUE_BUCKET_SIZE : limMod(i, HUE_BUCKET_SIZE))));*/
        this.boundsHSV = new HSVBounds((TreeMap<Integer, PointCollection>) StreamEx.toMap(rawHSV.entrySet().stream().flatMap(e -> StreamEx.modRange(e.getKey(), HUE_BUCKET_SIZE).mapToObj(i -> Map.entry(i, e.getValue()))), TreeMap::new));
    }


    private void calcMAD() {
        AtomicReference<Double> sum = new AtomicReference<>(0d);
        for (int i = 0; i < length; i++) {
            double diff = hsvDiff(cachedHSV[i], averageHSV);
            madDeviations.put(i, diff);
            sum.set(sum.get() + diff);
        }
        this.mad = MathEx.divide(sum.get(), length);
    }


    private double hsvDiff(double[] a, double[] b) {
        // hue circular diff
        double dh = Math.abs(a[0] - b[0]);
        if (dh > 0.5) dh = 1.0 - dh;
        return Math.sqrt(MathEx.square(dh) + MathEx.square(a[1] - b[1]) + MathEx.square(a[2] - b[2]));
    }

    public double[] getAvgHSV() {
        return averageHSV;
    }
    public HSVBounds getHSVBounds() {
        return boundsHSV;
    }
    public double getMAD() {
        return mad;
    }



    public void showHeatmap() {
        double maxDeviation = Collections.max(madDeviations.values());
        this.madDeviations.forEach((pixelIndex, deviation) -> {
            double alpha = deviation / maxDeviation;
//            double alpha = Math.abs((BigDecimal.valueOf(deviation).divide(BigDecimal.valueOf(maxDeviation), 2, RoundingMode.HALF_UP)).doubleValue());
//            System.out.println(deviation);
//            Color modGreen = new Color(0, 0, 0);
//            Color modGreen = null;
            Color modGreen = new Color(255, 255, 255);
            if (alpha >= 0.9) {
                modGreen = new Color(13, 69, 0);
            } else if (alpha >= 0.8) {
                modGreen = new Color(22, 109, 3);
            } else if (alpha >= 0.7) {
                modGreen = new Color(30, 139, 7);
            } else if (alpha >= 0.6) {
                modGreen = new Color(41, 171, 12);
            } else if (alpha >= 0.5) {
                // alpha doesn't even fucking work (and yes i tried to convert everything in ImageHandler to TYPE_INT_ARGB)
//                modGreen = new Color(41, 171, 12, (int) (alpha * 0.65) * 255);
                modGreen = new Color(56, 201, 24);
            } else if (alpha >= 0.4) {
                modGreen = new Color(77, 217, 46);
            } else if (alpha >= 0.3) {
                modGreen = new Color(120, 240, 93);
            } else if (alpha >= 0.2) {
                modGreen = new Color(176, 255, 158);
            } else if (alpha >= 0.1) {
                modGreen = new Color(221, 255, 214);
            } else if (alpha <= -0.9) {
                modGreen = new Color(120, 7, 3);
            } else if (alpha <= -0.8) {
                modGreen = new Color(163, 18, 5);
            } else if (alpha <= -0.7) {
                modGreen = new Color(184, 22, 7);
            } else if (alpha <= -0.6) {
                modGreen = new Color(201, 36, 20);
            } else if (alpha <= -0.5) {
                modGreen = new Color(219, 49, 33);
            } else if (alpha <= -0.4) {
                modGreen = new Color(245, 105, 92);
            } else if (alpha <= -0.3) {
                modGreen = new Color(252, 142, 134);
            } else if (alpha <= -0.2) {
                modGreen = new Color(252, 182, 177);
            } else if (alpha <= -0.1) {
                modGreen = new Color(255, 215, 212);
            }
            image.setRGB(pixelIndex % width, pixelIndex / width, modGreen.getRGB());
        });
        DebuggerEx.vis("hsv_heatmap", image);
    }

}

