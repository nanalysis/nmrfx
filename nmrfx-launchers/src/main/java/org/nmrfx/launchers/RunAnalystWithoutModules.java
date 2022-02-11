package org.nmrfx.launchers;

import javafx.application.Application;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.processor.gui.log.Log;

public class RunAnalystWithoutModules {
    public static void main(String[] args) {
        // setup log config from IDE
        Log.setConfigFile("nmrfx-analyst-gui/src/main/config/logback.xml");

        Application.launch(AnalystApp.class, args);
    }
}
