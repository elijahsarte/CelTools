package com.elijahsarte.celtools.mainex;

import com.elijahsarte.celtools.main.util.ConstructionEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class Debugger {

    private static final Map<String, Object> debugVars = new LinkedHashMap<>();
    private static String recentAddedID = "";
    private static boolean manualToggle = false;
    private static Boolean inDebugMode = null;


    public static <T> T create(String id, T obj) {
        if (!isDebuggerPresent()) return obj;
        recentAddedID = id;
        return ProgrammingEx.varMutate(ConstructionEx.Clone(obj), t -> debugVars.put(id, t));
    }
    public static <T> T create(T obj) {
        return create(UUID.randomUUID().toString(), obj);
    }
    public static <T> T createRef(String id, T obj) {
        if (!isDebuggerPresent()) return obj;
        recentAddedID = id;
        debugVars.put(id, obj);
        return obj;
    }
    public static void createRef(Object obj) {
        createRef(UUID.randomUUID().toString(), obj);
    }

    public static <T> T run(Supplier<T> obj) {
//        create(obj);
//        return run();
        return obj.get();
    }
    public static <T> T run(String id) {
        if (!isDebuggerPresent()) return null;
        return ((Supplier<T>) debugVars.get(id)).get();
    }
    public static <T> T run() {
        return run(recentAddedID);
    }

    public static void destroy(String id) {
        debugVars.put(id, null);
        debugVars.remove(id);
        System.gc();
    }
    public static void destroy() {
        destroy(recentAddedID);
    }

    public static Object get(String id) {
        return debugVars.get(id);
    }
    public static Object get() {
        return debugVars.get(recentAddedID);
    }


    public static Map<String, Object> list() {
        return Collections.unmodifiableMap(debugVars);
    }

    public static void clear() {
        debugVars.clear();
        recentAddedID = "";
        System.gc();
    }

    public static void toggle() {
        manualToggle = !manualToggle;
    }

    public static void trace(String msg) {
        if (isDebuggerPresent()) System.out.println(msg);
    }
    public static void traceLine(String msg) {
        if (isDebuggerPresent()) System.out.print(msg);
    }

    // https://stackoverflow.com/a/73125047
    private static boolean isDebuggerPresent() {
        if (manualToggle) return false;
        return true;
        /*
        if (inDebugMode != null) return inDebugMode;
        // Get ahold of the Java Runtime Environment (JRE) management interface
        RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();

        // Get the command line arguments that we were originally passed in
        List<String> args = runtime.getInputArguments();

        // Check if the Java Debug Wire Protocol (JDWP) agent is used.
        // One of the items might contain something like "-agentlib:jdwp=transport=dt_socket,address=9009,server=y,suspend=n"
        // We're looking for the string "jdwp".
        boolean jdwpPresent = args.toString().contains("jdwp");
        inDebugMode = jdwpPresent;
        return jdwpPresent;*/
    }


}

