package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.scene.input.MouseEvent;

public abstract class MouseHandler {
    MouseBindings mouseBindings;

    public MouseHandler(MouseBindings mouseBindings) {
        this.mouseBindings = mouseBindings;
    }

    public abstract void mousePressed(MouseEvent mouseEvent);

    public abstract void mouseReleased(MouseEvent mouseEvent);

    public abstract void mouseMoved(MouseEvent mouseEvent);

    public abstract void mouseDragged(MouseEvent mouseEvent);
}
