package com.elijahsarte.celtools.main.util.mathbase.constructs;

import com.elijahsarte.celtools.main.util.ProgrammingEx;

public class FnObject {

    private Double valD = null;
    private Function valFn = null;

    public FnObject(Object obj) {
        if (obj instanceof FnObject) {
            obj = ((FnObject) obj).get();
        }

        if (obj instanceof Double) {
            this.valD = (Double) obj;
        } else if (obj instanceof Integer) {
            this.valD = ((Integer) obj).doubleValue();
        } else if (obj instanceof Function) {
            this.valFn = (Function) obj;
        } else {
            throw new RuntimeException("FnObject instantiated with unsupported type " + obj.getClass().getName());
        }
    }

    public Double getDblObj() {
        return this.valD;
    }
    public double getDbl() {
        return this.getDblObj();
    }
    public int getInt() {
        return this.getDblObj().intValue();
    }
    public Function getFn() {
        return this.valFn;
    }

    public Object get() {
        return ProgrammingEx.nonNull(this.getDbl(), this.getFn());
    }


    public double add(double a) {
        return this.getDbl()+a;
    }
    public double subtract(double s) {
        return this.getDbl()-s;
    }
    public double mult(double m) {
        return this.getDbl()*m;
    }
    public double divide(double d) {
        return this.getDbl()/d;
    }
    public double pow(double p) {
        return Math.pow(this.getDbl(), p);
    }
    public double add(FnObject a) {
        return add(a.getDbl());
    }
    public double subtract(FnObject s) {
        return subtract(s.getDbl());
    }
    public double mult(FnObject m) {
        return mult(m.getDbl());
    }
    public double divide(FnObject d) {
        return divide(d.getDbl());
    }
    public double pow(FnObject p) {
        return pow(p.getDbl());
    }

}

