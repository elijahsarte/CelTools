package com.elijahsarte.celtools.main;

import com.elijahsarte.celtools.main.colormodel.HSVBounds;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.FrameParser;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionManager;
import com.elijahsarte.celtools.main.selectionui.SelectionTransform;
import com.elijahsarte.celtools.main.util.*;
import com.elijahsarte.celtools.main.util.datastructures.EnhancedTreeMap;
import com.elijahsarte.celtools.main.util.datastructures.UnionFind;
import com.elijahsarte.celtools.main.util.function.blocks.forloop.ForEach;
import com.elijahsarte.celtools.main.util.function.blocks.forloop.ForIncrement;
import com.elijahsarte.celtools.main.util.geomex.plane.Space2D;
import com.elijahsarte.celtools.main.util.geomex.plane.Vector2D;
import com.elijahsarte.celtools.main.util.iterators.CoordIterator;
import com.elijahsarte.celtools.main.util.iterators.PixelIterator;
import com.elijahsarte.celtools.main.util.math.LagrangeInterpolation;
import com.elijahsarte.celtools.main.util.math.SimpsonArea;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;
import com.elijahsarte.celtools.main.util.structures.bounds.ObjBounds;
import com.elijahsarte.celtools.main.util.structures.collections.FixedList;
import com.elijahsarte.celtools.main.util.structures.collections.IntList;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeInfo;
import com.elijahsarte.celtools.mainex.DebuggerEx;
import com.elijahsarte.celtools.mainex.TaskTracker;
import org.w3c.dom.Node;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.elijahsarte.celtools.main.util.CollectionsEx.*;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;
import static com.elijahsarte.celtools.mainex.TaskTracker.track;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;



public abstract class Main {

    // magic constants
    // todo: probably make some of these dynamic based off res
    // todo: make minvecdist based off image res, shape size, shape irregularity and object border irregularity, and distribution of shape pixels over a gradient (likely both horizontal and vertical) and vec distribution as well
    protected static final double minVecDist = 0.75;
    protected static final Predicate<double[]> blackQual = (hsv) -> (hsv[2] <= 11 && hsv[1] <= 11) || (hsv[1] > 11 && hsv[2] <= 25);

    protected final Map<String, String> args;
    public Main(Map<String, String> args) {
        this.args = args;
    }

    public abstract void run() throws Exception;

    protected static String cliArgParse(String[] args, String argName) {
        // for argument values that have spaces in them
        StringJoiner cumulativeArgValue = new StringJoiner(" ");
        boolean iteratingArgVal = false;
        for (String arg : args) {

            if (iteratingArgVal) {
                if (!arg.startsWith("--")) {
                    cumulativeArgValue.add(arg);
                    continue;
                }
                break;
            } else {
                if (arg.startsWith("--" + argName)) {
                    iteratingArgVal = true;
                    cumulativeArgValue.add(arg);
                }
                continue;
            }

        }
        if (cumulativeArgValue.toString().isEmpty()) {
            return null;
        }
        // make sure that only the argument name is not filtered out and that the
        // value is not screwed up if it itself has an equal sign in it
        return Arrays.stream(cumulativeArgValue.toString().split("=")).skip(1).collect(joining("="));
    }

    protected static String input(String prompt) {
        Scanner sc = new Scanner(System.in);    //System.in is a standard input stream
        System.out.println("\n" + prompt);
        return sc.nextLine();
    }

    protected static void trace(String message) {
        System.out.println(message);
    }

    // New methods
    protected String safeArg(String key, String msg) {
        return Optional.ofNullable(args.get(key))
                .orElseGet(() -> input(msg));
    }
    protected String safeArg(String key) {
        return safeArg(key, key + ":");
    }

    public static String readCustomMetadata(File file, String key) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
        try (var iis = ImageIO.createImageInputStream(file)) {
            reader.setInput(iis, true);
            IIOMetadata metadata = reader.getImageMetadata(0);
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_png_1.0");

            var textNodes = root.getElementsByTagName("tEXtEntry");
            for (int i = 0; i < textNodes.getLength(); i++) {
                Node node = textNodes.item(i);
                String keyword = node.getAttributes().getNamedItem("keyword").getNodeValue();
                String value = node.getAttributes().getNamedItem("value").getNodeValue();
                if (key.equalsIgnoreCase(keyword)) {
                    return value;
                }
            }
        } finally {
            reader.dispose();
        }
        return null;
    }

    public static ImageHandler handlerFromFile(File file) {
        return varMutate(new ImageHandler(file), i -> noExcept(i::loadImage));
    }
    public static ImageHandler handlerFromFile(String filePath) {
        return handlerFromFile(new File(filePath));
    }

    protected Rectangle cropBoxSelection(ImageHandler image, SelectionTransform transform) {
        TaskTracker.pause(); noExcept(image::loadImage); image.loadGuiImage();
        return noExcept(() -> new FreeformSelectionManager(image.getGuiImage(), transform, false).getCropBox());
    }
    protected Rectangle cropBoxSelection(ImageHandler image) {
        return cropBoxSelection(image, new SelectionTransform(MathEx.divide(image.getWidth(), image.guiWidth())));
    }
    protected Rectangle cropBoxArg(ImageHandler image, String argName, SelectionTransform transform) {
        return Optional.ofNullable(args.get(argName)).map(Main::parseCropboxData).orElseGet(() -> cropBoxSelection(image, transform));
    }
    protected Rectangle cropBoxArg(ImageHandler image, String argName) {
        return cropBoxArg(image, argName, new SelectionTransform(MathEx.divide(image.getWidth(), image.guiWidth())));
    }
    protected Rectangle cropBoxArg(ImageHandler image, SelectionTransform transform) {
        return cropBoxArg(image, "cropbox", transform);
    }
    protected Rectangle cropBoxArg(ImageHandler image) {
        return cropBoxArg(image, "cropbox");
    }

    protected TreeMap<Double, List<Double>> freeformSelection(ImageHandler image, SelectionTransform transform) {
        TaskTracker.pause(); noExcept(image::loadImage); image.loadGuiImage();
        return noExcept(() -> new TreeMap<>(new FreeformSelectionManager(image.getGuiImage(), transform).getDrawingAxis()));
    }
    protected TreeMap<Double, List<Double>> freeformSelection(ImageHandler image) {
        return freeformSelection(image, new SelectionTransform(MathEx.divide(image.getWidth(), image.guiWidth())));
    }
    protected TreeMap<Integer, List<Integer>> freeformArg(ImageHandler image, String argName, SelectionTransform transform) {
        return interpolateMissingCoordsInt(Optional.of(Optional.ofNullable(args.get(argName)).or(() -> Optional.ofNullable(args.get(argName + "-file")).map(p -> noExcept(() -> Files.readString(Paths.get(p), StandardCharsets.UTF_8)))))
                .flatMap(o -> o)
                .map(Main::parseFreeformData)
                .orElseGet(() -> freeformSelection(image, transform)), 1);
    }
    protected TreeMap<Integer, List<Integer>> freeformArg(ImageHandler image, String argName) {
        return freeformArg(image, argName, new SelectionTransform(MathEx.divide(image.getWidth(), image.guiWidth()), 0, 0));
    }
    protected TreeMap<Integer, List<Integer>> objectBorderArg(ImageHandler image, SelectionTransform transform) {
        return freeformArg(image, "object-border", transform);
    }
    protected TreeMap<Integer, List<Integer>> objectBorderArg(ImageHandler image) {
        return freeformArg(image, "object-border");
    }

    public static double[] colorFromString(String str) {
        return Arrays.stream(str.replaceAll("[{}]", "").split(","))
                .map(String::trim)
                .mapToDouble(Double::parseDouble)
                .toArray();
    }
    protected double[] colorArg(String argName, double[] def) {
        return Optional.ofNullable(args.get(argName)).map(Main::colorFromString).orElse(def);
    }
    protected double[] colorArg(String argName, int one, int two, int three) {
        return colorArg(argName, new double[] { one, two, three });
    }





    //    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {

    // true is right, false is left
    protected static Point findIntersection(TreeMap<Integer, Integer> pts, double roc, boolean dir) {
        NavigableMap<Integer, Integer> iterPts = dir ? pts : pts.descendingMap();
        TreeMap<Integer, Integer> interpolatedPts = pts.entrySet().stream().collect(toMap(
                Map.Entry::getKey,
                entry -> (int) ((roc * (entry.getKey() - iterPts.firstKey())) + iterPts.get(iterPts.firstKey())),
                (a, b) -> a,
                TreeMap::new
        ));
        TreeMap<Integer, Integer> foundIntersects = StreamEx.toMap(iterPts.entrySet().stream().filter(pt -> Objects.equals(pt.getValue(), interpolatedPts.get(pt.getKey()))), TreeMap::new);
        if (foundIntersects.isEmpty()) {
            return entryToPt(dir ? iterPts.lastEntry() : iterPts.firstEntry());
        }
        return entryToPt(foundIntersects.lastEntry());
    }

    public static Point entryToPt(Map.Entry<Integer, Integer> entry) {
        return new Point(entry.getKey(), entry.getValue());
    }


    protected static Rectangle parseCropboxData(String boxArg) {
        String[] rectData = boxArg.replaceAll("[\\[\\]]", "").split(",");
        Map<String, Integer> parsedRectFields = new HashMap<>();
        for (String rawRectField : rectData) {
            parsedRectFields.put(rawRectField.split("=")[0], Integer.parseInt(rawRectField.split("=")[1]));
        }
        return new Rectangle(parsedRectFields.get("x"), parsedRectFields.get("y"), parsedRectFields.get("width"), parsedRectFields.get("height"));
    }


    public static <K extends Number, V extends Number> String encodeFreeformData(Map<K, List<V>> map) {
        return map.entrySet()
                .stream()
                .map(en -> en.getKey() + "=" + en.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }
    public static TreeMap<Double, List<Double>> parseFreeformData(String objectBorderArg) {

        TreeMap<Double, List<Double>> currObjectBorder = new TreeMap<>();
//        String[] entries = objectBorderArg.replaceAll("[{}]", "").split(", (?=\\d+=\\[)");
        List<String> entries = new ArrayList<>();
        Matcher matcher = Pattern.compile("[\\d.]+=\\[[^]]*]").matcher(objectBorderArg);
        while (matcher.find()) {
            entries.add(matcher.group());
        }

        for (String entry : entries) {

            String[] entryData = entry.trim().replaceAll("[\\[\\]]", "").split("=");
            currObjectBorder.put(Double.parseDouble(entryData[0].trim()),
                    StreamEx.toArrayList(Arrays.stream(entryData[1].split(","))
                            .map(String::trim)
                            .mapToDouble(Double::parseDouble).boxed())

            );

        }
        return currObjectBorder;

    }


    protected static void outputCel(FastRGB pixelsHandler, String outputCelPath) throws IOException {
        track("outputting final result to " + outputCelPath);
        File outputCelFile = new File(outputCelPath);
        outputCelFile.createNewFile();
        ImageIO.write(pixelsHandler.getImage(), "png", outputCelFile);
//        System.exit(0);
    }

    protected static void outputCel(FastRGB pixelsHandler, String outputCelPath, Map<String, String> textTags) throws IOException {
        track("outputting final result to " + outputCelPath + " with custom PNG tags");

        File outputCelFile = new File(outputCelPath);
        outputCelFile.createNewFile();

        BufferedImage image = pixelsHandler.getImage();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB);
        IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);

        if (textTags != null && !textTags.isEmpty()) {
            String nativeFormat = "javax_imageio_png_1.0";
            Node root = metadata.getAsTree(nativeFormat);

            org.w3c.dom.Node textNode = null;
            for (int i = 0; i < root.getChildNodes().getLength(); i++) {
                Node child = root.getChildNodes().item(i);
                if ("tEXt".equals(child.getNodeName())) {
                    textNode = child;
                    break;
                }
            }

            if (textNode == null) {
                textNode = root.getOwnerDocument() != null
                        ? root.getOwnerDocument().createElement("tEXt")
                        : new javax.imageio.metadata.IIOMetadataNode("tEXt");
                root.appendChild(textNode);
            }

            for (Map.Entry<String, String> entry : textTags.entrySet()) {
                javax.imageio.metadata.IIOMetadataNode textEntry =
                        new javax.imageio.metadata.IIOMetadataNode("tEXtEntry");
                textEntry.setAttribute("keyword", entry.getKey());
                textEntry.setAttribute("value", entry.getValue());
                textNode.appendChild(textEntry);
            }

            metadata.setFromTree(nativeFormat, root);
        }

        try (ImageOutputStream output = ImageIO.createImageOutputStream(outputCelFile)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, metadata), writeParam);
        } finally {
            writer.dispose();
        }
//    System.exit(0);
    }

    protected static void outputCel(FastRGB pixelsHandler) throws IOException {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save PNG");
        fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));

        if (fileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File outputCelFile = fileChooser.getSelectedFile();

        if (!outputCelFile.getName().toLowerCase().endsWith(".png")) {
            outputCelFile = new File(outputCelFile.getAbsolutePath() + ".png");
        }

        outputCel(pixelsHandler, outputCelFile.getAbsolutePath());
    }

    protected static void outputCel(FastRGB pixelsHandler, Map<String, String> textTags, String ext) throws IOException {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save " + ext.toUpperCase());
        fileChooser.setFileFilter(new FileNameExtensionFilter(ext.toUpperCase() + " Images", ext));

        if (fileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File outputCelFile = fileChooser.getSelectedFile();

        if (!outputCelFile.getName().toLowerCase().endsWith("." + ext)) {
            outputCelFile = new File(outputCelFile.getAbsolutePath() + "." + ext);
        }

        outputCel(pixelsHandler, outputCelFile.getAbsolutePath(), textTags);
    }

    protected static void outputCel(ImageHandler pixelsHandler, String ext) throws IOException {
        outputCel(new FastRGB(pixelsHandler), Map.of(), ext);
    }
    protected static void outputCel(ImageHandler pixelsHandler, String ext, Map<String, String> textTags) throws IOException {
        outputCel(new FastRGB(pixelsHandler), textTags, ext);
    }
    protected static void outputCel(ImageHandler pixelsHandler) throws IOException {
        outputCel(pixelsHandler, "png");
    }


    protected static void outputDump(String dump, String outputCelPath) throws IOException {
        track("outputting final dump to " + outputCelPath);
        File outputDumpFile = new File(outputCelPath);
        outputDumpFile.createNewFile();
        Files.writeString(outputDumpFile.toPath(), dump);
    }
    protected boolean willDumpBorder() {
        return Boolean.parseBoolean(args.getOrDefault("dump-border", "false"));
    }
    protected boolean willLoadDumpedBorder() {
        return Boolean.parseBoolean(args.getOrDefault("load-dumped-border", "false"));
    }
    protected static void dumpBorder(TreeMap<Integer, List<Integer>> border, String outputCelPath) throws IOException {
        outputDump(Main.encodeFreeformData(border), outputCelPath + ".ctdata");
    }
    protected static Optional<TreeMap<Integer, List<Integer>>> loadDumpedBorder(String outputCelPath) throws IOException {
        File file = new File(outputCelPath + ".ctdata");
        return file.exists() ? Optional.ofNullable(interpolateMissingCoordsInt(Main.parseFreeformData(Files.readString(file.toPath(), StandardCharsets.UTF_8)), 1)) : Optional.empty();
    }

    protected static TreeMap<Integer, List<Integer>> upscaleGuiCoords(Map<Double, List<Double>> currObjectBorder, double guiRatio) {
        return interpolateMissingCoords(CollectionsEx.treeMapOf(currObjectBorder), MathEx.reciprocal(guiRatio)).entrySet().stream()
                .collect(toMap(
                        key -> (int) (key.getKey() * guiRatio),
                        value -> StreamEx.toArrayList(value.getValue().stream()
                                .map(yCoord -> (int) (yCoord * guiRatio))),
                        (r1, r2) -> uniqueList(concatLists(r1, r2)),
                        TreeMap::new
                ));
    }


    public static void interpolateMissingCoords(PointCollection currObjectBorder) {
        int firstCol = currObjectBorder.firstX();
        currObjectBorder.onRaw();
        TreeMap<Integer, TreeMap<Integer, Integer>> pointAssocs = varMutate(
                new TreeMap<>(),
                (pointAssocsWIP) -> currObjectBorder.forEachRaw((col, rows) -> rows.forEach(row -> {
                    if (Math.abs(firstCol - col) <= 10 && pointAssocsWIP.size() == 1 && rows.size() >= 2) {
                        varOper(
                                pointAssocsWIP.firstKey(),
                                (onlyAssoc) -> pointAssocsWIP.put(
                                        rows.stream().max(Comparator.comparingDouble(v -> Math.abs(onlyAssoc - row))).orElseThrow().intValue(),
                                        treeMapOf(col, row))
                        );
                    }
                    mapPut(pointAssocsWIP,
                            pointAssocsWIP.entrySet().stream()
                                    .collect(toMap(
                                            Map.Entry::getKey,
                                            entry -> entry.getValue().lastEntry(),
                                            (e1, e2) -> e1,
                                            TreeMap::new
                                    )).entrySet().stream()
                                    .min(Comparator.comparingDouble(v -> Math.abs(v.getValue().getValue() - row)))
                                    .map(Map.Entry::getKey)
                                    .orElseGet(() -> closestElem(pointAssocsWIP.keySet(), row).orElse(row)),
                            col, row, new TreeMap<>());
                }))
        );


        Collection<TreeMap<Integer, Integer>> assembledInterpolateGroups = pointAssocs.values();
        TreeMap<Double, List<Double>> interpolatedValues = new TreeMap<>();
        for (TreeMap<Integer, Integer> interpolateGroup : assembledInterpolateGroups) {
            if (interpolateGroup.size() <= 1) {
                continue;
            }

            int minCoord = interpolateGroup.firstKey();
            int maxCoord = interpolateGroup.lastKey();
            if (Math.abs(maxCoord - minCoord) < 4) {
                continue;
            }

            LagrangeInterpolation interpol = new LagrangeInterpolation(interpolateGroup, 3);
            double lastInterpolatedCoord = Double.NEGATIVE_INFINITY;
            for (int currCoord = minCoord; currCoord < maxCoord; currCoord++) {

                if (currObjectBorder.containsX(MathEx.roundInt(currCoord))) {
/*
                    if (lastInterpolatedCoord == Double.NEGATIVE_INFINITY) {
                        continue;
                    }*/

                    IntList hereVals = currObjectBorder.getYesAtX(currCoord);
                    IntList closestValGroup = varOper(
                            lastInterpolatedCoord,
                            liCoord -> groupByDist(hereVals, 3).stream().filter(grp -> containsDist(grp, liCoord, 3)).findFirst().orElse(new IntList())
                    );
                    if (!closestValGroup.isEmpty()) {
                        varExec(
                                closestElem(closestValGroup, (int) lastInterpolatedCoord).orElseThrow(),
                                (closestVal) -> closestValGroup.stream().filter(v -> !Objects.equals(v, closestVal)).forEach(hereVals::removeElem)
                        );
                        for (double val : hereVals)
                            currObjectBorder.add(new Point(MathEx.roundInt(currCoord), MathEx.roundInt(val)));
                        continue;
                    }
                    if (containsDist(hereVals, lastInterpolatedCoord, 5)) {
                        continue;
                    }
                }
                lastInterpolatedCoord = interpol.operate(currCoord);
                listPut(interpolatedValues, (double) currCoord, lastInterpolatedCoord);

            }


        }
        interpolatedValues.forEach((key, vals) -> vals.forEach(val -> currObjectBorder.add(new Point(key.intValue(), val.intValue()))));
    }


    private static Stream<Map.Entry<Double, List<Double>>> interpolateMissingCoordsCore(TreeMap<Double, List<Double>> rawCurrObjectBorder, double precision) {
        TreeMap<Double, List<Double>> currObjectBorder = rawCurrObjectBorder.entrySet().stream().collect(toMap(
                entry -> MathEx.round(entry.getKey(), precision),
                Map.Entry::getValue,
                (existing, replacement) -> uniqueList(concatLists(existing, replacement)),
                TreeMap::new
        ));


        double firstCol = currObjectBorder.firstKey();
        TreeMap<Double, TreeMap<Double, Double>> pointAssocs = varMutate(
                new TreeMap<>(),
                (pointAssocsWIP) -> currObjectBorder.forEach((col, rows) -> rows.forEach(row -> {
                    if (Math.abs(firstCol - col) <= 10 && pointAssocsWIP.size() == 1 && rows.size() >= 2) {
                        varOper(
                                pointAssocsWIP.firstKey(),
                                (onlyAssoc) -> pointAssocsWIP.put(
                                        rows.stream().max(Comparator.comparingDouble(v -> Math.abs(onlyAssoc - row))).orElseThrow(),
                                        treeMapOf(col, row))
                        );
                    }
                    mapPut(pointAssocsWIP,
                            pointAssocsWIP.entrySet().stream()
                                    .collect(toMap(
                                            Map.Entry::getKey,
                                            entry -> entry.getValue().lastEntry(),
                                            (e1, e2) -> e1,
                                            TreeMap::new
                                    )).entrySet().stream()
                                    .min(Comparator.comparingDouble(v -> Math.abs(v.getValue().getValue() - row)))
                                    .map(Map.Entry::getKey)
                                    .orElseGet(() -> closestElem(pointAssocsWIP.keySet(), row).orElse(row)),
                            col, row, new TreeMap<>());
                }))
        );


        Collection<TreeMap<Double, Double>> assembledInterpolateGroups = pointAssocs.values();
        TreeMap<Double, List<Double>> interpolatedValues = new TreeMap<>();
        for (TreeMap<Double, Double> interpolateGroup : assembledInterpolateGroups) {
            if (interpolateGroup.size() <= 1) {
                continue;
            }

            double minCoord = interpolateGroup.firstKey();
            double maxCoord = interpolateGroup.lastKey();
            if (Math.abs(maxCoord - minCoord) / precision < 4) {
                continue;
            }

            LagrangeInterpolation interpol = new LagrangeInterpolation(interpolateGroup, 3);
            double lastInterpolatedCoord = Double.NEGATIVE_INFINITY;
            for (double currCoord = minCoord; currCoord < maxCoord; currCoord += precision) {

                currCoord = MathEx.round(currCoord, MathEx.divide(1, BigDecimal.valueOf(precision).scale()*10));
                if (currObjectBorder.containsKey(currCoord)) {

                    if (lastInterpolatedCoord == Double.NEGATIVE_INFINITY) {
                        continue;
                    }

                    List<Double> hereVals = currObjectBorder.get(currCoord);
                    if (MathEx.isWhole(currCoord)) {

                        List<Double> closestValGroup = varOper(
                                lastInterpolatedCoord,
                                liCoord -> groupByDist(hereVals, 3).stream().filter(grp -> containsDist(grp, liCoord, 3)).findFirst().orElse(new ArrayList<>())
                        );
                        if (!closestValGroup.isEmpty()) {
                            varExec(
                                    closestElem(closestValGroup, lastInterpolatedCoord).orElseThrow(),
                                    (closestVal) -> closestValGroup.stream().filter(v -> !Objects.equals(v, closestVal)).forEach(hereVals::remove)
                            );
                            currObjectBorder.put(currCoord, hereVals);
                            continue;
                        }
                    }

                    if (containsDist(hereVals, lastInterpolatedCoord, 5)) {
                        continue;
                    }
                }
                lastInterpolatedCoord = interpol.operate(currCoord);
                listPut(interpolatedValues, currCoord, lastInterpolatedCoord);

            }


        }


        return Stream.concat(currObjectBorder.entrySet().stream(), interpolatedValues.entrySet().stream());
    }

    public static TreeMap<Double, List<Double>> interpolateMissingCoords(TreeMap<Double, List<Double>> rawCurrObjectBorder, double precision) {
        return interpolateMissingCoordsCore(rawCurrObjectBorder, precision).collect(toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                CollectionsEx::returnAddAll,
                TreeMap::new
        ));
    }
    public static TreeMap<Integer, List<Integer>> interpolateMissingCoordsInt(TreeMap<Double, List<Double>> rawCurrObjectBorder, int precision) {
        return interpolateMissingCoordsCore(rawCurrObjectBorder, precision).collect(toMap(
                e -> e.getKey().intValue(),
                e -> StreamEx.toArrayList(e.getValue().stream().map(Double::intValue)),
                CollectionsEx::returnAddAll,
                TreeMap::new
        ));
    }
    

    public static void replacePixel(FastRGB srcPixels, FastRGB destPixels, int x, int y) {
        varExec(srcPixels.getRGB(x, y), e -> {
            if (destPixels.getRGB(x, y) != e) destPixels.setRGB(x, y, e);
        });
    }
    private static void replacePixels(FastRGB srcPixels, FastRGB destPixels, CoordIterator iterator) {
        iterator.execute((x, y) -> replacePixel(srcPixels, destPixels, x, y));
    }
    public static void replacePixels(FastRGB srcPixels, FastRGB destPixels, int startX, int endX, int startY, int endY) {
        replacePixels(srcPixels, destPixels, new CoordIterator(startX, endX, startY, endY));
    }
    public static void replacePixels(FastRGB srcPixels, FastRGB destPixels, Rectangle bounds) {
        replacePixels(srcPixels, destPixels, new CoordIterator(bounds));
    }


    public static PointCollection filterPixelsToPtCollection(int[] pixels, int width, int height, HSVBounds hsvBounds) {
        return varMutate(new PointCollection(), (ptColl) -> FrameParser.filterHSV(pixels.clone(), width, height, hsvBounds, (col, row) -> ptColl.add(new Point(col, row))));
    }
    public static PointCollection filterPixelsToPtCollection(ImageHandler image, HSVBounds hsvBounds) {
        return filterPixelsToPtCollection(image.getImgPixels(), image.getWidth(), image.getHeight(), hsvBounds);
    }
    public static PointCollection filterPixelsToPtCollection(int[] pixels, int width, int height, double[] low, double[] high) {
        return filterPixelsToPtCollection(pixels, width, height, new HSVBounds(low, high));
    }
    public static PointCollection filterPixelsToPtCollection(ImageHandler image, double[] low, double[] high) {
        return filterPixelsToPtCollection(image, new HSVBounds(low, high));
    }
    public static PointCollection filterPixelsToPtCollection(BufferedImage image, HSVBounds hsvBounds) {
        return filterPixelsToPtCollection(new ImageHandler(image), hsvBounds);
    }
    public static PointCollection filterPixelsToPtCollection(BufferedImage image, double[] low, double[] high) {
        return filterPixelsToPtCollection(image, new HSVBounds(low, high));
    }
    public static PointCollection filterPixelsToPtCollection(int[] pixels, int width, int height, Predicate<double[]> hsvQualifier) {
        return varMutate(new PointCollection(), ptColl -> FrameParser.filterHSV(pixels.clone(), width, height, hsvQualifier, (col, row) -> ptColl.add(new Point(col, row))));
    }
    public static PointCollection filterPixelsToPtCollection(ImageHandler image, Predicate<double[]> hsvQualifier) {
        return filterPixelsToPtCollection(image.getImgPixels(), image.getWidth(), image.getHeight(), hsvQualifier);
    }
    public static PointCollection filterPixelsToPtCollection(BufferedImage image, Predicate<double[]> hsvQualifier) {
        return filterPixelsToPtCollection(new ImageHandler(image), hsvQualifier);
    }

    public static List<PointCollection> filterPixelsToGroups(int[] pixels, int width, int height, int dist, double[] low, double[] high) {
        List<PointCollection> groups = new ArrayList<>();
//        FixedList<Map<IntegerBounds, Integer>> grpBdXes = new FixedList<>(dist);
        FixedList<Map<Integer, Integer>> grpXes = new FixedList<>(dist);
        FixedList<IntList> lastXes = new FixedList<>(dist);
        FixedList<Integer> cacheCounter = new FixedList<>(dist);

        TreeSet<Integer> foundGrps = new TreeSet<>();
//        Map<IntegerBounds, Integer> currBdXes = new HashMap<>();
        Map<Integer, Integer> currBdXes = new HashMap<>();

        AtomicInteger currBd = new AtomicInteger();
        IntList currXes = new IntList();
        Map<Point, Boolean> currCache = new LinkedHashMap<>();

        AtomicInteger lastY = INVALID_ATOMIC_INT;
        AtomicBoolean ranFilterFirst = new AtomicBoolean(false);

        BiConsumer<Integer, Integer> distGreaterFn = (col, row) -> {

            if (!ranFilterFirst.get()) return;
            if (row != lastY.get() || Math.abs(col - OptionalEx.ofCond(currXes.last(), !currXes.isEmpty()).orElse(INVALID_INT)) != dist) return;

            if (foundGrps.isEmpty()) {
                IntStream.rangeClosed(currXes.first(), currXes.last()).forEach(i -> currBdXes.put(i, groups.size()));
//                currBdXes.put(new IntegerBounds(currXes.first(), currXes.last()), groups.size());
                groups.add(varMutate(new PointCollection(), c -> currXes.stream().map(x -> new Point(x, row)).forEach(c::add)));
            } else {

                if (foundGrps.size() > 1) {
                    PointCollection first = groups.get(foundGrps.first());
                    PointCollection merged = foundGrps.stream().skip(1).map(groups::get).collect(Collector.of(
                            () -> first,
                            PointCollection::addAll,
                            (c1, c2) -> varMutate(c1, c3 -> c3.addAll(c2)),
                            Collector.Characteristics.IDENTITY_FINISH
                    ));
                    Iterator<Integer> grpsToRemove = foundGrps.descendingIterator();
                    while (grpsToRemove.hasNext()) {
                        groups.remove(grpsToRemove.next().intValue());
                    }


                    Stream.concat(grpXes.stream(), Stream.of(currBdXes)).forEach(bounds -> {
                        bounds.entrySet().stream().filter(e -> foundGrps.contains(e.getValue())).map(Map.Entry::getKey).forEach(k -> bounds.put(k, groups.size()));
                    });
                    merged.addAtX(col, currXes);
                    IntStream.rangeClosed(currXes.first(), currXes.last()).forEach(i -> currBdXes.put(i, groups.size()));
//                    currBdXes.put(new IntegerBounds(currXes.first(), currXes.last()), groups.size());
                    groups.add(merged);

                } else {
                    IntStream.rangeClosed(currXes.first(), currXes.last()).forEach(i -> currBdXes.put(i, groups.size()));
//                    currBdXes.put(new IntegerBounds(currXes.first(), currXes.last()), foundGrps.first());
                    varExec(groups.get(foundGrps.first()), c -> currXes.stream().map(x -> new Point(x, row)).forEach(c::add));
                }

            }
            currBd.incrementAndGet();
            foundGrps.clear();
            ranFilterFirst.set(false);


        };

        FrameParser.filterHSV(pixels.clone(), width, height, new HSVBounds(low, high), (col, row) -> {

            if (!invalid(lastY) && row != lastY.get()) {
                grpXes.add(new HashMap<>(currBdXes));
                lastXes.add(new IntList(currXes));
                foundGrps.clear();
                currBdXes.clear();
                currXes.clear();
                if (!cacheCounter.isEmpty()) {
                    Iterator<Map.Entry<Point, Boolean>> it = currCache.entrySet().iterator();
                    for (int toRemove = 0; toRemove < cacheCounter.oldest() && it.hasNext(); toRemove++) {
                        it.next();
                        it.remove();
                    }
                    cacheCounter.add(currBd.get());
                }
                currBd.set(0);
                ranFilterFirst.set(false);
            }
            lastY.set(row);

            ranFilterFirst.set(true);
            currXes.add(col);

            for (int i = 1; i < dist; i++) {
                if ((i - 1) >= lastXes.size()) break;
                // derivation: sqrt((y) + (x1 - x)^2) < 3, solve for x1
                double z = Math.sqrt(9 - MathEx.square(i));
                IntList aboveXes = lastXes.get(i - 1);
                varExec(i, offset -> StreamEx.intsBetween(-z, z).forEach(z1 -> {
                    Point pt = new Point(z1 + col, row - offset);
                    boolean res = false;
                    if (/*(currCache.containsKey(pt) && currCache.get(pt)) || */aboveXes.contains(pt.x)) {
                        res = true;
//                        foundGrps.add(grpXes.get(offset - 1).entrySet().stream().filter(e -> e.getKey().inBetweenClosed(pt.x)).findFirst().orElseThrow().getValue());
                        foundGrps.add(grpXes.get(offset - 1).get(pt.x));
                    }
                    currCache.putIfAbsent(pt, res);

                }));
            }


        }, distGreaterFn);
        return groups;
    }

    public static PointCollection filterPixelsToLocalGroup(
            FastRGB image,
            Point seed,
            double dist,
            Predicate<double[]> hsvQualifier
    ) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(hsvQualifier, "hsvQualifier");

        PointCollection group = new PointCollection();

        int width = image.getWidth();
        int height = image.getHeight();

        if (Double.isNaN(dist) || dist < 0.0) {
            return group;
        }

        int sx = seed.x;
        int sy = seed.y;

        if (sx < 0 || sx >= width || sy < 0 || sy >= height) {
            return group;
        }

        if (!hsvQualifier.test(image.getHSV(sx, sy))) {
            return group;
        }

        double distSq = dist * dist;
        int radius = MathEx.ceilInt(dist);

        int maxOffsetCount = ((radius * 2) + 1) * ((radius * 2) + 1) - 1;
        int[] offsetXs = new int[maxOffsetCount];
        int[] offsetYs = new int[maxOffsetCount];
        int offsetCount = 0;

        for (int dy = -radius; dy <= radius; dy++) {
            int dySq = dy * dy;
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dy == 0) continue;
                if ((double) (dx * dx + dySq) <= distSq) {
                    offsetXs[offsetCount] = dx;
                    offsetYs[offsetCount] = dy;
                    offsetCount++;
                }
            }
        }

        int pixelCount = width * height;

        byte[] state = new byte[pixelCount];
        int[] queue = new int[pixelCount];
        int head = 0;
        int tail = 0;

        IntList[] pendingByX = new IntList[width];

        int seedIdx = sy * width + sx;
        state[seedIdx] = 2;
        queue[tail++] = seedIdx;

        if (pendingByX[sx] == null) {
            pendingByX[sx] = new IntList();
        }
        pendingByX[sx].add(sy);

        while (head < tail) {
            int idx = queue[head++];
            int x = idx % width;
            int y = idx / width;

            for (int i = 0; i < offsetCount; i++) {
                int nx = x + offsetXs[i];
                int ny = y + offsetYs[i];

                if ((nx | ny) < 0 || nx >= width || ny >= height) continue;

                int nIdx = ny * width + nx;
                if (state[nIdx] != 0) continue;

                if (hsvQualifier.test(image.getHSV(nx, ny))) {
                    state[nIdx] = 2;
                    queue[tail++] = nIdx;

                    IntList column = pendingByX[nx];
                    if (column == null) {
                        column = new IntList();
                        pendingByX[nx] = column;
                    }
                    column.add(ny);
                } else {
                    state[nIdx] = 1;
                }
            }
        }

        for (int x = 0; x < pendingByX.length; x++) {
            IntList ys = pendingByX[x];
            if (ys != null && !ys.isEmpty()) {
                group.addAtX(x, ys);
            }
        }

        return group;
    }

    public static PointCollection filterPixelsToLocalGroup(
            FastRGB image,
            Point seed,
            double dist,
            HSVBounds hsvBounds
    ) {
        Objects.requireNonNull(hsvBounds, "hsvBounds");
        return filterPixelsToLocalGroup(image, seed, dist, hsvBounds::within);
    }

    public static PointCollection filterPixelsToLocalGroup(
            FastRGB image,
            Point seed,
            double dist,
            double[] low,
            double[] high
    ) {
        return filterPixelsToLocalGroup(image, seed, dist, new HSVBounds(low, high));
    }

    public static PointCollection filterPixelsToLocalGroup(
            int[] pixels,
            int width,
            int height,
            Point seed,
            double dist,
            Predicate<double[]> hsvQualifier
    ) {
        return filterPixelsToLocalGroup(new FastRGB(pixels, width, height, true), seed, dist, hsvQualifier);
    }

    public static PointCollection filterPixelsToLocalGroup(
            int[] pixels,
            int width,
            int height,
            Point seed,
            double dist,
            HSVBounds hsvBounds
    ) {
        Objects.requireNonNull(hsvBounds, "hsvBounds");
        return filterPixelsToLocalGroup(new FastRGB(pixels, width, height, true), seed, dist, hsvBounds::within);
    }

    public static PointCollection filterPixelsToLocalGroup(
            int[] pixels,
            int width,
            int height,
            Point seed,
            double dist,
            double[] low,
            double[] high
    ) {
        return filterPixelsToLocalGroup(pixels, width, height, seed, dist, new HSVBounds(low, high));
    }

    public static PointCollection filterPixelsToLocalGroup(
            ImageHandler image,
            Point seed,
            double dist,
            Predicate<double[]> hsvQualifier
    ) {
        return filterPixelsToLocalGroup(new FastRGB(image), seed, dist, hsvQualifier);
    }

    public static PointCollection filterPixelsToLocalGroup(
            ImageHandler image,
            Point seed,
            double dist,
            HSVBounds hsvBounds
    ) {
        Objects.requireNonNull(hsvBounds, "hsvBounds");
        return filterPixelsToLocalGroup(new FastRGB(image), seed, dist, hsvBounds::within);
    }

    public static PointCollection filterPixelsToLocalGroup(
            ImageHandler image,
            Point seed,
            double dist,
            double[] low,
            double[] high
    ) {
        return filterPixelsToLocalGroup(image, seed, dist, new HSVBounds(low, high));
    }

    public static PointCollection filterPixelsToLocalGroup(
            BufferedImage image,
            Point seed,
            double dist,
            Predicate<double[]> hsvQualifier
    ) {
        return filterPixelsToLocalGroup(new FastRGB(new ImageHandler(image)), seed, dist, hsvQualifier);
    }

    public static PointCollection filterPixelsToLocalGroup(
            BufferedImage image,
            Point seed,
            double dist,
            HSVBounds hsvBounds
    ) {
        Objects.requireNonNull(hsvBounds, "hsvBounds");
        return filterPixelsToLocalGroup(new FastRGB(new ImageHandler(image)), seed, dist, hsvBounds::within);
    }

    public static PointCollection filterPixelsToLocalGroup(
            BufferedImage image,
            Point seed,
            double dist,
            double[] low,
            double[] high
    ) {
        return filterPixelsToLocalGroup(image, seed, dist, new HSVBounds(low, high));
    }

    public static void pixelsToType(int[] pixels, BiConsumer<Integer, Integer> addFn, int width, int height, int targetColor) {
        new PixelIterator(pixels, width, height, true).execute((hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> {
            if (rawRGB == targetColor) addFn.accept(col, row);
        });
    }
    public static PointCollection pixelsToPtCollection(int[] pixels, int width, int height, int targetColor) {
        return varMutate(new PointCollection(), (ptColl) -> pixelsToType(pixels, (col, row) -> ptColl.add(new Point(col, row)), width, height, targetColor));
    }
    public static TreeMap<Integer, List<Integer>> pixelsToMap(int[] pixels, int width, int height, int targetColor) {
        return varMutate(new TreeMap<>(), (map) -> new PixelIterator(pixels, width, height, true).execute((hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> {
            if (rawRGB == targetColor) CollectionsEx.listPut(map, col, row);
        }));
    }

    public static List<Point> mapToPoint(Map<Integer, List<Integer>> map) {
        return varMutate(new ArrayList<>(), (points) -> map.forEach((col, rows) -> rows.forEach(row -> points.add(new Point(col, row)))));
    }
    public static EnhancedTreeMap<Integer, List<Integer>> pointToMap(List<Point> points) {
        return varMutate(new EnhancedTreeMap<>(), (map) -> points.forEach(point -> listPut(map, point.x, point.y)));
    }
    public static EnhancedTreeMap<Integer, List<Integer>> mapToBorderMap(Map<Integer, List<Integer>> map) {
        return map.entrySet().stream().collect(toMap(
                Map.Entry::getKey,
                e -> varOper(returnSort(e.getValue()), va -> Arrays.asList(va.get(0), lastElem(va))),
                (r1, r2) -> uniqueList(concatLists(r1, r2)),
                EnhancedTreeMap::new
        ));
    }
    public static TreeMap<Integer, IntegerBounds> listMapToBoundsMap(Map<Integer, List<Integer>> map) {
        return map.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> new IntegerBounds(e.getValue()), (a, b) -> a, TreeMap::new));
    }
    public static TreeMap<Integer, List<Integer>> boundsMapToListMap(Map<Integer, IntegerBounds> map) {
        return varMutate(new TreeMap<>(), listMap -> map.forEach((col, bounds) -> { listPut(listMap, col, bounds.getLowerBound()); listPut(listMap, col, bounds.getUpperBound()); }));
    }
    public static TreeMap<Double, List<Double>> mapToDoubleMap(Map<Integer, List<Integer>> map) {
        return map.entrySet().stream().collect(toMap(
                e -> e.getKey().doubleValue(),
                e -> e.getValue().stream().mapToDouble(Integer::doubleValue).boxed().toList(),
                (r1, r2) -> uniqueList(concatLists(r1, r2)),
                TreeMap::new
        ));
    }





    public static String sepShapeQuery(Point p, int sideLen) {
        return (p.x / sideLen) + "," + (p.y / sideLen);
    }



    protected static Set<Point> groupRecursSearch(PointCollection allPoints, Point initPt, PointCollection cluster, double dist) {

        Set<Point> endpoints = new HashSet<>();
        Set<Integer> xPresent = new HashSet<>();
        Consumer<Integer> angleFn = (angle) -> {
            double roc = MathEx.tan(Math.toRadians(angle));
            Point lastPt = initPt;
            int lastX = initPt.x, lastY = initPt.y, currX = 0;
            while (lastPt.distance(lastX, lastY) <= dist) {
                currX += MathEx.signClamp(angle);
                int y = MathEx.roundInt(roc * currX);
                lastX = initPt.x + currX;
                lastY = initPt.y + y;

                Point pt = new Point(currX + initPt.x, y + initPt.y);
                if (allPoints.contains(pt)) {
                    cluster.add(pt);
                    allPoints.remove(pt);
                    lastPt = pt;
                }
            }
            if (!initPt.equals(lastPt)) endpoints.add(lastPt);
//            listPut(endpointSlopes, new Point(lastX, lastY), roc);
        };
        Consumer<Integer> yFn = (dir) -> {
            Point lastPt = initPt;
            int currY = 0;
            while (lastPt.distance(lastPt.x, lastPt.y + currY) <= dist) {
                currY += dir;

                Point pt = new Point(initPt.x, initPt.y + currY);
                if (allPoints.contains(pt)) {
                    cluster.add(pt);
                    allPoints.remove(pt);
                    lastPt = pt;
                }
            }
            if (!initPt.equals(lastPt)) endpoints.add(lastPt);
//            listPut(endpointSlopes, new Point(initPt.x, lastY), dir.doubleValue());
        };

        AtomicBoolean closestFound = new AtomicBoolean(false);
        Consumer<Integer> xFn = (dir) -> {
            Point lastPt = initPt;
            int currX = 0;
            while (lastPt.distance(lastPt.x + currX, lastPt.y) <= dist) {
                currX += dir;

                Point pt = new Point(initPt.x + currX, initPt.y);
                if (allPoints.contains(pt)) {
                    cluster.add(pt);
                    allPoints.remove(pt);
                    lastPt = pt;
                }
                if (!xPresent.contains(dir) && allPoints.containsX(pt.x)) {
                    xPresent.add(dir);
                    int[] allClosest = allPoints.getYesAtX(initPt.x + currX).closest(initPt.y);
                    for (int res : allClosest) {
                        if (res == initPt.y) continue;
                        varExec(new Point(initPt.x + currX, res), resPt -> {
                            if (resPt.distance(initPt) <= dist) {
                                cluster.add(resPt);
                                allPoints.remove(resPt);
                                closestFound.set(true);
                            }
                        });
                    }
                }
            }
            if (!initPt.equals(lastPt)) endpoints.add(lastPt);
//            listPut(endpointSlopes, new Point(lastX, initPt.y), dir.doubleValue());
        };


        xFn.accept(1);
        xFn.accept(-1);
        yFn.accept(1);
        yFn.accept(-1);

        allPoints.remove(initPt);
        cluster.add(initPt);
        if (!closestFound.get()) return endpoints;

        int end = xPresent.contains(1) ? 180 : -1;
        for (int angle = (xPresent.contains(-1) ? -178 : 2); angle < end; angle += 2) {
            if (anyEquals(angle, -90, 0, 90)) continue;
            angleFn.accept(angle);
        }

        return endpoints;
    }

    public static PointCollection deriveGroup(PointCollection all, PointCollection src, double dist) {
        all = new PointCollection(all);
        PointCollection fullCluster = new PointCollection();
        Queue<Point> toSearch = new ArrayDeque<>(src.size());
        toSearch.addAll(src.stream().toList());
        while (!toSearch.isEmpty()) {
            toSearch.addAll(groupRecursSearch(all, toSearch.poll(), fullCluster, dist));
        }
        return fullCluster;
    }

    public static List<PointCollection> groupPoints(PointCollection allPointsOrig, double dist) {
        PointCollection allPoints = new PointCollection(allPointsOrig);
        List<PointCollection> clusters = new ArrayList<>();
        while (!allPoints.isEmpty()) {
            PointCollection cluster = new PointCollection();
            Queue<Point> toSearch = new ArrayDeque<>(allPoints.size());
            toSearch.add(new Point(allPoints.firstX(), allPoints.getYesAtX(allPoints.firstX()).get(0)));

            while (!toSearch.isEmpty()) {
                toSearch.addAll(groupRecursSearch(allPoints, toSearch.poll(), cluster, dist));
            }
            clusters.add(cluster);
            /*
            clusters.add(ProgrammingEx.varMutate(new PointCollection(),
//                    cluster -> groupRecursSearch(allPoints, allPoints.get((int) (Math.random() * allPoints.size())), cluster, dist)));
                    cluster -> groupRecursSearch(allPoints, new Point(302, 266), cluster, dist)));*/
        }
        return clusters;
    }





    // todo: input custom distance
    public static List<List<Point>> groupPointsOldUnionFind(List<Point> points) {
        Map<Point, Integer> precomputedIndices = flipMapSingular(listToNumberedMap(points));
        int d = 3, queryShapeLen = 2*d, n = points.size();
        UnionFind uf = new UnionFind(n);
        Map<String, List<Point>> grid = varMutate(new HashMap<>(),
                g -> points.forEach(pt -> varOper(sepShapeQuery(pt, queryShapeLen), key -> g.computeIfAbsent(key, k -> new ArrayList<>()).add(pt))));

        int[] dirs = {-1, 0, 1};
        ProgrammingEx.forNumber(i -> {
            Point p = points.get(i);
            // iterate through all cardinal directions based off (semi-)permutations of base directions
            for (int dx : dirs) { for (int dy : dirs) {
                ((ForEach<Point>) new ForEach(grid.getOrDefault((p.x / queryShapeLen + dx) + "," + (p.y / queryShapeLen + dy), new ArrayList<>()))).execute((neighbor, l) -> OptionalEx.ofCond(0, p.distance(neighbor) <= (d*d)).thenRun(() -> uf.union(i, precomputedIndices.get(neighbor))));
            }}
        }, n);
        return valueList(varMutate(new HashMap<>(), (groups) -> new ForIncrement(0, n, 1).execute(i -> groups.computeIfAbsent(uf.find(i.intValue()), k -> new ArrayList<>()).add(points.get(i.intValue())))));
    }

    public static List<List<Point>> groupPointsOldUnionFind(Map<Integer, List<Integer>> points) {
        return groupPointsOldUnionFind(mapToPoint(points));
    }


    public static ShapeInfo processShape(PointCollection shapePts, ShapeInfo baseShapeInfo) throws IOException {
        // TODO: Make this stream the points properly not this weird way
        shapePts.onRaw();
        Point bottomPt = varOper(shapePts.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().last()))
                .max(Comparator.comparingInt(AbstractMap.SimpleEntry::getValue)).orElseThrow(), e -> new Point(e.getKey(), e.getValue()));
        shapePts.onRaw();
        Point topPt = varOper(shapePts.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().first()))
                .min(Comparator.comparingInt(AbstractMap.SimpleEntry::getValue)).orElseThrow(), e -> new Point(e.getKey(), e.getValue()));
        int leftVerticeCol = shapePts.firstX(), rightVerticeCol = shapePts.lastX();

        int midY = MathEx.midpoint(bottomPt.y, topPt.y);
        shapePts.onRaw();
        // todo: account for different centers and inners with different centers
        PointCollection inner = new PointCollection() {{
            shapePts.forEachRaw((x, yes) -> {
//                IntList yesD = yes.discontinuities();
                if (yes.contains(midY)) return;
                int[] closest = yes.closest(midY);
                addAtX(x, yes.get(closest[0]));
                addAtX(x, yes.get(lastElem(closest)));
            });
        }};
//        int middleX = MathEx.midpoint(rightVerticeCol, leftVerticeCol);
//        int middleY = MathEx.midpoint(bottomPt.y, topPt.y);


//        int rectWidth = rightVerticeCol - leftVerticeCol;
//        int rectHeight = bottomPt.y - topPt.y;
//        double iHatScalar = baseShapeInfo == null ? 1 : MathEx.divide(rectWidth, baseShapeInfo.rectWidth());
//        double jHatScalar = baseShapeInfo == null ? 1 : MathEx.divide(rectHeight, baseShapeInfo.rectHeight());
//        Space2D spacePlane = new Space2D(Space2D.DEFAULT_IHAT.scalarMultiply(iHatScalar), Space2D.DEFAULT_JHAT.scalarMultiply(jHatScalar), new Point(middleX, middleY));
//        TreeMap<Double, Vector2D> vecDirs = varMutate(new TreeMap<>(), (map) -> shapePts.stream().map(pt -> spacePlane.vectorizeAbsolute(pt.getX(), pt.getY())).forEach(vec -> map.put(spacePlane.vectorDirection(vec), vec)));
//        MouseCanvas canvas = new MouseCanvas("window");
//        valueList(vecDirs).stream().forEach(vec -> canvas.drawLine(250, 250, (int) (250+vec.x()),(int) (250+vec.y())));
//        canvas.appear();

//        List<Map<Double, Vector2D>> grpedVecDirs = varMutate(new ArrayList<>(), (list) -> new ForIncrement(0, 360, minVecDist).execute((currDeg, forLoop) -> list.add(StreamEx.toMap(keyList(vecDirs).stream().filter(dir -> dir >= (currDeg - minVecDist) && dir <= currDeg).map(v -> Map.entry(v, vecDirs.get(v))).peek(entry -> vecDirs.remove(entry.getKey()))))));
        /*List<TreeMap<Double, Vector2D>> grpedVecDirs = groupByDist(keyList(vecDirs), minVecDist).stream()
                .map(bucketKeys -> StreamEx.toMap(
                        bucketKeys.stream()
                                .map(k -> Map.entry(k, vecDirs.get(k))),
                        TreeMap::new
                ))
                .toList();*/

//        Map<Double, Vector2D> minVecs = grpedVecDirs.stream().map(grp -> grp.entrySet().stream().min(Comparator.comparingDouble(vecE -> vecE.getValue().getLength())).orElseThrow()).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
//        List<Vector2D> minVecs = valueList(StreamEx.toMap(grpedVecDirs.stream().map(grp -> grp.entrySet().stream().min(Comparator.comparingDouble(vecE -> vecE.getValue().getLength())).orElse(null)).filter(Objects::nonNull), TreeMap::new));
//        minVecs.stream().forEach(vec -> canvas.drawLine(250, 250, (int) (250+(vec.x()*2)),(int) (250+(vec.y()*2))));
//        canvas.appear();


        double sizeRatio = MathEx.divide(new SimpsonArea(2).calculate(shapePts), (rightVerticeCol - leftVerticeCol) * (bottomPt.y - topPt.y));

        return new ShapeInfo(
                inner,
                topPt,
                bottomPt,
                rightVerticeCol - leftVerticeCol,
                bottomPt.y - topPt.y,
                sizeRatio
        );
    }
    public static ShapeInfo processShape(PointCollection shapePts) throws IOException {
        return processShape(shapePts, null);
    }



    public static FastRGB scaleImageNearestNeighbor(FastRGB origImage,
                                                    TreeMap<Integer, IntegerBounds> origBounds,
                                                    TreeMap<Integer, IntegerBounds> newBounds) {

//        int origWidth = origImage.getWidth(), origHeight = origImage.getHeight();
        int origWidth = origBounds.lastKey() - origBounds.firstKey();
        int origHeight = origBounds.values().stream()
                .map(IntegerBounds::asList)
                .flatMap(List::stream)
                .max(Comparator.naturalOrder()).orElseThrow();
        int newWidth = newBounds.lastKey() - newBounds.firstKey();
        int newHeight = newBounds.values().stream()
                .map(IntegerBounds::asList)
                .flatMap(List::stream)
                .max(Comparator.naturalOrder()).orElseThrow();

        Space2D image = new Space2D(new Point(origWidth / 2, origHeight / 2));
        // todo: remove this hack
        origBounds.remove(260);


        Map<Integer, ObjBounds<List<Integer>>> xEdges = origBounds.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> varOper(e.getValue(), bds -> new ObjBounds<>(
                        IntStream.rangeClosed(bds.getLowerBound(), bds.getLowerBound() + 6)
                                .map(i -> origImage.getRGBRaw(e.getKey(), i))
                                .boxed().toList(),
                        IntStream.rangeClosed(bds.getUpperBound() - 6, bds.getUpperBound())
                                .map(i -> origImage.getRGBRaw(e.getKey(), i))
                                .boxed().toList()
                ))
        ));

        // todo: only works for circles for testing & development purposes
        // each maps yes to xes
        Map<Integer, IntegerBounds> leftYBounds = new HashMap<>(), rightYBounds = new HashMap<>();
        // for now first 6 keys
        // todo: make this dynamic based off calculus
        IntStream.rangeClosed(origBounds.firstKey(), origBounds.firstKey() + 4)
                .forEach(key -> origBounds.get(key).asList().forEach(y -> {
                    if (!leftYBounds.containsKey(y)) {
                        leftYBounds.put(y, new IntegerBounds(key, key));
                    } else {
                        leftYBounds.get(y).introduceBound(key);
                    }
                }));
        IntStream.rangeClosed(origBounds.lastKey() - 4, origBounds.lastKey())
                .forEach(key -> origBounds.get(key).asList().forEach(y -> {
                    if (!rightYBounds.containsKey(y)) {
                        rightYBounds.put(y, new IntegerBounds(key, key));
                    } else {
                        rightYBounds.get(y).introduceBound(key);
                    }
                }));

        // maps yes to a mapping of xes to colors at that cord
        Map<Integer, Map<Integer, Integer>> leftYEdges = leftYBounds.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> StreamEx.toMap(e.getValue().getSequence().stream()
                        .map(x -> new AbstractMap.SimpleEntry<>(x, origImage.getRGBRaw(x, e.getKey()))))
        ));
        Map<Integer, Map<Integer, Integer>> rightYEdges = rightYBounds.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> StreamEx.toMap(e.getValue().getSequence().stream()
                        .map(x -> new AbstractMap.SimpleEntry<>(x, origImage.getRGBRaw(x, e.getKey()))))
        ));



        FastRGB result = new FastRGB(newWidth, newHeight, origImage.hasAlphaChannel());

        int origMinX = origBounds.firstKey(), origMaxX = origBounds.lastKey();
        new CoordIterator(newWidth, newHeight).execute((x, y, xLoop, yLoop) -> {
            if (!newBounds.containsKey(x)) {
                xLoop.Continue();
                return;
            }
            if (!newBounds.get(x).inBetweenClosed(y)) {
                return;
            }

//            int srcX = (int)
//                    MathEx.bound(x * MathEx.divide(origWidth, newWidth), origBounds.firstKey() + 4, origBounds.lastKey() - 4);
            int srcX = (int) (MathEx.divide(x.intValue(), newWidth) * origWidth) + origBounds.firstKey();
            IntegerBounds bds = origBounds.get(srcX);
            if (bds == null) {
                String u = "8";
//            int srcY = (int) MathEx.bound(y * MathEx.divide(origHeight, newHeight), bds.getLowerBound(), bds.getUpperBound())
            };
            int srcY = (int) (MathEx.divide((y - newBounds.get(x).getLowerBound()), newBounds.get(x).getLength()) * bds.getLength()) + bds.getLowerBound();
            if (x == 39 && y == 79) {
                String yfndaskjfad = "u";
            }


            // todo: remove the need for these boilerplate manual checks
            if (Math.abs(srcX - origMinX) <= 4 && leftYEdges.containsKey(y)) {
                Map<Integer, Integer> xMappings = leftYEdges.get(y).entrySet().stream().collect(Collectors.toMap(
                        e -> (int) (e.getKey() * MathEx.divide(newWidth, origWidth)),
                        Map.Entry::getValue
                ));
                result.setRGB(x, y, xMappings.get(closestElem(keyList(xMappings), x).orElseThrow()));
                return;
            }
            if (Math.abs(srcX - origMinX) <= 4 && rightYEdges.containsKey(y)) {
                Map<Integer, Integer> xMappings = rightYEdges.get(y).entrySet().stream().collect(Collectors.toMap(
                        e -> (int) (e.getKey() * MathEx.divide(newWidth, origWidth)),
                        Map.Entry::getValue
                ));
                result.setRGB(x, y, xMappings.get(closestElem(keyList(xMappings), x).orElseThrow()));
                return;
            }

            if ((srcY - bds.getLowerBound()) <= 6 && xEdges.containsKey(srcX)) {
                result.setRGB(x, y, xEdges.get(srcX).lowerBound().get(srcY - bds.getLowerBound()));
                return;
            }
            if ((bds.getUpperBound() - srcY) <= 6) {
                result.setRGB(x, y, xEdges.get(srcX).upperBound().get(bds.getUpperBound() - srcY));
                return;
            }

            if (srcX >= origWidth) {
                srcX = origWidth - 1;
            }
            if (srcY >= origHeight) {
                srcY = origHeight - 1;
            }

            result.setRGB(x, y, origImage.getRGB(srcX, srcY));
        });

        DebuggerEx.vis("final_result_scale_nn_1_proto", result);
//        DebuggerEx.visM("final_result_scale_nn_1_proto", result, List.of(new PointCollection(mapToPoint(boundsMapToListMap(origBounds))), new PointCollection(mapToPoint(boundsMapToListMap(newBounds)))));

        return result;
    }



    public static int[] scaleImageNearestNeighbor(FastRGB pixelHandler, TreeMap<Integer, IntegerBounds> shapeBounds, int targetW, int targetH) {
        int width = pixelHandler.getWidth(), height = pixelHandler.getHeight(), channels = 3;

        Space2D image = new Space2D(new Point(width / 2, height / 2));
        Space2D scaledSpace = new Space2D(Space2D.DEFAULT_IHAT.multiply(MathEx.divide(targetW, width)), Space2D.DEFAULT_JHAT.multiply(MathEx.divide(targetH, height)), new Point(width / 2, height / 2));
        shapeBounds.remove(260);


        Map<Integer, ObjBounds<List<Integer>>> xEdges = shapeBounds.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> varOper(e.getValue(), bds -> new ObjBounds<>(
                        IntStream.rangeClosed(bds.getLowerBound(), bds.getLowerBound() + 6)
                                .map(i -> pixelHandler.getRGBRaw(e.getKey(), i))
                                .boxed().toList(),
                        IntStream.rangeClosed(bds.getUpperBound() - 6, bds.getUpperBound())
                                .map(i -> pixelHandler.getRGBRaw(e.getKey(), i))
                                .boxed().toList()
                ))
        ));

        // todo: only works for circles for testing & development purposes
        // each maps yes to xes
        Map<Integer, IntegerBounds> leftYBounds = new HashMap<>(), rightYBounds = new HashMap<>();
        // for now first 6 keys
        // todo: make this dynamic based off calculus
        IntStream.rangeClosed(shapeBounds.firstKey(), shapeBounds.firstKey() + 4)
                .forEach(key -> shapeBounds.get(key).asList().forEach(y -> {
                    if (!leftYBounds.containsKey(y)) {
                        leftYBounds.put(y, new IntegerBounds(key, key));
                    } else {
                        leftYBounds.get(y).introduceBound(key);
                    }
                }));
        IntStream.rangeClosed(shapeBounds.lastKey() - 4, shapeBounds.lastKey())
                .forEach(key -> shapeBounds.get(key).asList().forEach(y -> {
                    if (!rightYBounds.containsKey(y)) {
                        rightYBounds.put(y, new IntegerBounds(key, key));
                    } else {
                        rightYBounds.get(y).introduceBound(key);
                    }
                }));

        // maps yes to a mapping of xes to colors at that cord
        Map<Integer, Map<Integer, Integer>> leftYEdges = leftYBounds.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> StreamEx.toMap(e.getValue().getSequence().stream()
                        .map(x -> new AbstractMap.SimpleEntry<>(x, pixelHandler.getRGBRaw(x, e.getKey()))))
        ));
        Map<Integer, Map<Integer, Integer>> rightYEdges = rightYBounds.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> StreamEx.toMap(e.getValue().getSequence().stream()
                        .map(x -> new AbstractMap.SimpleEntry<>(x, pixelHandler.getRGBRaw(x, e.getKey()))))
        ));



        FastRGB result = new FastRGB(new int[targetW * targetH], targetW, targetH, pixelHandler.hasAlphaChannel());

        // todo: remove this and figure out issue
        int minX = shapeBounds.firstKey(), maxX = shapeBounds.lastKey();
        new CoordIterator(targetW, targetH).execute((x, y, xLoop, yLoop) -> {

            int srcX = (int)
                    MathEx.bound(x * MathEx.divide(width, targetW), shapeBounds.firstKey() + 4, shapeBounds.lastKey() - 4);
            IntegerBounds bds = shapeBounds.get(srcX);
            int srcY = (int) MathEx.bound(y * MathEx.divide(height, targetH), bds.getLowerBound(), bds.getUpperBound());

            // todo: remove the need for these boilerplate manual checks
            if (Math.abs(srcX - minX) <= 4 && leftYEdges.containsKey(y)) {
                Map<Integer, Integer> xMappings = leftYEdges.get(y).entrySet().stream().collect(Collectors.toMap(
                        e -> (int) (e.getKey() * MathEx.divide(targetW, width)),
                        Map.Entry::getValue
                ));
                result.setRGB(x, y, xMappings.get(closestElem(keyList(xMappings), x).orElseThrow()));
                return;
            }

            if ((srcY - bds.getLowerBound()) <= 6 && xEdges.containsKey(srcX)) {
                result.setRGB(x, y, xEdges.get(srcX).lowerBound().get(srcY - bds.getLowerBound()));
                return;
            }
            if ((bds.getUpperBound() - srcY) <= 6) {
                result.setRGB(x, y, xEdges.get(srcX).upperBound().get(bds.getUpperBound() - srcY));
                return;
            }
            /*
            if (yEdges.containsKey(srcX) || yEdges.containsKey(srcX - 6) || yEdges.containsKey(srcX + 6)) {
                ObjBounds<Map<Integer, Integer>> xyEdges = yEdges.get(closestKey(yEdges, srcX).orElseThrow());
                if (xyEdges.lowerBound().contains(srcY)) {
                    IntStream.range(srcX, srcX + )
                    closestElem(StreamEx.generalMap(xyEdges.lowerBound(), i -> i*MathEx.divide(targetW, width)), y);
                }
            }*/
/*
            IntegerBounds bd = shapeBounds.get(srcX);
            int additionalOffset = srcX > 230 ? 6 : 0 + (srcX > 235 ? 4 : 0);
            int upperOffset, lowerOffset;
            if ((bd.getLowerBound() + (8 + additionalOffset)) >= bd.getUpperBound()) {
                upperOffset = -(Math.abs(bd.getUpperBound() - (8 + additionalOffset)) + 6);
            } else {
                upperOffset = (8+ additionalOffset);
            }
            if ((bd.getUpperBound() - (8 + additionalOffset)) <= bd.getLowerBound()) {
                lowerOffset = (Math.abs(bd.getLowerBound() - (8 + additionalOffset)) + 6);
            } else {
                lowerOffset = (8+additionalOffset);
            }
            int srcY = varOper(shapeBounds.get(srcX), bds ->
                            MathEx.bound(y * MathEx.divide(height, targetH), bds.getLowerBound() + lowerOffset, bds.getUpperBound() - upperOffset)).intValue();
                            */
            Vector2D srcVec = image.vectorizeAbsolute(srcX, srcY);

//            Vector2D imageVec = image.vectorizeAbsolute(x, y);
//            Vector2D scaledVec = scaledSpace.vectorizeAbsolute(imageVec.x(), imageVec.y());
//            int srcX = MathEx.floorInt(scaledVec.x()), srcY = MathEx.floorInt(scaledVec.y());
            if (srcX >= width) {
                srcX = width - 1;
            }
            if (srcY >= height) {
                srcY = height - 1;
            }

            if (x == 750 && y == 575) {
                String u = "y";
            }
            if ((x > 400 && y == 0) || (x == 0 && y == 0) || (x == targetW - 1 && y == 124)) {
                String hh = "how";
            }
            if (Math.random() < 0.1 && x > 300) {
                String a = " how";
            }
            if (((y * width) + (x)) < 0) {
                String h = "how";
            }
            result.setRGB(x, y, pixelHandler.getRGB(srcX, srcY));

//            result.setRGB(x, y, varMutate(new int[3], rgbArr -> new ForIncrement(0, 2, 1).execute(iR -> varOper(iR.intValue(), i -> rgbArr[i] = closestInterpols.get(i).get(x).intValue()))));
        });

        return result.getPixels();
    }




    protected static List<PointCollection> getBlackGroups(ImageHandler cel) {
        return groupPoints(filterPixelsToPtCollection(cel, blackQual), 3);
    }




    public static void runTasksAsync(List<Runnable> fns) {
        ExecutorService executorService = Executors.newFixedThreadPool(fns.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        fns.forEach(fn -> futures.add(CompletableFuture.runAsync(fn, executorService)));

        CompletableFuture<Void> allThreads = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allThreads.join();
        executorService.shutdown();
    }
    public static void runTasksAsync(Runnable... fns) {
        runTasksAsync(Arrays.stream(fns).collect(Collectors.toList()));
    }

    public static void async(Runnable fn) {
        new Thread(fn).start();
    }
}

