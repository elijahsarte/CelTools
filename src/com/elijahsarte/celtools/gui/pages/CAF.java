package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.CelAutoFill;
import com.elijahsarte.celtools.CelLayerPartSelect;
import com.elijahsarte.celtools.main.Main;

import java.util.List;
import java.util.function.Supplier;

public final class CAF extends ProgramPage {

    @Override
    public String name() {
        return "CelAutoFill";
    }
    @Override
    public List<ProgramArg> args() {
        return ProgramArg.ofAll(
                "Base cel", "base-cel", "file",
                "Folder containing all cels", "all-cels-folder", "directory",
                "Folder to output cels", "output-cels-folder", "directory",
                "Object border", "object-border", "text"
        );
    }
    @Override
    public Supplier<? extends Main> program() {
        return (() -> new CelAutoFill(programArgs()));
    }
}

