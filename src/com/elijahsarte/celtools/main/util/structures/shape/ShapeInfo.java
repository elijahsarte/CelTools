package com.elijahsarte.celtools.main.util.structures.shape;


import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;

import java.awt.*;

public record ShapeInfo(PointCollection innerShape, Point topPt, Point bottomPt,
                        int rectWidth,
                        int rectHeight,
                        double sizeRatio
                        ) { }

