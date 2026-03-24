package com.elijahsarte.celtools.main.util.typeex.nullable;

import java.util.Objects;

public class NullableInt extends Nullable<Integer> {

    public NullableInt(Integer val) {
        super(val);
    }
    public NullableInt() {
        super();
    }

    public boolean equals(int other) {
        return isSet() && this.val == other;
    }
    public boolean equals(NullableInt other) {
        return isSet() && other.isSet() && Objects.equals(this.val, other.val);
    }
    public boolean greater(int other) {
        return isSet() && this.val > other;
    }
    public boolean greater(NullableInt other) {
        return validate(other) && greater(other.val);
    }
    public boolean greaterOrEqual(int other) {
        return isSet() && this.val >= other;
    }
    public boolean greaterOrEqual(NullableInt other) {
        return validate(other) && greaterOrEqual(other.val);
    }

}

