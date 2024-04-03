package org.nmrfx.fxutil;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Utility class to help working with JavaFX
 */
public class Fx {
    private Fx() {
        throw new IllegalArgumentException("Utility class");
    }

    /**
     * Run something in the FX application thread.
     * May be run asynchronously if the caller isn't already in the application thread, or synchronously if it is already on it.
     *
     * @param runnable some function to execute
     */
    public static void runOnFxThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    /**
     * Run something in the FX application thread, then wait for its result
     * May be run asynchronously if the caller isn't already in the application thread, or synchronously if it is already on it.
     *
     * @param callable some function to execute
     * @return the future's result
     */
    public static <T> T runOnFxThreadAndWait(Callable<T> callable) throws ExecutionException, InterruptedException {
        if (Platform.isFxApplicationThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        } else {
            FutureTask<T> future = new FutureTask<>(callable);
            Platform.runLater(future);
            return future.get();
        }
    }


    /**
     * Run something in the FX application thread and wait for its completion.
     * May be run asynchronously if the caller isn't already in the application thread, or synchronously if it is already on it.
     *
     * @param runnable some function to execute
     */
    public static void runOnFxThreadAndWait(Runnable runnable) throws ExecutionException, InterruptedException {
        runOnFxThreadAndWait(() -> {
            runnable.run();
            return null;
        });
    }
}
