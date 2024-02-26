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
package org.nmrfx.processor.datasets.vendor.nmrpipe;

import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.datasets.AcquisitionType;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetGroupIndex;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.parameters.FPMult;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.LPParams;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.VendorPar;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@PythonAPI("dscript")
public class NMRPipeData implements NMRData {
    private static final Logger log = LoggerFactory.getLogger(NMRPipeData.class);

    static private final int DIMSIZE = 4;
    static private final int MAXDIM = 10;
    static private final int MAX_FILECHANNELS = 16;
    private final String dirName;
    private final String fpath;
    private FileChannel fc = null;
    private double groupDelay = 0.0;
    private final double scale = 1.0e6;
    String template = "%03d.ft";
    private Double[] Ref = new Double[MAXDIM];
    private Double[] Sw = new Double[MAXDIM];
    private Double[] Sf = new Double[MAXDIM];
    private final AcquisitionType[] symbolicCoefs = new AcquisitionType[MAXDIM];

    private final List<DatasetGroupIndex> datasetGroupIndices = new ArrayList<>();

    Header fileHeader;
    private DatasetType preferredDatasetType = DatasetType.NMRFX;

    private SampleSchedule sampleSchedule = null;
    Map<Integer, FileChannel> fcMap;

    boolean swapBits = true;
    /**
     * open Varian parameter and data files
     *
     * @param path : full path to the .fid directory, fid file or spectrum
     */
    static final private int FILEHEADERSIZE = 2048;
    int ebytes = 4;
    int tbytes;
    int np;
    boolean isFloat = true;
    boolean isShort = false;
    int[] sizes;
    Dataset dataSource = null;
    File nusFile = null;

    public NMRPipeData(String path, File nusFile) throws IOException {
        this.nusFile = nusFile;
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        Path filePath = Paths.get(path);
        dirName = filePath.getParent().toString();
        template = "test%03d.fid";

        String fileName = filePath.getFileName().toString();
        if (fileName.contains("%")) {
            template = fileName;
            fileName = String.format(fileName, 1);
            path = Paths.get(dirName, fileName).toString();
        } else {
            Pattern pattern = Pattern.compile("(.+\\D)(\\d+)(\\..+)");
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.matches()) {
                // fixme  only works for 3d
                template = matcher.group(1) + "%0" + matcher.group(2).length() + "d" + matcher.group(3);
                System.out.println("template " + template);
            }
            // fixme use regex to come up with a pattern
        }

        this.fpath = path;
        fcMap = Collections.synchronizedMap(new FileChannelMap(MAX_FILECHANNELS));

        readHeader(fpath);
        openDataFile(path);
        // force caching nDim and sizes
        getNDim();
        np = getSize(0) * 2;
        getDimMap();
        getSizes();
        groupDelay = getDMXValue();
        tbytes = np * ebytes;
//        checkAndOpenSampleSchedule(path);
    }

    public NMRPipeData() {
        ByteBuffer bBuffer = ByteBuffer.allocate(2048);
        dirName = "";
        fpath = "";
        fileHeader = new Header(bBuffer);
    }

    public NMRPipeData(Dataset dataset) throws IOException {
        ByteBuffer bBuffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);
        dirName = "";
        fpath = "";
        fileHeader = new Header(bBuffer);
        setFromDataset(dataset);
        dataSource = dataset;
    }

    private void setFromDataset(Dataset dataset) throws IOException {
        setNDim(dataset.getNDim());
        for (int iDim = 0; iDim < getNDim(); iDim++) {
            setDimOrder(iDim, iDim + 1);
        }
        getDimMap();
        FIELDS.setInt(fileHeader, "QUADFLAG", 1);
        FIELDS.setInt(fileHeader, "2DPHASE", 2);
        FIELDS.setFloat(fileHeader, "TEMPERATURE", (float) dataset.getTempK());
        for (int iDim = 0; iDim < getNDim(); iDim++) {
            setSize(iDim, dataset.getSizeTotal(iDim));
            FIELDS.setFloat(fileHeader, iDim, "OBS", (float) dataset.getSf(iDim));
            FIELDS.setFloat(fileHeader, iDim, "SW", (float) dataset.getSw(iDim));
            FIELDS.setInt(fileHeader, iDim, "TDSIZE", dataset.getTDSize(iDim));
            FIELDS.setInt(fileHeader, iDim, "ZF", dataset.getZFSize(iDim));
            FIELDS.setInt(fileHeader, iDim, "FTSIZE", dataset.getZFSize(iDim));
            int x1 = dataset.getExtFirst(iDim) + 1;
            int x2 = dataset.getExtLast(iDim) + 1;
            int centerPt = dataset.getZFSize(iDim) / 2 + 1;
            if (x1 == x2) {
                x1 = 0;
                x2 = 0;
            } else {
                centerPt -= (x1 - 1);
            }

            double ref = dataset.pointToPPM(iDim, centerPt - 1);
            setRef(iDim, ref);

            FIELDS.setInt(fileHeader, iDim, "CENTER", centerPt);
            FIELDS.setInt(fileHeader, iDim, "X1", x1);
            FIELDS.setInt(fileHeader, iDim, "XN", x2);

            FIELDS.setInt(fileHeader, iDim, "TDSIZE", dataset.getTDSize(iDim));
            FIELDS.setInt(fileHeader, iDim, "QUADFLAG", 1);
            FIELDS.setInt(fileHeader, iDim, "FTFLAG", 1);
            double origenPPM = dataset.pointToPPM(iDim, dataset.getSizeTotal(iDim) - 1);
            double origenHz = origenPPM * dataset.getSf(iDim);
            FIELDS.setFloat(fileHeader, iDim, "ORIG", (float) origenHz);
            FIELDS.setFloat(fileHeader, iDim, "P0", (float) dataset.getPh0(iDim));
            FIELDS.setFloat(fileHeader, iDim, "P1", (float) dataset.getPh1(iDim));
            FIELDS.setString(fileHeader, iDim, "LABEL", dataset.getLabel(iDim));
        }
        getSizes();
        np = getSize(0) * 2;
        tbytes = np * ebytes;
    }

    public static boolean findFID(StringBuilder bpath) {
        return findFIDFiles(bpath.toString());
    }

    public static boolean findFIDFiles(String dpath) {
        boolean found = false;
        File file = new File(dpath);
        String dirName = file.getParent();
        String fileName = file.getName();
        if (fileName.contains("%")) {
            fileName = String.format(fileName, 1);
            file = Paths.get(dirName, fileName).toFile();
        }
        if (file.exists() && file.isFile()) {
            try (FileReader reader = new FileReader(file)) {
                boolean ok = true;
                for (int i = 0; i < 4; i++) {
                    if (reader.read() != 0) {
                        ok = false;
                        break;
                    }
                }
                found = ok;
            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);
            }
        }
        return found;
    } // findFIDFiles

    private FileChannel openDataFile(String dirPath, String templateFileName) {
        Path path = Paths.get(dirPath, templateFileName);
        FileChannel fileChannel = null;
        try {
            fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        } catch (IOException ex) {
            log.warn(fpath, ex);
        }
        return fileChannel;
    }

    // open Varian data file, read header
    private void openDataFile(String datapath) {
        try {
            fc = FileChannel.open(Paths.get(datapath), StandardOpenOption.READ);
        } catch (IOException ex) {
            log.warn(fpath, ex);
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void close() {
        try {
            fc.close();
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    @Override
    public DatasetType getPreferredDatasetType() {
        return preferredDatasetType;
    }

    @Override
    public void setPreferredDatasetType(DatasetType datasetType) {
        this.preferredDatasetType = datasetType;
    }

    @Override
    public String getFilePath() {
        return fpath;
    }

    @Override
    public List<VendorPar> getPars() {
        List<VendorPar> vendorPars = new ArrayList<>();
        for (FIELDS field : FIELDS.values()) {
            vendorPars.add(new VendorPar(field.name(), field.getString(fileHeader)));
        }
        return vendorPars;
    }

    @Override
    public String getPar(String parname) {
        return FIELDS.valueOf(parname).getString(fileHeader);
    }

    @Override
    public Double getParDouble(String parname) {
        return Double.valueOf(FIELDS.valueOf(parname).getFloat(fileHeader));
    }

    @Override
    public Integer getParInt(String parname) {
        return FIELDS.valueOf(parname).getInt(fileHeader);
    }

    @Override
    public int getNVectors() {
        return FIELDS.FDSPECNUM.getInt(fileHeader);
    }

    @Override
    public String getFTType(int iDim) {
        return "ft";
    }

    @Override
    public int getNPoints() {
        return FIELDS.FDSIZE.getInt(fileHeader);
    }

    @Override
    public final int getNDim() {
        return FIELDS.FDDIMCOUNT.getInt(fileHeader);
    }

    public void setNDim(int value) {
        FIELDS.FDDIMCOUNT.setInt(fileHeader, value);
    }

    public void setDimOrder(int dim, int value) {
        FIELDS.setDimOrder(fileHeader, dim, value);
    }

    @Override
    public final int getSize(int dim) {
        int size = FIELDS.getSize(fileHeader, dim);
        if (dim > 0) {
            size /= 2;
        }
        return size;
    }

    @Override
    public void setSize(int dim, int size) {
        FIELDS.setSize(fileHeader, dim, size);
    }

    @Override
    public String getSolvent() {
        return "";
    }

    @Override
    public double getTempK() {
        return FIELDS.FDTEMPERATURE.getFloat(fileHeader);
    }

    public final double getDMXValue() {
        return FIELDS.FDDMXVAL.getFloat(fileHeader);
    }

    @Override
    public String getSequence() {
        return "";
    }

    public String getLabel(int dim) {
        return FIELDS.getString(fileHeader, dim, "LABEL");
    }

    @Override
    public double getSF(int iDim) {
        double sf;
        if (Sf[iDim] != null) {
            sf = Sf[iDim];
        } else {
            sf = FIELDS.getFloat(fileHeader, iDim, "OBS");
        }
        return sf;
    }

    @Override
    public void setSF(int iDim, double value) {
        Sf[iDim] = value;
    }

    @Override
    public void resetSF(int iDim) {
        Sf[iDim] = null;
    }

    @Override
    public double getSW(int iDim) {
        double sw;
        if (Sw[iDim] != null) {
            sw = Sw[iDim];
        } else {
            sw = FIELDS.getFloat(fileHeader, iDim, "SW");
        }
        return sw;
    }

    @Override
    public void setSW(int iDim, double value) {
        Sw[iDim] = value;
    }

    @Override
    public void resetSW(int iDim) {
        Sw[iDim] = null;
    }

    @Override
    public double getRef(int iDim) {
        double ref;
        if (Ref[iDim] != null) {
            ref = Ref[iDim];
        } else {
            ref = FIELDS.getFloat(fileHeader, iDim, "CAR");
            setRef(iDim, ref);
        }
        return ref;
    }

    @Override
    public void setRef(int iDim, double ref) {
        Ref[iDim] = ref;
    }

    @Override
    public void resetRef(int dim) {
        Ref[dim] = null;
    }

    @Override
    public double getRefPoint(int dim) {
        double refpt = getSize(dim) / 2;
        return refpt;
    }

    @Override
    public String getTN(int dim) {
        return "H";
    }

    @Override
    public boolean isComplex(int dim) {
        return true; // fixme
    }

    @Override
    public double[] getCoefs(int dim) {
        return AcquisitionType.HYPER.getCoefficients();
    }

    @Override
    public String getSymbolicCoefs(int dim) {
        return AcquisitionType.HYPER.getLabel();
    }

    public void setUserSymbolicCoefs(int iDim, AcquisitionType coefs) {
        symbolicCoefs[iDim] = coefs;
    }

    public AcquisitionType getUserSymbolicCoefs(int iDim) {
        return symbolicCoefs[iDim];
    }

    @Override
    public String getVendor() {
        return "nmrPipe";
    }

    @Override
    public double getPH0(int dim) {
        double phase0 = FIELDS.getFloat(fileHeader, dim, "P0");
        return phase0;
    }

    @Override
    public double getPH1(int dim) {
        double phase1 = FIELDS.getFloat(fileHeader, dim, "P1");
        return phase1;
    }

    @Override
    public int getLeftShift(int dim) {
        return 0;
    }

    @Override
    public double getExpd(int dim) {
        // fixme lb or decay rate
        return FIELDS.getFloat(fileHeader, dim, "LB");

    }

    @Override
    public SinebellWt getSinebellWt(int dim) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public GaussianWt getGaussianWt(int dim) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public FPMult getFPMult(int dim) {
        return new NMRPipeFPMult(dim);
    }

    @Override
    public LPParams getLPParams(int dim) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String[] getSFNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            int j = fileHeader.getDim(i);
            names[i] = "FDF" + (j + 1) + "OBS";
        }
        return names;
    }

    @Override
    public String[] getSWNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            int j = fileHeader.getDim(i);
            names[i] = "FDF" + (j + 1) + "SW";
        }
        return names;
    }

    @Override
    public String[] getLabelNames() {
        int nDim = getNDim();
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < nDim; i++) {
            names.add(getLabel(i));
        }

        return names.toArray(new String[names.size()]);
    }

    @Override
    public void readVector(int iVec, Vec dvec) {
        dvec.setGroupDelay(groupDelay);
        if (dvec.isComplex()) {
            if (dvec.useApache()) {
                readVector(iVec, dvec.getCvec());
            } else {
                readVector(iVec, dvec.rvec, dvec.ivec);
            }
        } else {
            readVector(iVec, dvec.rvec);
        }
        dvec.fixWithPhasedHFT();
        dvec.dwellTime = 1.0 / getSW(0);
        dvec.centerFreq = getSF(0);
        dvec.setRefValue(getRef(0));
    }

    @Override
    public void readVector(int iVec, Complex[] cdata) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyVecData(dataBuf, cdata);
    }

    @Override
    public void readVector(int iVec, double[] rdata, double[] idata) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyVecData(dataBuf, rdata, idata);
    }

    @Override
    public void readVector(int iVec, double[] data) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyVecData(dataBuf, data);
    }

    @Override
    public void readVector(int iDim, int iVec, Vec dvec) {
        int shiftAmount = 0;
        if (groupDelay > 0) {
            // fixme which is correct (use ceil or not)
            shiftAmount = (int) Math.round(groupDelay);
        }
        if (dvec.isComplex()) {
            if (dvec.useApache()) {
                readVector(iDim, iVec + shiftAmount, dvec.getCvec());
            } else {
// fixme
                readVector(iVec + shiftAmount, dvec.rvec, dvec.ivec);
            }
        } else {
// fixme
            readVector(iVec, dvec.rvec);
        }
        dvec.dwellTime = 1.0 / getSW(iDim);
        dvec.centerFreq = getSF(iDim);
        dvec.setRefValue(getRef(iDim));
        dvec.setPh0(getPH0(iDim));
        dvec.setPh1(getPH1(iDim));
        if (iDim == 0) {
            dvec.setGroupDelay(groupDelay);
        } else {
            dvec.setGroupDelay(0.0);
        }
    }

    public void readVector(int iDim, int iVec, Complex[] cdata) {
        int size = getSize(iDim);
        int nPer = getGroupSize(iDim);
        int nPoints = size * nPer;
        byte[] dataBuf = new byte[nPoints * 4 * 2];
        ByteBuffer buf = ByteBuffer.wrap(dataBuf);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer fbuf = buf.asFloatBuffer();
        for (int j = 0; j < (nPoints * 2); j++) {
            fbuf.put(j, 0);
        }
        int stride = tbytes;
        for (int i = 1; i < iDim; i++) {
            stride *= getSize(i) * 2;
        }

        for (int i = 0; i < (nPoints); i++) {
            if (sampleSchedule != null) {
                int[] point = {i / 2};
                int index = sampleSchedule.getIndex(point);
                if (index != -1) {
                    index = index * 2 + (i % 2);
                    readValue(iDim, stride, index, i, iVec, dataBuf);
                }
            } else {
                readValue(iDim, stride, i, i, iVec, dataBuf);
            }
        }
        for (int j = 0; j < (nPoints * 2); j += 2) {
            double px = fbuf.get(j);
            double py = fbuf.get(j + 1);
            cdata[j / 2] = new Complex(px / scale, py / scale);
        }
    }

    @Override
    public void resetAcqOrder() {
    }

    @Override
    public String[] getAcqOrder() {
        int nDim = getNDim() - 1;
        String[] acqOrder = new String[nDim * 2];
        for (int i = 0; i < nDim; i++) {
            acqOrder[i * 2] = "p" + (i + 1);
            acqOrder[i * 2 + 1] = "d" + (i + 1);
        }
        return acqOrder;
    }

    @Override
    public void setAcqOrder(String[] acqOrder) {
    }

    @Override
    public SampleSchedule getSampleSchedule() {
        return sampleSchedule;
    }

    @Override
    public void setSampleSchedule(SampleSchedule sampleSchedule) {
        this.sampleSchedule = sampleSchedule;
    }

    FileChannel getFileChannel(int i) {
        if (i == 0) {
            return fc;
        }
        int planeIndex = getPlaneIndex(i);
        FileChannel fileChan = fcMap.get(planeIndex);
        if (fileChan == null) {
            String templateFile = getTemplateFile(planeIndex);
            fileChan = openDataFile(dirName, templateFile);
            fcMap.put(planeIndex, fileChan);
        }
        return fileChan;
    }

    // read i'th data block
    private void readVecBlock(int i, byte[] dataBuf) {
        FileChannel iFC = null;
        try {
            iFC = getFileChannel(i);
            int index = getIndexInFile(i);
            int skips = FILEHEADERSIZE + np * ebytes * index;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf);
            int nread = iFC.read(buf, skips);
            if (nread < np) {
                throw new ArrayIndexOutOfBoundsException("file index " + i + " / " + index + " out of bounds");
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
            if (iFC != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    log.warn(ex.getMessage(), ex);
                }
            }
        }
    }  // end readVecBlock

    // read value along dim
    // fixme only works for 2nd dim
    // read value along dim
    // fixme only works for 2nd dim
    private void readValue(int iDim, int stride, int fileIndex, int vecIndex, int xCol, byte[] dataBuf) {
        try {
            int skips = fileIndex * stride + xCol * 4 * 2;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf, vecIndex * 4 * 2, 4);
            int nread = fc.read(buf, skips);
            buf = ByteBuffer.wrap(dataBuf, vecIndex * 4 * 2 + 4, 4);
            nread = fc.read(buf, skips + stride / 2);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    log.warn(ex.getMessage(), ex);
                }
            }
        }
    }

    // copy read data into double array
    private void copyVecData(byte[] dataBuf, double[] data) {
        int j;
        if (isFloat) {
            FloatBuffer fbuf = ByteBuffer.wrap(dataBuf).asFloatBuffer();
            for (j = 0; j < np; j++) {
                data[j] = (double) fbuf.get(j) / scale;
            }
        } else if (isShort) {
            ShortBuffer sbuf = ByteBuffer.wrap(dataBuf).asShortBuffer();
            for (j = 0; j < np; j++) {
                data[j] = (double) sbuf.get(j) / scale;
            }
        } else {
            IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
            for (j = 0; j < np; j++) {
                data[j] = (double) ibuf.get(j) / scale;
            }
        }
    }  // end copyVecData

    // copy read data into Complex array
    private void copyVecData(byte[] dataBuf, Complex[] data) {
        int nComplex = np / 2;
        if (isFloat) {
            ByteBuffer buf = ByteBuffer.wrap(dataBuf);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            FloatBuffer fbuf = buf.asFloatBuffer();
            for (int j = 0; j < nComplex; j++) {
                data[j] = new Complex((double) fbuf.get(j) / scale, (double) -fbuf.get(j + nComplex) / scale);
            }
        } else if (isShort) {
            ShortBuffer sbuf = ByteBuffer.wrap(dataBuf).asShortBuffer();
            for (int j = 0; j < nComplex; j++) {
                data[j] = new Complex((double) sbuf.get(j) / scale, (double) -sbuf.get(j + nComplex) / scale);
            }
        } else {
            IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
            for (int j = 0; j < nComplex; j++) {
                data[j] = new Complex((double) ibuf.get(j) / scale, (double) -ibuf.get(j + nComplex) / scale);
            }
        }
    }  // end copyVecData

    // copy read data into double arrays of real, imaginary
    private void copyVecData(byte[] dataBuf, double[] rdata, double[] idata) {
        int nComplex = np / 2;
        if (isFloat) {
            FloatBuffer fbuf = ByteBuffer.wrap(dataBuf).asFloatBuffer();
            for (int j = 0; j < nComplex; j++) {
                rdata[j] = (double) fbuf.get(j) / scale;
                idata[j] = (double) -fbuf.get(j + nComplex) / scale;
            }
        } else if (isShort) {
            ShortBuffer sbuf = ByteBuffer.wrap(dataBuf).asShortBuffer();
            for (int j = 0; j < nComplex; j++) {
                rdata[j] = (double) sbuf.get(j) / scale;
                idata[j] = (double) -sbuf.get(j + nComplex) / scale;
            }
        } else {
            IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
            for (int j = 0; j < nComplex; j++) {
                rdata[j] = (double) ibuf.get(j) / scale;
                idata[j] = (double) -ibuf.get(j + nComplex) / scale;
            }
        }
    }  // end copyVecData

    private void getDimMap() {
        for (int iDim = 0; iDim < DIMSIZE; iDim++) {
            fileHeader.dimMap[iDim] = FIELDS.getInt(fileHeader, "DIMORDER" + (iDim + 1)) - 1;
        }
    }

    final void getSizes() throws IOException {
        sizes = new int[DIMSIZE];
        for (int dim = 0; dim < DIMSIZE; dim++) {
            int size = FIELDS.getSize(fileHeader, dim);
            sizes[dim] = size > 0 ? size : 1;
        }
        boolean gotSchedule = false;
        if (nusFile == null) {
            File fidFile = new File(fpath);
            Path nusPath = fidFile.getParentFile().toPath().resolve("nuslist");
            nusFile = nusPath.toFile();
        }
        if (nusFile.exists()) {
            readSampleSchedule(nusFile.getPath(), true);
            if (sampleSchedule.getTotalSamples() == 0) {
                throw new IOException("nuslist file exists, but is empty");
            } else {
                gotSchedule = true;
            }
        }
    }

    @Override
    public List<DatasetGroupIndex> getSkipGroups() {
        return datasetGroupIndices;
    }

    String getTemplateFile(int index) {
        int cube = index / (sizes[1] * sizes[2]);
        int plane = index;
        String fileName = String.format(template, plane + 1);
        return fileName;
    }

    int getIndexInFile(int index) {
        int newIndex = index % (sizes[1]);
        return newIndex;
    }

    int getPlaneIndex(int index) {
        int planeIndex = index / (sizes[1]);
        return planeIndex;
    }

    class FileChannelMap extends LRUMap<Integer, FileChannel> {

        public FileChannelMap(int maxEntries) {
            super(maxEntries);
        }

        @Override
        protected boolean removeLRU(LinkEntry<Integer, FileChannel> entry) {
            FileChannel fileChannel = entry.getValue();
            try {
                fileChannel.close();
            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);
            }
            return true;  // actually delete entry
        }
    }

    class NMRPipeFPMult extends FPMult {

        NMRPipeFPMult(int iDim) {
            float value = FIELDS.getFloat(fileHeader, iDim, "C1");
            if (value != 0.0) {
                value += 1.0f;
                fpmult = value;
                exists = true;
            }

        }
    }

    class Header {

        FloatBuffer fBuffer;
        ByteBuffer bBuffer;
        private int[] dimMap = new int[DIMSIZE];

        Header(ByteBuffer bBuffer) {
            this.bBuffer = bBuffer;
            fBuffer = bBuffer.asFloatBuffer();
        }

        byte[] getByteArray(int index, int size) {
            byte[] bytes = new byte[size];
            for (int i = 0; i < size; i++) {
                bytes[i] = bBuffer.get(index + i);
            }
            return bytes;
        }

        void setByteArray(int index, int size, byte[] bytes, int maxLen) {
            for (int i = 0; i < size; i++) {
                bBuffer.put(index + i, bytes[i]);
            }
            for (int i = size; i < maxLen; i++) {
                bBuffer.put(index + i, (byte) 0);
            }
        }

        String getString(int index, int size) {
            byte[] bytes = getByteArray(index, size);
            int length = bytes.length;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == 0) {
                    length = i;
                    break;
                }
            }
            String string = new String(bytes, 0, length);
            return string;
        }

        float getFloat(int index) {
            return fBuffer.get(index);
        }

        void setFloat(int index, float value) {
            fBuffer.put(index, value);
        }

        int getInt(int index) {
            return (int) Math.round(fBuffer.get(index));
        }

        void setInt(int index, int value) {
            fBuffer.put(index, value);
        }

        int getDim(int iDim) {
            return dimMap[iDim];
        }
    }

    enum FTYPES {
        INT {
            @Override
            String toString(FIELDS field, Header header) {
                int value = field.getInt(header);
                return String.format("%d", value);
            }
        },
        FLOAT {
            @Override
            String toString(FIELDS field, Header header) {
                float value = field.getFloat(header);
                return String.format("%.5f", value);
            }
        },
        STRING {
            @Override
            String toString(FIELDS field, Header header) {
                return header.getString(field.offset * 4, field.stringLen);
            }

        };

        abstract String toString(FIELDS field, Header header);
    }

    public enum FIELDS {
        FDMAGIC(0, FTYPES.INT),/* Should be zero in valid NMRPipe data.            */
        FDFLTFORMAT(1, FTYPES.INT),/* Constant defining floating point format.         */
        FDFLTORDER(2, FTYPES.INT),/* Constant defining byte order.                    */
        FDSIZE(99, FTYPES.INT),/* Number of points in current dim R|I.             */
        FDF2SIZE(99, FTYPES.INT),
        FDREALSIZE(97, FTYPES.INT),/* Number of valid time-domain pts (obsolete).      */
        FDSPECNUM(219, FTYPES.INT),/* Number of complex 1D slices in file.             */
        FDF1SIZE(219, FTYPES.INT),
        FDQUADFLAG(106, FTYPES.INT),/* See Data Type codes below.                       */
        FD2DPHASE(256, FTYPES.INT),/* See 2D Plane Type codes below.                   */
        FDTRANSPOSED(221, FTYPES.INT),/* 1=Transposed, 0=Not Transposed. (                */
        FDDIMCOUNT(9, FTYPES.INT),/* Number of dimensions in complete data.           */
        FDDIMORDER(24, FTYPES.INT),/* Array describing dimension order.                */
        FDDIMORDER1(24, FTYPES.INT),/* Dimension stored in X-Axis.                      */
        FDDIMORDER2(25, FTYPES.INT),/* Dimension stored in Y-Axis.                      */
        FDDIMORDER3(26, FTYPES.INT),/* Dimension stored in Z-Axis.                      */
        FDDIMORDER4(27, FTYPES.INT),/* Dimension stored in A-Axis.                      */
        FDNUSDIM(45, FTYPES.INT),/* Unexpanded NUS dimensions.                       */
        FDPIPEFLAG(57, FTYPES.INT),/* Dimension code of data stream.                  */
        FDCUBEFLAG(447, FTYPES.INT),/* Data is 3D cube file series.                    */
        FDPIPECOUNT(75, FTYPES.INT),/* Number of functions in pipe.                    */
        FDSLICECOUNT0(443, FTYPES.INT),/* Encodes number of 1D slices in stream. (        */
        FDSLICECOUNT1(446, FTYPES.INT),/* Encodes number of 1D slices in stream. (        */
        FDFILECOUNT(442, FTYPES.INT),/* Number of files in complete data.               */
        FDTHREADCOUNT(444, FTYPES.INT),/* Multi-Thread Mode: Number of Threads. (         */
        FDTHREADID(445, FTYPES.INT),/* Multi-Thread Mode: Thread ID, First = 0.        */
        FDFIRSTPLANE(77, FTYPES.INT),/* First Z-Plane in subset.       Added for NMRPipe. */
        FDLASTPLANE(78, FTYPES.INT),/* Last Z-Plane in subset.        Added for NMRPipe. */
        FDPARTITION(65, FTYPES.INT),/* Slice count for server mode.   Added for NMRPipe. */
        FDPLANELOC(14, FTYPES.INT),/* Location of this plane; currently unused.         */
        FDMAX(247),/* Max value in real part of data.                  */
        FDMIN(248),/* Min value in real part of data.                  */
        FDSCALEFLAG(250, FTYPES.INT),/* 1 if FDMAX and FDMIN are valid.                  */
        FDDISPMAX(251),/* Max value, used for display generation.          */
        FDDISPMIN(252),/* Min value, used for display generation.          */
        FDPTHRESH(253),/* Positive threshold for peak detection.           */
        FDNTHRESH(254),/* Negative threshold for peak detection.           */
        FDUSER1(70),
        FDUSER2(71),
        FDUSER3(72),
        FDUSER4(73),
        FDUSER5(74),
        FDUSER6(76),
        FDLASTBLOCK(359, FTYPES.INT),
        FDCONTBLOCK(360, FTYPES.INT),
        FDBASEBLOCK(361, FTYPES.INT),
        FDPEAKBLOCK(362, FTYPES.INT),
        FDBMAPBLOCK(363, FTYPES.INT),
        FDHISTBLOCK(364, FTYPES.INT),
        FD1DBLOCK(365, FTYPES.INT),
        FDMONTH(294, FTYPES.INT),
        FDDAY(295, FTYPES.INT),
        FDYEAR(296, FTYPES.INT),
        FDHOURS(283, FTYPES.INT),
        FDMINS(284, FTYPES.INT),
        FDSECS(285, FTYPES.INT),
        FDMCFLAG(135, FTYPES.INT),/* Magnitude Calculation performed.               */
        FDNOISE(153),/* Used to contain an RMS noise estimate.         */
        FDRANK(180, FTYPES.INT),/* Estimate of matrix rank; Added for NMRPipe.    */
        FDTEMPERATURE(157),/* Temperature, degrees C. (                      */
        FD2DVIRGIN(399, FTYPES.INT),/* 0=Data never accessed, header never adjusted.  */
        FDTAU(199),/* A Tau value (for spectral series).             */
        FDDOMINFO(266, FTYPES.INT),/* Spectral/Spatial Flags. Added for NMRPipe.     */
        FDMETHINFO(267, FTYPES.INT),/* FT/Direct Flags. Added for NMRPipe.            */
        FDSCORE(370),/* Added for screening score etc.                 */
        FDSCANS(371, FTYPES.INT),/* Number of Scans per 1D.                        */
        FDSRCNAME(286, FTYPES.STRING, 16),/* char srcFile[16]  286-289 */
        FDUSERNAME(290, FTYPES.STRING, 16),/* char uName[16]    290-293 */
        FDOPERNAME(464, FTYPES.STRING, 32),/* char oName[32]    464-471 */
        FDTITLE(297, FTYPES.STRING, 60),/* char title[60]    297-311 */
        FDCOMMENT(312, FTYPES.STRING, 160),/* char comment[160] 312-351 */
        FDF2LABEL(16, FTYPES.STRING, 8),
        FDF2APOD(95, FTYPES.INT),
        FDF2SW(100),
        FDF2OBS(119),
        FDF2OBSMID(378),
        FDF2ORIG(101),
        FDF2UNITS(152, FTYPES.INT),
        FDF2QUADFLAG(56, FTYPES.INT),/* Added for NMRPipe. */
        FDF2FTFLAG(220, FTYPES.INT),
        FDF2AQSIGN(64),/* Added for NMRPipe. */
        FDF2CAR(66),/* Added for NMRPipe. */
        FDF2CENTER(79),/* Added for NMRPipe. */
        FDF2OFFPPM(480),/* Added for NMRPipe. */
        FDF2P0(109),
        FDF2P1(110),
        FDF2APODCODE(413, FTYPES.INT),
        FDF2APODQ1(415),
        FDF2APODQ2(416),
        FDF2APODQ3(417),
        FDF2LB(111),
        FDF2GB(374),
        FDF2GOFF(382),
        FDF2C1(418) {
            @Override
            float getFloat(Header header) {
                return header.getFloat(offset) + 1.0f;
            }

            @Override
            void setFloat(Header header, float value) {
                header.setFloat(offset, value - 1.0f);
            }
        },
        FDF2ZF(108, FTYPES.INT) {
            @Override
            int getInt(Header header) {
                return -header.getInt(offset);
            }

            @Override
            void setInt(Header header, int value) {
                header.setInt(offset, -value);
            }
        },
        FDF2X1(257, FTYPES.INT),/* Added for NMRPipe. */
        FDF2XN(258, FTYPES.INT),/* Added for NMRPipe. */
        FDF2FTSIZE(96, FTYPES.INT),/* Added for NMRPipe. */
        FDF2TDSIZE(386, FTYPES.INT),/* Added for NMRPipe. */
        FDDMXVAL(40),/* Added for NMRPipe. */
        FDDMXFLAG(41),/* Added for NMRPipe. */
        FDDELTATR(42),/* Added for NMRPipe. */
        FDF1LABEL(18, FTYPES.STRING, 8),
        FDF1APOD(428, FTYPES.INT),
        FDF1SW(229),
        FDF1OBS(218),
        FDF1OBSMID(379),
        FDF1ORIG(249),
        FDF1UNITS(234, FTYPES.INT),
        FDF1FTFLAG(222, FTYPES.INT),
        FDF1AQSIGN(475),/* Added for NMRPipe. */
        FDF1QUADFLAG(55, FTYPES.INT),/* Added for NMRPipe. */
        FDF1CAR(67),/* Added for NMRPipe. */
        FDF1CENTER(80),/* Added for NMRPipe. */
        FDF1OFFPPM(481),/* Added for NMRPipe. */
        FDF1P0(245),
        FDF1P1(246),
        FDF1APODCODE(414, FTYPES.INT),
        FDF1APODQ1(420),
        FDF1APODQ2(421),
        FDF1APODQ3(422),
        FDF1LB(243),
        FDF1GB(375),
        FDF1GOFF(383),
        FDF1C1(423) {
            @Override
            float getFloat(Header header) {
                return header.getFloat(offset) + 1.0f;
            }

            @Override
            void setFloat(Header header, float value) {
                header.setFloat(offset, value - 1.0f);
            }
        },
        FDF1ZF(437, FTYPES.INT) {
            @Override
            int getInt(Header header) {
                return -header.getInt(offset);
            }

            @Override
            void setInt(Header header, int value) {
                header.setInt(offset, -value);
            }
        },
        FDF1X1(259, FTYPES.INT),/* Added for NMRPipe. */
        FDF1XN(260, FTYPES.INT),/* Added for NMRPipe. */
        FDF1FTSIZE(98, FTYPES.INT),/* Added for NMRPipe. */
        FDF1TDSIZE(387, FTYPES.INT),/* Added for NMRPipe. */
        FDF3LABEL(20, FTYPES.STRING, 8),
        FDF3APOD(50, FTYPES.INT),/* Added for NMRPipe. */
        FDF3OBS(10),
        FDF3OBSMID(380),
        FDF3SW(11),
        FDF3ORIG(12),
        FDF3FTFLAG(13, FTYPES.INT),
        FDF3AQSIGN(476, FTYPES.INT),/* Added for NMRPipe. */
        FDF3SIZE(15, FTYPES.INT),
        FDF3QUADFLAG(51, FTYPES.INT),/* Added for NMRPipe. */
        FDF3UNITS(58),/* Added for NMRPipe. */
        FDF3P0(60),/* Added for NMRPipe. */
        FDF3P1(61),/* Added for NMRPipe. */
        FDF3CAR(68),/* Added for NMRPipe. */
        FDF3CENTER(81),/* Added for NMRPipe. */
        FDF3OFFPPM(482),/* Added for NMRPipe. */
        FDF3APODCODE(400, FTYPES.INT),/* Added for NMRPipe. */
        FDF3APODQ1(401),/* Added for NMRPipe. */
        FDF3APODQ2(402),/* Added for NMRPipe. */
        FDF3APODQ3(403),/* Added for NMRPipe. */
        FDF3LB(372),/* Added for NMRPipe. */
        FDF3GB(376),/* Added for NMRPipe. */
        FDF3GOFF(384),/* Added for NMRPipe. */
        FDF3C1(404) {
            @Override
            float getFloat(Header header) {
                return header.getFloat(offset) + 1.0f;
            }

            @Override
            void setFloat(Header header, float value) {
                header.setFloat(offset, value - 1.0f);
            }
        },
        FDF3ZF(438, FTYPES.INT) {
            @Override
            int getInt(Header header) {
                return -header.getInt(offset);
            }

            @Override
            void setInt(Header header, int value) {
                header.setInt(offset, -value);
            }
        },
        FDF3X1(261, FTYPES.INT),/* Added for NMRPipe. */
        FDF3XN(262, FTYPES.INT),/* Added for NMRPipe. */
        FDF3FTSIZE(200, FTYPES.INT),/* Added for NMRPipe. */
        FDF3TDSIZE(388, FTYPES.INT),/* Added for NMRPipe. */
        FDF4LABEL(22, FTYPES.STRING, 8),
        FDF4APOD(53, FTYPES.INT),/* Added for NMRPipe. */
        FDF4OBS(28),
        FDF4OBSMID(381),
        FDF4SW(29),
        FDF4ORIG(30),
        FDF4FTFLAG(31),
        FDF4AQSIGN(477),/* Added for NMRPipe. */
        FDF4SIZE(32),
        FDF4QUADFLAG(54, FTYPES.INT),/* Added for NMRPipe. */
        FDF4UNITS(59),/* Added for NMRPipe. */
        FDF4P0(62),/* Added for NMRPipe. */
        FDF4P1(63),/* Added for NMRPipe. */
        FDF4CAR(69),/* Added for NMRPipe. */
        FDF4CENTER(82),/* Added for NMRPipe. */
        FDF4OFFPPM(483),/* Added for NMRPipe. */
        FDF4APODCODE(405, FTYPES.INT),/* Added for NMRPipe. */
        FDF4APODQ1(406),/* Added for NMRPipe. */
        FDF4APODQ2(407),/* Added for NMRPipe. */
        FDF4APODQ3(408),/* Added for NMRPipe. */
        FDF4LB(373),/* Added for NMRPipe. */
        FDF4GB(377),/* Added for NMRPipe. */
        FDF4GOFF(385),/* Added for NMRPipe. */
        FDF4C1(409) {
            @Override
            float getFloat(Header header) {
                return header.getFloat(offset) + 1.0f;
            }

            @Override
            void setFloat(Header header, float value) {
                header.setFloat(offset, value - 1.0f);
            }
        },
        FDF4ZF(439, FTYPES.INT) {
            @Override
            int getInt(Header header) {
                return -header.getInt(offset);
            }

            @Override
            void setInt(Header header, int value) {
                header.setInt(offset, -value);
            }
        },
        FDF4X1(263, FTYPES.INT),/* Added for NMRPipe. */
        FDF4XN(264, FTYPES.INT),/* Added for NMRPipe. */
        FDF4FTSIZE(201, FTYPES.INT),/* Added for NMRPipe. */
        FDF4TDSIZE(389, FTYPES.INT);/* Added for NMRPipe. *//* Added for NMRPipe. */

        int offset;
        FTYPES type;
        int stringLen;

        FIELDS(int fieldIndex) {
            offset = fieldIndex;
            this.type = FTYPES.FLOAT;
        }

        FIELDS(int fieldIndex, FTYPES type) {
            offset = fieldIndex;
            this.type = type;
        }

        FIELDS(int fieldIndex, FTYPES type, int stringLen) {
            offset = fieldIndex;
            this.type = type;
            this.stringLen = stringLen;
        }

        String getString(Header header) {
            return type.toString(this, header);
        }

        float getFloat(Header header) {
            return header.getFloat(offset);
        }

        int getInt(Header header) {
            return header.getInt(offset);
        }

        void setInt(Header header, int value) {
            header.setInt(offset, value);
        }

        void setFloat(Header header, float value) {
            header.setFloat(offset, value);
        }

        byte[] getByteArray(Header header, int nBytes) {
            return header.getByteArray(offset * 4, nBytes);
        }

        void setByteArray(Header header, byte[] bytes, int maxLen) {
            header.setByteArray(offset * 4, bytes.length, bytes, maxLen);
        }

        static int getSize(Header header, int iDim) {
            int size = 0;
            switch (iDim) {
                case 0:
                    size = FDSIZE.getInt(header);
                    break;
                case 1:
                    size = FDSPECNUM.getInt(header);
                    break;
                case 2:
                    size = FDF3SIZE.getInt(header);
                    break;
                case 3:
                    size = FDF4SIZE.getInt(header);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid dimension " + iDim);

            }
            return size;
        }

        static void setSize(Header header, int iDim, int size) {
            switch (iDim) {
                case 0:
                    FDSIZE.setInt(header, size);
                    break;
                case 1:
                    FDSPECNUM.setInt(header, size);
                    break;
                case 2:
                    FDF3SIZE.setInt(header, size);
                    break;
                case 3:
                    FDF4SIZE.setInt(header, size);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid dimension " + iDim);
            }
        }

        static float getSW(Header header, int iDim) {
            int jDim = header.getDim(iDim);
            return valueOf("FDF" + (jDim + 1) + "SW").getFloat(header);
        }

        void setSW(Header header, int iDim, float value) {
            int jDim = header.getDim(iDim);
            valueOf("FDF" + (jDim + 1) + "SW").setFloat(header, value);
        }

        static float getFloat(Header header, int iDim, String name) {
            int jDim = header.getDim(iDim);
            return valueOf("FDF" + (jDim + 1) + name).getFloat(header);
        }

        static void setFloat(Header header, int iDim, String name, float value) {
            int jDim = header.getDim(iDim);
            valueOf("FDF" + (jDim + 1) + name).setFloat(header, value);
        }

        static void setFloat(Header header, String name, float value) {
            valueOf("FD" + name).setFloat(header, value);
        }

        static float getFloat(Header header, String name) {
            return valueOf("FD" + name).getFloat(header);
        }

        static String getString(Header header, int iDim, String name) {
            int jDim = header.getDim(iDim);
            return valueOf("FDF" + (jDim + 1) + name).getString(header);
        }

        static String getString(Header header, String name) {
            return valueOf("FD" + name).getString(header);
        }

        static String getString(Header header, int iDim, String name, int size) {
            int jDim = header.getDim(iDim);
            byte[] bytes = valueOf("FDF" + (jDim + 1) + name).getByteArray(header, size);
            int length = bytes.length;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == 0) {
                    length = i;
                    break;
                }
            }
            String string = new String(bytes, 0, length);
            return string;
        }

        static void setString(Header header, int iDim, String name, String value) {
            int jDim = header.getDim(iDim);
            byte[] bytes = value.getBytes();
            int maxLen = valueOf("FDF" + (jDim + 1) + name).stringLen;
            valueOf("FDF" + (jDim + 1) + name).setByteArray(header, bytes, maxLen);
        }

        void setValue(Header header, int iDim, String name, float value) {
            int jDim = header.getDim(iDim);
            valueOf("FDF" + (jDim + 1) + name).setFloat(header, value);
        }

        static void setDimOrder(Header header, int iDim, int value) {
            valueOf("FDDIMORDER" + (iDim + 1)).setInt(header, value);
        }

        static void setInt(Header header, int iDim, String name, int value) {
            int jDim = header.getDim(iDim);
            valueOf("FDF" + (jDim + 1) + name).setInt(header, value);
        }

        static int getInt(Header header, int iDim, String name) {
            int jDim = header.getDim(iDim);
            return (int) Math.round(valueOf("FDF" + (jDim + 1) + name).getInt(header));
        }

        static int getInt(Header header, String name) {
            return (int) Math.round(valueOf("FD" + name).getInt(header));
        }

        static void setInt(Header header, String name, int value) {
            valueOf("FD" + name).setInt(header, value);
        }

    }

    public void saveFile(String template) throws IOException {
        this.template = template;
        ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
        int[] indices = new int[2];
        int nBytes = dataSource.getSizeTotal(0) * Float.BYTES;
        int nPlanes = dataSource.getNDim() > 2 ? dataSource.getSizeTotal(2) : 1;
        for (int iPlane = 0; iPlane < nPlanes; iPlane++) {
            indices[1] = iPlane;
            String fileName = getTemplateFile(iPlane);
            try (RandomAccessFile outFile = new RandomAccessFile(fileName, "rw")) {
                outFile.write(fileHeader.bBuffer.array());
                ByteBuffer byteBuffer = ByteBuffer.allocate(nBytes);
                FloatBuffer floatBuffer = byteBuffer.order(byteOrder).asFloatBuffer();
                Vec vec = new Vec(dataSource.getSizeTotal(0));
                for (int i = 0, nRows = dataSource.getSizeTotal(1); i < nRows; i++) {
                    indices[0] = i;
                    dataSource.readVector(vec, indices, 0);
                    for (int j = 0, n = vec.getSize(); j < n; j++) {
                        floatBuffer.put(j, (float) vec.getReal(j));
                    }
                    outFile.write(byteBuffer.array());
                }
            }
        }
    }

    public final void readHeader(String fileName) {
        ByteBuffer buffer = null;
        try (FileChannel fileChannel = FileChannel.open(Paths.get(fileName), StandardOpenOption.READ)) {
            buffer = ByteBuffer.allocate(2048);
            int nBytes = fileChannel.read(buffer);
            buffer.position(0);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        } catch (IOException ioE) {
            throw new IllegalArgumentException("File doesn't exist");
        }
        fileHeader = new Header(buffer);
    }

    public void dumpHeader() {
        dumpHeader(fileHeader);

    }

    private void dumpHeader(Header header) {
        int nDim = FIELDS.FDDIMCOUNT.getInt(header);
        String[] pars = {"MAGIC", "FLTFORMAT", "FLTORDER", "SIZE", "SPECNUM", "QUADFLAG", "2DPHASE", "TRANSPOSED", "DIMCOUNT",
                "DIMORDER", "DIMORDER1", "DIMORDER2", "DIMORDER3", "DIMORDER4", "NUSDIM",
                "PIPEFLAG", "PIPECOUNT", "SLICECOUNT0", "SLICECOUNT1", "FILECOUNT", "THREADCOUNT", "THREADID",
                "FIRSTPLANE", "LASTPLANE", "PARTITION", "MAX", "MIN", "SCALEFLAG", "DISPMAX", "DISPMIN", "PTHRESH",
                "NTHRESH", "MONTH", "DAY", "YEAR", "HOURS", "MINS", "SECS", "MCFLAG", "NOISE", "TEMPERATURE",
                "TAU", "TITLE", "COMMENT"
        };
        String[] dimPars = {"SIZE", "APOD", "SW", "OBS", "SW", "ORIG", "FTFLAG", "QUADFLAG", "LABEL", "OBSMID", "AQSIGN", "P0", "P1",
                "CAR", "CENTER", "OFFPPM", "APODQ1", "APODQ2", "APODQ3", "LB", "GB", "GOFF", "C1",
                "UNITS",
                "APODCODE", "ZF",
                "X1", "XN", "FTSIZE", "TDSIZE"};
        System.out.println("ndim " + nDim);
        for (String par : dimPars) {
            System.out.printf("%-10s", par + ":");
            for (int i = 0; i < nDim; i++) {
                System.out.printf("%12s", FIELDS.getString(header, i, par));
            }
            System.out.println("");
        }
        for (String par : pars) {
            System.out.printf("%-10s", par + ":");
            System.out.printf("%12s", FIELDS.getString(header, par));
            System.out.println("");
        }
    }
}
