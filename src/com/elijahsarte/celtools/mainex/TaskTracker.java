package com.elijahsarte.celtools.mainex;

import com.elijahsarte.celtools.main.util.MathEx;

public class TaskTracker {

    private static long time = 0;
    private static long startPause = 0;
    private static long pausedTime = 0;

    public static void startTrack(String taskName) {
        time = System.nanoTime();
        System.out.print(taskName);
    }
    public static void endTrack() {
        if (time == 0) return;
        if (startPause != 0) resume();
//        if (time == 0) throw new IllegalStateException("Tried to end time when time hasn't been started");
        System.out.println(" (" + MathEx.round(((System.nanoTime() - time) - (pausedTime)) / (1e9), 0.01) + " sec)");
        time = 0;
        startPause = 0;
        pausedTime = 0;
    }
    public static void track(String taskName) {
        if (time != 0) endTrack();
        startTrack(taskName);
    }

    public static void pause() {
        if (startPause != 0) return;
        startPause = System.nanoTime();
    }
    public static void resume() {
        pausedTime += (System.nanoTime() - startPause);
        startPause = 0;
    }

}

