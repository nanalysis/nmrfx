/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2023 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.analyst.gui.python;

import javafx.application.Application;
import org.python.util.InteractiveInterpreter;

import java.util.Collections;
import java.util.List;

/**
 * Single access to a centralized python interpreter across the analyst application.
 * Initialized at application startup or on first usage, loads a set of predefined modules.
 */
public class AnalystPythonInterpreter {
    private static InteractiveInterpreter interpreter;

    public static synchronized void initialize(Application.Parameters parameters) {
        interpreter = createNewInterpreter(parameters.getRaw());
    }

    public static synchronized InteractiveInterpreter getInterpreter() {
        if (interpreter == null) {
            interpreter = createNewInterpreter(Collections.emptyList());
        }
        return interpreter;
    }

    private static InteractiveInterpreter createNewInterpreter(List<String> argv) {
        InteractiveInterpreter interpreter = new InteractiveInterpreter();
        interpreter.exec("import os");
        interpreter.exec("import glob");
        interpreter.exec("from pyproc import *\ninitLocal()");
        interpreter.exec("from gscript_adv import *\nnw=NMRFxWindowAdvScripting()");
        interpreter.exec("from dscript import *");
        interpreter.exec("from mscript import *");
        interpreter.exec("from pscript import *");
        interpreter.set("argv", argv);
        interpreter.exec("parseArgs(argv)");
        return interpreter;
    }
}
