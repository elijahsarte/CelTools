package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;
import com.elijahsarte.celtools.mainex.Debugger;
import com.elijahsarte.celtools.mainex.DebuggerEx;

import java.io.File;
import java.util.*;

import static com.elijahsarte.celtools.mainex.TaskTracker.track;

public class CelLayerPartCombine extends Main {

    public CelLayerPartCombine(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws Exception {

        String baseCelPath = safeArg("base-cel", "File path to base cel:"),
                allCelPaths = safeArg("all-cels-folder", "Folder containing cels:"),
                outputCelPath = safeArg("output-cel", "Output cel path:");

        track("reading base cel");
        ImageHandler baseCelHandler = handlerFromFile(baseCelPath);
        clearIntArrayResources(baseCelHandler.getWidth() * baseCelHandler.getHeight());
        FastRGB baseCel = new FastRGB(baseCelHandler);

        track("opening all cels");
        List<File> allCels = Optional.ofNullable(allCelPaths)
                .map(c -> Arrays.stream(c.split(",")).map(p -> new File(p.trim())).toList())
                .orElseGet(() -> List.of(
                        Optional.ofNullable(sortedFilesByName(new File(allCelPaths).listFiles())).orElseGet(() -> new File[] {
                                new File(safeArg("all-cels", "Folder containing cels:"))
                        })
                ))
                .stream()
                .filter(file -> file != null && file.isFile())
                .filter(file -> !file.getName().toLowerCase(Locale.ROOT).endsWith(".ctdata"))
                .toList();
        for (File cel : allCels) {

            Debugger.clear();
            track("opening " + cel.getAbsolutePath());
            ImageHandler thisCelHandler = Debugger.createRef("thisCelHandler", loadCel(cel, baseCel, false));
            clearIntArrayResources(thisCelHandler.getWidth() * thisCelHandler.getHeight());
            FastRGB thisCel = new FastRGB(thisCelHandler);

            track("parsing and filling selection");
            ShapeContour objectBorder = new ShapeContour(new PointCollection(Debugger.createRef("obj_border_data",
                    willLoadDumpedBorder()
                            ? loadDumpedBorder(cel.getAbsolutePath()).orElseGet(() ->
                                    objectBorderArg(thisCelHandler, "CelLayerPartCombine object border")
                            )
                            : objectBorderArg(thisCelHandler, "CelLayerPartCombine object border")
            )));
            if (previewShapeData()) DebuggerEx.vis("CelLayerPartCombine object border", thisCelHandler, objectBorder);
            objectBorder.forEachInside(p -> replacePixel(thisCel, baseCel, p.x, p.y));

        }
        outputCel(baseCel, outputCelPath);

    }
}
