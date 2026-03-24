package com.elijahsarte.celtools.main.util.function.fntypes;

@FunctionalInterface
public interface QuadConsumer<A, B, C, D> {
    void accept(A a, B b, C c, D d);
}

