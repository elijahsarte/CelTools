package com.elijahsarte.celtools.main.util.function.blocks.forloop;

import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.ThreadEx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class ForLoopBase {

    protected volatile CompletableFuture<Boolean> currIter = null;
    protected final List<CompletableFuture<Boolean>> currIters = new ArrayList<>();

    protected boolean async = false;

    protected boolean Break = false;
    protected boolean Continue = false;

    // what the fuck is going on this with exception bullshit somehow not skipping the threads????
    // who even fucking cares anymore just try/catch it
    // are you fucking serious? why in the hell does the order of the lines even matter in continuing it????
    public void Break() {
        ProgrammingEx.noExcept(() -> this.currIter.cancel(true));
        this.Break = true;
        ProgrammingEx.noExcept(() -> ThreadEx.stop(this.currIter));
    }

    // oh and of FUCKING course whenever i run it through noExcept, it actually *continues* the thread
    // despite even the try/cathc loop literally the line right after it even though it's identical
    // to Break AND THE LATTER ACTUALLY FUCKING WORKS GODDAMN THIS
    public void Continue() {
        if (this.currIter != null) {
            ProgrammingEx.noExcept(() -> this.currIter.cancel(true));
            try { ThreadEx.stop(this.currIter); } catch (Exception e) { throw new RuntimeException(e); }
        }
        this.Continue = true;
    }

}

