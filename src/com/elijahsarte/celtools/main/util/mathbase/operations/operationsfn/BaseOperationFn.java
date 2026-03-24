package com.elijahsarte.celtools.main.util.mathbase.operations.operationsfn;

import com.elijahsarte.celtools.main.util.mathbase.constructs.Function;
import com.elijahsarte.celtools.main.util.mathbase.constructs.FunctionScope;

import java.util.function.DoubleBinaryOperator;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

public abstract class BaseOperationFn {

    protected final Function fn;
    protected final String variableIndex;

    protected BaseOperationFn(Function fn, String variableIndex) {
        this.fn = fn;
        this.variableIndex = variableIndex;
    }

    protected double operate(FunctionScope scope, int startX, int endX, IntPredicate restriction, DoubleBinaryOperator operator, double identity) {
        return IntStream.rangeClosed(startX, endX).filter(restriction).mapToDouble(x -> { scope.defineVar(variableIndex, x); return fn.operate(scope); }).reduce(identity, operator);
    }
    public double operate(FunctionScope scope, int startX, int endX) {
        return this.operate(scope, startX, endX, x -> x != endX + 1);
    }

    public abstract double operate(FunctionScope scope, int startX, int endX, IntPredicate restriction);

}

