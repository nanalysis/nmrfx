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
package org.nmrfx.peaks;

public class Peak extends org.nmrfx.peaks.PeakBase {

    static {
        int j = 1;

        for (int i = 0; i < N_TYPES; i++) {
            peakTypes[i] = typeToString(j);
            j *= 2;
        }
    }
    

    public Peak(int nDim) {
        super(nDim);
        flag = new boolean[NFLAGS];
        setComment("");
        for (int i = 0; i < NFLAGS; i++) {
            setFlag(i, false);
        }
        for (int i = 0; i < nDim; i++) {
            peakDims[i] = new PeakDim(this, i);
        }
        setStatus(0);
    }

    public Peak(PeakListBase peakList, int nDim) {
        this(nDim);
        this.peakList = peakList;
        idNum = peakList.idLast + 1;
        peakList.idLast += 1;
    }


    /* fixme
    public void fit() {
        DatasetBase dataset = DatasetBase.getDataset(this.getPeakList().fileName);
        try {
            PeakList.peakFit(dataset, this);
        } catch (IllegalArgumentException | IOException | PeakFitException ex) {
            Logger.getLogger(Peak.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
*/
}
