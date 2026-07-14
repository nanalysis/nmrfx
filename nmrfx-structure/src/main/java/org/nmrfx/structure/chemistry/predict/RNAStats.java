package org.nmrfx.structure.chemistry.predict;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Bruce Johnson
 */
public class RNAStats {

    static Map<String, RNAStats> statMap = new HashMap<>();
    static Map<String, String> attrMap = new HashMap<>();
    static Map<String, String> indexMap = new HashMap<>();
    static int NNUC = 5;
    static int NSTAT = 5;
    final int nValues;
    final float mean;
    final float sdev;
    final float min;
    final float max;
    Float predValue = null;

    public RNAStats(int nValues, float mean, float sdev, float min, float max) {
        this.nValues = nValues;
        this.mean = mean;
        this.sdev = sdev;
        this.min = min;
        this.max = max;
    }

    static String genKey(String[] fields) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(fields[0]);
        int nAttr = fields.length - 1 - NNUC - NSTAT;
        for (int i = 0; i < NNUC + nAttr; i++) {
            sBuilder.append(" ");
            sBuilder.append(fields[i + 1]);
        }
        return sBuilder.toString();
    }

    static RNAStats parseStats(String[] fields) {
        int nFields = fields.length;

        int nValues = Integer.parseInt(fields[nFields - 5]);
        float mean = Float.parseFloat(fields[nFields - 4]);
        float sdev = Float.parseFloat(fields[nFields - 3]);
        float min = Float.parseFloat(fields[nFields - 2]);
        float max = Float.parseFloat(fields[nFields - 1]);
        return new RNAStats(nValues, mean, sdev, min, max);
    }

    public static RNAStats get(String key, boolean convert) {
        String[] fields = key.split(" ");
        if (fields.length < 10) {
            fields = key.split("_");
        }
        StringBuilder sBuilder = new StringBuilder();
        int iField = 0;
        for (String field : fields) {
            if (iField > 0) {
                sBuilder.append(' ');
            }
            if ((iField > 5) && convert) {
                field = attrMap.get(field);
                if (field == null) {
                    field = "-";
                }
            }
            sBuilder.append(field);
            iField++;
        }
        String newKey = sBuilder.toString();
        return statMap.get(newKey);
    }

    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(String.format("N %2d ", nValues));
        sBuilder.append(String.format("Mean %.2f ", mean));
        sBuilder.append(String.format("+/- %.2f ", sdev));
        sBuilder.append(String.format("Range: %.2f ", min));
        sBuilder.append(String.format("-%.2f", max));

        return sBuilder.toString();
    }

    public void setPredValue(double value) {
        predValue = (float) value;
    }

    public Float getPredValue() {
        return predValue;
    }

    public double getMean() {
        return mean;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getSDev() {
        return sdev;
    }

    public int getN() {
        return nValues;
    }

    public static boolean loaded() {
        return !statMap.isEmpty();
    }

    public static void readFile(String resourceName) throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream(resourceName);
        if (istream == null) {
            throw new IllegalArgumentException("Cannot find '" + resourceName + "' on classpath");
        } else {
            InputStreamReader reader = new InputStreamReader(istream);
            BufferedReader breader = new BufferedReader(reader);
            while (true) {
                String line = breader.readLine();
                if (line == null) {
                    break;
                } else {
                    if (!line.equals("")) {
                        String[] fields = line.split("\t");
                        if (fields.length == 2) {
                            attrMap.put(fields[0], fields[1]);
                            indexMap.put(fields[1], fields[0]);
                        } else {
                            String key = genKey(fields);
                            RNAStats stats = parseStats(fields);
                            statMap.put(key, stats);
                        }
                    }
                }
            }
            breader.close();
        }
    }
}
