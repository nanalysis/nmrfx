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
package org.nmrfx.processor.processing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
//import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well19937c;

/**
 * A SampleSchedule specifies the sequence of increments or array elements used to acquire data in a non-uniformly
 * sampled experiment, and processed with a non-uniform method such as Iterative Soft Thresholding (IST). The schedule
 * contains elements for which data is acquired, and is specified internally by an <i>int</i> array. The same schedule
 * must be used to both acquire and process data.
 * <p>
 * The schedule is created using the Sinusoidal Poisson-Gap method by Hyberts et al., J. Am. Chem. Soc. 132, 2145
 * (2010).
 * <p>
 * IST_SCHEDULE : in python script <i>pyproc</i> - create a schedule from parameters, and write it to a file.
 * <p>
 * IST : in python script <i>pyproc</i> - read a schedule from a file, and perform IST processing.
 *
 * @see #v_samples
 * @see Ist
 * @since NMRViewJ 9.0
 * @author bfetler
 */
public class SampleSchedule {

    /**
     * Half of <i>PI</i> math constant.
     */
    private final static double HALF_PI = Math.PI / 2.0;

    /**
     * Maximum number of tries to create sample schedule.
     *
     * @see #createArray()
     */
    private final static int MAX_TRY = 500;

    /**
     * Number of sampled points (must be less than z_total).
     *
     * @see #z_total
     */
    private int p_sampled;

    /**
     * Total number of transformed points.
     *
     * @see #p_sampled
     */
    private int z_total;

    private int[] dimSizes;

    /**
     * Number of dimensions.
     */
    private int nDim;  // same as v_samples[0].length + 1

    /**
     * MultiVecCounter for NUS output data.
     */
    private MultiVecCounter outMult;

    /**
     * Optional flag used with fully acquired data set to simulate NUS.
     */
    private boolean demo = false;

    /**
     * Flag used to set schedule to be just zeros at end.
     */
    private boolean endOnly = false;

    /**
     * Seed for random number generator (optional).
     */
    private long seed = 0;  // pass seed in constructor?

    /**
     * Random number generator.
     *
     * @see <a
     * href='http://commons.apache.org/proper/commons-math/javadocs/api-3.2/org/apache/commons/math3/random/Well19937c.html'>Well19937c
     * in Apache Commons Math</a>
     */
    private Well19937c randomWell = null;

    /**
     * Array containing sample schedule elements.
     *
     * @see #createArray()
     */
    private int[][] v_samples;

    /**
     * HashMap of sample schedule elements.
     */
    private HashMap<Integer, Integer> sampleHash;

    /**
     * Array of sample schedule elements.
     */
    int[] sampleArray = null;

    /**
     * Full path file name.
     */
    String fpath = "/tmp/sample_schedule.txt";

    /**
     * Create a SampleSchedule from parameters, and write it to a file.
     * <p>
     * Used by SAMPLE_SCHEDULE(mode='create') in python script <i>pyproc</i>.
     *
     * @param sample number of sampled points (must be less than total)
     * @param total total number of points
     * @param path full path file name
     * @param demo set true if nmr dataset not NUS acquisition
     *
     * @see Ist
     * @see #p_sampled
     * @see #z_total
     * @see #fpath
     */
    public SampleSchedule(int sample, int total, String path, boolean demo) {
        if (sample > total) {
            sample = total / 2;
        }
        this.p_sampled = sample;
        this.z_total = total;
        this.dimSizes = new int[1];
        this.dimSizes[0] = total;
        this.nDim = 2;
        this.fpath = path;
        this.demo = demo;
        createArray();
        writeFile();
        display();
    }

    /**
     * Create a SampleSchedule by reading it from a file.
     * <p>
     * Used by SAMPLE_SCHEDULE(mode='read') in python script <i>pyproc</i>.
     *
     * @param path full path file name
     * @param demo set true if nmr dataset not NUS acquisition
     *
     * @see Ist
     * @see #fpath
     */
    public SampleSchedule(String path, boolean demo) {
        this.fpath = path;
        readFile();
        this.demo = demo;
    }

    /**
     */
    public SampleSchedule(int p, int z) {
        this.p_sampled = p;
        this.z_total = z;
        this.dimSizes = new int[1];
        this.dimSizes[0] = z;
        createArray();
    }

    /**
     */
    public SampleSchedule(int p, int z, boolean endOnly) {
        this.p_sampled = p;
        this.z_total = z;
        this.dimSizes = new int[1];
        this.dimSizes[0] = z;
        this.endOnly = endOnly;
        if (!endOnly) {
            createArray();
        } else {
            createEndOnlyArray();
        }
    }

    /**
     * Used only for internal testing in main(), call createArray() after.
     */
    private SampleSchedule(int p, int z, long seed) {
        this(p, z);
        this.seed = seed;
    }

    /**
     * Get the total number of elements in the sample schedule.
     *
     * @return total number of vectors to be read
     */
    public int getTotalSamples() {
        return p_sampled;
    }

    /**
     * Gets the array elements in the sample schedule.
     *
     * @return <i>int</i> array
     * @see #v_samples
     */
    public int[][] getSamples() {
        return v_samples;
    }

    /**
     * Set output dimensions for NUS processing.
     *
     * @param dims
     */
    public void setDims(int[] dims) {
        if (dims.length < nDim - 1) {
            System.out.println("too few dimensions in SAMPLE_SCHEDULE dims, ignore values");
        } else {
            // ignore values if too many, just use the ones available
            for (int i = 0; i < nDim - 1; i++) {
                dimSizes[i] = dims[i];
                // check if less than max v_samples[k][]?
            }
            calcSampleHash();
        }
    }

    /**
     * Get output dimensions for NUS processing.
     */
    public int[] getDims() {
        return dimSizes;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    public boolean isDemo() {
        return demo;
    }

    /**
     * Set output MultiVecCounter.
     *
     * @param complex
     * @param modes
     * @see MultiVecCounter
     */
    public void setOutMult(boolean[] complex, String[] modes) {
        if (outMult == null) {
            int[] zSizes = new int[nDim];
            for (int i = 1; i < zSizes.length; i++) {
                zSizes[i] = dimSizes[i - 1];
            }
            outMult = new MultiVecCounter(zSizes, complex, modes, nDim);
        }
    }

    /**
     * Modifies a VecIndex object that represents position in non-NUS dataset to be consistent with sample schedule for
     * this dataset. If the object represents a value that was not sampled it returns null.
     *
     * @param fullIndex VecIndex assuming the dataset doesn't have NUS sampling
     * @return vecIndex consistent with sampling schedule, or null if point not sampled.
     * @see MultiVecCounter
     * @see VecIndex
     */
    public VecIndex convertToNUSGroup(VecIndex fullIndex) {
        int groupSize = fullIndex.inVecs.length;
        int[] inVecs = fullIndex.inVecs;
        //System.out.print("next index ");
        boolean ok = true;
        for (int i = 0; i < groupSize; i++) {
            //System.out.print(inVecs[i]+" ");
            int j = inVecs[i];
            int phOff = j % groupSize;
            j /= groupSize;
            int index = sampleArray[j];
            if (index == -1) {
                ok = false;
                break;
            } else if (!demo) {
                inVecs[i] = groupSize * index + phOff;
            }
        }
        //System.out.print(inVecs[i]+" ");

        //System.out.println(groupSize);
        VecIndex nusIndex = null;
        if (ok) {
            nusIndex = fullIndex;
        }
        return nusIndex;
    }

    /**
     * Get next group of Vecs for processing. Input and output locations depend on demo mode. If demo, tmult contains
     * correct input and output, but location depends on sample schedule (v_samples). If not demo, two MultiVecCounters
     * are needed: tmult for input and outMult for output.
     *
     * @param vecGroup location in MultiVecCounter
     * @param tmult input MultiVecCounter
     * @return vecIndex contains input and output arrays
     * @see MultiVecCounter
     * @see VecIndex
     */
    public VecIndex getNextGroup(final int vecGroup, MultiVecCounter tmult) {
        int groupSize = tmult.getGroupSize();
        int[] inVecs = new int[groupSize];
        int k = vecGroup;
        VecIndex vecIndex = tmult.getNextGroup(vecGroup);
        for (int i = 0; i < groupSize; i++) {
            // inVecs[i] = vecIndex.inVecs[i];  // store inVecs, non-demo
            inVecs[i] = vecGroup * groupSize + i;
        }
        if (k < p_sampled) {
            int[] oSizes;
            if (demo) {
                oSizes = tmult.getOutSizes();
            } else {
//                oSizes = outMult.getOutSizes();
                oSizes = tmult.getOutSizes();
            }

            int sampGroup = v_samples[k][nDim - 2];  // read from sample schedule
            for (int i = nDim - 2; i > 0; i--) {  // works for nDim=2 or 3; nDim>3 untested
                sampGroup = oSizes[nDim - i - 1] * sampGroup + v_samples[k][i - 1];
            }
//            System.out.print(vecGroup + " " + sampGroup + " ");
//            for (int i = 0; i < v_samples[k].length; i++) {
//                System.out.print(v_samples[k][i] + " " + oSizes[i] + " ");
//            }
//            System.out.println("");
//            tmult.findOutGroup(v_samples[k]);
            if (demo) {
                vecIndex = tmult.getNextGroup(sampGroup);  // get outVecs for demo
            } else {
                vecIndex = outMult.getNextGroup(sampGroup);  // get outVecs
                for (int i = 0; i < groupSize; i++) {
                    // seems incorrect for nDim == 3
                    vecIndex.inVecs[i] = inVecs[i];         // copy inVecs
                }
            }
        }
        return vecIndex;
    }

    public synchronized boolean reloadFile(String path, int vecSize) {
        boolean recreate = false;
        if (!fpath.equals(path) || vecSize != z_total) {
            fpath = path;
            readFile();
            recreate = true;
        }
        return recreate;
    }

    public synchronized boolean recreateArray(int nSamples, int vecSize, boolean doEndOnly) {
        boolean recreate = false;
        if ((nSamples != p_sampled) || (vecSize != z_total) || (endOnly != doEndOnly)) {
            p_sampled = nSamples;
            z_total = vecSize;
            endOnly = doEndOnly;
            recreate = true;
            if (endOnly) {
                createEndOnlyArray();
            } else {
                createArray();
            }
        }
        return recreate;
    }

    private void createEndOnlyArray() {
        v_samples = new int[p_sampled][1];
        for (int i = 0; i < (p_sampled - 1); i++) {
            v_samples[i][0] = i;
        }
        v_samples[p_sampled - 1][0] = z_total - 1;
    }

    /**
     * Create internal <i>int</i> array using Hyberts et al. (2010) method.
     */
    private void createArray() {
        int i, j, k, n = 0;
        int count = 0;
        double ld = (double) z_total / (double) p_sampled;
        double adj = 2.0 * (ld - 1.0);
        double arg;

        v_samples = new int[p_sampled][1];
        if (seed > 0) {
            randomWell = new Well19937c(seed);
        } else {
            randomWell = new Well19937c();
        }
//        RandomDataGenerator rData = new RandomDataGenerator(randomWell);

        do {
            i = 0;
            n = 0;
//            System.out.print("iter "+count+":");
            while (i < z_total) {
                if (n < p_sampled) {
                    v_samples[n][0] = i;  // no need to assign if bigger than array
                }
                i++;
                arg = adj * Math.sin(HALF_PI * (double) (i + 0.5) / (double) (z_total + 1.0));
                k = poisson(arg);
//                k = (int) rData.nextPoisson(arg);  // alternate to poisson()
//                System.out.print(" "+k);
                // ex: adj init = 6, sin between 0 and 1
                i += k;
                n++;
            }
//            System.out.println(": n="+n+" adj="+adj);
            if (n > p_sampled) {
                adj *= 1.02;
            } // too many points
            else if (n < p_sampled) {
                adj /= 1.02;
            } // too few points
            count++;
        } while (n != p_sampled && count < MAX_TRY);

        if (count >= MAX_TRY) {  // avoid infinite loop
            p_sampled = n;
            System.err.println("sample schedule created with " + n + " samples, max tries reached");
        }
    }

    /**
     * Poisson distribution method from Knuth (1969), as used by Hyberts et al. (2010).
     * <p>
     * Example: <i>createArray()</i> increments over <i>v_samples</i>, giving values in the range: 0 &lt; <i>lambda</i>
     * &lt; 6, 1 &gt; <i>bigL</i> &gt; 0.002. Product of random doubles continues until <i>bigL</i> is reached,
     * returning number of steps minus one.
     *
     * @param lambda average gap length, calculated from p_sampled and z_total
     * @return gap size increment, zero or random positive integer
     * @see <a href='http://www.itl.nist.gov/div898/handbook/eda/section3/eda366j.htm'>Poisson Distribution from
     * NIST</a>
     */
    private int poisson(double lambda) {
        double bigL = Math.exp(-lambda);
        int k = 0;
        double u, product = 1.0;
        do {
            if (randomWell != null) {
                u = randomWell.nextDouble();
            } else {
                u = Math.random();
            }
            product *= u;  // product of u's, u average ~0.5
            k++;
        } while (product >= bigL);  // loop until product < bigL
        return (k - 1);
    }

    /**
     * Display SampleSchedule in <i>stdout</i>.
     */
    public void display() {
        System.out.print("sample schedule:");
        for (int j = 0; j < p_sampled; j++) {
            for (int k : v_samples[j]) {
                System.out.print(" " + k);
            }
            if (j < p_sampled - 1) {
                System.out.print(",");
            }
        }
        System.out.println();
        System.out.println("  " + p_sampled + " points out of " + z_total + " total");
    }

    /**
     * Write SampleSchedule to <i>fpath</i> file.
     *
     * @see #fpath
     */
    private void writeFile() {
        try {
            BufferedWriter bw = Files.newBufferedWriter(Paths.get(fpath), Charset.forName("US-ASCII"),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            System.out.println("writing new sample schedule: " + fpath);
//            bw.write("sizes "+z_total);
//            bw.newLine();
            for (int j = 0; j < p_sampled; j++) {
                bw.write(v_samples[j][0] + " ");
                bw.newLine();
            }
            bw.flush();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Read SampleSchedule from <i>fpath</i> file.
     *
     * @see #fpath
     */
    private void readFile() {
        try (BufferedReader br = Files.newBufferedReader(Paths.get(fpath), Charset.forName("US-ASCII"))) {
            if (br.ready()) {
                String[] sa;
                int k;
                z_total = 0;
                String line;
                ArrayList<int[]> sampList = new ArrayList();
                int sampLen = 0;
                int[] samp;
                int ct = 0;
                Pattern pat = Pattern.compile("[^0-9]+");
                while ((line = br.readLine()) != null) {  // read samples
                    if (line.startsWith("sizes")) {  // parse first line for sizes
                        sa = line.split("[\\s,]+");
                        sampLen = sa.length - 1;
                        z_total = 1;
                        for (int i = 1; i < sa.length; i++) {
                            k = -1;
                            try {
                                k = Integer.parseInt(sa[i]);
                            } catch (NumberFormatException e) {
                                throw new ProcessingException(e.getMessage());
                            }
                            if (k > -1) {
                                z_total *= k;
                            }
                        }
                        line = br.readLine();
                    }
                    samp = new int[sampLen];
                    String[] sc = line.trim().split("[\\s]+");
                    int j = 0;
                    for (String sd : sc) {
                        if (sampLen == 0) {
                            sampLen = sc.length;
                            samp = new int[sampLen];
                        }
                        // sd = sd.replaceAll("[^0-9]+", "");
                        sd = pat.matcher(sd).replaceAll("");
                        k = -1;
                        try {
                            k = Integer.parseInt(sd);
                        } catch (NumberFormatException e) {
                            throw new ProcessingException("syntax error in " + fpath);
                        }
                        if (k > -1) {
                            samp[j++] = k;
                        }
                    }
                    sampList.add(samp);
                    ct++;
                }
                p_sampled = ct;
                if (sampLen < 1) {
                    sampLen = 1;
                }
                // samp values are in order row, plane ...
                v_samples = new int[p_sampled][sampLen];
                sampList.toArray(v_samples);
                calcDims();
                calcSampleHash();
                calcSampleArray();
                System.out.println("sample schedule read " + p_sampled + " points from " + fpath);
            }
            br.close();
        } catch (IOException e) {
            throw new ProcessingException("error reading " + fpath);
        }
    }

    private void calcDims() {
        int sampLen = v_samples[0].length;
        this.nDim = sampLen + 1;
        if (dimSizes == null) {
            int i;
            dimSizes = new int[sampLen];
            for (int[] ia : v_samples) {
                for (i = 0; i < sampLen; i++) {
                    if ((ia[i] + 1) > dimSizes[i]) {
                        dimSizes[i] = (ia[i] + 1);
                    }
                }
            }
        }
    }

    public int calcKey(int i, int j) {    // only for 2D Matrix
        return (i + j * dimSizes[0]);
    }

    public int calcKey(int[] ar) {
        int r = 0;
        // 1D  ar[0]
        // 2D  ar[0] + dimSizes[0] * ar[1]
        // 3D  ar[0] + dimSizes[0] * (ar[1] + dimSizes[1] * ar[2])
        int j = ar.length - 1;
        r = ar[j];
        while (j > 0) {
            j--;
            r = r * dimSizes[j] + ar[j];
        }
        return r;
    }

    private void calcSampleArray() {
        int j = dimSizes.length - 1;
        int size = dimSizes[j];
        while (j > 0) {
            j--;
            size = size * dimSizes[j];
        }
        sampleArray = new int[size];
        for (int i = 0; i < size; i++) {
            sampleArray[i] = -1;
        }
        for (int i = 0; i < p_sampled; i++) {
            int key = calcKey(v_samples[i]);
            sampleArray[key] = i;
        }
    }

    public int getIndex(int[] point) {
        int key = calcKey(point);
        int index = -1;
        if (key < sampleArray.length) {
            index = sampleArray[key];
        }
        return index;
    }

    private void calcSampleHash() {
        sampleHash = new HashMap<>(p_sampled * 3 / 2);  // bigger than needed
        for (int i = 0; i < p_sampled; i++) {
            int key = calcKey(v_samples[i]);
            sampleHash.put(key, i);
        }
    }

    public HashMap<Integer, Integer> getSampleHash() {
        return sampleHash;
    }

    public String getFile() {
        return fpath;
    }

    @Override
    public String toString() {
        return fpath;
    }

    /**
     * Simple tests.
     */
    public static void main(String[] args) {
        SampleSchedule ss;
//        ss = new SampleSchedule(64, 256);
//        ss = new SampleSchedule(2, 8);
        ss = new SampleSchedule(32, 128);
//        ss = new SampleSchedule(32, 128, 22433778);
        ss.createArray();
        ss.writeFile();
        ss.readFile();

//        ss = new SampleSchedule("/tmp/sample_schedule.txt");
        ss.display();
        System.out.println("DONE");
    }

}
