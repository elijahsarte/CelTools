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
                "All cel(s)", "all-cels", "file",
                "Paint specimen", "paint-specimen", "file",
                "Paint image", "paint-image", "file",
                "Paint specimens by color", "paint-specimens", "map-rgb-file",
                "All painted cels", "all-painted-cels", "directory",
                "Output cel(s)", "output-cel", "file"
        );
    }
    @Override
    public Supplier<? extends Main> program() {
        return () -> new CelPaintFill(programArgs());
    }

}
