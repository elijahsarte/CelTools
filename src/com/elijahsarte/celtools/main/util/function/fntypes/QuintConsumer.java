package com.elijahsarte.celtools.main.util.function.fntypes;

@FunctionalInterface
public interface QuintConsumer<A, B, C, D, E> {
    void accept(A a, B b, C c, D d, E e);
}

