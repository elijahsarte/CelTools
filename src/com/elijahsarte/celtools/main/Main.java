package com.elijahsarte.celtools.main;

import com.elijahsarte.celtools.main.colormodel.HSVBounds;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.FrameParser;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionManager;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionWindow;
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
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.main.util.structures.collections.FixedList;
import com.elijahsarte.celtools.main.util.structures.collections.IntList;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeInfo;
import com.elijahsarte.celtools.mainex.Debugger;
import com.elijahsarte.celtools.mainex.DebuggerEx;
import com.elijahsarte.celtools.mainex.DebugDump;
import com.elijahsarte.celtools.mainex.TaskTracker;
import org.w3c.dom.Node;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.tools.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.elijahsarte.celtools.gui.Application.prefs;
import static com.elijahsarte.celtools.main.util.CollectionsEx.*;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;
import static com.elijahsarte.celtools.mainex.TaskTracker.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;


public abstract class Main {

    protected static final int INK_SCAN_DISTANCE = 5, PENCIL_SCAN_DISTANCE = 10;
    private static final String MAXIMUM_OUTPUT_THREADS_PREFERENCE = "maximum_output_threads";
    private static final String PREVIEW_SHAPE_DATA_PREFERENCE = "preview_shape_data";
    private static final String DEBUG_DUMP_CHOOSER_SCOPE = "debugDumpExportDirectory";
    private static final int DEFAULT_MAXIMUM_OUTPUT_THREADS = 5;
    private static final Object OUTPUT_THREAD_MONITOR = new Object();
    private static int activeOutputThreads = 0;

    protected static final Predicate<double[]> blackQual = (hsv) -> hsv[2] <= 11 || hsv[1] > 11 && hsv[2] <= 25;
    protected static final Predicate<double[]> whiteQual = (hsv) ->
            ((((((int) hsv[1]) >= 0 && ((int) hsv[1]) <= 4) && (((int) hsv[2]) >= (int) Math.floor(((2) / (double) (4)) * Math.abs(((int) hsv[1]) - 0) + 87))) || ((((int) hsv[1]) >= 4 && ((int) hsv[1]) <= 9) && (((int) hsv[2]) >= (int) Math.floor(((8) / (double) (5)) * Math.abs(((int) hsv[1]) - 4) + 89))) || ((((int) hsv[1]) >= 9 && ((int) hsv[1]) <= 11) && (((int) hsv[2]) >= (int) Math.floor(((3) / (double) (2)) * Math.abs(((int) hsv[1]) - 9) + 97)))));


    protected static final Predicate<double[]> DEFAULT_INK_QUAL = (hsv) -> {
        int h = MathEx.floorInt(hsv[0]), s = MathEx.floorInt(hsv[1]), v = MathEx.floorInt(hsv[2]);
        if ((h >= 0 && h <= 19) || (h >= 46 && h <= 214) || h >= 341) return hsv[2] <= 25;
        if (h >= 20 && h <= 45) return (
                ((s >= 0 && s <= 16) &&
                        (v <= (int) Math.floor((((40 - 34) / (double) (16)) * Math.abs(s)) + 34)))
                        || ((s >= 16 && s <= 24) &&
                        (v <= (int) Math.floor((((53 - 40) / (double) (24 - 16)) * Math.abs(s - 16)) + 40)))
                        || ((s >= 24 && s <= 31) &&
                        (v <= (int) Math.floor((((70 - 53) / (double) (31 - 24)) * Math.abs(s - 24)) + 53)))
                        || ((s >= 31 && s <= 41) &&
                        (v <= (int) Math.floor((((80 - 62) / (double) (41 - 31)) * Math.abs(s - 31)) + 62)))
                        || ((s >= 41 && s <= 57) &&
                        (v <= (int) Math.floor((((52 - 64) / (double) (57 - 41)) * Math.abs(s - 41)) + 64)))
                        || ((s >= 57 && s <= 88) &&
                        (v <= (int) Math.floor((((40 - 52) / (double) (88 - 57)) * Math.abs(s - 57)) + 52)))
                        || ((s >= 88 && s <= 100) &&
                        (v <= 40))
        );
        return ((h >= 215 && h <= 340) && ((s >= 0 && s <= 10 && v <= (int) Math.floor(((4) / (double) (10)) * Math.abs(s - 0) + 29)) || (s >= 10 && s <= 20 && v <= (int) Math.floor(((6) / (double) (10)) * Math.abs(s - 10) + 33)) || (s >= 20 && s <= 29 && v <= (int) Math.floor(((14) / (double) (9)) * Math.abs(s - 20) + 39)) || (s >= 29 && s <= 42 && v <= (int) Math.floor(((10) / (double) (13)) * Math.abs(s - 29) + 53)) || (s >= 42 && s <= 48 && v <= (int) Math.floor(((-1) / (double) (6)) * Math.abs(s - 42) + 63)) || (s >= 48 && s <= 68 && v <= (int) Math.floor(((-31) / (double) (20)) * Math.abs(s - 48) + 62)) || (s >= 68 && s <= 88 && v <= (int) Math.floor(((-2) / (double) (20)) * Math.abs(s - 68) + 31)) || (s >= 88 && s <= 100 && v <= (int) Math.floor(((-1) / (double) (12)) * Math.abs(s - 88) + 29))));
    };
    public static Predicate<double[]> inkQual = DEFAULT_INK_QUAL;

    public static final String DEFAULT_INK_QUALIFIER_NAME = "Default";
    public static final String INK_QUALIFIER_FILE_EXTENSION = "ctinkqual";
    private static final String INK_QUALIFIER_MAGIC = "CelTools Ink Qualifier Lambda";
    private static final int INK_QUALIFIER_VERSION = 3;
    private static final String INK_QUALIFIER_SPEC_MARKER = "CTINKQUAL-BAND-TABLE";
    private static final String INK_QUALIFIER_GENERATED_PACKAGE = "com.elijahsarte.celtools.generated";
    private static final int INK_QUALIFIER_HUE_BUCKETS = 360;
    private static final int INK_QUALIFIER_SATURATION_BUCKETS = 101;
    private static final int INK_QUALIFIER_CELL_COUNT = INK_QUALIFIER_HUE_BUCKETS * INK_QUALIFIER_SATURATION_BUCKETS;
    private static final int INK_QUALIFIER_VALUE_PADDING = 1;
    private static final byte INK_QUALIFIER_NO_VALUE = -1;
    private static final String INK_QUALIFIER_NAMES_PREFERENCE = "ink_qualifier_names";
    private static final String SELECTED_INK_QUALIFIER_PREFERENCE = "selected_ink_qualifier";
    private static final String INK_QUALIFIER_CACHE_DIR_NAME = "ink-qualifiers";
    private static final Map<String, InkQualifierData> CACHED_INK_QUALIFIERS = new LinkedHashMap<>();
    private static String selectedInkQualifierName = DEFAULT_INK_QUALIFIER_NAME;

    static {
        loadCachedInkQualifiersFromPreferences();
        try {
            setCurrentInkQualifierByName(prefs.get(SELECTED_INK_QUALIFIER_PREFERENCE, DEFAULT_INK_QUALIFIER_NAME));
        } catch (RuntimeException ignored) {
            setCurrentInkQualifierByName(DEFAULT_INK_QUALIFIER_NAME);
        }
    }

    public static synchronized List<String> getAvailableInkQualifierNames() {
        ArrayList<String> names = new ArrayList<>();
        names.add(DEFAULT_INK_QUALIFIER_NAME);
        names.addAll(CACHED_INK_QUALIFIERS.keySet());
        return names;
    }

    public static synchronized String getSelectedInkQualifierName() {
        return selectedInkQualifierName;
    }

    public static synchronized void setCurrentInkQualifierByName(String name) {
        String safeName = name == null || name.isBlank() ? DEFAULT_INK_QUALIFIER_NAME : name.trim();
        if (DEFAULT_INK_QUALIFIER_NAME.equals(safeName)) {
            inkQual = DEFAULT_INK_QUAL;
            selectedInkQualifierName = DEFAULT_INK_QUALIFIER_NAME;
        } else {
            InkQualifierData data = CACHED_INK_QUALIFIERS.get(safeName);
            if (data == null) {
                inkQual = DEFAULT_INK_QUAL;
                selectedInkQualifierName = DEFAULT_INK_QUALIFIER_NAME;
                prefs.put(SELECTED_INK_QUALIFIER_PREFERENCE, DEFAULT_INK_QUALIFIER_NAME);
                noExcept(prefs::flush);
                throw new IllegalArgumentException("Unknown ink qualifier: " + safeName);
            }
            inkQual = data.toPredicate();
            selectedInkQualifierName = safeName;
        }
        prefs.put(SELECTED_INK_QUALIFIER_PREFERENCE, selectedInkQualifierName);
        noExcept(prefs::flush);
    }

    public static synchronized String importInkQualifier(File file) throws IOException {
        Objects.requireNonNull(file, "file");
        InkQualifierData data = readInkQualifierData(file);
        String name = ensureExtension(new File(file.getName()), INK_QUALIFIER_FILE_EXTENSION).getName();
        CACHED_INK_QUALIFIERS.put(name, data);
        persistCachedInkQualifiers();
        return name;
    }

    public static synchronized String buildAndSaveInkQualifier(File inkedImageFile, File outputFile) throws IOException {
        Objects.requireNonNull(inkedImageFile, "inkedImageFile");
        Objects.requireNonNull(outputFile, "outputFile");
        File finalOutputFile = ensureExtension(outputFile, INK_QUALIFIER_FILE_EXTENSION);
        ImageHandler image = handlerFromFile(inkedImageFile);
        InkQualifierData data = buildInkQualifierDataFromInkedPixels(
                image.getImgPixels(),
                image.getWidth(),
                image.getHeight()
        );
        writeInkQualifierData(finalOutputFile, data);
        CACHED_INK_QUALIFIERS.put(finalOutputFile.getName(), data);
        persistCachedInkQualifiers();
        return finalOutputFile.getName();
    }

    public static Predicate<double[]> buildInkQualifierFromInkedPixels(int[] pixels, int width, int height) {
        return buildInkQualifierDataFromInkedPixels(pixels, width, height).toPredicate();
    }

    public static Predicate<double[]> buildInkQualifierFromInkedImage(File inkedImageFile) throws IOException {
        ImageHandler image = handlerFromFile(Objects.requireNonNull(inkedImageFile, "inkedImageFile"));
        return buildInkQualifierFromInkedPixels(image.getImgPixels(), image.getWidth(), image.getHeight());
    }

    private static InkQualifierData buildInkQualifierDataFromInkedPixels(int[] pixels, int width, int height) {
        Objects.requireNonNull(pixels, "pixels");

        if (width <= 0 || height <= 0 || pixels.length < width * height) {
            throw new IllegalArgumentException("Invalid image dimensions for ink qualifier build");
        }

        byte[] minValueByHueSaturation = new byte[INK_QUALIFIER_CELL_COUNT];
        byte[] maxValueByHueSaturation = new byte[INK_QUALIFIER_CELL_COUNT];
        Arrays.fill(minValueByHueSaturation, INK_QUALIFIER_NO_VALUE);
        Arrays.fill(maxValueByHueSaturation, INK_QUALIFIER_NO_VALUE);

        AtomicInteger sampleCount = new AtomicInteger();

        FrameParser.filterHSV(
                pixels,
                width,
                height,
                Predicate.not(whiteQual),
                (hPixels, x, y, idx, rawRGB, hsv, colLoop, rowLoop) -> {
                    int h = normalizeHueBucket(hsv[0]);
                    int s = MathEx.bound((int) hsv[1], 0, INK_QUALIFIER_SATURATION_BUCKETS - 1);
                    int v = MathEx.bound((int) hsv[2], 0, 100);
                    int tableIndex = inkQualifierTableIndex(h, s);

                    int oldMin = minValueByHueSaturation[tableIndex];
                    int oldMax = maxValueByHueSaturation[tableIndex];

                    if (oldMin < 0 || v < oldMin) {
                        minValueByHueSaturation[tableIndex] = (byte) v;
                    }
                    if (oldMax < 0 || v > oldMax) {
                        maxValueByHueSaturation[tableIndex] = (byte) v;
                    }
                    sampleCount.getAndIncrement();
                }
        );

        if (sampleCount.get() == 0) {
            throw new IllegalArgumentException("The selected image did not contain any non-white ink pixels to build from.");
        }

        return new InkQualifierData(sampleCount.get(), minValueByHueSaturation, maxValueByHueSaturation);
    }

    private static int inkQualifierTableIndex(int h, int s) {
        return h * INK_QUALIFIER_SATURATION_BUCKETS + s;
    }

    private static void writeInkQualifierData(File file, InkQualifierData data) throws IOException {
        Files.write(ensureExtension(file, INK_QUALIFIER_FILE_EXTENSION).toPath(), encodeInkQualifierData(data));
    }

    private static InkQualifierData readInkQualifierData(File file) throws IOException {
        return decodeInkQualifierData(Files.readAllBytes(Objects.requireNonNull(file, "file").toPath()));
    }

    private static byte[] encodeInkQualifierData(InkQualifierData data) {
        return Objects.requireNonNull(data, "data").toFileText().getBytes(StandardCharsets.UTF_8);
    }

    private static InkQualifierData decodeInkQualifierData(byte[] bytes) throws IOException {
        String text = new String(Objects.requireNonNull(bytes, "bytes"), StandardCharsets.UTF_8);
        if (!text.startsWith(INK_QUALIFIER_MAGIC)) {
            throw new IOException("Not a Cel Tools ink qualifier file");
        }

        int version = readInkQualifierHeaderInt(text, "Version", -1);
        if (version != INK_QUALIFIER_VERSION) {
            throw new IOException("Unsupported ink qualifier version: " + version + ". Rebuild this qualifier.");
        }

        int sampleCount = readInkQualifierHeaderInt(text, "Samples", 0);
        byte[] minValues = decodeInkQualifierTable(readInkQualifierHeaderString(text, "MinValuesBase64"), "MinValuesBase64");
        byte[] maxValues = decodeInkQualifierTable(readInkQualifierHeaderString(text, "MaxValuesBase64"), "MaxValuesBase64");
        return new InkQualifierData(sampleCount, minValues, maxValues);
    }

    private static void loadCachedInkQualifiersFromPreferences() {
        CACHED_INK_QUALIFIERS.clear();
        File cacheDirectory = inkQualifierCacheDirectory();
        for (String name : decodePreferenceNameList(prefs.get(INK_QUALIFIER_NAMES_PREFERENCE, ""))) {
            try {
                File cachedFile = new File(cacheDirectory, name);
                if (cachedFile.isFile()) {
                    CACHED_INK_QUALIFIERS.put(name, readInkQualifierData(cachedFile));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void persistCachedInkQualifiers() {
        File cacheDirectory = inkQualifierCacheDirectory();
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs()) {
            throw new CompletionException(new IOException("Unable to create ink qualifier cache directory: " + cacheDirectory));
        }

        prefs.put(INK_QUALIFIER_NAMES_PREFERENCE, encodePreferenceNameList(CACHED_INK_QUALIFIERS.keySet()));
        for (Map.Entry<String, InkQualifierData> entry : CACHED_INK_QUALIFIERS.entrySet()) {
            try {
                writeInkQualifierData(new File(cacheDirectory, entry.getKey()), entry.getValue());
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }
        noExcept(prefs::flush);
    }

    private static File inkQualifierCacheDirectory() {
        String userHome = System.getProperty("user.home", ".");
        return new File(new File(userHome, ".celtools"), INK_QUALIFIER_CACHE_DIR_NAME);
    }

    private static String encodePreferenceNameList(Collection<String> names) {
        return names.stream()
                .map(name -> Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(name.getBytes(StandardCharsets.UTF_8)))
                .collect(joining(";"));
    }

    private static List<String> decodePreferenceNameList(String encodedNames) {
        if (encodedNames == null || encodedNames.isBlank()) return new ArrayList<>();
        Base64.Decoder decoder = Base64.getUrlDecoder();
        ArrayList<String> names = new ArrayList<>();
        for (String encodedName : encodedNames.split(";")) {
            if (encodedName == null || encodedName.isBlank()) continue;
            try {
                names.add(new String(decoder.decode(encodedName), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return names;
    }

    private static File ensureExtension(File file, String extension) {
        String suffix = "." + extension.toLowerCase(Locale.ROOT);
        return file.getName().toLowerCase(Locale.ROOT).endsWith(suffix)
                ? file
                : new File(file.getAbsolutePath() + suffix);
    }

    private static int normalizeHueBucket(double hue) {
        int bucket = (int) Math.floor(hue);
        bucket %= INK_QUALIFIER_HUE_BUCKETS;
        return bucket < 0 ? bucket + INK_QUALIFIER_HUE_BUCKETS : bucket;
    }

    private static int readInkQualifierHeaderInt(String text, String key, int fallback) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:\\s*(-?\\d+)\\s*$").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static String readInkQualifierHeaderString(String text, String key) throws IOException {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:\\s*(.*?)\\s*$").matcher(text);
        if (!matcher.find()) {
            throw new IOException("Ink qualifier file is missing header: " + key);
        }
        return matcher.group(1).trim();
    }

    private static byte[] decodeInkQualifierTable(String encoded, String key) throws IOException {
        try {
            byte[] table = Base64.getDecoder().decode(encoded);
            if (table.length != INK_QUALIFIER_CELL_COUNT) {
                throw new IOException(
                        key + " has invalid length: " + table.length +
                                ", expected " + INK_QUALIFIER_CELL_COUNT
                );
            }
            return table;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Base64 data for " + key, exception);
        }
    }

    private static Predicate<double[]> compileInkQualifierPredicate(InkQualifierData data) throws IOException, ReflectiveOperationException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("JDK compiler is not available; custom ink qualifiers require a JDK runtime.");
        }

        String className = "InkQualifier_" + Integer.toUnsignedString(
                (data.minValuesBase64() + data.maxValuesBase64()).hashCode(),
                36
        );
        String fullClassName = INK_QUALIFIER_GENERATED_PACKAGE + "." + className;
        String packagePath = INK_QUALIFIER_GENERATED_PACKAGE.replace('.', File.separatorChar);

        File generatedRoot = new File(inkQualifierCacheDirectory(), "generated");
        File sourceDirectory = new File(new File(generatedRoot, "src"), packagePath);
        File classDirectory = new File(generatedRoot, "classes");
        if ((!sourceDirectory.isDirectory() && !sourceDirectory.mkdirs())
                || (!classDirectory.isDirectory() && !classDirectory.mkdirs())) {
            throw new IOException("Unable to create generated ink qualifier directory: " + generatedRoot);
        }

        File sourceFile = new File(sourceDirectory, className + ".java");
        Files.writeString(sourceFile.toPath(), buildInkQualifierClassSource(className, data), StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classDirectory));
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile));

            ArrayList<String> options = new ArrayList<>();
            String classpath = System.getProperty("java.class.path", "");
            if (!classpath.isBlank()) {
                options.add("-classpath");
                options.add(classpath);
            }

            Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IOException("Unable to compile ink qualifier:\n" + summarizeDiagnostics(diagnostics));
            }
        }

        URL[] urls = { classDirectory.toURI().toURL() };
        try (URLClassLoader loader = new URLClassLoader(urls, Main.class.getClassLoader())) {
            Class<?> generatedClass = Class.forName(fullClassName, true, loader);
            Object qualifier = generatedClass.getField("QUALIFIER").get(null);
            if (!(qualifier instanceof Predicate<?> predicate)) {
                throw new IOException("Generated ink qualifier did not expose a Predicate");
            }
            @SuppressWarnings("unchecked")
            Predicate<double[]> typedPredicate = (Predicate<double[]>) predicate;
            return typedPredicate;
        }
    }

    private static String summarizeDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .limit(8)
                .map(diagnostic -> "line " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(Locale.ROOT))
                .collect(joining("\n"));
    }

    private static String buildInkQualifierClassSource(String className, InkQualifierData data) {
        String minBase64 = data.minValuesBase64();
        String maxBase64 = data.maxValuesBase64();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(INK_QUALIFIER_GENERATED_PACKAGE).append(";\n\n");
        sb.append("public final class ").append(className).append(" {\n");
        sb.append("    private ").append(className).append("() {}\n\n");
        sb.append("    private static final int HUE_BUCKETS = ").append(INK_QUALIFIER_HUE_BUCKETS).append(";\n");
        sb.append("    private static final int SATURATION_BUCKETS = ").append(INK_QUALIFIER_SATURATION_BUCKETS).append(";\n");
        sb.append("    private static final int VALUE_PADDING = ").append(INK_QUALIFIER_VALUE_PADDING).append(";\n");
        sb.append("    private static final byte[] MIN_VALUES = java.util.Base64.getDecoder().decode(\"")
                .append(minBase64)
                .append("\");\n");
        sb.append("    private static final byte[] MAX_VALUES = java.util.Base64.getDecoder().decode(\"")
                .append(maxBase64)
                .append("\");\n\n");
        sb.append("    public static final java.util.function.Predicate<double[]> QUALIFIER = ")
                .append(className)
                .append("::test;\n\n");
        sb.append("    private static boolean test(double[] hsv) {\n");
        sb.append("        if (hsv == null || hsv.length < 3) return false;\n");
        sb.append("        int h = (int) Math.floor(hsv[0]);\n");
        sb.append("        h %= HUE_BUCKETS;\n");
        sb.append("        if (h < 0) h += HUE_BUCKETS;\n");
        sb.append("        int s = (int) hsv[1];\n");
        sb.append("        int v = (int) hsv[2];\n");
        sb.append("        if (s < 0 || s >= SATURATION_BUCKETS || v < 0 || v > 100) return false;\n");
        sb.append("        int index = h * SATURATION_BUCKETS + s;\n");
        sb.append("        int min = MIN_VALUES[index];\n");
        sb.append("        if (min < 0) return false;\n");
        sb.append("        int max = MAX_VALUES[index];\n");
        sb.append("        return v >= Math.max(0, min - VALUE_PADDING) && v <= Math.min(100, max + VALUE_PADDING);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String serializeInkQualifierLambdaJava(InkQualifierData data, String predicateName) {
        String safeName = sanitizeJavaIdentifier(predicateName, "inkQual");
        StringBuilder sb = new StringBuilder();
        sb.append("protected static final java.util.function.Predicate<double[]> ")
                .append(safeName)
                .append(" = new java.util.function.Predicate<>() {\n");
        sb.append("    private final int HUE_BUCKETS = ").append(INK_QUALIFIER_HUE_BUCKETS).append(";\n");
        sb.append("    private final int SATURATION_BUCKETS = ").append(INK_QUALIFIER_SATURATION_BUCKETS).append(";\n");
        sb.append("    private final int VALUE_PADDING = ").append(INK_QUALIFIER_VALUE_PADDING).append(";\n");
        sb.append("    private final byte[] MIN_VALUES = java.util.Base64.getDecoder().decode(\"")
                .append(data.minValuesBase64())
                .append("\");\n");
        sb.append("    private final byte[] MAX_VALUES = java.util.Base64.getDecoder().decode(\"")
                .append(data.maxValuesBase64())
                .append("\");\n\n");
        sb.append("    @Override\n");
        sb.append("    public boolean test(double[] hsv) {\n");
        sb.append("        if (hsv == null || hsv.length < 3) return false;\n");
        sb.append("        int h = (int) Math.floor(hsv[0]);\n");
        sb.append("        h %= HUE_BUCKETS;\n");
        sb.append("        if (h < 0) h += HUE_BUCKETS;\n");
        sb.append("        int s = (int) hsv[1];\n");
        sb.append("        int v = (int) hsv[2];\n");
        sb.append("        if (s < 0 || s >= SATURATION_BUCKETS || v < 0 || v > 100) return false;\n");
        sb.append("        int index = h * SATURATION_BUCKETS + s;\n");
        sb.append("        int min = MIN_VALUES[index];\n");
        sb.append("        if (min < 0) return false;\n");
        sb.append("        int max = MAX_VALUES[index];\n");
        sb.append("        return v >= Math.max(0, min - VALUE_PADDING) && v <= Math.min(100, max + VALUE_PADDING);\n");
        sb.append("    }\n");
        sb.append("};\n");
        return sb.toString();
    }

    private static String sanitizeJavaIdentifier(String raw, String fallback) {
        String value = (raw == null || raw.isBlank()) ? fallback : raw.trim();
        value = value.replaceAll("[^A-Za-z0-9_$]", "_");
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            value = "_" + value;
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        return out.toString();
    }

    private static final class InkQualifierData {
        private final int sampleCount;
        private final byte[] minValueByHueSaturation;
        private final byte[] maxValueByHueSaturation;
        private transient Predicate<double[]> predicate;

        private InkQualifierData(int sampleCount, byte[] minValueByHueSaturation, byte[] maxValueByHueSaturation) {
            this.sampleCount = Math.max(0, sampleCount);
            if (minValueByHueSaturation == null || minValueByHueSaturation.length != INK_QUALIFIER_CELL_COUNT) {
                throw new IllegalArgumentException("Invalid min-value HSV table");
            }
            if (maxValueByHueSaturation == null || maxValueByHueSaturation.length != INK_QUALIFIER_CELL_COUNT) {
                throw new IllegalArgumentException("Invalid max-value HSV table");
            }
            this.minValueByHueSaturation = minValueByHueSaturation.clone();
            this.maxValueByHueSaturation = maxValueByHueSaturation.clone();
        }

        private String toFileText() {
            StringBuilder sb = new StringBuilder();
            sb.append(INK_QUALIFIER_MAGIC).append('\n');
            sb.append("Version: ").append(INK_QUALIFIER_VERSION).append('\n');
            sb.append("Samples: ").append(sampleCount).append('\n');
            sb.append("ValuePadding: ").append(INK_QUALIFIER_VALUE_PADDING).append('\n');
            sb.append("MinValuesBase64: ").append(minValuesBase64()).append('\n');
            sb.append("MaxValuesBase64: ").append(maxValuesBase64()).append('\n');
            sb.append('\n');
            sb.append("/* ").append(INK_QUALIFIER_SPEC_MARKER).append('\n');
            sb.append("Each H/S bucket stores observed min and max V values.\n");
            sb.append("The predicate accepts only V values inside that learned band plus padding.\n");
            sb.append("*/\n\n");
            sb.append(serializeInkQualifierLambdaJava(this, "inkQual"));
            return sb.toString();
        }

        private Predicate<double[]> toPredicate() {
            if (predicate != null) return predicate;
            try {
                predicate = compileInkQualifierPredicate(this);
                return predicate;
            } catch (IOException | ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to compile ink qualifier: " + exception.getMessage(), exception);
            }
        }

        private String minValuesBase64() {
            return Base64.getEncoder().encodeToString(minValueByHueSaturation);
        }

        private String maxValuesBase64() {
            return Base64.getEncoder().encodeToString(maxValueByHueSaturation);
        }
    }


    protected final Map<String, String> args;
    public Main(Map<String, String> args) {
        this.args = args;
    }

    public abstract void run() throws Exception;

    public final String chooserScopeKey() {
        return getClass().getSimpleName();
    }

    public static File chooserLastDirectory(String scopeKey) {
        String path = prefs.get(scopeKey + "_lastDir", null);
        return path == null ? null : new File(path);
    }

    public static JFileChooser chooserFor(String scopeKey) {
        return chooserFor(scopeKey, chooserLastDirectory(scopeKey));
    }

    public static JFileChooser chooserFor(String scopeKey, File currentDirectory) {
        JFileChooser chooser = new JFileChooser();
        if (currentDirectory != null) {
            chooser.setCurrentDirectory(currentDirectory);
        }
        return chooser;
    }

    public static void rememberChooserDirectory(String scopeKey, File selectedFile) {
        if (selectedFile == null) {
            return;
        }
        rememberChooserDirectory(scopeKey, selectedFile, selectedFile.isDirectory());
    }

    public static void rememberChooserDirectory(String scopeKey, File selectedFile, boolean treatAsDirectory) {
        if (selectedFile == null) {
            return;
        }
        File directory = treatAsDirectory || selectedFile.isDirectory()
                ? selectedFile
                : selectedFile.getParentFile();
        if (directory != null) {
            prefs.put(scopeKey + "_lastDir", directory.getAbsolutePath());
            noExcept(prefs::flush);
        }
    }



    public static File createDebugDump(Component parent) {
        return createDebugDump(parent, null);
    }

    public static File createDebugDump(Component parent, Throwable exception) {
        JFileChooser chooser = chooserFor(DEBUG_DUMP_CHOOSER_SCOPE);
        chooser.setDialogTitle(exception == null ? "Create Debug Dump" : "Create Exception Debug Dump");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setApproveButtonText("Export");
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File directory = chooser.getSelectedFile();
        rememberChooserDirectory(DEBUG_DUMP_CHOOSER_SCOPE, directory, true);
        File output = nextDebugDumpFile(directory);

        try {
            File written = DebugDump.write(output, Debugger.list(), exception);
            JOptionPane.showMessageDialog(
                    parent,
                    "Debug dump exported to:\n" + written.getAbsolutePath(),
                    "Debug Dump Exported",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return written;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Unable to create debug dump:\n" + ex.getMessage(),
                    "Debug Dump Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
    }

    private static File nextDebugDumpFile(File directory) {
        if (directory == null) directory = new File(System.getProperty("user.home"));
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
                .format(java.time.LocalDateTime.now());
        File candidate = new File(directory, "celtools_debug_" + timestamp + "." + DebugDump.FILE_EXTENSION);
        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(directory, "celtools_debug_" + timestamp + "_" + suffix++ + "." + DebugDump.FILE_EXTENSION);
        }
        return candidate;
    }


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

    public static ImageHandler handlerFromFile(File file, int imageType) {
        return varMutate(new ImageHandler(file, imageType), i -> noExcept(i::loadImage));
    }
    public static ImageHandler handlerFromFile(File file) {
        return varMutate(new ImageHandler(file), i -> noExcept(i::loadImage));
    }
    public static ImageHandler handlerFromFile(String filePath, int imageType) {
        return handlerFromFile(new File(filePath), imageType);
    }
    public static ImageHandler handlerFromFile(String filePath) {
        return handlerFromFile(new File(filePath));
    }

    protected static File[] sortedFilesByName(File[] files) {
        if (files == null) return null;
        Arrays.sort(files, (a, b) -> compareFileNamesByExplorerOrder(a.getName(), b.getName()));
        return files;
    }

    private static int compareFileNamesByExplorerOrder(String a, String b) {
        int aIndex = 0, bIndex = 0;
        while (aIndex < a.length() && bIndex < b.length()) {
            char aChar = a.charAt(aIndex);
            char bChar = b.charAt(bIndex);
            if (Character.isDigit(aChar) && Character.isDigit(bChar)) {
                int aStart = aIndex;
                int bStart = bIndex;
                while (aIndex < a.length() && Character.isDigit(a.charAt(aIndex))) aIndex++;
                while (bIndex < b.length() && Character.isDigit(b.charAt(bIndex))) bIndex++;
                int aNumberStart = aStart;
                int bNumberStart = bStart;
                while (aNumberStart < aIndex && a.charAt(aNumberStart) == '0') aNumberStart++;
                while (bNumberStart < bIndex && b.charAt(bNumberStart) == '0') bNumberStart++;
                int aNumberLength = aIndex - aNumberStart;
                int bNumberLength = bIndex - bNumberStart;
                if (aNumberLength != bNumberLength) return Integer.compare(aNumberLength, bNumberLength);
                for (int i = 0; i < aNumberLength; i++) {
                    int cmp = Character.compare(a.charAt(aNumberStart + i), b.charAt(bNumberStart + i));
                    if (cmp != 0) return cmp;
                }
                int cmp = Integer.compare(aIndex - aStart, bIndex - bStart);
                if (cmp != 0) return cmp;
                continue;
            }
            int cmp = Character.compare(Character.toLowerCase(aChar), Character.toLowerCase(bChar));
            if (cmp != 0) return cmp;
            aIndex++;
            bIndex++;
        }
        return Integer.compare(a.length(), b.length());
    }

    private static String imageFormatForPath(String outputCelPath) {
        String name = new File(outputCelPath).getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "png";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    protected static ImageHandler loadCel(File celFile, FastRGB referenceCel) {
        return loadCel(celFile, referenceCel, true);
    }

    protected static ImageHandler loadCel(
            File celFile,
            FastRGB referenceCel,
            boolean allowRotationAndScaling
    ) {
        trackAsync("opening next cel " + celFile.getName());
        ImageHandler celHandler = handlerFromFile(celFile);
        endTrackAsync("opening next cel " + celFile.getName());

        if (celHandler.getWidth() == referenceCel.getWidth()
                && celHandler.getHeight() == referenceCel.getHeight()) {
            return celHandler;
        }

        if (!allowRotationAndScaling) {
            throw new IllegalArgumentException(
                    "Cel dimensions must match the reference cel: " + celFile.getName()
                            + " is " + celHandler.getWidth() + "x" + celHandler.getHeight()
                            + ", expected " + referenceCel.getWidth() + "x" + referenceCel.getHeight()
            );
        }

        trace("WARNING: cel and background cel are not the same size, output may be degraded");
        FastRGB cel = new FastRGB(celHandler.getImage(), false);
        double eps = 1e-9,
                scale0x = MathEx.divide(referenceCel.getWidth(), cel.getWidth()),
                scale0y = MathEx.divide(referenceCel.getHeight(), cel.getHeight()),
                scale90x = MathEx.divide(referenceCel.getWidth(), cel.getHeight()),
                scale90y = MathEx.divide(referenceCel.getHeight(), cel.getWidth());

        boolean rot0Or180WithScale = Math.abs(scale0x - scale0y) < eps,
                rot90Or270WithScale = Math.abs(scale90x - scale90y) < eps,
                rot0Or180NoScale = rot0Or180WithScale && Math.abs(scale0x - 1.0) < eps,
                rot90Or270NoScale = rot90Or270WithScale && Math.abs(scale90x - 1.0) < eps;

        if (rot90Or270NoScale) {
            trackAsync("rotating next cel to match background cel");
            cel.rotate90Clockwise();
            endTrackAsync("rotating next cel to match background cel");
        } else if (rot0Or180NoScale) {
            trace("cel already matches background dimensions; if rotated, it can only be 180 degrees");
        } else if (rot90Or270WithScale) {
            trackAsync("rotating/scaling next cel to match background cel");
            cel.rotate90Clockwise();
            cel.upscale(scale90x * 100.0);
            endTrackAsync("rotating/scaling next cel to match background cel");
        } else if (rot0Or180WithScale) {
            trackAsync("scaling next cel to match background cel");
            cel.upscale(scale0x * 100.0);
            endTrackAsync("scaling next cel to match background cel");
        } else {
            throw new IllegalArgumentException(
                    "Could not match cel to reference dimensions using uniform scaling and 90-degree rotation: "
                            + celFile.getName()
            );
        }

        return new ImageHandler(cel.getImage());
    }

    protected Rectangle cropBoxSelection(ImageHandler image, String frameName, SelectionTransform transform) {
        TaskTracker.pause(); noExcept(image::loadImage);
        return noExcept(() -> new FreeformSelectionManager(image.getImage(), frameName, transform, false).getCropBox());
    }
    protected Rectangle cropBoxSelection(ImageHandler image, String frameName) {
        return cropBoxSelection(image, frameName, SelectionTransform.empty());
    }
    protected Rectangle cropBoxSelection(ImageHandler image, SelectionTransform transform) {
        TaskTracker.pause(); noExcept(image::loadImage);
        return noExcept(() -> new FreeformSelectionManager(image.getImage(), transform, false).getCropBox());
    }
    protected Rectangle cropBoxSelection(ImageHandler image) {
        return cropBoxSelection(image, SelectionTransform.empty());
    }
    protected Rectangle cropBoxArg(ImageHandler image, String argName, SelectionTransform transform) {
        return Optional.ofNullable(args.get(argName)).map(Main::parseCropboxData).orElseGet(() -> cropBoxSelection(image, transform));
    }
    protected Rectangle cropBoxArg(ImageHandler image, String argName) {
        return cropBoxArg(image, argName, SelectionTransform.empty());
    }
    protected Rectangle cropBoxArg(ImageHandler image, SelectionTransform transform) {
        return cropBoxArg(image, "cropbox", transform);
    }
    protected Rectangle cropBoxArg(ImageHandler image) {
        return cropBoxArg(image, "cropbox");
    }

    protected Point pointSelection(ImageHandler image, SelectionTransform transform) {
        TaskTracker.pause(); noExcept(image::loadImage);
        return noExcept(() -> new FreeformSelectionManager(image.getImage(), "Point selector", transform, FreeformSelectionWindow.SelectionMode.POINT).getSelectedPoint());
    }
    protected Point pointSelection(ImageHandler image) {
        return pointSelection(image, SelectionTransform.empty());
    }
    protected Point pointArg(ImageHandler image, String argName, SelectionTransform transform) {
        return Optional.ofNullable(args.get(argName)).map(Main::parsePointData).orElseGet(() -> pointSelection(image, transform));
    }
    protected Point pointArg(ImageHandler image, String argName) {
        return pointArg(image, argName, SelectionTransform.empty());
    }
    protected Point pointArg(ImageHandler image, SelectionTransform transform) {
        return pointArg(image, "point", transform);
    }
    protected Point pointArg(ImageHandler image) {
        return pointArg(image, "point");
    }

    protected List<Point> multiPointSelection(ImageHandler image, SelectionTransform transform) {
        TaskTracker.pause(); noExcept(image::loadImage);
        return noExcept(() -> new FreeformSelectionManager(image.getImage(), "Point selector", transform, FreeformSelectionWindow.SelectionMode.MULTI_POINT).getSelectedPoints());
    }
    protected List<Point> multiPointSelection(ImageHandler image) {
        return multiPointSelection(image, SelectionTransform.empty());
    }
    protected Map<Point, String> labeledPointSelection(ImageHandler image, SelectionTransform transform, List<String> labels) {
        TaskTracker.pause(); noExcept(image::loadImage);
        return noExcept(() -> new FreeformSelectionManager(image.getImage(), "Labeled point selector", transform, FreeformSelectionWindow.SelectionMode.LABELED_MULTI_POINT, labels).getLabeledSelectedPoints());
    }
    protected Map<Point, String> labeledPointSelection(ImageHandler image, List<String> labels) {
        return labeledPointSelection(image, SelectionTransform.empty(), labels);
    }

    protected TreeMap<Double, List<Double>> freeformSelection(ImageHandler image, SelectionTransform transform) {
        return freeformSelection(image, "Cropper", transform);
    }
    protected TreeMap<Double, List<Double>> freeformSelection(
            ImageHandler image,
            String frameName,
            SelectionTransform transform
    ) {
        TaskTracker.pause(); noExcept(image::loadImage);
        return noExcept(() -> new TreeMap<>(
                new FreeformSelectionManager(image.getImage(), frameName, transform, true).getDrawingAxis()
        ));
    }
    protected TreeMap<Double, List<Double>> freeformSelection(ImageHandler image) {
        return freeformSelection(image, SelectionTransform.empty());
    }
    protected TreeMap<Integer, List<Integer>> freeformArg(ImageHandler image, String argName, SelectionTransform transform) {
        return freeformArg(image, argName, transform, "Cropper");
    }
    protected TreeMap<Integer, List<Integer>> freeformArg(
            ImageHandler image,
            String argName,
            SelectionTransform transform,
            String frameName
    ) {
        return interpolateMissingCoordsInt(Optional.of(Optional.ofNullable(args.get(argName)).or(() -> Optional.ofNullable(args.get(argName + "-file")).map(p -> noExcept(() -> Files.readString(Paths.get(p), StandardCharsets.UTF_8)))))
                .flatMap(o -> o)
                .map(Main::parseFreeformData)
                .orElseGet(() -> freeformSelection(image, frameName, transform)), 1);
    }
    protected TreeMap<Integer, List<Integer>> freeformArg(ImageHandler image, String argName) {
        return freeformArg(image, argName, SelectionTransform.empty());
    }
    protected TreeMap<Integer, List<Integer>> objectBorderArg(ImageHandler image, SelectionTransform transform) {
        return freeformArg(image, "object-border", transform);
    }
    protected TreeMap<Integer, List<Integer>> objectBorderArg(
            ImageHandler image,
            SelectionTransform transform,
            String frameName
    ) {
        return freeformArg(image, "object-border", transform, frameName);
    }
    protected TreeMap<Integer, List<Integer>> objectBorderArg(ImageHandler image) {
        return freeformArg(image, "object-border");
    }
    protected TreeMap<Integer, List<Integer>> objectBorderArg(ImageHandler image, String frameName) {
        return freeformArg(
                image,
                "object-border",
                SelectionTransform.empty(),
                frameName
        );
    }

    public static double[] colorFromString(String str) {
        return Arrays.stream(str.replaceAll("[{}]", "").split(","))
                .map(String::trim)
                .mapToDouble(Double::parseDouble)
                .toArray();
    }
    protected double[] colorArg(String argName) {
        return Optional.ofNullable(args.get(argName)).map(Main::colorFromString).orElse(null);
    }
    protected double[] colorArg(String argName, double[] def) {
        return Optional.ofNullable(args.get(argName)).map(Main::colorFromString).orElse(def);
    }
    protected double[] colorArg(String argName, int one, int two, int three) {
        return colorArg(argName, new double[] { one, two, three });
    }

    public static Map<Color, Color> colorMapFromString(String str) {
        Map<Color, Color> colorMap = new LinkedHashMap<>();
        if (str == null || str.isBlank()) return colorMap;

        for (String association : str.split(";")) {
            String[] colors = association.split("=", -1);
            if (colors.length != 2) {
                throw new IllegalArgumentException("Invalid color association: " + association);
            }
            colorMap.put(colorFromComponents(colors[0]), colorFromComponents(colors[1]));
        }
        return colorMap;
    }

    public static String colorMapToString(Map<Color, Color> colorMap) {
        return colorMap.entrySet().stream()
                .map(entry -> colorToString(entry.getKey()) + "=" + colorToString(entry.getValue()))
                .collect(joining(";"));
    }

    private static Color colorFromComponents(String value) {
        double[] components = colorFromString(value);
        if (components.length != 3) {
            throw new IllegalArgumentException("A color-map color must have exactly three RGB components: " + value);
        }
        int red = (int) components[0];
        int green = (int) components[1];
        int blue = (int) components[2];
        if (red != components[0] || green != components[1] || blue != components[2]) {
            throw new IllegalArgumentException("A color-map color must use integer RGB components: " + value);
        }
        return new Color(red, green, blue);
    }

    private static String colorToString(Color color) {
        return "{" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "}";
    }

    protected Map<Color, Color> colorMapArg(String argName) {
        return Optional.ofNullable(args.get(argName))
                .map(Main::colorMapFromString)
                .orElseGet(LinkedHashMap::new);
    }

    public static Map<String, String> mapFromString(String str) {
        Map<String, String> map = new LinkedHashMap<>();
        if (str == null || str.isBlank()) return map;
        Base64.Decoder decoder = Base64.getUrlDecoder();
        for (String association : str.split(";")) {
            String[] values = association.split(":", -1);
            if (values.length != 2) throw new IllegalArgumentException("Invalid map association");
            map.put(
                    new String(decoder.decode(values[0]), StandardCharsets.UTF_8),
                    new String(decoder.decode(values[1]), StandardCharsets.UTF_8)
            );
        }
        return map;
    }

    public static String mapToString(Map<String, String> map) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return map.entrySet().stream().map(entry ->
                encoder.encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8)) + ":" +
                        encoder.encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8))
        ).collect(joining(";"));
    }

    protected Map<String, String> mapArg(String argName) {
        return Optional.ofNullable(args.get(argName))
                .map(Main::mapFromString)
                .orElseGet(LinkedHashMap::new);
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

    protected static Point parsePointData(String pointArg) {
        String[] pointData = pointArg.replaceAll("[\\[\\]]", "").split(",");
        Map<String, Integer> parsedPointFields = new HashMap<>();
        for (String rawPointField : pointData) {
            parsedPointFields.put(rawPointField.split("=")[0], Integer.parseInt(rawPointField.split("=")[1]));
        }
        return new Point(parsedPointFields.get("x"), parsedPointFields.get("y"));
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


    protected static Thread outputCelBackground(FastRGB pixelsHandler, String outputCelPath) throws IOException {
        trackAsync("outputting final result to " + outputCelPath);
        return Main.async(asNoExcept(() -> {
            acquireOutputThreadSlot();
            try {
                File outputCelFile = new File(outputCelPath);
                outputCelFile.createNewFile();
                ImageIO.write(pixelsHandler.getImage(), imageFormatForPath(outputCelPath), outputCelFile);
            /*ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.0f);
            }

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputCelFile)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(pixelsHandler.getImage(), null, null), param);
            } finally {
                writer.dispose();
            }*/
                endTrackAsync("outputting final result to " + outputCelPath);
            } finally {
                releaseOutputThreadSlot();
            }
        }));
//        System.exit(0);
    }

    public static int getMaximumOutputThreads() {
        return Math.max(1, prefs.getInt(
                MAXIMUM_OUTPUT_THREADS_PREFERENCE,
                DEFAULT_MAXIMUM_OUTPUT_THREADS
        ));
    }

    public static void setMaximumOutputThreads(int maximumOutputThreads) {
        if (maximumOutputThreads < 1) {
            throw new IllegalArgumentException("maximumOutputThreads must be at least 1");
        }
        prefs.putInt(MAXIMUM_OUTPUT_THREADS_PREFERENCE, maximumOutputThreads);
        noExcept(prefs::flush);
        synchronized (OUTPUT_THREAD_MONITOR) {
            OUTPUT_THREAD_MONITOR.notifyAll();
        }
    }


    public static boolean previewShapeData() {
        return prefs.getBoolean(PREVIEW_SHAPE_DATA_PREFERENCE, false);
    }

    public static void setPreviewShapeData(boolean previewShapeData) {
        prefs.putBoolean(PREVIEW_SHAPE_DATA_PREFERENCE, previewShapeData);
        noExcept(prefs::flush);
    }

    public static synchronized File getCurrentInkQualifierFile() {
        if (DEFAULT_INK_QUALIFIER_NAME.equals(selectedInkQualifierName)) {
            return null;
        }
        return new File(inkQualifierCacheDirectory(), selectedInkQualifierName);
    }

    private static void acquireOutputThreadSlot() throws InterruptedException {
        synchronized (OUTPUT_THREAD_MONITOR) {
            while (activeOutputThreads >= getMaximumOutputThreads()) {
                OUTPUT_THREAD_MONITOR.wait();
            }
            activeOutputThreads++;
        }
    }

    private static void releaseOutputThreadSlot() {
        synchronized (OUTPUT_THREAD_MONITOR) {
            activeOutputThreads--;
            OUTPUT_THREAD_MONITOR.notifyAll();
        }
    }

    protected static boolean clearIntArrayResources(int length) {
        if (ConstructionEx.getTotalArrayCount(int.class, length) == 0) {
            ConstructionEx.forceReleaseAllArrays();
            return true;
        } else if (ConstructionEx.getAvailableArrayCount(int.class, length) == 0) {
            ConstructionEx.forceReleaseAllArrays(int.class);
            return true;
        }
        return false;
    }

    protected static CompletableFuture<Void> preAllocateIntArrayResourcesAsync(int length, int amount) {
        if (amount < 1) throw new IllegalArgumentException("amount must be at least 1");
        return CompletableFuture.runAsync(() -> {
            clearIntArrayResources(length);
            int available = ConstructionEx.getAvailableArrayCount(int.class, length);
            if (available < amount) {
                ConstructionEx.preAllocateIntArrays(length, amount - available);
            }
        });
    }

    protected static void outputCel(FastRGB pixelsHandler, String outputCelPath) throws IOException {
        track("outputting final result to " + outputCelPath);
        File outputCelFile = new File(outputCelPath);
        outputCelFile.createNewFile();
        ImageIO.write(pixelsHandler.getImage(), imageFormatForPath(outputCelPath), outputCelFile);
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

    protected static void outputCel(String chooserScopeKey, FastRGB pixelsHandler) throws IOException {
        JFileChooser fileChooser = chooserFor(chooserScopeKey);
        fileChooser.setDialogTitle("Save PNG");
        fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));

        if (fileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File outputCelFile = fileChooser.getSelectedFile();
        rememberChooserDirectory(chooserScopeKey, outputCelFile);

        if (!outputCelFile.getName().toLowerCase().endsWith(".png")) {
            outputCelFile = new File(outputCelFile.getAbsolutePath() + ".png");
        }

        outputCel(pixelsHandler, outputCelFile.getAbsolutePath());
    }

    protected static void outputCel(String chooserScopeKey, FastRGB pixelsHandler, Map<String, String> textTags, String ext) throws IOException {
        JFileChooser fileChooser = chooserFor(chooserScopeKey);
        fileChooser.setDialogTitle("Save " + ext.toUpperCase());
        fileChooser.setFileFilter(new FileNameExtensionFilter(ext.toUpperCase() + " Images", ext));

        if (fileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File outputCelFile = fileChooser.getSelectedFile();
        rememberChooserDirectory(chooserScopeKey, outputCelFile);

        if (!outputCelFile.getName().toLowerCase().endsWith("." + ext)) {
            outputCelFile = new File(outputCelFile.getAbsolutePath() + "." + ext);
        }

        outputCel(pixelsHandler, outputCelFile.getAbsolutePath(), textTags);
    }

    protected static void outputCel(String chooserScopeKey, ImageHandler pixelsHandler, String ext) throws IOException {
        outputCel(chooserScopeKey, new FastRGB(pixelsHandler), Map.of(), ext);
    }
    protected static void outputCel(String chooserScopeKey, ImageHandler pixelsHandler, String ext, Map<String, String> textTags) throws IOException {
        outputCel(chooserScopeKey, new FastRGB(pixelsHandler), textTags, ext);
    }
    protected static void outputCel(String chooserScopeKey, ImageHandler pixelsHandler) throws IOException {
        outputCel(chooserScopeKey, pixelsHandler, "png");
    }


    public static void printImage(String name, BufferedImage image) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(name);
        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D g2d = (Graphics2D) graphics;

            double printableX = pageFormat.getImageableX();
            double printableY = pageFormat.getImageableY();
            double printableW = pageFormat.getImageableWidth();
            double printableH = pageFormat.getImageableHeight();

            double scale = Math.min(printableW / image.getWidth(), printableH / image.getHeight());

            int drawW = (int) Math.round(image.getWidth() * scale);
            int drawH = (int) Math.round(image.getHeight() * scale);

            int drawX = (int) Math.round(printableX + ((printableW - drawW) / 2.0));
            int drawY = (int) Math.round(printableY + ((printableH - drawH) / 2.0));

            g2d.drawImage(image, drawX, drawY, drawW, drawH, null);
            return Printable.PAGE_EXISTS;
        });

        if (job.printDialog()) {
            job.print();
        }
    }
    public static void printImage(String name, ImageHandler imageHandler) throws PrinterException {
        printImage(name, imageHandler.getImage());
    }
    public static void printImage(String name, FastRGB fastRGB) throws PrinterException {
        printImage(name, fastRGB.getImage());
    }
    public static void printImage(BufferedImage image) throws PrinterException {
        printImage("CelTools Print", image);
    }
    public static void printImage(ImageHandler imageHandler) throws PrinterException {
        printImage(imageHandler.getImage());
    }
    public static void printImage(FastRGB fastRGB) throws PrinterException {
        printImage(fastRGB.getImage());
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
    public static void replacePixels(FastRGB srcPixels, FastRGB destPixels, ShapeBounds bounds) {
        bounds.forEachInner((x, y) -> {
            int rgb = srcPixels.getRGBRaw(x, y);
            if (destPixels.getRGBRaw(x, y) != rgb) {
                destPixels.setRGB(x, y, rgb);
            }
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
    public static void replacePixels(FastRGB srcPixels, FastRGB destPixels, PointCollection ptColl) {
        ptColl.forEach(pt -> destPixels.setRGB(pt.x, pt.y, srcPixels.getRGBRaw(pt.x, pt.y)));
    }


    public static PointCollection filterPixelsToPtCollection(int[] pixels, int width, int height, HSVBounds hsvBounds) {
        return varMutate(new PointCollection(), (ptColl) -> FrameParser.filterHSV(pixels.clone(), width, height, hsvBounds, (col, row) -> ptColl.add(new Point(col, row))));
    }
    public static PointCollection filterPixelsToPtCollection(ImageHandler image, HSVBounds hsvBounds) {
        return filterPixelsToPtCollection(image.getImgPixels(), image.getWidth(), image.getHeight(), hsvBounds);
    }
    public static PointCollection filterPixelsToPtCollection(FastRGB image, HSVBounds hsvBounds) {
        return filterPixelsToPtCollection(image.getPixels(), image.getWidth(), image.getHeight(), hsvBounds);
    }
    public static PointCollection filterPixelsToPtCollection(int[] pixels, int width, int height, double[] low, double[] high) {
        return filterPixelsToPtCollection(pixels, width, height, new HSVBounds(low, high));
    }
    public static PointCollection filterPixelsToPtCollection(ImageHandler image, double[] low, double[] high) {
        return filterPixelsToPtCollection(image, new HSVBounds(low, high));
    }
    public static PointCollection filterPixelsToPtCollection(FastRGB image, double[] low, double[] high) {
        return filterPixelsToPtCollection(image, new HSVBounds(low, high));
    }
    public static PointCollection filterPixelsToPtCollection(BufferedImage image, HSVBounds hsvBounds) {
        return filterPixelsToPtCollection(new ImageHandler(image), hsvBounds);
    }
    public static PointCollection filterPixelsToPtCollection(BufferedImage image, double[] low, double[] high) {
        return filterPixelsToPtCollection(image, new HSVBounds(low, high));
    }
    public static PointCollection filterPixelsToPtCollection(int[] pixels, int width, int height, Predicate<double[]> hsvQualifier) {
        return varMutate(new PointCollection(), ptColl -> FrameParser.filterHSV(pixels, width, height, hsvQualifier, (col, row) -> ptColl.add(new Point(col, row))));
    }
    public static PointCollection filterPixelsToPtCollection(ImageHandler image, Predicate<double[]> hsvQualifier) {
        return filterPixelsToPtCollection(image.getImgPixels(), image.getWidth(), image.getHeight(), hsvQualifier);
    }
    public static PointCollection filterPixelsToPtCollection(FastRGB image, Predicate<double[]> hsvQualifier) {
        return filterPixelsToPtCollection(image.getPixels(), image.getWidth(), image.getHeight(), hsvQualifier);
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

    private static int nextLabel = 1;
    public static List<PointCollection> filterPixelsToGroups(
            int[] pixels,
            int width,
            int height,
            double dist,
            Predicate<double[]> hsvQualifier
    ) {
        Objects.requireNonNull(pixels, "pixels");
        Objects.requireNonNull(hsvQualifier, "hsvQualifier");

        if (width <= 0 || height <= 0 || pixels.length < (width * height) || Double.isNaN(dist) || dist < 0.0) {
            return new ArrayList<>();
        }

        double distSq = dist * dist;
        int radius = MathEx.ceilInt(dist);

        nextLabel = 1;
        int[] labels = ConstructionEx.getIntArray(width * height);
        int[] parents = new int[labels.length + 1];

        int maxOffsetCount = ((radius * 2) + 1) * ((radius * 2) + 1) - 1;
        int[] offsetXs = new int[maxOffsetCount];
        int[] offsetYs = new int[maxOffsetCount];
        int offsetCount = 0;

        for (int dy = -radius; dy <= 0; dy++) {
            int dySq = dy * dy;
            for (int dx = -radius; dx <= radius; dx++) {
                if (dy == 0 && dx >= 0) continue;
                if ((double) ((dx * dx) + dySq) <= distSq) {
                    offsetXs[offsetCount] = dx;
                    offsetYs[offsetCount] = dy;
                    offsetCount++;
                }
            }
        }

        int finalOffsetCount = offsetCount;
        FrameParser.filterHSV(pixels, width, height, hsvQualifier, (hPixels, x, y, idx, rawRGB, rgb, colLoop, rowLoop) -> {            int label = 0;

            for (int i = 0; i < finalOffsetCount; i++) {
                int nx = x + offsetXs[i];
                int ny = y + offsetYs[i];

                if ((nx | ny) < 0 || nx >= width || ny >= height) {
                    continue;
                }

                int neighborLabel = labels[(ny * width) + nx];
                if (neighborLabel == 0) {
                    continue;
                }

                int root = filterPixelsToGroupsFind(parents, neighborLabel);
                if (label == 0) {
                    label = root;
                } else {
                    label = filterPixelsToGroupsUnion(parents, label, root);
                }
            }

            if (label == 0) {
                label = nextLabel;
                parents[nextLabel] = nextLabel;
                nextLabel++;
            }

            labels[idx] = label;
        });
        Map<Integer, PointCollection> grouped = new LinkedHashMap<>();

        for (int idx = 0; idx < labels.length; idx++) {
            int label = labels[idx];
            if (label == 0) {
                continue;
            }

            int root = filterPixelsToGroupsFind(parents, label);
            grouped.computeIfAbsent(root, k -> new PointCollection())
                    .add(new Point(idx % width, idx / width));
        }

        return new ArrayList<>(grouped.values());
    }
/*
    @SafeVarargs
    public static List<List<PointCollection>> filterPixelsToGroups(
            int[] pixels,
            int width,
            int height,
            double dist,
            Predicate<double[]>... hsvQualifiers
    ) {
        Objects.requireNonNull(pixels, "pixels");
        Objects.requireNonNull(hsvQualifiers, "hsvQualifiers");
        Arrays.stream(hsvQualifiers).forEach(qualifier -> Objects.requireNonNull(qualifier, "hsvQualifier"));

        List<List<PointCollection>> result = new ArrayList<>(hsvQualifiers.length);
        if (hsvQualifiers.length == 0) return result;
        if (width <= 0 || height <= 0 || pixels.length < (width * height) || Double.isNaN(dist) || dist < 0.0) {
            IntStream.range(0, hsvQualifiers.length).forEach(i -> result.add(new ArrayList<>()));
            return result;
        }

        double distSq = dist * dist;
        int radius = MathEx.ceilInt(dist);
        int maxOffsetCount = ((radius * 2) + 1) * ((radius * 2) + 1) - 1;
        int[] offsetXs = new int[maxOffsetCount];
        int[] offsetYs = new int[maxOffsetCount];
        int offsetCount = 0;

        for (int dy = -radius; dy <= 0; dy++) {
            int dySq = dy * dy;
            for (int dx = -radius; dx <= radius; dx++) {
                if (dy == 0 && dx >= 0) continue;
                if ((double) ((dx * dx) + dySq) <= distSq) {
                    offsetXs[offsetCount] = dx;
                    offsetYs[offsetCount] = dy;
                    offsetCount++;
                }
            }
        }

        int pixelCount = width * height;
        int[][] labels = new int[hsvQualifiers.length][pixelCount];
        int[][] parents = new int[hsvQualifiers.length][pixelCount + 1];
        int[] nextLabels = new int[hsvQualifiers.length];
        Arrays.fill(nextLabels, 1);
        int finalOffsetCount = offsetCount;
        double[] hsv = new double[3];

        new PixelIterator(pixels, width, height, true).execute((hPixels, x, y, idx, rawRGB, rgb, colLoop, rowLoop) -> {
            FrameParser.RGBtoHSV(rawRGB, hsv);
            for (int qualifierIndex = 0; qualifierIndex < hsvQualifiers.length; qualifierIndex++) {
                if (!hsvQualifiers[qualifierIndex].test(hsv)) continue;

                int label = 0;
                int[] qualifierLabels = labels[qualifierIndex];
                int[] qualifierParents = parents[qualifierIndex];
                for (int offsetIndex = 0; offsetIndex < finalOffsetCount; offsetIndex++) {
                    int neighborX = x + offsetXs[offsetIndex];
                    int neighborY = y + offsetYs[offsetIndex];
                    if ((neighborX | neighborY) < 0 || neighborX >= width || neighborY >= height) continue;

                    int neighborLabel = qualifierLabels[(neighborY * width) + neighborX];
                    if (neighborLabel == 0) continue;

                    int root = filterPixelsToGroupsFind(qualifierParents, neighborLabel);
                    label = label == 0 ? root : filterPixelsToGroupsUnion(qualifierParents, label, root);
                }

                if (label == 0) {
                    label = nextLabels[qualifierIndex]++;
                    qualifierParents[label] = label;
                }
                qualifierLabels[idx] = label;
            }
        });

        for (int qualifierIndex = 0; qualifierIndex < hsvQualifiers.length; qualifierIndex++) {
            Map<Integer, PointCollection> grouped = new LinkedHashMap<>();
            int[] qualifierLabels = labels[qualifierIndex];
            int[] qualifierParents = parents[qualifierIndex];
            for (int idx = 0; idx < pixelCount; idx++) {
                int label = qualifierLabels[idx];
                if (label == 0) continue;
                int root = filterPixelsToGroupsFind(qualifierParents, label);
                grouped.computeIfAbsent(root, key -> new PointCollection())
                        .add(new Point(idx % width, idx / width));
            }
            result.add(new ArrayList<>(grouped.values()));
        }
        return result;
    }
*/

    @SafeVarargs
    public static List<List<PointCollection>> filterPixelsToGroups(
            int[] pixels,
            int width,
            int height,
            double dist,
            Predicate<double[]>... hsvQualifiers
    ) {
        Objects.requireNonNull(pixels, "pixels");
        Objects.requireNonNull(hsvQualifiers, "hsvQualifiers");
        for (Predicate<double[]> qualifier : hsvQualifiers) {
            Objects.requireNonNull(qualifier, "hsvQualifier");
        }

        List<List<PointCollection>> result = new ArrayList<>(hsvQualifiers.length);
        for (int i = 0; i < hsvQualifiers.length; i++) {
            result.add(new ArrayList<>());
        }

        if (hsvQualifiers.length == 0) return result;
        if (width <= 0 || height <= 0 || pixels.length < width * height || Double.isNaN(dist) || dist < 0.0) {
            return result;
        }

        final int pixelCount = width * height;

        /*
         * Build full neighbor offsets for flood fill.
         * dist = 1.0 gives 4-connected grouping.
         * dist >= sqrt(2) gives 8-connected grouping.
         */
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

                if ((double) ((dx * dx) + dySq) <= distSq) {
                    offsetXs[offsetCount] = dx;
                    offsetYs[offsetCount] = dy;
                    offsetCount++;
                }
            }
        }

        /*
         * First pass: determine which pixels match each qualifier.
         * This is deliberately single-threaded because the predicates receive
         * a reusable mutable hsv buffer.
         */
        BitSet[] matches = new BitSet[hsvQualifiers.length];
        for (int i = 0; i < hsvQualifiers.length; i++) {
            matches[i] = new BitSet(pixelCount);
        }

        FrameParser.filterHSV(pixels, width, height, hsvQualifiers, (hPixels, x, y, idx, i, rgb, colLoop, rowLoop) ->
                matches[i].set(idx)
        );

        /*
         * Second pass: flood-fill connected groups for each qualifier.
         */
        int[] queue = ConstructionEx.getIntArray(pixelCount);

        for (int qualifierIndex = 0; qualifierIndex < hsvQualifiers.length; qualifierIndex++) {
            BitSet remaining = matches[qualifierIndex];

            for (int start = remaining.nextSetBit(0);
                 start >= 0;
                 start = remaining.nextSetBit(start + 1)) {

                PointCollection group = new PointCollection();

                int head = 0;
                int tail = 0;

                queue[tail++] = start;
                remaining.clear(start);

                while (head < tail) {
                    int idx = queue[head++];

                    int x = idx % width;
                    int y = idx / width;

                    group.add(new Point(x, y));

                    for (int offsetIndex = 0; offsetIndex < offsetCount; offsetIndex++) {
                        int nx = x + offsetXs[offsetIndex];
                        int ny = y + offsetYs[offsetIndex];

                        if ((nx | ny) < 0 || nx >= width || ny >= height) {
                            continue;
                        }

                        int nIdx = (ny * width) + nx;

                        if (!remaining.get(nIdx)) {
                            continue;
                        }

                        remaining.clear(nIdx);
                        queue[tail++] = nIdx;
                    }
                }

                result.get(qualifierIndex).add(group);
            }
        }

        return result;
    }
    public static List<PointCollection> filterPixelsToGroups(FastRGB cel, double dist, Predicate<double[]> hsvQualifier) {
        return filterPixelsToGroups(cel.getPixels(), cel.getWidth(), cel.getHeight(), dist, hsvQualifier);
    }

    public static List<PointCollection> filterPixelsToGroups(
            ImageHandler image,
            double dist,
            Predicate<double[]> hsvQualifier
    ) {
        return filterPixelsToGroups(image.getImgPixels(), image.getWidth(), image.getHeight(), dist, hsvQualifier);
    }

    @SafeVarargs
    public static List<List<PointCollection>> filterPixelsToGroups(
            ImageHandler image,
            double dist,
            Predicate<double[]>... hsvQualifiers
    ) {
        return filterPixelsToGroups(image.getImgPixels(), image.getWidth(), image.getHeight(), dist, hsvQualifiers);
    }

    public static List<PointCollection> filterPixelsToGroups(FastRGB image, double dist, double[] min, double[] max) {
        return filterPixelsToGroups(image, dist, hsv ->
                hsv[0] >= min[0] && hsv[0] <= max[0] &&
                        hsv[1] >= min[1] && hsv[1] <= max[1] &&
                        hsv[2] >= min[2] && hsv[2] <= max[2]);
    }

    protected static Point findLargestMiniGroupAnchor(
            ImageHandler image,
            double dist,
            Predicate<double[]> hsvQualifier
    ) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(hsvQualifier, "hsvQualifier");

        image.loadMiniImage();
        ImageHandler miniImage = new ImageHandler(image.getMiniImage());
        double miniDist = Math.max(1.0, dist * image.getMiniImageScale());
        PointCollection largestMiniGroup = filterPixelsToGroups(miniImage, miniDist, hsvQualifier)
                .stream()
                .max(Comparator.comparingInt(PointCollection::size))
                .orElseThrow(() -> new IllegalArgumentException("Image contains no pixels matching the qualifier"));
        return image.miniImagePointToImage(largestMiniGroup.centroid());
    }

    @SafeVarargs
    protected static List<Point> findLargestMiniGroupAnchors(
            ImageHandler image,
            double dist,
            boolean acceptEmpty,
            Predicate<double[]>... hsvQualifiers
    ) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(hsvQualifiers, "hsvQualifiers");
        Arrays.stream(hsvQualifiers).forEach(qualifier ->
                Objects.requireNonNull(qualifier, "hsvQualifier"));

        image.loadMiniImage();
        ImageHandler miniImage = new ImageHandler(image.getMiniImage());
        double miniDist = Math.max(1.0, dist * image.getMiniImageScale());
        List<List<PointCollection>> miniGroups =
                filterPixelsToGroups(miniImage, miniDist, hsvQualifiers);

        List<Point> anchors = new ArrayList<>(miniGroups.size());

        for (int qualifierIndex = 0; qualifierIndex < miniGroups.size(); qualifierIndex++) {
            List<PointCollection> groups = miniGroups.get(qualifierIndex);

            PointCollection largestMiniGroup = groups.stream()
                    .max(Comparator.comparingInt(PointCollection::size))
                    .orElse(null);

            if (largestMiniGroup == null || largestMiniGroup.isEmpty()) {
                if (acceptEmpty) continue;

                throw new IllegalArgumentException(
                        "Image contains no pixels matching qualifier " + qualifierIndex
                );
            }

            anchors.add(
                    image.miniImagePointToImage(
                            largestMiniGroup.centroid()
                    )
            );
        }

        return anchors;
    }
    @SafeVarargs
    protected static List<Point> findLargestMiniGroupAnchors(
            ImageHandler image,
            double dist,
            Predicate<double[]>... hsvQualifiers
    ) {
        return findLargestMiniGroupAnchors(
                image,
                dist,
                false,
                hsvQualifiers
        );
    }

    protected static PointCollection filterLargestMiniGroupToLocalGroup(
            ImageHandler image,
            double dist,
            Predicate<double[]> hsvQualifier
    ) {
        Point fullResolutionAnchor = findLargestMiniGroupAnchor(image, dist, hsvQualifier);
        return filterPixelsToLocalGroup(image, fullResolutionAnchor, dist, hsvQualifier);
    }

    private static int filterPixelsToGroupsFind(int[] parents, int label) {
        int root = label;
        while (parents[root] != root) {
            root = parents[root];
        }

        while (parents[label] != label) {
            int next = parents[label];
            parents[label] = root;
            label = next;
        }

        return root;
    }

    private static int filterPixelsToGroupsUnion(int[] parents, int a, int b) {
        int rootA = filterPixelsToGroupsFind(parents, a);
        int rootB = filterPixelsToGroupsFind(parents, b);

        if (rootA == rootB) {
            return rootA;
        }

        if (rootA < rootB) {
            parents[rootB] = rootA;
            return rootA;
        }

        parents[rootA] = rootB;
        return rootB;
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

        byte[] state = ConstructionEx.getByteArray(pixelCount);
        int[] queue = ConstructionEx.getIntArray(pixelCount);
//        byte[] state = new byte[pixelCount];
//        int[] queue = new int[pixelCount];
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

        double[] outHSV = new double[3];
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

                if (hsvQualifier.test(image.getHSV(nx, ny, outHSV))) {
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


        ConstructionEx.giveBackArray(state);
        ConstructionEx.giveBackArray(queue);
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
        return filterPixelsToLocalGroup(new FastRGB(image, false), seed, dist, hsvQualifier);
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


    public static int getOutlineWidth(PointCollection outline, ShapeContour outerContour) {
        if (outline == null || outerContour == null || outline.isEmpty() || outerContour.isEmpty()) {
            return 0;
        }

        PointCollection hole = outline.hole();
        if (hole == null || hole.isEmpty()) {
            return 0;
        }

        ShapeContour innerContour = new ShapeContour(hole);
        if (innerContour.isEmpty()) {
            return 0;
        }

        Point2D.Double outerCenter = outerContour.centroid();
        Point2D.Double innerCenter = innerContour.centroid();

        double cx = (outerCenter.x + innerCenter.x) * 0.5d;
        double cy = (outerCenter.y + innerCenter.y) * 0.5d;

        int samples = Math.max(32, Math.min(256, Math.max(outerContour.size(), innerContour.size()) / 4));
        double minThickness = Double.POSITIVE_INFINITY;

        for (int i = 0; i < samples; i++) {
            double angleFromTop = (Math.PI * 2.0d * i) / samples;

            double outerRadius = radialDistanceAtAngle(outerContour, cx, cy, angleFromTop);
            double innerRadius = radialDistanceAtAngle(innerContour, cx, cy, angleFromTop);

            if (!Double.isFinite(outerRadius) || !Double.isFinite(innerRadius)) {
                continue;
            }

            double thickness = Math.abs(outerRadius - innerRadius);
            if (thickness < minThickness) {
                minThickness = thickness;
            }
        }

        if (!Double.isFinite(minThickness)) {
            return 0;
        }

        return Math.max(1, MathEx.roundInt(minThickness));
    }

    private static double radialDistanceAtAngle(ShapeContour contour, double cx, double cy, double angleFromTop) {
        double raw = angleFromTop + (Math.PI / 2.0d);
        double cos = MathEx.cos(raw);
        double sin = MathEx.sin(raw);

        double bestProjection = Double.POSITIVE_INFINITY;

        for (int i = 0; i < contour.size(); i++) {
            java.awt.Point p = contour.get(i);

            double dx = p.x - cx;
            double dy = p.y - cy;

            double projection = (dx * cos) + (dy * sin);
            if (projection <= 0.0d) {
                continue;
            }

            double perp = Math.abs((-sin * dx) + (cos * dy));
            if (perp > 1.25d) {
                continue;
            }

            if (projection < bestProjection) {
                bestProjection = projection;
            }
        }

        return bestProjection;
    }





    public static PointCollection smooth(PointCollection outline,ShapeContour contour) {return smooth(outline, contour, false);}

    public static PointCollection smooth(
            PointCollection outline,
            ShapeContour contour,
            boolean debug
    ) {
        Objects.requireNonNull(outline, "outline");
        Objects.requireNonNull(contour, "contour");

        if (outline.isEmpty() && contour.isEmpty()) {
            return new PointCollection();
        }

        int estimatedThickness = 1;

        if (!outline.isEmpty()
                && !contour.isEmpty()
                && contour.perimeter() > 0.0) {

            estimatedThickness = Math.max(
                    1,
                    (int) Math.round(
                            (double) outline.size()
                                    / contour.perimeter()
                    )
            );
        }

        PointCollection stroke = fillLocalMissingColumns(
                outline,
                contour,
                estimatedThickness,
                debug
        );

        int radius = Math.max(1, estimatedThickness / 2);

        PointCollection closed = stroke
                .expand(radius)
                .contract(radius);

        if (closed.isEmpty()) {
            return stroke;
        }

        PointCollection opened = closed
                .contract(radius)
                .expand(radius);

        PointCollection result = opened.isEmpty()
                ? closed
                : opened;

        int maximumDefectArea = Math.max(
                16,
                estimatedThickness * estimatedThickness * 4
        );

        PointCollection finished = fillSmallEnclosedHoles(
                result,
                maximumDefectArea
        );

        if (debug) {
            System.out.println(
                    "[smooth] Morphology:"
                            + " radius=" + radius
                            + ", before=" + stroke.size()
                            + ", closed=" + closed.size()
                            + ", opened=" + opened.size()
                            + ", finished=" + finished.size()
            );
        }

        return finished;
    }

    private static PointCollection fillLocalMissingColumns(
            PointCollection source,
            ShapeContour contour,
            int thickness,
            boolean debug
    ) {
        PointCollection repaired = new PointCollection(source);

        if (contour.isEmpty() || contour.size() < 2 || thickness <= 0) {
            if (debug) {
                System.out.println(
                        "[smooth] Column repair skipped:"
                                + " sourceSize=" + source.size()
                                + ", contourSize=" + contour.size()
                                + ", thickness=" + thickness
                );
            }

            return repaired;
        }

        /*
         * Rasterize the complete expected outline band from the uninterrupted
         * ShapeContour trajectory.
         */
        PointCollection expectedBand = new PointCollection();

        addInwardContourBand(
                expectedBand,
                contour,
                thickness
        );

        HashSet<Long> occupied = new HashSet<>(
                Math.max(16, source.size() * 2)
        );

        source.forEach((x, y) ->
                occupied.add(smoothKey(x, y))
        );

        /*
         * Group expected contour pixels by actual X column.
         */
        TreeMap<Integer, ArrayList<Integer>> expectedByX =
                new TreeMap<>();

        expectedBand.forEach((x, y) ->
                expectedByX
                        .computeIfAbsent(
                                x,
                                ignored -> new ArrayList<>()
                        )
                        .add(y)
        );

        ArrayList<Integer> fullyMissingColumns = new ArrayList<>();
        ArrayList<Integer> partiallyMissingColumns = new ArrayList<>();

        int expectedPixels = 0;
        int existingPixels = 0;
        int addedPixels = 0;
        int debugColumnsPrinted = 0;

        for (Map.Entry<Integer, ArrayList<Integer>> entry
                : expectedByX.entrySet()) {

            int x = entry.getKey();
            ArrayList<Integer> expectedYs = entry.getValue();

            expectedYs.sort(Integer::compare);

            int presentInColumn = 0;
            int missingInColumn = 0;

            for (int y : expectedYs) {
                expectedPixels++;

                long key = smoothKey(x, y);

                if (occupied.contains(key)) {
                    presentInColumn++;
                    existingPixels++;
                } else {
                    /*
                     * This exact expected contour-band pixel is absent.
                     * Add it regardless of how many neighboring X columns
                     * are also missing.
                     */
                    occupied.add(key);
                    repaired.add(new Point(x, y));

                    missingInColumn++;
                    addedPixels++;
                }
            }

            if (missingInColumn == 0) {
                continue;
            }

            if (presentInColumn == 0) {
                fullyMissingColumns.add(x);
            } else {
                partiallyMissingColumns.add(x);
            }

            if (debug && debugColumnsPrinted < 40) {
                System.out.println(
                        "[smooth] Missing column:"
                                + " x=" + x
                                + ", expected=" + expectedYs.size()
                                + ", present=" + presentInColumn
                                + ", added=" + missingInColumn
                                + ", yRange="
                                + expectedYs.get(0)
                                + ".."
                                + expectedYs.get(expectedYs.size() - 1)
                                + ", type="
                                + (presentInColumn == 0
                                ? "FULL"
                                : "PARTIAL")
                );

                debugColumnsPrinted++;
            }
        }

        if (debug) {
            System.out.println(
                    "[smooth] Contour repair summary:"
                            + " sourcePixels=" + source.size()
                            + ", contourPoints=" + contour.size()
                            + ", perimeter=" + contour.perimeter()
                            + ", thickness=" + thickness
                            + ", expectedBandPixels=" + expectedBand.size()
                            + ", expectedPixelsScanned=" + expectedPixels
                            + ", alreadyPresent=" + existingPixels
                            + ", pixelsAdded=" + addedPixels
                            + ", resultPixels=" + repaired.size()
            );

            System.out.println(
                    "[smooth] Fully missing X columns: "
                            + fullyMissingColumns.size()
                            + " "
                            + formatColumnRanges(fullyMissingColumns)
            );

            System.out.println(
                    "[smooth] Partially missing X columns: "
                            + partiallyMissingColumns.size()
                            + " "
                            + formatColumnRanges(partiallyMissingColumns)
            );

            if (debugColumnsPrinted == 40
                    && fullyMissingColumns.size()
                    + partiallyMissingColumns.size() > 40) {

                System.out.println(
                        "[smooth] Individual column output limited to 40."
                );
            }
        }

        return repaired;
    }


    private static List<Point2D.Double> smoothContourPath(
            ShapeContour contour,
            int radius,
            double strength
    ) {
        int size = contour.size();

        ArrayList<Point2D.Double> original =
                new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            Point point = contour.get(i);

            original.add(
                    new Point2D.Double(point.x, point.y)
            );
        }

        ArrayList<Point2D.Double> smoothed =
                new ArrayList<>(size);

        double sigma = Math.max(1.0, radius * 0.5);
        double denominator = 2.0 * sigma * sigma;

        for (int i = 0; i < size; i++) {
            double weightedX = 0.0;
            double weightedY = 0.0;
            double totalWeight = 0.0;

            for (int offset = -radius;
                 offset <= radius;
                 offset++) {

                int index = Math.floorMod(i + offset, size);

                Point2D.Double neighbor = original.get(index);

                double weight = Math.exp(
                        -(double) (offset * offset) / denominator
                );

                weightedX += neighbor.x * weight;
                weightedY += neighbor.y * weight;
                totalWeight += weight;
            }

            Point2D.Double current = original.get(i);

            double averageX = weightedX / totalWeight;
            double averageY = weightedY / totalWeight;

            smoothed.add(
                    new Point2D.Double(
                            current.x
                                    + (averageX - current.x) * strength,
                            current.y
                                    + (averageY - current.y) * strength
                    )
            );
        }

        /*
         * Gaussian contour smoothing slightly contracts closed curves.
         * Restore the original enclosed area without restoring its noise.
         */
        double originalArea = Math.abs(
                signedContourArea(original)
        );

        double smoothedArea = Math.abs(
                signedContourArea(smoothed)
        );

        if (originalArea > 0.0 && smoothedArea > 0.0) {
            Point2D.Double originalCenter =
                    averageContourPoint(original);

            Point2D.Double smoothedCenter =
                    averageContourPoint(smoothed);

            double scale = Math.sqrt(
                    originalArea / smoothedArea
            );

            scale = Math.max(0.90, Math.min(1.10, scale));

            for (Point2D.Double point : smoothed) {
                point.x = originalCenter.x
                        + (point.x - smoothedCenter.x) * scale;

                point.y = originalCenter.y
                        + (point.y - smoothedCenter.y) * scale;
            }
        }

        return smoothed;
    }

    private static PointCollection rasterizeContourBand(
            List<Point2D.Double> path,
            int thickness
    ) {
        PointCollection result = new PointCollection();

        if (path.size() < 2 || thickness <= 0) {
            return result;
        }

        double orientation =
                signedContourArea(path) >= 0.0 ? 1.0 : -1.0;

        for (int i = 0; i < path.size(); i++) {
            Point2D.Double start = path.get(i);
            Point2D.Double end =
                    path.get((i + 1) % path.size());

            double deltaX = end.x - start.x;
            double deltaY = end.y - start.y;
            double length = Math.hypot(deltaX, deltaY);

            if (length == 0.0) {
                continue;
            }

            double inwardX =
                    orientation * (-deltaY / length);

            double inwardY =
                    orientation * (deltaX / length);

            /*
             * Half-pixel sampling eliminates holes along diagonal segments.
             */
            int steps = Math.max(
                    1,
                    (int) Math.ceil(length * 2.0)
            );

            for (int step = 0; step <= steps; step++) {
                double progress = (double) step / steps;

                double edgeX = start.x + deltaX * progress;
                double edgeY = start.y + deltaY * progress;

                for (double depth = 0.0;
                     depth < thickness;
                     depth += 0.5) {

                    int x = (int) Math.round(
                            edgeX + inwardX * depth
                    );

                    int y = (int) Math.round(
                            edgeY + inwardY * depth
                    );

                    result.add(new Point(x, y));
                }
            }
        }

        return result;
    }

    private static double signedContourArea(
            List<Point2D.Double> points
    ) {
        double area = 0.0;

        for (int i = 0; i < points.size(); i++) {
            Point2D.Double first = points.get(i);
            Point2D.Double second =
                    points.get((i + 1) % points.size());

            area += first.x * second.y
                    - second.x * first.y;
        }

        return area * 0.5;
    }

    private static Point2D.Double averageContourPoint(
            List<Point2D.Double> points
    ) {
        double x = 0.0;
        double y = 0.0;

        for (Point2D.Double point : points) {
            x += point.x;
            y += point.y;
        }

        return new Point2D.Double(
                x / points.size(),
                y / points.size()
        );
    }

    private static String formatColumnRanges(
            List<Integer> columns
    ) {
        if (columns.isEmpty()) {
            return "[]";
        }

        StringBuilder result = new StringBuilder("[");
        int rangeStart = columns.get(0);
        int previous = rangeStart;

        for (int i = 1; i < columns.size(); i++) {
            int current = columns.get(i);

            if (current == previous + 1) {
                previous = current;
                continue;
            }

            appendColumnRange(result, rangeStart, previous);
            result.append(", ");

            rangeStart = current;
            previous = current;
        }

        appendColumnRange(result, rangeStart, previous);

        return result.append(']').toString();
    }

    private static void appendColumnRange(
            StringBuilder destination,
            int start,
            int end
    ) {
        destination.append(start);

        if (start != end) {
            destination.append("..").append(end);
        }
    }


    private static void addInwardContourBand(
            PointCollection destination,
            ShapeContour contour,
            int thickness
    ) {
        int pointCount = contour.size();

        if (pointCount < 2 || thickness <= 0) {
            return;
        }

        /*
         * A counterclockwise contour has its interior on the left side of
         * each directed edge. Reverse the normal for clockwise contours.
         */
        double orientation = contour.isCounterClockwise()
                ? 1.0
                : -1.0;

        for (int i = 0; i < pointCount; i++) {
            Point start = contour.get(i);
            Point end = contour.get((i + 1) % pointCount);

            int deltaX = end.x - start.x;
            int deltaY = end.y - start.y;

            int steps = Math.max(
                    Math.abs(deltaX),
                    Math.abs(deltaY)
            );

            if (steps == 0) {
                destination.add(new Point(start));
                continue;
            }

            double length = Math.hypot(deltaX, deltaY);

            double inwardX =
                    orientation * (-deltaY / length);

            double inwardY =
                    orientation * (deltaX / length);

            /*
             * Rasterize the complete edge, including long edges spanning
             * any number of missing columns.
             */
            for (int step = 0; step <= steps; step++) {
                double progress = (double) step / steps;

                double edgeX =
                        start.x + deltaX * progress;

                double edgeY =
                        start.y + deltaY * progress;

                /*
                 * Extend only toward the shape's interior. This produces
                 * the intended outline thickness without filling the
                 * region in the middle of the shape.
                 */
                for (int depth = 0;
                     depth < thickness;
                     depth++) {

                    int x = (int) Math.round(
                            edgeX + inwardX * depth
                    );

                    int y = (int) Math.round(
                            edgeY + inwardY * depth
                    );

                    destination.add(new Point(x, y));
                }
            }
        }
    }

    private static PointCollection fillSmallEnclosedHoles(
            PointCollection source,
            int maximumArea
    ) {
        if (source.isEmpty() || maximumArea <= 0) {
            return new PointCollection(source);
        }

        PointCollection result = new PointCollection(source);

        /*
         * hole() produces vertically enclosed empty pixels without filling
         * the entire region inside the outline.
         */
        PointCollection possibleHoles = source.hole();

        if (possibleHoles.isEmpty()) {
            return result;
        }

        HashSet<Long> occupied = new HashSet<>(
                Math.max(16, source.size() * 2)
        );

        HashSet<Long> candidates = new HashSet<>(
                Math.max(16, possibleHoles.size() * 2)
        );

        source.forEach((x, y) ->
                occupied.add(smoothKey(x, y))
        );

        possibleHoles.forEach((x, y) ->
                candidates.add(smoothKey(x, y))
        );

        HashSet<Long> visited = new HashSet<>(
                Math.max(16, candidates.size() * 2)
        );

        ArrayDeque<Point> queue = new ArrayDeque<>();
        ArrayList<Point> component = new ArrayList<>();

        for (Point startingPoint : possibleHoles) {
            long startingKey = smoothKey(
                    startingPoint.x,
                    startingPoint.y
            );

            if (!visited.add(startingKey)) {
                continue;
            }

            queue.clear();
            component.clear();

            queue.addLast(new Point(startingPoint));

            boolean openToExterior = false;

            while (!queue.isEmpty()) {
                Point point = queue.removeFirst();
                component.add(point);

                /*
                 * Use eight-way connectivity. A cavity connected diagonally
                 * to the exterior is therefore not considered enclosed.
                 */
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }

                        int neighborX = point.x + dx;
                        int neighborY = point.y + dy;
                        long neighborKey = smoothKey(
                                neighborX,
                                neighborY
                        );

                        if (occupied.contains(neighborKey)) {
                            continue;
                        }

                        if (!candidates.contains(neighborKey)) {
                            /*
                             * This empty neighbor is not vertically enclosed,
                             * so this component connects to the exterior.
                             */
                            openToExterior = true;
                            continue;
                        }

                        if (visited.add(neighborKey)) {
                            queue.addLast(
                                    new Point(neighborX, neighborY)
                            );
                        }
                    }
                }
            }

            if (!openToExterior
                    && component.size() <= maximumArea) {

                for (Point point : component) {
                    result.add(point);
                }
            }
        }

        return result;
    }

    private static long smoothKey(int x, int y) {
        return (((long) x) << 32)
                | (y & 0xffffffffL);
    }

    private static void printRegion(
            PointCollection points,
            int centerX,
            int centerY,
            int radius
    ) {

        HashSet<Long> occupied =
                new HashSet<>(points.size() * 2);

        points.forEach((x, y) ->
                occupied.add(
                        (((long) x) << 32)
                                | (y & 0xffffffffL)
                )
        );

        System.out.println();

        for (int y = centerY - radius;
             y <= centerY + radius;
             y++) {

            StringBuilder line =
                    new StringBuilder();

            for (int x = centerX - radius;
                 x <= centerX + radius;
                 x++) {

                long key =
                        (((long) x) << 32)
                                | (y & 0xffffffffL);

                line.append(
                        occupied.contains(key)
                                ? '#'
                                : '.'
                );
            }

            System.out.println(line);
        }
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

    public static List<Object> runTasksAsync(Supplier<?>... suppliers) {
        CompletableFuture[] futures = Arrays.stream(suppliers)
                .map(CompletableFuture::supplyAsync)
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        return Arrays.stream(futures)
                .map(CompletableFuture::join)
                .toList();
    }

    public static void runTasksAsync(List<Runnable> fns) {
        ExecutorService executorService = Executors.newFixedThreadPool(fns.size());
        try {
            CompletableFuture.allOf(
                    fns.stream()
                            .map(fn -> CompletableFuture.runAsync(fn, executorService))
                            .toArray(CompletableFuture[]::new)
            ).join();
        } finally {
            executorService.shutdown();
        }
    }
    public static void runTasksAsync(Runnable... fns) {
        runTasksAsync(Arrays.stream(fns).collect(Collectors.toList()));
    }

    public static Thread async(Runnable fn) {
        return varMutate(new Thread(fn), Thread::start);
    }
}

