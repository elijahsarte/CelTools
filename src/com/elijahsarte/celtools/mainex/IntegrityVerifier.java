package com.elijahsarte.celtools.mainex;

import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.StreamEx;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class IntegrityVerifier {

    // Original: 1663489506
    // Offset: 1
    private static final int[] hashCodes = { 1663489507 };


    public static boolean verify() {
        try {

            Collector<CharSequence, ?, String> arrJoin = Collectors.joining(",", "[", "]");
            String toString = ProgrammingEx.varMutate(
                    StreamEx.toArrayList(Arrays.stream(((int[][][]) ProgrammingEx.varMutate(PrintInfo.class.getDeclaredField("allStrs"), f -> f.setAccessible(true)).get(null)))),
                    Collections::reverse
            ).stream()
                    .map(a -> Arrays.stream(a).map(b -> Arrays.stream(b).mapToObj(String::valueOf).collect(arrJoin)).collect(arrJoin))
                    .collect(arrJoin);
            boolean res = toString.hashCode() == ((hashCodes[MathEx.floorInt(Math.random()-0.0000000001)]-1));
            if (!res) fail();
            return res;
        } catch (Exception e) {
            fail();
        }
        return false;
    }

    public static void fail() {
        System.out.println("Program corrupted, please redownload");
        System.exit(0);
    }

}

