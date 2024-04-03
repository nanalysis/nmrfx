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
    private static final PopOver popOver = new PopOver();

    public void hide(boolean always) {
        if (popOver.isShowing() && (always || !popOver.isDetached())) {
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
        MultipletTool multipletTool = MultipletTool.getTool(chart);
        if (!multipletTool.popoverInitialized()) {
            multipletTool.initializePopover(popOver);
            popOver.setCloseButtonEnabled(true);
        }
        popOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_CENTER);
        multipletTool.setActiveMultiplet(multiplet);
        popOver.setContentNode(multipletTool.getBox());
        popOver.setTitle("Multiplets");
        if (!popOver.isShowing() || (popOver.isShowing() && !popOver.isDetached())) {
            popOver.show(chart.getCanvas(), objectBounds.getCenterX(), objectBounds.getMinY() - 10);
        }
    }

    public void showPopover(PolyChart chart, Bounds objectBounds, AnnoText annoText) {
        if (annoText instanceof AnnoJournalFormat) {
            JournalTool journalTool = JournalTool.getTool(chart);
            if (!journalTool.popoverInitialized()) {
                journalTool.initializePopover(popOver);
                popOver.setCloseButtonEnabled(true);
            }
            popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
            var annoJournalFormat = (AnnoJournalFormat) annoText;
            journalTool.setAnnoJournalFormat(annoJournalFormat);
            popOver.setTitle("Report");
            popOver.setContentNode(journalTool.getBox());
            if (!popOver.isShowing() || (popOver.isShowing() && !popOver.isDetached())) {
                popOver.show(chart.getCanvas(), objectBounds.getCenterX(), objectBounds.getMaxY() + 10);
            }
        }
    }

    public void showPopover(PolyChart chart, Bounds objectBounds, IntegralHit integralHit) {
        IntegralTool integralTool = IntegralTool.getTool(chart);
        if (!integralTool.popoverInitialized()) {
            integralTool.initializePopover(popOver);
            popOver.setCloseButtonEnabled(true);
        }
        integralTool.setHit(integralHit);
        popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
        popOver.setTitle("Integrals");
        popOver.setContentNode(integralTool.getBox());
        if (!popOver.isShowing() || (popOver.isShowing() && !popOver.isDetached())) {
            popOver.show(chart.getCanvas(), objectBounds.getCenterX(), objectBounds.getMaxY() + 10);
        }
    }
}
