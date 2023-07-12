package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.scene.input.MouseEvent;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.IntegralHit;

import java.util.Optional;

public class RegionMouseHandlerHandler extends MouseHandler {
    final IntegralHit integralHit;

    public RegionMouseHandlerHandler(MouseBindings mouseBindings, IntegralHit integralHit) {
        super(mouseBindings);
        this.integralHit = integralHit;
    }

    public static Optional<MouseHandler> handler(MouseBindings mouseBindings) {
        PolyChart chart = mouseBindings.getChart();
        Optional<IntegralHit> hit = chart.hitRegion(true, mouseBindings.getMouseX(), mouseBindings.getMouseY());
        RegionMouseHandlerHandler handler = null;
        if (hit.isPresent()) {
            handler = new RegionMouseHandlerHandler(mouseBindings, hit.get());
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
        mouseBindings.getChart().dragRegion(integralHit, x, y);

    }
}
