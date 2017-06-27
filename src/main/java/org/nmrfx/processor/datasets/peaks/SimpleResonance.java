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
package org.nmrfx.processor.datasets.peaks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Bruce Johnson
 */
public class SimpleResonance implements Resonance {

    String name = "";
    String atomName = "";
    List<PeakDimContrib> pdCs = new ArrayList<>();
    final long id;

    public SimpleResonance(long id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getAtomName() {
        return atomName;
    }

    @Override
    public String getIDString() {
        return String.valueOf(id);

    }

    @Override
    public long getID() {
        return id;
    }

    @Override
    public Iterator getIterator() {
        return pdCs.iterator();
    }

    @Override
    public void merge(Resonance resB) {

    }

    @Override
    public void removePeakDimContrib(PeakDimContrib pdC) {
        pdCs.remove(pdC);
    }

    @Override
    public void addPeakDimContrib(PeakDimContrib pdC) {
        pdCs.add(pdC);
    }

    public List<PeakDim> getPeakDims() {
        ArrayList<PeakDim> peakDims = new ArrayList<>();
        Iterator iter = getIterator();
        while (iter.hasNext()) {
            PeakDimContrib pdc = (PeakDimContrib) iter.next();
            PeakDim peakDim = pdc.getPeakDim();
            peakDims.add(peakDim);
        }
        /*for (int i=0;i<peakDimContribs.size();i++) {
         PeakDim peakDim = ((PeakDimContrib) peakDimContribs.get(i)).getPeakDim();
         peakDims.add(peakDim);
         }*/
        return peakDims;
    }

}
