package com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic;

import com.elijahsarte.celtools.main.util.mathbase.constructs.FunctionScope;

import java.util.List;
import java.util.function.BinaryOperator;

public abstract class BaseOperation {

    protected final List<String> variables;

    protected BaseOperation(String ...variables) {
        this.variables = List.of(variables);
    }


    protected double operate(FunctionScope scope, BinaryOperator<Double> operation) {
        return this.variables.stream().map(name -> {
            if (!scope.isVar(name)) return Double.parseDouble(name);
            return scope.getVar(name).getDbl();
        }).reduce(operation).orElseThrow();
    }
    public abstract double operate(FunctionScope scope);

}

