package com.elijahsarte.celtools.main.util.mathbase.constructs;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic.BaseOperation;

import java.util.*;

public class FunctionScope {

    private final List<Variable> vars = new ArrayList<>();
    // In the case of CapitalPi when the "tj" variable has to be substituted for something else
    // The subscript system in "Variable" also will not be used anymore
    private final Map<String, Variable> varLinks = new HashMap<>();
    // Designed to queue operations only to be executed after a certain operation level
    // has been processed, so that not everything can just be done in one operation
    // level
    private final List<Runnable> queuedVarOpers = new ArrayList<>();

    private double result;

    public FunctionScope(List<Variable> inputs) {
        this.vars.addAll(inputs);
    }
    public FunctionScope(Map<String, Double> inputs) {
        inputs.forEach((name, val) -> this.defineVar(ProgrammingEx.varMutate(new Variable(name), var -> var.substitute(val))));
    }


    public void defineVar(Variable var) {
        queuedVarOpers.add(() -> vars.add(var));
    }
    public void defineVar(String varName, Object val) {
        removeVar(varName);
        defineVar(ProgrammingEx.varMutate(new Variable(varName), var -> var.substitute(val)));
    }
    public void defineVar(String varName, BaseOperation operation) {
        defineVar(varName, operation.operate(this));
    }

    public void linkVar(String srcVar, Variable newVar) {
        queuedVarOpers.add(() -> varLinks.put(srcVar, newVar));
    }
    public void linkVar(Variable srcVar, Variable newVar) {
        queuedVarOpers.add(() -> linkVar(srcVar.getVarName(), newVar));
    }
    // In the operation level system you can only call this in the next operation level
    // succeeding an operation level that defines a variable
    public Variable getVarRaw(String varName) {
        if (varLinks.containsKey(varName)) return varLinks.get(varName);
        return vars.stream().filter(var -> var.getVarName().equals(varName)).findFirst().orElseThrow();
    }
    public FnObject getVar(String varName) {
        return getVarRaw(varName).get();
    }

    private void removeVar(String varName) {
        OptionalEx.ofCond(this.vars.stream().filter(v -> v.getVarName().equals(varName)).findFirst().orElse(null), Objects::nonNull).thenRun(this.vars::remove);
    }
    public boolean isVar(String varName) {
        return this.vars.stream().anyMatch(v -> v.getVarName().equals(varName)) || CollectionsEx.keyList(this.varLinks).stream().anyMatch(v -> v.equals(varName));
    }

    public void returnFn(double val) {
        this.result = val;
    }
    public void returnFn(BaseOperation val) {
        returnFn(val.operate(this));
    }

    public double getResult() {
        return this.result;
    }

    // similar to the swing method
    public void validate() {
        queuedVarOpers.forEach(Runnable::run);
        queuedVarOpers.clear();
    }

}

