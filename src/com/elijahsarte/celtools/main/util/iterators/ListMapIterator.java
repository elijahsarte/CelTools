package com.elijahsarte.celtools.main.util.iterators;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ListMapIterator<T, V> {

    private final Map<T, List<V>> listMap;

    public ListMapIterator(Map<T, List<V>> listMap) {
        this.listMap = listMap;
    }

    public void execute(BiConsumer<T, V> body) {
        listMap.forEach((key, vals) -> vals.forEach(val -> body.accept(key, val)));
    }

}

