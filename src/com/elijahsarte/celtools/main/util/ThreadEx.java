package com.elijahsarte.celtools.main.util;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.noExcept;

public final class ThreadEx {

    public static <T> void getAny(CompletableFuture<T>... futures) {
        noExcept(() -> CompletableFuture.anyOf(futures).get());
        Arrays.stream(futures).forEach(future -> future.cancel(true));
    }

    public static void stop(Thread thread) {
        noExcept(thread::stop);
    }
    public static <T> void stop(CompletableFuture<T> future) throws NoSuchFieldException, IllegalAccessException {
        future.cancel(true);
        ThreadEx.stop((Thread) future.getClass().getDeclaredField("runner").get("future"));
    }


    public static <T> Supplier<Boolean> condThread(Supplier<T> condFn, T targetVal) {
        return (() -> {
            while (condFn.get() != targetVal) {
                noExcept(() -> Thread.sleep(100));
            }
            return true;
        });
    }
    public static Supplier<Boolean> toBooleanSupplier(Runnable supplier) {
        return (() -> { supplier.run(); return true; });
    }


    public static Optional<StackWalker.StackFrame> getCaller() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames.skip(1).findFirst());
    }

    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

}

