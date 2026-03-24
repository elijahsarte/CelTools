package com.elijahsarte.celtools.main.util;

import com.elijahsarte.celtools.main.util.function.blocks.switchloop.SwitchExpEx;
import com.elijahsarte.celtools.main.util.function.blocks.switchloop.ValCase;
import com.elijahsarte.celtools.main.util.geomex.Line;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

public final class MathEx {

    private static final Random generalRandom = new Random(43L);
    private static final double[] SIN_TABLE = new double[65536];
    private static final double DEF_EXP_C = 1072632447;

    static {
        IntStream.range(0, 65536).forEach(delta -> SIN_TABLE[delta] = Math.sin(MathEx.divide(delta * Math.PI * 2, 65536)));
    }

    public static double sin(double input) {
        return SIN_TABLE[(int) (input * 10430.378) & '\uffff'];
    }
    public static double cos(double input) {
        return SIN_TABLE[(int) (input * 10430.378 + 16384.0) & '\uffff'];
    }
    public static double tan(double input) {
        return sin(input) / cos(input);
    }


    public static double round(double val, double precision) {
        return Math.round(val * (10.0/(precision*10.0))) / (10.0/(precision*10.0));
    }

//    public static double divide(double d1, int d2) {
//        return d1 / (double) d2;
//    }
//    public static double divide(int d1, double d2) {
//        return (double) d1 / d2;
//    }
    public static double divide(int d1, int d2) {
        return (double) d1 / (double) d2;
    }

    public static int ceilDiv(double d1, double d2) {
        return ceilInt(d1 / d2);
    }

    public static boolean isWhole(double val) {
        return val == Math.floor(val);
    }
    public static boolean isWhole(float val) {
        return val == Math.floor(val);
    }


    public static <T> double dblNumObj(T t1) {
        return ((Number) t1).doubleValue();
    }
    public static Double dblObjNumObj(Number num) {
        return num == null ? null : (Double) num.doubleValue();
    }

    public static Number castNum(Number num, double input) {
        return ReflectionEx.callMethod(num, ReflectionEx.getMethod(num, "valueOf",
                        (Class<?>) SwitchExpEx.of(
                Integer.class, int.class, Double.class, double.class,
                Float.class, float.class, Long.class, long.class,
                Short.class, short.class, Byte.class, byte.class).evaluate(num.getClass())),
                SwitchExpEx.of(
                        ValCase.of(Integer.class, () -> (int) input),
                        ValCase.of(Double.class, () -> input),
                        ValCase.of(Float.class, () -> (float) input),
                        ValCase.of(Long.class, () -> (long) input),
                        ValCase.of(Short.class, () -> (short) input),
                        ValCase.of(Byte.class, () -> (byte) input)
                ).evaluate(num.getClass()));
    }
    public static Number castNum(Number num, int input) {
        return castNum(num, (double) input);
    }

    public static <T> double add(T t1, T t2) {
        return dblNumObj(t1) + dblNumObj(t2);
    }
    public static <T> double subtract(T t1, T t2) {
        return dblNumObj(t1) - dblNumObj(t2);
    }
    public static <T> double multiply(T t1, T t2) {
        return dblNumObj(t1) * dblNumObj(t2);
    }
    public static <T> double divide(T t1, T t2) {
        return dblNumObj(t1) / dblNumObj(t2);
    }

    public static <T> int ceilDiv(T t1, T t2) {
        return ceilDiv(dblNumObj(t1), dblNumObj(t2));
    }


    public static <T> boolean greater(T t1, T t2) {
        return dblNumObj(t1) > dblNumObj(t2);
    }
    public static <T> boolean gEqu(T t1, T t2) {
        return dblNumObj(t1) >= dblNumObj(t2);
    }
    public static <T> boolean less(T t1, T t2) {
        return dblNumObj(t1) < dblNumObj(t2);
    }
    public static <T> boolean lEqu(T t1, T t2) {
        return dblNumObj(t1) <= dblNumObj(t2);
    }
    public static <T> boolean equ(T t1, T t2) {
        return dblNumObj(t1) == dblNumObj(t2);
    }
    public static <T> boolean nEqu(T t1, T t2) {
        return dblNumObj(t1) != dblNumObj(t2);
    }



    public static double bound(double input, double min, double max) {
        return Math.min(Math.max(min, input), max);
    }
    public static int bound(int input, int min, int max) {
        return (int) bound((double) input, min, max);
    }
    public static double posBound(double input) {
        return MathEx.bound(input, 0, Double.MAX_VALUE);
    }
    public static int posBound(int input) {
        return (int) posBound((double) input);
    }
    public static double negBound(double input) {
        return MathEx.bound(input, Double.MIN_VALUE, 0);
    }
    public static int negBound(int input) {
        return (int) negBound((double) input);
    }
    public static boolean bounded(double input, double min, double max) {
        return input >= min && input <= max;
    }

    public static double rand(double min, double max) {
        return Math.floor(Math.random() * (max - min  + 1) + min);
    }
    public static int rand(int min, int max) {
        return (int) rand((double) min, max);
    }

    public static int floorInt(double input) {
        return (int) Math.floor(input);
    }
    public static int roundInt(double input) {
        return (int) Math.round(input);
    }
    public static int ceilInt(double input) {
        return (int) Math.ceil(input);
    }

    public static <T> double max(T t1, T t2) {
        return Math.max(dblNumObj(t1), dblNumObj(t2));
    }
    public static <T> double min(T t1, T t2) {
        return Math.min(dblNumObj(t1), dblNumObj(t2));
    }

    public static double closer(double input, double num1, double num2) {
        return Math.abs(num1 - input) > Math.abs(num2 - input) ? num2 : num1;
    }
    public static int closer(int input, int num1, int num2) {
        return (int) closer((double) input, num1, num2);
    }
    public static boolean closerB(double input, double num1, double num2) {
        return closer(input, num1, num2) == num1;
    }
    public static boolean closerB(int input, int num1, int num2) {
        return closer(input, num1, num2) == num1;
    }

    public static double farther(double input, double num1, double num2) {
        return Math.abs(num1 - input) < Math.abs(num2 - input) ? num2 : num1;
    }
    public static int farther(int input, int num1, int num2) {
        return (int) farther((double) input, num1, num2);
    }
    public static boolean fartherB(double input, double num1, double num2) {
        return farther(input, num1, num2) == num1;
    }
    public static boolean fartherB(int input, int num1, int num2) {
        return farther(input, num1, num2) == num1;
    }


    public static double max(double... dbls) {
        double m = dbls[0];
        for (double d : dbls) if (d > m) m = d;
        return m;
    }
    public static int max(int... ints) {
        int m = ints[0];
        for (int i : ints) if (i > m) m = i;
        return m;
    }
    public static double min(double... dbls) {
        double m = dbls[0];
        for (double d : dbls) if (d < m) m = d;
        return m;
    }
    public static int min(int... ints) {
        int m = ints[0];
        for (int i : ints) if (i < m) m = i;
        return m;
    }


    public static int signClamp(int val) {
        return val < 0 ? -1 : 1;
    }
    public static int signClamp(double val) {
        return val < 0 ? -1 : 1;
    }
    public static boolean sameSign(double val1, double val2) {
        return signClamp(val1) == signClamp(val2);
    }

    public static double reciprocal(double input) {
        return MathEx.divide(1, input);
    }

    public static double square(double input) {
        return input*input;
    }
    public static double cube(double input) {
        return input*input*input;
    }

    public static double roc(double x1, double y1, double x2, double y2) {
        return (y2 - y1) / (x2 - x1);
    }
    public static double roc(Point one, Point two) {
        return roc(one.x, one.y, two.x, two.y);
    }

    public static double midpoint(double start, double end, double ratio) {
        return (start + end) / ratio;
    }
    public static int midpoint(int start, int end, int ratio) {
        return (int) midpoint((double) start, end, ratio);
    }
    public static double midpoint(double start, double end) {
        return midpoint(start, end, 2);
    }
    public static int midpoint(int start, int end) {
        return (int) midpoint((double) start, end);
    }
    public static int limMod(int input, int mod) {
        return input - (input % mod);
    }
    public static int nextMod(int input, int mod) {
        return input + (mod - (input % mod));
    }
    public static boolean divisible(int input, int mod) {
        return input % mod == 0;
    }



    // https://nic.schraudolph.org/pubs/Schraudolph99.pdf
    // old constant: 1072632447
    public static double fastExp(double x, double constant) {
        return Double.longBitsToDouble((long) (1512775 * x + constant) << 32);
    }
    public static double fastExp(double x) {
        return fastExp(x, DEF_EXP_C);
    }
    public static double fastLn(double x, double constant) {
        return ((Double.doubleToLongBits(x) >> 32) - constant) / 1512775;
    }
    public static double fastLn(double x) {
        return fastLn(x, DEF_EXP_C);
    }
    public static double fastPow(double a, double b, double c) {
        return fastExp(b*(fastLn(a, c)), c);
    }
    public static double fastPow(double a, double b) {
        return fastPow(a, b, DEF_EXP_C);
    }
    public static double fastTwoPow(double b) {
        if (b < 0) return MathEx.reciprocal(fastTwoPow(-b));
        int floored = (int) Math.floor(b);
        return fastTwoPow(floored) * (((((b - floored) + 1) * ((b - floored) + 1))*0.3319312963180252)+0.6699299210602);
    }
    public static double fastTwoPow(int b) {
        return (b < 0) ? MathEx.reciprocal(fastTwoPow(-b)) : 1 << b;
    }

    public static int threeDiv(long divisor) {
        return (int) ((0xAAAAAAABL * divisor) >>> 33);
    }
    public static int threeDiv(int divisor) {
        return threeDiv((long) divisor);
    }
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-util/src/commonMain/kotlin/androidx/compose/ui/util/MathHelpers.kt
    public static float fastCbrt(float x) {
        float estimate = Float.intBitsToFloat(0x2a510554 + threeDiv(Float.floatToRawIntBits(x) & 0x1FFFFFFFFL));
        estimate -= (estimate - x / (estimate*estimate)) * (0.33333333333333333333333333333333f);
        estimate -= (estimate - x / (estimate*estimate)) * (0.33333333333333333333333333333333f);
        return estimate;
    }

    public static double nonZero(double num, double rep) {
        return num == 0 ? rep : num;
    }
    public static double nonZero(double num) {
        return nonZero(num, 1);
    }
    public static double decimalPart(double num) {
        return num % 1.0;
    }
    public static double roundIfGreater(double num, double part) {
        return decimalPart(num) >= part ? Math.round(num) : Math.floor(num);
    }
    public static double roundIfLesser(double num, double part) {
        return decimalPart(num) <= part ? Math.round(num) : Math.ceil(num);
    }
    public static double roundIfThreshold(double num, double mPart, double gPart) {
        return decimalPart(num) >= gPart ? Math.ceil(num) : decimalPart(num) <= mPart ? Math.floor(num) : num;
    }

    public static boolean within(double x, double cmp, double percent) {
        return x > (cmp - (cmp * (percent - 1))) && x <= (cmp + (cmp * (percent - 1)));
    }

    public static long encode(int x, int y) {
        return (((long) x) << 32) | (y & 0xffffffffL);
    }

    public static double normalize(double num, double norm) {
        double normalized = num % norm;
        if (normalized < 0) normalized += norm;
        return normalized;
    }
    public static double normalize(double num) {
        return normalize(num, 1);
    }
    public static double normalizeAngle(double radians) {
        return normalize(radians, 2*Math.PI);
    }

    public static double deterministicRandom() {
        return generalRandom.nextDouble();
    }

    // Geometric operations
    public static Point distAsPt(Point pt1, Point pt2) {
        return new Point(pt2.x - pt1.x, pt2.y - pt1.y);
    }
    public static Point rotate(Point p, double radians) {
        double c = cos(radians), s = sin(radians);
        return new Point(roundInt(c * p.x - sin(radians) * p.y), roundInt(s * p.x + c * p.y));
    }
    public static double angleDelta(double a, double b) {
        return Math.min(Math.abs(a - b), (2*Math.PI) - Math.abs(a - b));
    }
    public static double diagonal(Rectangle rect) {
        return Math.hypot(rect.getWidth(), rect.getHeight());
    }


    // Vector math
    public static double cross(Line l1, Line l2) {
        return (l1.endX() - l1.startX()) * (l2.end().y - l2.start().y) - (l1.end().y - l1.start().y) * (l2.endX() - l2.startX());
    }
    public static double cross(Line l, Point p) {
        return (l.end().x - l.start().x) * (p.y - l.start().y) - (l.end().y - l.start().y) * (p.x - l.start().x);
    }

    // Statistics
    public static <T extends Number> double variance(List<T> values) {
        if (values.isEmpty()) return 0;
        double mean = CollectionsEx.average(values);
        return MathEx.divide(values.stream()
                .mapToDouble(v -> MathEx.square(v.doubleValue() - mean))
                .sum(), values.size());
    }
    /*
    public static <T extends Number> double variance(Stream<T> values) {
        if (StreamEx.empty(values)) return 0;
        BiTuple<Double, Long> statsData = StreamEx.statsData(values);
        return MathEx.divide(values
                .mapToDouble(v -> MathEx.square(v.doubleValue() - statsData.first()))
                .sum(), statsData.second());
    }*/
    public static <T extends Number> double sVariance(List<T> values) {
        return MathEx.divide(variance(values) * values.size(), values.size() - 1);
    }
    public static IntegerBounds iqrFences(Collection<? extends Number> values) {
        if (values.isEmpty() || values.size() < 4) return new IntegerBounds(0, 0);
        double[] a = values.stream().filter(Objects::nonNull)
                .mapToDouble(Number::doubleValue).sorted().toArray();
        double p1 = 0.25 * (a.length - 1), p3 = 0.75 * (a.length - 1);
        int i1 = MathEx.floorInt(p1), i3 = MathEx.floorInt(p3);
        double q1 = a[i1] + (p1 - i1) * (a[Math.min(i1 + 1, a.length - 1)] - a[i1]),
                q3 = a[i3] + (p3 - i3) * (a[Math.min(i3 + 1, a.length - 1)] - a[i3]);
        if (q3 - q1 <= 0.0) return new IntegerBounds(0, 0);
        return new IntegerBounds(MathEx.floorInt(q1 - 1.5 * (q3 - q1)), MathEx.ceilInt(q3 + 1.5 * (q3 - q1)));
    }



    // Calculus-based operations
    public static double curvature(double ax, double ay, double bx, double by, double cx, double cy) {
        double abx = bx - ax, aby = by - ay;
        double lab = Math.hypot(abx, aby), lbc = Math.hypot(cx - bx, cy - by), lca = Math.hypot(ax - cx, ay - cy);
        double denom = lab * lbc * lca;
        if (!(denom > 0)) return Double.NaN;
        return (2*(abx * (cy - ay) - aby * (cx - ax)))/denom;
    }

    // General geometric operations
    public static Point flip(Point pt) {
        return new Point(pt.y, pt.x);
    }



}

