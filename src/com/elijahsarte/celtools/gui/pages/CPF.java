package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.CelPaintFill;
import com.elijahsarte.celtools.main.Main;

import java.util.List;
import java.util.function.Supplier;

public final class CPF extends ProgramPage {

    @Override
    public String name() {
        return "CelPaintFill";
    }
    @Override
    public List<ProgramArg> args() {
        return ProgramArg.ofAll(
                "Cel", "cel", "file",
                "Paint specimen", "paint-specimen", "file",
                "Paint image", "paint-image", "file",
                "Output cel", "output-cel", "file"
        );
    }
    @Override
    public Supplier<? extends Main> program() {
        return () -> new CelPaintFill(programArgs());
    }

}
