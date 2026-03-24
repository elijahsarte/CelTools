package com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic;

import com.elijahsarte.celtools.main.util.mathbase.constructs.FunctionScope;

public class Multiplication extends BaseOperation {

    public Multiplication(String ...variables) {
        super(variables);
    }

    @Override
    public double operate(FunctionScope scope) {
        return super.operate(scope, (a, b) -> a * b);
    }

}

