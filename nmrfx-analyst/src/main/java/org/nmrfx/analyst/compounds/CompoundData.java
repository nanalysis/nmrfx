/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.compounds;

import org.apache.commons.math3.stat.StatUtils;
import org.nmrfx.processor.math.Vec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author brucejohnson
 */
//        set cData [java::new com.onemoonsci.datachord.compoundLib.CompoundData $data(ref) $data(sw) $data(sf) $data(n)]
public class CompoundData {

    private static final Map<String, CompoundData> cmpdMap = new HashMap<String, CompoundData>();
    private final String id;
    private final double ref;
    private final double sf;
    private final double sw;
    private final int n;
    private final String name;
    private final double refConc;
    private final double cmpdConc;
    private final double refNProtons;
    private double regionNorm = 0.0;
    List<Region> regions = new ArrayList<>();

    public CompoundData(String cmpdID, String name, double ref, double sf, double sw, int n, double refConc, double cmpdConc, double refNProtons) {
        this.id = cmpdID;
        this.name = name;
        this.ref = ref;
        this.sf = sf;
        this.sw = sw;
        this.n = n;
        this.refConc = refConc;
        this.cmpdConc = cmpdConc;
        this.refNProtons = refNProtons;
    }

    public static void put(CompoundData cData, String id) {
        cmpdMap.put(id, cData);
    }

    public static CompoundData get(String id) {
        return cmpdMap.get(id);
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("id ");
        sBuilder.append(getId());
        sBuilder.append(" name ");
        sBuilder.append(getName());
        sBuilder.append(" ref ");
        sBuilder.append(getRef());
        sBuilder.append(" sf ");
        sBuilder.append(sf);
        sBuilder.append(" sw ");
        sBuilder.append(sw);
        sBuilder.append(" n ");
        sBuilder.append(getN());

        return sBuilder.toString();
    }

    public double search(String vecName, double[] vData, double[] ppms, double[] tols) {
        Vec vec = (Vec) Vec.get(vecName);
        return search(vec, vData, ppms, tols);
    }

    public double search(Vec vec, double[] vData, double[] ppms, double[] tols) {
        int nMatch = 0;
        int[] used = new int[ppms.length];
        for (int i = 0; i < ppms.length; i++) {
            used[i] = -1;
            double ppm = ppms[i];
            double tol = tols[i];
            double min = Double.MAX_VALUE;
            int iRegion = 0;
            int minRegion = -1;
            for (Region region : regions) {
                double delta = region.scorePPM(ppm, tol);
                if (delta < tol) {
                    if (delta < min) {
                        min = delta;
                        minRegion = iRegion;
                    }
                }
                iRegion++;
            }
            if (minRegion >= 0) {
                nMatch++;
                used[i] = minRegion;
            }
        }
        double corrResult = -nMatch;
        if (nMatch == ppms.length) {
            int jPPM = 0;
            double corrProd = 1.0;
            for (int iRegion : used) {
                if (iRegion >= 0) {
                    Region region = getRegion(iRegion);
                    double ppm1 = ppms[jPPM] + tols[jPPM] / 2;
                    double ppm2 = ppms[jPPM] - tols[jPPM] / 2;
                    int pt1 = vec.refToPt(ppm1);
                    int pt2 = vec.refToPt(ppm2);
                    double corr = region.scoreCorr(vData, pt1, pt2, 50);
                    if (corr < 0.0) {
                        corrProd = -1.0;
                        break;
                    }
                    corrProd *= corr;
                }
                jPPM++;
            }
            corrResult = corrProd;
// fixme multiple negative correlated regions could give positive corrProd
            if ((corrResult < 0.0) || (corrResult > 1.1)) {
                corrResult = -nMatch;
            }
        }
        return corrResult;
    }

    public int getRegionCount() {
        return regions.size();
    }

    void updateRegionNorm() {
        double minSum = Double.MAX_VALUE;
        for (Region region : regions) {
            if (region.getSum() < minSum) {
                minSum = region.getSum();
            }
        }
        regionNorm = minSum;
    }

    double getRegionNorm() {
        if (regionNorm == 0.0) {
            updateRegionNorm();
        }
        return regionNorm;
    }

    public double getRegionNorm(int i) {
        Region region = getRegion(i);
        return region.getSum() / getRegionNorm();
    }

    public void addRegion(double[] intensities, int first, int last, double startPPM, double endPPM) {
        Region region = new Region(this, intensities, first, last, startPPM, endPPM);
        regions.add(region);
        regionNorm = 0.0;
    }

    public void addRegion(double[] intensities, int first, int last, double startPPM, double endPPM, double intMax, double max) {
        Region region = new Region(this, intensities, first, last, startPPM, endPPM, intMax, max);
        regions.add(region);
        regionNorm = 0.0;
    }

    public void addRegion(Object[] objArray, int first, int last, double startPPM, double endPPM, double intMax, double max) {
        double[] intensities = new double[objArray.length];
        for (int i = 0; i < objArray.length; i++) {
            intensities[i] = ((Number) objArray[i]).doubleValue();
        }
        double maxI = StatUtils.max(intensities);
        Region region = new Region(this, intensities, first, last, startPPM, endPPM, intMax, max);
        regions.add(region);
        regionNorm = 0.0;
    }

    public Region getRegion(int i) {
        return regions.get(i);
    }

    public int getRefPoint() {
        Region region = regions.get(regions.size() - 1);
        return region.getStart() + region.getMaxPt();
    }

    public void addToArray(Vec vec, int iRegion, double shift) {
        addToArray(vec, iRegion, shift, 1.0);
    }

    public void addToArray(Vec vec, int iRegion, double shift, double scale) {
        Region region = regions.get(iRegion);
        int iShift = (int) Math.floor(shift);
        double frac = shift - iShift;
        if (frac > 0.0) {
            frac = 1.0 - frac;
            iShift++;
        }
        double[] iData = region.getInterpolated(frac);
        int end = region.getEnd();
        for (int i = region.getStart(), j = 0; i <= end; i++) {
            int k = i + iShift;
            vec.add(k, iData[j++] * scale);
        }
    }

    public double[] addToMappedArray(double[] array, int[] rmap, int iRegion, double shift, double scale) {
        Region region = regions.get(iRegion);
        int iShift = (int) Math.floor(shift);
        double frac = shift - iShift;
        if (frac > 0.0) {
            frac = 1.0 - frac;
            iShift++;
        }
        double[] iData = region.getInterpolated(frac);
        int start = region.getStart();
        int end = region.getEnd();

        for (int i = start, j = 0; i <= end; i++) {
            int k = i + iShift;
            if (k < rmap.length) {
                int mapPoint = rmap[k];
                if (mapPoint >= 0) {
                    array[mapPoint] += iData[j] * scale;
                }
            }
            j++;
        }
        return array;
    }

    Vec getVec(String name) {
        Vec vec = (Vec) Vec.get(name);
        if (vec == null) {
            vec = Vec.createNamedVector(n, name, false);
        } else {
            vec.resize(n, false);
        }
        vec.setRefValue(ref, 0.0);
        vec.dwellTime = 1.0 / sw;
        vec.centerFreq = sf;
        vec.setFreqDomain(true);
        return vec;

    }

    public void addToVec(Vec vec, double scale) {

        for (int i = 0; i < regions.size(); i++) {
            addToArray(vec, i, 0, scale);
        }

    }

    public void addToVec(Vec vec, double[] shifts, double scale) {
        vec.resize(n, false);
        for (int i = 0; i < shifts.length; i++) {
            addToArray(vec, i, shifts[i], scale);
        }
    }

    public void addToVec(Vec vec, int iRegion, double shift, double scale) {
        vec.resize(n, false);
        addToArray(vec, iRegion, shift, scale);
    }

    public void addToVec(Vec vec, int iRegion, double shift) {
        addToArray(vec, iRegion, shift, 1.0);
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @return the ref
     */
    public double getRef() {
        return ref;
    }

    /**
     * @return the sf
     */
    public double getSF() {
        return sf;
    }

    /**
     * @return the sw
     */
    public double getSW() {
        return sw;
    }

    /**
     * @return the n
     */
    public int getN() {
        return n;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the refConc
     */
    public double getRefConc() {
        return refConc;
    }

    /**
     * @return the cmpdConc
     */
    public double getCmpdConc() {
        return cmpdConc;
    }

    /**
     * @return the refNProtons
     */
    public double getRefNProtons() {
        return refNProtons;
    }
}
