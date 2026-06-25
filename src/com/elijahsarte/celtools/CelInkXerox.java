package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.imageworkshop.Photoshop;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.mainex.Debugger;
import com.elijahsarte.celtools.mainex.DebuggerEx;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.printExcept;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.varOper;
import static com.elijahsarte.celtools.mainex.TaskTracker.endTrack;
import static com.elijahsarte.celtools.mainex.TaskTracker.track;

public class CelInkXerox extends Main {

    public CelInkXerox(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws Exception {
        List<File> pencilFiles = List.of(varOper(new File(safeArg("pencil-drawing", "File path of pencil drawing:")), f -> f.isDirectory() ? sortedFilesByName(f.listFiles()) : new File[] { f }));
        Color inkColor = new Color(FastRGB.getRGBRaw(colorArg("ink-color", 0, 0, 0)));
        boolean printOut = Boolean.parseBoolean(args.getOrDefault("print", "false"));
        String outputCelPath = safeArg("output-cel", "Output cel path:");

        if (pencilFiles.size() > 1 && !new File(outputCelPath).isDirectory()) {
            trace("loading multiple pencil scans, output cel must be folder");
            return;
        }
        List<Thread> outputThreads = new ArrayList<>(pencilFiles.size());
        for (File pencilFile : pencilFiles) {
            Debugger.clear();
            track("loading pencil scan " + pencilFile.getName());
            ImageHandler pencilScanHandler = Debugger.createRef("pencilScanHandler", handlerFromFile(pencilFile));
            FastRGB pencilScan = new FastRGB(pencilScanHandler);
            track("finding pencil drawing");
            PointCollection pencilDrawing = Debugger.createRef("pencilDrawingExpandedContracted", Debugger.createRef("pencilDrawingInit", filterLargestMiniGroupToLocalGroup(
                    pencilScanHandler,
                    PENCIL_SCAN_DISTANCE,
                    hsv -> hsv[0] >= 0 && hsv[0] <= 360
                            && hsv[1] >= 2 && hsv[1] <= 100
                            && hsv[2] >= 0 && hsv[2] <= 98
            ))
                    .expand(3)
                    .contract(4)
                    .expand(1));
            if (previewShapeData()) DebuggerEx.vis("pencil drawing", pencilScan, pencilDrawing);
            track("painting over pencil drawing");
            Photoshop brushedDrawing = new Photoshop(new FastRGB(Color.WHITE, pencilScan.getWidth(), pencilScan.getHeight()));
            brushedDrawing.paintContour(pencilDrawing, Photoshop.BrushSettings.of(inkColor, 5)
                    .withHardness(1)
                    .withOpacity(1)
                    .withFlow(1)
                    .withSpacing(1));
            endTrack();
            FastRGB brushed;
            outputThreads.add(outputCelBackground((brushed = brushedDrawing.flatten()), pencilFiles.size() > 1 ? outputCelPath + File.separator + pencilFile.getName() : outputCelPath));
            if (printOut) printImage(pencilFile.getName(), brushed);
        }
        outputThreads.forEach(t -> printExcept(() -> t.join()));
    }
}
