package com.elijahsarte.celtools.main.util.structures.collections;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class FixedList<E> implements Iterable<E> {

    private final E[] buffer;
    private final int capacity;
    private int head = 0, tail = 0, size = 0;

    @SuppressWarnings("unchecked")
    public FixedList(int capacity) {
        this.capacity = capacity;
        buffer = (E[]) new Object[capacity];
    }

    public void add(E e) {
        if (size == buffer.length) {
            head = (head + 1) % buffer.length;
            size--;
        }
        buffer[tail] = e;
        tail = (tail + 1) % buffer.length;
        size++;
    }

    public E get(int idx) {
        return buffer[size - (idx + 1)];
    }
    public E oldest() {
        return get(size() - 1);
    }

    public boolean remove(E e) {
        for (int i = 0; i < size; i++) {
            if (!Objects.equals(buffer[(head + i) % buffer.length], e)) {
                continue;
            }
            for (int j = i; j < size - 1; j++)
                buffer[(head + j) % buffer.length] = buffer[(head + j + 1) % buffer.length];
            int newTail = (tail - 1 + buffer.length) % buffer.length;
            buffer[newTail] = null;
            tail = newTail;
            size--;
        }
        return false;
    }

    public E poll() {
        if (size == 0) throw new NoSuchElementException();
        E e = buffer[head];
        head = (head + 1) % buffer.length;
        size--;
        return e;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int capacity() {
        return this.capacity;
    }
    public int size() {
        return size;
    }

    public Stream<E> stream() {
        return Arrays.stream(buffer);
    }

    @Override
    public Iterator<E> iterator() {
        return null;
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Iterable.super.forEach(action);
    }

    @Override
    public Spliterator<E> spliterator() {
        return Iterable.super.spliterator();
    }
}

