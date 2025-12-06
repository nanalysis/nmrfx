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
        if (spinSysA.fragment().isPresent()) {
            fragmentA = spinSysA.fragment().get();
        }
        if (spinSysB.fragment().isPresent()) {
            fragmentB = spinSysB.fragment().get();
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

    public void setResidueSeqScoreAtPosition(SpinSystem spinSys, Residue residue) {
        Polymer polymer = residue.getPolymer();
        Molecule molecule = (Molecule) polymer.molecule;
        int iRes = polymer.getResidues().indexOf(residue);
        if (isFrozen()) {
            thawFragment();
        }

        List<SpinSystem> spinSystems = getSpinSystems();
        int index = spinSystems.indexOf(spinSys);
        List<ResidueSeqScore> resSeqScores = scoreShifts(molecule);
        for (ResidueSeqScore residueSeqScore : resSeqScores) {
            Residue firstResidue = residueSeqScore.getFirstResidue();
            int jRes = polymer.getResidues().indexOf(firstResidue);
            int deltaRes = iRes - jRes;
            if (index == (deltaRes - 1)) {
                setResSeqScore(residueSeqScore);
                break;
            }
        }
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
        if (spinSysA.fragment().isEmpty() && spinSysB.fragment().isEmpty()) {
            result = new SeqFragment();
            result.spinSystemMatches.add(spinSysMatch);
        } else if (spinSysA.fragment().isPresent() && spinSysB.fragment().isPresent()) {
            if (spinSysA.fragment().get() != spinSysB.fragment().get()) {
                result = spinSysA.fragment().get();
                result.spinSystemMatches.add(spinSysMatch);
                result.spinSystemMatches.addAll(spinSysB.fragment().get().spinSystemMatches);
                if (spinSysA.fragment().get().isFrozen() || spinSysB.fragment().get().isFrozen()) {
                    newFrozen = true;
                }
            }

        } else if (spinSysA.fragment().isPresent()) {
            result = spinSysA.fragment().get();
            result.spinSystemMatches.add(spinSysMatch);
            if (spinSysA.fragment().get().isFrozen()) {
                newFrozen = true;
            }

        } else {
            result = spinSysB.fragment().get();
            result.spinSystemMatches.add(0, spinSysMatch);
            if (spinSysB.fragment().get().isFrozen()) {
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
        if (spinSysA.fragment().isPresent() && spinSysB.fragment().isPresent()) {
            SeqFragment currentFragment = spinSysA.fragment().get();
            List<SpinSystemMatch> spinSystemMatches = currentFragment.spinSystemMatches;

            SpinSystemMatch firstMatch = spinSystemMatches.get(0);
            SpinSystemMatch lastMatch = spinSystemMatches.get(spinSystemMatches.size() - 1);
            spinSysMatch.spinSystemA.setFragment(null);
            spinSysMatch.spinSystemB.setFragment(null);
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
            spinSysMatch.spinSystemA.setFragment(this);
            spinSysMatch.spinSystemB.setFragment(this);
        }
        var resSeqScores = scoreShifts(Molecule.getActive());
        if (resSeqScores.size() == 1) {
            setResSeqScore(resSeqScores.get(0));
        }
    }

    void updateSpinSystemMatches() {
        List<SpinSystemMatch> newMatches = new ArrayList<>();
        for (SpinSystemMatch spinSystemMatch : spinSystemMatches) {
            SpinSystem systemA = spinSystemMatch.getSpinSystemA();
            systemA.confirmS().ifPresent(match -> newMatches.add(match));
        }
        spinSystemMatches.clear();
        spinSystemMatches.addAll(newMatches);
    }

    boolean addNext(SpinSystemMatch spinSysMatch) {
        boolean result = false;
        if (spinSystemMatches.isEmpty() || (spinSysMatch.spinSystemA == spinSystemMatches.get(spinSystemMatches.size() - 1).spinSystemB)) {
            spinSystemMatches.add(spinSysMatch);
            result = true;
        }
        return result;
    }

    public static List<List<AtomShiftValue>> getShiftsForSystem(SpinSystem spinSystem) {
        List<List<AtomShiftValue>> result = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            List<AtomShiftValue> values = new ArrayList<>();
            result.add(values);

            for (var entry : spinSystem.getShiftValues(i).entrySet()) {
                AtomShiftValue atomValue = new AtomShiftValue(entry.getKey().name(), entry.getValue().value(), null);
                values.add(atomValue);
            }
        }

        return result;
    }

    public List<List<AtomShiftValue>> getShifts() {
        List<List<AtomShiftValue>> result = new ArrayList<>();
        int iSys = 0;
        SpinSystem spinSysA;
        SpinSystem spinSysB = null;
        for (SpinSystemMatch spinMatch : spinSystemMatches) {
            spinSysA = spinMatch.spinSystemA;
            spinSysB = spinMatch.spinSystemB;
            if (iSys == 0) {
                List<AtomShiftValue> values = new ArrayList<>();
                result.add(values);

                for (var entry : spinSysA.getShiftValues(0).entrySet()) {
                    AtomShiftValue atomValue = new AtomShiftValue(entry.getKey().name(), entry.getValue().value(), null);
                    values.add(atomValue);
                }
            }
            List<AtomShiftValue> values = new ArrayList<>();
            result.add(values);
            for (SpinSystem.AtomEnum matchedAtom : spinMatch.matched) {
                if (matchedAtom.resMatch()) {
                    Optional<Double> vAOpt = spinSysA.getValue(1, matchedAtom);
                    Optional<Double> vBOpt = spinSysB.getValue(0, matchedAtom);
                    if (vAOpt.isPresent() && vBOpt.isPresent()) {
                        double avg = (vAOpt.get() + vBOpt.get()) / 2.0;
                        AtomShiftValue atomValue = new AtomShiftValue(matchedAtom.name(), avg, null);
                        values.add(atomValue);
                    }
                }
            }
            for (var shiftValue : spinSysA.shiftValues[1].entrySet()) {
                if (!shiftValue.getKey().resMatch()) {
                    AtomShiftValue atomValue = new AtomShiftValue(shiftValue.getKey().name(), shiftValue.getValue().value(), null);
                    values.add(atomValue);
                }
            }
            iSys++;
        }
        if (spinSysB != null) {
            List<AtomShiftValue> values = new ArrayList<>();
            result.add(values);

            for (var entry : spinSysB.getShiftValues(1).entrySet()) {
                AtomShiftValue atomValue = new AtomShiftValue(entry.getKey().name(), entry.getValue().value(), null);
                values.add(atomValue);
            }
        }
        return result;
    }

    public List<ResidueSeqScore> scoreShifts(Molecule molecule) {
        List<List<AtomShiftValue>> shiftValues = getShifts();
        return scoreShifts(molecule, shiftValues, this);
    }

    public static List<ResidueSeqScore> scoreShifts(Molecule molecule, List<List<AtomShiftValue>> shiftValues, SeqFragment seqFragment) {
        List<ResidueSeqScore> result = new ArrayList<>();
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isPeptide()) {
                result.addAll(scoreShifts(polymer, shiftValues, seqFragment));
            }
        }
        return result;
    }
    public static List<ResidueSeqScore> scoreShifts(Molecule molecule, List<List<AtomShiftValue>> shiftValues, double sdevRatio) {
        List<ResidueSeqScore> result = new ArrayList<>();
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isPeptide()) {
                result.addAll(scoreShifts1(polymer, shiftValues, sdevRatio));
            }
        }
        return result;
    }

    public List<ResidueSeqScore> scoreShifts(Polymer polymer) {
        List<List<AtomShiftValue>> shiftValues = getShifts();
        return scoreShifts(polymer, shiftValues, this);
    }

    public static List<ResidueSeqScore> scoreShifts(Polymer polymer, List<List<AtomShiftValue>> shiftValues, SeqFragment fragment) {
        double sDevMul = 2.0;
        List<ResidueSeqScore> result = new ArrayList<>();
        int winSize = shiftValues.size();
        if (winSize < 1) {
            return result;
        }
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
                PPMScore ppmScore = FragmentScoring.scoreAtomPPM(spinSysProbability, sDevMul, residue, shiftValues.get(j));
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

    public static List<ResidueSeqScore> scoreShifts1(Polymer polymer, List<List<AtomShiftValue>> shiftValues, double sDevMul) {
        List<ResidueSeqScore> result = new ArrayList<>();
        int winSize = shiftValues.size();
        if (winSize < 1) {
            return result;
        }
        List<Residue> residues = polymer.getResidues();
        int nResidues = residues.size();
        int n = nResidues - winSize + 1;
        for (int i = 1; i < n; i++) {
            Residue residue = residues.get(i);
            Optional<Double> scoreOpt = FragmentScoring.scoreResidueAtomPPM(spinSysProbability, sDevMul, residue, shiftValues);
            scoreOpt.ifPresent(scoreVal -> {
                ResidueSeqScore resScore = new ResidueSeqScore(residue, winSize, scoreVal);
                result.add(resScore);
            });
        }
        return result;
    }

    private void assignResidue(Residue residue, List<AtomShiftValue> atomShiftValues) {
        for (AtomShiftValue atomShiftValue : atomShiftValues) {
            String aName = atomShiftValue.getAName();
            Atom atom = residue.getAtom(aName);
            if (atom != null) {
                double ppm = atomShiftValue.getPPM();
                atom.setPPM(ppm);
            } else {
                System.out.println("no atom " + aName);
            }
        }
    }

    public void freezeFragment(ResidueSeqScore resSeqScore) {
        setFrozen(true);
        List<List<AtomShiftValue>> atomShiftValues = getShifts();
        Residue firstResidue = resSeqScore.getFirstResidue();
        Residue residue = firstResidue;
        for (var atomShiftValueList : atomShiftValues) {
            assignResidue(residue, atomShiftValueList);
            residue = residue.getNext();
        }
        residue = firstResidue.getNext();
        spinSystemMatches.get(0).getSpinSystemA().assignPeaksInSystem(residue);
        for (int i = 0; i < spinSystemMatches.size(); i++) {
            residue = residue.getNext();
            spinSystemMatches.get(i).getSpinSystemB().assignPeaksInSystem(residue);
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
        return !fragment.scoreShifts(molecule).isEmpty();
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

    public Residue getResidueAtPostion(int pos) {
        Residue firstResidue = resSeqScore.getFirstResidue();
        Residue residue = firstResidue;
        for (int i = 0; i < pos; i++) {
            residue = residue.getNext();
        }
        return residue;
    }

}
