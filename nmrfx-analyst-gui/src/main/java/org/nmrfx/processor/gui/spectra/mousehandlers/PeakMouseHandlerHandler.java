package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.scene.input.MouseEvent;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.peaks.Peak;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.MultipletSelection;
import org.nmrfx.processor.gui.undo.PeaksUndo;

import java.util.Optional;

public class PeakMouseHandlerHandler extends MouseHandler {
    Peak peak;
    boolean widthMode = false;
    PeaksUndo peaksUndo;
    PeaksUndo peaksRedo;

    public PeakMouseHandlerHandler(MouseBindings mouseBindings) {
        super(mouseBindings);
    }

    public static Optional<PeakMouseHandlerHandler> handler(MouseBindings mouseBindings) {
        PolyChart chart = mouseBindings.getChart();

        Optional<Peak> hit = chart.hitPeak(mouseBindings.getMouseX(), mouseBindings.getMouseY());
        PeakMouseHandlerHandler handler = null;
        if (hit.isPresent()) {
            handler = new PeakMouseHandlerHandler(mouseBindings);
            handler.peak = hit.get();
        }
        return Optional.ofNullable(handler);
    }

    public static Optional<PeakMouseHandlerHandler> handlerHitMultiplet(MouseBindings mouseBindings) {
        PolyChart chart = mouseBindings.getChart();

        Optional<MultipletSelection> hit = chart.hitMultiplet(mouseBindings.getMouseX(), mouseBindings.getMouseY());
        PeakMouseHandlerHandler handler = null;
        if (hit.isPresent()) {
            handler = new PeakMouseHandlerHandler(mouseBindings);
            handler.widthMode = false;
        }
        return Optional.ofNullable(handler);
    }

    public static Optional<MultipletSelection> handlerOverMultiplet(MouseBindings mouseBindings) {
        PolyChart chart = mouseBindings.getChart();

        Optional<MultipletSelection> hit = chart.hitMultiplet(mouseBindings.getMouseX(), mouseBindings.getMouseY());
        return hit;
    }

    public static Optional<PeakMouseHandlerHandler> handlePeaks(MouseBindings mouseBindings, boolean append) {
        PolyChart chart = mouseBindings.getChart();
        boolean selectedPeaks = chart.selectPeaks(mouseBindings.getMouseX(), mouseBindings.getMouseY(), append);
        PeakMouseHandlerHandler handler = null;
        if (selectedPeaks) {
            handler = new PeakMouseHandlerHandler(mouseBindings);
            handler.peak = null;
            handler.widthMode = false;
        }
        return Optional.ofNullable(handler);
    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        widthMode = mouseEvent.isAltDown() || mouseEvent.isControlDown();
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        double x = mouseEvent.getX();
        double y = mouseEvent.getY();
        double[] dragStart = mouseBindings.getDragStart();
        dragStart[0] = x;
        dragStart[1] = y;
        if (mouseBindings.getMoved()) {
            mouseBindings.getChart().dragPeak(dragStart, x, y, widthMode);
            peaksRedo = new PeaksUndo(mouseBindings.getChart().getSelectedPeaks());
            mouseBindings.getChart().getFXMLController().getUndoManager().add("Auto Add Peak", peaksUndo, peaksRedo);
        }
    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        if (mouseBindings.getMoved()) {
            if (peaksUndo == null) {
                peaksUndo = new PeaksUndo(mouseBindings.getChart().getSelectedPeaks());
            }
            double x = mouseEvent.getX();
            double y = mouseEvent.getY();
            double[] dragStart = mouseBindings.getDragStart();
            mouseBindings.getChart().dragPeak(dragStart, x, y, widthMode);
        }
    }
}
