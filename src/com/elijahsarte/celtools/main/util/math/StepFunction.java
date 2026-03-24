package com.elijahsarte.celtools.main.util.math;



import static com.elijahsarte.celtools.main.util.MathEx.*;

import java.util.List;
import java.util.function.Function;

// todo: extends function maybe?
public final class StepFunction {

    private static final double SLOPE = 0.00000000000002;
    private static final double JUMP_DELTA = 0.1;
    public static final double PRACTICAL_THRESHOLD = 49000000000000d;

    public StepFunction(List<Integer> steps) {

    }

    public static Function<Double, Double> prefast(double a) {
        return (x) -> Math.floor(0.5 + (0.5*(((x-a))/nonZero(Math.abs(x-a)))));
    }
    public static Function<Double, Double> prefast(double a, double b) {
        return (x) -> prefast(a).apply(x) + prefast(b).apply(x);
    }
    public static Function<Double, Double> prefast(double a, double b, double c) {
        return (x) -> prefast(a, b).apply(x)
                + prefast(c).apply(x);
    }
    public static Function<Double, Double> prefast(double a, double b, double c, double d) {
        return (x) -> prefast(a, b, c).apply(x)
                + prefast(d).apply(x);
    }
    public static Function<Double, Double> prefast(double a, double b, double c, double d, double e) {
        return (x) -> prefast(a, b, c, d).apply(x)
                + prefast(e).apply(x);
    }
    public static Function<Double, Double> prefast(double a, double b, double c, double d, double e, double f) {
        return (x) -> prefast(a, b, c, d, e).apply(x)
                + prefast(f).apply(x);
    }
    public static Function<Double, Double> prefast(double a, double b, double c, double d, double e, double f, double g) {
        return (x) -> prefast(a, b, c, d, e, f).apply(x)
                + prefast(g).apply(x);
    }
    public static Function<Double, Double> prefast(double a, double b, double c, double d, double e, double f, double g, double h) {
        return (x) -> prefast(a, b, c, d, e, f, g).apply(x)
                + prefast(h).apply(x);
    }
    public static Function<Double, Double> prefast(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
        return (x) -> prefast(a, b, c, d, e, f, g, h).apply(x)
                + prefast(i).apply(x);
    }
    public static Function<Double, Double> prefast(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j) {
        return (x) -> prefast(a, b, c, d, e, f, g, h, i).apply(x)
                + prefast(j).apply(x);
    }
    public static Function<Double, Double> prefast(double... lastNums) {
        return (x) -> {
            double res = 0;
            for (double lastNum : lastNums) res += prefast(lastNum).apply(x);
            return res;
        };
    }


    public static Function<Double, Double> continuous(double a) {
        return (x) -> (0.5 + (0.5*(fastCbrt(fastCbrt(fastCbrt((float) (x-a)))))));
    }
    public static Function<Double, Double> continuous(double a, double b) {
        return (x) -> Math.floor(continuous(a).apply(x)) + continuous(b).apply(x);
    }
    public static Function<Double, Double> continuous(double a, double b, double c) {
        return (x) -> Math.floor(continuous(a, b).apply(x)) + continuous(c).apply(x);
    }
    public static Function<Double, Double> continuous(double a, double b, double c, double d) {
        return (x) -> Math.floor(continuous(a, b, c).apply(x)) + continuous(d).apply(x);
    }
    public static Function<Double, Double> continuous(double a, double b, double c, double d, double e) {
        return (x) -> Math.floor(continuous(a, b, c, d).apply(x)) + continuous(e).apply(x);
    }
    public static Function<Double, Double> continuous(double a, double b, double c, double d, double e, double f) {
        return (x) -> Math.floor(continuous(a, b, c, d, e).apply(x)) + continuous(f).apply(x);
    }
    public static Function<Double, Double> continuous(double a, double b, double c, double d, double e, double f, double g) {
        return (x) -> Math.floor(continuous(a, b, c, d, e, f).apply(x)) + continuous(g).apply(x);
    }
    public static Function<Double, Double> continuous(double a, double b, double c, double d, double e, double f, double g, double h) {
        return (x) -> Math.floor(continuous(a, b, c, d, e, f, g).apply(x)) + continuous(h).apply(x);
    }
    public static Function<Double, Double> continuous(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
        return (x) -> Math.floor(continuous(a, b, c, d, e, f, g, h).apply(x)) + continuous(i).apply(x);
    }
    public static Function<Double, Double> continuous(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j) {
        return (x) -> Math.floor(continuous(a, b, c, d, e, f, g, h, i).apply(x)) + continuous(j).apply(x);
    }
    public static Function<Double, Double> continuous(double... lastNums) {
        return (x) -> {
            double res = 0;
            for (int i = 0; i < lastNums.length - 1; i++) res += Math.floor(continuous(lastNums[i]).apply(x));
            res += continuous(lastNums[lastNums.length - 1]).apply(x);
            return res;
        };
    }

    //b\left(x\right)=\left(\frac{x-a}{\left|x-a\right|}\right)\cdot\left(ux\right)-\left(\left(-\frac{\left(x-a\right)}{\left|x-a\right|}\cdot0.5\right)-0.54\right)
    public static Function<Double, Double> sloped(double a) {
//        return (x) -> (signClamp(x-a)*(slope*x))-((-signClamp(x-a)*0.5)-0.54);
        return (x) -> ((SLOPE*(x-a)))-((-signClamp(x-a)*0.5)-0.5);
//        return (x) -> ((x < a) ? (SLOPE*(x-a))+(-a*SLOPE) : 1);
    }

    public static Function<Double, Double> sloped(double a, double b) {
        return (x) -> (x < b ? sloped(a).apply(x) : sloped(a).apply(x).intValue() + sloped(b).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c) {
        return (x) -> (x < c ? sloped(a, b).apply(x) : sloped(a, b).apply(x).intValue() + sloped(c).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d) {
        return (x) -> (x < d ? sloped(a, b, c).apply(x) : sloped(a, b, c).apply(x).intValue() + sloped(d).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e) {
        return (x) -> (x < e ? sloped(a, b, c, d).apply(x) : sloped(a, b, c, d).apply(x).intValue() + sloped(e).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f) {
        return (x) -> (x < f ? sloped(a, b, c, d, e).apply(x) : sloped(a, b, c, d, e).apply(x).intValue() + sloped(f).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g) {
        return (x) -> (x < g ? sloped(a, b, c, d, e, f).apply(x) : sloped(a, b, c, d, e, f).apply(x).intValue() + sloped(g).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h) {
        return (x) -> (x < h ? sloped(a, b, c, d, e, f, g).apply(x) : sloped(a, b, c, d, e, f, g).apply(x).intValue() + sloped(h).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
        return (x) -> (x < i ? sloped(a, b, c, d, e, f, g, h).apply(x) : sloped(a, b, c, d, e, f, g, h).apply(x).intValue() + sloped(i).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j) {
        return (x) -> (x < j ? sloped(a, b, c, d, e, f, g, h, i).apply(x) : sloped(a, b, c, d, e, f, g, h, i).apply(x).intValue() + sloped(j).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k) {
        return (x) -> (x < k ? sloped(a, b, c, d, e, f, g, h, i, j).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j).apply(x).intValue() + sloped(k).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l) {
        return (x) -> (x < l ? sloped(a, b, c, d, e, f, g, h, i, j, k).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k).apply(x).intValue() + sloped(l).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m) {
        return (x) -> (x < m ? sloped(a, b, c, d, e, f, g, h, i, j, k, l).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l).apply(x).intValue() + sloped(m).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n) {
        return (x) -> (x < n ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m).apply(x).intValue() + sloped(n).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o) {
        return (x) -> (x < o ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n).apply(x).intValue() + sloped(o).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p) {
        return (x) -> (x < p ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o).apply(x).intValue() + sloped(p).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q) {
        return (x) -> (x < q ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p).apply(x).intValue() + sloped(q).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q, double r) {
        return (x) -> (x < r ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q).apply(x).intValue() + sloped(r).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q, double r, double s) {
        return (x) -> (x < s ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r).apply(x).intValue() + sloped(s).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q, double r, double s, double t) {
        return (x) -> (x < t ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s).apply(x).intValue() + sloped(t).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q, double r, double s, double t, double u) {
        return (x) -> (x < u ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t).apply(x).intValue() + sloped(u).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q, double r, double s, double t, double u, double v) {
        return (x) -> (x < v ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u).apply(x).intValue() + sloped(v).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q, double r, double s, double t, double u, double v, double w) {
        return (x) -> (x < w ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v).apply(x) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v).apply(x).intValue() + sloped(w).apply(x));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q, double r, double s, double t, double u, double v, double w, double x) {
        return (x0) -> (x0 < x ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w).apply(x0) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w).apply(x0).intValue() + sloped(x).apply(x0));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q, double r, double s, double t, double u, double v, double w, double x, double y) {
        return (x0) -> (x0 < y ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x).apply(x0) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x).apply(x0).intValue() + sloped(y).apply(x0));
    }
    public static Function<Double, Double> sloped(double a, double b, double c, double d, double e, double f, double g, double h, double i, double j, double k, double l, double m, double n, double o, double p, double q, double r, double s, double t, double u, double v, double w, double x, double y, double z) {
        return (x0) -> (x0 < z ? sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y).apply(x0) : sloped(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y).apply(x0).intValue() + sloped(z).apply(x0));
    }
    public static Function<Double, Double> sloped(double... nums) {
        return switch (nums.length) {
            case 0 -> slopedZero();
            case 1 -> sloped(nums[0]);
            case 2 -> sloped(nums[0], nums[1]);
            case 3 -> sloped(nums[0], nums[1], nums[2]);
            case 4 -> sloped(nums[0], nums[1], nums[2], nums[3]);
            case 5 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4]);
            case 6 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5]);
            case 7 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6]);
            case 8 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7]);
            case 9 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8]);
            case 10 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9]);
            case 11 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10]);
            case 12 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11]);
            case 13 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12]);
            case 14 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13]);
            case 15 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14]);
            case 16 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15]);
            case 17 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16]);
            case 18 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16], nums[17]);
            case 19 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16], nums[17], nums[18]);
            case 20 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16], nums[17], nums[18], nums[19]);
            case 21 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16], nums[17], nums[18], nums[19], nums[20]);
            case 22 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16], nums[17], nums[18], nums[19], nums[20], nums[21]);
            case 23 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16], nums[17], nums[18], nums[19], nums[20], nums[21], nums[22]);
            case 24 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16], nums[17], nums[18], nums[19], nums[20], nums[21], nums[22], nums[23]);
            case 25 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16], nums[17], nums[18], nums[19], nums[20], nums[21], nums[22], nums[23], nums[24]);
            case 26 -> sloped(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6], nums[7], nums[8], nums[9], nums[10], nums[11], nums[12], nums[13], nums[14], nums[15], nums[16], nums[17], nums[18], nums[19], nums[20], nums[21], nums[22], nums[23], nums[24], nums[25]);
            default -> (x) -> {
                int res = 0;
                double lN = nums[0];
                for (int i = 0; i < nums.length - 1; i++) {
                    double n = nums[i];
                    if (x < n) return Math.max(res - 1, 0) + sloped(lN).apply(x);
                    if (x == n) return res + sloped(n).apply(x);
                    res++;
                    lN = n;
                }
                return res + sloped(nums[nums.length - 1]).apply(x);
            };
        };
    }
    public static Function<Double, Double> slopedZero() {
        return StepFunction.sloped(PRACTICAL_THRESHOLD);
    }


    public static double slopedjump(Function<Double, Double> fn, double lastNum) {
        return (decimalPart(fn.apply(lastNum))/(signClamp(fn.apply(lastNum+JUMP_DELTA)-fn.apply(lastNum))*SLOPE))+lastNum;
    }
    public static double slopedstart(Function<Double, Double> fn, double x) {
        double dist = ((fn.apply(x)-(fn.apply(x).intValue()))/SLOPE);
        return dist<0?0:x-dist;
    }




    public static Function<Double, Double> preadd(Function<Double, Double> fn, double num) {
        return (x) -> fn.apply(x) + (prefast(num).apply(x).intValue());
    }
    public static Function<Double, Double> preremove(Function<Double, Double> fn, double num) {
        return (x) -> fn.apply(x) - (prefast(num).apply(x).intValue());
    }
    public static Function<Double, Double> contadd(Function<Double, Double> fn, double num) {
        return (x) -> fn.apply(x) + (continuous(num).apply(x).intValue());
    }
    public static Function<Double, Double> contremove(Function<Double, Double> fn, double num) {
        return (x) -> fn.apply(x) - (continuous(num).apply(x).intValue());
    }
    public static Function<Double, Double> slopeadd(Function<Double, Double> fn, double num) {
//        return (x) -> (x < num ? fn.apply(x) : fn.apply(x).intValue()) + (x < num ? sloped(num).apply(x).intValue() : sloped(num).apply(x));
        return (x) -> (x < num ? fn.apply(x) : fn.apply(x).intValue() + sloped(num).apply(x));
    }
    public static Function<Double, Double> sloperemove(Function<Double, Double> fn, double num) {
//        return (x) -> fn.apply(x) - (sloped(num).apply(x).intValue());
//        return (x) -> fn.apply(x) - ((x < num ? fn.apply(x) : fn.apply(x).intValue()) + (x < num ? 0 : sloped(num).apply(x))
        return (x) -> (x >= num ? fn.apply(x) - sloped(num).apply(x) : fn.apply(x));
    }

}

