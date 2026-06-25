package com.elijahsarte.celtools.mainex;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.List;

public final class DebugDump {

    public static final String FILE_EXTENSION = "ctdebugdump";
    private static final String MAGIC = "CTDEBUGDUMP";
    private static final int VERSION = 1;
    private static final int MAX_DEBUG_STRING_DEPTH = 5;
    private static final int MAX_DEBUG_STRING_ITEMS = 10_000;

    private DebugDump() {}

    public record DumpFile(long createdMillis, String exceptionStackTrace, List<Entry> entries) {}
    public record SizeEntry(String key, String typeName, String encoding, String info, long entryBytes, int valueChars) {}

    public static final class Entry {
        private final String key;
        private final String typeName;
        private final String encoding;
        private final String value;
        private final String info;
        private final Object object;

        private Entry(String key, String typeName, String encoding, String value, String info, Object object) {
            this.key = key;
            this.typeName = typeName;
            this.encoding = encoding;
            this.value = value;
            this.info = info;
            this.object = object;
        }

        public String key() { return key; }
        public String typeName() { return typeName; }
        public String encoding() { return encoding; }
        public String value() { return value; }
        public String info() { return info; }
        public Object object() { return object; }
    }

    public static final class MetadataObject {
        private final String sourceType;
        private final LinkedHashMap<String, String> fields;

        public MetadataObject(String sourceType, Map<String, String> fields) {
            this.sourceType = sourceType;
            this.fields = new LinkedHashMap<>(fields);
        }

        public String sourceType() { return sourceType; }
        public Map<String, String> fields() { return Collections.unmodifiableMap(fields); }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder(sourceType);
            fields.forEach((key, value) -> out.append('\n').append(key).append(" = ").append(value));
            return out.toString();
        }
    }

    public static File write(File file, Map<String, Object> debugVars, Throwable exception) throws IOException {
        return writeInternal(file, debugVars, exception == null ? "" : stackTrace(exception));
    }

    public static File writeDecoded(File file, Map<String, Object> debugVars, String exceptionStackTrace) throws IOException {
        return writeInternal(file, debugVars, exceptionStackTrace == null ? "" : exceptionStackTrace);
    }

    private static File writeInternal(File file, Map<String, Object> debugVars, String exceptionStackTrace) throws IOException {
        Objects.requireNonNull(file, "file");
        if (debugVars == null) debugVars = Collections.emptyMap();
        if (file.getParentFile() != null) Files.createDirectories(file.getParentFile().toPath());

        List<Entry> entries = new ArrayList<>(debugVars.size());
        for (Map.Entry<String, Object> entry : debugVars.entrySet()) {
            entries.add(encodeEntry(entry.getKey(), entry.getValue()));
        }

        StringBuilder out = new StringBuilder();
        out.append(MAGIC).append('\t').append(VERSION).append('\n');
        out.append("CREATED_MILLIS\t").append(System.currentTimeMillis()).append('\n');
        out.append("CREATED_INSTANT\t").append(Instant.now()).append('\n');
        out.append("EXCEPTION\t").append(b64(exceptionStackTrace)).append('\n');
        out.append("ENTRY_COUNT\t").append(entries.size()).append('\n');
        for (Entry entry : entries) {
            out.append("ENTRY\n");
            out.append("KEY\t").append(b64(entry.key)).append('\n');
            out.append("TYPE\t").append(b64(entry.typeName)).append('\n');
            out.append("ENCODING\t").append(entry.encoding).append('\n');
            out.append("INFO\t").append(b64(entry.info)).append('\n');
            out.append("VALUE\t").append(b64(entry.value)).append('\n');
            out.append("END_ENTRY\n");
        }
        Files.writeString(file.toPath(), out.toString(), StandardCharsets.UTF_8);
        return file;
    }

    public static DumpFile read(File file) throws IOException {
        Objects.requireNonNull(file, "file");
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).startsWith(MAGIC + "\t")) {
            throw new IOException("Not a ctdebugdump file: " + file);
        }

        long createdMillis = 0L;
        String exception = "";
        ArrayList<Entry> entries = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) continue;
            if (line.startsWith("CREATED_MILLIS\t")) {
                createdMillis = Long.parseLong(line.substring("CREATED_MILLIS\t".length()));
                continue;
            }
            if (line.startsWith("EXCEPTION\t")) {
                exception = fromB64(line.substring("EXCEPTION\t".length()));
                continue;
            }
            if (!"ENTRY".equals(line)) continue;

            String key = "";
            String type = "";
            String encoding = "TEXT";
            String info = "";
            String value = "";
            while (++i < lines.size()) {
                String entryLine = lines.get(i);
                if ("END_ENTRY".equals(entryLine)) break;
                int tab = entryLine.indexOf('\t');
                if (tab < 0) continue;
                String tag = entryLine.substring(0, tab);
                String payload = entryLine.substring(tab + 1);
                switch (tag) {
                    case "KEY" -> key = fromB64(payload);
                    case "TYPE" -> type = fromB64(payload);
                    case "ENCODING" -> encoding = payload;
                    case "INFO" -> info = fromB64(payload);
                    case "VALUE" -> value = fromB64(payload);
                    default -> { }
                }
            }
            Object object = decodeObject(encoding, value);
            entries.add(new Entry(key, type, encoding, value, info, object));
        }
        return new DumpFile(createdMillis, exception, Collections.unmodifiableList(entries));
    }

    public static List<SizeEntry> sizeReport(File file) throws IOException {
        Objects.requireNonNull(file, "file");
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        ArrayList<SizeEntry> entries = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            if (!"ENTRY".equals(lines.get(i))) continue;

            String key = "";
            String type = "";
            String encoding = "";
            String info = "";
            String value = "";
            long entryBytes = bytesWithNewline(lines.get(i));

            while (++i < lines.size()) {
                String line = lines.get(i);
                entryBytes += bytesWithNewline(line);
                if ("END_ENTRY".equals(line)) break;
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String tag = line.substring(0, tab);
                String payload = line.substring(tab + 1);
                switch (tag) {
                    case "KEY" -> key = safeDecodeForReport(payload);
                    case "TYPE" -> type = safeDecodeForReport(payload);
                    case "ENCODING" -> encoding = payload;
                    case "INFO" -> info = safeDecodeForReport(payload);
                    case "VALUE" -> value = payload;
                    default -> { }
                }
            }
            entries.add(new SizeEntry(key, type, encoding, info, entryBytes, value.length()));
        }

        entries.sort(Comparator.comparingLong(SizeEntry::entryBytes).reversed());
        return Collections.unmodifiableList(entries);
    }

    private static long bytesWithNewline(String line) {
        return (long) (line == null ? 0 : line.getBytes(StandardCharsets.UTF_8).length) + 1L;
    }

    private static String safeDecodeForReport(String value) {
        try {
            return fromB64(value);
        } catch (Throwable ignored) {
            return value;
        }
    }

    private static Entry encodeEntry(String key, Object obj) {
        String safeKey = key == null ? "" : key;
        String typeName = obj instanceof MetadataObject metadata ? metadata.sourceType() : (obj == null ? "null" : obj.getClass().getName());
        Encoded encoded = encodeObject(obj);
        return new Entry(safeKey, typeName, encoded.encoding, encoded.value, encoded.info, obj);
    }

    private record Encoded(String encoding, String value, String info) {}

    private record EncodingRule(String label, java.util.function.Predicate<Object> predicate, java.util.function.Function<Object, Encoded> encoder) {
        boolean supports(Object obj) { return obj != null && predicate.test(obj); }
        Encoded encode(Object obj) { return encoder.apply(obj); }
    }

    /*
     * Programmer-facing explicit type behavior registry. Add new custom dump
     * encoders here before the Java serialization/text fallback.
     */
    private static List<EncodingRule> explicitEncodingRules() {
        ArrayList<EncodingRule> rules = new ArrayList<>();
        rules.add(new EncodingRule("PointCollection", obj -> obj instanceof PointCollection,
                obj -> new Encoded("POINT_COLLECTION", ((PointCollection) obj).toDebugDumpString(), infoPointCollection((PointCollection) obj))));
        rules.add(new EncodingRule("ShapeContour", obj -> obj instanceof ShapeContour,
                obj -> new Encoded("SHAPE_CONTOUR", ((ShapeContour) obj).toDebugDumpString(), infoShapeContour((ShapeContour) obj))));
        rules.add(new EncodingRule("ShapeBounds", obj -> obj instanceof ShapeBounds,
                obj -> new Encoded("SHAPE_BOUNDS", ((ShapeBounds) obj).toDebugDumpString(), infoShapeBounds((ShapeBounds) obj))));
        rules.add(new EncodingRule("MetadataObject", obj -> obj instanceof MetadataObject,
                obj -> {
                    MetadataObject metadata = (MetadataObject) obj;
                    return new Encoded("METADATA", encodeMetadata(metadata), firstLine(metadata.toString()));
                }));
        rules.add(new EncodingRule("FastRGB metadata", obj -> obj instanceof FastRGB,
                obj -> {
                    MetadataObject metadata = metadataFastRGB((FastRGB) obj);
                    return new Encoded("METADATA", encodeMetadata(metadata), firstLine(metadata.toString()));
                }));
        rules.add(new EncodingRule("ImageHandler metadata", obj -> obj instanceof ImageHandler,
                obj -> {
                    MetadataObject metadata = metadataImageHandler((ImageHandler) obj);
                    return new Encoded("METADATA", encodeMetadata(metadata), firstLine(metadata.toString()));
                }));
        rules.add(new EncodingRule("BufferedImage metadata", obj -> obj instanceof BufferedImage,
                obj -> {
                    MetadataObject metadata = metadataBufferedImage((BufferedImage) obj, "BufferedImage");
                    return new Encoded("METADATA", encodeMetadata(metadata), firstLine(metadata.toString()));
                }));
        rules.add(new EncodingRule("Map<Integer,List<Integer>>", obj -> obj instanceof Map<?, ?> map && isIntegerListMap(map),
                obj -> new Encoded("MAP_INTEGER_LIST", encodeIntegerListMap((Map<?, ?>) obj), infoIntegerListMap((Map<?, ?>) obj))));
        rules.add(new EncodingRule("Map<Integer,IntegerBounds>", obj -> obj instanceof Map<?, ?> map && isIntegerBoundsMap(map),
                obj -> new Encoded("MAP_INTEGER_BOUNDS", encodeIntegerBoundsMap((Map<?, ?>) obj), infoIntegerBoundsMap((Map<?, ?>) obj))));
        rules.add(new EncodingRule("List<PointCollection>", DebugDump::isPointCollectionCollection,
                obj -> new Encoded("POINT_COLLECTION_LIST", encodePointCollectionList((Collection<?>) obj), ((Collection<?>) obj).size() + " PointCollection item(s)")));
        rules.add(new EncodingRule("List<ShapeContour>", DebugDump::isShapeContourCollection,
                obj -> new Encoded("SHAPE_CONTOUR_LIST", encodeShapeContourList((Collection<?>) obj), ((Collection<?>) obj).size() + " ShapeContour item(s)")));
        rules.add(new EncodingRule("PointCollection[]", DebugDump::isPointCollectionArray,
                obj -> new Encoded("POINT_COLLECTION_ARRAY", encodeArray(obj, "POINT_COLLECTION"), Array.getLength(obj) + " PointCollection item(s)")));
        rules.add(new EncodingRule("ShapeContour[]", DebugDump::isShapeContourArray,
                obj -> new Encoded("SHAPE_CONTOUR_ARRAY", encodeArray(obj, "SHAPE_CONTOUR"), Array.getLength(obj) + " ShapeContour item(s)")));
        return Collections.unmodifiableList(rules);
    }

    private static Encoded encodeObject(Object obj) {
        if (obj == null) return new Encoded("NULL", "", "null");

        for (EncodingRule rule : explicitEncodingRules()) {
            if (!rule.supports(obj)) continue;
            try {
                return rule.encode(obj);
            } catch (Throwable ignored) {
                // Fall through to later encoders/fallbacks rather than making the entire dump fail.
            }
        }

        Encoded serialized = trySerialize(obj);
        if (serialized != null) return serialized;

        String text = debugString(obj);
        return new Encoded("TEXT", text, firstLine(text));
    }

    private static Object decodeObject(String encoding, String value) {
        try {
            return switch (encoding) {
                case "NULL" -> null;
                case "POINT_COLLECTION" -> PointCollection.fromDebugDumpString(value);
                case "SHAPE_CONTOUR" -> ShapeContour.fromDebugDumpString(value);
                case "SHAPE_BOUNDS" -> ShapeBounds.fromDebugDumpString(value);
                case "MAP_INTEGER_LIST" -> decodeIntegerListMap(value);
                case "MAP_INTEGER_BOUNDS" -> decodeIntegerBoundsMap(value);
                case "POINT_COLLECTION_LIST" -> decodePointCollectionList(value);
                case "SHAPE_CONTOUR_LIST" -> decodeShapeContourList(value);
                case "POINT_COLLECTION_ARRAY" -> decodePointCollectionList(value).toArray(new PointCollection[0]);
                case "SHAPE_CONTOUR_ARRAY" -> decodeShapeContourList(value).toArray(new ShapeContour[0]);
                case "METADATA" -> decodeMetadata(value);
                case "JAVA_SERIALIZED" -> deserialize(value);
                default -> value;
            };
        } catch (Throwable t) {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("decodeError", t.toString());
            fields.put("rawEncoding", encoding);
            fields.put("rawValue", value);
            return new MetadataObject("UnreadableResource", fields);
        }
    }


    private static boolean isIntegerListMap(Map<?, ?> map) {
        if (map.isEmpty()) return false;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof Integer)) return false;
            Object value = entry.getValue();
            if (!(value instanceof List<?> list)) return false;
            for (Object item : list) {
                if (!(item instanceof Integer)) return false;
            }
        }
        return true;
    }

    private static boolean isIntegerBoundsMap(Map<?, ?> map) {
        if (map.isEmpty()) return false;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof Integer) || !(entry.getValue() instanceof IntegerBounds)) return false;
        }
        return true;
    }

    private static boolean isPointCollectionCollection(Object obj) {
        return obj instanceof Collection<?> collection
                && !collection.isEmpty()
                && collection.stream().allMatch(PointCollection.class::isInstance);
    }

    private static boolean isShapeContourCollection(Object obj) {
        return obj instanceof Collection<?> collection
                && !collection.isEmpty()
                && collection.stream().allMatch(ShapeContour.class::isInstance);
    }

    private static boolean isPointCollectionArray(Object obj) {
        if (obj == null || !obj.getClass().isArray()) return false;
        int len = Array.getLength(obj);
        if (len == 0) return false;
        for (int i = 0; i < len; i++) if (!(Array.get(obj, i) instanceof PointCollection)) return false;
        return true;
    }

    private static boolean isShapeContourArray(Object obj) {
        if (obj == null || !obj.getClass().isArray()) return false;
        int len = Array.getLength(obj);
        if (len == 0) return false;
        for (int i = 0; i < len; i++) if (!(Array.get(obj, i) instanceof ShapeContour)) return false;
        return true;
    }

    private static String encodeIntegerListMap(Map<?, ?> map) {
        TreeMap<Integer, List<Integer>> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            ArrayList<Integer> values = new ArrayList<>();
            for (Object item : (List<?>) entry.getValue()) values.add((Integer) item);
            sorted.put((Integer) entry.getKey(), values);
        }

        StringBuilder out = new StringBuilder("CTMAPINTEGERLIST\t1\n");
        out.append("COUNT\t").append(sorted.size()).append('\n');
        sorted.forEach((x, ys) -> {
            out.append("X\t").append(x).append('\t').append(ys.size()).append('\t');
            for (int i = 0; i < ys.size(); i++) {
                if (i > 0) out.append(',');
                out.append(ys.get(i));
            }
            out.append('\n');
        });
        return out.toString();
    }

    private static String encodeIntegerBoundsMap(Map<?, ?> map) {
        TreeMap<Integer, IntegerBounds> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) sorted.put((Integer) entry.getKey(), (IntegerBounds) entry.getValue());

        StringBuilder out = new StringBuilder("CTMAPINTEGERBOUNDS\t1\n");
        out.append("COUNT\t").append(sorted.size()).append('\n');
        sorted.forEach((x, bounds) -> out.append("B\t")
                .append(x).append('\t')
                .append(bounds.getLowerBound()).append('\t')
                .append(bounds.getUpperBound()).append('\n'));
        return out.toString();
    }

    private static TreeMap<Integer, List<Integer>> decodeIntegerListMap(String value) {
        TreeMap<Integer, List<Integer>> decoded = new TreeMap<>();
        for (String line : value.split("\\R", -1)) {
            if (!line.startsWith("X\t")) continue;
            String[] parts = line.split("\t", 4);
            if (parts.length < 4) throw new IllegalArgumentException("Malformed Map<Integer,List<Integer>> line: " + line);
            int x = Integer.parseInt(parts[1]);
            int expected = Integer.parseInt(parts[2]);
            ArrayList<Integer> ys = new ArrayList<>(Math.max(0, expected));
            if (!parts[3].isBlank()) {
                for (String token : parts[3].split(",")) {
                    if (!token.isBlank()) ys.add(Integer.parseInt(token));
                }
            }
            if (expected >= 0 && ys.size() != expected) {
                throw new IllegalArgumentException("Map<Integer,List<Integer>> column " + x + " expected " + expected + " value(s), got " + ys.size());
            }
            decoded.put(x, ys);
        }
        return decoded;
    }

    private static TreeMap<Integer, IntegerBounds> decodeIntegerBoundsMap(String value) {
        TreeMap<Integer, IntegerBounds> decoded = new TreeMap<>();
        for (String line : value.split("\\R", -1)) {
            if (!line.startsWith("B\t")) continue;
            String[] parts = line.split("\t", 4);
            if (parts.length < 4) throw new IllegalArgumentException("Malformed Map<Integer,IntegerBounds> line: " + line);
            decoded.put(Integer.parseInt(parts[1]), new IntegerBounds(Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        }
        return decoded;
    }

    private static String infoIntegerListMap(Map<?, ?> map) {
        long valueCount = 0L;
        for (Object value : map.values()) valueCount += ((List<?>) value).size();
        return "Map<Integer,List<Integer>>: " + map.size() + " column(s), " + valueCount + " value(s)";
    }

    private static String infoIntegerBoundsMap(Map<?, ?> map) {
        return "Map<Integer,IntegerBounds>: " + map.size() + " bound column(s)";
    }

    private static String encodePointCollectionList(Collection<?> collection) {
        StringBuilder out = new StringBuilder("CTPOINTCOLLECTIONLIST\t1\n");
        out.append("COUNT\t").append(collection.size()).append('\n');
        for (Object item : collection) {
            out.append("ITEM\t").append(b64(((PointCollection) item).toDebugDumpString())).append('\n');
        }
        return out.toString();
    }

    private static String encodeShapeContourList(Collection<?> collection) {
        StringBuilder out = new StringBuilder("CTSHAPECONTOURLIST\t1\n");
        out.append("COUNT\t").append(collection.size()).append('\n');
        for (Object item : collection) {
            out.append("ITEM\t").append(b64(((ShapeContour) item).toDebugDumpString())).append('\n');
        }
        return out.toString();
    }

    private static String encodeArray(Object array, String elementEncoding) {
        int len = Array.getLength(array);
        StringBuilder out = new StringBuilder(elementEncoding.equals("POINT_COLLECTION") ? "CTPOINTCOLLECTIONLIST\t1\n" : "CTSHAPECONTOURLIST\t1\n");
        out.append("COUNT\t").append(len).append('\n');
        for (int i = 0; i < len; i++) {
            Object item = Array.get(array, i);
            String itemDump = elementEncoding.equals("POINT_COLLECTION")
                    ? ((PointCollection) item).toDebugDumpString()
                    : ((ShapeContour) item).toDebugDumpString();
            out.append("ITEM\t").append(b64(itemDump)).append('\n');
        }
        return out.toString();
    }

    private static List<PointCollection> decodePointCollectionList(String value) {
        ArrayList<PointCollection> out = new ArrayList<>();
        for (String line : value.split("\\R", -1)) {
            if (line.startsWith("ITEM\t")) out.add(PointCollection.fromDebugDumpString(fromB64(line.substring(5))));
        }
        return out;
    }

    private static List<ShapeContour> decodeShapeContourList(String value) {
        ArrayList<ShapeContour> out = new ArrayList<>();
        for (String line : value.split("\\R", -1)) {
            if (line.startsWith("ITEM\t")) out.add(ShapeContour.fromDebugDumpString(fromB64(line.substring(5))));
        }
        return out;
    }

    private static Encoded trySerialize(Object obj) {
        if (!(obj instanceof Serializable)) return null;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(obj);
            return new Encoded("JAVA_SERIALIZED", Base64.getEncoder().encodeToString(bytes.toByteArray()), obj.getClass().getName());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object deserialize(String value) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(value);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    private static MetadataObject metadataFastRGB(FastRGB rgb) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("width", String.valueOf(rgb.getWidth()));
        fields.put("height", String.valueOf(rgb.getHeight()));
        fields.put("length", String.valueOf(rgb.getLength()));
        fields.put("hasAlphaChannel", String.valueOf(rgb.hasAlphaChannel()));
        fields.put("pixels", "omitted");
        return new MetadataObject("FastRGB", fields);
    }

    private static MetadataObject metadataBufferedImage(BufferedImage image, String title) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (image == null) {
            fields.put("image", "null");
        } else {
            fields.put("width", String.valueOf(image.getWidth()));
            fields.put("height", String.valueOf(image.getHeight()));
            fields.put("type", String.valueOf(image.getType()));
            fields.put("hasAlpha", String.valueOf(image.getColorModel().hasAlpha()));
            fields.put("colorModel", image.getColorModel().getClass().getName());
            fields.put("sampleModel", image.getRaster().getSampleModel().getClass().getName());
            fields.put("pixels", "omitted");
        }
        return new MetadataObject(title, fields);
    }

    private static MetadataObject metadataImageHandler(ImageHandler handler) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("imageFile", String.valueOf(fieldValue(handler, "imageFile")));
        fields.put("rgbType", String.valueOf(fieldValue(handler, "rgbType")));
        Object image = fieldValue(handler, "image");
        Object guiImage = fieldValue(handler, "guiImage");
        Object miniImage = fieldValue(handler, "miniImage");
        fields.put("image", metadataBufferedImage(image instanceof BufferedImage bi ? bi : null, "image").fields().toString());
        fields.put("guiImage", metadataBufferedImage(guiImage instanceof BufferedImage bi ? bi : null, "guiImage").fields().toString());
        fields.put("miniImage", metadataBufferedImage(miniImage instanceof BufferedImage bi ? bi : null, "miniImage").fields().toString());
        fields.put("pixels", "omitted");
        return new MetadataObject("ImageHandler", fields);
    }

    private static String encodeMetadata(MetadataObject metadata) {
        StringBuilder out = new StringBuilder("CTMETADATA\t1\n");
        out.append("SOURCE\t").append(b64(metadata.sourceType())).append('\n');
        metadata.fields().forEach((key, value) -> out.append("FIELD\t").append(b64(key)).append('\t').append(b64(value)).append('\n'));
        return out.toString();
    }

    private static MetadataObject decodeMetadata(String value) {
        String source = "Metadata";
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (String line : value.split("\\R", -1)) {
            if (line.startsWith("SOURCE\t")) {
                source = fromB64(line.substring(7));
            } else if (line.startsWith("FIELD\t")) {
                String[] parts = line.split("\\t", 3);
                if (parts.length == 3) fields.put(fromB64(parts[1]), fromB64(parts[2]));
            }
        }
        return new MetadataObject(source, fields);
    }

    private static Object fieldValue(Object target, String name) {
        if (target == null) return null;
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (Throwable t) {
                return "<" + t.getClass().getSimpleName() + ": " + t.getMessage() + ">";
            }
        }
        return null;
    }

    public static String debugString(Object obj) {
        return debugString(obj, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static String debugString(Object obj, int depth, Set<Object> seen) {
        if (obj == null) return "null";
        if (depth > MAX_DEBUG_STRING_DEPTH) return shortObjectString(obj);

        if (obj instanceof CharSequence || obj instanceof Number || obj instanceof Boolean || obj instanceof Character || obj instanceof Enum<?>) {
            return String.valueOf(obj);
        }
        if (obj instanceof IntegerBounds bounds) {
            return "IntegerBounds[" + bounds.getLowerBound() + ".." + bounds.getUpperBound() + "]";
        }
        if (obj instanceof Point point) {
            return "Point[x=" + point.x + ",y=" + point.y + "]";
        }
        if (obj instanceof Rectangle rect) {
            return rect.toString();
        }
        if (obj instanceof PointCollection collection) {
            return infoPointCollection(collection);
        }
        if (obj instanceof ShapeContour contour) {
            return infoShapeContour(contour);
        }
        if (obj instanceof ShapeBounds bounds) {
            return infoShapeBounds(bounds);
        }
        if (obj instanceof MetadataObject metadata) {
            return metadata.toString();
        }

        if (!isPrimitiveLike(obj) && !seen.add(obj)) {
            return "<cycle " + obj.getClass().getName() + ">";
        }
        try {
            if (obj instanceof Map<?, ?> map) {
                StringBuilder out = new StringBuilder(obj.getClass().getName())
                        .append(" size=").append(map.size()).append(" {");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (i >= MAX_DEBUG_STRING_ITEMS) {
                        out.append("\n  ... truncated ").append(map.size() - i).append(" entry(s)");
                        break;
                    }
                    out.append("\n  ")
                            .append(debugString(entry.getKey(), depth + 1, seen))
                            .append(" -> ")
                            .append(debugString(entry.getValue(), depth + 1, seen));
                    i++;
                }
                out.append("\n}");
                return out.toString();
            }

            if (obj instanceof Collection<?> collection) {
                StringBuilder out = new StringBuilder(obj.getClass().getName())
                        .append(" size=").append(collection.size()).append(" [");
                int i = 0;
                for (Object item : collection) {
                    if (i >= MAX_DEBUG_STRING_ITEMS) {
                        out.append("\n  ... truncated ").append(collection.size() - i).append(" item(s)");
                        break;
                    }
                    out.append("\n  [").append(i).append("] ")
                            .append(debugString(item, depth + 1, seen));
                    i++;
                }
                out.append("\n]");
                return out.toString();
            }

            Class<?> cls = obj.getClass();
            if (cls.isArray()) {
                int len = Array.getLength(obj);
                StringBuilder out = new StringBuilder(cls.getComponentType() == null ? cls.getName() : cls.getComponentType().getName())
                        .append('[').append(len).append("] {");
                int max = Math.min(len, MAX_DEBUG_STRING_ITEMS);
                for (int i = 0; i < max; i++) {
                    out.append("\n  [").append(i).append("] ")
                            .append(debugString(Array.get(obj, i), depth + 1, seen));
                }
                if (max < len) out.append("\n  ... truncated ").append(len - max).append(" item(s)");
                out.append("\n}");
                return out.toString();
            }

            return reflectiveDescription(obj, depth, seen);
        } finally {
            if (!isPrimitiveLike(obj)) seen.remove(obj);
        }
    }

    private static boolean isPrimitiveLike(Object obj) {
        return obj == null
                || obj instanceof CharSequence
                || obj instanceof Number
                || obj instanceof Boolean
                || obj instanceof Character
                || obj instanceof Enum<?>;
    }

    private static String shortObjectString(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Collection<?> collection) return obj.getClass().getName() + " size=" + collection.size();
        if (obj instanceof Map<?, ?> map) return obj.getClass().getName() + " size=" + map.size();
        if (obj.getClass().isArray()) return obj.getClass().getComponentType().getName() + '[' + Array.getLength(obj) + ']';
        return String.valueOf(obj);
    }

    private static String reflectiveDescription(Object obj, int depth, Set<Object> seen) {
        if (obj == null) return "null";
        if (depth > MAX_DEBUG_STRING_DEPTH) return shortObjectString(obj);

        Class<?> cls = obj.getClass();
        StringBuilder out = new StringBuilder(cls.getName()).append('\n');
        out.append("toString = ").append(String.valueOf(obj)).append('\n');
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods)) continue;
                if ("pixels".equalsIgnoreCase(field.getName())) {
                    out.append(field.getName()).append(" = omitted\n");
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    out.append(field.getName()).append(" = ");
                    if (value == null || value instanceof Number || value instanceof CharSequence || value instanceof Boolean || value instanceof Enum<?>) {
                        out.append(String.valueOf(value));
                    } else if (value.getClass().isArray() || value instanceof Collection<?> || value instanceof Map<?, ?> || value instanceof IntegerBounds) {
                        out.append(debugString(value, depth + 1, seen));
                    } else {
                        out.append(String.valueOf(value));
                    }
                    out.append('\n');
                } catch (Throwable t) {
                    out.append(field.getName()).append(" = <unreadable: ").append(t.getClass().getSimpleName()).append(">\n");
                }
            }
        }
        return out.toString();
    }

    private static String infoPointCollection(PointCollection collection) {
        if (collection == null || collection.isEmpty()) return "PointCollection: 0 points";
        return "PointCollection: " + collection.size() + " points, x=" + collection.firstX() + ".." + collection.lastX()
                + ", y=" + collection.bottomY() + ".." + collection.topY();
    }

    private static String infoShapeContour(ShapeContour contour) {
        if (contour == null || contour.isEmpty()) return "ShapeContour: 0 points";
        Rectangle r = contour.rect();
        return "ShapeContour: " + contour.size() + " points, rect=" + r.x + "," + r.y + " " + r.width + "x" + r.height
                + ", perimeter=" + contour.perimeter();
    }

    private static String infoShapeBounds(ShapeBounds bounds) {
        if (bounds == null || bounds.get() == null || bounds.get().isEmpty()) return "ShapeBounds: empty";
        return "ShapeBounds: points=" + bounds.get().size() + ", x=" + bounds.firstX() + ".." + bounds.lastX()
                + ", y=" + bounds.bottomY() + ".." + bounds.topY();
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String firstLine(String text) {
        if (text == null || text.isEmpty()) return "";
        int idx = text.indexOf('\n');
        return idx < 0 ? text : text.substring(0, idx);
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    private static String fromB64(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
