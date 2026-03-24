package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.CelPaintCombine;
import com.elijahsarte.celtools.main.Main;

import java.util.List;
import java.util.function.Supplier;

public final class CPCO extends ProgramPage {

    @Override
    public String name() {
        return "CelPaintCombine";
    }
    @Override
    public List<ProgramArg> args() {
        return ProgramArg.ofAll(
                "Base cel", "base-cel", "file",
                "Folder containing all cels", "all-cels-folder", "directory",
                "Output cel", "output-cel", "file",
                "Load dumped border", "load-dumped-border", "checkbox"
        );
    }

    @Override
    public Supplier<? extends Main> program() {
        return () -> new CelPaintCombine(programArgs());
    }

}

