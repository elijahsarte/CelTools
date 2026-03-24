package com.elijahsarte.celtools.main.util.function.blocks.switchloop;

import java.util.function.Supplier;

public class ValCase<T, V> {

    private final Supplier<T> val;
    private final Supplier<V> returnVal;

    private boolean consumed = false;

    private ValCase(Supplier<T> val, Supplier<V> returnVal) {
        this.val = val;
        this.returnVal = returnVal;
    }
    private ValCase(T val, V returnVal) {
        this(() -> val, () -> returnVal);
    }

    public static <T, V> ValCase<T, V> of(T val, V returnVal) {
        return new ValCase<>(val, returnVal);
    }
    public static <T, V> ValCase<T, V> of(Supplier<T> val, Supplier<V> returnVal) {
        return new ValCase<>(val, returnVal);
    }
    public static <T, V> ValCase<T, V> of(T val, Supplier<V> returnVal) {
        return new ValCase<>(() -> val, returnVal);
    }
    public static <T, V> ValCase<T, V> of(Supplier<T> val, V returnVal) {
        return new ValCase<>(val, () -> returnVal);
    }

    public boolean compare(Object val) {
        this.consumed = true;
        return val.equals(this.val.get());
    }
    public V get() {
        if (!this.consumed) throw new IllegalStateException("Cannot get value before comparing it to another value");
        return returnVal.get();
    }

}

