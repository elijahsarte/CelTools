package com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic;

import com.elijahsarte.celtools.main.util.mathbase.constructs.FunctionScope;

public class Addition extends BaseOperation {

    public Addition(String ...variables) {
        super(variables);
    }

    @Override
    public double operate(FunctionScope scope) {
        return super.operate(scope, Double::sum);
    }

}

