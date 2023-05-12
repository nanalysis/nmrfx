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

import javafx.scene.paint.Color;

/**
 *
 * @author brucejohnson
 */
public class SpectrumColorParameters {

    static Color standardRegionColor = Color.rgb(255, 255, 180);
    public Color regionColor = standardRegionColor;
    public Color colorOn = Color.BLACK;
    public Color colorOff = Color.RED;
    public Color bgcolor = Color.WHITE;
    public boolean contourGrad = false;

    public static SpectrumColorParameters newInstance(SpectrumColorParameters colorPar) {
        SpectrumColorParameters newColor = new SpectrumColorParameters();
        newColor.regionColor = colorPar.regionColor;
        newColor.colorOn = colorPar.colorOn;
        newColor.colorOff = colorPar.colorOff;
        newColor.bgcolor = colorPar.bgcolor;
        newColor.contourGrad = colorPar.contourGrad;

        return newColor;
    }
}
