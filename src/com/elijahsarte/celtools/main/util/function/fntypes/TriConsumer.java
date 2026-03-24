package com.elijahsarte.celtools.main.util.function.fntypes;

@FunctionalInterface
public interface TriConsumer<A, B, C> {
    void accept(A a, B b, C c);
}

