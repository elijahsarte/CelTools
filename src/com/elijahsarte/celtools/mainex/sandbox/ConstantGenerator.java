package com.elijahsarte.celtools.mainex.sandbox;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.MathEx;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class ConstantGenerator {


    static int[] bounds = new int[] { -300, 300 };
    static Point origin = new Point(0, 0);

    static int attemptsToRun = 200;
    static int attemptsRequiredForMAD = 10;
    static int attempts = 1;
    static double currSumWeight = 1;
    static double currMultWeight = 1;
    static double currEuclidWeight = 1;
    static double euclidMadAvg = 1;
    public static void main(String[] args) {

        if (attempts > attemptsToRun) {
            System.out.println(MathEx.divide(currSumWeight, attempts));
            System.out.println(MathEx.divide(currMultWeight, attempts));
            System.out.println(MathEx.divide(currEuclidWeight, attempts));
            return;
        }

        List<Point2D> herePts = new ArrayList<>();
        List<Integer> sums = new ArrayList<>();
        List<Integer> mults = new ArrayList<>();
        List<Double> euclids = new ArrayList<>();
        int pointsToGen = (int) ((Math.random() * (200 - 20)) + 20);
        int closestTarget = (int) (Math.random() * pointsToGen);
        for (int i = 0; i < pointsToGen; i++) {
            int x = (int) ((Math.random() * bounds[1]) + bounds[0]);
            int y = (int) ((Math.random() * bounds[1]) + bounds[0]);
            herePts.add(new Point(x, y));
            sums.add(x + y);
            mults.add(x * y);
            euclids.add(Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2)));
        }
        Point2D closestTargetPt = herePts.get(closestTarget);
        List<Double> actualEuclids = herePts.stream().map(closestTargetPt::distance).toList();
        int lowestIdx = 0;
        double currLowest = Double.MAX_VALUE;
        for (int currIdx = 0; currIdx < actualEuclids.size(); currIdx++) {
            double elem = actualEuclids.get(currIdx);
            if (elem < currLowest) {
                currLowest = elem;
                lowestIdx = currIdx;
            }
        }

        Point2D indeedClosest = herePts.get(lowestIdx);
        Collections.sort(sums);
        Collections.sort(mults);
        Collections.sort(euclids);
        
        int closestSum = (int) (indeedClosest.getX() + indeedClosest.getY());
        int closestMult = (int) (indeedClosest.getX() * indeedClosest.getY());
        double closestEuclid = Math.sqrt(Math.pow(indeedClosest.getX(), 2) + Math.pow(indeedClosest.getY(), 2));
        
        int sumPlace = sums.indexOf(closestSum);
        int multPlace = mults.indexOf(closestMult);
        double euclidPlace = euclids.indexOf(closestEuclid);

        double hereSumWeight = sumPlace == 0 ? 1 : (1 - MathEx.divide(sumPlace, sums.size()));
        double hereMultWeight = multPlace == 0 ? 1 : (1 - MathEx.divide(multPlace, mults.size()));
        double hereEuclidWeight = euclidPlace == 0 ? 1: (1 - MathEx.divide(euclidPlace, closestEuclid));
        euclidMadAvg = MathEx.divide(euclidMadAvg + CollectionsEx.mad(euclids), attempts);
        Function<Double, Double> weightFactorHere = (weight) -> {
            if (weight == 1) return 1d;
            return (1 - (attempts >= attemptsRequiredForMAD ? (Math.min(1, Math.exp(-15*weight))) : 0));
        };

        currSumWeight += (hereSumWeight * weightFactorHere.apply(hereSumWeight));
        currMultWeight += (hereMultWeight * weightFactorHere.apply(hereMultWeight));
        currEuclidWeight += (hereEuclidWeight * weightFactorHere.apply(hereEuclidWeight));

        attempts++;
        main(args);
    }
}

