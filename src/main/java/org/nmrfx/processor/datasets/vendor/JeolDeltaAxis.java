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
package org.nmrfx.processor.datasets.vendor;

/*
 * This class implements the "JeolDelta" command in Vendor.
 */
public class JeolDeltaAxis {

    public enum AxisType {

        COMPLEX("complex", 2) {
        };
        String name;
        int sectionCount;

        AxisType(final String name, final int sectionCount) {
            this.name = name;
            this.sectionCount = sectionCount;
        }

        public int getSectionCount() {
            return sectionCount;
        }
    }
    final int nPoints;
    final int start;
    final int stop;
    final AxisType type;
    final int nSubMatrices;
    final int subMatrixEdge;

    public JeolDeltaAxis(final int nPoints, final int subMatrixEdge, final int start, final int stop, final AxisType type) {
        this.nPoints = nPoints;
        this.subMatrixEdge = subMatrixEdge;
        this.nSubMatrices = nPoints / subMatrixEdge;
        this.start = start;
        if (stop == 0) {
            this.stop = nPoints - 1;
        } else {
            this.stop = stop;
        }
        this.type = type;
    }
}
