package com.elijahsarte.celtools.main.util.geomex.path;

import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;

import java.awt.*;

public class ShapePath {

    public enum Direction {
        LEFT, RIGHT
    }
    public enum VertSide {
        UP, DOWN
    }

    private final PointCollection pts;
    private final Direction dir;
    private final ShapePath.VertSide side;
    private final int startX;

    private Point toTraverse;

    private boolean traversing = true;

    public ShapePath(PointCollection pts, ShapePath.Direction dir, ShapePath.VertSide side, int startX) {
        this.pts = pts;
        this.dir = dir;
        this.side = side;
        this.startX = startX;
    }
    public ShapePath(PointCollection pts, ShapePath.Direction dir, Point start) {
        this(pts, dir, ProgrammingEx.varOper(pts.getYesAtX(start.x), iL -> MathEx.closer(start.y, iL.first(), iL.last()) == iL.first() ? VertSide.DOWN : VertSide.UP), start.x);
    }

    public boolean traversing() {
        if (toTraverse == null) {
            Integer nextX = dir == Direction.LEFT ? pts.lowerX(startX) : pts.higherX(startX);
            if (nextX == null) this.traversing = false;
            else this.toTraverse = ProgrammingEx.varOper(pts.getYesAtX(nextX), yL -> new Point(nextX, side == VertSide.UP ? yL.last() : yL.first()));
        }
        return this.traversing;
    }

    public Point traverse() {
        if (!this.traversing) return null;
        if (toTraverse == null) return null;

        Integer nNextX = dir == Direction.LEFT ? pts.lowerX(toTraverse.x) : pts.higherX(toTraverse.x);
        if (nNextX == null) {
            this.traversing = false;
        } else {
            this.toTraverse = ProgrammingEx.varOper(pts.getYesAtX(nNextX), nNList -> new Point(nNextX, side == VertSide.UP ? nNList.last() : nNList.first()));
        }
        return this.toTraverse;
    }


}

