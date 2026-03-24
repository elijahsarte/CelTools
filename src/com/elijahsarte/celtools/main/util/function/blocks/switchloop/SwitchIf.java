package com.elijahsarte.celtools.main.util.function.blocks.switchloop;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import static com.elijahsarte.celtools.main.util.ProgrammingEx.toList;

public class SwitchIf<T> {

    private final List<ConditionalCase<T>> conds;
    private final ConditionalCase<T> defaultCase;

    private SwitchIf(List<ConditionalCase<T>> conds, ConditionalCase<T> defaultCase) {
        this.conds = conds;
        this.defaultCase = defaultCase;
    }
    private SwitchIf(List<ConditionalCase<T>> conds) {
        this(conds, ConditionalCase.ofBlock(true, () -> {}));
    }


    public static <T> SwitchIf<T> of(List<ConditionalCase<T>> conds, ConditionalCase<T> defaultCase) {
        return new SwitchIf<>(conds, defaultCase);
    }
    public static <T> SwitchIf<T> of(List<ConditionalCase<T>> conds, T defaultCase) {
        return of(conds, ConditionalCase.of(true, defaultCase));
    }
    public static <T> SwitchIf<T> of(List<ConditionalCase<T>> conds) {
        return new SwitchIf<>(conds);
    }
    @SafeVarargs
    public static <T> SwitchIf<T> ofDefault(ConditionalCase<T> defaultCase, ConditionalCase<T>... conds) {
        return of(toList(conds), defaultCase);
    }
    @SafeVarargs
    public static <T> SwitchIf<T> of(ConditionalCase<T>... conds) {
        return of(toList(conds));
    }
    @SafeVarargs
    public static <T> SwitchIf<T> ofDefault(Supplier<T> defaultCase, ConditionalCase<T>... conds) {
        return of(toList(conds), ConditionalCase.of(() -> true, defaultCase));
    }
    @SafeVarargs
    public static <T> SwitchIf<T> ofDefaultVal(T defaultCase, ConditionalCase<T>... conds) {
        return ofDefault(() -> defaultCase, conds);
    }
    @SafeVarargs
    public static <T> SwitchIf<T> ofDefaultBlock(Runnable defaultCase, ConditionalCase<T>... conds) {
        return of(Arrays.stream(conds).toList(), ConditionalCase.ofBlock(true, defaultCase));
    }


    public T evaluate() {
        for (ConditionalCase<T> cond : conds) {
            if (cond.is()) {
                if (cond.canGet()) return cond.get();
                if (cond.canRun()) {
                    cond.run();
                    return null;
                }
                throw new IllegalStateException("Matching conditional case cannot be ran or retrieved");
            }
        }
        defaultCase.is();
        if (defaultCase.canGet()) return defaultCase.get();
        if (defaultCase.canRun()) {
            defaultCase.run();
            return null;
        }
        return null;
    }

}

