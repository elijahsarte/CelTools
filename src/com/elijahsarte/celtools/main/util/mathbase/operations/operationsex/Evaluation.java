package com.elijahsarte.celautofill.util.mathbase.operations.operationsex;

import com.elijahsarte.celautofill.util.mathbase.constructs.GlobalVariablePool;
import com.elijahsarte.celautofill.util.mathbase.operations.operationsbasic.BaseOperation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Evaluation extends BaseOperation {

    private final String evalVal;
    private final Class clazz;
    private final Function[] args;

    @SafeVarargs
    public <T> Evaluation(Class<T> clazz, String evalVal, Function<HashMap<String, Double>, ?>... args) {
        this.clazz = clazz;
        this.evalVal = evalVal;
        this.args = args;
    }

    @Override
    public double operate(Map<String, Double> variableSubs) {
        Object[] actualArgs = Arrays.stream(args).map(func -> func.apply(variableSubs)).toArray();
        try {
            return ((FunctionEx) clazz.getConstructor(Arrays.stream(actualArgs)
                    .map(arg -> {
                        if (arg instanceof Double) {
                            return double.class;
                        } else if (arg instanceof Integer) {
                            return int.class;
                        } else {
                            return arg.getClass();
                        }
                    })
                    .toArray(Class[]::new))
                    .newInstance(actualArgs))
                    .evaluate(GlobalVariablePool.contains(evalVal) ? GlobalVariablePool.get(evalVal).get() : variableSubs.get(evalVal));*/
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public double operate(double valInput) {
        return this.operate(new HashMap<>(Map.ofEntries(Map.entry("final", valInput))));
    }

}

