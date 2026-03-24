package com.elijahsarte.celtools.main.util.mathbase.constructs;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic.*;

import java.util.*;
import java.util.function.Consumer;

public class Function {

    private final List<List<Consumer<FunctionScope>>> fnOperations;
    // inputVars is designed to be a "contract" so that outside classes
    // like Limit can see directly what variables to input
    private final List<String> inputVars;

    public Function(List<List<Consumer<FunctionScope>>> fnOperations, List<String> inputVars) {
        this.fnOperations = fnOperations;
        this.inputVars = inputVars;
    }
    public Function(List<List<Consumer<FunctionScope>>> fnOperations, String... inputVars) {
        this(fnOperations, Arrays.stream(inputVars).toList());
    }
    public Function(List<List<Consumer<FunctionScope>>> fnOperations) {
        this(fnOperations, new ArrayList<>());
    }


    public double operate(FunctionScope scope) {
        fnOperations.forEach(opLevel -> {
            scope.validate();
            opLevel.forEach(op -> op.accept(scope));
        });
        return scope.getResult();
    }

    public double operate(Map<String, Double> inputs) {
        if (!CollectionsEx.listLeftDiff(CollectionsEx.keyList(inputs), inputVars).isEmpty()) {
            throw new RuntimeException("Function called incorrectly with map");
        }
        return operate(new FunctionScope(inputs));
    }
    public double operate(List<Variable> inputs) {
        if (!CollectionsEx.listLeftDiff(inputs.stream().map(Variable::getVarName).toList(), inputVars).isEmpty()) {
            throw new RuntimeException("Function called incorrectly with list");
        }
        return operate(new FunctionScope(inputs));
    }

    public double operate(double valInput) {
        if (inputVars.size() != 1) {
            throw new RuntimeException("Function must be called with multiple inputs");
        }
        return operate(Map.ofEntries(Map.entry(inputVars.get(0), valInput)));
    }


    public List<String> getInputVars() {
        return this.inputVars;
    }


    public static void main(String[] args) {

        // example function: 5x-(6x+7)
        Function fn = new Function(List.of(
                List.of(
                        (scope) -> scope.defineVar("mult1", new Multiplication("6", "x"))
                ),
                List.of(
                        (scope) -> scope.defineVar("mult2", new Multiplication("5", "x")),
                        (scope) -> scope.defineVar("add2", new Addition("7", "mult1"))
                ),
                List.of(
                        (scope) -> scope.returnFn(new Subtraction("mult2", "add2"))
                )
        ), "x");
        System.out.println(fn.operate(new HashMap<>(Map.ofEntries(
                Map.entry("x", 78d)
        ))));

        // example function: 6x^4 + 7x^3 + 6x^2 - 89x - 79
        Function fn1 = new Function(List.of(
                List.of(
                        (scope) -> scope.defineVar("x2Raw", scope.getVar("x").pow(2)),
                        (scope) -> scope.defineVar("x3Raw", scope.getVar("x").pow(3)),
                        (scope) -> scope.defineVar("x4Raw", scope.getVar("x").pow(4))
                ),
                List.of(
                        (scope) -> scope.defineVar("x1", scope.getVar("x").mult(-89)),
                        (scope) -> scope.defineVar("x2", scope.getVar("x2Raw").mult(6)),
                        (scope) -> scope.defineVar("x3", scope.getVar("x3Raw").mult(7)),
                        (scope) -> scope.defineVar("x4", scope.getVar("x4Raw").mult(6))
                ),
                List.of(
                        (scope) -> scope.returnFn(scope.getVar("x4").getDbl() +
                                scope.getVar("x3").getDbl() + scope.getVar("x2").getDbl() +
                                scope.getVar("x1").getDbl() - 79)
                )
        ), "x");
        System.out.println(fn1.operate(new HashMap<>(Map.ofEntries(
                Map.entry("x", 7d)
        ))));
        // example function: 5x-(6x+7)
        /*
        Function fn = new Function(new TreeMap<>(Map.ofEntries(
                Map.entry(0, new HashMap<>(Map.ofEntries(
                    Map.entry("final", new Subtraction("mult2", "add2"))
                ))),
                Map.entry(1, new HashMap<>(Map.ofEntries(
                    Map.entry("add2", new Addition("mult1", "7")),
                    Map.entry("mult2", new Multiplication("5", "x"))
                ))),
                Map.entry(2, new HashMap<>(Map.ofEntries(
                    Map.entry("mult1", new Multiplication("6", "x"))
                )))
            ))
        );
        System.out.println(fn.evaluate(new HashMap<>(Map.ofEntries(
                Map.entry("x", 78d)
        ))));
        // example function: 6x^4 + 7x^3 + 6x^2 - 89x - 79
        Function fn1 = new Function(new TreeMap<>(Map.ofEntries(
                Map.entry(0, new HashMap<>(Map.ofEntries(
                        Map.entry("final", new Addition("x4", "x3", "x2", "x1", "-79"))
                ))),
                Map.entry(1, new HashMap<>(Map.ofEntries(
                        Map.entry("x4", new Multiplication("6", "x4Raw")),
                        Map.entry("x3", new Multiplication("7", "x3Raw")),
                        Map.entry("x2", new Multiplication("6", "x2Raw")),
                        Map.entry("x1", new Multiplication("-89", "x"))
                ))),
                Map.entry(2, new HashMap<>(Map.ofEntries(
                        Map.entry("x4Raw", new Power("x", "4")),
                        Map.entry("x3Raw", new Power("x", "3")),
                        Map.entry("x2Raw", new Power("x", "2"))
                )))
        )));
        System.out.println(fn1.evaluate(new HashMap<>(Map.ofEntries(
                Map.entry("x", 7d)
        ))));*/
    }
}

