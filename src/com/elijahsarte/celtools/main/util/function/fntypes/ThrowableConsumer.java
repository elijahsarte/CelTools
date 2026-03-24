package com.elijahsarte.celtools.main.util.function.fntypes;

@FunctionalInterface
public interface ThrowableConsumer<T> {
    void accept(T t) throws Exception;
}

