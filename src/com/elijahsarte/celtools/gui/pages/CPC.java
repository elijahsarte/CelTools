package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.CelPaintCrop;
import com.elijahsarte.celtools.main.Main;

import java.util.List;
import java.util.function.Supplier;

public final class CPC extends ProgramPage {

    @Override
    public String name() {
        return "CelPaintCrop";
    }
    @Override
    public List<ProgramArg> args() {
        return ProgramArg.ofAll(
                "Entire cel(s)", "all-cels", "file",
                "Background cel", "background-cel", "file",
                "Output cel(s)", "output-cel", "file",
                "Automatically detect object border", "autoobjectborder", "checkbox",
                "Lower color bound", "lower-color-bound", "rgb",
                "Upper color bound", "upper-color-bound", "rgb",
                "Object border", "object-border", "text",
                "Dump border", "dump-border", "checkbox"
        );
    }

    @Override
    public Supplier<? extends Main> program() {
        return () -> new CelPaintCrop(programArgs());
    }

}

