package com.elijahsarte.celtools.main.imageworkshop;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class LayerMask {

    private FastRGB image;

    public LayerMask(FastRGB image) {
//        this.image = Filter.copyPixels(Objects.requireNonNull(image, "image"));
        this.image = Objects.requireNonNull(image, "image");
    }

    public LayerMask(BufferedImage image, boolean copy) {
        this(new FastRGB(image, copy));
    }
    public LayerMask(BufferedImage image) {
        this(image, true);
    }

    public LayerMask(LayerMask other) {
        this(other.image);
    }

    public static LayerMask revealAll(int width, int height) {
        FastRGB mask = Filter.transparentPixels(width, height);
        mask.fill(Filter.argb(255, 255, 255, 255));
        return new LayerMask(mask);
    }

    public static LayerMask hideAll(int width, int height) {
        return new LayerMask(Filter.transparentPixels(width, height));
    }

    public static LayerMask fromSelection(PixelSelection selection, Rectangle layerBounds, boolean revealSelection) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(layerBounds, "layerBounds");

        FastRGB mask = Filter.transparentPixels(layerBounds.width, layerBounds.height);
        boolean layerLocalSelection =
                selection.getWidth() == layerBounds.width &&
                        selection.getHeight() == layerBounds.height;

        for (int y = 0; y < layerBounds.height; y++) {
            for (int x = 0; x < layerBounds.width; x++) {
                boolean selected = layerLocalSelection
                        ? selection.contains(x, y)
                        : selection.contains(layerBounds.x + x, layerBounds.y + y);
                int alpha = selected == revealSelection ? 255 : 0;
                mask.setRGB(x, y, Filter.argb(alpha, 255, 255, 255));
            }
        }
        return new LayerMask(mask);
    }

    public static LayerMask fromContour(ShapeContour contour, Rectangle layerBounds, boolean revealSelection) {
        Objects.requireNonNull(contour, "contour");
        Objects.requireNonNull(layerBounds, "layerBounds");

        FastRGB mask = Filter.transparentPixels(layerBounds.width, layerBounds.height);
        int[] pixels = mask.getPixels();
        int onArgb = Filter.argb(revealSelection ? 255 : 0, 255, 255, 255);

        if (!revealSelection) {
            Arrays.fill(pixels, Filter.argb(255, 255, 255, 255));
        }

        if (contour.isEmpty() || layerBounds.width <= 0 || layerBounds.height <= 0) {
            return new LayerMask(mask);
        }

        int contourMinX = contour.firstX();
        int contourMaxX = contour.lastX();
        int contourMinY = Math.min(contour.bottomY(), contour.topY());
        int contourMaxY = Math.max(contour.bottomY(), contour.topY());

        int clipMinX = Math.max(layerBounds.x, contourMinX);
        int clipMaxX = Math.min(layerBounds.x + layerBounds.width - 1, contourMaxX);
        int clipMinY = Math.max(layerBounds.y, contourMinY);
        int clipMaxY = Math.min(layerBounds.y + layerBounds.height - 1, contourMaxY);

        if (clipMinX > clipMaxX || clipMinY > clipMaxY) {
            return new LayerMask(mask);
        }

        List<Point> pts = contour.toList();
        int n = pts.size();

        if (n == 1) {
            paintLatticeSegment(
                    pts.get(0).x, pts.get(0).y,
                    pts.get(0).x, pts.get(0).y,
                    clipMinX, clipMaxX, clipMinY, clipMaxY,
                    layerBounds, pixels, onArgb
            );
            return new LayerMask(mask);
        }

        int[] x1 = new int[n];
        int[] y1 = new int[n];
        int[] minY = new int[n];
        int[] maxY = new int[n];
        double[] invSlope = new double[n];
        int edgeCount = 0;

        for (int i = 0; i < n; i++) {
            Point a = pts.get(i);
            Point b = pts.get((i + 1) % n);

            paintLatticeSegment(
                    a.x, a.y, b.x, b.y,
                    clipMinX, clipMaxX, clipMinY, clipMaxY,
                    layerBounds, pixels, onArgb
            );

            if (a.y == b.y) {
                continue;
            }

            int edgeMinY = Math.max(Math.min(a.y, b.y), clipMinY);
            int edgeMaxY = Math.min(Math.max(a.y, b.y), clipMaxY + 1);

            if (edgeMinY >= edgeMaxY) {
                continue;
            }

            x1[edgeCount] = a.x;
            y1[edgeCount] = a.y;
            minY[edgeCount] = edgeMinY;
            maxY[edgeCount] = edgeMaxY;
            invSlope[edgeCount] = (double) (b.x - a.x) / (double) (b.y - a.y);
            edgeCount++;
        }

        double[] intersections = new double[Math.max(edgeCount, 1)];

        for (int y = clipMinY; y <= clipMaxY; y++) {
            int count = 0;

            for (int e = 0; e < edgeCount; e++) {
                if (y < minY[e] || y >= maxY[e]) {
                    continue;
                }
                intersections[count++] = x1[e] + ((double) (y - y1[e]) * invSlope[e]);
            }

            if (count < 2) {
                continue;
            }

            Arrays.sort(intersections, 0, count);

            int rowOffset = (y - layerBounds.y) * layerBounds.width;
            for (int i = 0; i + 1 < count; i += 2) {
                int startX = Math.max(
                        clipMinX,
                        (int) Math.ceil(Math.min(intersections[i], intersections[i + 1]))
                );
                int endX = Math.min(
                        clipMaxX,
                        (int) Math.floor(Math.max(intersections[i], intersections[i + 1]))
                );

                if (startX <= endX) {
                    Arrays.fill(
                            pixels,
                            rowOffset + (startX - layerBounds.x),
                            rowOffset + (endX - layerBounds.x) + 1,
                            onArgb
                    );
                }
            }
        }

        return new LayerMask(mask);
    }

    private static void paintLatticeSegment(
            int x0, int y0,
            int x1, int y1,
            int clipMinX, int clipMaxX,
            int clipMinY, int clipMaxY,
            Rectangle layerBounds,
            int[] pixels,
            int argb
    ) {
        if (y0 == y1) {
            if (y0 < clipMinY || y0 > clipMaxY) {
                return;
            }

            int startX = Math.max(Math.min(x0, x1), clipMinX);
            int endX = Math.min(Math.max(x0, x1), clipMaxX);

            if (startX <= endX) {
                int rowOffset = (y0 - layerBounds.y) * layerBounds.width;
                Arrays.fill(
                        pixels,
                        rowOffset + (startX - layerBounds.x),
                        rowOffset + (endX - layerBounds.x) + 1,
                        argb
                );
            }
            return;
        }

        if (x0 == x1) {
            if (x0 < clipMinX || x0 > clipMaxX) {
                return;
            }

            int startY = Math.max(Math.min(y0, y1), clipMinY);
            int endY = Math.min(Math.max(y0, y1), clipMaxY);
            int localX = x0 - layerBounds.x;

            for (int y = startY; y <= endY; y++) {
                pixels[((y - layerBounds.y) * layerBounds.width) + localX] = argb;
            }
            return;
        }

        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        while (true) {
            paintMaskPoint(
                    x0, y0,
                    clipMinX, clipMaxX,
                    clipMinY, clipMaxY,
                    layerBounds,
                    pixels,
                    argb
            );

            if (x0 == x1 && y0 == y1) {
                return;
            }

            int e2 = err << 1;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static void paintMaskPoint(
            int x, int y,
            int clipMinX, int clipMaxX,
            int clipMinY, int clipMaxY,
            Rectangle layerBounds,
            int[] pixels,
            int argb
    ) {
        if (x < clipMinX || x > clipMaxX || y < clipMinY || y > clipMaxY) {
            return;
        }

        pixels[((y - layerBounds.y) * layerBounds.width) + (x - layerBounds.x)] = argb;
    }

    // New: mutate existing FastRGB in-place instead of allocating another copy.
    public void applyToInPlace(FastRGB source) {
        Objects.requireNonNull(source, "source");
        applyToInPlace(source.getPixels(), source.getWidth(), source.getHeight());
    }

    // Package-private so Layer can use it directly during direct canvas rendering if needed later.
    void applyToInPlace(int[] sourcePixels, int sourceWidth, int sourceHeight) {
        int[] maskPixels = image.getPixels();
        int maskWidth = image.getWidth();
        int maskHeight = image.getHeight();

        int width = Math.min(sourceWidth, maskWidth);
        int height = Math.min(sourceHeight, maskHeight);

        for (int y = 0; y < height; y++) {
            int srcRow = y * sourceWidth;
            int maskRow = y * maskWidth;

            for (int x = 0; x < width; x++) {
                int idx = srcRow + x;
                int sourceArgb = sourcePixels[idx];
                int newAlpha = (FastRGB.alpha(sourceArgb) * FastRGB.alpha(maskPixels[maskRow + x])) / 255;
                sourcePixels[idx] = Filter.withAlpha(sourceArgb, newAlpha);
            }
        }

        // Anything outside the mask bounds becomes fully transparent, matching previous behavior.
        if (sourceWidth > maskWidth) {
            for (int y = 0; y < height; y++) {
                int row = y * sourceWidth;
                Arrays.fill(sourcePixels, row + maskWidth, row + sourceWidth, 0);
            }
        }

        if (sourceHeight > maskHeight) {
            Arrays.fill(sourcePixels, height * sourceWidth, sourcePixels.length, 0);
        }
    }

    public FastRGB applyTo(FastRGB source) {
        Objects.requireNonNull(source, "source");
        FastRGB masked = Filter.copyPixels(source);
        applyToInPlace(masked);
        return masked;
    }

    public BufferedImage applyTo(BufferedImage source) {
        FastRGB masked = Filter.copyPixels(source);
        applyToInPlace(masked);
        return masked.getImage();
    }

    public LayerMask invert() {
        FastRGB inverted = image.copy();
        int[] pixels = inverted.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = Filter.argb(255 - FastRGB.alpha(pixels[i]), 255, 255, 255);
        }
        this.image = inverted;
        return this;
    }

    public int alphaAt(int x, int y) {
        if (!image.contains(x, y)) {
            return 0;
        }
        return FastRGB.alpha(image.getRGBRaw(x, y));
    }

    public LayerMask copy() {
        return new LayerMask(this);
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

    public BufferedImage getImage() {
        return exportImage();
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }
}