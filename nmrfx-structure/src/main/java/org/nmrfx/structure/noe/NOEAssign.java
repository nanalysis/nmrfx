package org.nmrfx.structure.noe;

import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.Constraint;
import org.nmrfx.chemistry.constraints.Noe;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.structure.chemistry.IdPeak;
import org.nmrfx.structure.chemistry.IdResult;
import org.nmrfx.structure.chemistry.MatchCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author brucejohnson
 */
public class NOEAssign {
    private static final Logger log = LoggerFactory.getLogger(NOEAssign.class);

    public static Optional<int[]> getProtonDims(PeakList peakList) {
        int nDim = peakList.nDim;
        int protonDim1 = -1;
        int protonDim2 = -1;
        String[][] atomPats = new String[nDim][];
        for (int iDim = 0; iDim < peakList.nDim; iDim++) {
            SpectralDim spectralDim = peakList.getSpectralDim(iDim);
            String pattern = spectralDim.getPattern();
            int dot = pattern.indexOf('.');
            if (dot < 0) {
                atomPats[iDim] = new String[1];
                atomPats[iDim][0] = "*";
            } else {
                atomPats[iDim] = pattern.substring(dot + 1).toLowerCase().split(",");
            }
            for (int j = 0; j < atomPats[iDim].length; j++) {
                if (Util.stringMatch(atomPats[iDim][j], "h*")) {
                    if (protonDim1 == -1) {
                        protonDim1 = iDim;
                    } else if (protonDim2 == -1) {
                        protonDim2 = iDim;
                    } else {
                        throw new IllegalArgumentException("Too many proton dimensions, check peakList patterns");
                    }
                }
            }
        }
        Optional<int[]> result = Optional.empty();
        if ((protonDim1 != -1) && (protonDim2 != -1)) {
            int[] dims = {protonDim1, protonDim2};
            result = Optional.of(dims);
        }
        return result;
    }

    public static MatchCriteria[] getMatchCriteria(PeakList peakList) throws IllegalArgumentException {
        return getMatchCriteria(peakList, true);
    }

    public static MatchCriteria[] getMatchCriteria(PeakList peakList, boolean require2protons) throws IllegalArgumentException {
        int nDim = peakList.nDim;
        String[][] atomPats = new String[nDim][];
        String[][] resPats = new String[nDim][];
        String[] relation = new String[nDim];
        double[] tol = new double[nDim];
        double[] folding = new double[nDim];
        int[] foldCount = new int[nDim];
        int protonDim1 = -1;
        int protonDim2 = -1;
        for (int iDim = 0; iDim < peakList.nDim; iDim++) {
            SpectralDim spectralDim = peakList.getSpectralDim(iDim);
            if (spectralDim.getSf() > 1.0E-6) {
                folding[iDim] = spectralDim.getSw() / spectralDim.getSf();
            }
            foldCount[iDim] = spectralDim.getFoldCount();
            if (spectralDim.getFoldMode() == 'n') {
                foldCount[iDim] = 0;
            } else if (spectralDim.getFoldMode() == 'f') {
                foldCount[iDim] = -foldCount[iDim];
            }
            tol[iDim] = spectralDim.getIdTol();
            relation[iDim] = spectralDim.getRelation();
            if (relation[iDim].equals(".")) {
                relation[iDim] = "";
            }
            String pattern = spectralDim.getPattern();
            int dot = pattern.indexOf('.');
            if (dot < 0) {
                resPats[iDim] = new String[1];
                atomPats[iDim] = new String[1];
                resPats[iDim][0] = "";
                atomPats[iDim][0] = "*";
            } else {
                resPats[iDim] = pattern.substring(0, dot).split(",");
                atomPats[iDim] = pattern.substring(dot + 1).toLowerCase().split(",");
            }
            for (int j = 0; j < atomPats[iDim].length; j++) {
                if (Util.stringMatch(atomPats[iDim][j], "h*")) {
                    if (protonDim1 == -1) {
                        protonDim1 = iDim;
                    } else if (protonDim2 == -1) {
                        protonDim2 = iDim;
                    } else {
                        throw new IllegalArgumentException("Too many proton dimensions, check peakList patterns");
                    }
                }
            }
        }
        MatchCriteria[] matchCriteria;

        if (require2protons) {
            matchCriteria = new MatchCriteria[4];
            if ((protonDim1 == -1) || (protonDim2 == -1)) {
                throw new IllegalArgumentException("Too few proton dimensions, check peakList patterns");
            }
            matchCriteria[0] = new MatchCriteria(protonDim1, 3.0, tol[protonDim1], atomPats[protonDim1], resPats[protonDim1], relation[protonDim1], folding[protonDim1], foldCount[protonDim1]);
            matchCriteria[1] = new MatchCriteria(protonDim2, 3.0, tol[protonDim2], atomPats[protonDim2], resPats[protonDim2], relation[protonDim2], folding[protonDim2], foldCount[protonDim2]);

            int[] pDim = {-1, -1};
            for (int i = 0; i < 2; i++) {
                MatchCriteria mC = matchCriteria[i];
                if (!mC.getRelation().isEmpty()) {
                    if ((mC.getRelation().charAt(0) != 'D') && (mC.getRelation().charAt(0) != 'd')) {
                        throw new IllegalArgumentException("Invalid relation " + mC.getRelation());
                    }
                    pDim[i] = Integer.parseInt(mC.getRelation().substring(1));
                    pDim[i]--;
                    matchCriteria[i + 2] = new MatchCriteria(pDim[i], 3.0, tol[pDim[i]], atomPats[pDim[i]], resPats[pDim[i]], relation[pDim[i]], folding[pDim[i]], foldCount[pDim[i]]);
                }
            }
        } else {
            matchCriteria = new MatchCriteria[nDim];
            for (int i = 0; i < nDim; i++) {
                matchCriteria[i] = new MatchCriteria(i, 3.0, tol[i], atomPats[i], resPats[i], relation[i], folding[i], foldCount[i]);
            }
        }
        return matchCriteria;
    }

    public static boolean checkAtoms(Atom[][] atoms) {
        int nAtoms = 0;
        boolean ok = true;
        for (int iDim = 0; iDim < atoms.length; iDim++) {
            if (atoms[iDim] == null) {
                ok = false;
                break;
            }
            if (iDim == 0) {
                nAtoms = atoms[iDim].length;
            } else if (atoms[iDim].length != nAtoms) {
                ok = false;
                break;
            }
        }
        return ok;
    }

    public static Noe makeNoe(Peak peak, Atom[][] atoms, int[] atomIndex, int iPos, int nAssign) {
        double scale = 1.0;
        Noe noe = new Noe(peak, atoms[atomIndex[0]][iPos].spatialSet, atoms[atomIndex[1]][iPos].spatialSet, scale);
        noe.setIntensity(peak.getIntensity());
        noe.setVolume(peak.getVolume1());
        noe.setNPossible(nAssign);
        return noe;
    }

    public static int countProtons(Peak peak, Atom[][] atoms, int[] atomIndex, int iPos) {
        PeakList peakList = peak.getPeakList();
        int nProtons = 0;
        for (int iDim = 0; iDim < peakList.nDim; iDim++) {
            if (atoms[iDim][iPos] == null) {
                continue;
            }
            if (atoms[iDim][iPos].aNum == 1) {
                if (nProtons > 1) {
                    throw new IllegalArgumentException("too many protons for peak " + peak.getIdNum());
                }
                atomIndex[nProtons] = iDim;
                nProtons++;
            }
        }
        return nProtons;
    }

    // unAmbiguous == true  only extract contraints for peaks with one assignment
    // unAmbiguous == false  extract constraints for peaks with one or more (ambiguous) assignments
    public static void extractNoePeaks(NoeSet noeSet, PeakList peakList, boolean unAmbiguous, boolean onlyFrozen, boolean includeDiag) {
        int[] atomIndex = new int[2];
        int nPeaks;
        nPeaks = peakList.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if ((peak == null) || (peak.getStatus() < 0) || (onlyFrozen) && !peak.isFrozen()) {
                continue;
            }
            Atom[][] atoms = Noe.getAtoms(peak);
            if (checkAtoms(atoms)) {
                int nAssign = 0;
                int[] nProton = new int[atoms[0].length];
                for (int iPos = 0; iPos < atoms[0].length; iPos++) {
                    int nProtons = countProtons(peak, atoms, atomIndex, iPos);
                    if (nProtons == 2) {
                        nAssign++;
                    }
                    nProton[iPos] = nProtons;
                }
                for (int iPos = 0; iPos < atoms[0].length; iPos++) {
                    int nProtons = nProton[iPos];
                    if (includeDiag || !atoms[atomIndex[0]][iPos].getShortName().equals(atoms[atomIndex[1]][iPos].getShortName())) {
                        if ((nProtons == 2) && ((nAssign == 1) || ((nAssign > 1) && !unAmbiguous))) {
                            Noe noe = makeNoe(peak, atoms, atomIndex, iPos, nAssign);
                            noeSet.add(noe);
                        }
                    }
                }
            }
        }
    }
    // mode == 0  only extract contraints for peaks with one assignment
    // mode == 1  extract constraints for peaks with one or more (ambiguous) assignments

    public static AssignResult extractNoePeaks2(NoeSet noeSet, final PeakList peakList, final int maxAmbig,
                                                final boolean strict, final int ppmSet, boolean onlyFrozen)
            throws InvalidMoleculeException, IllegalArgumentException {
        Optional<NoeSet> noeSetOpt = Optional.of(noeSet);
        return extractNoePeaks2(noeSetOpt, peakList, maxAmbig, strict, ppmSet, onlyFrozen);
    }

    public static AssignResult extractNoePeaks2(Optional<NoeSet> noeSetOpt, final PeakList peakList, final int maxAmbig,
                                                final boolean strict, final int ppmSet, boolean onlyFrozen)
            throws InvalidMoleculeException, IllegalArgumentException {
        Peak peak;
        double scale = 1.0;
        int nPeaks;
        nPeaks = peakList.size();
        IdPeak idPeak = new IdPeak();
        idPeak.setPPMSet(ppmSet);
        idPeak.setMolecule(MoleculeFactory.getActive());
        if (idPeak.getMolecule() == null) {
            throw new InvalidMoleculeException("Can't find default molecule");
        }
        MatchCriteria[] matchCriteria = getMatchCriteria(peakList);
        idPeak.getProtons(0, matchCriteria[0].getAtomPats());
        idPeak.getProtons(1, matchCriteria[1].getAtomPats());
        int nTotal = 0;
        int nMaxAmbig = 0;
        int nAssigned = 0;
        Map<String, Noe.NoeMatch> map = new HashMap<>();
        for (int i = 0; i < nPeaks; i++) {
            peak = peakList.getPeak(i);
            if ((peak != null) && (peak.getStatus() >= 0)) {
                boolean frozenOK = true;
                if (onlyFrozen) {
                    for (MatchCriteria criteria : matchCriteria) {
                        if (criteria != null) {
                            PeakDim peakDim = peak.getPeakDim(criteria.getDim());
                            if (!peakDim.isFrozen()) {
                                frozenOK = false;
                                break;
                            }
                        }
                    }
                }
                if (!frozenOK) {
                    continue;
                }
                map.clear();
                PeakDim peakDim = peak.getPeakDim(matchCriteria[0].getDim());
                Float ppm = peakDim.getChemShift();
                if (ppm == null) {
                    log.info(peak.getName());
                    continue;
                }
                matchCriteria[0].setPPM(ppm);
                ArrayList res1s = new ArrayList();
                peakDim = peak.getPeakDim(matchCriteria[1].getDim());
                ppm = peakDim.getChemShift();
                if (Math.abs(ppm - matchCriteria[0].getPpm()) < 0.01) {
                    continue; // diagonal fixme
                }
                matchCriteria[1].setPPM(ppm);
                ArrayList res2s = new ArrayList();
                int nRes1 = res1s.size();
                int nRes2 = res2s.size();
                if ((nRes1 > 0) && (nRes2 > 0)) {
                    if ((nRes1 != 1) && (nRes2 != 1) && (nRes1 != nRes2)) {
                        throw new IllegalArgumentException("Peak \"" + peak.getName() + "\" has unbalanced assignments");
                    }
                    int maxN = nRes1 > nRes2 ? nRes1 : nRes2;
                    for (int iRes = 0; iRes < maxN; iRes++) {
                        AtomResonance r1 = null;
                        if (iRes < nRes1) {
                            r1 = (AtomResonance) res1s.get(iRes);
                        } else {
                            r1 = (AtomResonance) res1s.get(0);
                        }
                        AtomResonance r2 = null;
                        if (iRes < nRes2) {
                            r2 = (AtomResonance) res2s.get(iRes);
                        } else {
                            r2 = (AtomResonance) res2s.get(0);
                        }
                        Atom r1Atom = r1.getAtom();
                        SpatialSet sp1 = null;
                        SpatialSet sp2 = null;
                        if (r1Atom != null) {
                            sp1 = r1Atom.spatialSet;
                        }
                        Atom r2Atom = r2.getAtom();
                        if (r2Atom != null) {
                            sp2 = r2Atom.spatialSet;
                        }
                        if ((sp1 != null) && (sp2 != null)) {
                            String name = sp1.getFullName() + "_" + sp2.getFullName();
                            Noe.NoeMatch match = new Noe.NoeMatch(sp1, sp2, Constraint.GenTypes.MANUAL, 0.0);
                            map.put(name, match);
                        }
                    }
                }
                if (matchCriteria[2] != null) {
                    peakDim = peak.getPeakDim(matchCriteria[2].getDim());
                    ppm = peakDim.getChemShift();
                    matchCriteria[2].setPPM(ppm);
                }
                if (matchCriteria[3] != null) {
                    peakDim = peak.getPeakDim(matchCriteria[3].getDim());
                    ppm = peakDim.getChemShift();
                    matchCriteria[3].setPPM(ppm);
                }
                Atom[][] atoms = Noe.getAtoms(peak);
                int pDim1 = matchCriteria[0].getDim();
                int pDim2 = matchCriteria[1].getDim();
                if ((atoms[pDim1] != null) && (atoms[pDim2] != null)) {
                    int nProtons1 = atoms[pDim1].length;
                    int nProtons2 = atoms[pDim2].length;
                    if ((nProtons1 > 0) && (nProtons2 > 0)) {
                        if ((nProtons1 == nProtons2) || (nProtons1 == 1) || (nProtons2 == 1)) {
                            int maxN = Math.max(nProtons1, nProtons2);
                            for (int iProton = 0; iProton < maxN; iProton++) {
                                SpatialSet sp1 = null;
                                SpatialSet sp2 = null;
                                int iProton1 = iProton;
                                int iProton2 = iProton;
                                if (iProton >= nProtons1) {
                                    iProton1 = 0;
                                }
                                if (iProton >= nProtons2) {
                                    iProton2 = 0;
                                }
                                if (atoms[pDim1][iProton1] != null) {
                                    sp1 = atoms[pDim1][iProton1].spatialSet;
                                }
                                if (atoms[pDim2][iProton2] != null) {
                                    sp2 = atoms[pDim2][iProton2].spatialSet;
                                }
                                if ((sp1 != null) && (sp2 != null)) {
                                    String name = sp1.getFullName() + "_" + sp2.getFullName();
                                    Noe.NoeMatch match = new Noe.NoeMatch(sp1, sp2, Constraint.GenTypes.MANUAL, 0.0);
                                    map.put(name, match);
                                }
                            }
                        }
                    }
                }
                List<SpatialSet>[] matchList = idPeak.scan3(matchCriteria, true);
                List<IdResult> idResults = idPeak.getResults2(matchList, matchCriteria);
                int nMan = map.size();
                Map<String, Noe.NoeMatch> autoMap = new HashMap<>();

                if ((nMan == 0) || !strict) {
                    for (IdResult idResult : idResults) {
                        SpatialSet sp1 = idResult.getSpatialSet(0);
                        SpatialSet sp2 = idResult.getSpatialSet(1);
                        String name = sp1.getFullName() + "_" + sp2.getFullName();
                        if (!map.containsKey(name)) {
                            Constraint.GenTypes type = Constraint.GenTypes.AUTOMATIC;
                            if (nMan > 0) {
                                type = Constraint.GenTypes.AUTOPLUS;
                            }
                            Noe.NoeMatch match = new Noe.NoeMatch(sp1, sp2, type, idResult.getPPMError(1.0));
                            autoMap.put(name, match);
                        }
                    }
                }
                int nAuto = autoMap.size();
                if (nAuto < maxAmbig) {
                    map.putAll(autoMap);
                }
                int nPossible = map.size();
                if ((nAuto > maxAmbig) && (nMan == 0)) {
                    nMaxAmbig++;
                } else if (nPossible > 0) {
                    nTotal += nPossible;
                    nAssigned++;
                    if (noeSetOpt.isPresent()) {
                        NoeSet noeSet = noeSetOpt.get();
                        for (Map.Entry<String, Noe.NoeMatch> entry : map.entrySet()) {
                            Noe.NoeMatch nM = entry.getValue();
                            final Noe noe = new Noe(peak, nM.sp1(), nM.sp2(), scale);
                            double atomScale = 1.0;
                            if (nM.sp1().getAtom().isMethyl()) {
                                atomScale *= 3.0;
                            }
                            if (nM.sp2().getAtom().isMethyl()) {
                                atomScale *= 3.0;
                            }
                            noe.setAtomScale(atomScale);
                            noe.setIntensity(peak.getIntensity());
                            noe.setVolume(peak.getVolume1());
                            noe.setPpmError(nM.error());
                            noe.setNPossible(nPossible);
                            noe.setGenType(nM.type());
                            noeSet.add(noe);
                        }
                    }
                }

            }
        }
        return new AssignResult(nPeaks, nAssigned, nMaxAmbig, nTotal);
    }

    public static void updateGenTypes(NoeSet noeSet) {
        Map<String, Noe.NoeMatch> map = new HashMap<>();
        MatchCriteria[] matchCriteria = null;
        PeakList lastList = null;
        for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
            Peak peak = entry.getKey();
            PeakList peakList = peak.getPeakList();
            if ((matchCriteria == null) || (lastList != peakList)) {
                try {
                    matchCriteria = NOEAssign.getMatchCriteria(peakList);
                } catch (NumberFormatException nfE) {
                    matchCriteria = null;
                }
                lastList = peakList;
            }
            if (matchCriteria != null) {
                List<Noe> noeList = entry.getValue();
            }
        }
    }

    public static void updatePPMErrors(NoeSet noeSet) {
        MatchCriteria[] matchCriteria = null;
        PeakList lastList = null;
        for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
            Peak peak = entry.getKey();
            PeakList peakList = peak.getPeakList();
            if ((matchCriteria == null) || (lastList != peakList)) {
                try {
                    matchCriteria = NOEAssign.getMatchCriteria(peakList);
                } catch (NumberFormatException nfE) {
                    matchCriteria = null;
                }
                lastList = peakList;
            }
            if (matchCriteria != null) {
                List<Noe> noeList = entry.getValue();
                for (Noe noe : noeList) {
                    updatePPMError(matchCriteria, noe);
                }
            }
        }
    }

    public static void updatePPMError(MatchCriteria[] matchCriteria, Noe noe) {
        double sum = 0.0;
        int nCriteria = matchCriteria.length;
        SpatialSet[] spSets = new SpatialSet[nCriteria];
        for (int i = 0; i < nCriteria; i++) {
            if (matchCriteria[i] != null) {
                matchCriteria[i].setPPM(noe.peak);
                if (i == 0) {
                    spSets[i] = noe.getSpg1().getSpatialSet();
                } else if (i == 1) {
                    spSets[i] = noe.getSpg2().getSpatialSet();
                } else {
                    spSets[i] = spSets[i - 2].atom.getParent().spatialSet;
                }
                PPMv ppmv = spSets[i].getPPM(Noe.PPM_SET);
                double dp;
                if (ppmv != null) {
                    double delta = IdPeak.getPPMDelta(ppmv.getValue(), matchCriteria[i]);
                    dp = delta / matchCriteria[i].getTol();
                } else {
                    dp = 1.0E30;
                    log.info("no ppm for {}", spSets[i].getFullName());
                }
                sum += dp * dp;
            }
        }
        noe.setPpmError(Math.exp(-1.0 * sum / 2.0));
    }

    public static class AssignResult {

        final int nPeaks;
        final int nAssigned;
        final int nMaxAmbig;
        final int nTotal;

        AssignResult(int nPeaks, int nAssigned, int nMaxAmbig, int nTotal) {
            this.nPeaks = nPeaks;
            this.nAssigned = nAssigned;
            this.nMaxAmbig = nMaxAmbig;
            this.nTotal = nTotal;
        }

        public String toString() {
            return String.format("nPeaks %d nAssignd %d nMaxAmbig %d nTotal %d", nPeaks, nAssigned, nMaxAmbig, nTotal);
        }

    }

    public static void extractNoePeaksSlow(NoeSet noeSet, PeakList peakList, int mode) throws InvalidMoleculeException {
        Peak peak;
        double scale = 1.0;
        int nPeaks;
        nPeaks = peakList.size();
        MatchCriteria[] matchCriteria = new MatchCriteria[peakList.nDim];
        IdPeak idPeak = new IdPeak();
        idPeak.setMolecule(MoleculeFactory.getActive());
        if (idPeak.getMolecule() == null) {
            throw new InvalidMoleculeException("Can't find default molecule");
        }
        idPeak.clearAtomList();
        idPeak.getAtomsWithPPMs();
        int nDim = peakList.nDim;
        String[][] atomPats = new String[nDim][];
        String[][] resPats = new String[nDim][];
        String[] relation = new String[nDim];
        double[] tol = new double[nDim];
        double[] folding = new double[nDim];
        int protonDim1 = -1;
        int protonDim2 = -1;
        for (int iDim = 0; iDim < peakList.nDim; iDim++) {
            SpectralDim spectralDim = peakList.getSpectralDim(iDim);
            if (spectralDim.getSf() > 1.0E-6) {
                folding[iDim] = spectralDim.getSw() / spectralDim.getSf();
            }
            tol[iDim] = spectralDim.getIdTol();
            relation[iDim] = spectralDim.getRelation();
            if (relation[iDim].equals(".")) {
                relation[iDim] = "";
            }
            String pattern = spectralDim.getPattern();
            int dot = pattern.indexOf('.');
            if (dot < 0) {
                resPats[iDim] = new String[1];
                atomPats[iDim] = new String[1];
                resPats[iDim][0] = "";
                atomPats[iDim][0] = "*";
            } else {
                resPats[iDim] = pattern.substring(0, dot).split(",");
                atomPats[iDim] = pattern.substring(dot + 1).toLowerCase().split(",");
            }
            for (int j = 0; j < atomPats[iDim].length; j++) {
                if (Util.stringMatch(atomPats[iDim][j], "h*")) {
                    if (protonDim1 == -1) {
                        protonDim1 = iDim;
                    } else if (protonDim2 == -1) {
                        protonDim2 = iDim;
                    } else {
                        throw new IllegalArgumentException("Too many proton dimensions, check peakList patterns");
                    }
                }
            }
        }
        if ((protonDim1 == -1) || (protonDim2 == -1)) {
            throw new IllegalArgumentException("Too few proton dimensions, check peakList patterns");
        }
        for (int i = 0; i < nPeaks; i++) {
            peak = peakList.getPeak(i);
            if ((peak != null) && (peak.getStatus() >= 0)) {
                for (int iDim = 0; iDim < peakList.nDim; iDim++) {
                    PeakDim peakDim = peak.getPeakDim(iDim);
                    double ppm = peakDim.getChemShift();
                    matchCriteria[iDim] = new MatchCriteria(iDim, ppm, tol[iDim], atomPats[iDim], resPats[iDim], relation[iDim], folding[iDim], 0);
                }
                List<SpatialSet>[] matchList = idPeak.scan(matchCriteria);
                List<IdResult> idResults = idPeak.getIdResults(matchList, matchCriteria);
                // fixme filter duplicates ( stereo specific )
                int nPossible = idResults.size();
                for (IdResult idResult : idResults) {
                    Noe noe = new Noe(peak, idResult.getSpatialSet(protonDim1), idResult.getSpatialSet(protonDim2), scale);
                    noe.setIntensity(peak.getIntensity());
                    noe.setVolume(peak.getVolume1());
                    noe.setPpmError(idResult.getPPMError(1.0));
                    noe.setNPossible(nPossible);
                    noeSet.add(noe);
                }
            }
        }
    }
    // mode == 0  only extract contraints for peaks with one assignment
    // mode == 1  extract constraints for peaks with one or more (ambiguous) assignments

    public static double findMax(PeakList peakList, int dim, double mult, boolean onlyFrozen) throws InvalidMoleculeException, IllegalArgumentException {
        boolean strict = true;
        if (mult < 1.0e-6) {
            mult = peakList.getSpectralDim(dim).getIdTol() / 4.0;
        }
        int ppmSet = 0;
        int maxAmbig = 1;
        int bestScore = 0;
        double bestTol = 0.1;
        int nTries = 20;
        for (int i = 0; i < nTries; i++) {
            double tol = i * mult;
            peakList.getSpectralDim(dim).setIdTol(tol);
            Optional<NoeSet> emptyOpt = Optional.empty();
            AssignResult result = extractNoePeaks2(emptyOpt, peakList, maxAmbig, strict, ppmSet, onlyFrozen);
            if (result.nAssigned > bestScore) {
                bestScore = result.nAssigned;
                bestTol = tol;
            }
        }
        peakList.getSpectralDim(dim).setIdTol(bestTol);
        return bestTol;
    }


    public static void idPeak(Peak peak) throws InvalidMoleculeException {
        PeakList peakList = peak.getPeakList();
        int ppmSet = 0;
        IdPeak idPeak = new IdPeak();
        idPeak.setPPMSet(ppmSet);
        idPeak.setMolecule(MoleculeFactory.getActive());
        if (idPeak.getMolecule() == null) {
            throw new InvalidMoleculeException("Can't find default molecule");
        }

        MatchCriteria[] matchCriteria = getMatchCriteria(peakList);
        idPeak.getProtons(0, matchCriteria[0].getAtomPats());
        idPeak.getProtons(1, matchCriteria[1].getAtomPats());
    }
}
