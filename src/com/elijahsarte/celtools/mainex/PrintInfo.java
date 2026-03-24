package com.elijahsarte.celtools.mainex;

import com.elijahsarte.celtools.main.util.MathEx;

import java.util.*;
import java.util.stream.IntStream;

public class PrintInfo {


    private static final int[][][] allStrs = {
            {
                    { // 0
                            14, 85, 1, // C
                            -1, 112, 0, // o
                            -5, 117, 0, // p
                            -1, 122, 0, // y
                            -82, 196, 0, // r
                            81, 24, 0, // i
                            20, 83, 0, // g
                            93, 11, 0, // h
                            24, 92, 0, // t
                            -71, 103, 0, //
                            42, -2, 0, // (
                            -35, 134, 0, // c
                            67, -26, 0, // )
                    },
                    { // 1
                            60, 41, 1, // E
                            49, 59, 0, // l
                            22, 83, 0, // i
                            -15, 121, 0, // j
                            -84, 181, 0, // a
                            5, 99, 0, // h
                            12, 20, 0, //
                            -66, 181, 1, // S
                            -12, 109, 0, // a
                            -22, 136, 0, // r
                            -10, 126, 0, // t
                            -61, 162, 0, // e
                    },
                    { // 2
                            -84, 116, 0, //
                            -73, 123, 0, // 2
                            -70, 118, 0, // 0
                            37, 13, 0, // 2
                            -59, 112, 0, // 5
                            -9, 41, 0, //
                    },
                    { // 3
                            82, -50, 0, //
                            -29, 143, 0, // r
                            -15, 120, 0, // i
                            47, 56, 0, // g
                            97, 7, 0, // h
                            -57, 173, 0, // t
                            77, 38, 0, // s
                            -67, 99, 0, //
                            -21, 135, 0, // r
                            69, 32, 0, // e
                            -64, 179, 0, // s
                            -2, 103, 0, // e
                            -64, 178, 0, // r
                            -56, 174, 0, // v
                            -42, 143, 0, // e
                            22, 78, 0, // d
                            33, 13, 0, // .
                    },
                    { // 4
                            98, -52, 0, // .
                            45, -13, 0, //
                            89, 8, 1, // A
                            -1, 109, 0, // l
                            63, 45, 0, // l
                    }
            },
            {
                    { // 0
                            -53, 99, 0, // .
                            41, 65, 0, // j
                            -100, 197, 0, // a
                            13, 105, 0, // v
                            55, 42, 0, // a
                            34, -2, 0, //
                    },
                    { // 1
                            -77, 122, 0, // -
                            46, -14, 0, //
                    },
                    { // 2
                            0, 115, 1, // S
                            56, 48, 1, // H
                            -69, 166, 1, // A
                            -87, 140, 0, // 5
                    },
                    { // 3
                        -90, 139, 0, // 1
                        -43, 93, 0, // 2
                        78, -46, 0, //
                        -79, 178, 0, // c
                        22, 82, 0, // h
                        5, 96, 0, // e
                        50, 49, 0, // c
                        14, 93, 0, // k
                        41, 74, 0, // s
                        -54, 171, 0, // u
                        74, 35, 0, // m
                        -64, 96, 0, //
                    },
                    { // 4
                        -72, 181, 1, // M
                        -18, 115, 0, // a
                        -76, 181, 0, // i
                        -33, 143, 0, // n
                    }
            }
    };
    private static final int[][] allStrsOrder = new int[][] {
            new int[] { 0, 2, 1, 4, 3 },
            new int[] { 4, 0, 2, 3, 1 }
    };


    /**
     * Int[] generation code:
     * var string = "Main";
     * var intOutput = "";
     * string.split("").forEach(char => {
     * var firstVar = Math.floor(Math.random()*201-100);
     * intOutput += `${firstVar}, ${secondVar = char.toLowerCase().charCodeAt(0) - firstVar}, ${char.toLowerCase() !== char ? 1 : 0}, // ${char}\n`;
     * });
     * intOutput;
     */
    public static String print() {

        StringJoiner info = new StringJoiner("\n");
        IntStream.range(0, allStrs.length).forEach(j -> {
            StringBuilder thisLine = new StringBuilder();
            Arrays.stream(allStrsOrder[j]).forEach(o -> {
                int[] strPart = allStrs[j][o];
                List<String> chars = new ArrayList<>();
                IntStream.range(0, strPart.length)
                        .filter(i -> i % 3 == 0)
                        .forEachOrdered(i -> chars.add(String.valueOf((char) (strPart[i] + strPart[i + 1]))));
                IntStream.range(0, strPart.length)
                        .filter(i -> i % 3 == 2)
                        .filter(i -> strPart[i] == 1)
                        .forEachOrdered(i -> chars.set(i / 3, chars.get(i / 3).toUpperCase(Locale.US)));
                thisLine.append(String.join("", chars));
            });
            info.add(thisLine.toString());
        });
//        System.out.println("Copyright (c) 2025 Elijah Sarte. All rights reserved.");
//        System.out.println("Main.java SHA512 checksum - ");
        for (int[][] line : allStrs) {
            for (int[] part : line) {
                IntStream.range(0, part.length)
                        .filter(i -> i % 3 != 2)
                        .forEach(c -> part[c] = MathEx.rand(-100, 100));
                IntStream.range(0, part.length)
                        .filter(i -> i % 3 == 2)
                        .forEach(c -> part[c] = MathEx.rand(0, 1));
            }
        }

        System.out.println(info);
        System.out.print("\n\n");
        return info.toString();
    }

}

