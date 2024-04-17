/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;


/**
 * @author Bruce Johnson
 */
public interface PeakNavigable {

    public void refreshPeakView(Peak peak);

    public void refreshPeakView();

    public void refreshPeakListView(PeakList peakList);

}
