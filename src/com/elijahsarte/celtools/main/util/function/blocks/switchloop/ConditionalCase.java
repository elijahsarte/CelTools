package com.elijahsarte.celtools.main.util.function.blocks.switchloop;

import java.util.function.Supplier;

public class ConditionalCase<T> {

    private final Supplier<Boolean> cond;
    private Supplier<T> value;
    private Runnable block;
    
    private boolean consumed = false;


    private ConditionalCase(Supplier<Boolean> cond, Supplier<T> value) {
        this.cond = cond;
        this.value = value;
    }
    private ConditionalCase(Supplier<Boolean> cond, Runnable block) {
        this.cond = cond;
        this.block = block;
    }

    
    public static <T> ConditionalCase<T> of(boolean cond, T value) {
        return new ConditionalCase<>(() -> cond, () -> value);
    }
    public static <T> ConditionalCase<T> of(Supplier<Boolean> cond, T value) {
        return new ConditionalCase<>(cond, () -> value);
    }
    public static <T> ConditionalCase<T> of(boolean cond, Supplier<T> value) {
        return new ConditionalCase<>(() -> cond, value);
    }
    public static <T> ConditionalCase<T> of(Supplier<Boolean> cond, Supplier<T> value) {
        return new ConditionalCase<>(cond, value);
    }
    public static <T> ConditionalCase<T> ofBlock(boolean cond, Runnable block) {
        return new ConditionalCase<>(() -> cond, block);
    }
    public static <T> ConditionalCase<T> ofBlock(Supplier<Boolean> cond, Runnable block) {
        return new ConditionalCase<>(cond, block);
    }
    
    
    
    public boolean is() {
        this.consumed = true;
        return this.cond.get();
    }
    private void stateCheck() {
        if (!this.consumed) throw new IllegalStateException("Cannot get value when not evaluated");
    }
    public T get() {
        stateCheck();
        if (this.value == null) throw new IllegalStateException("Cannot get value when case is runnable");
        return this.value.get();
    }
    public void run() {
        stateCheck();
        if (this.block == null) throw new IllegalStateException("Cannot run event when case contains value");
        this.block.run();
    }
    public boolean canGet() {
        return this.block == null;
    }
    public boolean canRun() {
        return this.value == null;
    }

}

