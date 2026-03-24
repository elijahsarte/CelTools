package com.elijahsarte.celtools.main.util.mathbase.operations.operationsfn;

import com.elijahsarte.celtools.main.util.mathbase.constructs.Function;
import com.elijahsarte.celtools.main.util.mathbase.constructs.FunctionScope;

import java.util.function.IntPredicate;

public class Sigma extends BaseOperationFn {

    public Sigma(Function fn, String variableIndex) {
        super(fn, variableIndex);
    }

    @Override
    public double operate(FunctionScope scope, int startX, int endX, IntPredicate restriction) {
        return super.operate(scope, startX, endX, restriction, Double::sum, 0.0);
    }

}

