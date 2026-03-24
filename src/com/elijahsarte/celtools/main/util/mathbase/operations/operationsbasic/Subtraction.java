package com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic;

import com.elijahsarte.celtools.main.util.mathbase.constructs.FunctionScope;

public class Subtraction extends BaseOperation {

    public Subtraction(String ...variables) {
        super(variables);
    }

    @Override
    public double operate(FunctionScope scope) {
        return super.operate(scope, (a, b) -> a - b);
    }

}

