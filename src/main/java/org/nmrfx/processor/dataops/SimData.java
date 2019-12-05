package org.nmrfx.processor.dataops;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author brucejohnson
 */
public class SimData {

    static Map<String, SimData> simDataMap = new HashMap<>();

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

    public static List<String> getNames() {
        List<String> names = new ArrayList<>();
        for (String name : simDataMap.keySet()) {
            names.add(name);
        }
        return names;
    }

    public static String genDataset(String name, int n, double sf, double sw, double centerPPM) {
        Vec vec = new Vec(n);
        vec.setSF(sf);
        vec.setSW(sw);
        double ref = sw / sf / 2 + centerPPM;
        vec.setRef(ref);
        genVec(name, vec);
        vec.setName(name);
        Dataset dataset = new Dataset(vec);
        return dataset.getName();
    }

    public static void genVec(String name, Vec vec) throws IllegalArgumentException {
        SimData data = simDataMap.get(name);
        if (data == null) {
            throw new IllegalArgumentException("Can't find data for \"" + name + "\"");
        }
        vec.zeros();
        int nBlocks = data.ppms.length;
        for (int i = 0; i < nBlocks; i++) {
            double[] shifts = data.getPPMs(i);
            double[] couplings = data.getJValues(i);
            int[] pairs = data.getJPairs(i);
            SimShifts simShifts = new SimShifts(shifts, couplings, pairs, vec.getSF());
            simShifts.diag();
            simShifts.makeSpec(vec);
        }
        vec.hft();
        vec.ift();
        vec.decay(1.0, 0.0, 1.0);
        vec.fft();
        vec.phase(45.0, 0.0);

    }

    public static void load() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream("data/bmse.yaml");

        Yaml yaml = new Yaml();
        for (Object data : yaml.loadAll(istream)) {
            Map<String, Object> dataMap = (HashMap<String, Object>) data;
            String name = ((String) dataMap.get("name")).trim();
            String id = (String) dataMap.get("id");
            System.out.println("name " + name + " id " + id);
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
            simDataMap.put(name, simData);
        }
    }

}
