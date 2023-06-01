package org.nmrfx.processor.gui;

import org.nmrfx.peaks.PeakList;


/**
 * @author brucejohnson
 */
public interface PeakMenuTarget {

    PeakList getPeakList();

    void setPeakList(PeakList peakList);

    void refreshPeakView();

    void refreshChangedListView();

    void copyPeakTableView();

    void deletePeaks();

    void restorePeaks();
}
