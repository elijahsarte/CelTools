package com.elijahsarte.celautofill.util.mathbase.operations.operationsex;

import com.elijahsarte.celautofill.util.mathbase.operations.operationsbasic.BaseOperation;

import java.util.Map;
import java.util.function.Function;

public class SimpleEvaluation extends BaseOperation {

    private final Function<Map<String, Double>, Double> fn;

    public SimpleEvaluation(Function<Map<String, Double>, Double> fn) {
        this.fn = fn;
    }

    @Override
    public double operate(Map<String, Double> variableSubs) {
        return this.fn.apply(variableSubs);
    }

}

