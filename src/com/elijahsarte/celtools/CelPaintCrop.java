package com.elijahsarte.celtools;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.selectionui.SelectionTransform;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;
import com.elijahsarte.celtools.mainex.DebuggerEx;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.elijahsarte.celtools.mainex.TaskTracker.track;


public class CelPaintCrop extends Main {

    public CelPaintCrop(Map<String, String> args) {
        super(args);
    }

    @Override
    public void run() throws Exception {

        String outputCelPath = safeArg("output-cel", "File path for final cel:");

        track("reading entire cel");
        ImageHandler enCelHandler = handlerFromFile(safeArg("entire-cel", "File path to entire cel:"));
        FastRGB enCelPixels = new FastRGB(enCelHandler);
        double enCelGuiRatio = MathEx.divide(enCelHandler.getWidth(), enCelHandler.guiWidth());

        track("reading background cel");
        ImageHandler bgCelHandler = handlerFromFile(safeArg("background-cel", "File path to background cel:"));
        FastRGB bgCelPixels = new FastRGB(bgCelHandler);



        track("processing user selection");
        Rectangle userSelection = cropBoxArg(enCelHandler);
        if (userSelection == null) {
            trace("user selection not given, exiting");
            return;
        }

        BufferedImage selectedImagePortion = enCelHandler.getImage().getSubimage(userSelection.x, userSelection.y, userSelection.width, userSelection.height);
        ShapeContour objectBorder;
        if (args.get("autoobjectborder") != null) {

            track("finding object border");
            // alpha, rgb
            double[] lowerBounds = colorArg("lower-color-bound", 0, 0, 0);
            double[] upperBounds = colorArg("upper-color-bound", 82, 74, 103);

            objectBorder = new ShapeContour(filterPixelsToPtCollection(selectedImagePortion, lowerBounds, upperBounds));
            objectBorder.translate(userSelection.x, userSelection.y);
            if (args.get("autoobjectborder-view-debug") != null) {
                DebuggerEx.vis("autoobjectborder-view-debug", selectedImagePortion, objectBorder);
            }

            // TODO: filter stray pixels
            /*
            TreeMap<Integer, Integer> xFreq = new TreeMap<>();
            TreeMap<Integer, Integer> yFreq = new TreeMap<>();
            objectBorder.forEach((xCoord, yCoords) -> {
                xFreq.put(xCoord, (xFreq.getOrDefault(xCoord, 0)) + 1);
                yCoords.forEach(yCoord -> yFreq.put(yCoord, (yFreq.getOrDefault(yCoord, 0)) + 1));
            });

            ArrayList<Integer> xCoords = new ArrayList<>(xFreq.keySet());
            ArrayList<Integer> yCoords = new ArrayList<>(yFreq.keySet());
            xCoords.forEach(xCoord -> {

            })*/


            /*
            HashMap<Integer, Integer> xFreq = new HashMap<>();
            HashMap<Integer, Integer> yFreq = new HashMap<>();
            objectBorder.entrySet().forEach(entry -> {
                xFreq.put(entry.getKey(), xFreq.containsKey(entry.getKey()) ? xFreq.get(entry.getKey()) + 1 : 0);
                entry.getValue().forEach(yCoord -> {
                    yFreq.put(yCoord, yFreq.containsKey(yCoord) ? yFreq.get(yCoord) + 1 :0);
                });
            });
*/



        } else {

            track("getting user object border selection");
            TreeMap<Integer, List<Integer>> userObjBord = objectBorderArg(new ImageHandler(selectedImagePortion), new SelectionTransform(enCelGuiRatio, userSelection.x, userSelection.y));
            if (willDumpBorder()) dumpBorder(userObjBord, outputCelPath);
            objectBorder = new ShapeContour(new PointCollection(userObjBord));
        }

        track("cropping paint");
        // NOTE: iterates from top to bottom, left to right
        objectBorder.forEachInside(p -> replacePixel(enCelPixels, bgCelPixels, p.x, p.y));
        outputCel(bgCelPixels, outputCelPath);

    }
}

