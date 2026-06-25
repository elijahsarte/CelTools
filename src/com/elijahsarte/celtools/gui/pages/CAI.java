package com.elijahsarte.celtools.gui.pages;

import com.elijahsarte.celtools.CelAutoInk;
import com.elijahsarte.celtools.main.Main;

import java.util.List;
import java.util.function.Supplier;

public class CAI extends ProgramPage {

    @Override
    public String name() {
        return "CelAutoInk (Experimental)";
    }
    @Override
    public List<ProgramArg> args() {
        return ProgramArg.ofAll(
                "Cel", "cel", "file",
                "Inking model", "inking-model", "file",
                "Pencil drawing", "pencil-drawing", "file",
                "Inked drawing", "inked-drawing", "file",
                "Output cel", "output-cel", "file"
        );
    }
    @Override
    public Supplier<? extends Main> program() {
        return () -> new CelAutoInk(programArgs());
    }

}
