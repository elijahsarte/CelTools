package com.elijahsarte.celtools.main.util;

import com.elijahsarte.celtools.main.util.function.blocks.forloop.ForIncrement;
import com.elijahsarte.celtools.main.util.function.blocks.switchloop.ConditionalCase;
import com.elijahsarte.celtools.main.util.function.blocks.switchloop.SwitchIf;
import com.elijahsarte.celtools.main.util.geomex.Line;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;
import com.elijahsarte.celtools.main.util.structures.collections.IntList;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;

public final class CollectionsEx {

    public static <K, V> Map<V, List<K>> flipListMap(Map<K, List<V>> map) {
        return map.entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(v1 -> new AbstractMap.SimpleEntry<>(v1, e.getKey())))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    public static <K, V> Map<V, List<K>> flipMap(Map<K, V> map) {
        return map.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));
    }

    public static <K, V> Map<V, K> flipMapSingular(Map<K, V> map) {
        return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    public static <K> K firstKey(Set<K> set) {
        return new TreeSet<>(set).first();
    }
    public static <K> K lastKey(Set<K> set) {
        return new TreeSet<>(set).last();
    }


    public static <K, V> void listPut(Map<K, List<V>> map, K key, V value, boolean unique) {
        List<V> hereList = map.getOrDefault(key, new ArrayList<>());
        if (unique && hereList.contains(value)) {
            return;
        }
        hereList.add(value);
        map.put(key, hereList);
    }

    public static <K, V> void listPut(Map<K, List<V>> map, K key, V value) {
        CollectionsEx.listPut(map, key, value, true);
    }

    public static <K, V> void listPut(Map<K, List<V>> map, K key, List<V> value, boolean unique) {
        value.forEach(val -> CollectionsEx.listPut(map, key, val, unique));
    }

    public static <K, V> void listPut(Map<K, List<V>> map, K key, List<V> value) {
        CollectionsEx.listPut(map, key, value, true);
    }

    public static <K, V> void structPut(Map<K, V> map, K key, Consumer<V> putFn, V def) {
        if (map.containsKey(key)) putFn.accept(map.get(key));
        else map.put(key, varMutate(def, putFn));
    }

    public static <T, L extends Collection<T>> List<T> concatLists(L list1, L list2) {
        return StreamEx.toArrayList(Stream.concat(list1.stream(), list2.stream()));
    }

    public static <T, L extends Collection<T>> List<T> uniqueList(L list) {
        return StreamEx.toArrayList(list.stream().distinct());
    }


    @SafeVarargs
    public static <T> List<T> arrayListOf(T... args) {
        return returnAddAll(new ArrayList<>(), args);
    }

    public static <K, V> TreeMap<K, V> treeMapOf(Map<K, V> args) {
        return (args instanceof TreeMap<K, V>) ? (TreeMap<K, V>) args : new TreeMap<>(args);
    }

    public static <T, K extends Comparable<T>, V> TreeMap<K, V> treeMapOf(K key, V val) {
        return varMutate(new TreeMap<>(), map -> map.put(key, val));
    }

    public static <K, V, M extends Map<K, V>> M concatMaps(M map1, Map<K, V> map2) {
        map1.putAll(map2);
        return map1;
    }

    public static <K, V, M extends Map<K, V>> M returnPut(M map, K key, V value) {
        map.put(key, value);
        return map;
    }

    public static <T, L extends Collection<T>> L returnAdd(L list, T element) {
        list.add(element);
        return list;
    }

    public static <T, L extends Collection<T>> L returnAddAll(L list, L list1) {
        list.addAll(list1);
        return list;
    }

    @SafeVarargs
    public static <T, L extends Collection<T>> L returnAddAll(L list, T... elements) {
        Collections.addAll(list, elements);
        return list;
    }

    public static <K, V> V returnElemPut(Map<K, V> map, K key, V value) {
        map.put(key, value);
        return value;
    }

    public static <T, L extends Collection<T>> T returnElemAdd(L list, T element) {
        list.add(element);
        return element;
    }

    public static <K, L, V, M extends Map<L, V>> void mapPut(Map<K, M> map, K key, L subKey, V value, M defaultVal) {
        map.put(key, returnPut(map.getOrDefault(key, defaultVal), subKey, value));
    }

    public static <T extends Comparable<? super T>> List<T> returnSort(List<T> list) {
        Collections.sort(list);
        return list;
    }

    public static <T extends Number, L extends Collection<T>> Optional<T> closestElem(L collection, T elem) {
        return collection.stream().min(Comparator.comparingDouble(v -> Math.abs(MathEx.subtract(v, elem))));
    }

    public static <L extends Collection<Double>> Optional<Double> closestElem(L collection, double elem) {
        return closestElem(collection, Double.valueOf(elem));
    }
    public static <L extends Collection<Integer>> Optional<Integer> closestElem(L collection, int elem) {
        return closestElem(collection, Integer.valueOf(elem));
    }
    public static <L extends Collection<Long>> Optional<Long> closestElem(L collection, long elem) {
        return closestElem(collection, Long.valueOf(elem));
    }
    public static <L extends Collection<Short>> Optional<Short> closestElem(L collection, short elem) {
        return closestElem(collection, Short.valueOf(elem));
    }
    public static <L extends Collection<Byte>> Optional<Byte> closestElem(L collection, byte elem) {
        return closestElem(collection, Byte.valueOf(elem));
    }

    public static Optional<Integer> closestElem(IntList collection, int elem) {
        int beforeElem = INVALID_INT, afterElem = INVALID_INT;
        for (int num : collection) {
            if (num < elem) beforeElem = num;
            if (num >= elem) {
                afterElem = num;
                break;
            }
        }
        if (beforeElem == INVALID_INT && afterElem == INVALID_INT) return Optional.empty();
        if (beforeElem == INVALID_INT) return Optional.of(afterElem);
        if (afterElem == INVALID_INT) return Optional.of(beforeElem);
        return Optional.of(MathEx.closer(elem, beforeElem, afterElem));
    }

    public static <T extends Number, L extends Collection<T>> Optional<T> furthestElem(L collection, T elem) {
        return collection.stream().min(Comparator.comparingDouble(v -> Math.abs(MathEx.subtract(v, elem))));
    }

    public static <L extends Collection<Double>> Optional<Double> furthestElem(L collection, double elem) {
        return furthestElem(collection, Double.valueOf(elem));
    }
    public static <L extends Collection<Integer>> Optional<Integer> furthestElem(L collection, int elem) {
        return furthestElem(collection, Integer.valueOf(elem));
    }
    public static <L extends Collection<Float>> Optional<Float> furthestElem(L collection, float elem) {
        return furthestElem(collection, Float.valueOf(elem));
    }
    public static <L extends Collection<Long>> Optional<Long> furthestElem(L collection, long elem) {
        return furthestElem(collection, Long.valueOf(elem));
    }
    public static <L extends Collection<Short>> Optional<Short> furthestElem(L collection, short elem) {
        return furthestElem(collection, Short.valueOf(elem));
    }
    public static <L extends Collection<Byte>> Optional<Byte> furthestElem(L collection, byte elem) {
        return furthestElem(collection, Byte.valueOf(elem));
    }


    public static <M extends Collection<?>> int lastIdx(M list) {
        return list.size() - 1;
    }

    public static <T> T lastElem(List<T> list) {
        return list.get(lastIdx(list));
    }

    public static <T> T secondToLastElem(List<T> list) {
        return list.get(list.size() - 2);
    }

    public static <T> T nToLastElem(List<T> list, int index) {
        return list.get(list.size() - (index + 1));
    }

    public static <T> T lastElem(T[] arr) {
        return arr[arr.length - 1];
    }
    public static int lastElem(int[] arr) {
        return arr[arr.length - 1];
    }
    public static byte lastElem(byte[] arr) {
        return arr[arr.length - 1];
    }
    public static short lastElem(short[] arr) {
        return arr[arr.length - 1];
    }
    public static long lastElem(long[] arr) {
        return arr[arr.length - 1];
    }
    public static float lastElem(float[] arr) {
        return arr[arr.length - 1];
    }
    public static double lastElem(double[] arr) {
        return arr[arr.length - 1];
    }
    public static char lastElem(char[] arr) {
        return arr[arr.length - 1];
    }
    public static boolean lastElem(boolean[] arr) {
        return arr[arr.length - 1];
    }


    public static <T extends Number, L extends Collection<T>> boolean containsDist(L list, T target, double dist) {
        return list.stream().anyMatch(val -> Math.abs(MathEx.subtract(val, target)) <= dist);
    }

    public static boolean containsDist(IntList list, double target, double dist) {
        return list.stream().anyMatch(val -> Math.abs(val - target) <= dist);
    }

    public static <T> List<List<T>> groupByDist(List<T> givenList, double dist) {
        return givenList.stream()
                .sorted()
                .collect(ArrayList::new,
                        (list, num) -> {
                            List<T> currList = list.isEmpty() ? new ArrayList<>() : lastElem(list);
                            if (list.isEmpty() || MathEx.subtract(num, lastElem(currList)) > dist) {
                                list.add(arrayListOf(num));
                            } else {
                                currList.add(num);
                            }
                        },
                        ArrayList::addAll);
    }

    public static List<IntList> groupByDist(IntList givenList, double dist) {
        return givenList.stream()
                .collect(ArrayList::new,
                        (list, num) -> {
                            IntList currList = list.isEmpty() ? new IntList() : lastElem(list);
                            if (list.isEmpty() || MathEx.subtract(num, currList.last()) > dist) {
                                list.add(new IntList(num));
                            } else {
                                currList.add(num);
                            }
                        },
                        ArrayList::addAll);
    }

    public static <T> double listDist(List<T> list) {
        return Math.abs(MathEx.subtract(lastElem(list), list.get(0)));
    }


    public static <K, V> List<K> keyList(Map<K, V> map) {
        return new ArrayList<>(map.keySet());
    }
    public static <K, V> List<V> valueList(Map<K, V> map) {
        return new ArrayList<>(map.values());
    }

    /*
    public static <T> List<T> listSlice(List<T> list, int start, int end) {
        ProgrammingEx.forNumber(() -> ProgrammingEx.varMutate(list, (l) -> l.remove(0)), start);
        ProgrammingEx.forNumber(() -> ProgrammingEx.varMutate(list, (l) -> l.remove(l.size() - 1)), list.size() - end);
        return list;
    }
     */
    public static <T> List<T> listSlice(List<T> list, int start, int end) {
        return varOper(list, (l) -> listSlice(l, start), (l) -> listSlice(list, -(list.size() - end)));
    }

    public static <T> List<T> listSlice(List<T> list, int idx) {
        return forceReturn(list,
                idx < 0 ? () -> forNumber(() -> varMutate(list, (l) -> l.remove(l.size() - 1)), -idx)
                        : () -> forNumber(() -> varMutate(list, (l) -> l.remove(0)), idx));
    }

    public static <T, V, M extends SortedMap<T, V>> M mapSlice(M map, int idx) {
        return forceReturn(map,
                idx < 0 ? () -> forNumber(() -> varMutate(map, m -> m.remove(m.lastKey())), -idx)
                        : () -> forNumber(() -> varMutate(map, m -> m.remove(m.firstKey())), idx));
    }

    public static <T> List<T> oddIndices(List<T> list) {
        return StreamEx.indexFilter(list, i -> i % 2 != 0).toList();
    }

    public static <T> List<T> evenIndices(List<T> list) {
        return StreamEx.indexFilter(list, i -> i % 2 == 0).toList();
    }

    public static List<Integer> toBoxedList(int[] arr) {
        return IntStream.of(arr).boxed().toList();
    }
    public static List<Double> toBoxedList(double[] arr) {
        return DoubleStream.of(arr).boxed().toList();
    }
    public static List<Long> toBoxedList(long[] arr) {
        return LongStream.of(arr).boxed().toList();
    }
    public static List<Boolean> toBoxedList(boolean[] arr) {
        return StreamEx.ofBoolean(arr).toList();
    }
    // the ugly methods
    public static List<Float> toBoxedList(float[] arr) {
        List<Float> list = new ArrayList<>();
        for (float v : arr) list.add(v);
        return list;
//        return varMutate(new ArrayList<>(), (list) -> new ForEach(arr).execute((v, f) -> list.add((float) v)));
    }
    public static List<Byte> toBoxedList(byte[] arr) {
        List<Byte> list = new ArrayList<>();
        for (byte v : arr) list.add(v);
        return list;
    }
    public static Integer[] toBoxedArr(int[] arr) {
        Integer[] out = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }

    public static Double[] toBoxedArr(double[] arr) {
        Double[] out = new Double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }
    public static Long[] toBoxedArr(long[] arr) {
        Long[] out = new Long[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }
    public static Boolean[] toBoxedArr(boolean[] arr) {
        Boolean[] out = new Boolean[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }

    public static Float[] toBoxedArr(float[] arr) {
        Float[] out = new Float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }

    public static Byte[] toBoxedArr(byte[] arr) {
        Byte[] out = new Byte[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }

    public static int[] toPrimitiveInt(Collection<? extends Number> arr) {
        return arr.stream().mapToInt(Number::intValue).toArray();
    }

    public static double[] toPrimitiveDouble(Collection<? extends Number> arr) {
        return arr.stream().mapToDouble(Number::doubleValue).toArray();
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toPrimitive(List<T> arr) {
        return (T[]) arr.toArray();
    }


    public static boolean inBetween(List<Double> min, List<Double> max, List<Double> given) {
        return IntStream.range(0, given.size()).allMatch(i -> varOper(given.get(i), (v) -> v > min.get(i) && v < max.get(i)));
    }

    public static boolean inBetweenClosed(List<Double> min, List<Double> max, List<Double> given) {
        return IntStream.range(0, given.size()).allMatch(i -> varOper(given.get(i), (v) -> v >= min.get(i) && v <= max.get(i)));
    }

    public static boolean inBetween(double[] min, double[] max, double[] given) {
        return IntStream.range(0, given.length).allMatch(i -> varOper(given[i], (v) -> v > min[i] && v < max[i]));
    }

    public static boolean inBetweenClosed(double[] min, double[] max, double[] given) {
        return IntStream.range(0, given.length).allMatch(i -> varOper(given[i], (v) -> v >= min[i] && v <= max[i]));
    }

    public static double[] toDoubleArr(int[] arr) {
        return StreamEx.unboxInt(CollectionsEx.toBoxedList(arr)).mapToDouble(i -> i).toArray();
    }

    public static double mapDifference(TreeMap<Number, Number> map1, TreeMap<Number, Number> map2) {
        List<Number> k1 = keyList(map1), k2 = keyList(map2);
        return varMutate(new ArrayList<>(), (devs) -> new ForIncrement(0, Math.min(map1.size(), map2.size()), 1).execute((i, loop) -> devs.add(Math.abs(MathEx.subtract(map1.get(k1.get(i.intValue())), map2.get(k2.get(i.intValue()))))))).stream().mapToInt(i -> ((Double) i).intValue()).average().orElseThrow();
    }

    public static <K extends Number, V extends Number> Map<K, Double> mapStandardDevs(Map<K, V> map) {
        return varOper(
                average(map.values()),
                (mean) -> map.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Math.abs(MathEx.subtract(mean, entry.getValue()))
                ))
        );
    }

    public static <L extends Collection<? extends Number>> double average(L list) {
        return StreamEx.average(list.stream());
    }

    public static double average(Set<? extends Number> list) {
        return StreamEx.average(list.stream());
    }

    public static double mad(List<? extends Number> list) {
        return varOper(average(list), avg -> StreamEx.average(list.stream().map(n -> Math.abs(MathEx.subtract(n, avg)))));
    }

    public static <T> Map<Integer, T> listToNumberedMap(List<T> list) {
        return varMutate(new HashMap<>(), (map) -> new ForIncrement(0, list.size(), 1).execute((i, loop) -> map.put(i.intValue(), list.get(i.intValue()))));
    }

    public static <T, V> V firstValue(TreeMap<T, V> map) {
        return map.get(map.firstKey());
    }

    public static <T> List<T> listLimit(List<T> list, int idx) {
        return listSlice(list, list.size() < idx ? 0 : idx - list.size());
    }

    public static <T, V, M extends SortedMap<T, V>> M mapLimit(M map, int idx) {
        return mapSlice(map, map.size() < idx ? 0 : idx - map.size());
    }

    public static <K extends Number, V> Optional<K> closestKey(Map<K, V> map, K key) {
        return closestElem(new ArrayList<>(map.keySet()), key);
    }

    public static <T, L extends Collection<T>> List<T> listDiff(L list1, L list2) {
        return Stream.concat(
                list1.stream().filter(e -> !list2.contains(e)),
                list2.stream().filter(e -> !list1.contains(e))
        ).distinct().toList();
    }

    public static <T, L extends Collection<T>> List<T> listLeftDiff(L list1, L list2) {
        return list2.stream().filter(e -> !list1.contains(e)).toList();
    }

    public static double listDifference(List<? extends Number> list1, List<? extends Number> list2) {
        return varOper(Math.min(list1.size(), list2.size()), length ->
                IntStream.range(0, length)
                        .mapToDouble(i -> Math.abs(MathEx.subtract(list1.get(i), list2.get(i))))
                        .reduce(0.0, Double::sum) / length);
    }

    public static <L extends Collection<? extends Number>> List<Double> listStandardDevs(L list) {
        return varOper(
                average(list),
                (mean) -> list.stream().map(e -> Math.abs(MathEx.subtract(mean, e))).toList()
        );
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> flattenList(Object list, List<T> result) {
        if (list instanceof List<?>) {
            for (Object element : (List<?>) list) flattenList(element, result);
        } else {
            result.add((T) list);
        }
        return result;
    }

    public static int minInt(Object list) {
        return flattenList(list, new ArrayList<Integer>()).stream().min(Comparator.naturalOrder()).orElseThrow();
    }
    public static <T extends Number, L extends Collection<T>> T minNum(L list) {
        return list.stream().min(Comparator.comparingDouble(Number::doubleValue)).orElseThrow();
    }

    public static int maxInt(Object list) {
        return flattenList(list, new ArrayList<Integer>()).stream().max(Comparator.naturalOrder()).orElseThrow();
    }
    public static <T extends Number, L extends Collection<T>> T maxNum(L list) {
        return list.stream().max(Comparator.comparingDouble(Number::doubleValue)).orElseThrow();
    }

    public static <T> List<T> copy(List<T> list) {
        return varMutate(new ArrayList<>(list.size()), out -> copy(out, list));
    }
    public static <T> void copy(List<T> copyTo, List<T> list) {
        for (T t : list) copyTo.add(t == null ? null : ConstructionEx.Clone(t));
    }
    public static <K, V, M extends Map<K, V>> M copy(M map, Supplier<M> supplier) {
        return ProgrammingEx.varMutate(supplier.get(), newMap -> newMap.putAll(map));
//        return noExcept(() -> (M) (map.getClass().getConstructor(Map.class).newInstance(map)));
    }

    // make it so that the list does not have to be converted to a primitive
    // every time for performance
    public static <T extends Integer, L extends List<T>> int binarySearch(L list, int target) {
        int left = 0;
        int right = list.size() - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;

            int midValue = list.get(mid);

            if (midValue == target) {
                return mid;
            } else if (midValue < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return -1;
    }


    public static <T> int binarySearchBds(
            List<T> arr,
            int key,
            int startIndex,
            ToIntFunction<T> getLower,
            ToIntFunction<T> getUpper,
            BiPredicate<T, Integer> contains
    ) {
        int n = arr.size();
        if (n == 0) return -1;

        if (startIndex < 0) startIndex = 0;
        else if (startIndex >= n) startIndex = n - 1;

        T bds = arr.get(startIndex);
        if (contains.test(bds, key)) {
            return startIndex;
        }

        int low, high;
        if (key > getUpper.applyAsInt(bds)) {
            low  = startIndex + 1;
            high = n - 1;
        } else if (key < getLower.applyAsInt(bds)) {
            low  = 0;
            high = startIndex - 1;
        } else {
            return -1;
        }

        while (low <= high) {
            int mid = MathEx.midpoint(low, high);
            T elem = arr.get(mid);

            if (contains.test(elem, key)) {
                return mid;
            }
            if (key > getUpper.applyAsInt(elem)) {
                low = mid + 1;
            } else if (key < getLower.applyAsInt(elem)) {
                high = mid - 1;
            } else {
                return -1;
            }
        }

        return -1;
    }

    public static int binarySearchBds(List<IntegerBounds> arr, int target, int startIndex) {
        return binarySearchBds(
                arr,
                target,
                startIndex,
                IntegerBounds::getLowerBound,
                IntegerBounds::getUpperBound,
                IntegerBounds::inBetweenClosed
        );
    }

    public static int binarySearchBds(List<IntegerBounds> arr, int target) {
        return binarySearchBds(arr, target, arr.size() / 2);
    }

    public static int binarySearchLine(List<Line> arr, int target, int startIndex) {
        return binarySearchBds(
                arr,
                target,
                startIndex,
                Line::startX,
                Line::endX,
                Line::containsX
        );
    }

    public static int binarySearchLine(List<Line> arr, int target) {
        return binarySearchLine(arr, target, arr.size() / 2);
    }



    public static int[] closestBinarySearchBds(List<IntegerBounds> arr, int target, int startIndex) {
        int n = arr.size();
        if (startIndex >= n) startIndex = n - 1;
        if (startIndex < 0) startIndex = 0;

        IntegerBounds bds = arr.get(startIndex);
        int low, high;
        if (target > bds.getUpperBound()) {
            low = startIndex + 1;
            high = n - 1;
        } else if (target < bds.getLowerBound()) {
            low = 0;
            high = startIndex - 1;
        } else {
            return new int[]{startIndex};
        }

        while (low <= high) {
            int mid = MathEx.midpoint(low, high);
            IntegerBounds bdsHere = arr.get(mid);
            if (target > bdsHere.getUpperBound()) {
                low = mid + 1;
            } else if (target < bdsHere.getLowerBound()) {
                high = mid - 1;
            } else {
                return new int[]{mid};
            }
        }

        return SwitchIf.ofDefaultVal(
                new int[] { high, low },
                ConditionalCase.of(low >= n, new int[] { n - 1 }),
                ConditionalCase.of(low == -1, new int[] { high  }),
                ConditionalCase.of(high == -1, new int[] { low })
        ).evaluate();
    }


    public static <K, V, M extends SortedMap<K, V>> Optional<Map.Entry<K, V>> headQual(M map, K past, Predicate<Map.Entry<K, V>> qualFn) {
        return map.headMap(past).entrySet().stream().filter(qualFn).findFirst();
    }
    public static <K, V, M extends SortedMap<K, V>> Optional<Map.Entry<K, V>> tailQual(M map, K from, Predicate<Map.Entry<K, V>> qualFn) {
        return map.tailMap(from).entrySet().stream().filter(qualFn).findFirst();
    }

    public static <K, V, M extends NavigableMap<K, V>> V lowerValue(M map, K key) {
        return safeNull(() -> map.get(map.lowerKey(key)));
    }
    public static <K, V, M extends NavigableMap<K, V>> V higherValue(M map, K key) {
        return safeNull(() -> map.get(map.higherKey(key)));
    }

    public static <T, L extends Collection<T>> boolean idxWithin(L list, int idx) {
        return MathEx.bounded(idx, 0, list.size() - 1);
    }

    public static <L extends Collection<?>> int capacity(L list) {
        return Optional.ofNullable(noExcept(() -> ((Object[]) ReflectionEx.getField(list, "elementData")).length)).orElse(0);
    }



    public static <T extends Collection<V>, U extends Collection<V>, V extends Number> double minDist(T a, U b) {
        if (a.isEmpty() || b.isEmpty()) return Double.POSITIVE_INFINITY;

        List<Double> listA = a.stream().map(Number::doubleValue).sorted().toList(),
                listB = b.stream().map(Number::doubleValue).sorted().toList();

        int i = 0, j = 0;
        int na = listA.size(), nb = listB.size();
        double best = Double.POSITIVE_INFINITY;

        while (i < na && j < nb) {
            double va = listA.get(i);
            double vb = listB.get(j);
            double diff = Math.abs(va - vb);
            if (diff < best) best = diff;
            if (diff == 0.0) return 0.0;
            if (va < vb) i++; else j++;
        }

        return best;
    }

    public static int minDist(IntList a, IntList b) {
        int i = 0, j = 0;
        int na = a.size(), nb = b.size();
        if (na == 0 || nb == 0) return Integer.MAX_VALUE;
        int best = Integer.MAX_VALUE;
        while (i < na && j < nb) {
            int va = a.get(i);
            int vb = b.get(j);
            int diff = va - vb;
            int abs = diff < 0 ? -diff : diff;
            if (abs < best) best = abs;
            if (diff == 0) return 0;
            if (diff < 0) i++; else j++;
        }
        return best;
    }

    public static <T extends Number> List<T> filterOutliers(Collection<T> in) {
        IntegerBounds b = MathEx.iqrFences(in);
        if (b.equals(0, 0)) return new ArrayList<>(in);
        return StreamEx.toArrayList(in.stream().filter(Objects::nonNull).filter(n -> b.inBetweenClosed(n.doubleValue())));
    }


}

