package com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic;

import com.elijahsarte.celtools.main.util.mathbase.constructs.FunctionScope;

public class Power extends BaseOperation {

    public Power(String ...variables) {
        super(variables);
    }

    @Override
    public double operate(FunctionScope scope) {
        return super.operate(scope, Math::pow);
    }

}

