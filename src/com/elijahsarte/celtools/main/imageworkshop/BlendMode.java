package com.elijahsarte.celtools.main.imageworkshop;

public enum BlendMode {
    NORMAL {
        @Override
        protected int blend8(int backdrop, int source) {
            return source;
        }

        @Override
        public int composite(int backdropArgb, int sourceArgb) {
            final int sa = alpha8(sourceArgb);
            if (sa == 0) return backdropArgb;
            if (sa == 255) return sourceArgb;

            final int ba = alpha8(backdropArgb);
            if (ba == 0) return sourceArgb;

            final int br = red8(backdropArgb);
            final int bg = green8(backdropArgb);
            final int bb = blue8(backdropArgb);

            final int sr = red8(sourceArgb);
            final int sg = green8(sourceArgb);
            final int sb = blue8(sourceArgb);

            final int invSa = 255 - sa;
            final int alphaNumerator = sa * 255 + ba * invSa;
            if (alphaNumerator <= 0) return 0;

            final int outA = div255Round(alphaNumerator);

            final int outR = divRound(((long) sa * 255L * sr) + ((long) ba * invSa * br), alphaNumerator);
            final int outG = divRound(((long) sa * 255L * sg) + ((long) ba * invSa * bg), alphaNumerator);
            final int outB = divRound(((long) sa * 255L * sb) + ((long) ba * invSa * bb), alphaNumerator);

            return Filter.argb(outA, outR, outG, outB);
        }
    },

    MULTIPLY {
        @Override
        protected int blend8(int backdrop, int source) {
            return MULTIPLY_LUT[lutIndex(backdrop, source)];
        }
    },

    SCREEN {
        @Override
        protected int blend8(int backdrop, int source) {
            return SCREEN_LUT[lutIndex(backdrop, source)];
        }
    },

    OVERLAY {
        @Override
        protected int blend8(int backdrop, int source) {
            return OVERLAY_LUT[lutIndex(backdrop, source)];
        }
    },

    DARKEN {
        @Override
        protected int blend8(int backdrop, int source) {
            return backdrop < source ? backdrop : source;
        }
    },

    LIGHTEN {
        @Override
        protected int blend8(int backdrop, int source) {
            return backdrop > source ? backdrop : source;
        }
    },

    ADD {
        @Override
        protected int blend8(int backdrop, int source) {
            final int sum = backdrop + source;
            return sum >= 255 ? 255 : sum;
        }
    },

    SUBTRACT {
        @Override
        protected int blend8(int backdrop, int source) {
            final int diff = backdrop - source;
            return diff <= 0 ? 0 : diff;
        }
    },

    DIFFERENCE {
        @Override
        protected int blend8(int backdrop, int source) {
            final int diff = backdrop - source;
            return diff < 0 ? -diff : diff;
        }
    };

    private static final int[] MULTIPLY_LUT = buildMultiplyLut();
    private static final int[] SCREEN_LUT = buildScreenLut();
    private static final int[] OVERLAY_LUT = buildOverlayLut();

    protected abstract int blend8(int backdrop, int source);

    public int composite(int backdropArgb, int sourceArgb) {
        final int sa = alpha8(sourceArgb);
        if (sa == 0) return backdropArgb;

        final int ba = alpha8(backdropArgb);
        if (ba == 0) return sourceArgb;

        final int br = red8(backdropArgb);
        final int bg = green8(backdropArgb);
        final int bb = blue8(backdropArgb);

        final int sr = red8(sourceArgb);
        final int sg = green8(sourceArgb);
        final int sb = blue8(sourceArgb);

        final int invSa = 255 - sa;
        final int invBa = 255 - ba;

        if (sa == 255) {
            final int outR = div255Round(invBa * sr + ba * blend8(br, sr));
            final int outG = div255Round(invBa * sg + ba * blend8(bg, sg));
            final int outB = div255Round(invBa * sb + ba * blend8(bb, sb));
            return Filter.argb(255, outR, outG, outB);
        }

        final int alphaNumerator = sa * 255 + ba * invSa;
        if (alphaNumerator <= 0) return 0;

        final int outA = div255Round(alphaNumerator);

        final int outR = compositeChannel(br, sr, ba, sa, invBa, invSa, alphaNumerator);
        final int outG = compositeChannel(bg, sg, ba, sa, invBa, invSa, alphaNumerator);
        final int outB = compositeChannel(bb, sb, ba, sa, invBa, invSa, alphaNumerator);

        return Filter.argb(outA, outR, outG, outB);
    }

    private int compositeChannel(
            int backdropChannel,
            int sourceChannel,
            int ba,
            int sa,
            int invBa,
            int invSa,
            int alphaNumerator
    ) {
        final int blended = blend8(backdropChannel, sourceChannel);

        final long numerator =
                (long) sa * invBa * sourceChannel +
                        (long) sa * ba * blended +
                        (long) ba * invSa * backdropChannel;

        return divRound(numerator, alphaNumerator);
    }

    private static int alpha8(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int red8(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int green8(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private static int blue8(int argb) {
        return argb & 0xFF;
    }

    private static int lutIndex(int backdrop, int source) {
        return (backdrop << 8) | source;
    }

    private static int div255Round(int value) {
        return (value + 127) / 255;
    }

    private static int divRound(long numerator, int denominator) {
        return (int) ((numerator + (denominator >> 1)) / denominator);
    }

    private static int[] buildMultiplyLut() {
        final int[] lut = new int[256 * 256];
        for (int b = 0; b < 256; b++) {
            final int row = b << 8;
            for (int s = 0; s < 256; s++) {
                lut[row | s] = div255Round(b * s);
            }
        }
        return lut;
    }

    private static int[] buildScreenLut() {
        final int[] lut = new int[256 * 256];
        for (int b = 0; b < 256; b++) {
            final int row = b << 8;
            final int ib = 255 - b;
            for (int s = 0; s < 256; s++) {
                lut[row | s] = 255 - div255Round(ib * (255 - s));
            }
        }
        return lut;
    }

    private static int[] buildOverlayLut() {
        final int[] lut = new int[256 * 256];
        for (int b = 0; b < 256; b++) {
            final int row = b << 8;
            if (b <= 127) {
                for (int s = 0; s < 256; s++) {
                    int v = div255Round(2 * b * s);
                    lut[row | s] = v > 255 ? 255 : v;
                }
            } else {
                final int ib = 255 - b;
                for (int s = 0; s < 256; s++) {
                    lut[row | s] = 255 - div255Round(2 * ib * (255 - s));
                }
            }
        }
        return lut;
    }
}