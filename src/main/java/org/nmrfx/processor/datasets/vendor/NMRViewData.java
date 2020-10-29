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
package org.nmrfx.processor.datasets.vendor;

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.parameters.FPMult;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.LPParams;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.processor.project.Project;

/**
 * @author bfetler
 *
 * access through NMRDataUtil
 */
public class NMRViewData implements NMRData {

    private final String fpath;
    private boolean isSpectrum = true;
    private boolean isFloat = false, isShort = false, isComplex = true;
    private Dataset dataset;
    private HashMap<String, String> parMap = null;
    private String[] acqOrder;
    private SampleSchedule sampleSchedule = null;

    static final Logger logger = Logger.getLogger("org.nmrfx.processor.datasets.Dataset");

    /**
     * open NMRView parameter and data files
     *
     * @param path : full path to the .fid directory, fid file or spectrum
     * @throws IOException if an I/O error occurs
     */
    public NMRViewData(String path) throws IOException {
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        this.fpath = path;
        openDataFile(path);
    }

    public NMRViewData(Dataset dataset) {
        this.dataset = dataset;
        this.fpath = dataset.getFileName();
    }

    public void close() {
        dataset.close();
    }

    protected static boolean findFID(StringBuilder bpath) {
        boolean found = false;
        if (findFIDFiles(bpath.toString())) {
            found = true;
        }
        return found;
    } // findFID

    private static boolean findFIDFiles(String dpath) {
        boolean found = false;
        if (dpath.toString().endsWith(".nv")) {
            found = true;
        } else if (dpath.toString().endsWith(".ucsf")) {
            found = true;
        }
        return found;
    } // findFIDFiles

    @Override
    public String toString() {
        return fpath;
    }

    @Override
    public String getVendor() {
        return "nmrviewj";
    }

    @Override
    public String getFilePath() {
        return fpath;
    }

    @Override
    public List<VendorPar> getPars() {
        List<VendorPar> vendorPars = new ArrayList<>();
        if (parMap != null) {
            for (Map.Entry<String, String> par : parMap.entrySet()) {
                vendorPars.add(new VendorPar(par.getKey(), par.getValue()));
            }
        }
        return vendorPars;
    }

    @Override
    public String getPar(String parname) {
        if (parMap == null) {
            return null;
        } else {
            return parMap.get(parname);
        }
    }

    @Override
    public Double getParDouble(String parname) {
        if ((parMap == null) || (parMap.get(parname) == null)) {
            return null;
        } else {
            return Double.parseDouble(parMap.get(parname));
        }
    }

    @Override
    public Integer getParInt(String parname) {
        if ((parMap == null) || (parMap.get(parname) == null)) {
            return null;
        } else {
            return Integer.parseInt(parMap.get(parname));
        }
    }

    @Override
    public int getNVectors() {  // number of vectors
        int nVec = 1;
        int nDim = dataset.getNDim();
        for (int i = 1; i < nDim; i++) {
            nVec *= dataset.getSize(i);
        }
        return nVec;
    }

    @Override
    public int getNPoints() {  // points per vector
        int np = dataset.getSize(0);
        if (dataset.getComplex(0)) {
            np /= 2;
        }
        return np;
    }

    public boolean isSpectrum() {
        return isSpectrum;
    }

    @Override
    public int getNDim() {
        return dataset.getNDim();
    }

    @Override
    public void resetAcqOrder() {
        acqOrder = null;
    }

    @Override
    public String[] getAcqOrder() {
        if (acqOrder == null) {
            int nDim = getNDim() - 1;
            acqOrder = new String[nDim * 2];
            for (int i = 0; i < nDim; i++) {
                acqOrder[i * 2] = "p" + (i + 1);
                acqOrder[i * 2 + 1] = "d" + (i + 1);
            }
        }
        return acqOrder;
    }

    @Override
    public void setAcqOrder(String[] newOrder) {
        if (newOrder.length == 1) {
            String s = newOrder[0];
            final int len = s.length();
            int nDim = getNDim();
            int nIDim = nDim - 1;
            if ((len == nDim) || (len == nIDim)) {
                acqOrder = new String[nIDim * 2];
                int j = 0;
                if (sampleSchedule != null) {
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals(nDim + "")) {
                            acqOrder[j++] = "p" + dimStr;
                        }
                    }
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals(nDim + "")) {
                            acqOrder[j++] = "d" + dimStr;
                        }
                    }
                } else {
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals(nDim + "")) {
                            acqOrder[j++] = "p" + dimStr;
                            acqOrder[j++] = "d" + dimStr;
                        }
                    }
                }
            } else if (len > nDim) {
                acqOrder = new String[(len - 1) * 2];
                int j = 0;
                if (sampleSchedule != null) {
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals((nDim + 1) + "")) {
                            acqOrder[j++] = "p" + dimStr;
                        }
                    }
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals((nDim + 1) + "")) {
                            acqOrder[j++] = "d" + dimStr;
                        }
                    }
                } else {
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals((nDim + 1) + "")) {
                            acqOrder[j++] = "p" + dimStr;
                            acqOrder[j++] = "d" + dimStr;
                        }
                    }
                }
            }
        } else {
            this.acqOrder = new String[newOrder.length];
            for (int i = 0; i < newOrder.length; i++) {
                this.acqOrder[i] = newOrder[i];
            }
        }
    }

    @Override
    public SampleSchedule getSampleSchedule() {
        return sampleSchedule;
    }

    @Override
    public void setSampleSchedule(SampleSchedule sampleSchedule) {
        this.sampleSchedule = sampleSchedule;
    }

    @Override
    public String getSymbolicCoefs(int idim) {
        return null;
    }

    @Override
    public double[] getCoefs(int iDim) {
        String name = "f" + iDim + "coef";
        double dcoefs[] = {1, 0, 0, 0}; // reasonable for noesy, tocsy
        String s;
        if ((s = getPar(name)) == null) {
            s = "";
        }
        if (!s.equals("")) {
            String[] coefs = s.split(" ");
            dcoefs = new double[coefs.length];
            for (int i = 0; i < coefs.length; i++) {
                dcoefs[i] = Double.valueOf(coefs[i]);
            }
        }
        return dcoefs;
    }
// e.g. hnco3d.fid f1coef="1 0 0 0 0 0 -1 0" f2coef="1 0 1 0 0 1 0 -1"

    private char getAxisChar(int iDim) {
        char achar = 'h';
        String axis = getPar("axis");
        if (axis != null) {
            if (iDim < axis.length()) {
                achar = axis.charAt(iDim);
            } else {
                switch (iDim) {
                    case 1:
                        achar = 'd';
                        break;
                    case 2:
                        achar = '2';
                        break;
                    case 3:
                        achar = '3';
                        break;
                    default:
                        achar = 'h';
                        break;
                }
            }
        }
        return achar;
    }

    private String getAxisFreqName(int iDim) {
        String freqName = "hz";
        char achar = getAxisChar(iDim);
        switch (achar) {
            case 'h':
                freqName = "hz";
                break;
            case 'p':
                freqName = "sfrq";
                break;
            case 'd':
                freqName = "dfrq";
                break;
            case '1':
                freqName = "dfrq";
                break;
            case '2':
                freqName = "dfrq2";
                break;
            case '3':
                freqName = "dfrq3";
                break;
            default:
                freqName = "hz";
                break;
        }
        return freqName;
    }

    public String getApptype() {
        // homo2d hetero2d etc. if exists
        return getPar("apptype");
    }

    @Override
    public double getSF(int iDim) {
        return dataset.getSf(iDim);
    }

    @Override
    public void setSF(int iDim, double value) {
        dataset.setSf(iDim, value);
    }

    @Override
    public void resetSF(int iDim) {
    }

    @Override
    public double getSW(int iDim) {
        return dataset.getSw(iDim);
    }

    @Override
    public void setSW(int iDim, double value) {
        dataset.setSw(iDim, value);
    }

    @Override
    public void resetSW(int iDim) {
    }

    @Override
    public String[] getSFNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            names[i] = "sf" + (i + 1);
        }
        return names;
    }

    @Override
    public String[] getSWNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        String name;
        for (int i = 0; i < nDim; i++) {
            names[i] = "sw" + (i + 1);
        }
        return names;
    }

    @Override
    public String[] getLabelNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            names[i] = getTN(i) + "_" + (i + 1);
        }
        return names;
    }

    private String getAxisTNname(int iDim) {
        return dataset.getStdLabel(iDim);
    }

    @Override
    public String getTN(int iDim) {
        return dataset.getNucleus(iDim).getName();
    }

    @Override
    public void setRef(int iDim, double ref) {
        dataset.setRefValue(iDim, ref);
    }

    @Override
    public void resetRef(int iDim) {
    }

    @Override
    public double getRef(int iDim) {
        return dataset.getRefValue(iDim);
    }

    @Override
    public double getRefPoint(int iDim) {
        return dataset.getRefPt(iDim);
    }

    @Override
    public int getSize(int iDim) {
        int size = dataset.getSize(iDim);
        if (dataset.getComplex(iDim)) {
            size /= 2;
        }
        return size;
    }

    @Override
    public void setSize(int dim, int size) {
        // can't change size of nmrview dataset
    }

    @Override
    public boolean isFID() {
        return !dataset.getFreqDomain(0);
    }

    @Override
    public boolean isFrequencyDim(int iDim) {
        boolean result = true;
        double[] values = dataset.getValues(iDim);
        if (!isComplex(iDim) && (iDim == getNDim() - 1)) {
            if ((values != null) && (values.length == getSize(iDim))) {
                result = false;
            }
        }
        return result;

    }

    @Override
    public boolean isComplex(int idim) {
        return dataset.getComplex(idim);
    }

    @Override
    public boolean getNegatePairs(int iDim) {
        return false;
    }

    @Override
    public String getSolvent() {
        return dataset.getSolvent();
    }

    @Override
    public double getTempK() {
        return 298.0;

    }

    @Override
    public String getSequence() {
        return "unknown";
    }

    @Override
    public double getPH0(int iDim) {
        return dataset.getPh0(iDim);
    }

    @Override
    public double getPH1(int iDim) {
        return dataset.getPh1(iDim);
    }

    @Override
    public int getLeftShift(int iDim) {
        return 0;
    }

    @Override
    public double getExpd(int iDim) {
        return 0.0;
    }

    @Override
    public SinebellWt getSinebellWt(int iDim) {
        return null;
    }

    @Override
    public GaussianWt getGaussianWt(int iDim) {
        return null;
    }

    @Override
    public FPMult getFPMult(int iDim) {
        return null;
    }

    @Override
    public LPParams getLPParams(int iDim) {
        return null;
    }

    public List<Double> getValues(int dim) {
        double[] values = dataset.getValues(dim);
        if (values == null) {
            return Collections.EMPTY_LIST;
        } else {
            List<Double> valueList = new ArrayList<>();
            for (int i = 0; i < values.length; i++) {
                valueList.add(values[i]);
            }
            return valueList;
        }
    }

    @Override
    public void readVector(int iVec, Vec dvec) {
        int nDim = getNDim();
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        pt[0][0] = 0;
        pt[0][1] = dataset.getSize(0) - 1;
        int offset = iVec;
        int[] strides = new int[nDim];
        strides[0] = 1;
        int nIndirect = nDim - 1;

        for (int i = 1; i < nIndirect; i++) {
            strides[i] = strides[i - 1] * dataset.getSize(i);
        }
        for (int i = nIndirect; i > 0; i--) {
            int index = iVec / strides[i - 1];
            pt[i][0] = index;
            pt[i][1] = index;
            iVec = iVec % strides[i - 1];
        }
        for (int i = 0; i < nDim; i++) {
            dim[i] = i;
        }
        try {
            dataset.readVectorFromDatasetFile(pt, dim, dvec);
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
        }
    }

    public void readVector(int iDim, int iVec, Vec dvec) {
    }

    @Override
    public void readVector(int iVec, Complex[] cdata) {
    }

    public void readVector(int iDim, int iVec, Complex[] cdata) {
    }

    @Override
    public void readVector(int iVec, double[] data) {
    }

    @Override
    public void readVector(int iVec, double[] rdata, double[] idata) {
    }

    // open NMRView data file, read header
    private void openDataFile(String datapath) throws IOException {
        System.out.println("open data file " + datapath);
        File file = new File(datapath);

        List<Dataset> currentDatasets = Project.getActive().getDatasetsWithFile(file);
        if (!currentDatasets.isEmpty()) {
            System.out.println("already open");
            dataset = currentDatasets.get(0);
        } else {
            dataset = new Dataset(datapath, datapath, true, false);
        }
    }

    public Dataset getDataset() {
        return dataset;
    }

    public static void main(String args[]) {
    }
}
