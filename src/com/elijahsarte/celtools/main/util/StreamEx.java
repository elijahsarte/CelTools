package com.elijahsarte.celtools.main.util;

import com.elijahsarte.celtools.main.util.function.fntypes.HexaConsumer;
import com.elijahsarte.celtools.main.util.function.fntypes.QuadConsumer;
import com.elijahsarte.celtools.main.util.function.fntypes.TriConsumer;
import com.elijahsarte.celtools.main.util.function.fnvals.BiTuple;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;
import com.elijahsarte.celtools.main.util.structures.collections.IntList;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.*;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;

public final class StreamEx {

    public static <T> Stream<T> of(Iterator<T> it) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, 0), false);
    }

    public static <K, V, T extends Map<K, V>> T toMap(Stream<Map.Entry<K, V>> stream, BinaryOperator<V> mergeFunction, Supplier<T> mapFactory) {
        return stream.collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        mergeFunction,
                        mapFactory
                ));
    }
    public static <K, V, T extends Map<K, V>> T toMap(Stream<Map.Entry<K, V>> stream, Supplier<T> mapFactory) {
        return StreamEx.toMap(stream, (a, b) -> a, mapFactory);
    }
    public static <K, V> Map<K, V> toMap(Stream<Map.Entry<K, V>> stream) {
        return StreamEx.toMap(stream, HashMap::new);
    }

    // returns mutable collection instead of immutable like toList() does
    public static <V> List<V> toArrayList(Stream<V> stream) {
        return stream.collect(Collectors.toCollection(ArrayList::new));
    }


    public static <T> Stream<T> reverseSkip(Stream<T> stream, Comparator<? super T> flipper, long skipLen) {
        return stream.sorted(flipper).skip(skipLen).sorted(flipper);
    }
    public static <T> Stream<T> reverseSkip(Stream<T> stream, long skipLen) {
        return StreamEx.reverseSkip(stream, Collections.reverseOrder(), skipLen);
    }

    public static <T> T first(Stream<T> stream) {
        return varOper(new AtomicInteger(0), index -> stream.takeWhile(elem -> index.getAndIncrement() >= 1).findFirst().orElseThrow());
//        return (T) varMutate(new AtomicReference<>(), first -> stream.peek(e -> first.compareAndSet(null, e))).get();
    }
    public static <T> T last(Stream<T> stream) {
        return varMutate(new AtomicReference<T>(), last -> stream.forEach(last::set)).get();
    }


    public static <T> Stream<T> indexFilter(List<T> list, IntPredicate filter) {
        return IntStream.range(0, list.size()).filter(filter).mapToObj(list::get);
    }
    public static <T> Stream<T> indexFilter(Stream<T> list, IntPredicate filter) {
        return StreamEx.indexFilter(list.toList(), filter);
    }

    // https://stackoverflow.com/a/63599591
    public static Stream<Boolean> ofBoolean(boolean[] boolArr) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new Iterator<>() {
                            int index = 0;
                            @Override public boolean hasNext() { return index < boolArr.length; }
                            @Override public Boolean next() { return boolArr[index++]; }
                        }, 0), false);
    }

    public static IntStream unboxInt(Collection<Integer> list) {
        return list.stream().mapToInt(Integer::intValue);
    }
    public static DoubleStream unboxDouble(Collection<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue);
    }



    public static IntList toIntList(Stream<Integer> stream) {
        return ProgrammingEx.varMutate(new IntList(), iL -> stream.forEach(iL::add));
    }

    public static <T, V> List<V> generalMap(List<T> list, Function<T, V> map) {
        return list.stream().map(map).toList();
    }

    public static <T extends Number> double average(Stream<T> stream) {
        return stream.mapToDouble(Number::doubleValue).average().orElse(0);
    }

    public static <T> void forEachInc(List<T> list, BiConsumer<T, Integer> forEach) {
        IntStream.range(0, list.size()).forEach(i -> forEach.accept(list.get(i), i));
    }
    @SafeVarargs
    public static <T> void forEachMulti(Stream<T> stream, Consumer<T>... fns) {
        stream.forEach(s -> Arrays.stream(fns).forEach(f -> f.accept(s)));
    }

    public static IntStream intsBetween(double start, double end) {
        return IntStream.rangeClosed(MathEx.floorInt(start), MathEx.floorInt(end));
    }
    public static IntStream percentStream(int start, int end, double startPercent, double endPercent) {
        return IntStream.rangeClosed((int) (start + ((end - start)*startPercent)), (int) (start + ((end - start)*endPercent)));
    }
    public static IntStream percentStream(double length, double startPercent, double endPercent) {
        return IntStream.rangeClosed((int) (startPercent * length), (int) (endPercent * length));
    }
    public static IntStream percentStream(double length, double percent) {
        return percentStream(length, 0, percent);
    }

    public static IntStream modRange(int input, int mod) {
        return IntStream.rangeClosed(MathEx.limMod(input, mod), MathEx.nextMod(input, mod));
    }

    public static <T> void forEachNeighbouring(List<T> list, TriConsumer<T, T, T> fn) {
        IntStream.range(0, list.size()).forEach(i -> fn.accept((i > 0) ? list.get(i - 1) : null, list.get(i), (i < list.size() - 1) ? list.get(i + 1) : null));
    }

    public static <K, V> void forEachNeighbouring(TreeMap<K, V> map, QuadConsumer<Map.Entry<K, V>, K, V, Map.Entry<K, V>> fn) {
        List<Map.Entry<K, V>> entries = new ArrayList<>(map.entrySet());
        IntStream.range(0, entries.size())
                .forEach(i ->
                    varExec(entries.get(i), curr -> fn.accept(
                            (i > 0) ? entries.get(i - 1) : null,
                            curr.getKey(), curr.getValue(),
                            (i < entries.size() - 1) ? entries.get(i + 1) : null))
                );
    }
    public static <K, V> void forEachNeighbouring(TreeMap<K, V> map, HexaConsumer<K, V, K, V, K, V> fn) {
        forEachNeighbouring(map, (prev, k, v, next) -> fn.accept(prev == null ? null : prev.getKey(), prev == null ? null : prev.getValue(), k, v, next == null ? null : next.getKey(), next == null ? null : next.getValue()));
    }

    public static <T, S extends NavigableSet<T>> void forEach(S set) {

    }

    public static <T> boolean contains(Stream<T> stream, T obj) {
        return stream.anyMatch(obj::equals);
    }
    public static <T> int indexOf(Stream<T> stream, T obj) {
        return varOper(new AtomicInteger(-1), i1 -> varMutate(new AtomicInteger(), i -> stream.peek(s -> {
            if (i1.get() != -1) return;
            i.incrementAndGet();
            if (s.equals(obj)) i1.set(i.get());
        }))).get();
    }

    // Extended stream operations
    public static <T> boolean empty(Stream<T> stream) {
        return stream.findAny().isEmpty();
    }
    public static <T> Stream<T> reverse(Stream<T> stream) {
        Stream.Builder<T> builder = Stream.builder();
        while (!nonTerminatingEmpty(stream)) {
            stream = stream.skip(1);
            builder.add(StreamEx.first(stream));
        }
        return builder.build();
    }

    // Non-terminating stream operations
    public static <T extends Number> BiTuple<Double, Long> statsData(Stream<T> stream) {
        AtomicLong count = new AtomicLong(0l);
        AtomicReference<Double> sum = new AtomicReference<>(0d);
        stream.peek(n -> {
            sum.set(sum.get() + n.doubleValue());
            count.incrementAndGet();
        });
        return new BiTuple<>(sum.get(), count.get());
    }
    public static <T> long nonTerminatingCount(Stream<T> stream) {
       return varMutate(new AtomicLong(), i -> stream.peek(e -> i.incrementAndGet())).get();
    }
    public static <T> boolean nonTerminatingEmpty(Stream<T> stream) {
        return varOper(new AtomicBoolean(), b -> stream.takeWhile(elem -> !b.getAndSet(true))).findAny().isPresent();
    }
    public static <T extends Number> double nonTerminatingAverage(Stream<T> stream) {
        return varOper(statsData(stream), n -> MathEx.divide(n.first(), n.second()));
    }

    // Statistics
    public static <T extends Number> Stream<T> filterOutliers(Stream<T> s) {
        List<T> list = s.toList();
        IntegerBounds b = MathEx.iqrFences(list);
        if (b.equals(0, 0)) return list.stream();
        return list.stream().filter(x -> b.inBetweenClosed(x.doubleValue()));
    }


}

