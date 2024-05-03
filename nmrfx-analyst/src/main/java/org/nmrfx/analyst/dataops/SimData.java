package org.nmrfx.analyst.dataops;

import org.nmrfx.analyst.compounds.CompoundData;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * @author brucejohnson
 */
public class SimData {

    private static final Map<String, SimData> simDataMap = new TreeMap<>();

    double ppmScale = 20.0;
    double jScale = 250.0;
    final String name;
    final String id;
    final short[][] ppms;
    final short[][] jValues;
    final short[][] jPairs;
    final int[][] ids;

    final int[][][] equiv;

    double[][][] matrices = null;

    public SimData(String name, String id, int nBlocks) {
        this.name = name;
        this.id = id;
        ppms = new short[nBlocks][];
        jValues = new short[nBlocks][];
        jPairs = new short[nBlocks][];
        ids = new int[nBlocks][];
        equiv = new int[nBlocks][][];
    }

    public static boolean loaded() {
        return !simDataMap.isEmpty();
    }

    public int nBlocks() {
        return ppms.length;
    }

    public void setMatrices(double[][][] matrices) {
        this.matrices = matrices;
    }

    public double[][][] getMatrices() {
        int nBlocks = ppms.length;
        double[][][] matrix = new double[nBlocks][][];
        for (int iBlock = 0;iBlock<nBlocks;iBlock++) {
            double[] ppms2 = getPPMs(iBlock);
            double[] j2 = getJValues(iBlock);
            int[] jPairs2 = getJPairs(iBlock);
            int n = ppms2.length;
            matrix[iBlock] = new double[n][n];
            double[][] subMatrix = matrix[iBlock];
            for (int i = 0; i < n; i++) {
                subMatrix[i][i] = ppms2[i];
            }
            for (int i = 0; i < jPairs2.length; i += 2) {
                int r = jPairs2[i] - 1;
                int c = jPairs2[i + 1] - 1;
                double coupling = j2[i / 2];
                subMatrix[r][c] = coupling;
                subMatrix[c][r] = coupling;
            }
        }
        return matrix;
    }

    public void setIDs(int iBlock, List<Integer> names) {
        ids[iBlock] = new int[names.size()];
        for (int i = 0; i < ids[iBlock].length; i++) {
            ids[iBlock][i] = names.get(i);
        }
    }

    public int[] getIDs(int iBlock) {
        return ids[iBlock];
    }

    public int[][] getEquiv(int iBlock) {
        return equiv[iBlock];
    }
    public void setPPMs(int iBlock, List<Double> values) {
        ppms[iBlock] = new short[values.size()];
        double tol = 0.002;
        equiv[iBlock] = new int[ppms[iBlock].length][0];

        for (int i=0;i<ppms[iBlock].length - 2;i++) {
                double d1 = Math.abs(values.get(i) - values.get(i+1));
                double d2 = Math.abs(values.get(i) - values.get(i+2));
                if ((d1 < tol) && (d2 < tol)) {
                    equiv[iBlock][i] = new int[]{i+1,i+2};
                    equiv[iBlock][i+1] = new int[]{i};
                    equiv[iBlock][i+2] = new int[]{i};
                }
        }
        for (int i = 0; i < ppms[iBlock].length; i++) {
            double f = values.get(i) / ppmScale;
            ppms[iBlock][i] = (short) Math.round(f * Short.MAX_VALUE);
        }
    }

    public double[] getPPMs(int iBlock) {
        double[] values = new double[ppms[iBlock].length];
        for (int i = 0; i < values.length; i++) {
            values[i] = ppms[iBlock][i] * ppmScale / Short.MAX_VALUE;
        }
        return values;
    }

    public void setJValues(int iBlock, List<Double> values) {
        jValues[iBlock] = new short[values.size()];
        for (int i = 0; i < jValues[iBlock].length; i++) {
            double f = values.get(i) / jScale;
            jValues[iBlock][i] = (short) Math.round((f * Short.MAX_VALUE));
        }
    }

    public double[] getJValues(int iBlock) {
        double[] values = new double[jValues[iBlock].length];
        for (int i = 0; i < values.length; i++) {
            values[i] = jValues[iBlock][i] * jScale / Short.MAX_VALUE;
        }
        return values;
    }

    public void setJPairs(int iBlock, List<Integer> values) {
        jPairs[iBlock] = new short[values.size()];
        for (int i = 0; i < jPairs[iBlock].length; i++) {
            jPairs[iBlock][i] = values.get(i).shortValue();
        }
    }

    public int[] getJPairs(int iBlock) {
        int[] values = new int[jPairs[iBlock].length];
        for (int i = 0; i < values.length; i++) {
            values[i] = jPairs[iBlock][i];
        }
        return values;
    }

    public String getName() {
        return name;
    }

    public static boolean contains(String name) {
        return simDataMap.containsKey(name.toLowerCase());
    }

    public static List<String> getNames() {
        List<String> names = new ArrayList<>();
        for (String name : simDataMap.keySet()) {
            names.add(name);
        }
        return names;
    }

    public static List<String> getNames(String pattern) {
        pattern = pattern.trim();
        List<String> names = new ArrayList<>();
        boolean startsWith = false;
        if (pattern.length() > 0) {
            if (Character.isUpperCase(pattern.charAt(0))
                    || Character.isDigit(pattern.charAt(0))) {
                startsWith = true;
            }
            pattern = pattern.toLowerCase();
            for (String name : simDataMap.keySet()) {
                boolean match = startsWith ? name.startsWith(pattern) : name.contains(pattern);
                if (match) {
                    names.add(name);
                }
            }

        }
        return names;
    }

    public static Vec prepareVec(String name, SimDataVecPars pars) {
        Vec vec = new Vec(pars.getN());
        vec.setSF(pars.getSf());
        vec.setSW(pars.getSw());
        vec.setRefValue(pars.getRef());
        vec.setName(name);
        return vec;
    }

    public static Dataset genDataset(String name, SimDataVecPars pars, double lb) {
        SimData data = simDataMap.get(name);
        if (data == null) {
            throw new IllegalArgumentException("Can't find data for \"" + name + "\"");
        }
        return genDataset(data, name, pars, lb);
    }

    public static Dataset genDataset(SimData data, String name, SimDataVecPars pars, double lb) {
        Vec vec = prepareVec(name, pars);
        genVec(data, vec, lb);
        Dataset dataset = new Dataset(vec);
        dataset.setLabel(0, pars.getLabel());
        return dataset;
    }
//    public CompoundData(String cmpdID, String name, double ref, double sf, double sw, int n, double refConc, double cmpdConc, double refNProtons) {

    public static CompoundData genCompoundData(String cmpdID, String name, SimDataVecPars pars, double lb,
                                               double refConc, double cmpdConc) {
        Vec vec = prepareVec(name, pars);
        List<Region> regions = genVec(name, vec, lb);
        CompoundData cData = genRegions(cmpdID, name, pars, refConc, cmpdConc, vec, regions);
        cData.setVec(vec);
        return cData;
    }

    static class Region {

        double min;
        double max;
        int nProtons;

        public Region(double min, double max, int nProtons) {
            this.min = min;
            this.max = max;
            this.nProtons = nProtons;
        }

    }

    public static Optional<SimData> getSimData(String name) {
        SimData data = simDataMap.get(name);
        return Optional.ofNullable(data);
    }

    public static List<Region> genVec(String name, Vec vec, double lb) throws IllegalArgumentException {
        SimData data = simDataMap.get(name);
        if (data == null) {
            throw new IllegalArgumentException("Can't find data for \"" + name + "\"");
        }
        return genVec(data, vec, lb);
    }

    public static List<Region> genVec(SimData data, Vec vec, double lb) throws IllegalArgumentException {
        vec.zeros();
        int nBlocks = data.ppms.length;
        List<double[]> regions = new ArrayList<>();
        for (int i = 0; i < nBlocks; i++) {
            double[] shifts = data.getPPMs(i);
            double[] couplings = data.getJValues(i);
            int[] pairs = data.getJPairs(i);
            for (int j = 0; j < shifts.length; j++) {
                double min = shifts[j];
                double max = shifts[j];
                for (int k = 0; k < couplings.length; k++) {
                    if ((pairs[k * 2] - 1 == j) || (pairs[k * 2 + 1] - 1 == j)) {
                        double delta = Math.abs(couplings[k]) / vec.getSF();
                        min = min - delta / 2;
                        max = max + delta / 2;
                    }
                }
                double[] region = {min - 3 * lb / vec.getSF(), max + 3 * lb / vec.getSF()};
                regions.add(region);
            }
            SimShifts simShifts;
            if (data.matrices != null) {
                simShifts = new SimShifts(data.matrices[i], vec.getSF());
            }  else {
                simShifts = new SimShifts(shifts, couplings, pairs, vec.getSF());
            }
            simShifts.diag();
            simShifts.makeSpec(vec);
        }
        regions.sort((a, b) -> Double.compare(a[0], b[0]));
        List<Region> filteredRegions = new ArrayList<>();

        for (int i = 0; i < regions.size(); i++) {
            double[] region = regions.get(i);
            double min = region[0];
            double max = region[1];
            boolean overlaps = false;
            for (Region fRegion : filteredRegions) {
                if ((max < fRegion.min) || (min > fRegion.max)) {
                    continue;
                } else {
                    overlaps = true;
                    fRegion.min = min < fRegion.min ? min : fRegion.min;
                    fRegion.max = max > fRegion.max ? max : fRegion.max;
                    fRegion.nProtons++;
                    break;
                }
            }
            if (!overlaps) {
                Region fRegion = new Region(min, max, 1);
                filteredRegions.add(fRegion);
            }
        }
        filteredRegions.sort((a, b) -> Double.compare(b.min, a.min));
        vec.hft();
        vec.ift();
        vec.decay(lb, 0.0, 1.0);
        vec.fft();
        vec.phase(45.0, 0.0);
        vec.scale(250.0);
        return filteredRegions;
    }

    public static CompoundData genRegions(String cmpdID, String name, SimDataVecPars pars, double refConc, double cmpdConc, Vec vec, List<Region> regions) {
        double refNProtons = 9.0;
        CompoundData cData = new CompoundData(cmpdID, name, pars.getRef(), pars.getSf(), pars.getSw(), pars.getN(), refConc, cmpdConc, refNProtons);
        int n = vec.getSize();

        for (Region region : regions) {
            int pt1 = vec.refToPt(region.max);
            int pt2 = vec.refToPt(region.min);
            double[] intensities = new double[pt2 - pt1 + 1];
            int j = 0;
            for (int i = pt1; i <= pt2; i++) {
                intensities[j++] = vec.getReal(i);
            }
            cData.addRegion(intensities, pt1, pt2, region.max, region.min);
        }
        return cData;

    }

    public static void load() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream("data/bmse.yaml");

        Yaml yaml = new Yaml();
        for (Object data : yaml.loadAll(istream)) {
            try {
                Map<String, Object> dataMap = (HashMap<String, Object>) data;
                String name = ((String) dataMap.get("name")).trim();
                String id = (String) dataMap.get("id");
                List blocks = (List) dataMap.get("blocks");
                SimData simData = new SimData(name, id, blocks.size());
                int iBlock = 0;
                for (Object block : blocks) {
                    Map<String, Object> blockMap = (Map<String, Object>) block;
                    List<Double> ppms = (List<Double>) blockMap.get("shifts");
                    List<Double> jValues = (List<Double>) blockMap.get("jValues");
                    List<Integer> jPairs = (List<Integer>) blockMap.get("jPairs");
                    List<Integer> ids = (List<Integer>) blockMap.get("id");
                    simData.setPPMs(iBlock, ppms);
                    simData.setJValues(iBlock, jValues);
                    simData.setJPairs(iBlock, jPairs);
                    simData.setIDs(iBlock, ids);

                    iBlock++;
                }
                simDataMap.put(name.toLowerCase().replace(' ', '-'), simData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
