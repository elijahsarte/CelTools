package com.elijahsarte.celtools.main.util.function.fnvals;

import java.util.Map;

public class BiTuple<T, U> {

    private final T t1;
    private final U t2;

    public BiTuple(T t1, U t2) {
        this.t1 = t1;
        this.t2 = t2;
    }

    public T first() {
        return this.t1;
    }
    public U second() {
        return this.t2;
    }

    // shorthand
    public static <T, U> BiTuple<T, U> of(T t1, U t2) {
        return new BiTuple<>(t1, t2);
    }
    public static <T, U> BiTuple<T, U> of(Map.Entry<T, U> entry) {
        return of(entry.getKey(), entry.getValue());
    }

}

