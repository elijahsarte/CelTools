package com.elijahsarte.celtools.main.util.function.blocks.forloop;

import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.ThreadEx;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class ForIncrement extends ForLoopBase {

    private final double start;
    private final double end;
    private final double increment;

    private double position;


    public static void main(String[] args) {
        new ForIncrement(0, 10, 1).executeThread((currInt, forLoop) -> {
            if (currInt == 0d) {
                forLoop.Continue();
            }
            if (currInt == 7d) {
                forLoop.Break();
            }
            System.out.println(currInt);
        });
    }

    public ForIncrement(double start, double end, double increment, boolean async) {
        this.start = start;
        this.end = end;
        this.increment = increment;
        this.position = start;
        this.async = async;
    }
    public ForIncrement(double start, double end, double increment) {
        this(start, end, increment, false);
    }


    public void executeThread(BiConsumer<Double, ForIncrement> body) {
        for (position = start; position < end; position += increment) {
            this.Continue = false;
            this.Break = false;

            position = MathEx.round(position, MathEx.divide(1, BigDecimal.valueOf(increment).scale()*10));
            this.currIter = CompletableFuture.supplyAsync(ThreadEx.toBooleanSupplier(() -> body.accept(position, this)));
            if (this.async) {
                this.currIters.add(this.currIter);
            } else {
                ProgrammingEx.noExcept(() -> this.currIter.join());
            }

            if (this.Continue) continue;
            if (this.Break) break;
        }
        if (this.async) {
            CompletableFuture.allOf(this.currIters.toArray(new CompletableFuture[0])).join();
        }
    }
    public void execute(BiConsumer<Double, ForIncrement> body) {
        boolean isWhole = MathEx.isWhole(increment);
        double places = isWhole ? 0 : MathEx.divide(1, BigDecimal.valueOf(increment).scale() * 10);
        for (position = start; position < end; position += increment) {
            this.Continue = false;
            this.Break = false;
            if (!isWhole) position = MathEx.round(position, places);
            body.accept(position, this);

            if (this.Continue) continue;
            if (this.Break) break;
        }
    }
    public void execute(Consumer<Double> body) {
        ProgrammingEx.varExec(MathEx.divide(1, BigDecimal.valueOf(increment).scale() * 10), p -> { for (position = start; position < end; position += increment) body.accept(MathEx.round(position, p)); });
    }
    public void executeInt(BiConsumer<Integer, ForIncrement> body) {
        for (int position = (int) start; position < end; position += (int) increment) {
            this.Continue = false;
            this.Break = false;
            body.accept(position, this);

            if (this.Continue) continue;
            if (this.Break) break;
        }
    }

    public void executeInt(Consumer<Integer> body) {
        for (int position = (int) start; position < end; position += (int) increment)
            body.accept(position);
    }


    public void setPosition(double newPos) {
        this.position = newPos;
    }

    public void reset() {
        this.position = this.start;
    }
}

