package org.nmrfx.structure.seqassign;

import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 *
 * @author brucejohnson
 */
public class SeqFragment {
    static private double fragmentScoreProbability = 0.02;
    List<SpinSystemMatch> spinSystemMatches = new ArrayList<>();
    ResidueSeqScore resSeqScore = null;

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

    public static SeqFragment join(SpinSystemMatch spinSysMatch, boolean testMode) {
        SpinSystem spinSysA = spinSysMatch.spinSystemA;
        SpinSystem spinSysB = spinSysMatch.spinSystemB;
        SeqFragment result = null;
        if (spinSysA.fragment.isEmpty() && spinSysB.fragment.isEmpty()) {
            result = new SeqFragment();
            result.spinSystemMatches.add(spinSysMatch);
        } else if (spinSysA.fragment.isPresent() && spinSysB.fragment.isPresent()) {
            if (spinSysA.fragment.get() != spinSysB.fragment.get()) {
                result = spinSysA.fragment.get();
                result.spinSystemMatches.add(spinSysMatch);
                result.spinSystemMatches.addAll(spinSysB.fragment.get().spinSystemMatches);
            }

        } else if (spinSysA.fragment.isPresent()) {
            result = spinSysA.fragment.get();
            result.spinSystemMatches.add(spinSysMatch);

        } else {
            result = spinSysB.fragment.get();
            result.spinSystemMatches.add(0, spinSysMatch);
        }
        if ((result != null) && !testMode) {
            result.updateFragment();
        }
        return result;

    }

    public List<SpinSystemMatch> getSpinSystemMatches() {
        return spinSystemMatches;
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

            System.out.println("remove " + spinSysMatch);
            System.out.println("from");
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

    void updateFragment() {
        for (SpinSystemMatch spinSysMatch : spinSystemMatches) {
            spinSysMatch.spinSystemA.fragment = Optional.of(this);
            spinSysMatch.spinSystemB.fragment = Optional.of(this);
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

    public double[][] getShifts() {
        double[][] result = new double[spinSystemMatches.size() + 2][SpinSystem.ATOM_TYPES.length];
        int iSys = 0;
        SpinSystem spinSysA;
        SpinSystem spinSysB = null;
        for (SpinSystemMatch spinMatch : spinSystemMatches) {
            spinSysA = spinMatch.spinSystemA;
            spinSysB = spinMatch.spinSystemB;
            if (iSys == 0) {
                for (int idx = 0;idx<SpinSystem.ATOM_TYPES.length;idx++) {
                    result[iSys][idx] = spinSysA.getValue(0, idx);
                }
                iSys++;
            }
            for (int idx = 0;idx<SpinSystem.ATOM_TYPES.length;idx++) {
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
                    result[iSys][idx] = spinSysA.getValue(1,idx);
                }
            }
            iSys++;
        }
        if (spinSysB != null) {
            for (int idx = 0;idx<SpinSystem.ATOM_TYPES.length;idx++) {
                result[iSys][idx] = spinSysB.getValue(1, idx);
            }
        }

        for (double[] doubles : result) {
            for (double aDouble : doubles) {
                System.out.printf("%7.2f ", aDouble);
            }
            System.out.println();
        }
        return result;
    }

    List<List<AtomShiftValue>> getShiftValues(double[][] shifts, boolean useAll) {
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
    public List<ResidueSeqScore> scoreFragment(Molecule molecule) {
        List<ResidueSeqScore> result = new ArrayList<>();
        for (Polymer polymer:molecule.getPolymers()) {
            if (polymer.isPeptide()) {
                result.addAll(scoreFragment(polymer));
            }
        }
        return result;
    }

    public List<ResidueSeqScore> scoreFragment(Polymer polymer) {
        double sDevMul = 2.0;
        double[][] shifts = getShifts();
        List<ResidueSeqScore> result = new ArrayList<>();
        List<List<AtomShiftValue>> atomShiftValues = getShiftValues(shifts, false);
        int winSize = atomShiftValues.size();
        List<Residue> residues = polymer.getResidues();
        int nResidues = residues.size();
        int n = nResidues - winSize + 1;
        for (int i = 0; i < n; i++) {
            boolean ok = true;
            double pScore = 1.0;
            for (int j = 0; j < winSize; j++) {
                Residue residue = residues.get(i + j);
                PPMScore ppmScore = FragmentScoring.scoreAtomPPM(fragmentScoreProbability, sDevMul, residue, atomShiftValues.get(j));
                if (!ppmScore.ok()) {
                    ok = false;
                    break;
                }
                pScore *= ppmScore.getTotalScore();
            }
            if (ok) {
                System.out.println(i + " " + residues.get(i).getName() + residues.get(i).getNumber() + pScore);
                ResidueSeqScore resScore = new ResidueSeqScore(residues.get(i), winSize, pScore);
                result.add(resScore);
            }
        }

        if (!result.isEmpty()) {
            ResidueSeqScore.norm(result);
            result.sort(null);
        }
        return result;
    }

    private void assignResidue(Residue residue, List<AtomShiftValue> atomShiftValues) {
        for (AtomShiftValue atomShiftValue:atomShiftValues) {
            String aName = atomShiftValue.getAName();
            double ppm = atomShiftValue.getPPM();
            residue.getAtom(aName).setPPM(ppm);
        }
    }

    public void freezeFragment(ResidueSeqScore resSeqScore) {
        double[][] shifts = getShifts();
        List<List<AtomShiftValue>> atomShiftValues = getShiftValues(shifts, true);
        int winSize = atomShiftValues.size();
        Residue firstResidue = resSeqScore.getFirstResidue();
        Polymer polymer = firstResidue.getPolymer();
        Residue residue = firstResidue;
        for (var atomShiftValueList:atomShiftValues) {
            assignResidue(residue, atomShiftValueList);
            residue = residue.getNext();
        }
    }

    public static boolean testFrag(SpinSystemMatch spinSystemMatch) {
        SeqFragment fragment = getTestFragment(spinSystemMatch);
        Molecule molecule = Molecule.getActive();
        boolean ok = false;
        for (Polymer polymer : molecule.getPolymers()) {
            List<ResidueSeqScore> resSeqScores = fragment.scoreFragment(polymer);
            if (!resSeqScores.isEmpty()) {
                ok = true;
                break;
            }
        }
        return ok;
    }
}
