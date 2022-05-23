package org.nmrfx.analyst.gui.tools;

import javafx.geometry.Bounds;
import org.controlsfx.control.PopOver;
import org.nmrfx.analyst.gui.annotations.AnnoJournalFormat;
import org.nmrfx.peaks.Multiplet;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.annotations.AnnoText;
import org.nmrfx.processor.gui.spectra.IntegralHit;
import org.nmrfx.processor.gui.spectra.MultipletSelection;

public class PopOverTools {
    static PopOver popOver = new PopOver();

    private static MultipletTool multipletPopOverTool = null;
    private static JournalTool journalTool = null;
    private static IntegralTool integralTool = null;

    public void hide() {
        if (popOver.isShowing() && !popOver.isDetached()) {
            popOver.hide();
        }
    }

    public void showPopover(PolyChart chart, Bounds objectBounds, Object hitObject) {
        if (hitObject instanceof MultipletSelection) {
            showPopover(chart, objectBounds, ((MultipletSelection) hitObject).getMultiplet());
        } else if (hitObject instanceof AnnoText) {
            showPopover(chart, objectBounds, (AnnoText) hitObject);

        } else if (hitObject instanceof IntegralHit) {
            showPopover(chart, objectBounds, (IntegralHit) hitObject);

        }
    }


    public void showPopover(PolyChart chart, Bounds objectBounds, Multiplet multiplet) {
        if (multipletPopOverTool == null) {
            multipletPopOverTool = new MultipletTool(chart.getController(), null);
            multipletPopOverTool.initializePopover(popOver);
            popOver.setCloseButtonEnabled(true);
        }
        popOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_CENTER);
        multipletPopOverTool.setActiveMultiplet(multiplet);
        popOver.setContentNode(multipletPopOverTool.getBox());
        if (!popOver.isShowing() || (popOver.isShowing() && !popOver.isDetached())) {
            popOver.show(chart.getCanvas(), objectBounds.getCenterX(), objectBounds.getMinY() - 10);
        }
    }

    public void showPopover(PolyChart chart, Bounds objectBounds, AnnoText annoText) {
        if (annoText instanceof AnnoJournalFormat) {
            if (journalTool == null) {
                journalTool = new JournalTool();
                journalTool.initializePopover(popOver);
                popOver.setCloseButtonEnabled(true);
            }
            popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
            var annoJournalFormat = (AnnoJournalFormat) annoText;
            journalTool.setAnnoJournalFormat(annoJournalFormat);
            popOver.setContentNode(journalTool.getBox());
            if (!popOver.isShowing() || (popOver.isShowing() && !popOver.isDetached())) {
                popOver.show(chart.getCanvas(), objectBounds.getCenterX(), objectBounds.getMaxY() + 10);
            }
        }
    }

    public void showPopover(PolyChart chart, Bounds objectBounds, IntegralHit integralHit) {
        if (integralTool == null) {
            integralTool = new IntegralTool();
            integralTool.initializePopover(popOver);
            popOver.setCloseButtonEnabled(true);
        }
        integralTool.setHit(integralHit);
        popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
        popOver.setContentNode(integralTool.getBox());
        if (!popOver.isShowing() || (popOver.isShowing() && !popOver.isDetached())) {
            popOver.show(chart.getCanvas(), objectBounds.getCenterX(), objectBounds.getMaxY() + 10);
        }

    }

}
