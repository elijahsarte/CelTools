package com.elijahsarte.celtools.main.util.math;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.mathbase.constructs.Function;
import com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic.Division;
import com.elijahsarte.celtools.main.util.mathbase.operations.operationsbasic.Subtraction;
import com.elijahsarte.celtools.main.util.mathbase.operations.operationsfn.CapitalPi;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LagrangeBasis extends Function {

    private final Map<String, Double> fnInputs;

    public LagrangeBasis(List<Double> dataPoints, int polynomialDegree, int baseIndex) {
        super(List.of(
                List.of(
                        (scope) -> scope.defineVar("baseFn", new Function(
                                List.of(
                                        List.of(
                                                (bScope) -> bScope.linkVar("tj", bScope.getVarRaw("t" + bScope.getVar("j").getInt()))
                                        ),
                                        List.of(
                                                (bScope) -> bScope.defineVar("bottom", new Subtraction("ti", "tj")),
                                                (bScope) -> bScope.defineVar("top", new Subtraction("t", "tj"))
                                        ),
                                        List.of(
                                                (bScope) -> bScope.returnFn(new Division("top", "bottom"))
                                        )
                                )))
                ),
                List.of(
                        (scope) -> scope.returnFn(new CapitalPi(scope.getVar("baseFn").getFn(), "j")
                                .operate(scope, 0, polynomialDegree, x -> x != baseIndex))
                )
        ), Stream.concat(
                Stream.of("t", "ti"),
                IntStream.rangeClosed(0, polynomialDegree).mapToObj(i -> "t" + i)).toList());


        this.fnInputs = ProgrammingEx.varMutate(new HashMap<>(polynomialDegree + 3), m -> {
            IntStream.rangeClosed(0, polynomialDegree).forEach(i -> m.put("t" + i, dataPoints.get(i)));
            m.put("ti", dataPoints.get(baseIndex));
        });
    }
    public LagrangeBasis(double[] dataPoints, int polynomialDegree, int baseIndex) {
        this(CollectionsEx.toBoxedList(dataPoints), polynomialDegree, baseIndex);
    }

    @Override
    public double operate(double valInput) {
        this.fnInputs.put("t", valInput);
        return super.operate(this.fnInputs);
    }


    public static void main(String[] args) {
        // expected result: -0.03546910755148741418764302059497
        LagrangeBasis basis = new LagrangeBasis(new double[] { 7, 19, 26, 53 }, 3, 0);
        System.out.println(basis.operate(22d));
        // expected result: -0.111111111111111111
        LagrangeBasis basis1 = new LagrangeBasis(new double[] { 8, 11, 16, 17 }, 3, 0);
        System.out.println(basis1.operate(13d));

    }
}

