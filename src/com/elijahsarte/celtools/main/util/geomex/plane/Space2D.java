package com.elijahsarte.celtools.main.util.geomex.plane;

import com.elijahsarte.celtools.main.util.ProgrammingEx;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.IntStream;

public class Space2D {

    public static final Vector2D DEFAULT_IHAT = new Vector2D(1, 0);
    public static final Vector2D DEFAULT_JHAT = new Vector2D(0, 1);

    private final Vector2D iHat;
    private final Vector2D jHat;
    private final Point2D origin;

    public Space2D(Vector2D iHat, Vector2D jHat, Point2D origin) {
        this.iHat = iHat;
        this.jHat = jHat;
        this.origin = origin;
    }
    public Space2D(Vector2D iHat, Vector2D jHat) {
        this(iHat, jHat, new Point(0, 0));
    }
    public Space2D(Point2D origin) {
        this(DEFAULT_IHAT, DEFAULT_JHAT, origin);
    }
    public Space2D() {
        this(new Point(0, 0));
    }


    // its important to note that these methods return the same result, its just that the methods
    // accept different types of vectorized stuff

    // returns absolute coords relative to origin. for instance, given i hat and j hat are their defaults,
    // (789, 913) with origin (682, 456) will translate to (107, 457)
    public Vector2D vectorizeAbsolute(double unitX, double unitY) {
        return new Vector2D(iHat.x()*(unitX-origin.getX()), jHat.y()*(unitY-origin.getY()));
    }
    // coords relative to origin, so (7, 0) relative to origin (678, 902) for instance
    public Vector2D vectorizeRelative(double unitX, double unitY) {
        return vectorizeAbsolute(unitX+origin.getX(), unitY+origin.getY());
    }

    // assumes that passed vector was vectorized relative to origin already (aka passed through vectorize abs)
    public Point getPoint(Vector2D relVec) {
        return new Point((int) ((relVec.x() / iHat.x())+origin.getX()), (int) ((relVec.y() / jHat.y())+origin.getY()));
    }

    // degrees are the same direction as the unit circle
    // assumes that the passed vector was vectorized from the space2d class
    public double vectorDirection(Vector2D vector) {
        // yes, i know that this does all the "quadrant calculations" for you because its based off unit circle's
        // periodic nature, but.... eh
//        double rawAngle = 90 - (Math.atan(vector.y() / vector.x())*(180/Math.PI));
        // rawAngle returns angle on side of vector adjacent to the previously crossed axis.
        double rawAngle = Math.abs(Math.atan(Math.abs(vector.y()) / Math.abs(vector.x()))*(180/Math.PI));
//        boolean posX = (vector.x() * iHat.x()) >= 0;
//        boolean posY = (vector.y() * jHat.y()) >= 0;
        boolean posX = vector.x() >= 0, posY = vector.y() >= 0;
        if (posX && posY) return rawAngle;
//        if (!posX && posY) return 90 + rawAngle;
//        if (!posX && !posY) return 180 + rawAngle;
//        if (posX && !posY) return 270 + rawAngle;
        if (!posX && posY) return 180 - rawAngle;
        if (!posX && !posY) return 270 - rawAngle;
        if (posX && !posY) return 360 - rawAngle;
        return 0;
    }


    public List<Vector2D> getVecPath(Vector2D vector) {
        return ProgrammingEx.varOper(
                vector.y() / vector.x(),
                vecChange -> IntStream.rangeClosed(0, (int) vector.x()).mapToObj(x -> new Vector2D(x, vecChange * x)).toList());
    }



}

