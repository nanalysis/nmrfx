/*
 * NMRFx Structure : A Program for Calculating Structures
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
package org.nmrfx.chemistry;

import org.nmrfx.chemistry.utilities.NvUtil;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.ResonanceFactory;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Bruce Johnson
 */
public class AtomResonance {

    private static final Logger log = LoggerFactory.getLogger(AtomResonance.class);

    Atom atom = null;
    Object resonanceSet = null;
    Object ssID = null;
    boolean labelValid = true;
    String atomName = "";
    List<PeakDim> peakDims = new ArrayList<>();
    private List<String> names;
    private long id;

    public AtomResonance(long id) {
        this.names = null;
        this.id = id;
    }

    public AtomResonance copy() {
        AtomResonance copy = new AtomResonance(getID());
        copy.getPeakDims().addAll(getPeakDims());
        if (getNames() != null) {
            copy.setName(getNames());
            copy.setName(getName());
        }
        copy.setAtomName(getAtomName());
        copy.atom = atom;
        return copy;
    }

    public void setName(List<String> newNames) {
        if (names == null) {
            names = new ArrayList<>();
        }
        names.clear();
        if (newNames != null) {
            names.addAll(newNames);
        }
        boolean valid = true;
        if (newNames != null) {
            for (var name : newNames) {
                if (!isLabelValid(name)) {
                    valid = false;
                    break;
                }
            }
        }
        labelValid = valid;
    }

    private boolean isLabelValid(String name) {
        MoleculeBase molBase = MoleculeFactory.getActive();
        boolean result = true;
        if (!name.isBlank() && (molBase != null)) {
            Atom testAtom = molBase.findAtom(name);
            if (testAtom == null) {
                testAtom = molBase.findAtom(name + "1");
            }
            result = testAtom != null;
        }
        return result;
    }

    public boolean isLabelValid() {
        return labelValid;
    }

    public String getAtomName() {
        if (atom != null) {
            return atom.getFullName();
        } else {
            return atomName;
        }
    }

    public void setAtom(Atom atom) {
        this.atom = atom;
    }

    public Atom getAtom() {
        return atom;
    }

    public Atom getPossibleAtom() {
        if (atom != null) {
            return atom;
        } else {
            Atom possibleAtom = null;
            MoleculeBase molBase = MoleculeFactory.getActive();
            String name = getName();
            if (!name.isBlank() && (molBase != null)) {
                possibleAtom = molBase.findAtom(name);
            }
            return possibleAtom;
        }
    }

    public static void processSTAR3ResonanceList(Saveframe saveframe, Map<String, Compound> compoundMap) throws ParseException {
        Loop loop = saveframe.getLoop("_Resonance");
        if (loop == null) {
            throw new ParseException("No \"_Resonance\" loop");
        }
        List<String> idColumn = loop.getColumnAsList("ID");
        List<String> nameColumn = loop.getColumnAsList("Name");
        ResonanceFactory resFactory = ProjectBase.getActive().resonanceFactory();
        for (int i = 0, n = idColumn.size(); i < n; i++) {
            String value;
            long idNum;
            if ((value = NvUtil.getColumnValue(idColumn, i)) != null) {
                idNum = NvUtil.toLong(value);
            } else {
                continue;
            }

            AtomResonance resonance = resFactory.get(idNum);
            if (resonance == null) {
                resonance = resFactory.build(idNum);
            }
            if ((value = NvUtil.getColumnValue(nameColumn, i)) != null) {
                resonance.setName(value);
            }
        }

        loop = saveframe.getLoop("_Resonance_assignment");
        if (loop != null) {
            List<String> resSetIDColumn = loop.getColumnAsList("Resonance_set_ID");
            List<String> entityAssemblyIDColumn = loop.getColumnAsList("Entity_assembly_ID");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            List<String> compIdxIDColumn = loop.getColumnAsList("Comp_index_ID");
            List<String> atomIDColumn = loop.getColumnAsList("Atom_ID");
            for (int i = 0, n = resSetIDColumn.size(); i < n; i++) {
                String value;
                String entityAssemblyID = "";
                String entityID;
                if ((value = NvUtil.getColumnValue(entityAssemblyIDColumn, i)) != null) {
                    entityAssemblyID = value;
                }
                if (entityAssemblyID.isEmpty()) {
                    entityAssemblyID = "1";
                }
                if ((value = NvUtil.getColumnValue(entityIDColumn, i)) != null) {
                    entityID = value;
                } else {
                    throw new ParseException("No entity ID");
                }
                String iRes;
                if ((value = NvUtil.getColumnValue(compIdxIDColumn, i)) != null) {
                    iRes = value;
                } else {
                    throw new ParseException("No compound ID");
                }
                String atomName;
                if ((value = NvUtil.getColumnValue(atomIDColumn, i)) != null) {
                    atomName = value;
                } else {
                    throw new ParseException("No atom ID");
                }

                String mapID = entityAssemblyID + "." + entityID + "." + iRes;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    log.warn("invalid compound in assignments saveframe \"{}\"", mapID);
                    continue;
                }
                Atom atom = compound.getAtomLoose(atomName);
                if ((atom == null) && atomName.equals("H")) {
                    atom = compound.getAtom(atomName + "1");
                }
                if (atom == null) {
                    log.warn("invalid atom in assignments saveframe \"{}.{}\"", mapID, atomName);
                }
            }
        }
    }

    public String toSTARResonanceString() {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(getID()).append(sep);
        result.append(STAR3.quote(getName()));
        result.append(sep);
        if (resonanceSet == null) {
            result.append(".");
        }
        result.append(sep);
        if (ssID == null) {
            result.append(".");
        } else {
            result.append(ssID);
        }
        result.append(sep);
        result.append("1");
        return result.toString();
    }

    public void clearPeakDims() {
        peakDims = null;
    }

    public List<String> getNames() {
        return names;
    }

    public void remove(PeakDim peakDim) {
        peakDims.remove(peakDim);
    }

    public String getName() {
        String result = "";
        if (names != null) {
            if (names.size() == 1) {
                result = names.get(0);
            } else if (names.size() > 1) {
                StringBuilder builder = new StringBuilder();
                for (String name : names) {
                    if (!builder.isEmpty()) {
                        builder.append(" ");
                    }
                    builder.append(name);
                }
                result = builder.toString();
            }
        }
        return result;
    }

    public void setName(String name) {
        setName(List.of(name));
    }

    public void setAtomName(String aName) {
        atomName = aName;
    }

    public String getIDString() {
        return String.valueOf(id);

    }

    public void setID(long value) {
        id = value;
    }

    public long getID() {
        return id;
    }

    public static void merge(AtomResonance resA, AtomResonance resB) {
        resA.merge(resB);
    }
    public void merge(AtomResonance resB) {
        if (resB != this) {
            Collection<PeakDim> peakDimsB = resB.getPeakDims();
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
        return peakDims;
    }

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
            if ((condition != null) && (!condition.isEmpty())) {
                String peakCondition = peakDim.getPeak().getPeakList().getSampleConditionLabel();
                if ((!condition.equals(peakCondition))) {
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
            if ((condition != null) && (!condition.isEmpty())) {
                String peakCondition = peakDim.getPeak().getPeakList().getSampleConditionLabel();
                if ((!condition.equals(peakCondition))) {
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
        Double result;
        for (PeakDim peakDim : peakDims) {
            if (peakDim == null) {
                continue;
            }
            if ((condition != null) && (!condition.isEmpty())) {
                String peakCondition = peakDim.getPeak().getPeakList().getSampleConditionLabel();
                if ((!condition.equals(peakCondition))) {
                    continue;
                }
            }
            if (peakDim.getChemShift() != null) {
                sum += peakDim.getChemShift();
                sumsq += peakDim.getChemShift() * peakDim.getChemShift();
                n++;
            }
        }
        if (n == 0) {
            result = null;
        } else if (n == 1) {
            result = 0.0;
        } else if (n > 1) {
            double mean = sum / n;
            double devsq = sumsq / n - mean * mean;
            if (devsq > 0.0) {
                result = Math.sqrt(devsq);
            } else {
                result = 0.0;
            }
        } else {
            result = null;
        }

        return result;
    }

    public int getPeakCount(String condition) {
        int n = 0;
        for (PeakDim peakDim : peakDims) {
            if ((condition != null) && (!condition.isEmpty())) {
                String peakCondition = peakDim.getPeak().getPeakList().getSampleConditionLabel();
                if ((!condition.equals(peakCondition))) {
                    continue;
                }
            }
            n++;
        }
        return n;
    }
}
