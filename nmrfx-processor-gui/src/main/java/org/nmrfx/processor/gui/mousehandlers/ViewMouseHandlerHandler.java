package org.nmrfx.processor.gui.mousehandlers;

import javafx.scene.input.MouseEvent;
import org.nmrfx.processor.gui.PolyChart;

import java.util.Optional;

public class ViewMouseHandlerHandler extends MouseHandler {
    int border = 0;
    public ViewMouseHandlerHandler(MouseBindings mouseBindings) {
        super(mouseBindings);
    }

    public static Optional<ViewMouseHandlerHandler> handler(MouseBindings mouseBindings) {
        PolyChart chart = mouseBindings.getChart();
        int border = chart.hitBorder(mouseBindings.getMouseX(), mouseBindings.getMouseY());
        ViewMouseHandlerHandler handler = null;
        //boolean altShift = mouseEvent.isShiftDown() && (mouseEvent.isAltDown() || mouseEvent.isControlDown());

        if (border !=0) {
            handler = new ViewMouseHandlerHandler(mouseBindings);
            handler.border = border;
            chart.refresh();
        }
        return Optional.ofNullable(handler);
    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        double x = mouseEvent.getX();
        double y = mouseEvent.getY();

        double[] dragStart = mouseBindings.getDragStart();

        double dx = x - dragStart[0];
        double dy = y - dragStart[1];
        if ((Math.abs(dx) >= 1.0) || (Math.abs(dy) >= 1.0)) {
            dragStart[0] = x;
            dragStart[1] = y;
            if ((border & 1) == 0) {
                dy = 0.0;
            }
            if ((border & 2) == 0) {
                dx = 0.0;
            }
            mouseBindings.getChart().scroll(dx, dy);
        }
    }
}
