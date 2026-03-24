package com.elijahsarte.celtools.main.util.math;

import static com.elijahsarte.celtools.main.util.CollectionsEx.*;

import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.mathbase.constructs.Function;
import com.elijahsarte.celtools.main.util.mathbase.operations.operationsfn.Sigma;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LagrangeInterpolation {

    private final Function lagrangeInterpolFunction;

    private final int polynomialDegree;
    private final TreeMap<Number, Number> dataPoints;

    private List<Double> evalDataPoints;


    public LagrangeInterpolation(TreeMap<?, ?> dataPoints, int polynomialDegree) {
        this.dataPoints = (TreeMap<Number, Number>) dataPoints;
        this.polynomialDegree = polynomialDegree;
        this.lagrangeInterpolFunction = new Function(
                List.of(
                        List.of(
                                (scope) -> scope.defineVar("eachIter", new Function(
                                        List.of(
                                                List.of(
                                                        (iScope) -> iScope.linkVar("ti", iScope.getVarRaw("t" + iScope.getVar("i").getInt())),
                                                        (iScope) -> iScope.linkVar("tiy", iScope.getVarRaw("t" + iScope.getVar("i").getInt() + "y")),
                                                        (iScope) -> iScope.defineVar("lagrangeFn", new LagrangeBasis(evalDataPoints, polynomialDegree, iScope.getVar("i").getInt()))
                                                ),
                                                List.of(
                                                        (iScope) -> iScope.returnFn(iScope.getVar("tiy").mult(iScope.getVar("lagrangeFn").getFn().operate(iScope.getVar("t").getDbl())))
                                                )
                                        )
                                ))
                        ),
                        List.of(
                                (scope) -> scope.returnFn(new Sigma(scope.getVar("eachIter").getFn(), "i")
                                        .operate(scope, 0, polynomialDegree))
                        )
                ), Stream.concat(
                        Stream.of("t"),
                        Stream.concat(
                                IntStream.rangeClosed(0, this.polynomialDegree).mapToObj(i -> "t" + i),
                                IntStream.rangeClosed(0, this.polynomialDegree).mapToObj(i -> "t" + i + "y"))
                ).toList()
        );
    }


    public double operate(double valInput) {

        if (this.polynomialDegree == 0) return firstValue(this.dataPoints).doubleValue();

        List<Double> closestDataKeys = new ArrayList<>(this.polynomialDegree + 1);

        boolean iterLower = true, iterUpper = true;
        double lastLower = valInput, lastUpper = valInput;

        Number numObjComp = this.dataPoints.firstKey();
        java.util.function.Function<Double, Number> toNum = d -> MathEx.castNum(numObjComp, d);

        while (closestDataKeys.size() < this.polynomialDegree + 1) {
            if (iterUpper) {
                Double upper = MathEx.dblObjNumObj(this.dataPoints.higherKey(toNum.apply(lastUpper)));
                if (upper != null) {
                    closestDataKeys.add(upper);
                    lastUpper = upper;
                }
                iterUpper = upper != null;
            }
            if (iterLower) {
                Double lower = MathEx.dblObjNumObj(this.dataPoints.lowerKey(toNum.apply(lastLower)));
                if (lower != null) {
                    closestDataKeys.add(0, lower);
                    lastLower = lower;
                }
                iterLower = lower != null;
            }
        }

        Map<String, Double> inputs = new HashMap<>(closestDataKeys.size() + 1);
        ProgrammingEx.forNumber(i -> ProgrammingEx.varExec(
            closestDataKeys.get(i),
            retrievedIndex -> {
                inputs.put("t" + i, retrievedIndex);
                inputs.put("t" + i + "y", MathEx.dblNumObj(this.dataPoints.get(toNum.apply(retrievedIndex))));
            }
        ), closestDataKeys.size());
        inputs.put("t", valInput);

        this.evalDataPoints = closestDataKeys;
        return this.lagrangeInterpolFunction.operate(inputs);
    }


    public static void main(String[] args) {
        // expected value: 14718
        LagrangeInterpolation interpolation = new LagrangeInterpolation(new TreeMap<>(Map.ofEntries(
                Map.entry(8d, 3703d),
                Map.entry(11d, 9118d),
                Map.entry(16d, 26823d),
                Map.entry(17d, 31990d)
        )), 3);
        System.out.println(interpolation.operate(13));

        // orig function: 6x^3+9x^2+78x+902
        // expected value (22) -> 70862
        LagrangeInterpolation interpolation2 = new LagrangeInterpolation(new TreeMap<>(Map.ofEntries(
                Map.entry(21d, 62075d),
                Map.entry(23d, 80459d),
                Map.entry(27d, 127667d),
                Map.entry(32d, 209222d)
        )), 3);
        System.out.println(interpolation2.operate(22));

        // same thing but with more than 4 vals
        // expected vals respectively: 57974, 80459, 190715, 497939
        LagrangeInterpolation interpolation3 = new LagrangeInterpolation(new TreeMap<>(Map.ofEntries(
                Map.entry(20d, 54062d),
                Map.entry(21d, 62075d),
                Map.entry(28d, 141854d),
                Map.entry(37d, 320027d),
                Map.entry(45d, 569387d)
        )),3);
        System.out.println(interpolation3.operate(20.5));
        System.out.println(interpolation3.operate(23));
        System.out.println(interpolation3.operate(31));
        System.out.println(interpolation3.operate(43));
    }
    
}

