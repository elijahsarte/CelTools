package com.elijahsarte.celtools.main.util.structures.collections;

public record IntCipher(int cipher, int threshold, boolean greater) {
    // takes index given index and equalizes it to shifted index (so minus not plus)
    public int apply(int num) {
        return (greater ? num > threshold : num < threshold) ? num + cipher : num;
    }
}

