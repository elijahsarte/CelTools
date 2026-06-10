package com.elijahsarte.celtools.main.imageworkshop;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class Layer {

    private String name;
    private FastRGB image;
    private Point offset = new Point();
    private BlendMode blendMode = BlendMode.NORMAL;
    private double opacity = 1.0;
    private boolean visible = true;
    private boolean clippedToPrevious;
    private LayerMask mask;

    public Layer(String name, FastRGB image, boolean copy) {
        this.name = name == null || name.isBlank() ? "Layer" : name;
        this.image = copy ? Filter.copyPixels(Objects.requireNonNull(image, "image")) : image;
    }
    public Layer(String name, FastRGB image) {
        this(name, image, true);
    }
    public Layer(String name, BufferedImage image, boolean copy) {
        this(name, copy ? Filter.copyPixels(image) : new FastRGB(image));
    }
    public Layer(String name, BufferedImage image) {
        this(name, image, true);
    }

    public Layer(String name, ImageHandler imageHandler) {
        this(name, Filter.copyPixels(imageHandler));
    }

    public Layer(FastRGB image, boolean copy) {
        this("Layer", image, copy);
    }
    public Layer(FastRGB image) {
        this(image, true);
    }

    public Layer(BufferedImage image) {
        this("Layer", image);
    }

    public Layer(int width, int height, String name) {
        this(name, Filter.transparentPixels(width, height));
    }

    public Layer() {
        this(1, 1, "Layer");
    }

    public Layer(Layer other) {
        this(other.name, other.image);
        this.offset = new Point(other.offset);
        this.blendMode = other.blendMode;
        this.opacity = other.opacity;
        this.visible = other.visible;
        this.clippedToPrevious = other.clippedToPrevious;
        this.mask = other.mask == null ? null : other.mask.copy();
    }

    public Layer copy() {
        return new Layer(this);
    }

    // Keeps public semantics safe, but now only does a single copy and mutates it in-place.
    FastRGB renderWithoutOffsetPixels(boolean includeOpacity) {
        FastRGB rendered = image.copy();

        if (mask != null) {
            mask.applyToInPlace(rendered);
        }

        if (includeOpacity && opacity < 1.0) {
            applyOpacityInPlace(rendered, opacity);
        }

        return rendered;
    }

    // New: render directly into an existing canvas with no intermediate FastRGB copies.
    void renderOnto(FastRGB canvas, boolean includeOpacity) {
        Objects.requireNonNull(canvas, "canvas");

        if (!visible) {
            return;
        }

        final int dstW = canvas.getWidth();
        final int dstH = canvas.getHeight();
        final int srcW = image.getWidth();
        final int srcH = image.getHeight();

        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
            return;
        }

        final int dstStartX = Math.max(0, offset.x);
        final int dstStartY = Math.max(0, offset.y);
        final int srcStartX = Math.max(0, -offset.x);
        final int srcStartY = Math.max(0, -offset.y);

        final int width = Math.min(srcW - srcStartX, dstW - dstStartX);
        final int height = Math.min(srcH - srcStartY, dstH - dstStartY);

        if (width <= 0 || height <= 0) {
            return;
        }

        final int[] dst = canvas.getPixels();
        final int[] src = image.getPixels();

        final boolean useOpacity = includeOpacity && opacity < 1.0;
        final int opacity255 = useOpacity ? (int) Math.round(opacity * 255.0) : 255;

        FastRGB maskImage = mask == null ? null : mask.pixels();
        final int[] maskPixels = maskImage == null ? null : maskImage.getPixels();
        final boolean useMask = maskPixels != null;
        final int maskW = maskImage == null ? 0 : maskImage.getWidth();

        // Fastest path: plain blit, no mask, no opacity math.
        if (!useMask && !useOpacity) {
            for (int y = 0; y < height; y++) {
                int srcRow = (srcStartY + y) * srcW + srcStartX;
                int dstRow = (dstStartY + y) * dstW + dstStartX;
                System.arraycopy(src, srcRow, dst, dstRow, width);
            }
            return;
        }

        for (int y = 0; y < height; y++) {
            int srcRow = (srcStartY + y) * srcW + srcStartX;
            int dstRow = (dstStartY + y) * dstW + dstStartX;
            int maskRow = useMask ? ((srcStartY + y) * maskW + srcStartX) : 0;

            for (int x = 0; x < width; x++) {
                int argb = src[srcRow + x];
                int alpha = FastRGB.alpha(argb);

                if (alpha == 0) {
                    continue;
                }

                if (useMask) {
                    alpha = (alpha * FastRGB.alpha(maskPixels[maskRow + x]) + 127) / 255;
                    if (alpha == 0) {
                        continue;
                    }
                }

                if (useOpacity) {
                    alpha = (alpha * opacity255 + 127) / 255;
                    if (alpha == 0) {
                        continue;
                    }
                }

                dst[dstRow + x] = alpha == FastRGB.alpha(argb)
                        ? argb
                        : Filter.withAlpha(argb, alpha);
            }
        }
    }

    FastRGB renderToCanvasPixels(int documentWidth, int documentHeight) {
        FastRGB canvas = Filter.transparentPixels(documentWidth, documentHeight);
        renderOnto(canvas, true);
        return canvas;
    }

    public BufferedImage renderWithoutOffset(boolean includeOpacity) {
        return renderWithoutOffsetPixels(includeOpacity).getImage();
    }

    public BufferedImage renderToCanvas(int documentWidth, int documentHeight) {
        FastRGB canvas = Filter.transparentPixels(documentWidth, documentHeight);
        renderOnto(canvas, true);
        return canvas.getImage();
    }

    // Useful for whole-document flatten/export.
    public static FastRGB flattenToCanvasPixels(java.util.List<Layer> layers, int documentWidth, int documentHeight) {
        FastRGB canvas = Filter.transparentPixels(documentWidth, documentHeight);
        if (layers == null || layers.isEmpty()) {
            return canvas;
        }

        for (Layer layer : layers) {
            if (layer == null || !layer.isVisible()) {
                continue;
            }
            layer.renderOnto(canvas, true);
        }

        return canvas;
    }

    public static BufferedImage flattenToCanvas(java.util.List<Layer> layers, int documentWidth, int documentHeight) {
        return flattenToCanvasPixels(layers, documentWidth, documentHeight).getImage();
    }

    private static void applyOpacityInPlace(FastRGB target, double opacity) {
        if (opacity >= 1.0) {
            return;
        }
        if (opacity <= 0.0) {
            target.fill(0);
            return;
        }

        int[] pixels = target.getPixels();
        int opacity255 = (int) Math.round(opacity * 255.0);

        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int alpha = FastRGB.alpha(argb);
            if (alpha == 0) {
                continue;
            }
            int newAlpha = (alpha * opacity255 + 127) / 255;
            pixels[i] = newAlpha == alpha ? argb : Filter.withAlpha(argb, newAlpha);
        }
    }

    public Layer applyFilter(UnaryOperator<FastRGB> operation) {
        setPixels(operation.apply(getPixels()));
        return this;
    }

    public Layer applyMask() {
        if (mask == null) {
            return this;
        }
        mask.applyToInPlace(this.image);
        this.mask = null;
        return this;
    }

    public Layer fill(Color color) {
        Objects.requireNonNull(color, "color");
        image.fill(Filter.argb(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue()));
        return this;
    }

    public Rectangle bounds() {
        return new Rectangle(offset.x, offset.y, image.getWidth(), image.getHeight());
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    public String getName() {
        return name;
    }

    public Layer setName(String name) {
        this.name = name == null || name.isBlank() ? "Layer" : name;
        return this;
    }

    public FastRGB getPixels() {
        return image.copy();
    }

    FastRGB pixels() {
        return image;
    }

    public BufferedImage exportImage() {
        return image.getImage();
    }

    public ImageHandler exportHandler() {
        return new ImageHandler(exportImage());
    }

    public BufferedImage getImage() {
        return exportImage();
    }

    public Layer setPixels(FastRGB image) {
        Objects.requireNonNull(image, "image");
        this.image = Filter.copyPixels(image);
        if (mask != null && (mask.getWidth() != image.getWidth() || mask.getHeight() != image.getHeight())) {
            this.mask = null;
        }
        return this;
    }

    public Layer setImage(BufferedImage image) {
        return setPixels(Filter.copyPixels(image));
    }

    public Point getOffset() {
        return new Point(offset);
    }

    public Layer setOffset(int x, int y) {
        this.offset = new Point(x, y);
        return this;
    }

    public Layer translate(int dx, int dy) {
        this.offset.translate(dx, dy);
        return this;
    }

    public BlendMode getBlendMode() {
        return blendMode;
    }

    public Layer setBlendMode(BlendMode blendMode) {
        this.blendMode = Objects.requireNonNull(blendMode, "blendMode");
        return this;
    }

    public double getOpacity() {
        return opacity;
    }

    public Layer setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    public Layer setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean isClippedToPrevious() {
        return clippedToPrevious;
    }

    public Layer setClippedToPrevious(boolean clippedToPrevious) {
        this.clippedToPrevious = clippedToPrevious;
        return this;
    }

    public boolean hasMask() {
        return mask != null;
    }

    public LayerMask getMask() {
        return mask == null ? null : mask.copy();
    }

    public Layer setMask(LayerMask mask) {
        if (mask != null && (mask.getWidth() != image.getWidth() || mask.getHeight() != image.getHeight())) {
            throw new IllegalArgumentException("Layer mask must match layer image dimensions");
        }
        this.mask = mask == null ? null : mask.copy();
        return this;
    }
}