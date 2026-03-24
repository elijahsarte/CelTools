package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.CelLayerPartSelect;
import com.elijahsarte.celtools.main.Main;

import java.util.List;
import java.util.function.Supplier;

public final class CLPP extends ProgramPage {

    @Override
    public String name() {
        return "CelLayerPartSelect";
    }
    @Override
    public List<ProgramArg> args() {
        return ProgramArg.ofAll(
                "Entire cel", "entire-cel", "file",
                "Background cel", "background-cel", "file",
                "Output cel", "output-cel", "file",
                "Outline data", "cropbox", "text"
        );
    }
    @Override
    public Supplier<? extends Main> program() {
        return () -> new CelLayerPartSelect(programArgs());
    }

}

