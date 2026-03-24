package com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic;

import com.elijahsarte.celtools.main.util.mathbase.constructs.FunctionScope;

public class Division extends BaseOperation {

    public Division(String ...variables) {
        super(variables);
    }

    @Override
    public double operate(FunctionScope scope) {
        return super.operate(scope, (a, b) -> a / b);
    }

}

