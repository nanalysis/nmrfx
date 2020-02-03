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
package org.nmrfx.structure.chemistry.constraints;

import org.nmrfx.structure.chemistry.*;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.structure.utilities.Util;
import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

@SuppressWarnings({"UnusedDeclaration"})
enum Flags {

    REDUNDANT("redundant", 'r') {
    },
    FIXED("fixed", 'f') {
    },
    MAXAMBIG("maxamb", 'a') {
    },
    MINCONTRIB("mincontrib", 'c') {
    },
    DIAGONAL("diagonal", 'd') {
    },
    MINPPM("minppm", 'p') {
    },
    MAXVIOL("maxviol", 'v') {
    },
    LABEL("label", 'l') {
    },
    USER("user", 'u') {
    };
    private String description;
    private char charDesc;

    Flags(String description, char charDesc) {
        this.description = description;
        this.charDesc = charDesc;
    }

    void set(Noe noe) {
        noe.inactivate(this);
    }

    public String getDescription() {
        return description;
    }

    public char getCharDesc() {
        return charDesc;
    }
}

enum DisTypes {

    MINIMUM("minimum") {
        @Override
        public double getDistance(Noe noe) {
            return noe.getDisStatAvg().getMin();
        }
    },
    MAXIMUM("maximum") {
        @Override
        public double getDistance(Noe noe) {
            return noe.getDisStatAvg().getMax();
        }
    },
    MEAN("mean") {
        @Override
        public double getDistance(Noe noe) {
            return noe.getDisStatAvg().getMean();
        }
    };
    private String description;

    public abstract double getDistance(Noe noe);

    DisTypes(String description) {
        this.description = description;
    }
}

public class Noe implements Constraint, Serializable {

    private static boolean useDistances = false;
    private static int nStructures = 0;
    private static double tolerance = 0.2;
    private static DistanceStat defaultStat = new DistanceStat();
    private int idNum = 0;
    public SpatialSetGroup spg1;
    public SpatialSetGroup spg2;
    public Peak peak = null;
    private double intensity = 0.0;
    private double volume = 0.0;
    private double scale = 1.0;
    public double atomScale = 1.0;
    public DistanceStat disStat = defaultStat;
    private DistanceStat disStatAvg = defaultStat;
    private double lower = 0.0;
    public double target = 0.0;
    private double upper = 0.0;
    public int dcClass = 0;
    private double ppmError = 0.0;
    private short active = 1;
    public boolean symmetrical = false;
    private double contribution = 1.0;
    private double disContrib = 1.0;
    private int nPossible = 0;
    private double networkValue = 1;
    private boolean swapped = false;
    private boolean filterSwapped = false;
    public Map resMap = null;
    EnumSet<Flags> activeFlags = null;
    private static DisTypes distanceType = DisTypes.MINIMUM;
    private GenTypes genType = GenTypes.MANUAL;
    public static int ppmSet = 0;

    public Noe(Peak p, SpatialSet sp1, SpatialSet sp2, double newScale) {
        SpatialSetGroup spg1t = new SpatialSetGroup(sp1);
        SpatialSetGroup spg2t = new SpatialSetGroup(sp2);
        this.spg1 = spg1t;
        this.spg2 = spg2t;
        if (spg1t.compare(spg2t) >= 0) {
            swapped = true;
        }

        peak = p;
        scale = newScale;
        activeFlags = EnumSet.noneOf(Flags.class);

    }

    public Noe(Peak p, SpatialSetGroup spg1, SpatialSetGroup spg2, double newScale) {
        this.spg1 = spg1;
        this.spg2 = spg2;
        if (spg1.compare(spg2) > 0) {
            swapped = true;
        }
        peak = p;
        scale = newScale;
        activeFlags = EnumSet.noneOf(Flags.class);
    }

    @Override
    public String toString() {
        char sepChar = '\t';
        StringBuilder sBuild = new StringBuilder();
        sBuild.append(spg1.getFullName()).append(sepChar);
        sBuild.append(spg2.getFullName()).append(sepChar);
        sBuild.append(String.format("%.1f", lower)).append(sepChar).append(String.format("%.1f", upper)).append(sepChar);
        sBuild.append(peak != null ? peak.getName() : "").append(sepChar);
        sBuild.append(idNum).append(sepChar);
        sBuild.append(genType).append(sepChar);
        sBuild.append(String.format("%3d", getDeltaRes())).append(sepChar);
        sBuild.append(String.format("%.2f", ppmError)).append(sepChar);
        sBuild.append(String.format("%b", symmetrical)).append(sepChar);
        sBuild.append(String.format("%.2f", networkValue)).append(sepChar);
        sBuild.append(String.format("%.2f", disContrib)).append(sepChar);
        sBuild.append(String.format("%10s", getActivityFlags())).append(sepChar);
        sBuild.append(String.format("%.2f", contribution));
        return sBuild.toString();
    }

    @Override
    public int getID() {
        return idNum;
    }

    public void setID(int id) {
        this.idNum = id;
    }

    public static double getTolerance() {
        return tolerance;
    }

    public static void setTolerance(double value) {
        tolerance = value;
    }

    public void updatePPMError(MatchCriteria[] matchCriteria) {
        double sum = 0.0;
        int nCriteria = matchCriteria.length;
        SpatialSet[] spSets = new SpatialSet[nCriteria];
        for (int i = 0; i < nCriteria; i++) {
            if (matchCriteria[i] != null) {
                matchCriteria[i].setPPM(peak);
                if (i == 0) {
                    spSets[i] = spg1.getFirstSet();
                } else if (i == 1) {
                    spSets[i] = spg2.getFirstSet();
                } else {
                    spSets[i] = spSets[i - 2].atom.getParent().spatialSet;
                }
                PPMv ppmv = spSets[i].getPPM(ppmSet);
                double dp;

                if (ppmv != null) {
                    double delta = IdPeak.getPPMDelta(ppmv.getValue(), matchCriteria[i]);
                    dp = delta / matchCriteria[i].getTol();
                } else {
                    dp = 1.0e30;
                    System.out.println("no ppm for " + spSets[i].getFullName());
                }
                sum += dp * dp;
            }
        }

        ppmError = Math.exp(-1.0 * sum / 2.0);

    }

    public static void updatePPMErrors(NoeSet noeSet) {
        MatchCriteria[] matchCriteria = null;
        PeakList lastList = null;
        for (Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
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
                    noe.updatePPMError(matchCriteria);
                }
            }
        }
    }

    public static void updateGenTypes(NoeSet noeSet) {
        Map<String, NoeMatch> map = new HashMap<>();
        MatchCriteria[] matchCriteria = null;
        PeakList lastList = null;
        for (Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
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
//                    noe.updateGenType(map, matchCriteria);
                }
            }
        }
    }

//    public void updateGenType(Map<String, NoeMatch> map, MatchCriteria[] matchCriteria) {
//        if ((peak != null) && (peak.getStatus() >= 0)) {
//            map.clear();
//            PeakDim peakDim = peak.getPeakDim(matchCriteria[0].getDim());
//            double ppm = peakDim.getChemShift();
//            matchCriteria[0].setPPM(ppm);
//            ArrayList res1s = peakDim.getResonances();
//
//            peakDim = peak.getPeakDim(matchCriteria[1].getDim());
//            ppm = peakDim.getChemShift();
//            matchCriteria[1].setPPM(ppm);
//            ArrayList res2s = peakDim.getResonances();
//
//            int nRes1 = res1s.size();
//            int nRes2 = res2s.size();
//            if ((nRes1 > 0) && (nRes2 > 0)) {
//                if ((nRes1 != 1) && (nRes2 != 1) && (nRes1 != nRes2)) {
//                    throw new IllegalArgumentException("Peak \"" + peak.getName() + "\" has unbalanced assignments");
//                }
//                int maxN = nRes1 > nRes2 ? nRes1 : nRes2;
//
//                for (int iRes = 0; iRes < maxN; iRes++) {
//                    AtomResonance r1 = null;
//                    if (iRes < nRes1) {
//                        r1 = (AtomResonance) res1s.get(iRes);
//                    } else {
//                        r1 = (AtomResonance) res1s.get(0);
//                    }
//                    AtomResonance r2 = null;
//                    if (iRes < nRes2) {
//                        r2 = (AtomResonance) res2s.get(iRes);
//                    } else {
//                        r2 = (AtomResonance) res2s.get(0);
//                    }
//                    Atom r1Atom = r1.getAtom();
//                    SpatialSet sp1 = null;
//                    SpatialSet sp2 = null;
//                    if ((r1Atom != null)) {
//                        sp1 = r1Atom.spatialSet;
//                    }
//                    Atom r2Atom = r2.getAtom();
//                    if ((r2Atom != null)) {
//                        sp2 = r2Atom.spatialSet;
//                    }
//                    if ((sp1 != null) && (sp2 != null) && (sp1 != sp2)) {
//                        String name = sp1.getFullName() + "_" + sp2.getFullName();
//                        NoeMatch match = new NoeMatch(sp1, sp2, Constraint.GenTypes.MANUAL, 0.0);
//                        map.put(name, match);
//                    }
//                }
//            }
//
//            if (matchCriteria[2] != null) {
//                peakDim = peak.getPeakDim(matchCriteria[2].getDim());
//                ppm = peakDim.getChemShift();
//                matchCriteria[2].setPPM(ppm);
//            }
//
//            if (matchCriteria[3] != null) {
//                peakDim = peak.getPeakDim(matchCriteria[3].getDim());
//                ppm = peakDim.getChemShift();
//                matchCriteria[3].setPPM(ppm);
//            }
//            Atom[][] atoms = getAtoms(peak);
//            int pDim1 = matchCriteria[0].getDim();
//            int pDim2 = matchCriteria[1].getDim();
//            if ((atoms[pDim1] != null) && (atoms[pDim2] != null)) {
//                int nProtons1 = atoms[pDim1].length;
//                int nProtons2 = atoms[pDim2].length;
//                if ((nProtons1 > 0) && (nProtons2 > 0)) {
//                    if ((nProtons1 == nProtons2) || (nProtons1 == 1) || (nProtons2 == 1)) {
//                        int maxN = nProtons1 > nProtons2 ? nProtons1 : nProtons2;
//                        for (int iProton = 0; iProton < maxN; iProton++) {
//                            SpatialSet sp1 = null;
//                            SpatialSet sp2 = null;
//                            int iProton1 = iProton;
//                            int iProton2 = iProton;
//                            if (iProton >= nProtons1) {
//                                iProton1 = 0;
//                            }
//                            if (iProton >= nProtons2) {
//                                iProton2 = 0;
//                            }
//                            if (atoms[pDim1][iProton1] != null) {
//                                sp1 = atoms[pDim1][iProton1].spatialSet;
//                            }
//                            if ((atoms[pDim2][iProton2] != null)) {
//                                sp2 = atoms[pDim2][iProton2].spatialSet;
//                            }
//                            if ((sp1 != null) && (sp2 != null) && (sp1 != sp2)) {
//                                String name = sp1.getFullName() + "_" + sp2.getFullName();
//                                NoeMatch match = new NoeMatch(sp1, sp2, Constraint.GenTypes.MANUAL, 0.0);
//                                map.put(name, match);
//                            }
//                        }
//
//                    }
//                }
//            }
//
//            int nMan = map.size();
//            Constraint.GenTypes type = Constraint.GenTypes.MANUAL;
//
//            String name = spg1.getFullName() + "_" + spg2.getFullName();
//            if (!map.containsKey(name)) {
//                type = Constraint.GenTypes.AUTOMATIC;
//                if (nMan > 0) {
//                    type = Constraint.GenTypes.AUTOPLUS;
//                }
//            }
//            setGenType(type);
//        }
//    }
//    public void updatePPMError() {
//   //
//
//    }
    public SpatialSetGroup getSPG(int setNum, boolean getSwapped, boolean filterMode) {
        if (setNum == 0) {
            if ((filterMode && filterSwapped) || (!filterMode && swapped && getSwapped)) {
                return spg2;
            } else {
                return spg1;
            }
        } else if ((filterMode && filterSwapped) || (!filterMode && swapped && getSwapped)) {
            return spg1;
        } else {
            return spg2;
        }
    }

    public static int getNStructures() {
        return nStructures;
    }

    @Override
    public DistanceStat getStat() {
        return disStat;
    }

    public static int getSize(NoeSet noeSet) {
        return noeSet.getSize();
    }

    public static void resetConstraints(NoeSet noeSet) {
        noeSet.clear();
    }

    public static synchronized List<Noe> getConstraints(NoeSet noeSet, boolean requireActive) {
        return noeSet.getConstraints("", requireActive);
    }

    public String getPeakListName() {
        String listName = "";
        if (peak != null) {
            listName = peak.peakList.getName();
        }
        return listName;
    }

    public int getPeakNum() {
        int peakNum = 0;
        if (peak != null) {
            peakNum = peak.getIdNum();
        }
        return peakNum;
    }

    public String getEntity(SpatialSetGroup spg) {
        String value = "";
        if (spg != null) {
            Entity entity = spg.getAnAtom().getEntity();
            if (entity instanceof Residue) {
                value = ((Residue) entity).polymer.getName();
            } else {
                value = ((Compound) entity).getName();
            }
        }
        return value;
    }

    public static double avgDistance(List<Double> dArray, double expValue, int nMonomers, boolean sumAverage) {
        double sum = 0.0;
        int n = 0;
        for (Double dis : dArray) {
            sum += Math.pow(dis, expValue);
            n++;

        }
        if (!sumAverage) {
            nMonomers = n;
        }
        double distance = Math.pow((sum / nMonomers), 1.0 / expValue);
        return distance;

    }

    static Atom[][] getProtons(Atom[][] atoms) {
        Atom[][] protons = new Atom[2][0];
        if (atoms[0] != null) {
            protons[0] = new Atom[atoms[0].length];
            protons[1] = new Atom[atoms[0].length];
            int k = 0;
            for (int j = 0; j < atoms[0].length; j++) {
                int nProton = 0;
                for (Atom[] atom : atoms) {
                    if ((atom != null) && (j < atom.length)) {
                        if (atom[j].aNum == 1) {
                            if (nProton == 0) {
                                protons[0][k] = atom[j];
                                nProton++;
                            } else if (nProton == 1) {
                                protons[1][k] = atom[j];
                                k++;
                                nProton++;
                            }
                        }
                    }
                }
            }
        }
        return protons;
    }

    public boolean isActive() {
        boolean activeFlag = false;
        if (activeFlags.isEmpty()) {
            activeFlag = true;
        } else if (activeFlags.size() == 1) {
            if (getActivityFlags().equals("f")) {
                activeFlag = true;
            }
        }
        return activeFlag;
    }

    @Override
    public boolean isUserActive() {
        return (active > 0);
    }

    public int getActive() {
        return active;
    }

    public void setActive(int newState) {
        this.active = (short) newState;
    }

    public String getActivityFlags() {
        StringBuilder result = new StringBuilder();
        for (Flags f : activeFlags) {
            result.append(f.getCharDesc());
        }
        return result.toString();
    }

    public void inactivate(Flags enumVal) {
        activeFlags.add(enumVal);
    }

    public void activate(Flags enumVal) {
        activeFlags.remove(enumVal);
    }

    /**
     * @return the distance
     */
    public double getDistance() {

        return distanceType.getDistance(this);
    }

    @Override
    public double getValue() {
        return getDistance();
    }

    @Override
    public String toSTARString() {
        if (peak != NoeSet.lastPeakWritten) {
            NoeSet.ID++;
            NoeSet.lastPeakWritten = peak;
            NoeSet.memberID = 1;
        } else {
            NoeSet.memberID++;
        }
        String logic = ".";
        if (nPossible > 1) {
            logic = "OR";
        }

        StringBuilder result = new StringBuilder();
        char sep = ' ';
        char stringQuote = '"';

        //        Gen_dist_constraint.ID
        result.append(NoeSet.ID);
        result.append(sep);
        //_Gen_dist_constraint.Member_ID
        result.append(NoeSet.memberID);
        result.append(sep);
        //_Gen_dist_constraint.Member_logic_code
        result.append(logic);
        result.append(sep);
        spg1.addToSTARString(result);
        result.append(sep);
        //_Gen_dist_constraint.Resonance_ID_1
        result.append('.');
        result.append(sep);
        spg2.addToSTARString(result);
        result.append(sep);
        //_Gen_dist_constraint.Resonance_ID_2
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Intensity_val
        result.append(intensity);
        result.append(sep);
        //_Gen_dist_constraint.Intensity_lower_val_err
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Intensity_upper_val_err
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Distance_val
        if (target < lower) {
            target = (lower + upper) / 2.0;
        }
        result.append(target);
        result.append(sep);
        //_Gen_dist_constraint.Distance_lower_bound_val
        result.append(lower);
        result.append(sep);
        //_Gen_dist_constraint.Distance_upper_bound_val
        result.append(upper);
        result.append(sep);
        //_Gen_dist_constraint.Contribution_fractional_val
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Spectral_peak_ID
        if (peak == null) {
            result.append('.');
            result.append(sep);
            result.append('.');
        } else {
            result.append(peak.getIdNum());
            result.append(sep);
            //_Gen_dist_constraint.Spectral_peak_list_ID
            result.append(peak.getPeakList().getId());
        }
        result.append(sep);

        result.append("."); // fixme do we need to save ssid here

        result.append(sep);
        result.append("1");
        return result.toString();
    }

    /**
     * @return the genType
     */
    public GenTypes getGenType() {
        return genType;
    }

    /**
     * @param genType the genType to set
     */
    public void setGenType(GenTypes genType) {
        this.genType = genType;
    }

    public static Atom[][] getAtoms(Peak peak) {
        Atom[][] atoms = new Atom[peak.peakList.nDim][];

        for (int i = 0; i < peak.peakList.nDim; i++) {
            atoms[i] = null;
            String label = peak.peakDims[i].getLabel();
            String[] elems = label.split(" ");

            if (elems.length == 0) {
                continue;
            }

            int nElems = elems.length;
            atoms[i] = new Atom[nElems];

            for (int j = 0; j < elems.length; j++) {
                atoms[i][j] = Molecule.getAtomByName(elems[j]);
            }
        }

        return atoms;
    }

    static class NoeMatch {

        final SpatialSet sp1;
        final SpatialSet sp2;
        final Constraint.GenTypes type;
        final double error;

        NoeMatch(SpatialSet sp1, SpatialSet sp2, Constraint.GenTypes type, double error) {
            this.sp1 = sp1;
            this.sp2 = sp2;
            this.type = type;
            this.error = error;
        }

        @Override
        public String toString() {
            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append(sp1.atom.getShortName()).append("\t");
            sBuilder.append(sp2.atom.getShortName()).append("\t");
            sBuilder.append(type).append("\t");
            sBuilder.append(error);
            return sBuilder.toString();
        }
    }

    /**
     * @return the intensity
     */
    public double getIntensity() {
        return intensity;
    }

    /**
     * @param intensity the intensity to set
     */
    public void setIntensity(double intensity) {
        this.intensity = intensity;
    }

    /**
     * @return the volume
     */
    public double getVolume() {
        return volume;
    }

    /**
     * @param volume the volume to set
     */
    public void setVolume(double volume) {
        this.volume = volume;
    }

    /**
     * @return the scale
     */
    public double getScale() {
        return scale;
    }

    /**
     * @param scale the scale to set
     */
    public void setScale(double scale) {
        this.scale = scale;
    }

    /**
     * @return the disStatAvg
     */
    public DistanceStat getDisStatAvg() {
        return disStatAvg;
    }

    public void setDisStatAvg(DistanceStat disStatAvg) {
        this.disStatAvg = disStatAvg;
    }

    /**
     * @return the lower
     */
    public double getLower() {
        return lower;
    }

    /**
     * @param lower the lower to set
     */
    public void setLower(double lower) {
        this.lower = lower;
    }

    /**
     * @return the upper
     */
    public double getUpper() {
        return upper;
    }

    /**
     * @param upper the upper to set
     */
    public void setUpper(double upper) {
        this.upper = upper;
    }

    /**
     * @return the contribution
     */
    public double getContribution() {
        return contribution;
    }

    public void setContribution(double contribution) {
        this.contribution = contribution;
    }

    /**
     * @return the disContrib
     */
    public double getDisContrib() {
        return disContrib;
    }

    public void setDisContrib(double value) {
        disContrib = value;
    }

    /**
     * @return the nPossible
     */
    public int getNPossible() {
        return nPossible;
    }

    public void setNPossible(int value) {
        nPossible = value;
    }

    /**
     * @return the netWorkValue
     */
    public double getNetworkValue() {
        return networkValue;
    }

    public void setNetworkValue(double value) {
        this.networkValue = value;
    }

    /**
     * @return the ppmError
     */
    public double getPpmError() {
        return ppmError;
    }

    /**
     * @param ppmError the ppmError to set
     */
    public void setPpmError(double ppmError) {
        this.ppmError = ppmError;
    }

    public void setFilterSwapped(boolean swapped) {
        filterSwapped = swapped;
    }

    public Peak getPeak() {
        return peak;
    }

    public int getDeltaRes() {
        Entity e1 = spg1.getFirstSet().atom.getEntity();
        Entity e2 = spg2.getFirstSet().atom.getEntity();
        int iRes1 = 0;
        int iRes2 = 0;
        // fixme what about multiple polymers or other entities
        if (e1 instanceof Residue) {
            iRes1 = ((Residue) e1).iRes;
        }
        if (e2 instanceof Residue) {
            iRes2 = ((Residue) e2).iRes;
        }
        return Math.abs(iRes1 - iRes2);
    }
}
