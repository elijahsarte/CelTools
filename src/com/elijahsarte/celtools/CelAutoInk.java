package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
//import com.elijahsarte.celtools.main.analysis.inking.PencilInkTransferSystem;
import com.elijahsarte.celtools.main.analysis.inking.SimplePencilInkTransferSystem;
import com.elijahsarte.celtools.main.colormodel.HSVBounds;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.geomex.Line;
import com.elijahsarte.celtools.main.util.geomex.Polygon;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.mainex.DebuggerEx;
//import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.elijahsarte.celtools.mainex.TaskTracker.track;

public class CelAutoInk extends Main {


    private static final HSVBounds extendedInkQual = new HSVBounds(ProgrammingEx.varMutate(new TreeMap<>(), (TreeMap<Integer, Polygon> m) -> {
        HSVBounds.putHueRange(m, 20, 45, true, Arrays.asList(
                new Line(new Point(0, 34), new Point(16, 40)),
                new Line(new Point(16, 40), new Point(24, 53)),
                new Line(new Point(24, 53), new Point(31, 62)),
                new Line(new Point(31, 62), new Point(41, 64)),
                new Line(new Point(41, 64), new Point(57, 52)),
                new Line(new Point(57, 52), new Point(88, 40)),
                new Line(new Point(88, 40), new Point(100, 40))
        ));
        HSVBounds.putHueRange(m, 0, 19, true, Arrays.asList(
                new Line(new Point(0, 25), new Point(11, 25))
                //new Line(new Point(11, 25), new Point(11, 0))
        ));
        HSVBounds.putHueRange(m, 46, 360, true, Arrays.asList(
                new Line(new Point(0, 25), new Point(11, 25))
        ));
    }));

    public CelAutoInk(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws Exception {
        List<File> allCels = List.of(Optional.ofNullable(args.get("all-cels")).map(s -> sortedFilesByName(new File(s).listFiles())).orElse(new File[] { new File(safeArg("cel", "File path of cel:")) }));
        String inkingModelPath = args.get("inking-model"),
                pencilDrawingPath = args.get("pencil-drawing"),
                inkedDrawingPath = args.get("inked-drawing"),
                outputCelPath = safeArg("output-cel", "Output cel path:");

        if (allCels.size() > 1 && !new File(outputCelPath).isDirectory()) {
            trace("loading multiple cels, output cel must be folder");
            return;
        }

        if (inkingModelPath != null) {
            trace("inking model not given");
            return;
        }
        track("loading scans");
        CompletableFuture<FastRGB> pencilScanFuture = CompletableFuture.supplyAsync(() -> new FastRGB(handlerFromFile(pencilDrawingPath, BufferedImage.TYPE_INT_ARGB))),
                inkedScanFuture = CompletableFuture.supplyAsync(() -> new FastRGB(handlerFromFile(inkedDrawingPath, BufferedImage.TYPE_INT_ARGB)));
        CompletableFuture.allOf(pencilScanFuture, inkedScanFuture).join();
        FastRGB pencilScan = pencilScanFuture.join(),
                inkedScan = inkedScanFuture.join();

        track("extracting pencil and ink outline points");
        List<Object> simpleDrawings = runTasksAsync(
                () -> groupPoints(
                        filterPixelsToPtCollection(
                                pencilScan,
                                new double[] { 0, 2, 0 },
                                new double[] { 360, 100, 98 }
                        ),
                        PENCIL_SCAN_DISTANCE
                ).stream()
                        .max(Comparator.comparingInt(PointCollection::size))
                        .orElseThrow(),
                () -> groupPoints(
                        filterPixelsToPtCollection(inkedScan, inkQual),
                        INK_SCAN_DISTANCE
                ).stream()
                        .max(Comparator.comparingInt(PointCollection::size))
                        .orElseThrow()
        );
        PointCollection pencilDrawing = (PointCollection) simpleDrawings.get(0);
        PointCollection inkedDrawing = (PointCollection) simpleDrawings.get(1);
        if (previewShapeData()) {
            DebuggerEx.vis("pencil drawing", pencilScan, pencilDrawing);
            DebuggerEx.vis("inked drawing", inkedScan, inkedDrawing);
        }

        /* Legacy contour/regression transfer system. The simpler system below
         * works directly from the two scans, so none of this preprocessing is
         * needed.
        track("analyzing scans");
        List<Object> drawings = runTasksAsync(
                () -> groupPoints(
                        filterPixelsToPtCollection(
                                pencilScan,
                                new double[] { 0, 2, 0 },
                                new double[] { 360, 100, 98 }
                        ),
                        PENCIL_SCAN_DISTANCE
                ).stream()
                        .max(Comparator.comparingInt(PointCollection::size))
                        .orElseThrow(),

                () -> groupPoints(
                        filterPixelsToPtCollection(
                                inkedScan,
                                inkQual
                        ),
                        INK_SCAN_DISTANCE
                ).stream()
                        .max(Comparator.comparingInt(PointCollection::size))
                        .orElseThrow()
        );

        PointCollection pencilDrawing = (PointCollection) drawings.get(0),
                inkedDrawing  = (PointCollection) drawings.get(1);

        List<Object> contours = runTasksAsync(
                () -> new ShapeContour(pencilDrawing, 10),
                () -> new ShapeContour(inkedDrawing, INK_SCAN_DISTANCE)
        );

        ShapeContour pencilContour = (ShapeContour) contours.get(0),
                inkedContour  = (ShapeContour) contours.get(1);

        track("building model");
        PencilInkTransferSystem.VariableToggles toggles =
                PencilInkTransferSystem.VariableToggles.allEnabled();

        PencilInkTransferSystem.BuildOptions buildOptions =
                new PencilInkTransferSystem.BuildOptions();

        PencilInkTransferSystem.TrainingOptions trainingOptions =
                new PencilInkTransferSystem.TrainingOptions();

        PencilInkTransferSystem.Workbench workbench =
                PencilInkTransferSystem.launch(
                        pencilContour,
                        inkedContour,
                        pencilDrawing,
                        inkedDrawing,
                        pencilScan,
                        inkedScan,
                        toggles,
                        buildOptions,
                        trainingOptions
                );
        */

        track("comparing pencil and ink scans");
        SimplePencilInkTransferSystem.Workbench workbench =
                SimplePencilInkTransferSystem.launch(
                        pencilDrawing,
                        inkedDrawing,
                        pencilScan,
                        inkedScan
                );
        SimplePencilInkTransferSystem.Comparison comparison = workbench.calibration();
        trace(String.format(
                Locale.ROOT,
                "simple ink workbench opened: shape %.3f, appearance %.3f",
                comparison.shapeSimilarity(),
                comparison.appearanceSimilarity()
        ));
        //PencilInkTransferSystem system = new PencilInkTransferSystem()
        //DebuggerEx.vis("pencil drawing", pencilDrawingImage.getImage(), pencilDrawing);
        //FastRGB inkedDrawing = new FastRGB(handlerFromFile(inkedDrawingPath));
    }
}
