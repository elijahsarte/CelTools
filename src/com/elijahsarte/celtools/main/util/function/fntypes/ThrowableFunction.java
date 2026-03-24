package com.elijahsarte.celtools.main.util.function.fntypes;

@FunctionalInterface
public interface ThrowableFunction<T, V> {
    V apply(T t) throws Exception;
}

