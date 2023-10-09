package org.nmrfx.processor.gui.undo;

import org.nmrfx.peaks.Peak;

import java.util.ArrayList;
import java.util.List;

public class GroupUndo extends ChartUndo {
    List<ChartUndo> undos = new ArrayList<>();
    public GroupUndo( List<? extends ChartUndo> undos) {
        this.undos.addAll(undos);
    }
    @Override
    public boolean execute() {
        for (var undo:undos) {
            undo.execute();
        }
        return true;
    }
}
