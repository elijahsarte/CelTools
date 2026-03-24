package com.elijahsarte.celtools.main.util.geomex.path;

public class PathPoint {

    public enum Location {
        TOP_RIGHT, TOP_LEFT, BOTTOM_LEFT, BOTTOM_RIGHT, MID, INDEX;
        public boolean bottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
        public boolean top() {
            return this == TOP_LEFT || this == TOP_RIGHT;
        }
        public boolean left() {
            return this == BOTTOM_LEFT || this == TOP_LEFT;
        }
        public boolean right() {
            return this == BOTTOM_RIGHT || this == TOP_RIGHT;
        }
    }

    private final Location loc;
    private int index;

    public PathPoint(Location loc, int index) {
        this.loc = loc;
        validateIndex();
        this.index = index;
    }
    public PathPoint(Location loc) {
        this.loc = loc;
    }

    private void validateIndex() {
        if (loc != Location.INDEX) throw new IllegalArgumentException("Cannot instantiate with an index when the Location is not INDEX");
    }

    public Location getLocation() {
        return this.loc;
    }
    public int index() {
        validateIndex();
        return index;
    }

}

