package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.CelInkXerox;
import com.elijahsarte.celtools.main.Main;

import java.util.List;
import java.util.function.Supplier;

public class CIX extends ProgramPage {

    @Override
    public String name() {
        return "CelInkXerox";
    }
    @Override
    public List<ProgramArg> args() {
        return ProgramArg.ofAll(
                "Pencil drawing(s)", "pencil-drawing", "file",
                "Ink color", "ink-color", "rgb",
                "Output cel(s)", "output-cel", "file",
                "Print?", "print", "checkbox"
        );
    }
    @Override
    public Supplier<? extends Main> program() {
        return () -> new CelInkXerox(programArgs());
    }

}
