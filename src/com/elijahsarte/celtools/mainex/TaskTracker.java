package com.elijahsarte.celtools.mainex;

import com.elijahsarte.celtools.main.util.MathEx;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TaskTracker {

    private static long time = 0;
    private static long startPause = 0;
    private static long pausedTime = 0;
    private static String currentTaskName = null;
    private static boolean currentTaskTimed = true;

    private static final Map<String, Long> asyncTasks = new ConcurrentHashMap<>();
    private static final Queue<String> pendingAsyncLogs = new ConcurrentLinkedQueue<>();

    private static final Map<Integer, Long> threadTimes = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> threadStartPauses = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> threadPausedTimes = new ConcurrentHashMap<>();
    private static final Map<Integer, String> threadTaskNames = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> threadTaskTimed = new ConcurrentHashMap<>();

    private static final Object PRINT_LOCK = new Object();

    public static void startTrack(String taskName) {
        synchronized (PRINT_LOCK) {
            time = System.nanoTime();
            startPause = 0;
            pausedTime = 0;
            currentTaskName = taskName;
            currentTaskTimed = true;
            System.out.print(taskName);
        }
    }

    public static void startTrackFree(String taskName) {
        synchronized (PRINT_LOCK) {
            time = System.nanoTime();
            startPause = 0;
            pausedTime = 0;
            currentTaskName = taskName;
            currentTaskTimed = false;
            System.out.print(taskName);
        }
    }

    public static void track(String taskName) {
        synchronized (PRINT_LOCK) {
            endTrack();
            time = System.nanoTime();
            startPause = 0;
            pausedTime = 0;
            currentTaskName = taskName;
            currentTaskTimed = true;
            System.out.print(taskName);
        }
    }

    public static void trackFree(String taskName) {
        synchronized (PRINT_LOCK) {
            if (time != 0) {
                endTrackInternal();
            }
            time = System.nanoTime();
            startPause = 0;
            pausedTime = 0;
            currentTaskName = taskName;
            currentTaskTimed = false;
            System.out.print(taskName);
        }
    }

    public static void endTrack() {
        synchronized (PRINT_LOCK) {
            endTrackInternal();
            flushPendingAsyncLogs();
        }
    }

    public static void pause() {
        synchronized (PRINT_LOCK) {
            if (time == 0 || startPause != 0) return;
            startPause = System.nanoTime();
        }
    }

    public static void resume() {
        synchronized (PRINT_LOCK) {
            if (time == 0 || startPause == 0) return;
            pausedTime += (System.nanoTime() - startPause);
            startPause = 0;
        }
    }

    public static void trackAsync(String taskName) {
        asyncTasks.put(taskName, System.nanoTime());

        synchronized (PRINT_LOCK) {
            if (time != 0 || hasActiveThreadTrack()) {
                pendingAsyncLogs.add("started " + taskName + " in background");
            } else {
                System.out.println("started " + taskName + " in background");
            }
        }
    }

    public static void endTrackAsync(String taskName) {
        long endTime = System.nanoTime();
        Long startTime = asyncTasks.remove(taskName);
        if (startTime == null) return;

        String msg = "finished " + taskName + " in "
                + MathEx.round((endTime - startTime) / 1e9, 0.01)
                + " sec";

        synchronized (PRINT_LOCK) {
            if (time != 0 || hasActiveThreadTrack()) {
                pendingAsyncLogs.add(msg);
            } else {
                System.out.println(msg);
            }
        }
    }

    private static boolean hasActiveThreadTrack() {
        return threadTimes.values().stream().anyMatch(t -> t != null && t != 0);
    }

    public static void trackAsyncThread(int threadNumber, String taskName) {
        trackAsync(threadIdentifier(threadNumber) + taskName);
    }
    public static void endTrackAsyncThread(int threadNumber, String taskName) {
        endTrackAsync(threadIdentifier(threadNumber) + taskName);
    }
    private static String threadIdentifier(int threadNumber) {
        return threadNumber < 0 ? "" : "[thread " + threadNumber + "] ";
    }

    public static void trackThread(int threadNumber, String taskName) {
        synchronized (PRINT_LOCK) {
            endTrackThreadInternal(threadNumber);
            threadTimes.put(threadNumber, System.nanoTime());
            threadStartPauses.put(threadNumber, 0L);
            threadPausedTimes.put(threadNumber, 0L);
            threadTaskNames.put(threadNumber, taskName);
            threadTaskTimed.put(threadNumber, true);
            System.out.println(threadIdentifier(threadNumber) + taskName);
        }
    }

    public static void endTrackThread(int threadNumber) {
        synchronized (PRINT_LOCK) {
            endTrackThreadInternal(threadNumber);
            if (time == 0 && !hasActiveThreadTrack()) {
                flushPendingAsyncLogs();
            }
        }
    }

    public static void reset() {
        synchronized (PRINT_LOCK) {
            if (time != 0) {
                endTrackInternal();
            }
            asyncTasks.clear();
            pendingAsyncLogs.clear();

            threadTimes.clear();
            threadStartPauses.clear();
            threadPausedTimes.clear();
            threadTaskNames.clear();
            threadTaskTimed.clear();
        }
    }

    private static void endTrackInternal() {
        if (time == 0) return;

        if (startPause != 0) {
            pausedTime += (System.nanoTime() - startPause);
            startPause = 0;
        }

        if (currentTaskTimed) {
            System.out.println(" (" + MathEx.round(((System.nanoTime() - time) - pausedTime) / 1e9, 0.01) + " sec)");
        } else {
            System.out.println();
        }

        time = 0;
        startPause = 0;
        pausedTime = 0;
        currentTaskName = null;
        currentTaskTimed = true;
    }

    private static void endTrackThreadInternal(int threadNumber) {
        Long threadTime = threadTimes.get(threadNumber);
        if (threadTime == null || threadTime == 0) return;

        long threadStartPause = threadStartPauses.getOrDefault(threadNumber, 0L);
        long threadPausedTime = threadPausedTimes.getOrDefault(threadNumber, 0L);

        if (threadStartPause != 0) {
            threadPausedTime += (System.nanoTime() - threadStartPause);
            threadStartPauses.put(threadNumber, 0L);
            threadPausedTimes.put(threadNumber, threadPausedTime);
        }

        if (threadTaskTimed.getOrDefault(threadNumber, true)) {
            System.out.println(threadIdentifier(threadNumber) + "finished " + threadTaskNames.get(threadNumber) + " in " + MathEx.round(((System.nanoTime() - threadTime) - threadPausedTime) / 1e9, 0.01) + " sec");
        } else {
            System.out.println();
        }

        threadTimes.remove(threadNumber);
        threadStartPauses.remove(threadNumber);
        threadPausedTimes.remove(threadNumber);
        threadTaskNames.remove(threadNumber);
        threadTaskTimed.remove(threadNumber);
    }

    private static void flushPendingAsyncLogs() {
        String msg;
        while ((msg = pendingAsyncLogs.poll()) != null) {
            System.out.println(msg);
        }
    }
}