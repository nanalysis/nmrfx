package org.nmrfx.fxutil;

import javafx.application.Platform;

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
}
