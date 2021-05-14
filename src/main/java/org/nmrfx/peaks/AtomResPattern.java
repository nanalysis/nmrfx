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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
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
                ResAtom resAtom = getResAtom(label);
                if (!atomResPatterns.atomResPatterns.isEmpty() && !atomResPatterns.ambiguousResidue) {
                    AtomResPattern arPat = atomResPatterns.atomResPatterns.get(0);
                    if (resAtom.resNum != null) {
                        resMap.put(arPat.resLetter, resAtom.resNum);
                    }

                }
            }
        }
        return resMap;

    }

    static class ResAtom {

        String aName;
        Integer resNum;

        public ResAtom(String aName, Integer resNum) {
            this.aName = aName;
            this.resNum = resNum;
        }
    }

    static ResAtom getResAtom(String label) {
        int dot = label.indexOf(".");
        Integer resNum = null;
        String atomStr = null;
        if (dot != -1) {
            String resStr = label.substring(0, dot);
            try {
                resNum = Integer.parseInt(resStr);
            } catch (NumberFormatException nfE) {
                throw new IllegalArgumentException("Can't parse label");
            }
            atomStr = label.substring(dot + 1);
        } else {
            try {
                resNum = Integer.parseInt(label);
            } catch (NumberFormatException nfE) {
                resNum = null;
                atomStr = label;
            }
        }
        ResAtom resAtom = new ResAtom(atomStr, resNum);
        return resAtom;

    }

    static void setResidue(PeakDim peakDim, Map<String, Integer> resMap) {
        AtomResPatterns atomResPatterns = peakDim.getSpectralDimObj().getAtomResPatterns();
        if (!atomResPatterns.atomResPatterns.isEmpty() && !atomResPatterns.ambiguousResidue
                && !atomResPatterns.ambiguousANames) {
            AtomResPattern arPat = atomResPatterns.atomResPatterns.get(0);
            Integer resNum = resMap.get(arPat.resLetter) + arPat.resDelta;
            String aName = arPat.aName;
            String newLabel = resNum.toString() + "." + aName;
            peakDim.setLabel(newLabel);
        }
    }

    public static boolean assign(String label, PeakDim setPeakDim, PeakDim[] peakDims) throws IllegalArgumentException {
        ResAtom resAtom = getResAtom(label);

        AtomResPatterns atomResPatterns = setPeakDim.getSpectralDimObj().getAtomResPatterns();

        var resMap = new HashMap<String, Integer>();
        List<AtomResPattern> arPats = atomResPatterns.atomResPatterns;
        if (!arPats.isEmpty()) {
            AtomResPattern arPat = arPats.get(0);
            String resLetter = arPat.resLetter;
            int resDelta = arPat.resDelta;

            if ((resAtom.resNum != null) && !atomResPatterns.ambiguousResidue) {
                resMap.put(resLetter, resAtom.resNum - resDelta);
                if ((resAtom.aName == null) && !atomResPatterns.ambiguousANames) {
                    resAtom.aName = arPat.aName;
                }

                String newLabel = resAtom.resNum.toString() + "." + resAtom.aName;
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
        }

        return true;
    }

}
