package org.nmrfx.structure.seqassign;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;

/**
 *
 * @author brucejohnson
 */
public class SeqFragment {

    List<SpinSystemMatch> spinSystemMatches = new ArrayList<>();

    public static SeqFragment join(SpinSystemMatch spinSysMatch, boolean testMode) {
        SpinSystem spinSysA = spinSysMatch.spinSystemA;
        SpinSystem spinSysB = spinSysMatch.spinSystemB;
        SeqFragment result;
        if (!spinSysA.fragment.isPresent() && !spinSysB.fragment.isPresent()) {
            result = new SeqFragment();
            result.spinSystemMatches.add(spinSysMatch);
        } else if (spinSysA.fragment.isPresent() && spinSysB.fragment.isPresent()) {
            result = spinSysA.fragment.get();
            result.spinSystemMatches.add(spinSysMatch);
            for (SpinSystemMatch match : spinSysB.fragment.get().spinSystemMatches) {
                result.spinSystemMatches.add(match);
            }

        } else if (spinSysA.fragment.isPresent()) {
            result = spinSysA.fragment.get();
            result.spinSystemMatches.add(spinSysMatch);

        } else {
            result = spinSysB.fragment.get();
            result.spinSystemMatches.add(0, spinSysMatch);

        }
        if (!testMode) {
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

            System.out.println("remove " + spinSysMatch.toString());
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
        double[][] result = new double[spinSystemMatches.size() + 2][SpinSystem.RES_MTCH.length];
        int iSys = 0;
        SpinSystem spinSysA = null;
        SpinSystem spinSysB = null;
        for (SpinSystemMatch spinMatch : spinSystemMatches) {
            spinSysA = spinMatch.spinSystemA;
            spinSysB = spinMatch.spinSystemB;
            if (iSys == 0) {
                int j = 0;
                for (int idx : SpinSystem.RES_MTCH) {
                    result[iSys][j++] = spinSysA.getValue(0, idx);
                }
                iSys++;
            }
            int j = 0;
            for (int idx : SpinSystem.RES_MTCH) {
                if (spinMatch.matched[j]) {
                    double vA = spinSysA.getValue(1, idx);
                    double vB = spinSysB.getValue(0, idx);
                    double avg = (vA + vB) / 2.0;
                    result[iSys][j] = avg;
                } else {
                    result[iSys][j] = Double.NaN;
                }
                j++;
            }
            iSys++;
        }
        if (spinSysB != null) {
            int j = 0;
            for (int idx : SpinSystem.RES_MTCH) {
                result[iSys][j] = spinSysB.getValue(1, idx);
                j++;
            }
        }

        for (int i = 0; i < result.length; i++) {
            for (int k = 0; k < result[i].length; k++) {
                System.out.printf("%7.2f ", result[i][k]);
            }
            System.out.println("");
        }
        return result;
    }

    List<List<AtomShiftValue>> getShiftValues(double[][] shifts) {
        List<List<AtomShiftValue>> result = new ArrayList<>();
        for (int i = 0; i < shifts.length; i++) {
            List<AtomShiftValue> values = new ArrayList<>();
            for (int k = 0; k < shifts[i].length; k++) {
                String aName = SpinSystem.getAtomName(SpinSystem.RES_MTCH[k]);
                double value = shifts[i][k];
                if (value != Double.NaN) {
                    AtomShiftValue atomValue = new AtomShiftValue(aName, value, null);
                    values.add(atomValue);
                }
            }
            result.add(values);
        }
        return result;
    }

    public List<ResidueSeqScore> scoreFragment(Polymer polymer) {
        double sDevMul = 2.0;
        double pOK = 0.05;
        double[][] shifts = getShifts();
        List<ResidueSeqScore> result = new ArrayList<>();
        List<List<AtomShiftValue>> atomShiftValues = getShiftValues(shifts);
        int winSize = atomShiftValues.size();
        List<Residue> residues = polymer.getResidues();
        int nResidues = residues.size();
        int n = nResidues - winSize + 1;
        for (int i = 0; i < n; i++) {
            boolean ok = true;
            double pScore = 1.0;
            for (int j = 0; j < winSize; j++) {
                Residue residue = residues.get(i + j);
                PPMScore ppmScore = FragmentScoring.scoreAtomPPM(pOK, sDevMul, residue, atomShiftValues.get(j));
                if (!ppmScore.ok()) {
                    ok = false;
                    break;
                }
                pScore *= ppmScore.getTotalScore();
            }
            if (ok) {
                System.out.println(i + " " + residues.get(i).getName() + residues.get(i).getNumber());
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
}
