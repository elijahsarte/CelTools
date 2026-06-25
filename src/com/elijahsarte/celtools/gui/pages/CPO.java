package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.CelPartOverlay;
import com.elijahsarte.celtools.main.Main;

import java.util.List;
import java.util.function.Supplier;

public class CPO extends ProgramPage {

    @Override
    public String name() {
        return "CelPartOverlay";
    }
    @Override
    public List<ProgramArg> args() {
        return ProgramArg.ofAll(
                "Background", "background-cel", "file",
                "All cel part(s)", "all-cels", "file",
                "Output cel(s)", "output-cel", "file",
                "How many files at once?", "files-at-once", "text"
        );
    }
    @Override
    public Supplier<? extends Main> program() {
        return () -> new CelPartOverlay(programArgs());
    }

}
