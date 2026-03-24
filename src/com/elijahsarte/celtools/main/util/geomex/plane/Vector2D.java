package com.elijahsarte.celtools.main.util.geomex.plane;

import com.elijahsarte.celtools.main.util.MathEx;

public record Vector2D(double x, double y) {

    public double getLength() {
        return Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
    }
    public Vector2D multiply(double scalar) {
        return new Vector2D(x*scalar, y*scalar);
    }

    // Vector math
    public double dot(Vector2D b) {
        return x * b.x() + y * b.y();
    }
    public double cross(Vector2D b) {
        return x * b.y() - y * b.x();
    }
    public Vector2D normalize() {
        double length = getLength();
        return length == 0 ? this : new Vector2D(x / length, y / length);
    }
    public Vector2D perpendicular() {
        return new Vector2D(y, -x);
    }
    public double angle(Vector2D b) {
        double mLens = getLength() * b.getLength();
        return mLens == 0 ? 0 : Math.acos(Math.max(-1, Math.min(1, dot(b) / mLens)));
    }
    public Vector2D project(Vector2D b) {
        return b.multiply(dot(b) / b.getLength());
    }
    public Vector2D reflect(Vector2D b) {
        return project(b).multiply(-2);
    }
    public Vector2D rotate(double angle) {
        double cos = MathEx.cos(angle), sin = MathEx.sin(angle);
        return new Vector2D(x * cos - y * sin, x * sin + y * cos);
    }


}

