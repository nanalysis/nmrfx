package org.nmrfx.analyst.dataops;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.nmrfx.math.VecBase.IndexValue;
import org.nmrfx.analyst.compounds.CompoundData;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author brucejohnson
 */
public class SimData {

    static Map<String, SimData> simDataMap = new TreeMap<>();

    double ppmScale = 20.0;
    double jScale = 250.0;
    final String name;
    final String id;
    final short[][] ppms;
    final short[][] jValues;
    final short[][] jPairs;

    SimData(String name, String id, int nBlocks) {
        this.name = name;
        this.id = id;
        ppms = new short[nBlocks][];
        jValues = new short[nBlocks][];
        jPairs = new short[nBlocks][];
    }


    public static boolean loaded() {
        return !simDataMap.isEmpty();
    }

    void setPPMs(int iBlock, List<Double> values) {
        ppms[iBlock] = new short[values.size()];
        for (int i = 0; i < ppms[iBlock].length; i++) {
            double f = values.get(i) / ppmScale;
            ppms[iBlock][i] = (short) Math.round(f * Short.MAX_VALUE);
        }
    }

    double[] getPPMs(int iBlock) {
        double[] values = new double[ppms[iBlock].length];
        for (int i = 0; i < values.length; i++) {
            values[i] = ppms[iBlock][i] * ppmScale / Short.MAX_VALUE;
        }
        return values;
    }

    void setJValues(int iBlock, List<Double> values) {
        jValues[iBlock] = new short[values.size()];
        for (int i = 0; i < jValues[iBlock].length; i++) {
            double f = values.get(i) / jScale;
            jValues[iBlock][i] = (short) Math.round((f * Short.MAX_VALUE));
        }
    }

    double[] getJValues(int iBlock) {
        double[] values = new double[jValues[iBlock].length];
        for (int i = 0; i < values.length; i++) {
            values[i] = jValues[iBlock][i] * jScale / Short.MAX_VALUE;
        }
        return values;
    }

    void setJPairs(int iBlock, List<Integer> values) {
        jPairs[iBlock] = new short[values.size()];
        for (int i = 0; i < jPairs[iBlock].length; i++) {
            jPairs[iBlock][i] = values.get(i).shortValue();
        }
    }

    int[] getJPairs(int iBlock) {
        int[] values = new int[jPairs[iBlock].length];
        for (int i = 0; i < values.length; i++) {
            values[i] = jPairs[iBlock][i];
        }
        return values;
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
        double ref = pars.getSw() / pars.getSf() / 2 + pars.getRef();
        vec.setRef(ref);
        vec.setName(name);
        return vec;
    }

    public static Dataset genDataset(String name, SimDataVecPars pars, double lb) {
        Vec vec = prepareVec(name, pars);
        genVec(name, vec, lb);
        Dataset dataset = new Dataset(vec);
        dataset.setLabel(0, pars.getLabel());
        return dataset;
    }
//    public CompoundData(String cmpdID, String name, double ref, double sf, double sw, int n, double refConc, double cmpdConc, double refNProtons) {

    public static CompoundData genCompoundData(String cmpdID, String name, SimDataVecPars pars, double lb,
            double refConc, double cmpdConc, double frac) {
        Vec vec = prepareVec(name, pars);
        int nProtons = genVec(name, vec, lb);
        double refNProtons = 9.0;
        CompoundData cData = new CompoundData(cmpdID, name, pars.getVref(), pars.getSf(), pars.getSw(), pars.getN(), refConc, cmpdConc, nProtons, refNProtons);
        genRegions(cData, vec, frac);
        return cData;
    }

    public static int genVec(String name, Vec vec, double lb) throws IllegalArgumentException {
        SimData data = simDataMap.get(name);
        if (data == null) {
            throw new IllegalArgumentException("Can't find data for \"" + name + "\"");
        }
        vec.zeros();
        int nBlocks = data.ppms.length;
        int nProtons = 0;
        for (int i = 0; i < nBlocks; i++) {
            double[] shifts = data.getPPMs(i);
            nProtons += shifts.length;
            double[] couplings = data.getJValues(i);
            int[] pairs = data.getJPairs(i);
            SimShifts simShifts = new SimShifts(shifts, couplings, pairs, vec.getSF());
            simShifts.diag();
            simShifts.makeSpec(vec);
        }
        vec.hft();
        vec.ift();
        vec.decay(lb, 0.0, 1.0);
        vec.fft();
        vec.phase(45.0, 0.0);
        return nProtons;
    }

    public static void genRegions(CompoundData cData, Vec vec, double frac) {
        IndexValue indexValue = vec.maxIndex();
        double maxIntensity = indexValue.getValue();
        double threshold = maxIntensity * frac;
        int n = vec.getSize();
        boolean inSignal = false;
        int start = 0;
        int end = 0;
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double value = vec.getReal(i);
            if (value > threshold) {
                if (!inSignal) {
                    inSignal = true;
                    start = i;
                }
                values.add(value);
            } else {
                if (inSignal) {
                    inSignal = false;
                    end = i - 1;
                    double[] intensities = values.stream().mapToDouble(d -> d).toArray();
                    values.clear();
                    double startPPM = vec.pointToPPM(start);
                    double endPPM = vec.pointToPPM(end);
                    cData.addRegion(intensities, start, end, startPPM, endPPM);
                }
            }
        }
    }

    public static void load() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream("data/bmse.yaml");

        Yaml yaml = new Yaml();
        for (Object data : yaml.loadAll(istream)) {
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
                simData.setPPMs(iBlock, ppms);
                simData.setJValues(iBlock, jValues);
                simData.setJPairs(iBlock, jPairs);
                iBlock++;
            }
            simDataMap.put(name.toLowerCase().replace(' ', '-'), simData);
        }
    }

}
