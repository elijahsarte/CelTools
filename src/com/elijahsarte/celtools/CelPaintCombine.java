package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.elijahsarte.celtools.mainex.TaskTracker.track;

public class CelPaintCombine extends Main {

    public CelPaintCombine(Map<String, String> args) {
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

        track("opening all cels");
        List<File> allCels = Optional.ofNullable(allCelPaths)
                .filter(s -> !s.endsWith(".ctdata"))
                .map(c -> Arrays.stream(c.split(",")).map(p -> new File(p.trim())).toList())
                .orElseGet(() -> List.of(new File(recursCelPath).listFiles()));
        for (File cel : allCels) {

            track("opening " + cel.getAbsolutePath());
            ImageHandler thisCelHandler = handlerFromFile(cel);
            FastRGB thisCel = new FastRGB(thisCelHandler);

            track("parsing and filling selection");
            ShapeContour objectBorder = new ShapeContour(new PointCollection(willLoadDumpedBorder() ? loadDumpedBorder(cel.getAbsolutePath()).orElseGet(() -> objectBorderArg(thisCelHandler)) : objectBorderArg(thisCelHandler)));
            objectBorder.forEachInside(p -> replacePixel(thisCel, baseCel, p.x, p.y));

        }
        outputCel(baseCel, outputCelPath);

    }
}

