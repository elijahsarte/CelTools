package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.selectionui.SelectionTransform;
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.mainex.Debugger;
import com.elijahsarte.celtools.mainex.DebuggerEx;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.printExcept;
import static com.elijahsarte.celtools.mainex.TaskTracker.track;

public class CelLayerPartSelect extends Main {

    private static final int PREALLOCATED_INT_ARRAYS_PER_CEL = 4;


    public CelLayerPartSelect(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws IOException {

        File allCelsFile = new File(safeArg("all-cels", "File path of all cel(s):"));
        List<File> allCels = List.of(allCelsFile.isDirectory()
                ? sortedFilesByName(allCelsFile.listFiles())
                : new File[] {allCelsFile});
        String outputCelPath = safeArg("output-cel", "File path for final cel:");
        if (allCels.size() > 1 && !new File(outputCelPath).isDirectory()) {
            trace("loading multiple cels, output cel must be folder");
            return;
        }

        String bgCelPath = safeArg("background-cel", "File path to background cel:");
        track("loading background cel");
        ImageHandler bgCelHandler = handlerFromFile(bgCelPath);
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
            ShapeBounds userSelection = Debugger.createRef("userSelection", new ShapeBounds(
                    Debugger.createRef("cropbox-map", args.get("cropbox") != null
                            ? freeformArg(
                                    enCelHandler,
                                    "cropbox",
                                    SelectionTransform.empty(),
                                    "CelLayerPartSelect selection"
                            )
                            : objectBorderArg(enCelHandler, "CelLayerPartSelect selection"))
            ));
            if (previewShapeData()) DebuggerEx.vis("CelLayerPartSelect selection", enCelHandler, userSelection);

            track("extracting selected part");
            replacePixels(enCelPixels, bgCel, userSelection);
            outputThreads.add(outputCelBackground(bgCel, allCels.size() > 1 ? outputCelPath + File.separator + celFile.getName() : outputCelPath));

            if (nextCelFile != null) {
                currentCelFuture = nextCelFuture;
                currentArrayResourcesFuture = nextArrayResourcesFuture;
                currentBgCelFuture = nextBgCelFuture;
            }
        }
        outputThreads.forEach(t -> printExcept(() -> t.join()));

    }


}
