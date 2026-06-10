package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.imageworkshop.BlendMode;
import com.elijahsarte.celtools.main.imageworkshop.Filter;
import com.elijahsarte.celtools.main.imageworkshop.Layer;
import com.elijahsarte.celtools.main.imageworkshop.Photoshop;
import com.elijahsarte.celtools.main.selectionui.ImageEyedropperFrame;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.iterators.CoordIterator;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.varMutate;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.varOper;
import static com.elijahsarte.celtools.mainex.TaskTracker.endTrack;
import static com.elijahsarte.celtools.mainex.TaskTracker.track;

public class CelPaintFill extends Main {

    private final double SCALE_DIFFERENCE = 10;
    private final int MAX_INITIAL_TILES = 12;
    //private final double MAX_TILE_UPSCALE_PERCENT = 0.35;
    //private final double MAX_TILE_UPSCALE_PERCENT = 2.596551724;
    private final double MAX_TILE_UPSCALE_PERCENT = 4.64827586207;

    public CelPaintFill(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws Exception {
        List<File> allCels = List.of(Optional.ofNullable(args.get("all-cels")).map(s -> new File(s).listFiles()).orElse(new File[] { new File(safeArg("cel", "File path of cel:")) }));
        String paintSpecimenPath = args.get("paint-specimen"),
                paintImagePath = args.get("paint-image"),
                outputCelPath = safeArg("output-cel", "Output cel path:"),
                sampleColor;

        if (allCels.size() > 1 && !new File(outputCelPath).isDirectory()) {
            trace("loading multiple cels, output cel must be folder");
            return;
        }

        FastRGB paintSpecimen;
        if (paintSpecimenPath == null) {
            track("prompting user to create paint specimen");
            ImageHandler paintImageHandler = handlerFromFile(paintImagePath);
            sampleColor = ImageEyedropperFrame.pickColor(paintImageHandler);
            BufferedImage colorSpecimenSection = varOper(cropBoxSelection(paintImageHandler), r -> paintImageHandler.getImage().getSubimage(r.x, r.y, r.width, r.height));
            track("crafting paint specimen");
            Photoshop specimenDoc = new Photoshop(colorSpecimenSection);
            specimenDoc.duplicateLayer(0);
            specimenDoc.applyFilter(0, Filter::average);
            specimenDoc.setBlendMode(1, BlendMode.DIFFERENCE);
            specimenDoc.mergeDown(1);
            paintSpecimen = specimenDoc.flatten();
            outputCel(paintSpecimen, Map.of("sample-color", sampleColor), "pspec");
        } else {
            track("loading paint specimen image");
            if (!paintSpecimenPath.endsWith("pspec")) {
                trace("invalid paint specimen");
                return;
            }
            paintSpecimen = new FastRGB(handlerFromFile(paintSpecimenPath));
            sampleColor = readCustomMetadata(new File(paintSpecimenPath), "sample-color");
            if (sampleColor == null) {
                trace("sample color from specimen not found, specimen corrupted");
                return;
            }
        }

        endTrack();
        trace("iterating through all cels");
        for (File celFile : allCels) {
            track("opening cel " + celFile.getName());
            ImageHandler cel = handlerFromFile(celFile);
            track("getting outline location");
            Point magicWandPt = ImageEyedropperFrame.pickPoint(cel);
            //Point magicWandPt = new Point(4544, 1055);
            track("getting magic wand selection");
            //PointCollection outlinePoints = getBlackGroups(cel).stream().filter(g -> g.contains(magicWandPt)).findFirst().orElseThrow();
            PointCollection outlinePoints = filterPixelsToLocalGroup(cel, magicWandPt, 3, blackQual);
            track("finalizing magic wand selection");
            Photoshop paintedFrame = new Photoshop(cel, false);
            ShapeContour outerEdge = new ShapeContour(outlinePoints);
            PointCollection innerPts = varMutate(new PointCollection(outlinePoints), PointCollection::fillHoles).subtract(outlinePoints);
            ShapeContour paintRegion = new ShapeContour(innerPts.expand((int) (outerEdge.nearest(magicWandPt)).distance(innerPts.closest(magicWandPt)) / 2));
                    //paintRegion = outerEdge.halfway(innerEdge);
            //DebuggerEx.vis("a", cel.getImage(), paintRegion);
            track("adding sample color layer");
            paintedFrame.addTransparentLayer("Sample Color").fill(Color.decode(sampleColor));
            paintedFrame.setBlendMode(1, BlendMode.MULTIPLY);
            paintedFrame.createLayerMask(1, paintRegion);
            track("spreading paint specimen across shape");
            FastRGB noiseImage = new FastRGB(cel.getWidth(), cel.getHeight(), false);
            Rectangle toFill = paintRegion.toPointCollection().imgRect();
            if (paintSpecimen.getWidth() > toFill.width && paintSpecimen.getHeight() > toFill.height) {
                trace("case 1");
                int xDist = Math.abs((toFill.width - paintSpecimen.getWidth()) / 2),
                        yDist = Math.abs((toFill.height - paintSpecimen.getHeight()) / 2);
                new CoordIterator(toFill.x - xDist, toFill.x + xDist, toFill.y - yDist, toFill.y + yDist).execute((x, y) -> {
                    noiseImage.setHSV(x, y, paintSpecimen.getHSV((x - (toFill.x - xDist)) % (paintSpecimen.getWidth()), (y - (toFill.y - yDist)) % (paintSpecimen.getHeight())));
                });
            } else if (
                    MathEx.withinPos(toFill.width, paintSpecimen.getWidth(), SCALE_DIFFERENCE)
                            &&
                            MathEx.withinPos(toFill.height, paintSpecimen.getHeight(), SCALE_DIFFERENCE)
            ) {
                trace("case 2");
                paintSpecimen.upscale(Math.max(MathEx.divide(toFill.width, paintSpecimen.getWidth()), MathEx.divide(toFill.height, paintSpecimen.getHeight())) + 1);
                int xDist = Math.abs((toFill.width - paintSpecimen.getWidth()) / 2),
                        yDist = Math.abs((toFill.height - paintSpecimen.getHeight()) / 2);
                new CoordIterator(toFill.x - xDist, toFill.x + xDist, toFill.y - yDist, toFill.y + yDist).execute((x, y) -> {
                    noiseImage.setHSV(x, y, paintSpecimen.getHSV(x, y));
                });
            } else {
                trace("case 3");

                int specimenW = paintSpecimen.getWidth();
                int specimenH = paintSpecimen.getHeight();

                int tilesX = MathEx.ceilInt(MathEx.divide(toFill.width, specimenW));
                int tilesY = MathEx.ceilInt(MathEx.divide(toFill.height, specimenH));
                int initialTileCount = tilesX * tilesY;

                if (initialTileCount > MAX_INITIAL_TILES) {
                    //double requiredScaleX = MathEx.divide(toFill.width, specimenW * Math.max(1, MathEx.ceilInt(Math.sqrt(MAX_INITIAL_TILES))));
                    //double requiredScaleY = MathEx.divide(toFill.height, specimenH * Math.max(1, MathEx.ceilInt(Math.sqrt(MAX_INITIAL_TILES))));
                    //double candidateScale = Math.max(requiredScaleX, requiredScaleY);

                    //double appliedScale = Math.min(1.0 + MAX_TILE_UPSCALE_PERCENT, Math.max(1.0, candidateScale));
                    //double appliedScale = Math.min(1.0 + MAX_TILE_UPSCALE_PERCENT);
                    double appliedScale = MAX_TILE_UPSCALE_PERCENT;
                    if (appliedScale > 1.0) {
                        paintSpecimen.upscale(appliedScale * 100);
                        specimenW = paintSpecimen.getWidth();
                        specimenH = paintSpecimen.getHeight();
                    } else {
                        specimenH = paintSpecimen.getHeight();
                        specimenW = paintSpecimen.getWidth();
                    }
                } else {
                    specimenH = paintSpecimen.getHeight();
                    specimenW = paintSpecimen.getWidth();
                }

                tilesX = Math.max(1, MathEx.ceilInt(MathEx.divide(toFill.width, specimenW)));
                trace(String.valueOf(tilesX));
                tilesY = Math.max(1, MathEx.ceilInt(MathEx.divide(toFill.height, specimenH)));

                int coveredWidth = tilesX * specimenW;
                int coveredHeight = tilesY * specimenH;

                int startX = toFill.x - Math.max(0, (coveredWidth - toFill.width) / 2);
                int startY = toFill.y - Math.max(0, (coveredHeight - toFill.height) / 2);
/*
                for (int tileX = 0; tileX < tilesX; tileX++) {
                    for (int tileY = 0; tileY < tilesY; tileY++) {
                        int drawX = startX + tileX * specimenW;
                        int drawY = startY + tileY * specimenH;

                        int copyStartX = Math.max(0, drawX);
                        int copyStartY = Math.max(0, drawY);
                        int copyEndX = Math.min(cel.getWidth(), drawX + specimenW);
                        int copyEndY = Math.min(cel.getHeight(), drawY + specimenH);

                        if (copyStartX >= copyEndX || copyStartY >= copyEndY) {
                            continue;
                        }

                        new CoordIterator(copyStartX, copyEndX, copyStartY, copyEndY).execute((x, y) -> {
                            int specimenX = x - drawX;
                            int specimenY = y - drawY;
                            noiseImage.setRGB(x, y, paintSpecimen.getRGBRaw(specimenX, specimenY));
                        });
                    }
                }*/
                int totalTiles = tilesX * tilesY;
                int tilesPerTask = 2; // configurable
                int threadCount = Runtime.getRuntime().availableProcessors();

                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                List<Future<?>> futures = new ArrayList<>();

                for (int startTile = 0; startTile < totalTiles; startTile += tilesPerTask) {
                    int taskStartTile = startTile;
                    int taskEndTile = Math.min(totalTiles, taskStartTile + tilesPerTask);

                    int finalTilesX = tilesX;
                    int finalSpecimenW = specimenW;
                    int finalSpecimenH = specimenH;
                    futures.add(executor.submit(() -> {
                        for (int tile = taskStartTile; tile < taskEndTile; tile++) {
                            int tileX = tile % finalTilesX;
                            int tileY = tile / finalTilesX;

                            int drawX = startX + tileX * finalSpecimenW;
                            int drawY = startY + tileY * finalSpecimenH;

                            int copyStartX = Math.max(0, drawX);
                            int copyStartY = Math.max(0, drawY);
                            int copyEndX = Math.min(cel.getWidth(), drawX + finalSpecimenW);
                            int copyEndY = Math.min(cel.getHeight(), drawY + finalSpecimenH);

                            if (copyStartX >= copyEndX || copyStartY >= copyEndY) {
                                continue;
                            }

                            new CoordIterator(copyStartX, copyEndX, copyStartY, copyEndY).execute((x, y) -> {
                                int specimenX = x - drawX;
                                int specimenY = y - drawY;
                                noiseImage.setRGB(x, y, paintSpecimen.getRGBRaw(specimenX, specimenY));
                            });
                        }
                    }));
                }

                for (Future<?> future : futures) {
                    future.get();
                }

                executor.shutdown();
            }

            paintedFrame.addLayer(new Layer(noiseImage, false));
            paintedFrame.setBlendMode(2, BlendMode.SUBTRACT);
            paintedFrame.createClippingMask(2);
            outputCel(paintedFrame.flatten(), allCels.size() > 1 ? outputCelPath + "/" + celFile.getName() : outputCelPath);
        }

    }
}
