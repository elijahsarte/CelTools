package com.elijahsarte.celtools.main.util.function.blocks.switchloop;


import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class SwitchExpEx<T, V> {

    private final List<ValCase<T, V>> vals;

    private SwitchExpEx(List<ValCase<T, V>> vals) {
        this.vals = vals;
    }

    public static <T, V> SwitchExpEx<T, V> of(List<ValCase<T, V>> cases) {
        return new SwitchExpEx<>(cases);
    }
    @SafeVarargs
    public static <T, V> SwitchExpEx<T, V> of(ValCase<T, V>... cases) {
        return of(Arrays.stream(cases).toList());
    }
    @SuppressWarnings("unchecked")
    public static <T, V> SwitchExpEx<T, V> of(Object... vals) {
        if (vals.length % 2 != 0) throw new IllegalArgumentException("Stray value found");
        return of(IntStream.range(0, vals.length / 2)
                .mapToObj(i -> ValCase.of((T) vals[2 * i], (V) vals[2 * i + 1]))
                .toList());
    }


    public V evaluate(Object val) {
        return this.vals.stream().filter(v -> v.compare(val)).findFirst().orElseThrow().get();
    }

}

