package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.CelPaintMergeFill;
import com.elijahsarte.celtools.main.Main;

import java.util.List;
import java.util.function.Supplier;

public final class CPMF extends ProgramPage {

    @Override
    public String name() {
        return "CelPaintMergeFill";
    }
    @Override
    public List<ProgramArg> args() {
        return ProgramArg.ofAll(
                "Base cel", "base-cel", "file",
                "Color sample cel", "color-cel", "file",
                "Color sample core rect", "color-rect", "text",
                "Folder containing all cels", "all-cels-folder", "directory",
                "Output cel", "output-cel", "file",
                "Object border", "object-border", "text",
                "Color sample", "color-sample", "text",
                "Dump border", "dump-border", "checkbox"
        );
    }

    @Override
    public Supplier<? extends Main> program() {
        return (() -> new CelPaintMergeFill(programArgs()));
    }
}

