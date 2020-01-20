
package org.nmrfx.processor.gui;

import org.nmrfx.processor.datasets.peaks.PeakList;

/**
 *
 * @author brucejohnson
 */
public interface PeakMenuTarget {
    
    public void setPeakList(PeakList peakList);
    public PeakList getPeakList();
    public void refreshPeakView();
    
}
