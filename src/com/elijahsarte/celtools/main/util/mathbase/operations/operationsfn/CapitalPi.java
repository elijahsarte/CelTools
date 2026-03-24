package com.elijahsarte.celtools.main.util.mathbase.operations.operationsfn;

import com.elijahsarte.celtools.main.util.mathbase.constructs.Function;
import com.elijahsarte.celtools.main.util.mathbase.constructs.FunctionScope;

import java.util.function.IntPredicate;

public class CapitalPi extends BaseOperationFn {

    public CapitalPi(Function fn, String variableIndex) {
        super(fn, variableIndex);
    }

    @Override
    public double operate(FunctionScope scope, int startX, int endX, IntPredicate restriction) {
        return super.operate(scope, startX, endX, restriction, (a, b) -> a * b, 1.0);
    }

}

