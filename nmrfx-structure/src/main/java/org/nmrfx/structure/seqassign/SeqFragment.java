package org.nmrfx.structure.seqassign;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author brucejohnson
 */
public class SeqFragment {
    static private double spinSysProbability = 0.02;

    static private double fragmentScoreProbability = 0.1;
    static private int lastID = -1;
    int id = 0;
    List<SpinSystemMatch> spinSystemMatches = new ArrayList<>();
    ResidueSeqScore resSeqScore = null;
    boolean frozen = false;

    public SeqFragment() {
        id = ++lastID;
    }

    public static void setFragmentScoreProbability(double value) {
        fragmentScoreProbability = value;
    }

    public static SeqFragment getTestFragment(SpinSystemMatch spinSysMatch) {
        SpinSystem spinSysA = spinSysMatch.spinSystemA;
        SpinSystem spinSysB = spinSysMatch.spinSystemB;
        SeqFragment result = new SeqFragment();
        SeqFragment fragmentA = null;
        SeqFragment fragmentB = null;
        if (spinSysA.fragment.isPresent()) {
            fragmentA = spinSysA.fragment.get();
        }
        if (spinSysB.fragment.isPresent()) {
            fragmentB = spinSysB.fragment.get();
        }
        if ((fragmentA != null) && (fragmentA == fragmentB)) {
            result.spinSystemMatches.addAll(fragmentA.spinSystemMatches);
        } else if (fragmentA != null) {
            result.spinSystemMatches.addAll(fragmentA.spinSystemMatches);
            result.spinSystemMatches.add(spinSysMatch);
            if (fragmentB != null) {
                result.spinSystemMatches.addAll(fragmentB.spinSystemMatches);
            }
        } else if (fragmentB != null) {
            result.spinSystemMatches.add(spinSysMatch);
            result.spinSystemMatches.addAll(fragmentB.spinSystemMatches);
        } else {
            result.spinSystemMatches.add(spinSysMatch);
        }
        for (int i = result.getSpinSystemMatches().size() - 1; i >= 1; i--) {
            if (result.getSpinSystemMatches().get(i) == result.getSpinSystemMatches().get(i - 1)) {
                result.getSpinSystemMatches().remove(i);
            }
        }
        return result;
    }

    public ResidueSeqScore getResSeqScore() {
        return resSeqScore;
    }

    public void setResSeqScore(ResidueSeqScore residueSeqScore) {
        this.resSeqScore = residueSeqScore;
    }

    public void setId(int value) {
        id = value;
    }

    public int getId() {
        return id;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public static SeqFragment join(SpinSystemMatch spinSysMatch, boolean testMode) {
        SpinSystem spinSysA = spinSysMatch.spinSystemA;
        SpinSystem spinSysB = spinSysMatch.spinSystemB;
        SeqFragment result = null;
        boolean newFrozen = false;
        if (spinSysA.fragment.isEmpty() && spinSysB.fragment.isEmpty()) {
            result = new SeqFragment();
            result.spinSystemMatches.add(spinSysMatch);
        } else if (spinSysA.fragment.isPresent() && spinSysB.fragment.isPresent()) {
            if (spinSysA.fragment.get() != spinSysB.fragment.get()) {
                result = spinSysA.fragment.get();
                result.spinSystemMatches.add(spinSysMatch);
                result.spinSystemMatches.addAll(spinSysB.fragment.get().spinSystemMatches);
                if (spinSysA.fragment.get().isFrozen() || spinSysB.fragment.get().isFrozen()) {
                    newFrozen = true;
                }
            }

        } else if (spinSysA.fragment.isPresent()) {
            result = spinSysA.fragment.get();
            result.spinSystemMatches.add(spinSysMatch);
            if (spinSysA.fragment.get().isFrozen()) {
                newFrozen = true;
            }

        } else {
            result = spinSysB.fragment.get();
            result.spinSystemMatches.add(0, spinSysMatch);
            if (spinSysB.fragment.get().isFrozen()) {
                newFrozen = true;
            }
        }
        if ((result != null) && !testMode) {
            if (newFrozen) {
                result.setFrozen(true);
                result.updateScore();
            }
            result.updateFragment();

        }
        return result;

    }

    public List<SpinSystemMatch> getSpinSystemMatches() {
        return spinSystemMatches;
    }

    public List<SpinSystem> getSpinSystems() {
        List<SpinSystem> spinSystems = new ArrayList<>();
        spinSystems.add(spinSystemMatches.get(0).getSpinSystemA());
        for (var match : spinSystemMatches) {
            spinSystems.add(match.getSpinSystemB());
        }
        return spinSystems;
    }

    public SpinSystem getSpinSystem(int index) {
        SpinSystem spinSystem;
        if (index < 2) {
            spinSystem = spinSystemMatches.get(0).getSpinSystemA();
        } else {
            spinSystem = spinSystemMatches.get(index - 2).getSpinSystemB();
        }
        return spinSystem;
    }

    public void dump() {
        for (SpinSystemMatch match : spinSystemMatches) {
            System.out.println(match.toString());
        }

    }

    public static List<SeqFragment> remove(SpinSystemMatch spinSysMatch, boolean testMode) {
        SpinSystem spinSysA = spinSysMatch.spinSystemA;
        SpinSystem spinSysB = spinSysMatch.spinSystemB;
        List<SeqFragment> result = new ArrayList<>();
        if (spinSysA.fragment.isPresent() && spinSysB.fragment.isPresent()) {
            SeqFragment currentFragment = spinSysA.fragment.get();
            List<SpinSystemMatch> spinSystemMatches = currentFragment.spinSystemMatches;

            currentFragment.dump();

            SpinSystemMatch firstMatch = spinSystemMatches.get(0);
            SpinSystemMatch lastMatch = spinSystemMatches.get(spinSystemMatches.size() - 1);
            spinSysMatch.spinSystemA.fragment = Optional.empty();
            spinSysMatch.spinSystemB.fragment = Optional.empty();
            if (spinSysMatch == firstMatch) {
                spinSystemMatches.remove(0);
                result.add(null);
                result.add(currentFragment);
                currentFragment.updateFragment();
            } else if (spinSysMatch == lastMatch) {
                spinSystemMatches.remove(spinSystemMatches.size() - 1);
                currentFragment.updateFragment();
                result.add(currentFragment);
                result.add(null);
            } else {
                SeqFragment newFragment = new SeqFragment();
                int index = getIndex(spinSystemMatches, spinSysMatch);
                if (index != -1) {
                    List<SpinSystemMatch> prevMatches = spinSystemMatches.subList(0, index);
                    List<SpinSystemMatch> clearMatches = spinSystemMatches.subList(0, index + 1);
                    newFragment.spinSystemMatches.addAll(prevMatches);
                    clearMatches.clear();
                    newFragment.updateFragment();
                    currentFragment.updateFragment();
                    result.add(newFragment);
                    result.add(currentFragment);
                } else {
                    result.add(null);
                    result.add(null);
                }
            }
        }
        return result;

    }

    static int getIndex(List<SpinSystemMatch> matches, SpinSystemMatch match) {
        int index = -1;
        for (int i = 0; i < matches.size(); i++) {
            SpinSystemMatch testMatch = matches.get(i);
            if ((testMatch.spinSystemA == match.spinSystemA) && (testMatch.spinSystemB == match.spinSystemB)) {
                index = i;
            }
        }
        return index;
    }

    void updateScore() {
        var resSeqScores = scoreShifts(Molecule.getActive());
        if (resSeqScores.size() == 1) {
            freezeFragment(resSeqScores.get(0));
            setResSeqScore(resSeqScores.get(0));
        }

    }
    void updateFragment() {
        for (SpinSystemMatch spinSysMatch : spinSystemMatches) {
            spinSysMatch.spinSystemA.fragment = Optional.of(this);
            spinSysMatch.spinSystemB.fragment = Optional.of(this);
        }
        var resSeqScores = scoreShifts(Molecule.getActive());
        if (resSeqScores.size() == 1) {
            setResSeqScore(resSeqScores.get(0));
        }
    }

    boolean addNext(SpinSystemMatch spinSysMatch) {
        boolean result = false;
        if (spinSystemMatches.isEmpty() || (spinSysMatch.spinSystemA == spinSystemMatches.get(spinSystemMatches.size() - 1).spinSystemB)) {
            spinSystemMatches.add(spinSysMatch);
            result = true;
        }
        return result;
    }

    public static double[][] getShiftsForSystem(SpinSystem spinSystem) {
        double[][] result = new double[2][SpinSystem.ATOM_TYPES.length];
        for (int idx = 0; idx < SpinSystem.ATOM_TYPES.length; idx++) {
            result[0][idx] = spinSystem.getValue(0, idx);
            result[1][idx] = spinSystem.getValue(1, idx);
        }
        return result;
    }

    public double[][] getShifts() {
        double[][] result = new double[spinSystemMatches.size() + 2][SpinSystem.ATOM_TYPES.length];
        int iSys = 0;
        SpinSystem spinSysA;
        SpinSystem spinSysB = null;
        for (SpinSystemMatch spinMatch : spinSystemMatches) {
            spinSysA = spinMatch.spinSystemA;
            spinSysB = spinMatch.spinSystemB;
            if (iSys == 0) {
                for (int idx = 0; idx < SpinSystem.ATOM_TYPES.length; idx++) {
                    result[iSys][idx] = spinSysA.getValue(0, idx);
                }
                iSys++;
            }
            for (int idx = 0; idx < SpinSystem.ATOM_TYPES.length; idx++) {
                if (SpinSystem.RES_MTCH[idx]) {
                    if (spinMatch.matched[idx]) {
                        double vA = spinSysA.getValue(1, idx);
                        double vB = spinSysB.getValue(0, idx);
                        double avg = (vA + vB) / 2.0;
                        result[iSys][idx] = avg;
                    } else {
                        result[iSys][idx] = Double.NaN;
                    }
                } else {
                    result[iSys][idx] = spinSysA.getValue(1, idx);
                }
            }
            iSys++;
        }
        if (spinSysB != null) {
            for (int idx = 0; idx < SpinSystem.ATOM_TYPES.length; idx++) {
                result[iSys][idx] = spinSysB.getValue(1, idx);
            }
        }

        return result;
    }

    static List<List<AtomShiftValue>> getShiftValues(double[][] shifts, boolean useAll) {
        List<List<AtomShiftValue>> result = new ArrayList<>();
        for (double[] shift : shifts) {
            List<AtomShiftValue> values = new ArrayList<>();
            for (int k = 0; k < shift.length; k++) {
                if (useAll || SpinSystem.RES_SCORE_ATOM[k]) {
                    String aName = SpinSystem.getAtomName(k);
                    double value = shift[k];
                    if (!Double.isNaN(value)) {
                        AtomShiftValue atomValue = new AtomShiftValue(aName, value, null);
                        values.add(atomValue);
                    }
                }
            }
            result.add(values);
        }
        return result;
    }

    public List<ResidueSeqScore> scoreShifts(Molecule molecule) {
        double[][] shifts = getShifts();
        return scoreShifts(molecule, shifts, this);
    }
    public static List<ResidueSeqScore> scoreShifts(Molecule molecule, double[][] shifts, SeqFragment seqFragment) {
        List<ResidueSeqScore> result = new ArrayList<>();
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isPeptide()) {
                result.addAll(scoreShifts(polymer, shifts, seqFragment));
            }
        }
        return result;
    }

    public  List<ResidueSeqScore> scoreShifts(Polymer polymer) {
        double[][] shifts = getShifts();
        return scoreShifts(polymer, shifts, this);
    }

    public static List<ResidueSeqScore> scoreShifts(Polymer polymer, double[][] shifts, SeqFragment fragment) {
        double sDevMul = 2.0;
        List<ResidueSeqScore> result = new ArrayList<>();
        List<List<AtomShiftValue>> atomShiftValues = getShiftValues(shifts, false);
        int winSize = atomShiftValues.size();
        List<Residue> residues = polymer.getResidues();
        int nResidues = residues.size();
        int n = nResidues - winSize + 1;
        for (int i = 0; i < n; i++) {
            boolean ok = true;
            double pScore = 1.0;
            boolean isCurrent = false;
            if ((fragment != null) && (fragment.isFrozen())) {
                if (fragment.getResSeqScore().getFirstResidue() != residues.get(i)) {
                    continue;
                } else {
                    isCurrent = true;
                }
            }
            for (int j = 0; j < winSize; j++) {
                Residue residue = residues.get(i + j);
                PPMScore ppmScore = FragmentScoring.scoreAtomPPM(spinSysProbability, sDevMul, residue, atomShiftValues.get(j));
                if (!ppmScore.ok()) {
                    ok = false;
                    break;
                }
                pScore *= ppmScore.getTotalScore();
            }
            if (ok) {
                ResidueSeqScore resScore = new ResidueSeqScore(residues.get(i), winSize, pScore);
                result.add(resScore);
            }
        }

        if (!result.isEmpty()) {
            ResidueSeqScore.norm(result);
            result = result.stream().sorted().filter(r -> r.getScore() > fragmentScoreProbability).toList();
        }
        return result;
    }

    private void assignResidue(Residue residue, List<AtomShiftValue> atomShiftValues) {
        for (AtomShiftValue atomShiftValue : atomShiftValues) {
            String aName = atomShiftValue.getAName();
            double ppm = atomShiftValue.getPPM();
            residue.getAtom(aName).setPPM(ppm);
        }
    }

    public void freezeFragment(ResidueSeqScore resSeqScore) {
        setFrozen(true);
        double[][] shifts = getShifts();
        List<List<AtomShiftValue>> atomShiftValues = getShiftValues(shifts, true);
        Residue firstResidue = resSeqScore.getFirstResidue();
        Residue residue = firstResidue;
        int i = 0;
        for (var atomShiftValueList : atomShiftValues) {
            assignResidue(residue, atomShiftValueList);
            residue = residue.getNext();
            if (i < spinSystemMatches.size()) {
                if (i == 0) {
                    spinSystemMatches.get(i).getSpinSystemA().assignPeaksInSystem(residue);
                }
                spinSystemMatches.get(i).getSpinSystemB().assignPeaksInSystem(residue);
            }
            i++;
        }
    }

    public void thawFragment() {
        setFrozen(false);
        if (resSeqScore != null) {
            Residue residue = resSeqScore.getFirstResidue();
            for (int i = 0; i < resSeqScore.nResidues; i++) {
                for (Atom atom : residue.atoms) {
                    if ((i == 0) && (atom.getName().equals("N") || atom.getName().equals("H"))) {
                        continue;
                    }
                    atom.setPPMValidity(0, false);
                }
                residue = residue.getNext();
            }
        }
        for (SpinSystem spinSystem : getSpinSystems()) {
            spinSystem.clearPeaksInSystem();
        }
    }

    public static boolean testFrag(SpinSystemMatch spinSystemMatch) {
        SeqFragment fragment = getTestFragment(spinSystemMatch);
        Molecule molecule = Molecule.getActive();
        boolean ok = false;
        for (Polymer polymer : molecule.getPolymers()) {
            List<ResidueSeqScore> resSeqScores = fragment.scoreShifts(polymer);
            if (!resSeqScores.isEmpty()) {
                ok = true;
                break;
            }
        }
        return ok;
    }

    String getFragmentSTARString() {
        String polyID = frozen && resSeqScore != null ?
                String.valueOf(resSeqScore.getFirstResidue().getPolymer().getIDNum()) : ".";
        String resID = frozen && resSeqScore != null ?
                String.valueOf(resSeqScore.getFirstResidue().getResNum()) : ".";
        String nResidues = frozen && resSeqScore != null ?
                String.valueOf(resSeqScore.nResidues) : ".";
        String score = frozen && resSeqScore != null ?
                String.format("%9.5f", resSeqScore.score) : ".";
        return String.format("%3d %3s %3s %3s %9s\n", id, polyID, resID, nResidues, score);
    }
}
