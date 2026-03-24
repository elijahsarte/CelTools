package com.elijahsarte.celtools.main.util.mathbase.constructs;

public class Variable {

    private final String varName;
    private String baseSubscriptName = null;
    private String subscriptName = null;
    private FnObject val = null;

    public Variable(String varName) {
        this.varName = varName;
    }
    public Variable(String varName, String subscriptName) {
        this(varName);
        this.subscriptName = subscriptName;
        this.baseSubscriptName = subscriptName;
    }

    public void substituteSubscript(String subscriptVal) {
        this.subscriptName = subscriptVal;
//        if (this.subscriptName != null) {
//            this.variableName.replace(this.subscriptName, subscriptVal);
//        }
    }

    public void substitute(Object obj) {
        this.val = new FnObject(obj);
    }
    public FnObject get() {
        if (this.val == null) {
            throw new RuntimeException("Variable " + this.varName + " with subscript " + this.subscriptName + " was queried for a value when none has been substituted");
        }
        return this.val;
    }

    public String getVarName() {
        return this.varName;
    }
    public String getSubscriptName() {
        return this.subscriptName;
    }
    public String getBaseSubscriptName() {
        return this.baseSubscriptName;
    }
    public String getRawName() {
        String currStr = this.varName;
        if (this.subscriptName != null) currStr += this.subscriptName;
        return currStr;
    }

    public boolean equals(Variable obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        return this.getVarName().equals(obj.getVarName());
    }

}

