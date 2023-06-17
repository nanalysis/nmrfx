/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui.undo;

/**
 * @author Bruce Johnson
 */
public class UndoRedo {

    private final static int MIN_DELTA_TIME = 500;
    final String name;
    final ChartUndo undo;
    final ChartUndo redo;
    final long time;

    UndoRedo(String name, ChartUndo undo, ChartUndo redo) {
        this.name = name;
        this.undo = undo;
        this.redo = redo;
        time = System.currentTimeMillis();
    }

    public boolean canMerge(UndoRedo newUR) {
        boolean result = false;
        if (name.equals(newUR.name)) {
            if (Math.abs(newUR.time - time) < MIN_DELTA_TIME) {
                result = true;

            }
        }
        return result;
    }

    public UndoRedo merge(UndoRedo newUR) {
        UndoRedo merged = new UndoRedo(name, undo, newUR.redo);
        return merged;
    }

}
