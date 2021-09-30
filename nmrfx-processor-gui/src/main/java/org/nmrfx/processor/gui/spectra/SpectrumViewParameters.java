/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui.spectra;

/**
 *
 * @author brucejohnson
 */
public class SpectrumViewParameters {
    //
    // view pars

    public int mode = 0;
    public boolean rotated = false;
    public int disDim = 2;
    public boolean mode2D = true;
    public boolean stackMode = false;
    // fixme should not be public
    public double xOffset = 0.0;
    public double yOffset = 0.0;
    public double xShear = 0.0;
    public double yShear = 0.0;
    public double deltaOffset = 0.0;
    public int calDelta = 0;
    public double scale1D = 10.0;
    public int maxVecLen = 2048;
    public int iPeakList = 0;
    public double level = 1.0;
    public boolean dcOffset = false;
    public boolean drawRegions = false;
    public int activeRegion = -1;
    public boolean drawTitles = false;
    public boolean drawLinks = false;
    public int drawThumbWidth = 0;
    public int[][] border = new int[2][2];
    public int[] extra = new int[2];
    public int winPeak = 0;

    public SpectrumViewParameters() {
        border[0][0] = 60;
        border[0][1] = 15;
        border[1][0] = 10;
        border[1][1] = 30;
    }

    public static SpectrumViewParameters newInstance(SpectrumViewParameters viewPar) {
        SpectrumViewParameters newView = new SpectrumViewParameters();

        newView.mode = viewPar.mode;
        newView.rotated = viewPar.rotated;
        newView.disDim = viewPar.disDim;
        newView.mode2D = viewPar.mode2D;
        newView.stackMode = viewPar.stackMode;
        newView.xOffset = viewPar.xOffset;
        newView.yOffset = viewPar.yOffset;
        newView.xShear = viewPar.xShear;
        newView.yShear = viewPar.yShear;
        newView.deltaOffset = viewPar.deltaOffset;
        newView.calDelta = viewPar.calDelta;
        newView.scale1D = viewPar.scale1D;
        newView.maxVecLen = viewPar.maxVecLen;
        newView.iPeakList = viewPar.iPeakList;
        newView.level = viewPar.level;
        newView.dcOffset = viewPar.dcOffset;
        newView.drawRegions = viewPar.drawRegions;
        newView.activeRegion = viewPar.activeRegion;
        newView.drawTitles = viewPar.drawTitles;
        newView.drawLinks = viewPar.drawLinks;
        newView.drawThumbWidth = viewPar.drawThumbWidth;
        newView.border = viewPar.border.clone();
        newView.extra = viewPar.extra.clone();
        newView.winPeak = viewPar.winPeak;
        return newView;
    }
}
