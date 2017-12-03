/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;

/**
 *
 * @author Bruce Johnson
 */
public interface PeakNavigable {

    public void setPeak(Peak peak);
    public void setPeakList(PeakList peakList);
    public void refreshPeakView();
    public void refreshPeakListView();

}
