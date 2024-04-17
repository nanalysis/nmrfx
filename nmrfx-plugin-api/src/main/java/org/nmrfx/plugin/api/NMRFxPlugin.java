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

import java.util.Set;

/**
 * A plugin definition.
 */
public interface NMRFxPlugin {
    default String getName() {
        return getClass().getSimpleName();
    }

    default String getVersion() {
        return "undefined";
    }

    /**
     * NMRfx defines several entry points where plugins can be injected.
     * This allows the plugin tell NMRfx where it can be called.
     *
     * @return a set of supported entry points.
     */
    Set<EntryPoint> getSupportedEntryPoints();

    /**
     * Called by NMRfx to let a plugin integrates itself on a supported entry point.
     *
     * @param entryPoint the type of entry point
     * @param object     the actual entry point. Its type will depend, and could be a Menu, a Scene, ...
     */
    void registerOnEntryPoint(EntryPoint entryPoint, Object object);
}
