package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.scene.input.MouseEvent;
import org.nmrfx.processor.gui.PolyChart;

import java.util.Optional;

public class CrossHairMouseHandlerHandler extends MouseHandler {
    public CrossHairMouseHandlerHandler(MouseBindings mouseBindings) {
        super(mouseBindings);
    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        mouseBindings.getChart().handleCrossHair(mouseEvent, true);
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        mouseBindings.getChart().handleCrossHair(mouseEvent, false);
        PolyChart chart = mouseBindings.getChart();
        if (!chart.getCanvasCursor().toString().equals("CROSSHAIR")) {
            chart.getCrossHairs().setAllStates(false);
        }

    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        mouseBindings.getChart().handleCrossHair(mouseEvent, false);
    }

    public static Optional<CrossHairMouseHandlerHandler> handler(MouseBindings mouseBindings) {
        CrossHairMouseHandlerHandler handler = new CrossHairMouseHandlerHandler(mouseBindings);
        return Optional.of(handler);
    }
}
