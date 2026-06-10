package com.elijahsarte.celtools.main.framefactory;

import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;

import java.awt.image.*;
import java.util.Arrays;

// https://stackoverflow.com/a/26713029/15446511
// this class is for int[] pixels based off FastRGB based off stack overflow
public class FastRGB {

    private int width;
    private int height;
    private boolean hasAlphaChannel;
    private int[] pixels;

    public FastRGB(int[] pixels, int width, int height, boolean hasAlphaChannel) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("FastRGB dimensions must be positive");
        }
        if (pixels.length < width * height) {
            throw new IllegalArgumentException("Provided pixel buffer is smaller than width * height");
        }

        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.hasAlphaChannel = hasAlphaChannel;
    }

    public FastRGB(int width, int height, boolean hasAlphaChannel) {
        this(new int[width * height], width, height, hasAlphaChannel);
    }

    public FastRGB(ImageHandler imageHandler) {
        this(loadImage(imageHandler));
    }

    public FastRGB(BufferedImage image, boolean copy) {
        this(
                //image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()),
                extractPixels(image, copy),
                image.getWidth(),
                image.getHeight(),
                image.getColorModel().hasAlpha()
        );
    }
    public FastRGB(BufferedImage image) {
        this(image, true);
    }

    public FastRGB(FastRGB other) {
        this(other.pixels.clone(), other.width, other.height, other.hasAlphaChannel);
    }

    public static int[] extractPixels(BufferedImage image, boolean copy) {
        if (image.getRaster().getDataBuffer() instanceof DataBufferInt buffer) {
            return copy ? buffer.getData().clone() : buffer.getData();
        }

        return image.getRGB(
                0,
                0,
                image.getWidth(),
                image.getHeight(),
                null,
                0,
                image.getWidth()
        );
    }
    public static int[] extractPixels(BufferedImage image) {
        return extractPixels(image, true);
    }

    private static BufferedImage loadImage(ImageHandler imageHandler) {
        ProgrammingEx.noExcept(imageHandler::loadImage);
        if (imageHandler.getImage() == null) {
            throw new IllegalArgumentException("ImageHandler does not contain a loaded image");
        }
        return imageHandler.getImage();
    }

    public int getRGBRaw(int x, int y) {
        return pixels[index(x, y)];
    }

    public static int getRGBRaw(int[] rgb) {
        return ProgrammingEx.varOper(
                OptionalEx.ofCond(rgb, rgb.length == 3).orElse(new int[] { rgb[0], rgb[1], rgb[2], 255 }),
                arr -> ((arr[3] & 0xff) << 24) | ((arr[0] & 0xff) << 16) | ((arr[1] & 0xff) << 8) | (arr[2] & 0xff)
        );
    }

    public int getRGBRaw(double x, double y) {
        return getRGBRaw((int) x, (int) y);
    }

    public static int[] getRGB(int rgb) {
        return new int[] { red(rgb), green(rgb), blue(rgb) };
    }

    public static int[] getRGBA(int argb) {
        return new int[] { red(argb), green(argb), blue(argb), alpha(argb) };
    }

    public int[] getRGB(int x, int y) {
        return getRGB(getRGBRaw(x, y));
    }

    public int[] getRGB(double x, double y) {
        return getRGB((int) x, (int) y);
    }

    public int[] getRGBA(int x, int y) {
        return getRGBA(getRGBRaw(x, y));
    }

    public double[] getHSV(int x, int y) {
        return FrameParser.RGBtoHSV(getRGB(x, y));
    }

    public double[] getHSV(double x, double y) {
        return getHSV((int) x, (int) y);
    }

    public int getAlpha(int x, int y) {
        return alpha(getRGBRaw(x, y));
    }

    public int getRed(int x, int y) {
        return red(getRGBRaw(x, y));
    }

    public int getGreen(int x, int y) {
        return green(getRGBRaw(x, y));
    }

    public int getBlue(int x, int y) {
        return blue(getRGBRaw(x, y));
    }

    public void setRGB(int x, int y, int argb) {
        pixels[index(x, y)] = hasAlphaChannel ? argb : (argb & 0x00ffffff);
    }
/*
    public void setRGB(int x, int y, int[] rgb) {
        int existingAlpha = hasAlphaChannel ? getAlpha(x, y) : 255;
        int argb = getRGBRaw(rgb.length == 4 ? rgb : new int[] { rgb[0], rgb[1], rgb[2], existingAlpha });
        setRGB(x, y, argb);
    }*/
    public void setRGB(int x, int y, int[] rgb) {
        int alpha = rgb.length == 4 ? rgb[3] : (hasAlphaChannel ? getAlpha(x, y) : 255);

        setRGB(
                x,
                y,
                ((alpha & 0xff) << 24)
                        | ((rgb[0] & 0xff) << 16)
                        | ((rgb[1] & 0xff) << 8)
                        | (rgb[2] & 0xff)
        );
    }

    public void setRGB(int x, int y, FastRGB other) {
        setRGB(x, y, other.getRGBRaw(x, y));
    }

    public void setHSV(int x, int y, double[] hsv) {
        setRGB(x, y, FrameParser.HSVtoRGB(hsv));
    }

    public void fill(int argb) {
        Arrays.fill(pixels, hasAlphaChannel ? argb : (argb & 0x00ffffff));
    }

    public boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public int index(int x, int y) {
        int pos = (y * width) + x;
        if (pos >= pixels.length || pos < 0 || x < 0 || y < 0 || x >= width || y >= height) {
            throw new ArrayIndexOutOfBoundsException(
                    "Index " + pos + " at x " + x + " and y " + y + " out of bounds for length " + pixels.length +
                            ", width " + width + " and height " + height
            );
        }
        return pos;
    }

    public FastRGB withAlphaChannel() {
        if (hasAlphaChannel) {
            return copy();
        }

        int[] converted = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            converted[i] = (0xff << 24) | (pixels[i] & 0x00ffffff);
        }
        return new FastRGB(converted, width, height, true);
    }

    public void upscale(double percentage) {
        if (percentage <= 0) {
            throw new IllegalArgumentException("Scale percentage must be positive");
        }

        double scaleFactor = percentage / 100.0;

        int newWidth = (int) Math.round(width * scaleFactor);
        int newHeight = (int) Math.round(height * scaleFactor);
        int[] newPixels = new int[newWidth * newHeight];

        for (int y = 0; y < newHeight; y++) {
            int sourceY = (int) (y / scaleFactor);
            if (sourceY >= height) {
                sourceY = height - 1;
            }

            for (int x = 0; x < newWidth; x++) {
                int sourceX = (int) (x / scaleFactor);
                if (sourceX >= width) {
                    sourceX = width - 1;
                }

                newPixels[(y * newWidth) + x] = pixels[(sourceY * width) + sourceX];
            }
        }

        this.width = newWidth;
        this.height = newHeight;
        this.pixels = newPixels;
    }

    public boolean hasAlphaChannel() {
        return hasAlphaChannel;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLength() {
        return width * height;
    }

    public int[] getPixels() {
        return pixels;
    }
/*
    public BufferedImage getImage() {
        int type = hasAlphaChannel ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage outputImage = new BufferedImage(width, height, type);
        outputImage.setRGB(0, 0, width, height, pixels, 0, width);
        return outputImage;
    }*/
public BufferedImage getImage() {
    int type = hasAlphaChannel ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

    // 1. Wrap your existing pixels array directly into a DataBuffer
    DataBufferInt buffer = new DataBufferInt(pixels, pixels.length);

    // 2. Define the color bitmasks based on your channel type
    DirectColorModel colorModel;
    WritableRaster raster;

    if (hasAlphaChannel) {
        // ARGB masks: Alpha (24-bit shift), Red (16), Green (8), Blue (0)
        colorModel = new DirectColorModel(32, 0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000);
        raster = Raster.createPackedRaster(buffer, width, height, width,
                new int[]{0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000}, null);
    } else {
        // RGB masks: Red (16), Green (8), Blue (0)
        colorModel = new DirectColorModel(24, 0x00ff0000, 0x0000ff00, 0x000000ff);
        raster = Raster.createPackedRaster(buffer, width, height, width,
                new int[]{0x00ff0000, 0x0000ff00, 0x000000ff}, null);
    }

    // 3. Construct the image instantly using the underlying array
    return new BufferedImage(colorModel, raster, false, null);
}

    public FastRGB copy() {
        return new FastRGB(this);
    }

    @Override
    public FastRGB clone() {
        return copy();
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xff;
    }

    public static int red(int argb) {
        return (argb >>> 16) & 0xff;
    }

    public static int green(int argb) {
        return (argb >>> 8) & 0xff;
    }

    public static int blue(int argb) {
        return argb & 0xff;
    }
}
