/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.analyst.gui;

import javafx.application.Application;
import org.nmrfx.processor.gui.log.Log;

import java.io.File;

public class NMRAnalystApp {

    /**
     * The main() method is ignored in correctly deployed JavaFX application. main() serves only as fallback in case the
     * application can not be launched through deployment artifacts, e.g., in IDEs with limited FX support. NetBeans
     * ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // setup default logging when run from IDE
        File sourceLogbackConfig = new File("nmrfx-analyst-gui/src/main/config/logback.xml");
        if (!Log.isConfigFileSet() && sourceLogbackConfig.exists()) {
            Log.setConfigFile("nmrfx-analyst-gui/src/main/config/logback.xml");
        }

        Application.launch(AnalystApp.class, args);
    }

}
