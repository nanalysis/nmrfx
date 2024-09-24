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

import javafx.beans.property.SimpleBooleanProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class UndoManager {

    List<UndoRedo> queue = new ArrayList<>();
    int index = -1;

    public SimpleBooleanProperty undoable = new SimpleBooleanProperty(false);
    public SimpleBooleanProperty redoable = new SimpleBooleanProperty(false);

    public void add(String name, ChartUndo undo, ChartUndo redo) {
        UndoRedo newItem = new UndoRedo(name, undo, redo);
        int last = queue.size() - 1;
        for (int i = last; i > index; i--) {
            queue.remove(i);
        }
        last = queue.size() - 1;
        boolean merged = false;
        if (last >= 0) {
            UndoRedo lastItem = queue.get(last);
            if (lastItem.canMerge(newItem)) {
                lastItem = lastItem.merge(newItem);
                queue.set(last, lastItem);
                merged = true;
            }
        }
        if (!merged) {
            queue.add(newItem);
        }
        index = queue.size() - 1;
        updateProps();
    }

    public void undo() {
        if (index >= 0) {
            queue.get(index).undo.execute();
            index--;
        }
        updateProps();
    }

    public void redo() {
        if (index < (queue.size() - 1)) {
            index++;
            queue.get(index).redo.execute();
        }
        updateProps();
    }

    public void clear() {
        queue.clear();
        index = -1;
        updateProps();
    }

    private void updateProps() {
        undoable.set(index >= 0);
        redoable.set(index < (queue.size() - 1));
    }

    public String getFirstInQueueName() {
        if (queue.size() == 0) {
            return "";
        }
        return queue.get(0).name;
    }

}
