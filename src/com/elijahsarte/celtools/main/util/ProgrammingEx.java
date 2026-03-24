package com.elijahsarte.celtools.main.util;

import com.elijahsarte.celtools.main.util.function.fntypes.ThrowableSupplier;
import com.elijahsarte.celtools.main.util.function.fntypes.ThrowableRunnable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.IntStream;

public final class ProgrammingEx {

    public static final int INVALID_INT = Integer.MIN_VALUE;
    public static final double INVALID_DBL = Double.MIN_VALUE;
    public static final AtomicInteger INVALID_ATOMIC_INT = new AtomicInteger(INVALID_INT);
    public static final AtomicReference<Double> INVALID_ATOMIC_DBL = new AtomicReference<>(INVALID_DBL);

    public static <T, V> V varOper(T t, Function<T, V> fn) {
        return fn.apply(t);
    }
    @SafeVarargs
    public static <T> T varOper(T t, Function<T, T>... fns) {
        return Arrays.stream(fns)
                .reduce((f1, f2) -> f -> f2.apply(f1.apply(f)))
                .map(fn -> fn.apply(t))
                .orElseThrow();
    }
    public static <T, V> T varMutate(T t, Consumer<T> fn) {
        fn.accept(t);
        return t;
    }
    @SafeVarargs
    public static <T> T varMutate(T t, Consumer<T>... fns) {
        Arrays.stream(fns).forEach(fn -> fn.accept(t));
        return t;
    }
    public static <T> void varExecC(T t, Consumer<T> fn) {
        varMutate(t, fn);
    }
    @SafeVarargs
    public static <T> void varExec(T t, Consumer<T>... fns) {
        varMutate(t, fns);
    }


    @SafeVarargs
    public static <T> void multiExec(Consumer<T> fn, T... vars) {
        Arrays.stream(vars).forEach(fn);
    }

    public static void noExcept(ThrowableRunnable action) {
        try { action.run(); } catch (Exception ignored) {}
    }
    public static <T> T noExcept(ThrowableSupplier<T> fn) {
        try { return fn.get(); } catch (Exception e) { return null; }
    }

    public static <T, W extends Throwable> T tryCat(ThrowableSupplier<T> valFn, Function<Exception, W> catchFn) throws W{
        try {
            return valFn.get();
        } catch (Exception ex) {
            throw catchFn.apply(ex);
        }
    }
    public static <T> T tryCat(ThrowableSupplier<T> valFn) throws Exception {
        return valFn.get();
    }

    public static <T> T safeNull(Supplier<T> fn) {
        try { return fn.get(); } catch (NullPointerException ignored) { return null; }
    }

    public static void forNumber(Supplier<?> fn, int count) {
        IntStream.range(0, count).forEach(i -> fn.get());
    }
    public static void forNumber(Consumer<Integer> fn, int count) {
        IntStream.range(0, count).forEach(fn::accept);
    }
    public static <T> T forceReturn(T t, Supplier<?> fn) {
        fn.get(); return t;
    }
    public static <T> T forceReturn(T t, Runnable fn) {
        fn.run(); return t;
    }
    public static void noReturn(Supplier<?> fn) {
        fn.get();
    }
    public static <T> void noReturn(T t, Consumer<T> fn) {
        fn.accept(t);
    }
    public static <T> Consumer<T> toNoReturn(Supplier<T> fn) {
        return (t) -> fn.get();
    }
    public static <T> Consumer<T> noReturn(Function<T, ?> fn) {
        return (t) -> fn.apply(t);
    }


    public static Object nonNull(Object... objs) {
        return Arrays.stream(objs).filter(Objects::nonNull).findFirst().orElse(null);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T, V, W> V argCastCall(Function<T[], V> method, W... args) {
        return method.apply((T[]) args);
    }

    public static <T> void mutateEquals(T obj1, T obj2) {
        Arrays.stream(ReflectionEx.getFields(obj1)).filter(f -> ReflectionEx.hasField(obj2, f)).forEach(f ->
            ReflectionEx.setField(obj2, f, ReflectionEx.getField(obj1, f))
        );
    }

    public static AtomicInteger emptyAtomicInt() {
        return new AtomicInteger(0);
    }
    public static AtomicReference<Double> emptyAtomicDbl() {
        return new AtomicReference<>(0d);
    }

    public static boolean invalid(int num) {
        return num == Integer.MIN_VALUE || num == Integer.MAX_VALUE;
    }
    public static boolean invalid(double num) {
        return num == Double.MIN_VALUE || num == Double.MAX_VALUE;
    }
    public static boolean invalid(AtomicInteger num) {
        return anyEquals(num.get(), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    public static boolean invalid(AtomicReference<Double> num) {
        return anyEquals(num.get(), Double.MIN_VALUE, Double.MAX_VALUE);
    }
    public static void invalidAndSet(AtomicInteger num, int newValue) {
        num.compareAndSet(Integer.MIN_VALUE, newValue);
        num.compareAndSet(Integer.MAX_VALUE, newValue);
    }
    public static void invalidAndSet(AtomicReference<Double> num, double newValue) {
        num.compareAndSet(Double.MIN_VALUE, newValue);
        num.compareAndSet(Double.MAX_VALUE, newValue);
    }

    public static boolean truthy(int num) {
        return num == 1;
    }
    public static boolean truthy(Object obj) {
        return obj != null;
    }
    public static boolean looseTruthy(int num) {
        return num != 0;
    }

    public static Object multiEquals(Object obj1, Object... objs) {
        return Arrays.stream(objs).filter(obj1::equals).findFirst().orElse(false);
    }
    public static int multiEquals(int int1, int... ints) {
        return Arrays.stream(ints).filter(i -> i == int1).findFirst().orElse(Integer.MIN_VALUE);
    }
    public static double multiEquals(double dbl, double... dbls) {
        return Arrays.stream(dbls).filter(i -> i == dbl).findFirst().orElse(Double.MIN_VALUE);
    }
    public static boolean anyEquals(Object obj1, Object... objs) {
        return Arrays.asList(objs).contains(obj1);
    }

    @SafeVarargs
    public static <T> T firstQual(Predicate<T> qualifier, T... vals) {
        return Arrays.stream(vals).filter(qualifier).findFirst().orElse(vals[0]);
    }

    @SafeVarargs
    public static <T> List<T> toList(T... args) {
        return Arrays.stream(args).toList();
    }
    public static List<Double> toList(double... args) {
        return Arrays.stream(args).boxed().toList();
    }

    public static <T, V> V fnCall(Function<T, V> fn, T input) {
        return fn.apply(input);
    }

}
