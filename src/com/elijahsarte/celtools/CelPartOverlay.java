package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.imageworkshop.BlendMode;
import com.elijahsarte.celtools.main.imageworkshop.Layer;
import com.elijahsarte.celtools.main.imageworkshop.Photoshop;
import com.elijahsarte.celtools.main.util.ConstructionEx;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;
import com.elijahsarte.celtools.mainex.Debugger;
import com.elijahsarte.celtools.mainex.DebuggerEx;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;
import static com.elijahsarte.celtools.mainex.TaskTracker.*;

public class CelPartOverlay extends Main {

    private static final int MAX_ALLOC_PER_THREAD = 6, PER_PROCESS_CEL = 2;

    public CelPartOverlay(Map<String, String> args) {
        super(args);
    }

    private Thread processCel(ImageHandler cel, FastRGB backgroundCel, File celFile, String outputCelPath, boolean multipleOutputs, int threadNumber) throws Exception {

        trace("processing cel " + celFile.getName());
        CompletableFuture<Photoshop> photoshopFuture = CompletableFuture.supplyAsync(() -> {
            trackAsyncThread(threadNumber, "creating photoshop structure");
            Photoshop backgroundPhotoshop = new Photoshop(backgroundCel.getImage(), false);
            backgroundPhotoshop.addLayer(new Layer("Whole Image", cel.getImage(), false));
            backgroundPhotoshop.setBlendMode(1, BlendMode.MULTIPLY);
            backgroundPhotoshop.addLayer("Inside", cel.getImage());
            endTrackAsyncThread(threadNumber, "creating photoshop structure");
            return backgroundPhotoshop;
        });

        trackThread(threadNumber, "finding cel part");
        PointCollection partPoints = Debugger.createRef("partPoints_" + threadNumber, filterLargestMiniGroupToLocalGroup(cel, INK_SCAN_DISTANCE, inkQual));
        if (previewShapeData()) DebuggerEx.vis("cel part", cel.getImage(), partPoints);

        ShapeContour partEdge = Debugger.createRef("partEdge_ " + threadNumber, varOper(
                new ShapeContour(partPoints),
                s -> s.contract(Math.max(1, MathEx.roundInt(MathEx.divide(getOutlineWidth(partPoints, s), 3))))
        ));
        if (previewShapeData()) DebuggerEx.vis("cel part edge", cel.getImage(), partEdge);

        trackThread(threadNumber, "compositing cel and background cel");
        Photoshop backgroundPhotoshop = photoshopFuture.join();
        backgroundPhotoshop.createLayerMask(2, partEdge);

        String outputCelPath1 = multipleOutputs ? outputCelPath + File.separator + celFile.getName() : outputCelPath;
        if (threadNumber != -1) {
            outputCel(
                    backgroundPhotoshop.flatten(),
                    outputCelPath1
            );
            return null;
        } else {
            return outputCelBackground(
                    backgroundPhotoshop.flatten(),
                    outputCelPath1
            );
        }
    }

    @Override
    public void run() throws Exception {
        List<File> allCels = List.of(
                Optional.ofNullable(args.get("all-cels"))
                        .map(File::new)
                        .map(f -> f.isDirectory() ? sortedFilesByName(f.listFiles()) : new File[] { f })
                        .orElseGet(() -> new File[]{new File(safeArg("cel", "File path of cel:"))})
        );

        String backgroundCelPath = safeArg("background-cel", "File path of background cel:"),
                outputCelPath = safeArg("output-cel", "Output cel path:");

        int filesAtOnce = Math.max(
                1,
                Optional.ofNullable(args.get("files-at-once"))
                        .map(Integer::parseInt)
                        .orElse(1)
        );

        if (allCels.isEmpty()) {
            trace("no cels found");
            return;
        }

        if (allCels.size() > 1 && !new File(outputCelPath).isDirectory()) {
            trace("loading multiple cels, output cel must be folder");
            return;
        }

        track("loading background");
        FastRGB backgroundCelBase = new FastRGB(handlerFromFile(backgroundCelPath));
        Main.async(() ->
            ConstructionEx.preAllocateIntArrays(backgroundCelBase.getWidth() * backgroundCelBase.getHeight(), PER_PROCESS_CEL + MAX_ALLOC_PER_THREAD)
        );
        endTrack();

        boolean multipleOutputs = allCels.size() > 1;

        if (filesAtOnce > 1) {
            for (int i = 0; i < allCels.size(); i += filesAtOnce) {
                Debugger.clear();
                List<Runnable> batch = new ArrayList<>();
                List<Thread> outputThreads = new ArrayList<>();

                for (int j = i; j < Math.min(i + filesAtOnce, allCels.size()); j++) {
                    File celFile = allCels.get(j);
                    int threadNumber = j - i;

                    batch.add(() -> {
                        try {
                            CompletableFuture<ImageHandler> celFuture = CompletableFuture.supplyAsync(() -> loadCel(celFile, backgroundCelBase));
                            CompletableFuture<FastRGB> backgroundCelFuture = CompletableFuture.supplyAsync(backgroundCelBase::copy);
                            outputThreads.add(processCel(Debugger.createRef("cel_" + threadNumber, celFuture.join()), backgroundCelFuture.join(), celFile, outputCelPath, multipleOutputs, threadNumber));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                runTasksAsync(batch);
                outputThreads.forEach(t -> noExcept(() -> t.join()));
                ConstructionEx.resetAllArrays();
            }
        } else {
            Iterator<File> celIterator = allCels.iterator();

            File currentCelFile = celIterator.next();
            File firstCelFile = currentCelFile;
            CompletableFuture<ImageHandler> currentCelFuture = CompletableFuture.supplyAsync(() -> loadCel(firstCelFile, backgroundCelBase));
            CompletableFuture<FastRGB> currentBGFuture = CompletableFuture.supplyAsync(backgroundCelBase::copy);
            Main.async(() -> ConstructionEx.preAllocateIntArrays(backgroundCelBase.getWidth() * backgroundCelBase.getHeight(), MAX_ALLOC_PER_THREAD));
            ImageHandler currentCel = currentCelFuture.join();
            FastRGB currentBG = currentBGFuture.join();

            Main.async(() -> ConstructionEx.preAllocateIntArrays(backgroundCelBase.getWidth() * backgroundCelBase.getHeight(), PER_PROCESS_CEL + MAX_ALLOC_PER_THREAD));
            List<Thread> outputThreads = new ArrayList<>();
            while (true) {
                Debugger.clear();
                File nextCelFile = null;
                CompletableFuture<ImageHandler> nextCelFuture = null;
                CompletableFuture<FastRGB> nextBGFuture = null;

                if (celIterator.hasNext()) {
                    nextCelFile = celIterator.next();
                    File queuedCelFile = nextCelFile;
                    nextCelFuture = CompletableFuture.supplyAsync(() -> loadCel(queuedCelFile, backgroundCelBase));
                    nextBGFuture = CompletableFuture.supplyAsync(() -> {
                        trackAsync("copying background cel");
                        FastRGB copied = backgroundCelBase.copy();
                        endTrackAsync("copying background cel");
                        return copied;
                    });

                }

                outputThreads.add(processCel(currentCel, currentBG, currentCelFile, outputCelPath, multipleOutputs, -1));

                if (nextCelFuture == null) break;
                ImageHandler nextCel = Debugger.createRef("nextCel", nextCelFuture.join());

                currentCelFile = nextCelFile;
                currentCel = nextCel;
                currentBG = nextBGFuture.join();
            }
            outputThreads.forEach(t -> printExcept(() -> t.join()));
        }
    }
}
