package com.elijahsarte.celtools.main.util;

import java.util.function.Consumer;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.noExcept;
import static com.elijahsarte.celtools.main.util.ReflectionEx.*;

public class ConstructionEx {

    public static String fastToString(byte[] buf, int length) {
        String s = construct(String.class);
        noExcept(() -> setField(s, "value", buf));
        if (hasField(s, "count")) noExcept(() -> setField(s, "count", length));
        return s;
    }
    public static String fastToString(byte[] buf) {
        return fastToString(buf, buf.length);
    }

    public static String fastToString(CharSequence cs) {
        return hasField(cs, "value") ? fastToString(noExcept(() -> getField(cs, "value")), cs.length()) : null;
    }

    public static <T> T mutateCopy(T t, Consumer<T> mutator) {
        return ProgrammingEx.varMutate(Clone(t), mutator);
    }

    @SuppressWarnings("unchecked")
    public static <T> T Clone(T t) {
        return (T) ReflectionEx.callMethod(t, "clone");
    }
}

