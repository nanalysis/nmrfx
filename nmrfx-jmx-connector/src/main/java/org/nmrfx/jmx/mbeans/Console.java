/*
 * NMRFx: A Program for Processing NMR Data
 * Copyright (C) 2004-2022 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.jmx.mbeans;

import org.nmrfx.console.ConsoleController;

/**
 * Use NMRfx's console from JMX.
 */
public class Console implements ConsoleMBean {
    @Override
    public void run(String script) {
        // This might be a bad idea: what if some text is already being typed by the user?
        // Probably be better to use the interpreter only, and not set any visual text here.
        ConsoleController.getConsoleController().writeAndRun(script);
    }
}
