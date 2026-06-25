package com.elijahsarte.celtools.main.imageworkshop;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.MathEx;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private final List<LayerMask> masks = new ArrayList<>();

    public Layer(String name, FastRGB image, boolean copy) {
        this.name = name == null || name.isBlank() ? "Layer" : name;
        this.image = copy ? Filter.copyPixels(Objects.requireNonNull(image, "image")) : image;
    }

    public Layer(String name, FastRGB image) {
        this(name, image, true);
    }

    public Layer(String name, BufferedImage image, boolean copy) {
        this(name, copy ? Filter.copyPixels(image) : new FastRGB(image, copy));
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
        for (LayerMask mask : other.masks) {
            this.masks.add(mask.copy());
        }
    }

    public Layer copy() {
        return new Layer(this);
    }

    FastRGB renderWithoutOffsetPixels(boolean includeOpacity) {
        FastRGB rendered = image.copy();

        applyEffectiveMaskInPlace(rendered, buildEffectiveMaskAlphas());

        if (includeOpacity && opacity < 1.0) {
            applyOpacityInPlace(rendered, opacity);
        }

        return rendered;
    }
    public BufferedImage renderWithoutOffset(boolean includeOpacity) {
        return renderWithoutOffsetPixels(includeOpacity).getImage();
    }
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

        final int maskCount = masks.size();
        final int[] singleMaskPixels = maskCount == 1
                ? masks.get(0).pixels().getPixels()
                : null;
        final int[][] maskPixelArrays;
        if (maskCount > 1) {
            maskPixelArrays = new int[maskCount][];
            for (int i = 0; i < maskCount; i++) {
                maskPixelArrays[i] = masks.get(i).pixels().getPixels();
            }
        } else {
            maskPixelArrays = null;
        }

        for (int y = 0; y < height; y++) {
            int srcRow = (srcStartY + y) * srcW + srcStartX;
            int dstRow = (dstStartY + y) * dstW + dstStartX;

            for (int x = 0; x < width; x++) {
                int srcIndex = srcRow + x;
                int argb = src[srcIndex];
                int alpha = FastRGB.alpha(argb);

                if (alpha == 0) {
                    continue;
                }

                if (maskCount != 0) {
                    int maskAlpha;
                    if (maskCount == 1) {
                        maskAlpha = singleMaskPixels[srcIndex] >>> 24;
                    } else {
                        maskAlpha = 0;
                        for (int maskIndex = 0; maskIndex < maskCount; maskIndex++) {
                            int candidate = maskPixelArrays[maskIndex][srcIndex] >>> 24;
                            if (candidate > maskAlpha) {
                                maskAlpha = candidate;
                                if (maskAlpha == 255) {
                                    break;
                                }
                            }
                        }
                    }
                    alpha = (alpha * maskAlpha + 127) / 255;
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

                int source = alpha == FastRGB.alpha(argb) ? argb : Filter.withAlpha(argb, alpha);
                int dstIndex = dstRow + x;
                dst[dstIndex] = BlendMode.NORMAL.composite(dst[dstIndex], source);
            }
        }
    }
    FastRGB renderToCanvasPixels(int documentWidth, int documentHeight) {
        FastRGB canvas = Filter.transparentPixels(documentWidth, documentHeight);
        renderOnto(canvas, true);
        return canvas;
    }

    /**
     * Composites this layer directly into an existing document canvas.  This
     * avoids allocating a document-sized temporary image and then scanning the
     * entire document a second time merely to composite that temporary.
     *
     * @param clipAlphas     optional document-sized alpha mask used by clipping
     *                       layers
     * @param renderedAlphas optional document-sized destination for this
     *                       layer's final alpha (used as a clipping-group base)
     */
    void compositeOnto(
            FastRGB canvas,
            BlendMode mode,
            int[] clipAlphas,
            int[] renderedAlphas
    ) {
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(mode, "mode");

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
        final int drawWidth = Math.min(srcW - srcStartX, dstW - dstStartX);
        final int drawHeight = Math.min(srcH - srcStartY, dstH - dstStartY);

        if (drawWidth <= 0 || drawHeight <= 0) {
            return;
        }

        final int pixelCount = dstW * dstH;
        if (clipAlphas != null && clipAlphas.length < pixelCount) {
            throw new IllegalArgumentException("clipAlphas is smaller than the canvas");
        }
        if (renderedAlphas != null && renderedAlphas.length < pixelCount) {
            throw new IllegalArgumentException("renderedAlphas is smaller than the canvas");
        }

        final int[] dst = canvas.getPixels();
        final int[] src = image.getPixels();
        // Do not build a full image-sized effective-mask array here. Mask
        // evaluation is fused into the already-required compositing pass and
        // therefore touches only pixels inside the on-canvas intersection.
        final int maskCount = masks.size();
        final int[] singleMaskPixels = maskCount == 1
                ? masks.get(0).pixels().getPixels()
                : null;
        final int[][] maskPixelArrays;
        if (maskCount > 1) {
            maskPixelArrays = new int[maskCount][];
            for (int i = 0; i < maskCount; i++) {
                maskPixelArrays[i] = masks.get(i).pixels().getPixels();
            }
        } else {
            maskPixelArrays = null;
        }
        final boolean useOpacity = opacity < 1.0;
        final int opacity255 = useOpacity ? MathEx.roundInt(opacity * 255.0) : 255;

        for (int y = 0; y < drawHeight; y++) {
            int srcIndex = (srcStartY + y) * srcW + srcStartX;
            int dstIndex = (dstStartY + y) * dstW + dstStartX;
            int rowEnd = srcIndex + drawWidth;

            while (srcIndex < rowEnd) {
                int argb = src[srcIndex];
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    if (renderedAlphas != null) renderedAlphas[dstIndex] = alpha;
                    srcIndex++;
                    dstIndex++;
                    continue;
                }

                if (maskCount != 0) {
                    int maskAlpha;
                    if (maskCount == 1) {
                        maskAlpha = singleMaskPixels[srcIndex] >>> 24;
                    } else {
                        maskAlpha = 0;
                        for (int maskIndex = 0; maskIndex < maskCount; maskIndex++) {
                            int candidate = maskPixelArrays[maskIndex][srcIndex] >>> 24;
                            if (candidate > maskAlpha) {
                                maskAlpha = candidate;
                                if (maskAlpha == 255) {
                                    break;
                                }
                            }
                        }
                    }
                    alpha = MathEx.div255(alpha * maskAlpha + 127);
                }
                if (alpha != 0) {
                    if (useOpacity) {
                        alpha = MathEx.div255(alpha * opacity255 + 127);
                    }
                    if (clipAlphas != null && alpha != 0) {
                        alpha = MathEx.div255(alpha * clipAlphas[dstIndex]);
                    }
                }

                if (renderedAlphas != null) {
                    renderedAlphas[dstIndex] = alpha;
                }

                if (alpha != 0) {
                    int source = alpha == (argb >>> 24)
                            ? argb
                            : (argb & 0x00ffffff) | (alpha << 24);
                    int backdrop = dst[dstIndex];

                    // These two common cases avoid the considerably more
                    // expensive blend-mode dispatch and channel arithmetic.
                    if ((backdrop >>> 24) == 0) {
                        dst[dstIndex] = source;
                    } else {
                        dst[dstIndex] = mode.composite(backdrop, source);
                    }
                }

                srcIndex++;
                dstIndex++;
            }
        }
    }

    public BufferedImage renderToCanvas(int documentWidth, int documentHeight) {
        return renderToCanvasPixels(documentWidth, documentHeight).getImage();
    }

    public static FastRGB flattenToCanvasPixels(List<Layer> layers, int documentWidth, int documentHeight) {
        FastRGB canvas = Filter.transparentPixels(documentWidth, documentHeight);

        if (layers == null || layers.isEmpty()) {
            return canvas;
        }

        for (Layer layer : layers) {
            if (layer == null || !layer.isVisible()) {
                continue;
            }

            FastRGB rendered = layer.renderToCanvasPixels(documentWidth, documentHeight);
            compositeInto(canvas, rendered, layer.getBlendMode());
        }

        return canvas;
    }

    public static BufferedImage flattenToCanvas(List<Layer> layers, int documentWidth, int documentHeight) {
        return flattenToCanvasPixels(layers, documentWidth, documentHeight).getImage();
    }

    private static void compositeInto(FastRGB backdrop, FastRGB source, BlendMode blendMode) {
        int[] backdropPixels = backdrop.getPixels();
        int[] sourcePixels = source.getPixels();

        for (int i = 0; i < backdropPixels.length; i++) {
            backdropPixels[i] = blendMode.composite(backdropPixels[i], sourcePixels[i]);
        }
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

    private void validateMaskDimensions(LayerMask mask) {
        Objects.requireNonNull(mask, "mask");
        if (mask.getWidth() != image.getWidth() || mask.getHeight() != image.getHeight()) {
            throw new IllegalArgumentException("Layer mask must match layer image dimensions");
        }
    }

    public Layer applyFilter(UnaryOperator<FastRGB> operation) {
        setPixels(operation.apply(getPixels()));
        return this;
    }

    public Layer applyMasks() {
        int[] effectiveMaskAlphas = buildEffectiveMaskAlphas();

        if (effectiveMaskAlphas == null) {
            return this;
        }

        applyEffectiveMaskInPlace(this.image, effectiveMaskAlphas);
        masks.clear();
        return this;
    }
    public Layer applyMask() {
        return applyMasks();
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

        boolean sizeChanged =
                this.image.getWidth() != image.getWidth() ||
                        this.image.getHeight() != image.getHeight();

        this.image = Filter.copyPixels(image);

        if (sizeChanged) {
            this.masks.clear();
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
        return !masks.isEmpty();
    }

    public int getMaskCount() {
        return masks.size();
    }

    public List<LayerMask> getMasks() {
        return masks.stream().map(LayerMask::copy).toList();
    }

    public LayerMask getMask() {
        return masks.isEmpty() ? null : masks.get(masks.size() - 1).copy();
    }

    public LayerMask getMask(int index) {
        if (index < 0 || index >= masks.size()) {
            throw new IndexOutOfBoundsException("Mask index " + index + " out of bounds for " + masks.size() + " masks");
        }
        return masks.get(index).copy();
    }

    public Layer setMask(LayerMask mask) {
        masks.clear();
        if (mask != null) {
            validateMaskDimensions(mask);
            masks.add(mask.copy());
        }
        return this;
    }

    public Layer addMask(LayerMask mask) {
        validateMaskDimensions(mask);
//        masks.add(mask.copy());
        masks.add(mask);
        return this;
    }

    public Layer addMasks(Collection<LayerMask> masks) {
        Objects.requireNonNull(masks, "masks");
        for (LayerMask mask : masks) {
            addMask(mask);
        }
        return this;
    }

    public Layer removeMask(int index) {
        if (index < 0 || index >= masks.size()) {
            throw new IndexOutOfBoundsException("Mask index " + index + " out of bounds for " + masks.size() + " masks");
        }
        masks.remove(index);
        return this;
    }

    public Layer clearMasks() {
        masks.clear();
        return this;
    }


    private int[] buildEffectiveMaskAlphas() {
        if (masks.isEmpty()) {
            return null;
        }

        int pixelCount = image.getWidth() * image.getHeight();
        int[] effective = new int[pixelCount];

        for (LayerMask mask : masks) {
            int[] maskPixels = mask.pixels().getPixels();
            for (int i = 0; i < pixelCount; i++) {
                int alpha = FastRGB.alpha(maskPixels[i]);
                if (alpha > effective[i]) {
                    effective[i] = alpha;
                }
            }
        }

        return effective;
    }

    private static void applyEffectiveMaskInPlace(FastRGB target, int[] effectiveMaskAlphas) {
        if (effectiveMaskAlphas == null) {
            return;
        }

        int[] pixels = target.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int alpha = FastRGB.alpha(argb);

            if (alpha == 0) {
                continue;
            }

            int newAlpha = (alpha * effectiveMaskAlphas[i] + 127) / 255;
            pixels[i] = newAlpha == alpha ? argb : Filter.withAlpha(argb, newAlpha);
        }
    }
}
