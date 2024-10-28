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

    static double ppmScale = 20.0;
    static double jScale = 250.0;
    final String name;
    final String id;

    AtomBlock[] blocks;
    double[][][] matrices = null;

    public SimData(String name, String id, int nBlocks) {
        this.name = name;
        this.id = id;
        blocks = new AtomBlock[nBlocks];
        for (int i = 0; i < nBlocks; i++) {
            blocks[i] = new AtomBlock();
        }
    }

    public static class AtomBlock {
        List<int[]> ppmList = new ArrayList<>();
        List<Short> sppms = new ArrayList<>();
        List<Short> jValues = new ArrayList<>();
        List<Short> jPairs = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();

        public int nPPMs() {
            return ppmList.size();
        }

        public double ppm(int i) {
            int[] j = ppmList.get(i);
            return sppms.get(j[0]) * ppmScale / Short.MAX_VALUE;
        }

        public void ppm(int i, double value) {
            int[] iPPMs = ppmList.get(i);
            double f = value / ppmScale;
            short sppm = (short) Math.round(f * Short.MAX_VALUE);
            for (int iPPM : iPPMs) {
                sppms.set(iPPM, sppm);
            }
        }
        public Double j(int i, int j) {
            for (int k=0;k<jValues.size();k++) {
                int iTest = jPairs.get(k * 2);
                int jTest = jPairs.get(k*2 +1);
                if (((i == iTest) && (j == jTest)) || ((j == iTest) && (i == jTest))){
                    return jValues.get(k) * jScale / Short.MAX_VALUE;
                }
            }
            return 0.0;
        }

        public void j(int i, int j, double value) {
            double f = value / jScale;
            short jValue = (short) Math.round(f * Short.MAX_VALUE);
            for (int k=0;k<jValues.size();k++) {
                int iTest = jPairs.get(k * 2);
                int jTest = jPairs.get(k*2 +1);
                if (((i == iTest) && (j == jTest)) || ((j == iTest) && (i == jTest))){
                    jValues.set(k, jValue);
                }
            }
        }

        public int id(int i) {
            return ids.get(i);
        }
    }

    public SimData copy() {
        int nBlocks = nBlocks();
        SimData simData = new SimData(name, id, nBlocks);
        for (int i = 0; i < nBlocks; i++) {
            AtomBlock newBlock = simData.blocks[i];
            AtomBlock oldBlock = blocks[i];
            newBlock.ppmList.addAll(oldBlock.ppmList);
            newBlock.sppms.addAll(oldBlock.sppms);
            newBlock.jValues.addAll(oldBlock.jValues);
            newBlock.jPairs.addAll(oldBlock.jPairs);
            newBlock.ids.addAll(oldBlock.ids);
        }
        return simData;
    }

    public AtomBlock atomBlock(int i) {
        return blocks[i];
    }

    public static boolean loaded() {
        return !simDataMap.isEmpty();
    }

    public int nBlocks() {
        return blocks.length;
    }

    public void setMatrices(double[][][] matrices) {
        this.matrices = matrices;
    }

/*
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
*/

    public void setIDs(int iBlock, List<Integer> names) {
        AtomBlock block = blocks[iBlock];
        block.ids.addAll(names);
    }

    public List<Integer> getIDs(int iBlock) {
        return blocks[iBlock].ids;
    }

    public void setPPMs(int iBlock, List<Double> values) {
        AtomBlock block = blocks[iBlock];
        double tol = 0.004;
        block.sppms.clear();
        block.ppmList.clear();
        for (double value : values) {
            double f = value / ppmScale;
            short sppm = (short) Math.round(f * Short.MAX_VALUE);
            block.sppms.add(sppm);
        }

        int nPPMS = values.size();
        boolean[] used = new boolean[nPPMS];
        for (int i = 0; i < nPPMS; i++) {
            if (used[i]) {
                continue;
            }
            double v1 = values.get(i);
            List<Integer> matches = new ArrayList<>();
            matches.add(i);
            used[i] = true;
            for (int j = i + 1; j < nPPMS; j++) {
                double v2 = values.get(j);
                double delta = Math.abs(v2 - v1);
                if (delta < tol) {
                    matches.add(j);
                    used[j] = true;
                }
            }
            int[] matchArray = new int[matches.size()];
            for (int j = 0; j < matchArray.length; j++) {
                matchArray[j] = matches.get(j);
            }
            block.ppmList.add(matchArray);
        }
    }

    public double[] getPPMs(int iBlock) {
        AtomBlock block = blocks[iBlock];
        double[] values = new double[block.sppms.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = block.sppms.get(i) * ppmScale / Short.MAX_VALUE;
        }
        return values;
    }

    public void setJValues(int iBlock, List<Double> values) {
        AtomBlock block = blocks[iBlock];
        for (Double value : values) {
            double f = value / jScale;
            block.jValues.add((short) Math.round((f * Short.MAX_VALUE)));
        }
    }

    public double[] getJValues(int iBlock) {
        AtomBlock block = blocks[iBlock];
        double[] values = new double[block.jValues.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = block.jValues.get(i) * jScale / Short.MAX_VALUE;
        }
        return values;
    }

    public void setJPairs(int iBlock, List<Integer> values) {
        AtomBlock block = blocks[iBlock];
        for (Integer value : values) {
            block.jPairs.add((short) (value - 1));
        }
    }

    public int[] getJPairs(int iBlock) {
        AtomBlock block = blocks[iBlock];
        int[] values = new int[block.jPairs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = block.jPairs.get(i);
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
        if (!pattern.isEmpty()) {
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

    public static CompoundData genCompoundData(String cmpdID, String name, SimData simData, SimDataVecPars pars, double lb,
                                               double refConc, double cmpdConc) {
        Vec vec = prepareVec(name, pars);
        List<Region> regions = genVec(simData, vec, lb);
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

    public static List<Region> genVec(SimData data, Vec vec, double lb) throws IllegalArgumentException {
        vec.zeros();
        int nBlocks = data.blocks.length;
        List<double[]> regions = new ArrayList<>();
        for (int i = 0; i < nBlocks; i++) {
            double[] shifts = data.getPPMs(i);
            double[] couplings = data.getJValues(i);
            int[] pairs = data.getJPairs(i);
            for (int j = 0; j < shifts.length; j++) {
                double min = shifts[j];
                double max = shifts[j];
                for (int k = 0; k < couplings.length; k++) {
                    if ((pairs[k * 2] == j) || (pairs[k * 2 + 1] == j)) {
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
            } else {
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
                    fRegion.min = Math.min(min, fRegion.min);
                    fRegion.max = Math.max(max, fRegion.max);
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

    public static void load(InputStream istream) {

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
