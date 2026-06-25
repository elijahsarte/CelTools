package com.elijahsarte.celtools.main.selectionui;

import com.elijahsarte.celtools.main.util.function.fnvals.BiTuple;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;

import java.awt.*;

public record SelectionTransform(double scaleRatio, double offsetX, double offsetY, PointCollection bounds) {

    public SelectionTransform(double scaleRatio, double offsetX, double offsetY) {
        this(scaleRatio, offsetX, offsetY, null);
    }
    public SelectionTransform(double scaleRatio, PointCollection bounds) {
        this(scaleRatio, 0, 0, bounds);
    }
    public SelectionTransform(double scaleRatio) {
        this(scaleRatio, 0, 0);
    }

    public double applyX(double x) {
        return (x + offsetX) * scaleRatio;
    }
    public double applyY(double y) {
        return (y + offsetY) * scaleRatio;
    }
    public BiTuple<Double, Double> apply(double x, double y) {
        return new BiTuple<>(applyX(x), applyY(y));
    }
    public Rectangle apply(Rectangle rect) {
        return rect == null ? null : new Rectangle((int) applyX(rect.getX()), (int) applyY(rect.getY()), (int) (rect.getWidth() * scaleRatio), (int) (rect.getHeight() * scaleRatio));
    }
    public Point apply(Point point) {
        return point == null ? null : new Point((int) Math.round(applyX(point.getX())), (int) Math.round(applyY(point.getY())));
    }

    private double validatedScale() {
        if (scaleRatio == 0) {
            throw new IllegalStateException("SelectionTransform scaleRatio cannot be zero when performing inverse operations.");
        }
        return scaleRatio;
    }

    public double unapplyX(double value) {
        return (value / validatedScale()) - offsetX;
    }
    public double unapplyY(double value) {
        return (value / validatedScale()) - offsetY;
    }
    public Rectangle unapply(Rectangle rect) {
        if (rect == null) return null;
        double scale = validatedScale();
        return new Rectangle(
                (int) Math.round(unapplyX(rect.getX())),
                (int) Math.round(unapplyY(rect.getY())),
                (int) Math.round(rect.getWidth() / scale),
                (int) Math.round(rect.getHeight() / scale)
        );
    }
    public Point unapply(Point point) {
        return point == null ? null : new Point(
                (int) Math.round(unapplyX(point.getX())),
                (int) Math.round(unapplyY(point.getY()))
        );
    }

    public boolean bounded() {
        return bounds != null;
    }
    public boolean included(Point point) {
        return !bounded() || bounds.contains(point);
    }
    public Point clamp(Point point) {
        return bounded() ? new Point(
                (int) Math.max(bounds.firstX(), Math.min(point.getX(), bounds.lastX())),
                (int) Math.max(bounds.bottomY(), Math.min(point.getY(), bounds.topY()))
        ) : point;
    }
    public Rectangle clamp(Rectangle rect) {
        if (!bounded() || rect == null) return rect == null ? null : new Rectangle(rect);

        int x = (int) Math.round(rect.getX()),
                y = (int) Math.round(rect.getY()),
                w = (int) Math.round(rect.getWidth()),
                h = (int) Math.round(rect.getHeight());

        int minX = bounds.firstX(),
                maxX = bounds.lastX(),
                minY = bounds.bottomY(),
                maxY = bounds.topY();

        x = Math.max(minX, Math.min(x, maxX));
        y = Math.max(minY, Math.min(y, maxY));

        w = Math.max(0, Math.min(w, maxX - x));
        h = Math.max(0, Math.min(h, maxY - y));

        if (x + w > maxX) {
            x = Math.max(minX, maxX - w);
            w = Math.max(0, Math.min(w, maxX - x));
        }
        if (y + h > maxY) {
            y = Math.max(minY, maxY - h);
            h = Math.max(0, Math.min(h, maxY - y));
        }

        return new Rectangle(x, y, w, h);
    }


    public static SelectionTransform empty() {
        return new SelectionTransform(1, 0, 0);
    }

}

