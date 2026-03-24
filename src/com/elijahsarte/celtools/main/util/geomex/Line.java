package com.elijahsarte.celtools.main.util.geomex;

import com.elijahsarte.celtools.main.util.MathEx;

import java.awt.*;

public class Line {

    private final Point start, end;

    public Line(Point start, Point end) {
        this.start = start;
        this.end = end;
    }
    public Line(Point pt) {
        this(new Point(0, 0), pt);
    }
    public Line(int x1, int y1, int x2, int y2) {
        this(new Point(x1, y1), new Point(x2, y2));
    }


    public Point yAt(int x) {
        return new Point(x, MathEx.floorInt((MathEx.divide(end.y - start.y, end.x - start.x) * Math.abs(x - start.x)) + start.y));
    }
    public Point yAt(Point pt) {
        return yAt(pt.x);
    }
    public Point xAt(int y) {
        return new Point(
                end.y == start.y ? start.x : MathEx.floorInt(invSlope() * (y - start.y) + start.x),
                y);
    }
    public Point xAt(Point pt) {
        return xAt(pt.y);
    }


    public boolean on(Point pt) {
        return containsX(pt.x) && pt.y == yAt(pt.x).y;
    }
    public boolean above(Point pt) {
        return containsX(pt.x) && unboundedAbove(pt);
    }
    public boolean below(Point pt) {
        return containsX(pt.x) && unboundedBelow(pt);
    }
    public boolean unboundedAbove(Point pt) {
        return pt.y > yAt(pt.x).y;
    }
    public boolean unboundedBelow(Point pt) {
        return pt.y < yAt(pt.x).y;
    }

    public boolean above(Line line) {
        return xBounded(line) && unboundedAbove(line);
    }
    public boolean below(Line line) {
        return xBounded(line) && unboundedBelow(line);
    }
    public boolean unboundedAbove(Line line) {
        return line.start().y > start().y && line.end().y > end.y;
    }
    public boolean unboundedBelow(Line line) {
        return line.start().y < start.y && line.end().y < end.y;
    }

    public boolean containsX(int x) {
        return x >= start.x && x <= end.x;
    }
    public boolean xBounded(Line line) {
        return line.start().x >= start.x && line.end().x <= end.x;
    }
    public boolean intersects(Line line) {
        return xBounded(line) && (line.start().y == start.y || line.end().y == end.y || ((line.start().y > start.y && line.end().y < end.y) || (line.start().y < start.y && line.end().y > end.y)));
    }


    public boolean onlyVert() {
        return start.x == end.x;
    }
    public boolean onlyHoriz() {
        return start.y == end.y;
    }
    public boolean diag() {
        return !onlyVert() && !onlyHoriz();
    }

    public Point lowest() {
        return start.y < end.y ? start : end;
    }
    public Point highest() {
        return start.y > end.y ? start : end;
    }

    public Line translate(int dx, int dy) {
        return new Line(new Point(start.x + dx, start.y + dy), new Point(end.x + dx, end.y + dy));
    }
    public Line translate(double dx, double dy) {
        return translate(MathEx.roundInt(dx), MathEx.roundInt(dy));
    }
    public Line scale(double cx, double cy) {
        return new Line(new Point(MathEx.roundInt(start.x * cx), MathEx.roundInt(start.y * cy)), new Point(MathEx.roundInt(end.x * cx), MathEx.roundInt(end.y * cy)));
    }

    public Point intersectsAt(Line l) {
        double x1 = startX(), y1 = start().y;
        double x2 = endX(),   y2 = end().y;
        double x3 = l.startX(), y3 = l.start().y;
        double x4 = l.endX(),   y4 = l.end().y;

        double denom = (x1 - x2)*(y3 - y4) - (y1 - y2)*(x3 - x4);
        if (Math.abs(denom) < 1e-9) return null; // parallel or collinear

        double px = ((x1*y2 - y1*x2)*(x3 - x4) - (x1 - x2)*(x3*y4 - y3*x4)) / denom;
        double py = ((x1*y2 - y1*x2)*(y3 - y4) - (y1 - y2)*(x3*y4 - y3*x4)) / denom;

        // round to nearest integer coordinates
        int rx = (int) Math.round(px);
        int ry = (int) Math.round(py);

        return new Point(MathEx.roundInt(rx), MathEx.roundInt(ry));
    }

    public double slope() {
        return MathEx.divide(end.y - start.y, end.x - start.x);
    }
    public double invSlope() {
        return MathEx.divide(end.x - start.x, end.y - start.y);
    }

    public Point start() {
        return this.start;
    }
    public Point end() {
        return this.end;
    }
    public int startX() {
        return start.x;
    }
    public int endX() {
        return end.x;
    }

    @Override
    public String toString() {
        return "{" + start.toString() + ", " + end.toString() + "}";
    }

}

