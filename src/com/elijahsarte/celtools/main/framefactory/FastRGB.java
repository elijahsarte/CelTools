package com.elijahsarte.celtools.main.framefactory;

import com.elijahsarte.celtools.main.util.ConstructionEx;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;

import java.awt.*;
import java.awt.image.*;
import java.util.Arrays;

import static com.elijahsarte.celtools.main.util.CollectionsEx.of;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.varMutate;

// https://stackoverflow.com/a/26713029/15446511
// this class is for int[] pixels based off FastRGB based off stack overflow
public class FastRGB {

    private int width;
    private int height;
    private boolean hasAlphaChannel;
    private int[] pixels;

    private final int[] intColorArr = new int[3], aIntColorArr = new int[3];
    private static final int[] stIntColorArr = new int[3], stAIntColorArr = new int[3];

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

    public FastRGB(int pixel, int width, int height, boolean hasAlphaChannel) {
        this(of(ConstructionEx.getIntArray(width*height), pixel), width, height, hasAlphaChannel);
    }
    public FastRGB(Color color, int width, int height, boolean hasAlphaChannel) {
        this(
                hasAlphaChannel
                        ? color.getRGB()
                        : (color.getRGB() & 0x00FFFFFF),
                width,
                height,
                hasAlphaChannel
        );
    }
    public FastRGB(Color color, int width, int height) {
        this(color, width, height, true);
    }


    public FastRGB(int width, int height, boolean hasAlphaChannel) {
        this(ConstructionEx.getIntArray(width * height), width, height, hasAlphaChannel);
    }

    public FastRGB(ImageHandler imageHandler, boolean copy) {
        this(loadImage(imageHandler), copy);
    }
    public FastRGB(ImageHandler imageHandler) {
        this(imageHandler, true);
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
//        this(other.pixels.clone(), other.width, other.height, other.hasAlphaChannel);
        this(varMutate(ConstructionEx.getIntArray(other.pixels.length), i -> System.arraycopy(other.pixels, 0, i, 0, other.pixels.length)), other.width, other.height, other.hasAlphaChannel);
    }
/*
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
    }*/
    public static int[] extractPixels(BufferedImage image, boolean copy) {
        int w = image.getWidth();
        int h = image.getHeight();

        Raster raster = image.getRaster();
        DataBuffer dataBuffer = raster.getDataBuffer();
        SampleModel sampleModel = raster.getSampleModel();

        if (dataBuffer instanceof DataBufferInt buffer
                && sampleModel instanceof SinglePixelPackedSampleModel sm) {

            int[] data = buffer.getData();
            int scanlineStride = sm.getScanlineStride();

            /*
             * Raster coordinates are not necessarily raw-buffer coordinates.
             *
             * For getSubimage(...), the DataBuffer offset may still be 0.
             * The crop's actual position is usually represented by
             * sampleModelTranslateX/Y.
             */
            int sampleX0 = raster.getMinX() - raster.getSampleModelTranslateX();
            int sampleY0 = raster.getMinY() - raster.getSampleModelTranslateY();

            int base = buffer.getOffset() + sm.getOffset(sampleX0, sampleY0);

            boolean ownsWholeTightBuffer =
                    raster.getMinX() == 0
                            && raster.getMinY() == 0
                            && raster.getSampleModelTranslateX() == 0
                            && raster.getSampleModelTranslateY() == 0
                            && scanlineStride == w
                            && base == 0
                            && data.length == w * h;

            /*
             * Only return the backing array directly when this image is a full,
             * tightly packed owner of that exact array.
             */
            if (ownsWholeTightBuffer) {
                return copy ? data.clone() : data;
            }

            /*
             * Subimage / translated raster / view into larger image / padded stride.
             * Copy only this BufferedImage's visible rectangle into a compact array.
             */
            int[] out = ConstructionEx.getIntArray(w*h);

            for (int y = 0; y < h; y++) {
                int srcPos = base + y * scanlineStride;
                System.arraycopy(data, srcPos, out, y * w, w);
            }

            return out;
        }

        /*
         * Fallback for non-DataBufferInt images.
         * This is slower, but always gives a correct compact ARGB/RGB array.
         */
        return image.getRGB(0, 0, w, h, null, 0, w);
    }
public static int[] extractPixels(BufferedImage image) {
        return extractPixels(image, false);
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
    public static int getRGBRaw(double[] rgb) {
        return getRGBRaw(rgb.length == 4 ? new int[] { (int) rgb[0], (int) rgb[1], (int) rgb[2], (int) rgb[3] } : new int[] { (int) rgb[0], (int) rgb[1], (int) rgb[2] });
    }

    public int getRGBRaw(double x, double y) {
        return getRGBRaw((int) x, (int) y);
    }

    public static int[] getRGB(int rgb, int[] out) {
        out[0] = red(rgb);
        out[1] = green(rgb);
        out[2] = blue(rgb);
        return out;
    }
    public static int[] getRGB(int rgb) {
        return getRGB(rgb, stIntColorArr);
    }

    public static int[] getRGBA(int argb, int[] out) {
        out[0] = red(argb);
        out[1] = green(argb);
        out[2] = blue(argb);
        out[3] = alpha(argb);
        return out;
    }
    public static int[] getRGBA(int argb) {
        return getRGBA(argb, stAIntColorArr);
    }

    public int[] getRGB(int x, int y, int[] out) {
        return getRGB(getRGBRaw(x, y), out);
    }
    public int[] getRGB(int x, int y) {
        return getRGB(getRGBRaw(x, y), intColorArr);
    }

    public int[] getRGB(double x, double y) {
        return getRGB((int) x, (int) y);
    }

    public int[] getRGBA(int x, int y) {
        return getRGBA(getRGBRaw(x, y), aIntColorArr);
    }

    public double[] getHSV(int x, int y, double[] outHsv) {
        return FrameParser.RGBtoHSV(getRGB(x, y), outHsv);
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
        int rgb = other.getRGBRaw(x, y);

        if (other.hasAlphaChannel()) {
            if (this.hasAlphaChannel()) {
                setRGB(x, y, rgb); // preserve ARGB exactly
            } else {
                setRGB(x, y, rgb & 0x00FFFFFF); // strip alpha
            }
        } else {
            if (this.hasAlphaChannel()) {
                setRGB(x, y, 0xFF000000 | (rgb & 0x00FFFFFF)); // force opaque alpha
            } else {
                setRGB(x, y, rgb); // plain RGB
            }
        }
    }


    public void copyRectFrom(FastRGB src, int srcX, int srcY, int rectWidth, int rectHeight, int dstX, int dstY) {
        if (rectWidth <= 0 || rectHeight <= 0) {
            return;
        }

        int copySrcX = srcX;
        int copySrcY = srcY;
        int copyDstX = dstX;
        int copyDstY = dstY;
        int copyWidth = rectWidth;
        int copyHeight = rectHeight;

        if (copySrcX < 0) {
            int shift = -copySrcX;
            copySrcX = 0;
            copyDstX += shift;
            copyWidth -= shift;
        }
        if (copySrcY < 0) {
            int shift = -copySrcY;
            copySrcY = 0;
            copyDstY += shift;
            copyHeight -= shift;
        }
        if (copyDstX < 0) {
            int shift = -copyDstX;
            copyDstX = 0;
            copySrcX += shift;
            copyWidth -= shift;
        }
        if (copyDstY < 0) {
            int shift = -copyDstY;
            copyDstY = 0;
            copySrcY += shift;
            copyHeight -= shift;
        }

        copyWidth = Math.min(copyWidth, src.width - copySrcX);
        copyWidth = Math.min(copyWidth, this.width - copyDstX);
        copyHeight = Math.min(copyHeight, src.height - copySrcY);
        copyHeight = Math.min(copyHeight, this.height - copyDstY);

        if (copyWidth <= 0 || copyHeight <= 0) {
            return;
        }

        for (int row = 0; row < copyHeight; row++) {
            int srcPos = ((copySrcY + row) * src.width) + copySrcX;
            int dstPos = ((copyDstY + row) * this.width) + copyDstX;
            if (this.hasAlphaChannel && !src.hasAlphaChannel) {
                for (int i = 0; i < rectWidth; i++) {
                    this.pixels[dstPos + i] = src.pixels[srcPos + i] & 0x00ffffff;
                }
            } else if (!this.hasAlphaChannel && src.hasAlphaChannel) {
                for (int i = 0; i < rectWidth; i++) {
                    this.pixels[dstPos + i] = (src.pixels[srcPos + i] >>> 24) | (this.pixels[dstPos + i] & 0x00ffffff);
                }
            } else {
                System.arraycopy(src.pixels, srcPos, this.pixels, dstPos, copyWidth);
            }
        }
    }

    public void copyRowFrom(FastRGB src, int srcX, int srcY, int length, int dstX, int dstY) {
        copyRectFrom(src, srcX, srcY, length, 1, dstX, dstY);
    }

    public void copyColumnFrom(FastRGB src, int srcX, int srcY, int length, int dstX, int dstY) {
        if (length <= 0) {
            return;
        }

        int copySrcX = srcX;
        int copySrcY = srcY;
        int copyDstX = dstX;
        int copyDstY = dstY;
        int copyLength = length;

        if (copySrcX < 0 || copyDstX < 0 || copySrcX >= src.width || copyDstX >= this.width) {
            return;
        }

        if (copySrcY < 0) {
            int shift = -copySrcY;
            copySrcY = 0;
            copyDstY += shift;
            copyLength -= shift;
        }
        if (copyDstY < 0) {
            int shift = -copyDstY;
            copyDstY = 0;
            copySrcY += shift;
            copyLength -= shift;
        }

        copyLength = Math.min(copyLength, src.height - copySrcY);
        copyLength = Math.min(copyLength, this.height - copyDstY);

        if (copyLength <= 0) {
            return;
        }

        int srcPos = (copySrcY * src.width) + copySrcX;
        int dstPos = (copyDstY * this.width) + copyDstX;

        for (int i = 0; i < copyLength; i++) {
            this.pixels[dstPos] = src.pixels[srcPos];
            srcPos += src.width;
            dstPos += this.width;
        }
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

    public void imposeAlphaChannel() {
        if (hasAlphaChannel) return;
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (0xff << 24) | (pixels[i] & 0x00ffffff);
        }
        hasAlphaChannel = true;
    }

    public FastRGB withAlphaChannel() {
        if (hasAlphaChannel) {
            return copy();
        }

        int[] converted = ConstructionEx.getIntArray(pixels.length);
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

        pixels = newPixels;
        width = newWidth;
        height = newHeight;
    }

    public void rotate90Clockwise() {
        int[] rotated = ConstructionEx.getIntArray(pixels.length);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int newX = height - 1 - y;
                int newY = x;
                rotated[(newY * height) + newX] = pixels[(y * width) + x];
            }
        }

        int oldWidth = width;

        pixels = rotated;
        width = height;
        height = oldWidth;
    }

    public void rotate90CounterClockwise() {
        int[] rotated = ConstructionEx.getIntArray(pixels.length);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int newX = y;
                int newY = width - 1 - x;
                rotated[(newY * height) + newX] = pixels[(y * width) + x];
            }
        }

        int oldWidth = width;

        pixels = rotated;
        width = height;
        height = oldWidth;
    }

    public void rotate180() {
        int[] rotated = ConstructionEx.getIntArray(pixels.length);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int newX = width - 1 - x;
                int newY = height - 1 - y;
                rotated[(newY * width) + newX] = pixels[(y * width) + x];
            }
        }

        pixels = rotated;
    }

    public static FastRGB upscale(FastRGB rgb, double percentage) {
        return varMutate(new FastRGB(rgb), f -> f.upscale(percentage));
    }
    public static FastRGB rotate90Clockwise(FastRGB rgb) {
        return varMutate(new FastRGB(rgb), f -> f.rotate90Clockwise());
    }
    public static FastRGB rotate90CounterClockwise(FastRGB rgb) {
        return varMutate(new FastRGB(rgb), f -> f.rotate90CounterClockwise());
    }
    public static FastRGB rotate180(FastRGB rgb) {
        return varMutate(new FastRGB(rgb), f -> f.rotate180());
    }


    public void setWidth(int width) {
        this.width = width;
    }
    public void setHeight(int height) {
        this.height = height;
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

    public void release() {
        ConstructionEx.giveBackArray(pixels);
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
