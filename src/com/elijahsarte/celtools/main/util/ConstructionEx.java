package com.elijahsarte.celtools.main.util;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.noExcept;
import static com.elijahsarte.celtools.main.util.ReflectionEx.*;

public class ConstructionEx {

    private static final Map<ArrayResourceKey, ArrayDeque<Object>> ARRAY_RESOURCES = new HashMap<>();
    private static final Map<Object, ArrayResourceKey> LOCKED_ARRAY_RESOURCES = new WeakHashMap<>();

    public static void preAllocateArray(Class<?> componentType, int length) {
        preAllocateArrays(componentType, length, 1);
    }

    public static void preAllocateIntArrays(int length, int amount) {
        preAllocateArrays(int.class, length, amount);
    }

    public static void preAllocateLongArrays(int length, int amount) {
        preAllocateArrays(long.class, length, amount);
    }

    public static void preAllocateDoubleArrays(int length, int amount) {
        preAllocateArrays(double.class, length, amount);
    }

    public static void preAllocateFloatArrays(int length, int amount) {
        preAllocateArrays(float.class, length, amount);
    }

    public static void preAllocateByteArrays(int length, int amount) {
        preAllocateArrays(byte.class, length, amount);
    }

    public static void preAllocateShortArrays(int length, int amount) {
        preAllocateArrays(short.class, length, amount);
    }

    public static void preAllocateCharArrays(int length, int amount) {
        preAllocateArrays(char.class, length, amount);
    }

    public static void preAllocateBooleanArrays(int length, int amount) {
        preAllocateArrays(boolean.class, length, amount);
    }

    public static <T> void preAllocateObjectArrays(Class<T> componentType, int length, int amount) {
        preAllocateArrays(componentType, length, amount);
    }

    public static synchronized void preAllocateArrays(Class<?> componentType, int length, int amount) {
        ArrayResourceKey key = arrayResourceKey(componentType, length);
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
        ArrayDeque<Object> resources = ARRAY_RESOURCES.computeIfAbsent(key, k -> new ArrayDeque<>());
        for (int i = 0; i < amount; i++) {
            resources.add(Array.newInstance(componentType, length));
        }
    }

    public static synchronized Object getArray(Class<?> componentType, int length) {
        ArrayResourceKey key = arrayResourceKey(componentType, length);
        ArrayDeque<Object> resources = ARRAY_RESOURCES.computeIfAbsent(key, k -> new ArrayDeque<>());
        Object array = resources.pollFirst();
        if (array == null) array = Array.newInstance(componentType, length);
        LOCKED_ARRAY_RESOURCES.put(array, key);
        return array;
    }

    public static int[] getIntArray(int length) {
        return (int[]) getArray(int.class, length);
    }

    public static long[] getLongArray(int length) {
        return (long[]) getArray(long.class, length);
    }

    public static double[] getDoubleArray(int length) {
        return (double[]) getArray(double.class, length);
    }

    public static float[] getFloatArray(int length) {
        return (float[]) getArray(float.class, length);
    }

    public static byte[] getByteArray(int length) {
        return (byte[]) getArray(byte.class, length);
    }

    public static short[] getShortArray(int length) {
        return (short[]) getArray(short.class, length);
    }

    public static char[] getCharArray(int length) {
        return (char[]) getArray(char.class, length);
    }

    public static boolean[] getBooleanArray(int length) {
        return (boolean[]) getArray(boolean.class, length);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] getObjectArray(Class<T> componentType, int length) {
        return (T[]) getArray(componentType, length);
    }

    public static void giveBackArray(Object array) {
        giveBackArray(array, true);
    }

    public static synchronized void giveBackArray(Object array, boolean reset) {
        if (array == null || !array.getClass().isArray()) throw new IllegalArgumentException("array must be an array");
        ArrayResourceKey key = LOCKED_ARRAY_RESOURCES.remove(array);
        if (key == null) throw new IllegalArgumentException("array was not allocated or is not currently locked");
        if (reset) resetArray(array);
        ARRAY_RESOURCES.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(array);
    }

    public static synchronized int getAvailableArrayCount(Class<?> componentType, int length) {
        ArrayDeque<Object> resources = ARRAY_RESOURCES.get(arrayResourceKey(componentType, length));
        return resources == null ? 0 : resources.size();
    }

    public static synchronized int getTotalArrayCount(Class<?> componentType, int length) {
        ArrayResourceKey key = arrayResourceKey(componentType, length);
        int available = ARRAY_RESOURCES.getOrDefault(key, new ArrayDeque<>()).size();
        int locked = (int) LOCKED_ARRAY_RESOURCES.values().stream()
                .filter(key::equals)
                .count();
        return available + locked;
    }

    public static synchronized int getTotalArrayCount(Class<?> componentType) {
        if (componentType == null) throw new IllegalArgumentException("componentType must not be null");
        int available = ARRAY_RESOURCES.entrySet().stream()
                .filter(entry -> entry.getKey().componentType.equals(componentType))
                .mapToInt(entry -> entry.getValue().size())
                .sum();
        int locked = (int) LOCKED_ARRAY_RESOURCES.values().stream()
                .filter(key -> key.componentType.equals(componentType))
                .count();
        return available + locked;
    }

    public static synchronized int getTotalArrayCount() {
        return ARRAY_RESOURCES.values().stream().mapToInt(ArrayDeque::size).sum()
                + LOCKED_ARRAY_RESOURCES.size();
    }

    public static synchronized boolean isArrayLocked(Object array) {
        return LOCKED_ARRAY_RESOURCES.containsKey(array);
    }

    public static synchronized boolean forceReleaseArray(Object array) {
        if (array == null || !array.getClass().isArray()) throw new IllegalArgumentException("array must be an array");
        boolean released = LOCKED_ARRAY_RESOURCES.remove(array) != null;

        Class<?> componentType = array.getClass().getComponentType();
        int length = Array.getLength(array);
        ArrayDeque<Object> resources = ARRAY_RESOURCES.get(arrayResourceKey(componentType, length));
        if (resources != null && resources.removeIf(resource -> resource == array)) {
            released = true;
            if (resources.isEmpty()) ARRAY_RESOURCES.remove(arrayResourceKey(componentType, length));
        }

        return released;
    }

    public static int forceReleaseIntArrays(int length, int amount) {
        return forceReleaseArrays(int.class, length, amount);
    }

    public static int forceReleaseLongArrays(int length, int amount) {
        return forceReleaseArrays(long.class, length, amount);
    }

    public static int forceReleaseDoubleArrays(int length, int amount) {
        return forceReleaseArrays(double.class, length, amount);
    }

    public static int forceReleaseFloatArrays(int length, int amount) {
        return forceReleaseArrays(float.class, length, amount);
    }

    public static int forceReleaseByteArrays(int length, int amount) {
        return forceReleaseArrays(byte.class, length, amount);
    }

    public static int forceReleaseShortArrays(int length, int amount) {
        return forceReleaseArrays(short.class, length, amount);
    }

    public static int forceReleaseCharArrays(int length, int amount) {
        return forceReleaseArrays(char.class, length, amount);
    }

    public static int forceReleaseBooleanArrays(int length, int amount) {
        return forceReleaseArrays(boolean.class, length, amount);
    }

    public static <T> int forceReleaseObjectArrays(Class<T> componentType, int length, int amount) {
        return forceReleaseArrays(componentType, length, amount);
    }

    public static synchronized int forceReleaseArrays(Class<?> componentType, int length, int amount) {
        ArrayResourceKey key = arrayResourceKey(componentType, length);
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
        if (amount == 0) return 0;
        int released = forceReleaseAvailableArrays(key, amount);
        return released + forceReleaseLockedArrays(key, amount - released);
    }

    public static synchronized int forceReleaseAllArrays(Class<?> componentType, int length) {
        ArrayResourceKey key = arrayResourceKey(componentType, length);
        return forceReleaseAvailableArrays(key, Integer.MAX_VALUE) + forceReleaseLockedArrays(key, Integer.MAX_VALUE);
    }

    public static synchronized int forceReleaseAllArrays(Class<?> componentType) {
        if (componentType == null) throw new IllegalArgumentException("componentType must not be null");
        return forceReleaseAvailableArrays(componentType, Integer.MAX_VALUE) + forceReleaseLockedArrays(componentType, Integer.MAX_VALUE);
    }

    public static synchronized int forceReleaseAllArrays() {
        return forceReleaseAllResources();
    }

    public static synchronized int forceReleaseAllResources() {
        int released = ARRAY_RESOURCES.values().stream().mapToInt(ArrayDeque::size).sum() + LOCKED_ARRAY_RESOURCES.size();
        ARRAY_RESOURCES.clear();
        LOCKED_ARRAY_RESOURCES.clear();
        return released;
    }

    public static synchronized boolean resetArrayResource(Object array) {
        if (array == null || !array.getClass().isArray()) throw new IllegalArgumentException("array must be an array");
        boolean found = LOCKED_ARRAY_RESOURCES.containsKey(array);

        Class<?> componentType = array.getClass().getComponentType();
        int length = Array.getLength(array);
        ArrayDeque<Object> resources = ARRAY_RESOURCES.get(arrayResourceKey(componentType, length));
        if (resources != null) {
            for (Object resource : resources) {
                if (resource == array) {
                    found = true;
                    break;
                }
            }
        }

        if (found) resetArray(array);
        return found;
    }

    public static int resetIntArrays(int length, int amount) {
        return resetArrays(int.class, length, amount);
    }

    public static int resetLongArrays(int length, int amount) {
        return resetArrays(long.class, length, amount);
    }

    public static int resetDoubleArrays(int length, int amount) {
        return resetArrays(double.class, length, amount);
    }

    public static int resetFloatArrays(int length, int amount) {
        return resetArrays(float.class, length, amount);
    }

    public static int resetByteArrays(int length, int amount) {
        return resetArrays(byte.class, length, amount);
    }

    public static int resetShortArrays(int length, int amount) {
        return resetArrays(short.class, length, amount);
    }

    public static int resetCharArrays(int length, int amount) {
        return resetArrays(char.class, length, amount);
    }

    public static int resetBooleanArrays(int length, int amount) {
        return resetArrays(boolean.class, length, amount);
    }

    public static <T> int resetObjectArrays(Class<T> componentType, int length, int amount) {
        return resetArrays(componentType, length, amount);
    }

    public static synchronized int resetArrays(Class<?> componentType, int length, int amount) {
        ArrayResourceKey key = arrayResourceKey(componentType, length);
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
        if (amount == 0) return 0;
        int reset = resetAvailableArrays(key, amount);
        return reset + resetLockedArrays(key, amount - reset);
    }

    public static synchronized int resetAllArrays(Class<?> componentType, int length) {
        ArrayResourceKey key = arrayResourceKey(componentType, length);
        return resetAvailableArrays(key, Integer.MAX_VALUE) + resetLockedArrays(key, Integer.MAX_VALUE);
    }

    public static synchronized int resetAllArrays(Class<?> componentType) {
        if (componentType == null) throw new IllegalArgumentException("componentType must not be null");
        return resetAvailableArrays(componentType, Integer.MAX_VALUE) + resetLockedArrays(componentType, Integer.MAX_VALUE);
    }

    public static synchronized int resetAllArrays() {
        return resetAllResources();
    }

    public static synchronized int resetAllResources() {
        int reset = 0;
        for (ArrayDeque<Object> resources : ARRAY_RESOURCES.values()) {
            for (Object resource : resources) {
                resetArray(resource);
                reset++;
            }
        }
        for (Object resource : LOCKED_ARRAY_RESOURCES.keySet()) {
            resetArray(resource);
            reset++;
        }
        return reset;
    }

    public static void resetArray(Object array) {
        if (array instanceof Object[]) Arrays.fill((Object[]) array, null);
        else if (array instanceof int[]) Arrays.fill((int[]) array, 0);
        else if (array instanceof long[]) Arrays.fill((long[]) array, 0L);
        else if (array instanceof double[]) Arrays.fill((double[]) array, 0D);
        else if (array instanceof float[]) Arrays.fill((float[]) array, 0F);
        else if (array instanceof byte[]) Arrays.fill((byte[]) array, (byte) 0);
        else if (array instanceof short[]) Arrays.fill((short[]) array, (short) 0);
        else if (array instanceof char[]) Arrays.fill((char[]) array, '\0');
        else if (array instanceof boolean[]) Arrays.fill((boolean[]) array, false);
        else if (array == null || !array.getClass().isArray()) throw new IllegalArgumentException("array must be an array");
    }

    private static ArrayResourceKey arrayResourceKey(Class<?> componentType, int length) {
        if (componentType == null) throw new IllegalArgumentException("componentType must not be null");
        if (length < 0) throw new NegativeArraySizeException("length must not be negative");
        return new ArrayResourceKey(componentType, length);
    }

    private static int forceReleaseAvailableArrays(ArrayResourceKey key, int amount) {
        ArrayDeque<Object> resources = ARRAY_RESOURCES.get(key);
        if (resources == null) return 0;

        int released = 0;
        while (released < amount && !resources.isEmpty()) {
            resources.removeLast();
            released++;
        }
        if (resources.isEmpty()) ARRAY_RESOURCES.remove(key);
        return released;
    }

    private static int forceReleaseAvailableArrays(Class<?> componentType, int amount) {
        int released = 0;
        java.util.Iterator<Map.Entry<ArrayResourceKey, ArrayDeque<Object>>> entries = ARRAY_RESOURCES.entrySet().iterator();
        while (entries.hasNext() && released < amount) {
            Map.Entry<ArrayResourceKey, ArrayDeque<Object>> entry = entries.next();
            if (!entry.getKey().componentType.equals(componentType)) continue;

            ArrayDeque<Object> resources = entry.getValue();
            while (released < amount && !resources.isEmpty()) {
                resources.removeLast();
                released++;
            }
            if (resources.isEmpty()) entries.remove();
        }
        return released;
    }

    private static int forceReleaseLockedArrays(ArrayResourceKey key, int amount) {
        int released = 0;
        java.util.Iterator<Map.Entry<Object, ArrayResourceKey>> entries = LOCKED_ARRAY_RESOURCES.entrySet().iterator();
        while (entries.hasNext() && released < amount) {
            Map.Entry<Object, ArrayResourceKey> entry = entries.next();
            if (!key.equals(entry.getValue())) continue;
            entries.remove();
            released++;
        }
        return released;
    }

    private static int forceReleaseLockedArrays(Class<?> componentType, int amount) {
        int released = 0;
        java.util.Iterator<Map.Entry<Object, ArrayResourceKey>> entries = LOCKED_ARRAY_RESOURCES.entrySet().iterator();
        while (entries.hasNext() && released < amount) {
            Map.Entry<Object, ArrayResourceKey> entry = entries.next();
            if (!entry.getValue().componentType.equals(componentType)) continue;
            entries.remove();
            released++;
        }
        return released;
    }

    private static int resetAvailableArrays(ArrayResourceKey key, int amount) {
        ArrayDeque<Object> resources = ARRAY_RESOURCES.get(key);
        if (resources == null) return 0;

        int reset = 0;
        for (Object resource : resources) {
            if (reset >= amount) break;
            resetArray(resource);
            reset++;
        }
        return reset;
    }

    private static int resetAvailableArrays(Class<?> componentType, int amount) {
        int reset = 0;
        for (Map.Entry<ArrayResourceKey, ArrayDeque<Object>> entry : ARRAY_RESOURCES.entrySet()) {
            if (reset >= amount) break;
            if (!entry.getKey().componentType.equals(componentType)) continue;

            for (Object resource : entry.getValue()) {
                if (reset >= amount) break;
                resetArray(resource);
                reset++;
            }
        }
        return reset;
    }

    private static int resetLockedArrays(ArrayResourceKey key, int amount) {
        int reset = 0;
        for (Map.Entry<Object, ArrayResourceKey> entry : LOCKED_ARRAY_RESOURCES.entrySet()) {
            if (reset >= amount) break;
            if (!key.equals(entry.getValue())) continue;
            resetArray(entry.getKey());
            reset++;
        }
        return reset;
    }

    private static int resetLockedArrays(Class<?> componentType, int amount) {
        int reset = 0;
        for (Map.Entry<Object, ArrayResourceKey> entry : LOCKED_ARRAY_RESOURCES.entrySet()) {
            if (reset >= amount) break;
            if (!entry.getValue().componentType.equals(componentType)) continue;
            resetArray(entry.getKey());
            reset++;
        }
        return reset;
    }

    public static String fastToString(byte[] buf, int length) {
        String s = construct(String.class);
        noExcept(() -> setField(s, "value", buf));
        if (hasField(s, "count")) noExcept(() -> setField(s, "count", length));
        return s;
    }
    public static String fastToString(byte[] buf) {
        return fastToString(buf, buf.length);
    }

    public static String fastToString(CharSequence cs) {
        return hasField(cs, "value") ? fastToString(noExcept(() -> getField(cs, "value")), cs.length()) : null;
    }

    public static <T> T mutateCopy(T t, Consumer<T> mutator) {
        return ProgrammingEx.varMutate(Clone(t), mutator);
    }

    @SuppressWarnings("unchecked")
    public static <T> T Clone(T t) {
        return (T) ReflectionEx.callMethod(t, "clone");
    }




    private static class ArrayResourceKey {
        private final Class<?> componentType;
        private final int length;

        private ArrayResourceKey(Class<?> componentType, int length) {
            this.componentType = componentType;
            this.length = length;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ArrayResourceKey)) return false;
            ArrayResourceKey that = (ArrayResourceKey) o;
            return length == that.length && componentType.equals(that.componentType);
        }

        @Override
        public int hashCode() {
            return 31 * componentType.hashCode() + length;
        }
    }
}
