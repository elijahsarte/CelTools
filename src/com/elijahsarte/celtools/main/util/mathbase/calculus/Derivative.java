package com.elijahsarte.celtools.main.util.mathbase.calculus;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.mathbase.constructs.Function;
import com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic.Division;
import com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic.Subtraction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Derivative {

    private final Limit derivLimit;

    public Derivative(Function fn, Map<String, Double> addInputs) {
        this.derivLimit = new Limit(new Function(List.of(
                List.of(
                        (scope) -> scope.defineVar("offset", fn.operate(scope.getVar("x").getDbl() + scope.getVar("h").getDbl())),
                        (scope) -> scope.defineVar("src", fn.operate(scope.getVar("h").getDbl()))
                ),
                List.of(
                        (scope) -> scope.defineVar("dx", new Subtraction("offset", "src"))
                ),
                List.of(
                        (scope) -> scope.returnFn(new Division("dx", "h"))
                )
        ), "x", "h"), CollectionsEx.concatMaps(addInputs, Map.of("h", 0d)));
    }
    public Derivative(Function fn) {
        this(fn, new HashMap<>());
    }
    // multivariable derivatives are not well defined for the purposes of this program
//    public double evaluate(String var, double input) {
//        return this.derivLimit.evaluate(var, input);
//    }
    public double evaluate(double input) {
        return this.derivLimit.evaluate("x", input);
    }

}

