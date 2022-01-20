package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.scene.input.MouseEvent;
import org.nmrfx.processor.gui.PolyChart;

import java.util.Optional;

public class CrossHairMouseHandlerHandler extends MouseHandler {
    public CrossHairMouseHandlerHandler(MouseBindings mouseBindings) {
        super(mouseBindings);
    }

    public static Optional<CrossHairMouseHandlerHandler> handler(MouseBindings mouseBindings) {
        CrossHairMouseHandlerHandler handler = new CrossHairMouseHandlerHandler(mouseBindings);
        return Optional.of(handler);
    }


    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        mouseBindings.getChart().handleCrossHair(mouseEvent, true);
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        mouseBindings.getChart().handleCrossHair(mouseEvent, false);
        PolyChart chart = mouseBindings.getChart();
        if (!chart.getCursor().toString().equals("CROSSHAIR")) {
            chart.getCrossHairs().setCrossHairState(false);
        }

    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        mouseBindings.getChart().handleCrossHair(mouseEvent, false);
    }
}
