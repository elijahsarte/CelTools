package com.elijahsarte.celtools.main.imageworkshop;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.UnaryOperator;

public class Photoshop {
    private final int width;
    private final int height;
    private final List<Layer> layers = new ArrayList<>();

    public Photoshop(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Document dimensions must be positive");
        }
        this.width = width;
        this.height = height;
    }

    public Photoshop(BufferedImage baseImage, boolean copy) {
        this(baseImage.getWidth(), baseImage.getHeight());
        addLayer(new Layer("Background", baseImage, copy));
    }
    public Photoshop(BufferedImage baseImage) {
        this(baseImage, true);
    }
    public Photoshop(FastRGB baseImage, boolean copy) {
        this(baseImage.getWidth(), baseImage.getHeight());
        addLayer(new Layer("Background", baseImage, copy));
    }
    public Photoshop(FastRGB baseImage) {
        this(baseImage, true);
    }
    public Photoshop(ImageHandler imageHandler, boolean copy) {
        this(copy ? Filter.copy(imageHandler) : imageHandler.getImage(), false);
    }
    public Photoshop(ImageHandler imageHandler) {
        this(imageHandler, true);
    }

    public Layer addLayer(Layer layer) {
        Objects.requireNonNull(layer, "layer");
        layers.add(layer);
        return layers.get(layers.size() - 1);
    }

    public Layer addCopyLayer(Layer layer) {
        return addLayer(layer.copy());
    }

    public Layer addLayer(int index, Layer layer) {
        Objects.requireNonNull(layer, "layer");
        int boundedIndex = Math.max(0, Math.min(index, layers.size()));
        layers.add(boundedIndex, layer);
        return layers.get(boundedIndex);
    }

    public Layer addCopyLayer(int index, Layer layer) {
        return addLayer(index, layer.copy());
    }

    public Layer addLayer(String name, BufferedImage image) {
        return addLayer(new Layer(name, image));
    }

    public Layer addTransparentLayer(String name) {
        return addLayer(new Layer(width, height, name));
    }

    public Layer duplicateLayer(int index) {
        Layer duplicate = getLayer(index).copy();
        duplicate.setName(duplicate.getName() + " Copy");
        return addLayer(index + 1, duplicate);
    }

    public Layer removeLayer(int index) {
        return layers.remove(index);
    }

    public Layer moveLayerUp(int index) {
        requireIndex(index);
        if (index >= layers.size() - 1) {
            return layers.get(index);
        }
        Collections.swap(layers, index, index + 1);
        return layers.get(index + 1);
    }

    public Layer moveLayerDown(int index) {
        requireIndex(index);
        if (index <= 0) {
            return layers.get(index);
        }
        Collections.swap(layers, index, index - 1);
        return layers.get(index - 1);
    }

    public Layer moveLayer(int fromIndex, int toIndex) {
        requireIndex(fromIndex);
        Layer layer = layers.remove(fromIndex);
        int boundedIndex = Math.max(0, Math.min(toIndex, layers.size()));
        layers.add(boundedIndex, layer);
        return layer;
    }

    public Layer mergeDown(int upperIndex) {
        if (upperIndex <= 0 || upperIndex >= layers.size()) {
            throw new IllegalArgumentException("Upper layer index must have a layer below it");
        }
        String mergedName = getLayer(upperIndex - 1).getName() + " + " + getLayer(upperIndex).getName();
        return mergeLayers(upperIndex - 1, upperIndex, mergedName);
    }

    public Layer mergeLayers(int lowerIndex, int upperIndex, String mergedName) {
        requireIndex(lowerIndex);
        requireIndex(upperIndex);

        if (lowerIndex > upperIndex) {
            throw new IllegalArgumentException("lowerIndex must be <= upperIndex");
        }

        FastRGB mergedImage = rasterizeRange(lowerIndex, upperIndex);
        Layer merged = new Layer(
                mergedName == null || mergedName.isBlank() ? "Merged Layer" : mergedName,
                mergedImage
        );

        layers.subList(lowerIndex, upperIndex + 1).clear();
        layers.add(lowerIndex, merged);
        return merged;
    }

    public Layer setBlendMode(int index, BlendMode blendMode) {
        return getLayer(index).setBlendMode(blendMode);
    }

    public Layer createClippingMask(int index) {
        requireIndex(index);
        if (index == 0) {
            throw new IllegalArgumentException("The bottom layer cannot clip to a previous layer");
        }
        return getLayer(index).setClippedToPrevious(true);
    }

    public LayerMask createClippingMask(int index, int maskLayerIndex) {
        Layer layer = getLayer(index);
        Layer maskLayer = getLayer(maskLayerIndex);
        LayerMask mask = createMaskFromLayer(maskLayer, layer.bounds());
        layer.addMask(mask);
        return mask;
    }

    public LayerMask createClippingMask(int index, LayerMask layerMask) {
        Layer layer = getLayer(index);
        layer.addMask(layerMask);
        return layerMask;
    }

    public Layer releaseClippingMask(int index) {
        return getLayer(index).setClippedToPrevious(false);
    }

    public LayerMask createLayerMask(int index, PixelSelection selection) {
        return createLayerMask(index, selection, true);
    }

    public LayerMask createLayerMask(int index, PixelSelection selection, boolean revealSelection) {
        Layer layer = getLayer(index);
        LayerMask mask = selection.toLayerMask(layer.bounds(), revealSelection);
        layer.addMask(mask);
        return mask.copy();
    }

    public LayerMask createLayerMask(int index, ShapeContour contour) {
        return createLayerMask(index, contour, true);
    }

    public LayerMask createLayerMask(int index, ShapeContour contour, boolean revealSelection) {
        Layer layer = getLayer(index);
        LayerMask mask = LayerMask.fromContour(contour, layer.bounds(), revealSelection);
        layer.addMask(mask);
//        return mask.copy();
        return mask;
    }

    public Layer applyLayerMask(int index) {
        return getLayer(index).applyMasks();
    }

    public Layer applyLayerMasks(int index) {
        return getLayer(index).applyMasks();
    }

    public Layer removeLayerMask(int index) {
        return getLayer(index).clearMasks();
    }

    public Layer removeLayerMask(int index, int maskIndex) {
        return getLayer(index).removeMask(maskIndex);
    }

    public Layer removeAllLayerMasks(int index) {
        return getLayer(index).clearMasks();
    }

    public int getLayerMaskCount(int index) {
        return getLayer(index).getMaskCount();
    }

    public List<LayerMask> getLayerMasks(int index) {
        return getLayer(index).getMasks();
    }

    public LayerMask getLayerMask(int index) {
        return getLayer(index).getMask();
    }

    public LayerMask getLayerMask(int index, int maskIndex) {
        return getLayer(index).getMask(maskIndex);
    }

    public Layer applyFilter(int index, UnaryOperator<FastRGB> operation) {
        return Filter.apply(getLayer(index), operation);
    }

    public PixelSelection magicWand(int x, int y, int tolerance, boolean contiguous) {
        return PixelSelection.magicWand(flatten(), x, y, tolerance, contiguous);
    }

    public PixelSelection magicWand(int layerIndex, int x, int y, int tolerance, boolean contiguous) {
        return PixelSelection.magicWand(
                getLayer(layerIndex).renderToCanvasPixels(width, height),
                x, y, tolerance, contiguous
        );
    }

    /**
     * Paints a closed brush stroke through every edge of {@code contour} on an
     * existing layer. Contour coordinates are document coordinates; the
     * target layer's offset is accounted for automatically.
     *
     * <p>Flow accumulates between brush dabs while opacity caps the completed
     * stroke, matching the distinction made by Photoshop's brush tool. The
     * contour is sampled by arc length, so sparse and dense contours produce
     * the same stroke.</p>
     */
    public Layer paintContour(int layerIndex, ShapeContour contour, BrushSettings brush) {
        Objects.requireNonNull(contour, "contour");
        Objects.requireNonNull(brush, "brush");

        Layer layer = getLayer(layerIndex);
        if (contour.isEmpty() || brush.opacity() == 0.0 || brush.flow() == 0.0
                || brush.color().getAlpha() == 0) {
            return layer;
        }

        FastRGB target = layer.pixels();
        int targetWidth = target.getWidth();
        int targetHeight = target.getHeight();
        float[] coverage = new float[targetWidth * targetHeight];
        Point offset = layer.getOffset();

        double spacingPixels = Math.max(0.25, brush.thickness() * brush.spacing());
        Point first = contour.get(0);
        stampBrush(coverage, targetWidth, targetHeight,
                first.x - offset.x, first.y - offset.y, brush);

        if (contour.size() > 1) {
            double distanceToNextDab = spacingPixels;
            for (int edgeIndex = 0; edgeIndex < contour.size(); edgeIndex++) {
                Point start = contour.get(edgeIndex);
                Point end = contour.get((edgeIndex + 1) % contour.size());
                double dx = end.x - start.x;
                double dy = end.y - start.y;
                double edgeLength = Math.hypot(dx, dy);

                if (edgeLength == 0.0) {
                    continue;
                }

                while (distanceToNextDab <= edgeLength) {
                    double t = distanceToNextDab / edgeLength;
                    stampBrush(coverage, targetWidth, targetHeight,
                            start.x + dx * t - offset.x,
                            start.y + dy * t - offset.y,
                            brush);
                    distanceToNextDab += spacingPixels;
                }
                distanceToNextDab -= edgeLength;
            }
        }

        compositeBrushStroke(target, coverage, brush);
        return layer;
    }

    /**
     * Applies one brush dab at every point in {@code points} that can intersect
     * the target layer. Unlike the ShapeContour overload, this does not infer
     * an ordered path or discard interior and disconnected points. Brush
     * spacing is therefore not used; each supplied point is an explicit dab.
     */
    public Layer paintContour(int layerIndex, PointCollection points, BrushSettings brush) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(brush, "brush");

        Layer layer = getLayer(layerIndex);
        if (points.isEmpty() || brush.opacity() == 0.0 || brush.flow() == 0.0
                || brush.color().getAlpha() == 0) {
            return layer;
        }

        FastRGB target = layer.pixels();
        int targetWidth = target.getWidth();
        int targetHeight = target.getHeight();
        float[] coverage = new float[targetWidth * targetHeight];
        Point offset = layer.getOffset();

        for (Point point : points) {
            stampBrush(
                    coverage,
                    targetWidth,
                    targetHeight,
                    (double) point.x - offset.x,
                    (double) point.y - offset.y,
                    brush
            );
        }

        compositeBrushStroke(target, coverage, brush);
        return layer;
    }

    /** Creates a transparent layer and paints the contour onto it. */
    public Layer paintContour(ShapeContour contour, BrushSettings brush) {
        Objects.requireNonNull(contour, "contour");
        Objects.requireNonNull(brush, "brush");
        Layer layer = addTransparentLayer("Brush Stroke");
        return paintContour(layers.size() - 1, contour, brush);
    }

    /** Creates a transparent layer and applies a brush dab at every point. */
    public Layer paintContour(PointCollection points, BrushSettings brush) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(brush, "brush");
        addTransparentLayer("Brush Stroke");
        return paintContour(layers.size() - 1, points, brush);
    }

    /** Convenience overload for a round, fully opaque normal-mode brush. */
    public Layer paintContour(
            int layerIndex,
            ShapeContour contour,
            Color color,
            double thickness,
            double hardness
    ) {
        return paintContour(layerIndex, contour,
                BrushSettings.of(color, thickness).withHardness(hardness));
    }

    /** Convenience overload that applies a brush dab at every supplied point. */
    public Layer paintContour(
            int layerIndex,
            PointCollection points,
            Color color,
            double thickness,
            double hardness
    ) {
        return paintContour(layerIndex, points,
                BrushSettings.of(color, thickness).withHardness(hardness));
    }

    private static void stampBrush(
            float[] coverage,
            int width,
            int height,
            double centerX,
            double centerY,
            BrushSettings brush
    ) {
        double radius = brush.thickness() * 0.5;
        double bound = radius + 1.0;
        int minX = Math.max(0, (int) Math.floor(centerX - bound));
        int maxX = Math.min(width - 1, (int) Math.ceil(centerX + bound));
        int minY = Math.max(0, (int) Math.floor(centerY - bound));
        int maxY = Math.min(height - 1, (int) Math.ceil(centerY + bound));
        double radians = Math.toRadians(brush.angleDegrees());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double inverseMinorRadius = 1.0 / (radius * brush.roundness());
        double inverseMajorRadius = 1.0 / radius;
        double antiAliasWidth = Math.min(1.0, 0.5 / radius);
        double inner = brush.hardness() * Math.max(0.0, 1.0 - antiAliasWidth);
        double outer = 1.0 + antiAliasWidth;
        double flow = brush.flow();

        for (int y = minY; y <= maxY; y++) {
            int row = y * width;
            for (int x = minX; x <= maxX; x++) {
                double dx = x - centerX;
                double dy = y - centerY;
                double major = dx * cos + dy * sin;
                double minor = -dx * sin + dy * cos;
                double normalizedRadius = Math.hypot(
                        major * inverseMajorRadius,
                        minor * inverseMinorRadius
                );

                double tipAlpha;
                if (normalizedRadius <= inner) {
                    tipAlpha = 1.0;
                } else if (normalizedRadius >= outer) {
                    continue;
                } else {
                    double t = (outer - normalizedRadius) / (outer - inner);
                    tipAlpha = t * t * (3.0 - 2.0 * t);
                }

                int index = row + x;
                double dabAlpha = flow * tipAlpha;
                coverage[index] = (float) (1.0 - (1.0 - coverage[index]) * (1.0 - dabAlpha));
            }
        }
    }

    private static void compositeBrushStroke(FastRGB target, float[] coverage, BrushSettings brush) {
        int[] pixels = target.getPixels();
        Color color = brush.color();
        double alphaScale = (color.getAlpha() / 255.0) * brush.opacity();
        int rgb = (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();

        for (int i = 0; i < pixels.length; i++) {
            int alpha = (int) Math.round(255.0 * alphaScale * coverage[i]);
            if (alpha == 0) {
                continue;
            }
            int source = (alpha << 24) | rgb;
            pixels[i] = brush.blendMode().composite(pixels[i], source);
        }
    }

    /**
     * Immutable brush-tip and stroke settings. Fractions use the range 0..1;
     * spacing is a fraction of brush thickness (Photoshop's 25% is 0.25).
     */
    public record BrushSettings(
            Color color,
            double thickness,
            double hardness,
            double opacity,
            double flow,
            double spacing,
            double roundness,
            double angleDegrees,
            BlendMode blendMode
    ) {
        public BrushSettings {
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(blendMode, "blendMode");
            requireFinitePositive(thickness, "thickness");
            requireUnitInterval(hardness, "hardness");
            requireUnitInterval(opacity, "opacity");
            requireUnitInterval(flow, "flow");
            requireFinitePositive(spacing, "spacing");
            requireUnitIntervalExclusiveZero(roundness, "roundness");
            if (!Double.isFinite(angleDegrees)) {
                throw new IllegalArgumentException("angleDegrees must be finite");
            }
        }

        public static BrushSettings of(Color color, double thickness) {
            return new BrushSettings(
                    color, thickness, 1.0, 1.0, 1.0,
                    0.25, 1.0, 0.0, BlendMode.NORMAL
            );
        }

        public BrushSettings withColor(Color value) {
            return new BrushSettings(value, thickness, hardness, opacity, flow,
                    spacing, roundness, angleDegrees, blendMode);
        }

        public BrushSettings withThickness(double value) {
            return new BrushSettings(color, value, hardness, opacity, flow,
                    spacing, roundness, angleDegrees, blendMode);
        }

        public BrushSettings withHardness(double value) {
            return new BrushSettings(color, thickness, value, opacity, flow,
                    spacing, roundness, angleDegrees, blendMode);
        }

        public BrushSettings withOpacity(double value) {
            return new BrushSettings(color, thickness, hardness, value, flow,
                    spacing, roundness, angleDegrees, blendMode);
        }

        public BrushSettings withFlow(double value) {
            return new BrushSettings(color, thickness, hardness, opacity, value,
                    spacing, roundness, angleDegrees, blendMode);
        }

        public BrushSettings withSpacing(double value) {
            return new BrushSettings(color, thickness, hardness, opacity, flow,
                    value, roundness, angleDegrees, blendMode);
        }

        public BrushSettings withShape(double roundness, double angleDegrees) {
            return new BrushSettings(color, thickness, hardness, opacity, flow,
                    spacing, roundness, angleDegrees, blendMode);
        }

        public BrushSettings withBlendMode(BlendMode value) {
            return new BrushSettings(color, thickness, hardness, opacity, flow,
                    spacing, roundness, angleDegrees, value);
        }

        private static void requireFinitePositive(double value, String name) {
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException(name + " must be finite and > 0");
            }
        }

        private static void requireUnitInterval(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(name + " must be between 0 and 1");
            }
        }

        private static void requireUnitIntervalExclusiveZero(double value, String name) {
            if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
                throw new IllegalArgumentException(name + " must be > 0 and <= 1");
            }
        }
    }

    public FastRGB flatten() {
        int layerCount = layers.size();
        if (layerCount == 0) {
            return Filter.transparentPixels(width, height);
        }

        if (layerCount == 1) {
            Layer only = layers.get(0);
            return only.isVisible()
                    ? only.renderToCanvasPixels(width, height)
                    : Filter.transparentPixels(width, height);
        }

        return rasterizeRange(0, layerCount - 1);
    }

    public BufferedImage export() {
        return flatten().getImage();
    }

    public ImageHandler exportHandler() {
        return new ImageHandler(export());
    }

    public List<Layer> getLayers() {
        return layers.stream().map(Layer::copy).toList();
    }

    public Layer getLayer(int index) {
        requireIndex(index);
        return layers.get(index);
    }

    public int getLayerCount() {
        return layers.size();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

/*
    private FastRGB rasterizeRange(int lowerIndex, int upperIndex) {
        requireIndex(lowerIndex);
        requireIndex(upperIndex);

        if (lowerIndex > upperIndex) {
            throw new IllegalArgumentException("lowerIndex must be <= upperIndex");
        }

        FastRGB canvas = null;
        int index = lowerIndex;

        while (index <= upperIndex) {
            Layer baseLayer = layers.get(index);
            boolean usableBase = !baseLayer.isClippedToPrevious() || index == lowerIndex;

            if (!usableBase) {
                if (baseLayer.isVisible()) {
                    FastRGB rendered = baseLayer.renderToCanvasPixels(width, height);
                    canvas = compositeInto(canvas, rendered, baseLayer.getBlendMode());
                }
                index++;
                continue;
            }

            int clipEndExclusive = index + 1;
            boolean hasVisibleClippedLayers = false;

            while (clipEndExclusive <= upperIndex && layers.get(clipEndExclusive).isClippedToPrevious()) {
                if (layers.get(clipEndExclusive).isVisible()) {
                    hasVisibleClippedLayers = true;
                }
                clipEndExclusive++;
            }

            if (!baseLayer.isVisible()) {
                index = clipEndExclusive;
                continue;
            }

            FastRGB baseRendered = baseLayer.renderToCanvasPixels(width, height);
            canvas = compositeInto(canvas, baseRendered, baseLayer.getBlendMode());

            if (hasVisibleClippedLayers) {
                for (int clippedIndex = index + 1; clippedIndex < clipEndExclusive; clippedIndex++) {
                    Layer clippedLayer = layers.get(clippedIndex);

                    if (!clippedLayer.isVisible()) {
                        continue;
                    }

                    FastRGB clippedRendered = clippedLayer.renderToCanvasPixels(width, height);
                    FastRGB masked = Filter.applyAlphaMask(clippedRendered, baseRendered);
                    canvas = compositeInto(canvas, masked, clippedLayer.getBlendMode());
                }
            }

            index = clipEndExclusive;
        }

        return canvas != null ? canvas : Filter.transparentPixels(width, height);
    }
*/
    private FastRGB rasterizeRange(int lowerIndex, int upperIndex) {
        requireIndex(lowerIndex);
        requireIndex(upperIndex);

        if (lowerIndex > upperIndex) {
            throw new IllegalArgumentException("lowerIndex must be <= upperIndex");
        }

        // Allocate the final canvas once. Layers now composite directly into
        // it instead of creating a full document-sized temporary per layer.
        FastRGB canvas = Filter.transparentPixels(width, height);
        int index = lowerIndex;

        while (index <= upperIndex) {
            Layer baseLayer = layers.get(index);
            boolean usableBase = !baseLayer.isClippedToPrevious() || index == lowerIndex;

            if (!usableBase) {
                if (baseLayer.isVisible()) {
                    baseLayer.compositeOnto(canvas, baseLayer.getBlendMode(), null, null);
                }
                index++;
                continue;
            }

            int clipEndExclusive = index + 1;
            boolean hasVisibleClippedLayers = false;

            while (clipEndExclusive <= upperIndex && layers.get(clipEndExclusive).isClippedToPrevious()) {
                if (layers.get(clipEndExclusive).isVisible()) {
                    hasVisibleClippedLayers = true;
                }
                clipEndExclusive++;
            }

            if (!baseLayer.isVisible()) {
                index = clipEndExclusive;
                continue;
            }

            // The base alpha is only materialized when a visible clipping
            // layer actually needs it. Non-clipping groups allocate nothing.
            int[] baseAlphas = hasVisibleClippedLayers ? new int[width * height] : null;
            baseLayer.compositeOnto(canvas, baseLayer.getBlendMode(), null, baseAlphas);

            if (hasVisibleClippedLayers) {
                for (int clippedIndex = index + 1; clippedIndex < clipEndExclusive; clippedIndex++) {
                    Layer clippedLayer = layers.get(clippedIndex);

                    if (!clippedLayer.isVisible()) {
                        continue;
                    }

                    clippedLayer.compositeOnto(
                            canvas,
                            clippedLayer.getBlendMode(),
                            baseAlphas,
                            null
                    );
                }
            }

            index = clipEndExclusive;
        }

        return canvas;
    }

    private FastRGB compositeInto(FastRGB canvas, FastRGB source, BlendMode blendMode) {
        if (source == null) {
            return canvas;
        }

        // First composite onto a transparent canvas is just the source itself.
        if (canvas == null) {
            return source.copy();
        }

        composite(canvas, source, blendMode);
        return canvas;
    }


    private static void composite(FastRGB backdrop, FastRGB source, BlendMode blendMode) {
        int[] backdropPixels = backdrop.getPixels();
        int[] sourcePixels = source.getPixels();

        for (int i = 0, len = backdropPixels.length; i < len; i++) {
            int src = sourcePixels[i];
            int srcAlpha = src >>> 24;

            if (srcAlpha == 0) {
                continue; // fully transparent source pixel
            }

            int dst = backdropPixels[i];
            int dstAlpha = dst >>> 24;

            if (dstAlpha == 0) {
                backdropPixels[i] = src; // compositing onto empty pixel
                continue;
            }

            backdropPixels[i] = blendMode.composite(dst, src);
        }
    }

    private LayerMask createMaskFromLayer(Layer maskLayer, Rectangle targetBounds) {
        FastRGB renderedMaskLayer = maskLayer.renderToCanvasPixels(width, height);
        FastRGB mask = Filter.transparentPixels(targetBounds.width, targetBounds.height);
        int[] sourcePixels = renderedMaskLayer.getPixels();
        int[] maskPixels = mask.getPixels();
        int sourceWidth = renderedMaskLayer.getWidth();
        int sourceHeight = renderedMaskLayer.getHeight();

        for (int y = 0; y < targetBounds.height; y++) {
            int documentY = targetBounds.y + y;
            if (documentY < 0 || documentY >= sourceHeight) {
                continue;
            }

            int sourceRow = documentY * sourceWidth;
            int maskRow = y * targetBounds.width;
            for (int x = 0; x < targetBounds.width; x++) {
                int documentX = targetBounds.x + x;
                if (documentX < 0 || documentX >= sourceWidth) {
                    continue;
                }

                int alpha = sourcePixels[sourceRow + documentX] >>> 24;
                maskPixels[maskRow + x] = Filter.argb(alpha, 255, 255, 255);
            }
        }

        return new LayerMask(mask);
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= layers.size()) {
            throw new IndexOutOfBoundsException(
                    "Layer index " + index + " out of bounds for " + layers.size() + " layers"
            );
        }
    }
}
