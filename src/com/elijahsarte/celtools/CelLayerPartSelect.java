package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;


import java.awt.*;
import java.io.IOException;
import java.util.Map;

import static com.elijahsarte.celtools.mainex.TaskTracker.track;

public class CelLayerPartSelect extends Main {


    public CelLayerPartSelect(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws IOException {

        String outputCelPath = safeArg("output-cel", "File path for final cel:");

        track("reading entire cel");
        ImageHandler enCelHandler = handlerFromFile(safeArg("entire-cel", "File path to entire cel:"));
        FastRGB enCelPixels = new FastRGB(enCelHandler);


        track("reading background cel");
        ImageHandler bgCelHandler = handlerFromFile(safeArg("background-cel", "File path to background cel:"));
        FastRGB bgCelPixels = new FastRGB(bgCelHandler);

        track("processing user selection");
        Rectangle userSelection = cropBoxArg(enCelHandler);
        if (userSelection == null) {
            trace("user selection not given, exiting");
            return;
        }


        track("extracting selected part");
        replacePixels(enCelPixels, bgCelPixels, userSelection);
        outputCel(bgCelPixels, outputCelPath);
    }


}

