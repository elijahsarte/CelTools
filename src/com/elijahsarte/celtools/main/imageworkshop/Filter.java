package com.elijahsarte.celtools.main.imageworkshop;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.ProgrammingEx;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class Filter {

    private Filter() {
    }

    public static Layer apply(Layer layer, UnaryOperator<FastRGB> operation) {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(operation, "operation");
        layer.setPixels(operation.apply(layer.getPixels()));
        return layer;
    }

    public static FastRGB apply(FastRGB image, UnaryOperator<FastRGB> operation) {
        Objects.requireNonNull(operation, "operation");
        return copyPixels(operation.apply(copyPixels(image)));
    }

    public static BufferedImage apply(BufferedImage image, UnaryOperator<FastRGB> operation) {
        return apply(copyPixels(image), operation).getImage();
    }

    public static BufferedImage apply(ImageHandler imageHandler, UnaryOperator<FastRGB> operation) {
        return apply(copyPixels(imageHandler), operation).getImage();
    }

    public static FastRGB copy(FastRGB image) {
        return copyPixels(image);
    }

    public static BufferedImage copy(ImageHandler imageHandler) {
//        return copyPixels(imageHandler).getImage();
        try {
            return imageHandler.copy().getImage();
        } catch (Exception e) {
            return copyPixels(imageHandler).getImage();
        }
    }

    public static BufferedImage copy(BufferedImage image) {
        return copyPixels(image).getImage();
    }

    public static FastRGB copyPixels(ImageHandler imageHandler) {
        Objects.requireNonNull(imageHandler, "imageHandler");
        ProgrammingEx.noExcept(imageHandler::loadImage);
        if (imageHandler.getImage() == null) {
            throw new IllegalArgumentException("ImageHandler does not contain a loaded image");
        }
        return copyPixels(imageHandler.getImage());
    }

    public static FastRGB copyPixels(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        return new FastRGB(image).withAlphaChannel();
    }

    public static FastRGB copyPixels(FastRGB image) {
        Objects.requireNonNull(image, "image");
        return image.withAlphaChannel();
    }

    public static FastRGB transparentPixels(int width, int height) {
        return new FastRGB(width, height, true);
    }

    public static BufferedImage transparentCanvas(int width, int height) {
        return transparentPixels(width, height).getImage();
    }

    public static FastRGB withOpacity(FastRGB image, double opacity) {
        double boundedOpacity = Math.max(0.0, Math.min(1.0, opacity));
        FastRGB adjusted = copyPixels(image);
        int[] pixels = adjusted.getPixels();

        if (boundedOpacity >= 1.0) {
            return adjusted;
        }

        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int newAlpha = (int) Math.round(FastRGB.alpha(argb) * boundedOpacity);
            pixels[i] = withAlpha(argb, newAlpha);
        }
        return adjusted;
    }

    public static BufferedImage withOpacity(BufferedImage image, double opacity) {
        return withOpacity(copyPixels(image), opacity).getImage();
    }

    public static FastRGB average(FastRGB image) {
        FastRGB source = copyPixels(image);
        int[] pixels = source.getPixels();
        long pixelCount = pixels.length;

        if (pixelCount == 0) {
            return transparentPixels(source.getWidth(), source.getHeight());
        }

        long alphaSum = 0L;
        long redSum = 0L;
        long greenSum = 0L;
        long blueSum = 0L;

        for (int pixel : pixels) {
            redSum += FastRGB.red(pixel);
            greenSum += FastRGB.green(pixel);
            blueSum += FastRGB.blue(pixel);
            alphaSum += FastRGB.alpha(pixel);
        }

        int averageAlpha = clamp((double) alphaSum / pixelCount);
        int averageRed = clamp((double) redSum / pixelCount);
        int averageGreen = clamp((double) greenSum / pixelCount);
        int averageBlue = clamp((double) blueSum / pixelCount);

        FastRGB filtered = transparentPixels(source.getWidth(), source.getHeight());
        filtered.fill(argb(averageAlpha, averageRed, averageGreen, averageBlue));
        return filtered;
    }

    public static BufferedImage average(BufferedImage image) {
        return average(copyPixels(image)).getImage();
    }

    public static BufferedImage average(ImageHandler imageHandler) {
        return average(copyPixels(imageHandler)).getImage();
    }

    public static Layer average(Layer layer) {
        return apply(layer, Filter::average);
    }

    public static FastRGB applyAlphaMask(FastRGB image, FastRGB alphaMask) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(alphaMask, "alphaMask");

        FastRGB masked = copyPixels(image);
        int[] sourcePixels = masked.getPixels();
        int[] maskPixels = alphaMask.getPixels();
        int maskWidth = alphaMask.getWidth();
        int maskHeight = alphaMask.getHeight();

        for (int y = 0; y < masked.getHeight(); y++) {
            int rowOffset = y * masked.getWidth();
            for (int x = 0; x < masked.getWidth(); x++) {
                int index = rowOffset + x;
                int maskAlpha = x < maskWidth && y < maskHeight
                        ? FastRGB.alpha(maskPixels[(y * maskWidth) + x])
                        : 0;
                int srcArgb = sourcePixels[index];
                int newAlpha = (FastRGB.alpha(srcArgb) * maskAlpha) / 255;
                sourcePixels[index] = withAlpha(srcArgb, newAlpha);
            }
        }
        return masked;
    }

    public static BufferedImage applyAlphaMask(BufferedImage image, BufferedImage alphaMask) {
        return applyAlphaMask(copyPixels(image), copyPixels(alphaMask)).getImage();
    }

    public static FastRGB grayscale(FastRGB image) {
        FastRGB filtered = copyPixels(image);
        int[] pixels = filtered.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int gray = clamp((0.299 * FastRGB.red(argb)) + (0.587 * FastRGB.green(argb)) + (0.114 * FastRGB.blue(argb)));
            pixels[i] = argb(FastRGB.alpha(argb), gray, gray, gray);
        }
        return filtered;
    }

    public static BufferedImage grayscale(BufferedImage image) {
        return grayscale(copyPixels(image)).getImage();
    }

    public static BufferedImage grayscale(ImageHandler imageHandler) {
        return grayscale(copyPixels(imageHandler)).getImage();
    }

    public static Layer grayscale(Layer layer) {
        return apply(layer, Filter::grayscale);
    }

    public static FastRGB invert(FastRGB image) {
        FastRGB filtered = copyPixels(image);
        int[] pixels = filtered.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            pixels[i] = argb(
                    FastRGB.alpha(argb),
                    255 - FastRGB.red(argb),
                    255 - FastRGB.green(argb),
                    255 - FastRGB.blue(argb)
            );
        }
        return filtered;
    }

    public static BufferedImage invert(BufferedImage image) {
        return invert(copyPixels(image)).getImage();
    }

    public static BufferedImage invert(ImageHandler imageHandler) {
        return invert(copyPixels(imageHandler)).getImage();
    }

    public static Layer invert(Layer layer) {
        return apply(layer, Filter::invert);
    }

    public static FastRGB threshold(FastRGB image, int threshold) {
        int boundedThreshold = clamp(threshold);
        FastRGB filtered = copyPixels(image);
        int[] pixels = filtered.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int gray = clamp((0.299 * FastRGB.red(argb)) + (0.587 * FastRGB.green(argb)) + (0.114 * FastRGB.blue(argb)));
            int value = gray >= boundedThreshold ? 255 : 0;
            pixels[i] = argb(FastRGB.alpha(argb), value, value, value);
        }
        return filtered;
    }

    public static BufferedImage threshold(BufferedImage image, int threshold) {
        return threshold(copyPixels(image), threshold).getImage();
    }

    public static BufferedImage threshold(ImageHandler imageHandler, int threshold) {
        return threshold(copyPixels(imageHandler), threshold).getImage();
    }

    public static Layer threshold(Layer layer, int threshold) {
        return apply(layer, image -> threshold(image, threshold));
    }

    public static FastRGB brightnessContrast(FastRGB image, double brightnessOffset, double contrastMultiplier) {
        FastRGB filtered = copyPixels(image);
        double boundedContrast = Math.max(0.0, contrastMultiplier);
        int[] pixels = filtered.getPixels();

        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int red = clamp((((FastRGB.red(argb) - 127.5) * boundedContrast) + 127.5) + brightnessOffset);
            int green = clamp((((FastRGB.green(argb) - 127.5) * boundedContrast) + 127.5) + brightnessOffset);
            int blue = clamp((((FastRGB.blue(argb) - 127.5) * boundedContrast) + 127.5) + brightnessOffset);
            pixels[i] = argb(FastRGB.alpha(argb), red, green, blue);
        }
        return filtered;
    }

    public static BufferedImage brightnessContrast(BufferedImage image, double brightnessOffset, double contrastMultiplier) {
        return brightnessContrast(copyPixels(image), brightnessOffset, contrastMultiplier).getImage();
    }

    public static BufferedImage brightnessContrast(ImageHandler imageHandler, double brightnessOffset, double contrastMultiplier) {
        return brightnessContrast(copyPixels(imageHandler), brightnessOffset, contrastMultiplier).getImage();
    }

    public static Layer brightnessContrast(Layer layer, double brightnessOffset, double contrastMultiplier) {
        return apply(layer, image -> brightnessContrast(image, brightnessOffset, contrastMultiplier));
    }

    public static FastRGB boxBlur(FastRGB image, int radius) {
        if (radius <= 0) {
            return copyPixels(image);
        }
        int size = (radius * 2) + 1;
        float[] kernel = new float[size * size];
        float weight = 1.0f / kernel.length;
        java.util.Arrays.fill(kernel, weight);
        return convolve(image, kernel, size);
    }

    public static BufferedImage boxBlur(BufferedImage image, int radius) {
        return boxBlur(copyPixels(image), radius).getImage();
    }

    public static BufferedImage boxBlur(ImageHandler imageHandler, int radius) {
        return boxBlur(copyPixels(imageHandler), radius).getImage();
    }

    public static Layer boxBlur(Layer layer, int radius) {
        return apply(layer, image -> boxBlur(image, radius));
    }

    public static FastRGB gaussianBlur(FastRGB image, int radius) {
        if (radius <= 0) {
            return copyPixels(image);
        }
        int size = (radius * 2) + 1;
        float[] kernel = gaussianKernel(radius);
        return convolve(image, kernel, size);
    }

    public static BufferedImage gaussianBlur(BufferedImage image, int radius) {
        return gaussianBlur(copyPixels(image), radius).getImage();
    }

    public static BufferedImage gaussianBlur(ImageHandler imageHandler, int radius) {
        return gaussianBlur(copyPixels(imageHandler), radius).getImage();
    }

    public static Layer gaussianBlur(Layer layer, int radius) {
        return apply(layer, image -> gaussianBlur(image, radius));
    }

    public static FastRGB sharpen(FastRGB image) {
        return convolve(image, new float[] {
                0f, -1f, 0f,
                -1f, 5f, -1f,
                0f, -1f, 0f
        }, 3);
    }

    public static BufferedImage sharpen(BufferedImage image) {
        return sharpen(copyPixels(image)).getImage();
    }

    public static BufferedImage sharpen(ImageHandler imageHandler) {
        return sharpen(copyPixels(imageHandler)).getImage();
    }

    public static Layer sharpen(Layer layer) {
        return apply(layer, Filter::sharpen);
    }

    private static FastRGB convolve(FastRGB image, float[] kernel, int kernelSize) {
        Objects.requireNonNull(image, "image");
        FastRGB source = copyPixels(image);
        FastRGB filtered = transparentPixels(source.getWidth(), source.getHeight());
        int radius = kernelSize / 2;
        int[] sourcePixels = source.getPixels();
        int[] filteredPixels = filtered.getPixels();
        int width = source.getWidth();
        int height = source.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double red = 0.0;
                double green = 0.0;
                double blue = 0.0;
                double alpha = 0.0;

                for (int kernelY = 0; kernelY < kernelSize; kernelY++) {
                    for (int kernelX = 0; kernelX < kernelSize; kernelX++) {
                        int sampleX = clampIndex(x + kernelX - radius, width);
                        int sampleY = clampIndex(y + kernelY - radius, height);
                        int sample = sourcePixels[(sampleY * width) + sampleX];
                        float weight = kernel[(kernelY * kernelSize) + kernelX];
                        red += FastRGB.red(sample) * weight;
                        green += FastRGB.green(sample) * weight;
                        blue += FastRGB.blue(sample) * weight;
                        alpha += FastRGB.alpha(sample) * weight;
                    }
                }

                filteredPixels[(y * width) + x] = argb(clamp(alpha), clamp(red), clamp(green), clamp(blue));
            }
        }
        return filtered;
    }

    private static float[] gaussianKernel(int radius) {
        int size = (radius * 2) + 1;
        float[] kernel = new float[size * size];
        double sigma = Math.max(1.0, radius / 2.0);
        double sigma2 = 2.0 * sigma * sigma;
        double sum = 0.0;

        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                double value = Math.exp(-((x * x) + (y * y)) / sigma2);
                int index = ((y + radius) * size) + (x + radius);
                kernel[index] = (float) value;
                sum += value;
            }
        }

        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= (float) sum;
        }
        return kernel;
    }

    private static int clampIndex(int index, int size) {
        if (index < 0) {
            return 0;
        }
        if (index >= size) {
            return size - 1;
        }
        return index;
    }

    public static int alpha(int argb) {
        return FastRGB.alpha(argb);
    }

    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00ffffff) | (clamp(alpha) << 24);
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return ((clamp(alpha) & 0xff) << 24) |
                ((clamp(red) & 0xff) << 16) |
                ((clamp(green) & 0xff) << 8) |
                (clamp(blue) & 0xff);
    }

    public static int clamp(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(255.0, value)));
    }

    public static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
