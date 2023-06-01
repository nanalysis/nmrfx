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

package org.nmrfx.processor.gui.spectra;

import javafx.geometry.Orientation;
import org.nmrfx.chart.Axis;

public class NMRAxis extends Axis implements NMRAxisLimits {
    private boolean showTicsAndLabels = true;

    public NMRAxis(Orientation orientation, double lowerBound, double upperBound, double width, double height) {
        super(orientation, lowerBound, upperBound, width, height);
    }

    public void setShowTicsAndLabels(boolean state) {
        showTicsAndLabels = state;
    }

    @Override
    public boolean getReverse() {
        return isReversed();
    }

    public void updateStateAndLabel(String label) {
        setTickLabelsVisible(showTicsAndLabels);
        setTickMarksVisible(showTicsAndLabels);
        setLabelVisible(showTicsAndLabels);

        setVisible(true);
        if (!showTicsAndLabels) {
            setLabel("");
        } else if (!label.equals(getLabel())) {
            setLabel(label);
        }
    }
}
