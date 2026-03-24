package com.elijahsarte.celtools.main.util.mathbase.constructs;

import java.util.HashMap;
import java.util.function.BiConsumer;

public class GlobalVariablePool {

    //    private static final ArrayList<Variable> variables = new ArrayList<>();
    private static final HashMap<String, Variable> variables = new HashMap<>();

    public static void put(String variableName, Variable variable) {
        GlobalVariablePool.remove(variableName);
        GlobalVariablePool.variables.put(variableName, variable);
    }
    public static void add(Variable variable) {
        GlobalVariablePool.put(variable.getVarName(), variable);
    }

    public static void remove(String variableName) {
        GlobalVariablePool.variables.remove(variableName);
    }
    public static void remove(Variable variable) {
        GlobalVariablePool.variables.values().forEach(var -> {
            if (var.equals(variable)) {
                GlobalVariablePool.variables.remove(variable);
            }
        });
    }

    public static boolean contains(String variableName) {
        return GlobalVariablePool.variables.containsKey(variableName);
    }
    public static boolean contains(Variable variable) {
        return GlobalVariablePool.variables.containsValue(variable);
    }

    public static Variable get(String variableName) {
        return GlobalVariablePool.variables.get(variableName);
    }

    public static void clear() {
        GlobalVariablePool.variables.clear();
    }

    public static void forEach(BiConsumer<? super String, ? super Variable> consumer) {
        GlobalVariablePool.variables.forEach(consumer);
    }

}

