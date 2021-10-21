/*
 * Copyright (C) 2016 Bruce Johnson
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.chart;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * A chart that displays rectangular bars with heights indicating data values
 * for categories. Used for displaying information when at least one axis has
 * discontinuous or discrete data.
 */
public class PlotApp extends Application {

    static private boolean launched = false;
    static private PlotApp myApp = null;
    static int iStage = -1;

    @Override
    public void start(Stage primaryStage) throws Exception {
        myApp = this;
        PlotDemo plotManager = new PlotDemo(primaryStage);
        primaryStage.show();
        launched = true;
        plotManager.showCanvasNow();
    }

    public static void setLaunched() {
        launched = true;
    }

    public static void exitApp() {
        Platform.exit();
        System.exit(0);
    }

    public static void initApp() {
        initApp(false);
    }

    public static void initApp(final boolean showStage) {
        if (!launched) {
            launched = true;
            Thread thread = new Thread() {
                @Override
                public void run() {
                    String[] args = new String[0];
                    Application.launch(PlotApp.class, args);
                    Platform.setImplicitExit(false);
                }
            };
            thread.start();
            // cheap trick to make sure javafx started before trying to do anything
            while (myApp == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public static void main(String[] args) {
        initApp();
    }
}
