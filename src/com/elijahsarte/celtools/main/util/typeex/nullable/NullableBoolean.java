package com.elijahsarte.celtools.main.util.typeex.nullable;

public class NullableBoolean extends Nullable<Boolean> {

    public boolean isTrue() {
        return isSet() && val;
    }
    public boolean isFalse() {
        return isSet() && !val;
    }
}

