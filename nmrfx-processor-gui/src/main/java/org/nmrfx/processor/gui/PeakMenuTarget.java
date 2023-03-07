package org.nmrfx.processor.gui;

import org.nmrfx.peaks.PeakList;


/**
 *
 * @author brucejohnson
 */
public interface PeakMenuTarget {
    
    public void setPeakList(PeakList peakList);
    public PeakList getPeakList();
    public void refreshPeakView();
    public void refreshChangedListView();
    public void copyPeakTableView();
}
