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
package org.nmrfx.structure.rna;

import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SecondaryStructure;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bajlabuser
 */
public class SSGen {
    enum SSTypes {
        JUNCTION,
        NONLOOP,
        BULGE,
        INTERNALLOOP,
        LOOP,
        HELIX
    }

    private int tracker = 0;
    private final String viennaSeq;
    private final Molecule molecule;
    private SSTypes type;
    private final List<SecondaryStructure> structures = new ArrayList<>();
    private List<Residue> residues;

    public SSGen(Molecule mol, String vienna) {
        molecule = mol;
        viennaSeq = vienna;
    }

    public SSGen(Molecule mol) {
        molecule = mol;
        char[] vSeq = RNAAnalysis.getViennaSequence(mol);
        viennaSeq = new String(vSeq);
    }

    public List<SecondaryStructure> analyze() {
        residues = RNAAnalysis.getRNAResidues(molecule);
        pairTo();
        secondaryStructGen();
        return structures;
    }

    public List<SecondaryStructure> structures() {
        return structures;
    }

        private void pairTo() {
        SSLayout ssLay = new SSLayout(viennaSeq.length());
        ssLay.interpVienna(viennaSeq, residues);
    }

    private SecondaryStructure classifyRes(List<Residue> residues) {
        SecondaryStructure secondaryStructure = null;
        if (residues != null && !(residues.isEmpty()) && (null != type)) {
            secondaryStructure = switch (type) {
                case JUNCTION:
                    yield new Junction(residues);
                case NONLOOP:
                    yield new NonLoop(residues);
                case BULGE:
                    yield new Bulge(residues);
                case INTERNALLOOP:
                    yield new InternalLoop(residues);
                case LOOP: {
                    yield new Loop(residues);
                }
                case HELIX: {
                    yield new RNAHelix(residues);
                }
            };
        }
        return secondaryStructure;
    }

    private List<Residue> genResList() {
        if (residues.get(tracker).pairedTo == null) {
            List<Residue> currentSS = new ArrayList<>();
            while (tracker < residues.size() && residues.get(tracker).pairedTo == null) {
                currentSS.add(residues.get(tracker));
                tracker++;
            }
            return buildOther(currentSS);
        } else {
            return buildHelix();
        }
    }

    private List<Residue> buildOther(List<Residue> currentSS) {
        List<Residue> ssResidues = new ArrayList<>();
        Residue ssFirstRes = currentSS.get(0);
        Residue ssLastRes = currentSS.get(currentSS.size() - 1);
        Residue firstRes = residues.get(0);
        Residue lastRes = residues.get(residues.size() - 1);
        Residue resBefore = ssFirstRes.getPrevious();
        Residue resAfter = ssLastRes.getNext();
        boolean samePoly = ssFirstRes.getPolymer() == ssLastRes.getPolymer();
        boolean add = false;

        if (ssLastRes == lastRes || ssFirstRes == firstRes || (resAfter == null)) { //last residue or first residue (string of non pairing all the way to end)
            add = true;
            type = SSTypes.NONLOOP; //instead of calling first residue, call last residue
        } else if (samePoly && (resBefore.pairedTo == resAfter)) { //loop
            add = true;
            type = SSTypes.LOOP;
        } else if (resBefore.pairedTo.iRes < resAfter.pairedTo.iRes) { //junction
            add = true;
            type = SSTypes.JUNCTION;
        } else if (resBefore.pairedTo.getPrevious() == resAfter.pairedTo) {
            add = true;
            type = SSTypes.BULGE;
        } else if (resBefore.pairedTo.getPrevious().pairedTo == null && (resBefore.iRes < resBefore.pairedTo.iRes)) { //second half of internal loop
            Residue otherLoopRes = resBefore.pairedTo.getPrevious();
            while (otherLoopRes.pairedTo == null) {
                currentSS.add(otherLoopRes);
                otherLoopRes = otherLoopRes.previous;
            }
            add = true;
            type = SSTypes.INTERNALLOOP;
        } else {
            boolean hasType = true;
            for (Residue res : currentSS) {
                if (res.secStruct == null) {
                    hasType = false;
                    break;
                }
            }
            if (!hasType) {
                add = true;
                type = SSTypes.BULGE;
            }
        }
        if (add) {
            ssResidues.addAll(currentSS);
        }
        return  ssResidues;
    }

    private List<Residue> buildHelix() {
        List<Residue> ssResidues = new ArrayList<>();
        while (tracker < residues.size() && residues.get(tracker).pairedTo != null) {
            Residue res1 = residues.get(tracker);
            Residue res2 = res1.pairedTo;
            Residue res1Next = res1.getNext();
            Residue res2Before = res2.getPrevious();
            Polymer poly1 = res1.getPolymer();
            Polymer poly2 = res2.getPolymer();
            int polyID1 = poly1.getIDNum();
            int polyID2 = poly2.getIDNum();
            boolean firstInstance = polyID1 < polyID2;
            if (polyID1 == polyID2) {
                firstInstance = res1.iRes < res2.iRes;
            }
            if (firstInstance) {
                ssResidues.add(residues.get(tracker));
                ssResidues.add(residues.get(tracker).pairedTo);
            }
            tracker++;
            if ((res1Next != null) && (res1Next.pairedTo != null) && (res2Before != null) && res2Before.pairedTo == null) {
                // bulge on opposite strand so end helix
                break;
            }
        }
        type = SSTypes.HELIX;
        return ssResidues;

    }
    private void secondaryStructGen() {
        tracker = 0;
        structures.clear();
        while (tracker < residues.size()) {
            SecondaryStructure ss = classifyRes(genResList());
            if (ss != null) {
                for (Residue residue : ss.secResidues) {
                    residue.secStruct = ss;
                }
                ss.size = ss.secResidues.size();
                structures.add(ss);
            }
        }
    }

}
