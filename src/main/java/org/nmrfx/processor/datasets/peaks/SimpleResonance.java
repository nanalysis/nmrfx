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
import java.util.Collection;
import java.util.List;

/**
 *
 * @author Bruce Johnson
 */
public class SimpleResonance implements Resonance {

    String atomName = "";
    private List<String> names;
    List<PeakDim> peakDims = new ArrayList<>();
    final long id;

    public SimpleResonance(long id) {
        this.names = null;
        this.id = id;
    }

    @Override
    public void setName(List<String> newNames) {
        if (names == null) {
            names = new ArrayList<>();
        }
        names.clear();
        names.addAll(newNames);
    }

    @Override
    public void remove(PeakDim peakDim) {
        peakDims.remove(peakDim);
    }

    @Override
    public String getName() {
        String result = "";
        if (names != null) {
            if (names.size() == 1) {
                result = names.get(0);
            } else if (names.size() > 1) {
                StringBuilder builder = new StringBuilder();
                for (String name : names) {
                    if (builder.length() > 0) {
                        builder.append(" ");
                    }
                    builder.append(name);
                }
                result = builder.toString();
            }
        }
        return result;
    }

    @Override
    public void setName(String name) {
        if (names == null) {
            names = new ArrayList<>();
        }
        names.clear();
        names.add(name);
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
    public void merge(Resonance resB) {
        if (resB != this) {
            Collection<PeakDim> peakDimsB = resB.getPeakDims();
            int sizeA = peakDims.size();
            int sizeB = peakDimsB.size();
            for (PeakDim peakDim : peakDimsB) {
                peakDim.setResonance(this);
                if (!peakDims.contains(peakDim)) {
                    peakDims.add(peakDim);
                }
            }
            peakDimsB.clear();
        }

    }

    public List<PeakDim> getPeakDims() {
        // fixme should be unmodifiable or copy
        return peakDims;
    }

    @Override
    public void add(PeakDim peakDim) {
        peakDim.setResonance(this);
        if (!peakDims.contains(peakDim)) {
            peakDims.add(peakDim);
        }
    }

    public Double getPPMAvg(String condition) {
        double sum = 0.0;
        int n = 0;
        Double result = null;
        for (PeakDim peakDim : peakDims) {
            if (peakDim == null) {
                continue;
            }
            if ((condition != null) && (condition.length() > 0)) {
                String peakCondition = peakDim.getPeak().getPeakList().getSampleConditionLabel();
                if ((peakCondition == null) || (!condition.equals(peakCondition))) {
                    continue;
                }
            }
            if (peakDim.getChemShift() != null) {
                sum += peakDim.getChemShift();
                n++;
            }
        }
        if (n > 0) {
            result = sum / n;
        }
        return result;
    }

    public Double getWidthAvg(String condition) {
        double sum = 0.0;
        int n = 0;
        Double result = null;
        for (PeakDim peakDim : peakDims) {
            if (peakDim == null) {
                continue;
            }
            if ((condition != null) && (condition.length() > 0)) {
                String peakCondition = peakDim.getPeak().getPeakList().getSampleConditionLabel();
                if ((peakCondition == null) || (!condition.equals(peakCondition))) {
                    continue;
                }
            }
            Float lw = peakDim.getLineWidth();
            if (lw != null) {
                sum += lw;
                n++;
            }
        }
        if (n > 0) {
            result = sum / n;
        }
        return result;
    }

    public Double getPPMDev(String condition) {
        double sum = 0.0;
        double sumsq = 0.0;
        int n = 0;
        Double result = null;
        for (PeakDim peakDim : peakDims) {
            if (peakDim == null) {
                continue;
            }
            if ((condition != null) && (condition.length() > 0)) {
                String peakCondition = peakDim.getPeak().getPeakList().getSampleConditionLabel();
                if ((peakCondition == null) || (!condition.equals(peakCondition))) {
                    continue;
                }
            }
            if (peakDim.getChemShift() != null) {
                sum += peakDim.getChemShift();
                sumsq += peakDim.getChemShift() * peakDim.getChemShift();
                n++;
            }
        }
        if (n > 1) {
            double mean = sum / n;
            double devsq = sumsq / n - mean * mean;
            if (devsq > 0.0) {
                result = Math.sqrt(devsq);
            } else {
                result = 0.0;
            }
        } else if (n == 1) {
            result = 0.0;
        }
        /*for (int i=0;i<peakDimContribs.size();i++) {
         PeakDim peakDim = ((PeakDimContrib) peakDimContribs.get(i)).getPeakDim();
         peakDims.add(peakDim);
         }*/
        return result;
    }

    public int getPeakCount(String condition) {
        int n = 0;
        for (PeakDim peakDim : peakDims) {
            if ((condition != null) && (condition.length() > 0)) {
                String peakCondition = peakDim.getPeak().getPeakList().getSampleConditionLabel();
                if ((peakCondition == null) || (!condition.equals(peakCondition))) {
                    continue;
                }
            }
            n++;
        }
        return n;
    }

}
