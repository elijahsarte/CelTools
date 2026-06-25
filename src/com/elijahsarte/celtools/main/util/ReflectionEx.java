package com.elijahsarte.celtools.main.util;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;

@SuppressWarnings("unchecked")
public final class ReflectionEx {

    private static long classModuleOffset = 80L;
    private static Unsafe unsafe = null;
    private static Object inUnsafe = null;

    private static final int LOW_CACHE = -128, HIGH_CACHE =
            (((Runtime.getRuntime().maxMemory()
                    - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())) / 6L)
                    >= ((16_777_216L + 128L) * 28L))
                    ? 16_777_216
                    : 700_000;

    static {
        try {
            unsafe = (Unsafe) setAccessible(Unsafe.class.getDeclaredField("theUnsafe")).get(null);
            inUnsafe = setAccessible(Unsafe.class.getDeclaredField("theInternalUnsafe")).get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            classModuleOffset = unsafe.objectFieldOffset(Class.class.getDeclaredField("module"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Class<?> reflectionClass = Class.forName("jdk.internal.reflect.Reflection");
            setFieldUnsafe(reflectionClass, reflectionClass, "fieldFilterMap", new HashMap<>());
            setFieldUnsafe(reflectionClass, reflectionClass, "methodFilterMap", new HashMap<>());
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            unsafe.getAndSetObject(ReflectionEx.class, classModuleOffset, Class.class.getModule());
            Class<?> cacheClass = Class.forName("java.lang.Integer$IntegerCache");
            Integer[] cache = new Integer[HIGH_CACHE - LOW_CACHE + 1];
            for (int i = LOW_CACHE; i <= HIGH_CACHE; i++) {
                cache[i - LOW_CACHE] = new Integer(i);
            }
            setStaticFieldUnsafe(cacheClass, "archivedCache", cache);
            setStaticFieldUnsafe(cacheClass, "cache", cache);
            setStaticFieldUnsafe(cacheClass, "low", LOW_CACHE);
            setStaticFieldUnsafe(cacheClass, "high", HIGH_CACHE);
            System.out.println("high cache: " + HIGH_CACHE + ", low cache: " + LOW_CACHE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static <T> Supplier<T> getConstructorReference(T obj) {
        return () -> {
            try {
                return (T) obj.getClass().getConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
    public static <T> T construct(Class<T> clazz) {
        return noExcept(() -> clazz.getConstructor().newInstance());
    }


    public static Field[] getFields(Object instance) {
        return instance.getClass().getDeclaredFields();
    }
    public static List<String> getFieldNames(Object instance) {
        return Arrays.stream(getFields(instance)).map(Field::getName).toList();
    }
    public static Method[] getMethods(Object instance) {
        return instance.getClass().getDeclaredMethods();
    }

    private static Field[] getAllFields(Class<?> clazz, Stream<Field> stream) {
        if (clazz == null) return stream.toArray(Field[]::new);
        return getAllFields(
                clazz.getSuperclass(),
                Stream.concat(
                        stream,
                        Arrays.stream(clazz.getDeclaredFields())
                                .peek(f -> f.setAccessible(true))
                ));
    }
    public static Field[] getAllFields(Object instance) {
        return getAllFields(instance.getClass(), null);
    }
    private static Method[] getAllMethods(Class<?> clazz, Stream<Method> stream) {
        if (clazz == null) return stream.toArray(Method[]::new);
        return getAllMethods(clazz.getSuperclass(),
                Stream.concat(
                        Optional.ofNullable(stream).orElse(Stream.empty()),
                        Arrays.stream(clazz.getDeclaredMethods())
                                .filter(m -> Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers()))
                ));
    }
    public static Method[] getAllMethods(Object instance) {
        return getAllMethods(instance.getClass(), null);
    }

    public static boolean hasField(Object instance, Field field) {
        return field.getDeclaringClass().isAssignableFrom(instance.getClass());
    }
    public static boolean hasField(Object instance, String fieldName) {
        try {
            instance.getClass().getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return false;
        } catch (Exception ignored) {}
        return true;
    }

    public static Field setAccessible(Field field) {
        return varMutate(field, f -> f.setAccessible(true));
    }
    public static Method setAccessible(Method method) {
        return varMutate(method, m -> m.setAccessible(true));
    }


    private static <T> T getFieldUnsafe(Class<?> clazz, Object instance, String fieldName) {
        return (T) noExcept(() -> setAccessible(inUnsafe.getClass().getDeclaredMethod("getReference", Object.class, long.class)).invoke(inUnsafe, instance, getFieldOffset(clazz, fieldName)));
    }
    private static <T> T getFieldUnsafe(Object instance, String fieldName) {
        return getFieldUnsafe(instance.getClass(), instance, fieldName);
    }

    private static Field getFieldRaw(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() == null) throw e;
            return getField(clazz.getSuperclass(), fieldName);
        }
    }
    private static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return setAccessible(getFieldRaw(clazz, fieldName));
    }
    public static <T> T getField(Object instance, Field field) {
        return (T) noExcept(() -> setAccessible(field).get(instance));
    }
    public static <T> T getField(Object instance, String fieldName) throws NoSuchFieldException {
        return (T) Optional.ofNullable(getField(instance, getField(instance.getClass(), fieldName))).orElseGet(() -> getFieldUnsafe(instance, fieldName));
    }
    public static <T> T getStaticField(Class<?> clazz, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        return (T) getField(clazz, fieldName).get(null);
    }
    @SuppressWarnings("unchecked")
    public static <T> T getStaticFieldUnsafe(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return varOper(getFieldRaw(clazz, fieldName), f -> (T) unsafe.getObject(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f)));
    }



    public static long getFieldOffset(Class<?> clazz, String fieldName) {
        return (long) noExcept(() -> setAccessible(inUnsafe.getClass().getDeclaredMethod("objectFieldOffset", Class.class, String.class)).invoke(inUnsafe, clazz, fieldName));
    }
    public static <T> T setFieldUnsafe(Class<?> clazz, Object instance, String fieldName, Object o) {
        return (T) noExcept(() -> setAccessible(inUnsafe.getClass().getDeclaredMethod("putReference", Object.class, long.class, Object.class)).invoke(inUnsafe, instance, getFieldOffset(clazz, fieldName), o));
    }
    public static void setStaticFieldUnsafe(Class<?> clazz, String fieldName, Object o) throws NoSuchFieldException {
        varExec(getFieldRaw(clazz, fieldName), f -> unsafe.putObject(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), o));
    }
    public static void setStaticFieldUnsafe(Class<?> clazz, String fieldName, int o) throws NoSuchFieldException {
        varExec(getFieldRaw(clazz, fieldName), f -> unsafe.putInt(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), o));
    }
    public static void setStaticFieldUnsafe(Class<?> clazz, String fieldName, long o) throws NoSuchFieldException {
        varExec(getFieldRaw(clazz, fieldName), f -> unsafe.putLong(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), o));
    }
    public static void setStaticFieldUnsafe(Class<?> clazz, String fieldName, float o) throws NoSuchFieldException {
        varExec(getFieldRaw(clazz, fieldName), f -> unsafe.putFloat(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), o));
    }
    public static void setStaticFieldUnsafe(Class<?> clazz, String fieldName, double o) throws NoSuchFieldException {
        varExec(getFieldRaw(clazz, fieldName), f -> unsafe.putDouble(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), o));
    }
    public static void setStaticFieldUnsafe(Class<?> clazz, String fieldName, boolean o) throws NoSuchFieldException {
        varExec(getFieldRaw(clazz, fieldName), f -> unsafe.putBoolean(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), o));
    }
    public static void setStaticFieldUnsafe(Class<?> clazz, String fieldName, char o) throws NoSuchFieldException {
        varExec(getFieldRaw(clazz, fieldName), f -> unsafe.putChar(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), o));
    }
    public static void setStaticFieldUnsafe(Class<?> clazz, String fieldName, short o) throws NoSuchFieldException {
        varExec(getFieldRaw(clazz, fieldName), f -> unsafe.putShort(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), o));
    }
    public static void setStaticFieldUnsafe(Class<?> clazz, String fieldName, byte o) throws NoSuchFieldException {
        varExec(getFieldRaw(clazz, fieldName), f -> unsafe.putByte(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), o));
    }


    public static void setField(Object instance, Field field, Object value) {
        noExcept(() -> setAccessible(field).set(instance, value));
    }
    public static void setField(Object instance, String fieldName, Object value) throws NoSuchFieldException {
        setField(instance, getField(instance.getClass(), fieldName), value);
    }
    public static void setStaticField(Class<?> clazz, String fieldName, Object value) throws NoSuchFieldException {
        noExcept(() -> getField(clazz, fieldName).set(null, value));
    }

    private static MethodHandles.Lookup privateLookup(Class<?> clazz) {
        return noExcept(() -> MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()));
    }
    // todo: integrate with getMethod
    private static MethodHandle getMethodUnsafe(Class<?> clazz, String methodName, MethodType args) {
        return noExcept(() ->
                OptionalEx.ofExcept(() -> privateLookup(clazz).findSpecial(clazz, methodName, args, clazz))
                        .orElse(() -> noExcept(() -> privateLookup(clazz).findStatic(clazz, methodName, args))));
    }
    private static MethodHandle getMethodUnsafe(Class<?> clazz, String methodName) {
        return getMethodUnsafe(clazz, methodName, MethodType.methodType(clazz));
    }
    private static MethodHandle getMethodUnsafe(Class<?> clazz, String methodName, Class<?>... types) {
        return getMethodUnsafe(clazz, methodName, MethodType.methodType(clazz, types));
    }

    private static Method getMethod(Class<?> clazz, String methodName) {
        if (clazz == null) return null;
        if (methodName == null) return null;
        try {
            return setAccessible(clazz.getDeclaredMethod(methodName));
        } catch (NoSuchMethodException e) {
            return getMethod(clazz.getSuperclass(), methodName);
        }
    }
    private static Method getMethod(Class<?> clazz, String methodName, Class<?>... types) {
        if (clazz == null) return null;
        if (methodName == null) return null;
        try {
            return setAccessible(clazz.getDeclaredMethod(methodName, types));
        } catch (NoSuchMethodException e) {
            return getMethod(clazz.getSuperclass(), methodName, types);
        }
    }
    public static Method getMethod(Object instance, String methodName) {
        return getMethod(instance.getClass(), methodName);
    }
    public static Method getMethod(Object instance, String methodName, Class<?>... types) {
        return getMethod(instance.getClass(), methodName, types);
    }

    public static <T> T callMethod(Object instance, Method method, Object... args) {
        return (T) noExcept(() -> method.invoke(instance, args));
    }
    public static <T> T callMethod(Object instance, String methodName, Object... args) {
        return callMethod(instance, getMethod(instance.getClass(), methodName, Arrays.stream(args).map(Object::getClass).toArray(Class[]::new)), args);
    }
    public static <T> T callMethod(Object instance, Method method) {
        return (T) noExcept(() -> method.invoke(instance));
    }
    public static <T> T callMethod(Object instance, String methodName) {
        return callMethod(instance, getMethod(instance.getClass(), methodName));
    }

    public static void main(String[] args) throws Exception {
        ReflectionEx.getStaticField(Class.forName("java.lang.Integer$IntegerCache"), "archivedCache");

        Integer a = -150;
        Integer b = -150;

        // Will print "true" because both pointers now hit your modified cache!
        System.out.println("Are the objects identical? " + (a == b));
    }

}

