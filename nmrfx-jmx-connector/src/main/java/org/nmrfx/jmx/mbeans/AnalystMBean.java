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

/**
 * The main entrypoint to control NMRFx Analyst Gui by JMX.
 */
public interface AnalystMBean {
    String NAME = "org.nmrfx:type=AnalystControl";

    /**
     * Open a dataset
     *
     * @param path the dataset path
     */
    void open(String path);

    /**
     * Show current stage on front of other windows.
     */
    void setWindowOnFront();

    /**
     * Generate an automatic processing script.
     *
     * @param isPseudo2D whether to generate a script for a pseudo 2D experiment (ie, do FT only in the direct dimension)
     */
    void generateAutoScript(boolean isPseudo2D);
}
