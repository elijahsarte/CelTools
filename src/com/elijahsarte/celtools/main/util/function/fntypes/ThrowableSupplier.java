package com.elijahsarte.celtools.main.util.function.fntypes;

@FunctionalInterface
public interface ThrowableSupplier<T> {
    T get() throws Exception;
}
