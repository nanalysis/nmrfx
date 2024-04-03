package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.scene.input.MouseEvent;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.IntegralHit;

import java.util.Optional;

public class IntegralMouseHandlerHandler extends MouseHandler {
    final IntegralHit integralHit;
    double startingLowPosition;
    double startingHighPosition;

    public IntegralMouseHandlerHandler(MouseBindings mouseBindings, IntegralHit integralHit) {
        super(mouseBindings);
        this.integralHit = integralHit;
    }

    public static Optional<IntegralHit> handlerOverIntegral(MouseBindings mouseBindings) {
        PolyChart chart = mouseBindings.getChart();
        Optional<IntegralHit> hit = chart.hitIntegral(mouseBindings.getMouseX(), mouseBindings.getMouseY());
        return hit;
    }

    public static Optional<MouseHandler> handler(MouseBindings mouseBindings) {
        PolyChart chart = mouseBindings.getChart();
        Optional<IntegralHit> intHitOpt = chart.selectIntegral(mouseBindings.getMouseX(), mouseBindings.getMouseY());
        IntegralMouseHandlerHandler handler = null;
        if (intHitOpt.isPresent()) {
            handler = new IntegralMouseHandlerHandler(mouseBindings, intHitOpt.get());
            chart.refresh();
        }
        return Optional.ofNullable(handler);
    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        PolyChart chart = mouseBindings.getChart();
        startingLowPosition = chart.getChartProperties().getIntegralLowPos();
        startingHighPosition = chart.getChartProperties().getIntegralHighPos();
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        updateIntegralPosition(mouseEvent);

    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        updateIntegralPosition(mouseEvent);
    }

    private void updateIntegralPosition(MouseEvent mouseEvent) {
        double y = mouseEvent.getY();
        double[] dragStart = mouseBindings.getDragStart();
        PolyChart chart = mouseBindings.getChart();
        double height = chart.getHeight();
        double deltaFrac = (y - dragStart[1]) / height;
        double lowpos = startingLowPosition;
        double highpos = startingHighPosition;
        if (integralHit.getHandle() == -1) {
            lowpos -= deltaFrac;
            highpos -= deltaFrac;
        } else {
            highpos -= deltaFrac;
            if (highpos < lowpos) {
                highpos = lowpos;
            }
        }
        if (highpos > 1.0) {
            highpos = 1.0;
        }
        if (lowpos > 1.0) {
            lowpos = 1.0;
        }
        if (lowpos < 0.0) {
            lowpos = 0.0;
        }
        chart.getChartProperties().setIntegralLowPos(lowpos);
        chart.getChartProperties().setIntegralHighPos(highpos);
        chart.refresh();
    }
}
