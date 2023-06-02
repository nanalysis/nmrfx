package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.scene.input.MouseEvent;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.processor.gui.PolyChart;

import java.util.Optional;

public class AnnotationMouseHandlerHandler extends MouseHandler {
    final CanvasAnnotation canvasAnnotation;

    public AnnotationMouseHandlerHandler(MouseBindings mouseBindings, CanvasAnnotation canvasAnnotation) {
        super(mouseBindings);
        this.canvasAnnotation = canvasAnnotation;
    }

    public static Optional<AnnotationMouseHandlerHandler> handler(MouseBindings mouseBindings) {
        PolyChart chart = mouseBindings.getChart();
        Optional<CanvasAnnotation> anno = chart.hitAnnotation(mouseBindings.getMouseX(), mouseBindings.getMouseY(), true);
        AnnotationMouseHandlerHandler handler = null;
        if (anno.isPresent()) {
            handler = new AnnotationMouseHandlerHandler(mouseBindings, anno.get());
            chart.refresh();
        }
        return Optional.ofNullable(handler);
    }


    @Override
    public void mousePressed(MouseEvent mouseEvent) {
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        double x = mouseEvent.getX();
        double y = mouseEvent.getY();
        double[] dragStart = mouseBindings.getDragStart();
        mouseBindings.getChart().finishAnno(dragStart, x, y, canvasAnnotation);
    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        double x = mouseEvent.getX();
        double y = mouseEvent.getY();
        double[] dragStart = mouseBindings.getDragStart();
        mouseBindings.getChart().dragAnno(dragStart, x, y, canvasAnnotation);

    }
}
