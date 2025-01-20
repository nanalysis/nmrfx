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
package org.nmrfx.peaks;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.peaks.events.FreezeListener;
import org.nmrfx.star.STAR3Base;

import java.io.IOException;
import java.io.Writer;
import java.util.*;


/**
 * @author Bruce Johnson
 */
public class ResonanceFactory implements FreezeListener {
    private static final String[] resonanceLoopStrings = {
            "_Resonance.ID",
            "_Resonance.Name",
            "_Resonance.Resonance_set_ID",
            "_Resonance.Spin_system_ID ",
            "_Resonance.Resonance_linker_list_ID ",};
    private static final String[] resonanceCovalentLinkStrings = {
            "_Resonance_covalent_link.Resonance_ID_1",
            "_Resonance_covalent_link.Resonance_ID_2",};

    Map<Long, AtomResonance> map = new HashMap<>();
    private long lastID = -1;

    public void init() {
        PeakList.registerFreezeListener(this);
    }

    public AtomResonance build() {
        while (map.get(lastID++) != null) ;
        AtomResonance resonance = new AtomResonance(lastID);
        map.put(lastID, resonance);
        return resonance;
    }

    public AtomResonance build(long id) {
        AtomResonance resonance = get(id);
        if (resonance == null) {
            resonance = new AtomResonance(id);
            map.put(id, resonance);
        }
        return resonance;
    }

    public void reassignResonanceFactoryMap(AtomResonance resonance) {
        //map.clear();
        map.put(resonance.getID(), resonance);
    }

    public AtomResonance get(long id) {
        return map.get(id);
    }

    public void clean() {
        Map<Long, AtomResonance> resonancesNew = new TreeMap<>();
        long resID = 0;
        for (Map.Entry<Long, AtomResonance> entry : map.entrySet()) {
            AtomResonance resonance = entry.getValue();
            if (((resonance.getPeakDims() != null) && (!resonance.getPeakDims().isEmpty()))) {
                resonance.setID(resID);
                resonancesNew.put(resID, resonance);
                resID++;
            }
        }
        map.clear();
        map = resonancesNew;
    }

    public synchronized Map<String, List<AtomResonance>> getLabelMap() {
        clean();
        Map<String, List<AtomResonance>> labelMap = new HashMap<>();
        map.values().forEach(resonance -> {
            String label = resonance.getName();
            if ((label != null) && (!label.isEmpty())) {
                label = label.trim().toUpperCase();
                if ((label.length() > 1) && Character.isLetter(label.charAt(0)) && Character.isDigit(label.charAt(1))) {
                    label = label.substring(1);
                }
                List<AtomResonance> resList = labelMap.computeIfAbsent(label, k -> new ArrayList<>());

                resList.add(resonance);
            }
        });
        return labelMap;
    }

    public AtomResonance merge(AtomResonance resonanceA, AtomResonance resonanceB) {
        if (resonanceA == resonanceB) {
            return null;
        }
        // FIXME  should we also test if they have names assigned and the names are different
        if ((resonanceA.getAtom() != null) && (resonanceB.getAtom() != null)) {
            if (resonanceA.getAtom() != resonanceB.getAtom()) {
                return null;
            }
        }
        if ((resonanceA.getAtom() == null) && (resonanceB.getAtom() != null)) {
            AtomResonance hold = resonanceA;
            resonanceA = resonanceB;
            resonanceB = hold;
        } else if (resonanceA.getName().isEmpty() && !resonanceB.getName().isEmpty()) {
            AtomResonance hold = resonanceA;
            resonanceA = resonanceB;
            resonanceB = hold;
        }
        for (PeakDim peakDim : resonanceB.getPeakDims()) {
            resonanceA.add(peakDim);

        }
        resonanceB.clearPeakDims();
        map.remove(resonanceB.getID());
        return resonanceA;
    }

    public synchronized void merge(String condition, double tol) {
        Map<String, List<AtomResonance>> labelMap = getLabelMap();
        for (var entry : labelMap.entrySet()) {
            List<AtomResonance> resList = entry.getValue();
            // find res with atom
            // if none find res closest to mean
            // or resonance with most peaks    
            // merge remaining
            AtomResonance refRes = null;
            for (AtomResonance res : resList) {
                if ((res.getPeakCount(condition) > 0) && (res.getAtom() != null)) {
                    refRes = res;
                    break;
                }
            }
            if (refRes == null) {
                int maxCount = 0;
                for (AtomResonance res : resList) {
                    int nPeakDims = res.getPeakCount(condition);
                    if (nPeakDims > maxCount) {
                        maxCount = nPeakDims;
                        refRes = res;
                    }
                }
            }
            if (refRes != null) {
                Double ppmAvg = refRes.getPPMAvg(null);
                Double widthAvg = refRes.getWidthAvg(null);
                if (ppmAvg == null) {
                    continue;
                }
                if (widthAvg < 0.05) {
                    widthAvg = 0.05;
                }
                for (AtomResonance res : resList) {
                    if (res == refRes) {
                        continue;
                    }
                    if (res.getPeakCount(condition) == 0) {
                        continue;
                    }
                    double ppmAvg2 = res.getPPMAvg(null);
                    double delta = Math.abs(ppmAvg - ppmAvg2);
                    if (delta < (tol * widthAvg)) {
                        refRes = merge(refRes, res);
                    }
                }
            }
        }
    }

    public void assignFrozenAtoms(String condition) {
        for (AtomResonance res : map.values()) {
            for (PeakDim peakDim : res.getPeakDims()) {
                if (peakDim.getPeak().getPeakList().getSampleConditionLabel().equals(condition) && peakDim.isFrozen()) {
                    Double ppmAvg = res.getPPMAvg(condition);
                    Atom atom = MoleculeBase.getAtomByName(peakDim.getLabel());
                    if (atom != null) {
                        atom.setPPM(ppmAvg);
                        res.setAtomName(atom.getFullName());
                        break;
                    }
                }
            }

        }
    }

    public void assignFromPeaks(String condition) {
        for (AtomResonance res : map.values()) {
            for (PeakDim peakDim : res.getPeakDims()) {
                Double ppmAvg = res.getPPMAvg(condition);
                String label = peakDim.getLabel();
                Atom atom = MoleculeBase.getAtomByName(label);
                if (atom == null) {
                    atom = MoleculeBase.getAtomByName(label+"1");
                }
                if (atom != null) {
                    atom.setPPM(ppmAvg);
                    res.setAtomName(atom.getFullName());
                    break;
                }

            }

        }
    }


    @Override
    public void freezeHappened(Peak peak, boolean state) {
        for (PeakDim peakDim : peak.peakDims) {
            String condition = peak.getPeakList().getSampleConditionLabel();
            AtomResonance res = peakDim.getResonance();
            Double ppmAvg = res.getPPMAvg(condition);
            Atom atom = null;
            if (MoleculeFactory.getActive() != null) {
                atom = MoleculeBase.getAtomByName(peakDim.getLabel());
            }
            if (peakDim.isFrozen()) {
                if (atom != null) {
                    if (ppmAvg != null) {
                        atom.setPPM(ppmAvg);
                    }
                    res.setAtomName(atom.getFullName());
                }
            } else {
                if (atom != null) {
                    atom.setPPMValidity(0, false);
                }
                res.setAtomName("");
            }
        }
    }

    public void writeResonancesSTAR3(Writer chan)
            throws IOException {

        chan.write(STAR3Base.SAVE + "resonance_linker_list\n");

        chan.write("_Resonance_linker_list.Sf_category    ");
        chan.write("resonance_linker\n");

        chan.write("_Resonance_linker_list.Sf_framecode   ");
        chan.write("resonance_linker_list\n");

        chan.write("_Resonance_linker_list.ID   ");
        chan.write("1" + "\n"); //fixme how to get list ID

        chan.write("_Resonance_linker_list.Details        ");
        chan.write(".\n");

        chan.write("\n");
        chan.write("loop_\n");
        for (String loopString : resonanceLoopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        for (Map.Entry<Long, AtomResonance> entry : map.entrySet()) {
            AtomResonance resonance = entry.getValue();
            if (resonance == null) {
                throw new IOException("AtomResonance.writeResonances: resonance null at ");
            }
            chan.write(resonance.toSTARResonanceString() + "\n");
        }
        chan.write("stop_\n");
        chan.write(STAR3Base.SAVE + "\n");

    }

}
