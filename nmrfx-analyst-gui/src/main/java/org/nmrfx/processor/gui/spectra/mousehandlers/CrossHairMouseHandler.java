package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.geometry.Orientation;
import javafx.scene.input.MouseEvent;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;

public class CrossHairMouseHandler extends MouseHandler {
    // Some computers (mac, laptops) have only left & right click.
    // Toggle to true the first time the middle button (or mouse wheel) is clicked.
    private boolean hasMiddleButton = false;

    private int selectedHorizontalIndex = 0;
    private int selectedVerticalIndex = 0;

    public CrossHairMouseHandler(MouseBindings mouseBindings) {
        super(mouseBindings);
    }

    @Override
    public void mousePressed(MouseEvent event) {
        selectClosest(event);
        moveSelected(event);
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        deselect();
    }

    @Override
    public void mouseMoved(MouseEvent event) {
        // nothing
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        moveSelected(event);
    }

    private void selectClosest(MouseEvent event) {
        boolean isMiddleButtonDown = event.isMiddleButtonDown();
        hasMiddleButton = hasMiddleButton || isMiddleButtonDown;

        int[] crossNums = getCrossHairs().findAtPosition(event.getX(), event.getY(), hasMiddleButton, isMiddleButtonDown);
        selectedHorizontalIndex = crossNums[0];
        selectedVerticalIndex = crossNums[1];
    }

    private void deselect() {
        selectedHorizontalIndex = -1;
        selectedVerticalIndex = -1;

        PolyChart chart = mouseBindings.getChart();
        if (!chart.getCanvasCursor().toString().equals("CROSSHAIR")) {
            chart.getCrossHairs().setAllStates(false);
        }
    }

    private void moveSelected(MouseEvent event) {
        if (selectedHorizontalIndex >= 0) {
            getCrossHairs().move(selectedHorizontalIndex, Orientation.HORIZONTAL, event.getY());
        }
        if (selectedVerticalIndex >= 0) {
            getCrossHairs().move(selectedVerticalIndex, Orientation.VERTICAL, event.getX());
        }
    }

    private CrossHairs getCrossHairs() {
        return mouseBindings.getChart().getCrossHairs();
    }
}
