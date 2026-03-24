package com.elijahsarte.celtools.main.util.function.blocks.forloop;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.ThreadEx;
import com.elijahsarte.celtools.main.util.function.fntypes.TriConsumer;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class ForEach<T> extends ForLoopBase {

    private final Iterable<T> arr;

    public ForEach(List<T> arr) {
        this.arr = arr;
    }
    public ForEach(Set<T> arr) {
        this.arr = arr;
    }
    public ForEach(T[] arr) {
        this.arr = List.of(arr);
    }
    public ForEach(int[] arr) {
        this.arr = (List<T>) CollectionsEx.toBoxedList(arr);
    }
    public ForEach(double[] arr) {
        this.arr = (List<T>) CollectionsEx.toBoxedList(arr);
    }
    public ForEach(long[] arr) {
        this.arr = (List<T>) CollectionsEx.toBoxedList(arr);
    }
    public ForEach(boolean[] arr) {
        this.arr = (List<T>) CollectionsEx.toBoxedList(arr);
    }
    public ForEach(float[] arr) {
        this.arr = (List<T>) CollectionsEx.toBoxedList(arr);
    }

    public void executeThread(BiConsumer<T, ForEach<T>> body) {
        for (T elem : arr) {
            this.Continue = false;
            this.Break = false;

            this.currIter = CompletableFuture.supplyAsync(ThreadEx.toBooleanSupplier(() -> body.accept(elem, this)));
            ProgrammingEx.noExcept(() -> this.currIter.join());

            if (this.Continue) continue;
            if (this.Break) break;
        }
    }

    public void execute(BiConsumer<T, ForEach<T>> body) {
        for (T elem : arr) {
            this.Continue = false;
            this.Break = false;
            body.accept(elem, this);
            if (this.Continue) continue;
            if (this.Break) break;
        }
    }
    public void execute(TriConsumer<T, Integer, ForEach<T>> body) {
        int index = 0;
        for (T elem : arr) {
            this.Continue = false;
            this.Break = false;
            body.accept(elem, index++, this);
            if (this.Continue) continue;
            if (this.Break) break;
        }
    }



}

