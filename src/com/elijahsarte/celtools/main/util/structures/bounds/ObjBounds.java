package com.elijahsarte.celtools.main.util.structures.bounds;

import java.util.List;

public record ObjBounds<T>(T lowerBound, T upperBound) {

    public List<T> asList() {
        return List.of(lowerBound, upperBound);
    }

}
