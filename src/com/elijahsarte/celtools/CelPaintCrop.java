package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.selectionui.SelectionTransform;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;
import com.elijahsarte.celtools.mainex.Debugger;
import com.elijahsarte.celtools.mainex.DebuggerEx;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.printExcept;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.varOper;
import static com.elijahsarte.celtools.mainex.TaskTracker.track;


public class CelPaintCrop extends Main {

    private static final int PREALLOCATED_INT_ARRAYS_PER_CEL = 4;

    public CelPaintCrop(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws Exception {

        File allCelsFile = new File(safeArg("all-cels", "File path to all cel(s):"));
        List<File> allCels = List.of(allCelsFile.isDirectory()
                ? Optional.ofNullable(sortedFilesByName(allCelsFile.listFiles())).orElseGet(() -> new File[] {
                        new File(safeArg("entire-cel", "File path to entire cel(s):"))
                })
                : new File[] {allCelsFile});
        Predicate<double[]> customQual = (args.get("lower-color-bound") != null && args.get("upper-color-bound") != null) ?
                varOper(colorArg("lower-color-bound"), c ->
                    varOper(colorArg("upper-color-bound"), c2 -> hsv -> MathEx.betweenClosed(hsv[0], c[0], c2[0]) && MathEx.betweenClosed(hsv[1], c[1], c2[1]) && MathEx.betweenClosed(hsv[2], c[2], c2[2])))
                : null;

        String outputCelPath = safeArg("output-cel", "File path for final cel:");
        if (allCels.size() > 1 && !new File(outputCelPath).isDirectory()) {
            trace("loading multiple cels, output cel must be folder");
            return;
        }

        track("loading background cel");
        ImageHandler bgCelHandler = handlerFromFile(safeArg("background-cel", "File path to background cel:"));
        clearIntArrayResources(bgCelHandler.getWidth() * bgCelHandler.getHeight());
        FastRGB backgroundCel = new FastRGB(bgCelHandler);
        int imageArrayLength = backgroundCel.getWidth() * backgroundCel.getHeight();

        List<Thread> outputThreads = new ArrayList<>();
        Iterator<File> queuedCelIterator = allCels.iterator();
        if (!queuedCelIterator.hasNext()) return;

        File firstCelFile = queuedCelIterator.next();
        CompletableFuture<ImageHandler> currentCelFuture = CompletableFuture.supplyAsync(
                () -> loadCel(firstCelFile, backgroundCel, false)
        );
        CompletableFuture<Void> currentArrayResourcesFuture = preAllocateIntArrayResourcesAsync(
                imageArrayLength,
                PREALLOCATED_INT_ARRAYS_PER_CEL
        );
        CompletableFuture<FastRGB> currentBgCelFuture = CompletableFuture.supplyAsync(backgroundCel::copy);

        for (File celFile : allCels) {
            Debugger.clear();
            ImageHandler enCelHandler = Debugger.createRef("enCelHandler", currentCelFuture.join());
            currentArrayResourcesFuture.join();
            FastRGB bgCel = Debugger.createRef("bgCel", currentBgCelFuture.join());

            File nextCelFile = queuedCelIterator.hasNext() ? queuedCelIterator.next() : null;
            CompletableFuture<ImageHandler> nextCelFuture = null;
            CompletableFuture<Void> nextArrayResourcesFuture = null;
            CompletableFuture<FastRGB> nextBgCelFuture = null;
            if (nextCelFile != null) {
                File queuedCelFile = nextCelFile;
                nextCelFuture = CompletableFuture.supplyAsync(
                        () -> loadCel(queuedCelFile, backgroundCel, false)
                );
                nextArrayResourcesFuture = preAllocateIntArrayResourcesAsync(
                        imageArrayLength,
                        PREALLOCATED_INT_ARRAYS_PER_CEL
                );
                nextBgCelFuture = CompletableFuture.supplyAsync(backgroundCel::copy);
            }

            track("reading entire cel");
            FastRGB enCelPixels = new FastRGB(enCelHandler);

            track("processing user selection");
            Rectangle userSelection = Debugger.createRef("userSelection", new ShapeBounds(
                    Debugger.createRef("crop-map", objectBorderArg(enCelHandler, "CelPaintCrop crop region"))
            ).get()).imgRect();

            BufferedImage selectedImagePortion = enCelHandler.getImage().getSubimage(
                    userSelection.x,
                    userSelection.y,
                    userSelection.width,
                    userSelection.height
            );
            PointCollection outline;
            ShapeContour objectBorder;
            String currentOutputPath = allCels.size() > 1
                    ? outputCelPath + File.separator + celFile.getName()
                    : outputCelPath;

            if (Boolean.parseBoolean(args.getOrDefault("autoobjectborder", "false"))) {
                track("finding object border");
                objectBorder = new ShapeContour(
                        outline = Debugger.createRef("outline_auto", filterPixelsToPtCollection(
                                selectedImagePortion,
                                Optional.ofNullable(customQual).orElse(inkQual)
                        ))
                );
                objectBorder.translate(userSelection.x, userSelection.y);
                if (Boolean.parseBoolean(args.getOrDefault("autoobjectborder-view-debug", "false"))) {
                    DebuggerEx.vis("autoobjectborder-view-debug", selectedImagePortion, objectBorder);
                }
            } else {
                track("getting user object border selection");
                ImageHandler selectedImageHandler = new ImageHandler(selectedImagePortion);
                TreeMap<Integer, List<Integer>> userObjectBorder = Debugger.createRef("obj-border-map", freeformArg(
                        selectedImageHandler,
                        "object-border-2",
                        new SelectionTransform(
                                1,
                                userSelection.x,
                                userSelection.y
                        ),
                        "CelPaintCrop object border"
                ));
                if (willDumpBorder()) dumpBorder(userObjectBorder, currentOutputPath);
                objectBorder = new ShapeContour(outline = new PointCollection(userObjectBorder));
            }
            if (previewShapeData()) DebuggerEx.vis("CelPaintCrop object border", enCelHandler, outline);
            if (previewShapeData()) DebuggerEx.vis("CelPaintCrop object border pre-contracted", enCelHandler, objectBorder);
            Debugger.createRef("outline_contracted", objectBorder = objectBorder.contract(
                    Math.max(1, MathEx.roundInt(MathEx.divide(getOutlineWidth(outline, objectBorder), 3)))
            ));
            if (previewShapeData()) DebuggerEx.vis("CelPaintCrop object border post-contracted", enCelHandler, objectBorder);

            track("cropping paint");
            // NOTE: iterates from top to bottom, left to right
            FastRGB outputPixels = bgCel;
            objectBorder.forEachInside(p -> replacePixel(enCelPixels, outputPixels, p.x, p.y));
            outputThreads.add(outputCelBackground(outputPixels, currentOutputPath));

            if (nextCelFile != null) {
                currentCelFuture = nextCelFuture;
                currentArrayResourcesFuture = nextArrayResourcesFuture;
                currentBgCelFuture = nextBgCelFuture;
            }
        }
        outputThreads.forEach(thread -> printExcept(() -> thread.join()));
    }
}
