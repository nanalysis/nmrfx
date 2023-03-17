package org.nmrfx.processor.gui;

import org.nmrfx.peaks.PeakList;


/**
 *
 * @author brucejohnson
 */
public interface PeakMenuTarget {
    
    void setPeakList(PeakList peakList);
    PeakList getPeakList();
    void refreshPeakView();
    void refreshChangedListView();
    void copyPeakTableView();
    void deletePeaks();
    void restorePeaks();
}
