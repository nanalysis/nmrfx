package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.scene.input.MouseEvent;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.ChartBorder;

import java.util.Optional;

public class ViewMouseHandlerHandler extends MouseHandler {
    ChartBorder border = ChartBorder.NONE;

    public ViewMouseHandlerHandler(MouseBindings mouseBindings) {
        super(mouseBindings);
    }

    public static Optional<ViewMouseHandlerHandler> handler(MouseBindings mouseBindings) {
        PolyChart chart = mouseBindings.getChart();
        ChartBorder border = chart.hitBorder(mouseBindings.getMouseX(), mouseBindings.getMouseY());
        ViewMouseHandlerHandler handler = null;

        if (border != ChartBorder.NONE) {
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
            if (border == ChartBorder.BOTTOM) {
                dy = 0.0;
            }
            if (border == ChartBorder.LEFT) {
                dx = 0.0;
            }
            mouseBindings.getChart().scroll(dx, dy);
        }
    }
}
