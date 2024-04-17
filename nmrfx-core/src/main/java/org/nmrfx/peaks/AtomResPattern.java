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
package org.nmrfx.peaks;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.chemistry.AtomSpecifier;
import org.nmrfx.chemistry.Residue;

import java.util.*;

/**
 * @author brucejohnson
 */
public class AtomResPattern {

    String aName;
    String resLetter;
    int resDelta;
    String bondedDim;

    public AtomResPattern(String aName, String resLetter, int resDelta, String bondedDim) {
        this.aName = aName;
        this.resLetter = resLetter;
        this.resDelta = resDelta;
        this.bondedDim = bondedDim;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(resLetter);
        if (resDelta > 0) {
            sBuilder.append("+").append(resDelta);

        } else if (resDelta < 0) {
            sBuilder.append(resDelta);
        }
        sBuilder.append(".");
        sBuilder.append(aName);
        if (!bondedDim.isBlank()) {
            sBuilder.append("_D").append(bondedDim);
        }
        return sBuilder.toString();
    }

    public static AtomResPatterns parsePattern(String pattern, String bondedDim) {
        var patternList = new ArrayList<AtomResPattern>();
        int dot = pattern.indexOf('.');
        String[] resPats;
        String[] atomPats;
        if (dot < 0) {
            resPats = new String[1];
            atomPats = new String[1];
            resPats[0] = "";
            atomPats[0] = "";
        } else {
            resPats = pattern.substring(0, dot).split(",");
            atomPats = pattern.substring(dot + 1).toLowerCase().split(",");
        }
        int i = 0;
        for (String resPat : resPats) {
            for (String atomPat : atomPats) {
                if (!resPat.isBlank() && !atomPat.isBlank()) {
                    String resChar = resPat.substring(0, 1);
                    var delta = 0;
                    if (resPat.length() > 2) {
                        if ((resPat.charAt(1) == '-') && Character.isDigit(resPat.charAt(2))) {
                            delta = -Integer.parseInt(resPat.substring(2));
                        } else if ((resPat.charAt(1) == '+') && Character.isDigit(resPat.charAt(2))) {
                            delta = Integer.parseInt(resPat.substring(2));
                        }
                    }
                    AtomResPattern arPat = new AtomResPattern(atomPat, resChar, delta, bondedDim);
                    patternList.add(arPat);
                }
            }
        }
        AtomResPatterns aPats = new AtomResPatterns(patternList);
        return aPats;
    }

    public static Map<String, Integer> getResMap(PeakDim[] peakDims) {
        var resMap = new HashMap<String, Integer>();
        for (PeakDim peakDim : peakDims) {
            AtomResPatterns atomResPatterns = peakDim.getSpectralDimObj().getAtomResPatterns();
            String label = peakDim.getLabel();
            if (!label.isBlank()) {
                AtomSpecifier atomSpecifier = getResAtom(label);
                if (!atomResPatterns.atomResPatterns.isEmpty() && !atomResPatterns.ambiguousResidue) {
                    AtomResPattern arPat = atomResPatterns.atomResPatterns.get(0);
                    if (atomSpecifier.getResNum() != null) {
                        resMap.put(arPat.resLetter, atomSpecifier.getResNum());
                    }

                }
            }
        }
        return resMap;

    }

    static AtomSpecifier getResAtom(String label) {
        AtomSpecifier atomSpecifier;
        int dot = label.indexOf(".");
        if (dot != -1) {
            atomSpecifier = AtomSpecifier.parseString(label);
        } else {
            try {
                Integer.parseInt(label);
                atomSpecifier = new AtomSpecifier(label, null, null);
            } catch (NumberFormatException nfE) {
                atomSpecifier = new AtomSpecifier(null, null, label);
            }
        }
        return atomSpecifier;

    }

    static void setResidue(PeakDim peakDim, Map<String, Integer> resMap) {
        AtomResPatterns atomResPatterns = peakDim.getSpectralDimObj().getAtomResPatterns();
        if (!atomResPatterns.atomResPatterns.isEmpty() && !atomResPatterns.ambiguousResidue
                && !atomResPatterns.ambiguousANames) {
            AtomResPattern arPat = atomResPatterns.atomResPatterns.get(0);
            if (resMap.containsKey(arPat.resLetter)) {
                Integer resNum = resMap.get(arPat.resLetter) + arPat.resDelta;
                String aName = arPat.aName;
                String newLabel = resNum.toString() + "." + aName;
                peakDim.setLabel(newLabel);
            }
        }
    }

    public static Optional<Atom> setLabelFromUserField(PeakDim peakDim, Residue residue) {
        Atom atom = null;
        AtomResPatterns atomResPatterns = parsePattern(peakDim.getUser(), "");
        if (!atomResPatterns.atomResPatterns.isEmpty() && !atomResPatterns.ambiguousResidue
                && !atomResPatterns.ambiguousANames) {
            AtomResPattern arPat = atomResPatterns.atomResPatterns.get(0);
            if (arPat.resLetter.equals("i")) {
                int iResidue = residue.getResNum();
                StringBuilder stringBuilder = new StringBuilder();
                if (residue.molecule.getPolymers().size() > 1) {
                    stringBuilder.append(residue.getPolymer().getName()).append((":"));
                }
                iResidue += arPat.resDelta;
                stringBuilder.append(iResidue).append(".");
                String aName = arPat.aName;
                stringBuilder.append(aName);
                atom = residue.getAtom(aName);
                String newLabel = stringBuilder.toString();
                peakDim.setLabel(newLabel);
            }
        }
        return Optional.ofNullable(atom);
    }

    static void setFromRelation(PeakDim activeDim, PeakDim[] peakDims) {
        AtomResPatterns atomResPatterns = activeDim.getSpectralDimObj().getAtomResPatterns();
        if (!atomResPatterns.atomResPatterns.isEmpty()) {
            AtomResPattern arPat = atomResPatterns.atomResPatterns.get(0);
            if (!arPat.bondedDim.isBlank()) {

                AtomResonance atomRes = activeDim.getResonance();
                Atom atom = atomRes.getPossibleAtom();
                if (atom != null) {
                    Atom parent = atom.getParent();

                    for (PeakDim peakDim : peakDims) {
                        if (peakDim.getSpectralDimObj().getDimName().equals(arPat.bondedDim)) {
                            peakDim.setLabel(parent.getShortName());
                        }
                    }
                }
            }
        }
    }

    public static boolean assign(String label, PeakDim setPeakDim, PeakDim[] peakDims) throws IllegalArgumentException {
        AtomSpecifier atomSpecifier = getResAtom(label);
        AtomResPatterns atomResPatterns = setPeakDim.getSpectralDimObj().getAtomResPatterns();

        var resMap = new HashMap<String, Integer>();
        List<AtomResPattern> arPats = atomResPatterns.atomResPatterns;
        if (!arPats.isEmpty()) {
            AtomResPattern arPat = arPats.get(0);
            String resLetter = arPat.resLetter;
            int resDelta = arPat.resDelta;

            if ((atomSpecifier.getResNum() != null) && !atomResPatterns.ambiguousResidue) {
                resMap.put(resLetter, atomSpecifier.getResNum() - resDelta);
                if ((atomSpecifier.getAtomName() == null) && !atomResPatterns.ambiguousANames) {
                    atomSpecifier = atomSpecifier.setAtomName(arPat.aName);
                }

                String newLabel = atomSpecifier.getAtomName() == null ? label
                        : atomSpecifier.getResNumString() + "." + atomSpecifier.getAtomName();
                setPeakDim.setLabel(newLabel);
                for (PeakDim peakDim : peakDims) {
                    if (peakDim != setPeakDim) {
                        setResidue(peakDim, resMap);
                    }
                }
            }
            var resMap2 = getResMap(peakDims);
            for (PeakDim peakDim : peakDims) {
                setResidue(peakDim, resMap2);
            }
            setFromRelation(setPeakDim, peakDims);
        }

        return true;
    }

    public static void assignDim(PeakDim peakDim, String label) {
        Peak peak = peakDim.getPeak();
        peakDim.setLabel(label);
        PeakDim[] peakDims = peak.peakDims;
        if (!label.isBlank()) {
            assign(label, peakDim, peakDims);
        }
    }

}
