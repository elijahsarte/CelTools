package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.FrameParser;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.imageworkshop.*;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionManager;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionWindow;
import com.elijahsarte.celtools.main.util.ConstructionEx;
import com.elijahsarte.celtools.main.util.GraphicsEx;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.function.fnvals.BiTuple;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;
import com.elijahsarte.celtools.main.util.structures.collections.IntList;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointIterator;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;
import com.elijahsarte.celtools.main.util.typeex.nullable.Nullable;
import com.elijahsarte.celtools.mainex.Debugger;
import com.elijahsarte.celtools.mainex.DebuggerEx;
import com.elijahsarte.celtools.mainex.TaskTracker;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;
import static com.elijahsarte.celtools.mainex.TaskTracker.*;

public class CelPaintFill extends Main {

    private static final int NECESSARY_BYTE_ARRAYS = 3;
    private static final int NECESSARY_INT_ARRAYS = 7;

    private static final double SCALE_DIFFERENCE = 10;
    private static final int MAX_INITIAL_TILES = 10;
    private static final double MAX_TILE_UPSCALE_PERCENT = 4.64827586207;

    private static final boolean DEBUG_ADAPTIVE_TILING = false;
    private static int adaptiveTilingDebugId;

    private FastRGB paintSpecimen;

    public CelPaintFill(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws Exception {
        List<File> allCels = List.of(Optional.ofNullable(args.get("all-cels")).map(File::new).map(f -> f.isDirectory() ? sortedFilesByName(f.listFiles()) : new File[] { f }).orElseGet(() -> new File[] { new File(safeArg("cel", "File path of cel:")) }));
        Map<Color, File> paintSpecimenMap = mapArg("paint-specimens").entrySet().stream().collect(Collectors.toMap(
                entry -> {
                    double[] rgb = colorFromString(entry.getKey());
                    return new Color((int) rgb[0], (int) rgb[1], (int) rgb[2]);
                },
                entry -> new File(entry.getValue()),
                (first, second) -> second,
                LinkedHashMap::new
        ));
        List<File> allPaintedCels = List.of(Optional.ofNullable(paintSpecimenMap.isEmpty()
                ? null
                : new File(safeArg("all-painted-cels", "All painted cel(s):"))).map(f -> f.isDirectory () ? sortedFilesByName(f.listFiles()) : new File[] { f }).orElse(new File[] {}));

        List<FastRGB> mappedPaintSpecimens = new ArrayList<>(paintSpecimenMap.size());
        List<String> mappedSampleColors = new ArrayList<>(paintSpecimenMap.size());
        FreeformSelectionManager.saving = false;
        for (File specimenFile : paintSpecimenMap.values()) {
            if (specimenFile.getName().toLowerCase().endsWith(".pspec")) {
                String mappedSampleColor = readCustomMetadata(specimenFile, "sample-color");
                if (mappedSampleColor == null) {
                    throw new IllegalArgumentException("Sample color not found in paint specimen: " + specimenFile);
                }
                mappedSampleColors.add(mappedSampleColor);
                mappedPaintSpecimens.add(varMutate(new FastRGB(handlerFromFile(specimenFile)), FastRGB::imposeAlphaChannel));
            } else {
                track("prompting user to create paint specimen from " + specimenFile.getName());
                ImageHandler paintImageHandler = handlerFromFile(specimenFile);
                Color samplePoint = new FreeformSelectionManager(paintImageHandler.getImage(), "Pick Sample Point").getSelectedColor();
                String mappedSampleColor = GraphicsEx.toHex(samplePoint);
                BufferedImage specimenSection = varOper(
                        cropBoxSelection(paintImageHandler, "Select Paint Specimen Section"),
                        rectangle -> paintImageHandler.getImage().getSubimage(
                                rectangle.x, rectangle.y, rectangle.width, rectangle.height
                        )
                );
                track("crafting paint specimen from " + specimenFile.getName());
                Photoshop specimenDocument = new Photoshop(specimenSection);
                specimenDocument.duplicateLayer(0);
                specimenDocument.applyFilter(0, Filter::average);
                specimenDocument.setBlendMode(1, BlendMode.DIFFERENCE);
                specimenDocument.mergeDown(1);
                FastRGB mappedSpecimen = specimenDocument.flatten();
                outputCel(chooserScopeKey(), mappedSpecimen, Map.of("sample-color", mappedSampleColor), "pspec");
                mappedSampleColors.add(mappedSampleColor);
                mappedPaintSpecimens.add(mappedSpecimen);
                endTrack();
            }
        }
        FreeformSelectionManager.saving = true;

        String paintSpecimenPath = args.get("paint-specimen"),
                paintImagePath = args.get("paint-image"),
                outputCelPath = safeArg("output-cel", "Output cel path:"),
                sampleColor;

        if (allCels.size() > 1 && !new File(outputCelPath).isDirectory()) {
            trace("loading multiple cels, output cel must be folder");
            return;
        }

        CompletableFuture<FastRGB> paintSpecimenFuture;
        if (!paintSpecimenMap.isEmpty()) {
            paintSpecimenFuture = CompletableFuture.completedFuture(null);
            sampleColor = null;
        } else if (paintSpecimenPath == null) {
            FreeformSelectionManager.saving = false;
            paintSpecimenFuture = new CompletableFuture<>();
            paintSpecimenFuture.complete(null);
            track("prompting user to create paint specimen");
            ImageHandler paintImageHandler = handlerFromFile(paintImagePath);
            sampleColor = GraphicsEx.toHex(new FreeformSelectionManager(paintImageHandler.getImage(), "Pick Sample Point").getSelectedColor());
            BufferedImage colorSpecimenSection = varOper(cropBoxSelection(paintImageHandler, "Select Paint Specimen Section"), r -> paintImageHandler.getImage().getSubimage(r.x, r.y, r.width, r.height));
            FreeformSelectionManager.saving = true;
            track("crafting paint specimen");
            Photoshop specimenDoc = new Photoshop(colorSpecimenSection);
            specimenDoc.duplicateLayer(0);
            specimenDoc.applyFilter(0, Filter::average);
            specimenDoc.setBlendMode(1, BlendMode.DIFFERENCE);
            specimenDoc.mergeDown(1);
            paintSpecimen = specimenDoc.flatten();
            outputCel(chooserScopeKey(), paintSpecimen, Map.of("sample-color", sampleColor), "pspec");
        } else {
            trackAsync("loading paint specimen image");
            if (!paintSpecimenPath.endsWith(".pspec")) {
                trace("invalid paint specimen");
                return;
            }
            sampleColor = readCustomMetadata(new File(paintSpecimenPath), "sample-color");
            if (sampleColor == null) {
                trace("sample color from specimen not found, specimen corrupted");
                return;
            }
            paintSpecimenFuture = CompletableFuture.supplyAsync(() -> forceReturn(varMutate(new FastRGB(handlerFromFile(paintSpecimenPath)), FastRGB::imposeAlphaChannel), () -> TaskTracker.endTrackAsync("loading paint specimen image")));
        }

        endTrack();
        trace("iterating through all cels");
        Iterator<File> celIterator = allCels.iterator(), allPCIt = allPaintedCels.iterator();
        while (celIterator.hasNext()) {
            Debugger.clear();
            File celFile = celIterator.next();
            Nullable<File> paintedCelFile = new Nullable<>(allPCIt.hasNext() ? allPCIt.next() : null);
            CompletableFuture<Nullable<ImageHandler>> paintedCelFuture = CompletableFuture.supplyAsync(() -> paintedCelFile.ifPresent(() -> trackAsync("opening painted cel " + paintedCelFile.get().getName())).map(Main::handlerFromFile).ifPresent(() -> endTrackAsync("opening painted cel " + paintedCelFile.get().getName())));
            track("opening cel " + celFile.getName());
            ImageHandler cel = Debugger.createRef("cel", handlerFromFile(celFile));
            Nullable<ImageHandler> paintedCel = Debugger.createRef("paintedCel", paintedCelFuture.join());
            if (paintedCel.isSet() && (paintedCel.get().getWidth() != cel.getWidth() || paintedCel.get().getHeight() != cel.getHeight())) {
                throw new IllegalArgumentException("Painted cel dimensions must match cel dimensions: " + celFile.getName());
            }
            Main.async(() -> {
                boolean res = clearIntArrayResources(cel.getWidth() * cel.getHeight());
                int bArrsCt = ConstructionEx.getAvailableArrayCount(byte.class, cel.getWidth() * cel.getHeight());
                if (bArrsCt == 0) ConstructionEx.forceReleaseAllArrays(byte.class);
                if (bArrsCt < NECESSARY_BYTE_ARRAYS) {
                    ConstructionEx.preAllocateByteArrays(cel.getWidth() * cel.getHeight(), NECESSARY_BYTE_ARRAYS - bArrsCt);
                }
                if (res) return;
                int iArrsCount = ConstructionEx.getAvailableArrayCount(int.class, cel.getWidth() * cel.getHeight());
                if (iArrsCount == 0) ConstructionEx.forceReleaseAllArrays(int.class);
                if (iArrsCount < NECESSARY_INT_ARRAYS) {
                    ConstructionEx.preAllocateIntArrays(cel.getWidth() * cel.getHeight(), NECESSARY_INT_ARRAYS - iArrsCount);
                }
            });

            track("getting outline location");
            Point magicWandPt = Debugger.createRef("magicWandPt", findLargestMiniGroupAnchor(cel, INK_SCAN_DISTANCE, inkQual));
            if (paintedCel.isSet()) track("getting anchor locations");
            else trackFree("getting anchor locations");
            List<Point> innerAnchorPts = new ArrayList<>();
            if (paintedCel.isSet()) {
                Predicate<double[]>[] paintQualifiers = paintSpecimenMap.keySet().stream().map(color -> {
                    double[] targetHsv = FrameParser.RGBtoHSV(color.getRed(), color.getGreen(), color.getBlue(), new double[3]);
                    return (Predicate<double[]>) hsv -> MathEx.betweenClosed(hsv[0], Math.floor(targetHsv[0]) - 1, Math.ceil(targetHsv[0]) + 1) && MathEx.betweenClosed(hsv[1], Math.floor(targetHsv[1]) - 1, Math.ceil(targetHsv[1]) + 1) && MathEx.betweenClosed(hsv[2], Math.floor(targetHsv[2]) - 3, Math.ceil(targetHsv[2]) + 3);
                }).toArray(Predicate[]::new);
                innerAnchorPts.addAll(findLargestMiniGroupAnchors(paintedCel.get(), 1, false, paintQualifiers));
                if (previewShapeData()) DebuggerEx.vis("automatically determined inner points", innerAnchorPts.stream().map(PointCollection::new).map(p -> p.expand(50)));
            }
            if (!paintedCel.isSet() || innerAnchorPts.isEmpty()) {
                innerAnchorPts.addAll(new FreeformSelectionManager(
                        cel.getImage(),
                        "Select Inner Parts",
                        FreeformSelectionWindow.SelectionMode.MULTI_POINT
                ).getSelectedPoints());
            }
            Debugger.createRef("innerAnchorPts", innerAnchorPts);
            endTrack();
            Main.async(() -> ConstructionEx.preAllocateIntArrays(cel.getWidth() * cel.getHeight(), innerAnchorPts.size() - 1));
            trackAsync("getting outline points");
            CompletableFuture<BiTuple<PointCollection, ShapeContour>> outlinePointsFuture = CompletableFuture.supplyAsync(() -> {
                PointCollection outlinePoints = Debugger.createRef("outline_points", filterPixelsToLocalGroup(cel, magicWandPt, INK_SCAN_DISTANCE, inkQual));
                ShapeContour outerEdge = new ShapeContour(outlinePoints);
                endTrackAsync("getting outline points");
                return new BiTuple<>(outlinePoints, outerEdge);
            });
            track("deriving inner parts");
            List<PointCollection> innerPts = Debugger.createRef("innerPts", innerAnchorPts.parallelStream().map(p -> filterPixelsToLocalGroup(cel, p, 1, Predicate.not(inkQual))).filter(Predicate.not(PointCollection::isEmpty)).toList());
            if (innerPts.isEmpty()) {
                trace("no inner parts given");
                return;
            }
            if (previewShapeData()) DebuggerEx.vis("inner parts", cel.getImage(), innerPts);
            ShapeContour outerEdge = outlinePointsFuture.get().second();
            Point outerMagicWandPt = outerEdge.nearest(magicWandPt);
            Point innerPtClosestToWand = innerPts.stream().map(p -> p.closest(outerMagicWandPt)).min(Comparator.comparingDouble(p -> p.distance(outerMagicWandPt))).orElseThrow();
            if (previewShapeData()) {
                DebuggerEx.vis("outline points", cel.getImage(), outlinePointsFuture.get().first());
                DebuggerEx.vis("outline edge", cel.getImage(), outerEdge);
            }
            int expansionDist = Debugger.createRef("expansionDist", (int) Math.max(1, (outerMagicWandPt.distance(innerPtClosestToWand) / (3d))));
            List<ShapeContour> paintRegions = innerPts.parallelStream().map(p -> new ShapeContour(p.expand(expansionDist))).toList();
            if (previewShapeData()) DebuggerEx.vis("paint regions", cel.getImage(), paintRegions);
            List<FastRGB> paintSpecimens;
            List<String> sampleColors;
            if (paintSpecimenMap.isEmpty()) {
                if (paintSpecimen == null) paintSpecimen = paintSpecimenFuture.join();
                paintSpecimens = Collections.nCopies(innerPts.size(), paintSpecimen);
                sampleColors = Collections.nCopies(innerPts.size(), sampleColor);
            } else {
                paintSpecimens = mappedPaintSpecimens;
                sampleColors = mappedSampleColors;
            }
            if (paintSpecimens.size() != innerPts.size()) {
                throw new IllegalStateException("Paint specimen count must match detected paint-region count");
            }

            trackAsync("creating photoshop structure");
            Photoshop paintedFrame = new Photoshop(cel.getImage(), false);
            List<LayerMask> regionMasks = new ArrayList<>();
            CompletableFuture<Void> paintedFrameFuture = CompletableFuture.runAsync(() -> {
                for (int regionIndex = 0; regionIndex < paintRegions.size(); regionIndex++) {
                    int layerIndex = 1 + regionIndex;
                    paintedFrame.addTransparentLayer("Sample Color " + (regionIndex + 1)).fill(Color.decode(sampleColors.get(regionIndex)));
                    paintedFrame.setBlendMode(layerIndex, BlendMode.MULTIPLY);
                    regionMasks.add(paintedFrame.createLayerMask(layerIndex, paintRegions.get(regionIndex)));
                }
                endTrackAsync("creating photoshop structure");
            });

            track("spreading paint specimen across shape");
            List<FastRGB> noiseImages = new ArrayList<>(innerPts.size());
            for (int regionIndex = 0; regionIndex < innerPts.size(); regionIndex++) {
                PointCollection region = innerPts.get(regionIndex);
                FastRGB specimen = paintSpecimens.get(regionIndex);
                Rectangle toFill = region.imgRect();
                FastRGB hereNoiseImage = new FastRGB(cel.getWidth(), cel.getHeight(), true);

                if (specimen.getWidth() > toFill.width && specimen.getHeight() > toFill.height) {
                    int drawX = toFill.x + (toFill.width - specimen.getWidth()) / 2;
                    int drawY = toFill.y + (toFill.height - specimen.getHeight()) / 2;
                    copyClipped(hereNoiseImage, specimen, drawX, drawY);
                } else if (
                        MathEx.withinPos(toFill.width, specimen.getWidth(), SCALE_DIFFERENCE)
                                && MathEx.withinPos(toFill.height, specimen.getHeight(), SCALE_DIFFERENCE)
                ) {
                    FastRGB scaledSpecimen = FastRGB.upscale(
                            specimen,
                            Math.max(
                                    MathEx.divide(toFill.width, specimen.getWidth()),
                                    MathEx.divide(toFill.height, specimen.getHeight())
                            ) + 1
                    );
                    int drawX = toFill.x + (toFill.width - scaledSpecimen.getWidth()) / 2;
                    int drawY = toFill.y + (toFill.height - scaledSpecimen.getHeight()) / 2;
                    copyClipped(hereNoiseImage, scaledSpecimen, drawX, drawY);
                } else {
                    FastRGB specimenHere = specimen;
                    if (Math.max(1, MathEx.ceilInt(MathEx.divide(toFill.width, specimen.getWidth())))
                            * Math.max(1, MathEx.ceilInt(MathEx.divide(toFill.height, specimen.getHeight())))
                            > MAX_INITIAL_TILES) {
                        specimenHere = FastRGB.upscale(specimen, MAX_TILE_UPSCALE_PERCENT * 100);
                    }
                    tileRegionAdaptively(
                            hereNoiseImage,
                            specimenHere,
                            region.expand(expansionDist),
                            cel.getWidth(),
                            cel.getHeight()
                    );
                }
                noiseImages.add(hereNoiseImage);
            }
            endTrack();

            paintedFrameFuture.join();
            track("imposing noise on cel");
            int noiseLayerStart = 1 + paintRegions.size();
            for (int regionIndex = 0; regionIndex < noiseImages.size(); regionIndex++) {
                int layerIndex = noiseLayerStart + regionIndex;
                paintedFrame.addLayer(new Layer(noiseImages.get(regionIndex), false));
                paintedFrame.setBlendMode(layerIndex, BlendMode.SUBTRACT);
                paintedFrame.createClippingMask(layerIndex, regionMasks.get(regionIndex));
            }
            endTrack();
            outputCel(paintedFrame.flatten(), allCels.size() > 1 ? outputCelPath + File.separator + celFile.getName() : outputCelPath);
            ConstructionEx.resetAllArrays();
        }

    }

    private static void tileRegionAdaptively(
            FastRGB destination,
            FastRGB specimen,
            PointCollection region,
            int imageWidth,
            int imageHeight
    ) {
        int debugId = ++adaptiveTilingDebugId;

        if (DEBUG_ADAPTIVE_TILING) {
            System.out.println("\n=== ADAPTIVE TILE REGION " + debugId + " ===");
            printImageData("Destination before", destination);
            printImageData("Specimen", specimen);

            System.out.println(
                    "Passed canvas: " + imageWidth + "x" + imageHeight
            );
        }

        RegionMask mask = RegionMask.create(
                region,
                destination.getWidth(),
                destination.getHeight()
        );

        if (mask == null) {
            System.out.println(
                    "REGION " + debugId +
                            " ABORTED: no region points were inside the destination"
            );
            return;
        }

        int tileWidth = specimen.getWidth();
        int tileHeight = specimen.getHeight();

        if (DEBUG_ADAPTIVE_TILING) {
            System.out.println(
                    "Mask bounds: x=" + mask.minX +
                            ".." + mask.maxX +
                            ", y=" + mask.minY +
                            ".." + mask.maxY
            );

            System.out.println(
                    "Mask dimensions: " +
                            (mask.maxX - mask.minX + 1) + "x" +
                            (mask.maxY - mask.minY + 1)
            );

            System.out.println(
                    "Pixels inside mask: " + mask.insidePixelCount()
            );

            System.out.println(
                    "Tile dimensions: " + tileWidth + "x" + tileHeight
            );
        }

        int iteration = 0;
        long totalWritten = 0;
        long totalNonZeroWritten = 0;

        while (mask.hasUncoveredPixels()) {
            iteration++;

            long remainingBefore = mask.uncoveredPixelCount();
            Point anchor = mask.nextUncoveredPixel();

            IntSpan horizontalSpan = mask.horizontalSpan(anchor.x, anchor.y);
            IntSpan verticalSpan = mask.verticalSpan(anchor.x, anchor.y);

            Set<Integer> candidateXs = tileStarts(
                    anchor.x,
                    horizontalSpan,
                    mask.minX,
                    mask.maxX,
                    tileWidth
            );

            Set<Integer> candidateYs = tileStarts(
                    anchor.y,
                    verticalSpan,
                    mask.minY,
                    mask.maxY,
                    tileHeight
            );

            TileCandidate best = null;

            for (int drawX : candidateXs) {
                if (drawX > anchor.x || drawX + tileWidth <= anchor.x) {
                    continue;
                }

                for (int drawY : candidateYs) {
                    if (drawY > anchor.y || drawY + tileHeight <= anchor.y) {
                        continue;
                    }

                    TileCandidate candidate = scoreTile(
                            mask,
                            drawX,
                            drawY,
                            tileWidth,
                            tileHeight,
                            horizontalSpan,
                            verticalSpan
                    );

                    if (best == null || candidate.score > best.score) {
                        best = candidate;
                    }
                }
            }

            if (best == null) {
                best = new TileCandidate(
                        anchor.x - tileWidth / 2,
                        anchor.y - tileHeight / 2,
                        0
                );

                System.out.println(
                        "WARNING: iteration " + iteration +
                                " required fallback candidate"
                );
            }

            long expectedCoverage = mask.countUncovered(
                    best.x,
                    best.y,
                    tileWidth,
                    tileHeight
            );

            CopyResult copied = copyClippedDebug(
                    destination,
                    specimen,
                    best.x,
                    best.y
            );

            totalWritten += copied.writtenPixels;
            totalNonZeroWritten += copied.nonZeroPixels;

            mask.markCovered(
                    best.x,
                    best.y,
                    tileWidth,
                    tileHeight
            );

            long remainingAfter = mask.uncoveredPixelCount();

            if (DEBUG_ADAPTIVE_TILING
                    && (iteration <= 20 || iteration % 25 == 0)) {
                System.out.println(
                        "Iteration " + iteration +
                                ": anchor=(" + anchor.x + "," + anchor.y + ")" +
                                ", horizontalSpan=" + horizontalSpan +
                                ", verticalSpan=" + verticalSpan +
                                ", candidates=" + candidateXs.size() +
                                "x" + candidateYs.size() +
                                ", tile=(" + best.x + "," + best.y + ")" +
                                ", expectedCoverage=" + expectedCoverage +
                                ", copied=" + copied.writtenPixels +
                                ", copiedNonZero=" + copied.nonZeroPixels +
                                ", copiedOpaque=" + copied.opaquePixels +
                                ", remaining=" + remainingBefore +
                                " -> " + remainingAfter
                );
            }

            if (remainingAfter >= remainingBefore) {
                System.out.println(
                        "ERROR: iteration " + iteration +
                                " made no mask progress; stopping to prevent an infinite loop"
                );
                break;
            }
        }

        if (DEBUG_ADAPTIVE_TILING) {
            System.out.println(
                    "Region " + debugId +
                            " completed in " + iteration + " tiles"
            );

            System.out.println(
                    "Total copied pixels: " + totalWritten +
                            ", nonzero copied pixels: " + totalNonZeroWritten
            );

            printImageData("Destination after", destination);
            System.out.println("=== END REGION " + debugId + " ===\n");
        }
    }

    private static Set<Integer> tileStarts(
            int anchor,
            IntSpan localSpan,
            int regionStart,
            int regionEnd,
            int tileSize
    ) {
        Set<Integer> starts = new LinkedHashSet<>();

        /*
         * Centering candidates. The local-span candidate handles thin portions:
         * if the tile is larger than the span, both tile edges land outside it.
         */
        starts.add(centeredStart(localSpan.start, localSpan.end, tileSize));
        starts.add(centeredStart(regionStart, regionEnd, tileSize));
        starts.add(anchor - tileSize / 2);

        /*
         * Boundary-aligned candidates. When multiple tiles are required, these
         * keep one tile boundary directly against a local contour edge.
         */
        starts.add(localSpan.start);
        starts.add(localSpan.end - tileSize + 1);
        starts.add(regionStart);
        starts.add(regionEnd - tileSize + 1);

        /*
         * Place the anchor near either tile edge. This is useful for narrow
         * leftover fragments after previous tiles have covered most of a region.
         */
        starts.add(anchor);
        starts.add(anchor - tileSize + 1);

        return starts;
    }

    private static int centeredStart(int spanStart, int spanEnd, int tileSize) {
        return Math.floorDiv(spanStart + spanEnd - tileSize + 1, 2);
    }

    private static TileCandidate scoreTile(
            RegionMask mask,
            int x,
            int y,
            int width,
            int height,
            IntSpan horizontalSpan,
            IntSpan verticalSpan
    ) {
        long newlyCovered = mask.countUncovered(x, y, width, height);

        /*
         * Any contour pixels touched by a tile perimeter can expose a texture
         * discontinuity. Corners receive a much larger penalty because that is
         * where four ordinary grid tiles would intersect.
         */
        long edgePixels = mask.countPerimeterPixels(x, y, width, height);
        long cornerPixels = mask.countCornerNeighborhoodPixels(
                x,
                y,
                width,
                height,
                2
        );

        int idealX = centeredStart(
                horizontalSpan.start,
                horizontalSpan.end,
                width
        );
        int idealY = centeredStart(
                verticalSpan.start,
                verticalSpan.end,
                height
        );

        long centeringError =
                Math.abs((long) x - idealX)
                        + Math.abs((long) y - idealY);

        long score =
                newlyCovered * 4096L
                        - edgePixels * 256L
                        - cornerPixels * 8192L
                        - centeringError;

        return new TileCandidate(x, y, score);
    }


    private static CopyResult copyClippedDebug(
            FastRGB destination,
            FastRGB specimen,
            int drawX,
            int drawY
    ) {
        int startX = Math.max(0, drawX);
        int startY = Math.max(0, drawY);
        int endX = Math.min(
                destination.getWidth(),
                drawX + specimen.getWidth()
        );
        int endY = Math.min(
                destination.getHeight(),
                drawY + specimen.getHeight()
        );

        if (startX >= endX || startY >= endY) {
            if (DEBUG_ADAPTIVE_TILING) {
                System.out.println(
                        "COPY REJECTED: draw=(" + drawX + "," + drawY + ")" +
                                ", destination=" + destination.getWidth() +
                                "x" + destination.getHeight() +
                                ", specimen=" + specimen.getWidth() +
                                "x" + specimen.getHeight() +
                                ", clipped=[" + startX + "," + startY +
                                " -> " + endX + "," + endY + "]"
                );
            }

            return new CopyResult(0, 0, 0);
        }

        long written = 0;
        long nonZero = 0;
        long opaque = 0;

        for (int y = startY; y < endY; y++) {
            int sourceY = y - drawY;

            for (int x = startX; x < endX; x++) {
                int sourceX = x - drawX;

                int argb =
                        specimen.getRGBRaw(sourceX, sourceY)
                                | 0xFF000000;

                destination.setRGB(x, y, argb);

                written++;
                nonZero++;
                opaque++;
            }
        }
        return new CopyResult(written, nonZero, opaque);
    }

    private static void printImageData(String name, FastRGB image) {
        int[] pixels = image.getPixels();

        long nonZero = 0;
        long nonTransparent = 0;

        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int index = 0; index < pixels.length; index++) {
            int argb = pixels[index];

            if (argb != 0) {
                nonZero++;

                int x = index % image.getWidth();
                int y = index / image.getWidth();

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }

            if ((argb >>> 24) != 0) {
                nonTransparent++;
            }
        }

        System.out.println(
                name + ": dimensions=" +
                        image.getWidth() + "x" + image.getHeight() +
                        ", arrayLength=" + pixels.length +
                        ", nonZero=" + nonZero +
                        ", nonTransparent=" + nonTransparent +
                        ", dataBounds=" +
                        (maxX < 0
                                ? "EMPTY"
                                : "[" + minX + "," + minY +
                                  " -> " + maxX + "," + maxY + "]")
        );
    }

    private record CopyResult(
            long writtenPixels,
            long nonZeroPixels,
            long opaquePixels
    ) {}

    private static void copyClipped(
            FastRGB destination,
            FastRGB specimen,
            int drawX,
            int drawY
    ) {
        int destinationStartX = Math.max(0, drawX);
        int destinationStartY = Math.max(0, drawY);
        int destinationEndX = Math.min(
                destination.getWidth(),
                drawX + specimen.getWidth()
        );
        int destinationEndY = Math.min(
                destination.getHeight(),
                drawY + specimen.getHeight()
        );

        if (destinationStartX >= destinationEndX
                || destinationStartY >= destinationEndY) {
            return;
        }

        for (int y = destinationStartY; y < destinationEndY; y++) {
            int sourceY = y - drawY;

            for (int x = destinationStartX; x < destinationEndX; x++) {
                int sourceX = x - drawX;

                destination.setRGB(
                        x,
                        y,
                        specimen.getRGBRaw(sourceX, sourceY)
                );
            }
        }
    }

    private record TileCandidate(int x, int y, long score) {}

    private record IntSpan(int start, int end) {}

    private static final class RegionMask {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;

        private final BitSet[] inside;
        private final BitSet[] uncovered;

        private RegionMask(
                int minX,
                int minY,
                int maxX,
                int maxY,
                BitSet[] inside,
                BitSet[] uncovered
        ) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.inside = inside;
            this.uncovered = uncovered;
        }

        long insidePixelCount() {
            long count = 0;

            for (BitSet row : inside) {
                count += row.cardinality();
            }

            return count;
        }

        long uncoveredPixelCount() {
            long count = 0;

            for (BitSet row : uncovered) {
                count += row.cardinality();
            }

            return count;
        }

        static RegionMask create(
                PointCollection region,
                int imageWidth,
                int imageHeight
        ) {
            int minX = imageWidth;
            int minY = imageHeight;
            int maxX = -1;
            int maxY = -1;
/*
            for (Point point : region) {
                if (point.x < 0 || point.y < 0
                        || point.x >= imageWidth || point.y >= imageHeight) {
                    continue;
                }

                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }*/

            Point left   = region.leftPoint();
            Point right  = region.rightPoint();
            Point top    = region.topPoint();
            Point bottom = region.bottomPoint();

            int rawMinX = left.x;
            int rawMaxX = right.x;
            int rawMinY = bottom.y;
            int rawMaxY = top.y;

            minX = Math.max(0, rawMinX);
            minY = Math.max(0, rawMinY);
            maxX = Math.min(imageWidth - 1, rawMaxX);
            maxY = Math.min(imageHeight - 1, rawMaxY);

            if (maxX < minX || maxY < minY) return null;

            int width = maxX - minX + 1;
            int height = maxY - minY + 1;

            BitSet[] inside = new BitSet[height];

            for (int y = 0; y < height; y++) {
                inside[y] = new BitSet(width);
            }

//            PointIterator rIt = region.iterator();
            PointIterator rIt = region.iterator();
            while (rIt.hasNext()) {
                Map.Entry<Integer, IntList> entry = rIt.nextEntry();
                int x = entry.getKey();

                if (x < minX) continue;
                if (x > maxX) break; // X entries are ordered

                IntList ys = entry.getValue();
                ys.onRaw();

                try {
                    IntList.IntListIterator yIt = ys.iterator();

                    while (yIt.hasNextBd()) {
                        IntegerBounds yBound = yIt.nextBd();

                        if (yBound.getUpperBound() < minY) continue;
                        if (yBound.getLowerBound() > maxY) break;

                        int fromY = Math.max(yBound.getLowerBound(), minY);
                        int toY   = Math.min(yBound.getUpperBound(), maxY);

                        for (int y = fromY; y <= toY; y++) {
                            inside[y - minY].set(x - minX);
                        }
                    }
                } finally {
                    ys.offRaw();
                }
            }

            BitSet[] uncovered = new BitSet[height];

            for (int y = 0; y < height; y++) {
                uncovered[y] = (BitSet) inside[y].clone();
            }

            return new RegionMask(
                    minX,
                    minY,
                    maxX,
                    maxY,
                    inside,
                    uncovered
            );
        }

        boolean hasUncoveredPixels() {
            for (BitSet row : uncovered) {
                if (!row.isEmpty()) return true;
            }
            return false;
        }

        Point nextUncoveredPixel() {
            for (int row = 0; row < uncovered.length; row++) {
                int x = uncovered[row].nextSetBit(0);
                if (x >= 0) {
                    return new Point(minX + x, minY + row);
                }
            }
            throw new IllegalStateException("No uncovered pixel exists");
        }

        boolean isInside(int x, int y) {
            if (x < minX || x > maxX || y < minY || y > maxY) {
                return false;
            }

            return inside[y - minY].get(x - minX);
        }

        IntSpan horizontalSpan(int x, int y) {
            BitSet row = inside[y - minY];
            int localX = x - minX;

            int start = localX;
            while (start > 0 && row.get(start - 1)) start--;

            int end = localX;
            while (end < maxX - minX && row.get(end + 1)) end++;

            return new IntSpan(minX + start, minX + end);
        }

        IntSpan verticalSpan(int x, int y) {
            int start = y;
            while (start > minY && isInside(x, start - 1)) start--;

            int end = y;
            while (end < maxY && isInside(x, end + 1)) end++;

            return new IntSpan(start, end);
        }
/*
        long countUncovered(int x, int y, int width, int height) {
            int startX = Math.max(x, minX);
            int endX = Math.min(x + width - 1, maxX);
            int startY = Math.max(y, minY);
            int endY = Math.min(y + height - 1, maxY);

            if (startX > endX || startY > endY) return 0;

            long count = 0;
            int localStartX = startX - minX;
            int localEndX = endX - minX;

            for (int yy = startY; yy <= endY; yy++) {
                BitSet row = uncovered[yy - minY];

                for (int bit = row.nextSetBit(localStartX);
                     bit >= 0 && bit <= localEndX;
                     bit = row.nextSetBit(bit + 1)) {
                    count++;
                }
            }

            return count;
        }
*/
long countUncovered(int x, int y, int width, int height) {
    int startX = Math.max(x, minX);
    int endX = Math.min(x + width - 1, maxX);
    int startY = Math.max(y, minY);
    int endY = Math.min(y + height - 1, maxY);

    if (startX > endX || startY > endY) return 0;

    int localStartX = startX - minX;
    int localEndXExclusive = endX - minX + 1;

    long count = 0;

    for (int yy = startY; yy <= endY; yy++) {
        BitSet row = uncovered[yy - minY];

        int bit = row.nextSetBit(localStartX);

        while (bit >= 0 && bit < localEndXExclusive) {
            int clear = row.nextClearBit(bit);

            if (clear > localEndXExclusive) {
                count += localEndXExclusive - bit;
                break;
            }

            count += clear - bit;

            bit = row.nextSetBit(clear);
        }
    }

    return count;
}
        long countPerimeterPixels(int x, int y, int width, int height) {
            long count = 0;
            int right = x + width - 1;
            int bottom = y + height - 1;

            for (int xx = x; xx <= right; xx++) {
                if (isInside(xx, y)) count++;
                if (bottom != y && isInside(xx, bottom)) count++;
            }

            for (int yy = y + 1; yy < bottom; yy++) {
                if (isInside(x, yy)) count++;
                if (right != x && isInside(right, yy)) count++;
            }

            return count;
        }

        long countCornerNeighborhoodPixels(
                int x,
                int y,
                int width,
                int height,
                int radius
        ) {
            int right = x + width - 1;
            int bottom = y + height - 1;

            return countNeighborhood(x, y, radius)
                    + countNeighborhood(right, y, radius)
                    + countNeighborhood(x, bottom, radius)
                    + countNeighborhood(right, bottom, radius);
        }

        private long countNeighborhood(int centerX, int centerY, int radius) {
            long count = 0;

            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    if (isInside(x, y)) count++;
                }
            }

            return count;
        }

        void markCovered(int x, int y, int width, int height) {
            int startX = Math.max(x, minX);
            int endX = Math.min(x + width - 1, maxX);
            int startY = Math.max(y, minY);
            int endY = Math.min(y + height - 1, maxY);

            if (startX > endX || startY > endY) return;

            int localStartX = startX - minX;
            int localEndExclusive = endX - minX + 1;

            for (int yy = startY; yy <= endY; yy++) {
                uncovered[yy - minY].clear(
                        localStartX,
                        localEndExclusive
                );
            }
        }
    }
}
