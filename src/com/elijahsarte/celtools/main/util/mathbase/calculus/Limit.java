package com.elijahsarte.celtools.main.util.mathbase.calculus;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.function.blocks.forloop.ForIncrement;
import com.elijahsarte.celtools.main.util.mathbase.constructs.Function;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Limit {

    private final Function fn;
    private final Map<String, Double> fnInputs;

    public Limit(Function fn, Map<String, Double> fnInputs) {
        this.fn = fn;
        this.fnInputs = fnInputs;
    }
    public Limit(Function fn) {
        this(fn, new HashMap<>());
    }


    public double leftHand(Map<String, Double> approaches) {
        AtomicReference<Double> res = new AtomicReference<>();
        new ForIncrement(1, 1e-14, -0.01).execute(dx -> {
            res.set(fn.operate(CollectionsEx.concatMaps(fnInputs, approaches.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, e -> e.getValue() - dx
            )))));
        });
        return res.get();
    }
    public double leftHand(String var, double input) {
        return leftHand(Map.of(var, input));
    }
    public double leftHand(double input) {
        return leftHand(fn.getInputVars().get(0), input);
    }


    public double rightHand(Map<String, Double> approaches) {
        AtomicReference<Double> res = new AtomicReference<>();
        new ForIncrement(1, 1e-14, -0.01).execute(dx -> {
            res.set(fn.operate(CollectionsEx.concatMaps(fnInputs, approaches.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, e -> e.getValue() + dx
            )))));
        });
        return res.get();
    }
    public double rightHand(String var, double input) {
        return rightHand(Map.of(var, input));
    }
    public double rightHand(double input) {
        return rightHand(fn.getInputVars().get(0), input);
    }

    public double evaluate(Map<String, Double> approaches) {
        double initialRes = fn.operate(CollectionsEx.concatMaps(fnInputs, approaches));
        if (!Double.isNaN(initialRes)) {
            return initialRes;
        }
        return ProgrammingEx.varOper(leftHand(approaches), lh -> lh == rightHand(approaches) ? lh : Double.NaN);
    }
    public double evaluate(String var, double input) {
        return evaluate(Map.of(var, input));
    }
    public double evaluate(double input) {
        return evaluate(fn.getInputVars().get(0), input);
    }

}

