package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.main.util.ProgrammingEx;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public record ProgramArg(String label, String argName, String type) {

    public static List<ProgramArg> ofAll(Object... args) {
        if (args.length % 3 != 0) throw new IllegalArgumentException("Arguments provided to ProgramArg.of must be divisible by three");

        List<ProgramArg> allConstructed = new ArrayList<>();
        Object[] currArgs = new Object[3];
        IntStream.rangeClosed(0, args.length).forEach(i -> {
            if (i != 0 && i % 3 == 0) allConstructed.add(ProgrammingEx.noExcept(() -> ProgramArg.class.getConstructor(String.class, String.class, String.class).newInstance(currArgs)));
            currArgs[i % 3] = args[Math.min(args.length - 1, i)];
        });
        return allConstructed;
    }
}

