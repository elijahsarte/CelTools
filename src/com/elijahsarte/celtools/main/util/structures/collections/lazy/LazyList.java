package com.elijahsarte.celtools.main.util.structures.collections.lazy;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class LazyList<T, V> implements List<V> {

    private final List<T> list;
    private final Function<T, V> map;
    private final List<Function<V, V>> subsequentMaps;

    private LazyList(List<T> list, Function<T, V> map, List<Function< V, V>> subsequentMaps) {
        this.list = list;
        this.map = map;
        this.subsequentMaps = subsequentMaps;
    }
    public LazyList(List<T> list, Function<T, V> map) {
        this(list, map, new ArrayList<>());
    }

    public List<V> realize() {
        return stream().toList();
    }

    public void addMap(Function<V, V> fn) {
        subsequentMaps.add(fn);
    }

    private V applyMap(T elem) {
        V prev = map.apply(elem);
        for (Function<V, V> fn : subsequentMaps) prev = fn.apply(prev);
        return prev;
    }


    @Override
    public int size() {
        return list.size();
    }
    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }
    @Override
    public boolean contains(Object o) {
        return list.stream().anyMatch(t -> Objects.equals(applyMap(t), o));
    }
    @Override
    public Iterator<V> iterator() {
        Function<Integer, Boolean> removeParent = this::remove;
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size();
            }
            @Override
            public V next() {
                if (!hasNext()) throw new NoSuchElementException();
                return get(index++);
            }
            @Override
            public void remove() {
                removeParent.apply(index);
                index--;
            }
        };
    }

    public Stream<V> stream() {
        Stream<V> prev = list.stream().map(map);
        for (Function<V, V> fn : subsequentMaps) prev = prev.map(fn);
        return prev;
    }

    @Override
    public Object[] toArray() {
        return stream().toArray();
    }
    @Override
    public <T> T[] toArray(T[] a) {
        return null;
    }

    @Override
    public boolean add(V v) {
        throw new UnsupportedOperationException("Not supported yet");
    }
    @Override
    public boolean remove(Object o) {
        for (int i = 0; i < list.size(); i++) {
            if (o.equals(get(i))) {
                remove(i);
                return true;
            }
        }
        return false;
    }
    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public boolean addAll(int index, Collection<? extends V> c) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    public boolean equals(Object o) {
        // note: make it so that it can compare these two
//        if (o instanceof List oL) return false;
        return o instanceof LazyList oL && oL.list.equals(list) && oL.map.equals(map) && oL.subsequentMaps.equals(subsequentMaps);
    }

    @Override
    public int hashCode() {
        return list.hashCode() * map.hashCode();
    }

    public V get(int idx) {
        return applyMap(list.get(idx));
    }

    @Override
    public V set(int index, V element) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public void add(int index, V element) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public V remove(int index) {
        return applyMap(list.remove(index));
    }

    @Override
    public int indexOf(Object o) {
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(get(i), o)) return i;
        }
        return -1;
    }
    @Override
    public int lastIndexOf(Object o) {
        int latest = -1;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(get(i), o)) latest = i;
        }
        return latest;
    }

    @Override
    public ListIterator<V> listIterator() {
        return null;
    }
    @Override
    public ListIterator<V> listIterator(int index) {
        return null;
    }
    @Override
    public List<V> subList(int fromIndex, int toIndex) {
        return new LazyList<>(list.subList(fromIndex, toIndex), map);
    }

}

