package com.elijahsarte.celtools.main.util;

import com.elijahsarte.celtools.main.util.function.fntypes.ThrowableSupplier;
import com.elijahsarte.celtools.main.util.typeex.nullable.NullableBoolean;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class OptionalEx<T> {

    private final T value;
    private Predicate<T> cond;
    private final NullableBoolean condRes = new NullableBoolean();


    private OptionalEx(T value, boolean cond) {
        this.value = value;
        this.condRes.set(cond);
    }
    private OptionalEx(T value, Predicate<T> cond) {
        this.value = value;
        this.cond = cond;
    }
    /*
    private OptionalEx(T value, boolean... conds) {
        this.value = value;
        this.conds = StreamEx.ofBoolean(conds).map(cond -> (t) -> cond);
    }*/

    private boolean condTrue(T value) {
        if (!condRes.isSet()) condRes.set(cond.test(value));
        return condRes.get();
    }

    public boolean get() {
        return condTrue(value);
    }
    public T getVal() {
        if (!condRes.isSet()) throw new IllegalStateException("Condition must be consumed before value may be retrieved");
        return value;
    }

    public T orElse(T otherVal) {
        return condTrue(otherVal) ? otherVal : value;
    }
    public T orElseVal(T otherVal) {
        return condTrue(value) ? value : otherVal;
    }
    @SafeVarargs
    public final T orElse(Function<T, T>... otherApplys) {
        return condTrue(value) ? ProgrammingEx.varOper(value, otherApplys) : value;
    }
    @SafeVarargs
    public final void orElse(Consumer<T>... otherFns) {
        if (!condTrue(value)) Stream.of(otherFns).forEach(fn -> fn.accept(value));
    }
    public T orElse(Supplier<T> fn) {
        return condTrue(value) ? value : fn.get();
    }
    public void orElse(Runnable... otherFns) {
        if (!condTrue(value)) Stream.of(otherFns).forEach(Runnable::run);
    }


    public <V> OptionalEx<V> then(Function<T, V> fn) {
        return condTrue(value) ? new OptionalEx<>(fn.apply(value), true) : empty();
    }
    public <V> OptionalEx<V> then(Supplier<V> fn) {
        return condTrue(value) ? new OptionalEx<>(fn.get(), true) : empty();
    }
    public OptionalEx<?> then(Object val) {
        return condTrue(value) ? new OptionalEx<>(val, true) : this;
    }
    public OptionalEx<T> thenRun(Consumer<T> fn) {
        if (condTrue(value)) { fn.accept(value); } return this;
    }
    @SafeVarargs
    public final OptionalEx<T> thenRun(Consumer<T>... fns) {
        if (condTrue(value)) { Arrays.stream(fns).forEach(fn -> fn.accept(value)); } return this;
    }
    public OptionalEx<T> thenRun(Runnable fn) {
        if (condTrue(value)) { fn.run(); } return this;
    }
    public OptionalEx<T> thenRun(Runnable... fns) {
        if (condTrue(value)) { Arrays.stream(fns).forEach(Runnable::run); } return this;
    }


    public static <T> OptionalEx<T> ofCond(T t, Predicate<T> cond) {
        return new OptionalEx<>(t, cond);
    }
    public static <T> OptionalEx<T> ofCond(T t, boolean cond) {
        return new OptionalEx<>(t, cond);
    }
    public static OptionalEx<?> ofCond(boolean cond) {
        return new OptionalEx<>(new Object(), cond);
    }
    @SafeVarargs
    public static <T> OptionalEx<T> ofConds(T t, Predicate<T>... conds) {
        return new OptionalEx<>(t, (tv) ->
            Arrays.stream(conds).allMatch(c -> c.test(tv))
        );
    }
    /*public static <T> OptionalEx<T> ofConds(T t, boolean... conds) {
        return new OptionalEx<>(t, conds);
    }*/

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> OptionalEx<T> ofExcept(T t, Supplier<T>... excepts) {
        return OptionalEx.ofConds(t, (Predicate<T>[]) Arrays.stream(excepts).map(e -> (Predicate<T>) ((e1) -> { try { e.get(); return true; } catch (Exception h) { return false; } } )).toArray());
    }
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T, V> OptionalEx<T> ofExcept(T t, Function<T, V>... excepts) {
        return OptionalEx.ofConds(t, (Predicate<T>[]) Arrays.stream(excepts).map(e -> (Predicate<T>) ((e1) -> { try { e.apply(t); return true; } catch (Exception h) { return false; } } )).toArray());
    }

    public static <T> OptionalEx<T> ofExcept(ThrowableSupplier<T> t) {
        return OptionalEx.ofNonNullable(ProgrammingEx.noExcept(t));
    }

    public static <T> OptionalEx<T> ofNonNullable(T t) {
        return OptionalEx.ofCond(t, Objects::nonNull);
    }
    public static OptionalEx<Double> ofNaN(double t) {
        return OptionalEx.ofCond(t, v -> !Double.isNaN(v));
    }

    public static <T> OptionalEx<T> empty() {
        return new OptionalEx<>(null, false);
    }

}

