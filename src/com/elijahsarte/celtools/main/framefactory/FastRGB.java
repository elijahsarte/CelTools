package com.elijahsarte.celtools.main.framefactory;


import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.util.Arrays;

// https://stackoverflow.com/a/26713029/15446511
// this class is for int[] pixels based off FastRGB based off stack overflow
public class FastRGB {

    private int width;
    private int height;
    private boolean hasAlphaChannel;
    private int[] pixels;

    public static void main(String[] args) {
        /*
        System.out.println("fastrgb: " + FastRGB.getRGBRaw(new int[] { 255, 255, 255 }));
        System.out.println("fastrgb: " + Arrays.toString(FastRGB.getRGB(-1)));
        System.out.println(new Color(-1).getRed());
        System.out.println(new Color(-1).getGreen());
        System.out.println(new Color(-1).getBlue());
        System.out.println(new Color(255, 255, 255).getRGB());*/
        // 12/29/2024 1:04:00 PM: meh good enough
        int g = FastRGB.getRGBRaw(new int[] { 45, 109, 203});
        System.out.println("raw bitshift: " + ((g >> 24) & 0xff));
//        int h = FastRGB.getRGBRaw(new int[] { 45, 109, 203, 178});
        int h = FastRGB.getRGBRaw(new int[] { 45, 109, 203 });
        System.out.println("fastrgb: " + h);
//        System.out.println("fastrgb: " + Arrays.toString(FastRGB.getRGBA(h)));
        System.out.println("fastrgb: " + Arrays.toString(FastRGB.getRGB(h)));
        System.out.println("color rgb: " + String.join(", ", Arrays.stream(new int[] { new Color(h).getRed(), new Color(h).getGreen(), new Color(h).getBlue(), new Color(h).getAlpha() }).boxed().map(String::valueOf).toList()));
//        System.out.println("color raw: " + new Color(45, 109, 203, 178).getRGB());
        System.out.println("color raw: " + new Color(45, 109, 203).getRGB());
    }

    public FastRGB(int[] pixels, int width, int height, boolean hasAlphaChannel) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.hasAlphaChannel = hasAlphaChannel;        
    }
    public FastRGB(int width, int height, boolean hasAlphaChannel) {
        this(new int[width * height], width, height, hasAlphaChannel);
    }
    public FastRGB(ImageHandler imageHandler) {
        this(imageHandler.getImgPixels(), imageHandler.getWidth(), imageHandler.getHeight(), imageHandler.getAlphaRaster() != null);
    }
    public FastRGB(BufferedImage image) {
        this(new ImageHandler(image));
    }



    public int getRGBRaw(int x, int y)  {
        int pos = (y * width) + (x);
        /*
        int argb = -16777216; // 255 alpha
        if (hasAlphaChannel) argb = (((int) pixels[pos] & 0xff) << 24); // alpha
        if (pos == 351597) {
            String h = "l";
        }
        argb += ((int) pixels[pos] & 0xff); // blue
        argb += (((int) pixels[pos] & 0xff) << 8); // green
        argb += (((int) pixels[pos] & 0xff) << 16); // red
        return argb;*/
        if (pos >= pixels.length || pos < 0) {
            throw new ArrayIndexOutOfBoundsException("Index " + pos + " at x " + x + " and y " + y + " out of bounds for length " + pixels.length + ", width " + width + " and height " + height);
        }
        return pixels[pos];
    }
    public static int getRGBRaw(int[] rgb) {
        return ProgrammingEx.varOper(
                OptionalEx.ofCond(rgb, rgb.length == 3).orElse(new int[] { rgb[0], rgb[1], rgb[2], 255}),
                arr -> ((arr[3] & 0xff) << 24) | ((arr[0] & 0xff) << 16) | ((arr[1] & 0xff) << 8) | ((arr[2] & 0xff))
        );
    }

    public int getRGBRaw(double x, double y) {
        return getRGBRaw((int) x, (int) y);
    }
    public static int[] getRGB(int rgb) {
//        return ProgrammingEx.varOper(argb & 0xff, (baseBitshift) -> new int[] { baseBitshift, baseBitshift << 8, baseBitshift << 16});
        return new int[] { (rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff };
    }
    public static int[] getRGBA(int argb) {
        return new int[] { (argb >> 16) & 0xff, (argb >> 8) & 0xff, (argb) & 0xff, (argb >> 24) & 0xff };
    }
    public int[] getRGB(int x, int y) {
        return getRGB(pixels[(y * width) + (x)]);
    }
    public int[] getRGB(double x, double y) {
        return getRGB((int) x, (int) y);
    }
    public double[] getHSV(int x, int y) {
        return FrameParser.RGBtoHSV(getRGB(x, y));
    }
    public double[] getHSV(double x, double y) {
        return getHSV((int) x, (int) y);
    }

    public void setRGB(int x, int y, int argb) {
        int pos = (y * width) + x;
        if (pos >= pixels.length || pos < 0) throw new ArrayIndexOutOfBoundsException("Index " + pos + " at x " + x + " and y " + y + " out of bounds for length " + pixels.length);
        this.pixels[(y * width) + x] = argb;
    }
    public void setRGB(int x, int y, int[] rgb) {
        this.pixels[(y * width) + x] = ((rgb[0] << 16) | (rgb[1] << 8) | (rgb[2]));
    }
    public void setHSV(int x, int y, double[] hsv) {
        setRGB(x, y, FrameParser.HSVtoRGB(hsv));
    }


    public boolean hasAlphaChannel() {
        return this.hasAlphaChannel;
    }
    public int getWidth() {
        return this.width;
    }
    public int getHeight() {
        return this.height;
    }
    public int getLength() {
        return this.width * this.height;
    }

    public int[] getPixels() {
        return this.pixels;
    }

    public BufferedImage getImage() {
        BufferedImage outputCel = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        outputCel.setData(Raster.createRaster(outputCel.getSampleModel(), new DataBufferInt(pixels, pixels.length), new Point()));
        return outputCel;
    }

    public FastRGB clone() {
        return new FastRGB(pixels.clone(), width, height, hasAlphaChannel);
    }

}

