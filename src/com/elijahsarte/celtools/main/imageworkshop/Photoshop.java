package com.elijahsarte.celtools.main.imageworkshop;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

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
    public Photoshop(ImageHandler imageHandler, boolean copy) {
        this(copy ? Filter.copy(imageHandler) : imageHandler.getImage(), copy);
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
        Layer merged = new Layer(mergedName == null || mergedName.isBlank() ? "Merged Layer" : mergedName, mergedImage);
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

    public Layer releaseClippingMask(int index) {
        return getLayer(index).setClippedToPrevious(false);
    }

    public LayerMask createLayerMask(int index, PixelSelection selection) {
        return createLayerMask(index, selection, true);
    }

    public LayerMask createLayerMask(int index, PixelSelection selection, boolean revealSelection) {
        Layer layer = getLayer(index);
        LayerMask mask = selection.toLayerMask(layer.bounds(), revealSelection);
        layer.setMask(mask);
        return mask.copy();
    }

    public LayerMask createLayerMask(int index, ShapeContour contour) {
        return createLayerMask(index, contour, true);
    }

    public LayerMask createLayerMask(int index, ShapeContour contour, boolean revealSelection) {
        Layer layer = getLayer(index);
        LayerMask mask = LayerMask.fromContour(contour, layer.bounds(), revealSelection);
        layer.setMask(mask);
        return mask.copy();
    }

    public Layer applyLayerMask(int index) {
        return getLayer(index).applyMask();
    }

    public Layer removeLayerMask(int index) {
        return getLayer(index).setMask(null);
    }

    public Layer applyFilter(int index, UnaryOperator<FastRGB> operation) {
        return Filter.apply(getLayer(index), operation);
    }

    public PixelSelection magicWand(int x, int y, int tolerance, boolean contiguous) {
        return PixelSelection.magicWand(flatten(), x, y, tolerance, contiguous);
    }

    public PixelSelection magicWand(int layerIndex, int x, int y, int tolerance, boolean contiguous) {
        return PixelSelection.magicWand(getLayer(layerIndex).renderToCanvasPixels(width, height), x, y, tolerance, contiguous);
    }

    public FastRGB flatten() {
        if (layers.isEmpty()) {
            return Filter.transparentPixels(width, height);
        }
        return rasterizeRange(0, layers.size() - 1);
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

    private FastRGB rasterizeRange(int lowerIndex, int upperIndex) {
        FastRGB canvas = Filter.transparentPixels(width, height);
        int index = lowerIndex;

        while (index <= upperIndex) {
            Layer baseLayer = layers.get(index);
            boolean usableBase = !baseLayer.isClippedToPrevious() || index == lowerIndex;
            if (!usableBase) {
                if (baseLayer.isVisible()) {
                    composite(canvas, baseLayer.renderToCanvasPixels(width, height), baseLayer.getBlendMode());
                }
                index++;
                continue;
            }

            FastRGB baseRendered;
            if (baseLayer.isVisible()) {
                baseRendered = baseLayer.renderToCanvasPixels(width, height);
                composite(canvas, baseRendered, baseLayer.getBlendMode());
            } else {
                baseRendered = Filter.transparentPixels(width, height);
            }

            index++;
            while (index <= upperIndex && layers.get(index).isClippedToPrevious()) {
                Layer clippedLayer = layers.get(index);
                if (clippedLayer.isVisible()) {
                    FastRGB clippedRendered = clippedLayer.renderToCanvasPixels(width, height);
                    FastRGB masked = Filter.applyAlphaMask(clippedRendered, baseRendered);
                    composite(canvas, masked, clippedLayer.getBlendMode());
                }
                index++;
            }
        }
        return canvas;
    }

    private static void composite(FastRGB backdrop, FastRGB source, BlendMode blendMode) {
        int[] backdropPixels = backdrop.getPixels();
        int[] sourcePixels = source.getPixels();

        for (int i = 0; i < backdropPixels.length; i++) {
            backdropPixels[i] = blendMode.composite(backdropPixels[i], sourcePixels[i]);
        }
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= layers.size()) {
            throw new IndexOutOfBoundsException("Layer index " + index + " out of bounds for " + layers.size() + " layers");
        }
    }
}
