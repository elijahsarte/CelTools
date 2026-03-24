package com.elijahsarte.celautofill.util.mathbase.operations.operationsex;

import com.elijahsarte.celautofill.util.mathbase.operations.operationsbasic.BaseOperation;

import java.util.Map;

public class GetVar extends BaseOperation {

    private final Map<String, Double> varSubs;

    public GetVar(Map<String, Double> varSubs) {
        this.varSubs = varSubs;
    }

    @Override
    public double operate(Map<String, Double> variableSubs) {
        return 0;
    }

    public double get(String var) {
        return varSubs.get(var);
    }
}

