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
package org.nmrfx.plugin.api;

/**
 * EntryPoints are locations where a plugin could register itself inside NMRfx.
 * The obvious ones would be menus and scenes.
 */
public enum EntryPoint {
    STARTUP, MENU_FILE, MENU_MOLECULE_VIEWER, MENU_PLUGINS, STATUS_BAR_TOOLS, RIGHT_TOOLS, DATASET_MENU, PEAK_MENU, ATOM_MENU
}
