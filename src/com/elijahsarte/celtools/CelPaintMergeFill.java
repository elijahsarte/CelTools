package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.colormodel.ColorSampleDataModel;
import com.elijahsarte.celtools.main.colormodel.HSVBounds;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.StreamEx;
import com.elijahsarte.celtools.main.util.iterators.CoordIterator;
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;
import com.elijahsarte.celtools.main.util.typeex.nullable.NullableBoolean;
import com.elijahsarte.celtools.mainex.TaskTracker;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.elijahsarte.celtools.mainex.TaskTracker.track;


public class CelPaintMergeFill extends Main {

    private static final double twentyQual = 0.23;
    private static final int distFromEdge = 5;

    private static final double sampleWidthTolerance = 0.15, groupInclusionThreshold = 0.9,
            borderDistThreshold = 2, sampleGrpThreshold = 1.3, derivativeThreshold = 0.4;

    public CelPaintMergeFill(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws Exception {

        String baseCelPath = safeArg("base-cel", "File path to base cel:");
        String recursCelPath = args.get("all-cels-folder");
        String allCelPaths = OptionalEx.ofNonNullable(recursCelPath).then(() -> (String) null).orElse(() -> safeArg("all-cels", "Folder containing cels:"));
        String outputCelPath = safeArg("output-cel", "Output cel path:");

        track("reading base cel");
        ImageHandler baseCelHandler = handlerFromFile(baseCelPath);
        FastRGB baseCel = new FastRGB(baseCelHandler);

        track("parsing given object border");
        TreeMap<Integer, List<Integer>> rawObjectBorderData = objectBorderArg(baseCelHandler);
        PointCollection rawObjectBorder = new PointCollection(rawObjectBorderData);

        track("locating object border");
//        PointCollection blackPixels = filterPixelsToPtCollection(baseCelHandler, new double[] { 0, 0, 0 }, new double[] { 360, 11, 11 });
        PointCollection blackPixels = filterPixelsToPtCollection(baseCelHandler, (hsv) -> (hsv[2] <= 11 && hsv[1] <= 11) || (hsv[1] > 11 && hsv[2] <= 25));
        PointCollection objectPoints = deriveGroup(blackPixels, rawObjectBorder, 3);

        track("reading color sample cel");
        ImageHandler colorSampleCelHandler = handlerFromFile(safeArg("color-cel", "File path to color sample cel:"));

        track("getting color sample");
        Rectangle colorRect = cropBoxArg(colorSampleCelHandler, "color-rect");
        BufferedImage colorSample = colorSampleCelHandler.getImage().getSubimage(colorRect.x, colorRect.y, colorRect.width, colorRect.height);

        track("analyzing color sample");
        ColorSampleDataModel colorModel = new ColorSampleDataModel(colorSample);
        HSVBounds hsvBounds = colorModel.getHSVBounds();
        hsvBounds.cache(false);

        track("decomposing color sample properties");
        PointCollection baseSamplePixels = groupPoints(filterPixelsToPtCollection(colorSampleCelHandler, hsvBounds), sampleGrpThreshold).stream().max(Comparator.comparingInt(PointCollection::size)).orElseThrow(() -> new IllegalArgumentException("No sample found in given sample cel"));
//        ShapeContour baseSampleContour = new ShapeContour(baseSamplePixels);
        // NOTE: BOTTOM Y IS REALLY THE TOP Y IN AN IMAGE
        colorRect.translate(-baseSamplePixels.firstX(), -baseSamplePixels.bottomY());

        track("opening all cels");

        List<File> allCels = Optional.ofNullable(allCelPaths)
                .map(c -> Arrays.stream(c.split(",")).map(p -> new File(p.trim())).toList())
                .orElseGet(() -> List.of(new File(recursCelPath).listFiles()));


        trace("analyzing given cels");
        for (File cel : allCels) {

            track("opening " + cel.getAbsolutePath());
            ImageHandler thisCelHandler = handlerFromFile(cel);
            FastRGB thisCel = new FastRGB(thisCelHandler);

            track("locating object border");
            PointCollection thisObjectPoints = groupPoints(filterPixelsToPtCollection(thisCelHandler, (hsv) -> (hsv[2] <= 11 && hsv[1] <= 11) || (hsv[1] > 11 && hsv[2] <= 25)), 3).stream().min(Comparator.comparingDouble(objectPoints::distance)).orElseThrow(() -> new IllegalArgumentException("No object border found in given cel"));
            ShapeContour thisObjectContour = new ShapeContour(thisObjectPoints);
            PointCollection objectPointsNotInBase = thisObjectPoints.subtract(objectPoints);

            track("finding painted section");
            PointCollection coloredPixels = filterPixelsToPtCollection(thisCelHandler, hsvBounds);
            List<PointCollection> paintedGroups = StreamEx.toArrayList(groupPoints(coloredPixels, sampleGrpThreshold)
                    .stream()
                    .sorted(Comparator.comparingInt(PointCollection::width).reversed()));
            if (paintedGroups.isEmpty()) {
                TaskTracker.endTrack();
                trace("WARNING: EMPTY COLOR SAMPLE, CONTINUING");
                continue;
            }

            track("profiling painted section");
            ShapeBounds selectionBounds = new ShapeBounds(cropBoxSelection(thisCelHandler));
            /*
            if (MathEx.within(paintedGroups.get(0).size(), baseSamplePixels.size(), 0.98)) {
                selectionBounds = new ShapeBounds(ConstructionEx.mutateCopy(colorRect, r -> r.translate(paintedGroups.get(0).firstX(), paintedGroups.get(0).topY())));
            } else {
                selectionBounds = new ShapeBounds(cropBoxSelection(thisCelHandler));
            }*/

            PointCollection paintedPixels = coloredPixels.stream().filter(selectionBounds::inside).filter(thisObjectContour::inside).collect(PointCollection.toPointCollection());
            paintedPixels.fillHoles();
            ShapeContour paintedContour = new ShapeContour(paintedPixels);
            PointCollection paintedBorder = new PointCollection();

            if (!thisObjectContour.inside(selectionBounds)) {
                NullableBoolean lastUseY = new NullableBoolean();
                Point lastPt = null, lastCPt = null;
                BiConsumer<Integer, Integer> addToBorder = (x, y) -> {
//                if (thisObjectPoints.contains(new Point(x, y))) return;
                    paintedBorder.add(new Point(x, y));
                };
                int toTravel = -1, traveled = 0;
                for (Point pt : paintedContour) {
                    if (toTravel != -1 && traveled <= toTravel) {
                        traveled++;
                        continue;
                    }
                    toTravel = -1;
                    traveled = 0;
                    Point cX = thisObjectPoints.closestX(pt), cY = thisObjectPoints.closestY(pt);
                    if (cX.distance(pt) > 6 && cY.distance(pt) > 6) {
                        toTravel = (int) Math.min(cX.distance(pt), cY.distance(pt));
                        continue;
                    }

                    boolean useY = cY.distance(pt) <= cX.distance(pt);
                    if (useY && objectPointsNotInBase.contains(cY))
                        cY = objectPointsNotInBase.probeY(cY, MathEx.signClamp(cY.y - pt.y));
                    if (!useY && objectPointsNotInBase.contains(cX))
                        cX = objectPointsNotInBase.probeX(cX, MathEx.signClamp(cX.x - pt.x));
                    if (lastUseY.isSet() && useY != lastUseY.get()) {
                        if (useY)
                            new CoordIterator(MathEx.min(pt.x, cY.x, lastPt.x, lastCPt.x), MathEx.max(pt.x, cY.x, lastPt.x, lastCPt.x) + 1, MathEx.min(pt.y, cY.y, lastPt.y, lastCPt.y), MathEx.max(pt.y, cY.y, lastPt.y, lastCPt.y) + 1).execute(addToBorder);
                        else
                            new CoordIterator(MathEx.min(pt.x, cX.x, lastPt.x, lastCPt.x), MathEx.max(pt.x, cX.x, lastPt.x, lastCPt.x) + 1, MathEx.min(pt.y, cY.y, lastPt.y, lastCPt.y), MathEx.max(pt.y, cY.y, lastPt.y, lastCPt.y) + 1).execute(addToBorder);
                    } else {
                        if (useY) IntStream.rangeClosed(Math.min(cY.y, pt.y), Math.max(cY.y, pt.y))
                                .mapToObj(y -> new Point(pt.x, y))
//                            .filter(Predicate.not(thisObjectPoints::contains))
                                .forEach(paintedBorder::add);
                        else IntStream.rangeClosed(Math.min(cX.x, pt.x), Math.max(cX.x, pt.x))
                                .mapToObj(x -> new Point(x, pt.y))
//                            .filter(Predicate.not(thisObjectPoints::contains))
                                .forEach(paintedBorder::add);
                    }

                    lastUseY.set(useY);
                    lastPt = pt;
                    lastCPt = useY ? cY : cX;
                }
            }

            track("filling painted section");
            Stream.concat(paintedPixels.stream(), paintedBorder.stream())
                    .filter(selectionBounds::inside)
                    .filter(thisObjectContour::inside)
                    .forEach(p -> {
                        Point newPt = new Point(p.x - thisObjectPoints.firstX() + objectPoints.firstX(), p.y - thisObjectPoints.topY() + objectPoints.topY());
                        baseCel.setHSV(newPt.x, newPt.y, thisCel.getHSV(p.x, p.y));
                    });

        }

        outputCel(baseCel, outputCelPath);
        if (willDumpBorder()) dumpBorder(rawObjectBorderData, outputCelPath);
    }
}

