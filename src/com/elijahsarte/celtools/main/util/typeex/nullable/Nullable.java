package com.elijahsarte.celtools.main.util.typeex.nullable;

import java.util.Objects;

public class Nullable<T> {

    protected T val = null;
    public Nullable(T val) {
        this.val = val;
    }
    public Nullable() {}

    public void set(T val) {
        this.val = val;
    }
    public void unset() {
        this.val = null;
    }
    public boolean isSet() {
        return this.val != null;
    }
    public T get() {
        return this.val;
    }

    public boolean equals(Object o) {
        return Objects.equals(o, this.val);
    }

    protected <N extends Nullable<?>> boolean validate(N nullable) {
        return nullable != null && nullable.isSet();
    }

}

