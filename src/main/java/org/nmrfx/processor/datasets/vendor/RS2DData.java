/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.vendor;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.processor.datasets.parameters.FPMult;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.LPParams;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author brucejohnson
 */
public class RS2DData implements NMRData {

    final static Logger LOGGER = Logger.getLogger(RS2DData.class.getCanonicalName());

    static final int MAXDIM = 4;

    private final String fpath;
    private FileChannel fc = null;
    private HashMap<String, List<String>> parMap = null;
    File nusFile = null;

    int nDim = 0;
    private final int tdsize[] = new int[MAXDIM];
    private final Double[] Ref = new Double[MAXDIM];
    private final Double[] Sw = new Double[MAXDIM];
    private final Double[] Sf = new Double[MAXDIM];
    private final String[] obsNuc = new String[MAXDIM];
    private final boolean[] complexDim = new boolean[MAXDIM];
    private final double[] f1coef[] = new double[MAXDIM][];
    private final String[] f1coefS = new String[MAXDIM];
    private final String fttype[] = new String[MAXDIM];
    private double groupDelay = 0.0;
    private SampleSchedule sampleSchedule = null;

    private String[] acqOrder;
    int receiverCount = 1;
    int nvectors = 1;
    int np = 1;
    int tbytes = 1;
    boolean exchangeXY = false;
    boolean negatePairs = false;
    double scale = 1.0;
    double tempK = 298.15;

    public RS2DData(String path, File nusFile) throws IOException {
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        this.fpath = path;
        this.nusFile = nusFile;
        openParFile(path);
        openDataFile(path);

    }

    /**
     * Finds FID data, given a path to search for vendor-specific files and
     * directories.
     *
     * @param bpath full path for FID data
     * @return if FID data was successfully found or not
     */
    protected static boolean findFID(StringBuilder bpath) {
        boolean found = false;
        if (findFIDFiles(bpath.toString())) {
            found = true;
        } else {
            File f = new File(bpath.toString());
            File parent = f.getParentFile();
            if (findFIDFiles(parent.getAbsolutePath())) {
                String fileName = f.getName();
                if (fileName.equals("data.dat")) {
                    found = true;
                }
                bpath.setLength(0);
                bpath.append(parent.toString());
            }
        }
        return found;
    }

    private static boolean findFIDFiles(String dirPath) {
        Path headerPath = Paths.get(dirPath, "header.xml");
        Path dataPath = Paths.get(dirPath, "data.dat");
        System.out.println(headerPath.toString() + " " + headerPath.toFile().exists());
        System.out.println(dataPath.toString() + " " + dataPath.toFile().exists());
        return headerPath.toFile().exists() && dataPath.toFile().exists();
    }

    private void openParFile(String parpath) throws IOException {
        parMap = new LinkedHashMap<>(200);
        Path path = Paths.get(parpath, "header.xml");
        Document xml;
        try {
            xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
            var parNames = getParams(xml);
            for (String parName : parNames) {
                List<String> parValues = getParamValue(xml, parName);
                parMap.put(parName, parValues);
            }
            tempK = getParDouble("SAMPLE_TEMPERATURE") + 273.15;
            nDim = 0;

            for (int i = 0; i < MAXDIM; i++) {
                int dimSize = getParInt("MATRIX_DIMENSION_" + (i + 1) + "D");
                tdsize[i] = dimSize;
                if (dimSize > 1) {
                    nDim++;
                    if (i > 0) {
                        nvectors *= dimSize;
                    } else {
                        np = 2 * dimSize;
                        tbytes = dimSize * 2 * Float.BYTES;
                    }
                }
            }
            setFTPars();
        } catch (ParserConfigurationException | SAXException | XPathExpressionException | NullPointerException ex) {
            throw new IOException(ex.getMessage());
        }
    }

    private List<String> getParams(Document xml) throws XPathExpressionException {
        String expression = "/header/params/entry/key/text()";
        XPath path = XPathFactory.newInstance().newXPath();
        XPathExpression expr = path.compile(expression);
        NodeList nodes = (NodeList) expr.evaluate(xml, XPathConstants.NODESET);
        var nodeValues = new ArrayList<String>();
        for (int i = 0; i < nodes.getLength(); i++) {
            nodeValues.add(nodes.item(i).getNodeValue());
        }
        return nodeValues;
    }

    private static List<String> getParamValue(Document xml, String paramName) throws XPathExpressionException {
        if (!paramName.contains("'")) {
            paramName =  "'" + paramName + "'";
        } else if (!paramName.contains("\"")) {
            paramName =  "\"" + paramName + "\"";
        } else {
            paramName =   "concat('" + paramName.replace("'", "',\"'\",'") + "')";
        }
        String expression = "/header/params/entry/key[text()=" + paramName + "]/../value/value";
        XPath path = XPathFactory.newInstance().newXPath();
        XPathEvaluationResult<?> result = path.evaluateExpression(expression, xml.getDocumentElement());
        var parList = new ArrayList<String>();
        switch (result.type()) {
            case NODESET:
                XPathNodes nodes = (XPathNodes) result.value();
                for (Node node : nodes) {
                    parList.add(node.getTextContent());
                }
                break;
            case NODE:
                Node node = (Node) result.value();
                parList.add(node.getTextContent());
                break;
            default:
                System.out.println("default");
        }
        return parList;
    }

    private void openDataFile(String datapath) {
        File file = new File(datapath);
        Path path;
        if (file.isDirectory()) {
            path = Paths.get(file.getAbsolutePath(), "data.dat");
        } else {
            path = file.toPath();
        }
        try {
            fc = FileChannel.open(path, StandardOpenOption.READ);
        } catch (IOException ex) {
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void setFTPars() {
        // see bruker.tcl line 781-820
        complexDim[0] = true;  // same as exchange really
        exchangeXY = false;
        negatePairs = false;
        fttype[0] = "ft";
        List<String> acqModes = parMap.get("ACQUISITION_MODE");
        if (!acqModes.isEmpty()) {
            if (acqModes.get(0).equals("REAL")) {
                fttype[0] = "rft";
                complexDim[0] = false;
                exchangeXY = false;
                negatePairs = true;
            }
        }
        for (int i = 1; i < nDim; i++) {
            String fnmode = acqModes.size() > i ? acqModes.get(i) : "COMPLEX";
            complexDim[i] = true;
            fttype[i] = "ft";
            switch (fnmode) {
                case "REAL":
                    complexDim[i] = false;
                    fttype[i] = "rft";
                    f1coefS[i] = "real";
                    break;
                case "TPPI":
                    complexDim[i] = false;
                    fttype[i] = "rft";
                    f1coefS[i] = "real";
                    break;
                case "COMPLEX": // f1coef[i-1] = "1 0 0 0 0 0 1 0";
                    f1coef[i] = new double[]{1, 0, 0, 0, 0, 0, 1, 0};
                    complexDim[i] = true;
                    fttype[i] = "negate";
                    f1coefS[i] = "hyper";
                    tdsize[i] = tdsize[i] / 2;
                    break;
                case "ECHO_ANTIECHO": // f1coef[i-1] = "1 0 -1 0 0 1 0 1";
                    f1coef[i] = new double[]{1, 0, -1, 0, 0, -1, 0, -1};
                    f1coefS[i] = "echo-antiecho";
                    tdsize[i] = tdsize[i] / 2;
                    break;
                default:
                    f1coef[i] = new double[]{1, 0, 0, 1};
                    f1coefS[i] = "sep";
                    //tdsize[i] = tdsize[i] * 2;
                    tdsize[i] = tdsize[i] / 2;
                    break;
            }
        }
        for (int j = 0; j < tdsize.length; j++) {
            if (tdsize[j] == 0) {
                tdsize[j] = 1;
                complexDim[j] = false;
            }
        }
    }

    @Override
    public void close() {
        try {
            fc.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
        }
    }

    @Override
    public String getFilePath() {
        return fpath;
    }

    String getMultiPar(String parName) {
        if (!parMap.containsKey(parName)) {
            return null;
        }
        List<String> values = parMap.get(parName);
        StringBuilder sBuilder = new StringBuilder();
        if (!values.isEmpty()) {
            sBuilder.append(values.get(0));
        }
        for (int i = 1; i < values.size(); i++) {
            sBuilder.append(",").append(values.get(i));
        }
        return sBuilder.toString();

    }

    @Override
    public String getPar(String parName) {
        if (parMap == null) {
            return null;
        } else {
            return getMultiPar(parName);
        }
    }

    @Override
    public Double getParDouble(String parname) {
        if ((parMap == null) || (getPar(parname) == null)) {
            return null;
//            throw new NullPointerException();
        } else {
            return Double.parseDouble(getPar(parname));
        }
    }

    public List<Double> getParDoubleList(String parname) {
        var values = new ArrayList<Double>();
        if ((parMap == null) || !parMap.containsKey(parname)) {
            return values;
        } else {
            var strValues = parMap.get(parname);
            for (String strValue : strValues) {
                try {
                    values.add(Double.parseDouble(strValue));
                } catch (NumberFormatException nfE) {
                    values.add(null);
                }
            }
        }
        return values;
    }

    @Override
    public Integer getParInt(String parname) {
        if ((parMap == null) || (getPar(parname) == null)) {
            return null;
        } else {
            return Integer.parseInt(getPar(parname));
        }
    }

    @Override
    public List<VendorPar> getPars() {
        List<VendorPar> vendorPars = new ArrayList<>();
        for (String key : parMap.keySet()) {
            String value = getMultiPar(key);
            vendorPars.add(new VendorPar(key, value));
        }
        return vendorPars;
    }

    @Override
    public int getNVectors() {
        return nvectors;
    }

    @Override
    public int getNPoints() {
        return np / 2;
    }

    @Override
    public int getNDim() {
        return nDim;
    }

    @Override
    public int getSize(int iDim) {
        return tdsize[iDim];
    }

    @Override
    public void setSize(int dim, int size) {
        tdsize[dim] = size;
    }

    @Override
    public String getSolvent() {
        return getPar("SOLVENT");
    }

    @Override
    public double getTempK() {
        return tempK;
    }

    @Override
    public String getSequence() {
        return getPar("SEQUENCE_NAME");
    }

    @Override
    public double getSF(int iDim) {
        double sf = 1.0;
        if (Sf[iDim] != null) {
            sf = Sf[iDim];
        } else {
            Double dpar;
            if ((dpar = getParDouble("BASE_FREQ_," + (iDim + 1))) != null) {
                sf = dpar;
            }
        }
        return sf;
    }

    @Override
    public void setSF(int dim, double value) {
        Sf[dim] = value;
    }

    @Override
    public void resetSF(int dim) {
        Sf[dim] = null;
    }

    @Override
    public double getSW(int iDim) {
        double sw = 1.0;
        if (Sw[iDim] != null) {
            sw = Sw[iDim];
        } else {
            Double dpar;
            String name;
            switch (iDim) {
                case 0:
                    name = "SPECTRAL_WIDTH";
                    break;
                case 1:
                    name = "SPECTRAL_WIDTH_2D";
                    break;
                default:
                    name = "SPECTRAL_WiDTH_" + (iDim + 1) + "D";
            }
            if ((dpar = getParDouble(name)) != null) {
                sw = dpar;
            }
        }
        return sw;
    }

    @Override
    public void setSW(int dim, double value) {
        Sw[dim] = value;
    }

    @Override
    public void resetSW(int dim) {
        Sw[dim] = null;
    }

    @Override
    public double getRef(int iDim) {
        double ref = 1.0;
        if (Ref[iDim] != null) {
            ref = Ref[iDim];
        } else {
            var values = getParDoubleList("SR");
            for (int i = 0; i < values.size(); i++) {
                Ref[i] = values.get(i);
            }
        }
        return ref;
    }

    @Override
    public void setRef(int dim, double ref) {
        Ref[dim] = ref;
    }

    @Override
    public void resetRef(int dim) {
        Ref[dim] = null;
    }

    @Override
    public double getRefPoint(int dim) {
        return 0;
    }

    @Override
    public String getTN(int iDim) {
        String tn = "";
        if (obsNuc[iDim] != null) {
            tn = obsNuc[iDim];
        } else {
            String dpar;
            if ((dpar = getPar("NUCLEUS_" + (iDim + 1))) != null) {
                tn = dpar;
                obsNuc[iDim] = tn;
            }
        }
        return tn;
    }

    @Override
    public boolean isComplex(int dim) {
        return complexDim[dim];
    }

    @Override
    public double[] getCoefs(int iDim) {
        return f1coef[iDim];
    }

    @Override
    public String getSymbolicCoefs(int iDim) {
        return f1coefS[iDim];
    }

    @Override
    public String getVendor() {
        return "rs2d";
    }

    @Override
    public double getPH0(int dim) {
        return 0.0;
    }

    @Override
    public double getPH1(int dim) {
        return 0.0;
    }

    @Override
    public int getLeftShift(int dim) {
        return 0;
    }

    @Override
    public double getExpd(int dim) {
        return 0.5;
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
        return new FPMult(); // does not exist in Bruker params
    }

    @Override
    public LPParams getLPParams(int iDim) {
        return new LPParams(); // does not exist in Bruker params
    }

    @Override
    public String[] getSFNames() {
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            names[i] = "BASE_FREQ_" + (i + 1);
        }
        return names;
    }

    @Override
    public String[] getSWNames() {
        String[] names = new String[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            String name;
            switch (iDim) {
                case 0:
                    name = "SPECTRAL_WIDTH";
                    break;
                case 1:
                    name = "SPECTRAL_WIDTH_2D";
                    break;
                default:
                    name = "SPECTRAL_WiDTH_" + (iDim + 1) + "D";
            }
            names[iDim] = name;
        }
        return names;
    }

    @Override
    public String[] getLabelNames() {
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < nDim; i++) {
            String name = getTN(i);
            if (names.contains(name)) {
                name = name + "_" + (i + 1);
            }
            names.add(name);
        }
        return names.toArray(new String[names.size()]);
    }

    @Override
    public void readVector(int iVec, Vec dvec) {
        dvec.setGroupDelay(0);
        if (dvec.isComplex()) {
            if (dvec.useApache()) {
                readVector(iVec, dvec.getCvec());
                //fixDSP(dvec);
            } else {
                readVector(iVec, dvec.rvec, dvec.ivec);
                //fixDSP(dvec);
            }
        } else {
            readVector(iVec, dvec.rvec);
            // cannot dspPhase
        }
        dvec.dwellTime = 1.0 / getSW(0);
        dvec.centerFreq = getSF(0);

        //double delRef = (dvec.getSize() / 2 - 0) * (1.0 / dvec.dwellTime) / dvec.centerFreq / dvec.getSize();
        double delRef = ((1.0 / dvec.dwellTime) / dvec.centerFreq) / 2.0;
        dvec.refValue = getRef(0) + delRef;
        //System.out.println("zeroref " + dvec.refValue);
        //dvec.setPh0(getPH0(1));
        //dvec.setPh1(getPH1(1));
    }

    private void copyFloatVecData(byte[] dataBuf, Complex[] cdata) {
        FloatBuffer dBuffer = ByteBuffer.wrap(dataBuf).asFloatBuffer();
        double px, py;
        for (int j = 0; j < np; j += 2) {
            px = dBuffer.get(j);
            py = dBuffer.get(j + 1);
            if (exchangeXY) {
                cdata[j / 2] = new Complex((double) py / scale, (double) px / scale);
            } else {
                cdata[j / 2] = new Complex((double) px / scale, -(double) py / scale);
            }
        }
        if (negatePairs) {
            Vec.negatePairs(cdata);
        }

    }

    private void copyFloatVecData(byte[] dataBuf, double[] rdata, double[] idata) {
        FloatBuffer dBuffer = ByteBuffer.wrap(dataBuf).asFloatBuffer();
        double px, py;
        for (int j = 0; j < np; j += 2) {
            px = dBuffer.get(j);
            py = dBuffer.get(j + 1);
            if (exchangeXY) {
                rdata[j / 2] = (double) py / scale;
                idata[j / 2] = (double) px / scale;
            } else {
                rdata[j / 2] = (double) px / scale;
                idata[j / 2] = -(double) py / scale;
            }
        }
        if (negatePairs) {
            Vec.negatePairs(rdata, idata);
        }
    }

    private void copyFloatVecData(byte[] dataBuf, double[] data) {
        FloatBuffer dBuffer = ByteBuffer.wrap(dataBuf).asFloatBuffer();
        double px, py;
        for (int j = 0; j < np; j += 2) {
            px = dBuffer.get(j);
            py = dBuffer.get(j + 1);
            if (exchangeXY) {
                data[j] = (double) py / scale;
                data[j + 1] = (double) px / scale;
            } else {
                data[j] = (double) px / scale;
                data[j + 1] = -(double) py / scale;
            }
        }
        if (negatePairs) {
            Vec.negatePairs(data);
        }
    }

    @Override
    public void readVector(int iVec, Complex[] cdata) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyFloatVecData(dataBuf, cdata);
    }

    @Override
    public void readVector(int iVec, double[] rdata, double[] idata) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyFloatVecData(dataBuf, rdata, idata);
    }

    @Override
    public void readVector(int iVec, double[] data) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyFloatVecData(dataBuf, data);
    }

    @Override
    public void readVector(int iDim, int iVec, Vec dvec) {
        int shiftAmount = 0;
        if (groupDelay > 0) {
            // fixme which is correct (use ceil or not)
            //shiftAmount = (int)Math.round(Math.ceil(groupDelay));
            shiftAmount = (int) Math.round(groupDelay);
            System.out.println(iVec + " " + groupDelay + " " + shiftAmount);
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
//        double delRef = (dvec.getSize() / 2 - 0) * (1.0 / dvec.dwellTime) / dvec.centerFreq / dvec.getSize();
        double delRef = ((1.0 / dvec.dwellTime) / dvec.centerFreq) / 2.0;
        dvec.refValue = getRef(iDim) + delRef;
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
        int nPer = 1;
        if (isComplex(iDim)) {
            nPer = 2;
        }
        int nPoints = size * nPer;
        byte[] dataBuf = new byte[nPoints * Float.BYTES * 2];
        FloatBuffer floatBuffer = ByteBuffer.wrap(dataBuf).asFloatBuffer();
        for (int j = 0; j < (nPoints * 2); j++) {
            floatBuffer.put(j, 0);
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
            double px = floatBuffer.get(j);
            double py = floatBuffer.get(j + 1);
            cdata[j / 2] = new Complex(px / scale, py / scale);
        }
    }
    // read value along dim
    // fixme only works for 2nd dim

    private void readValue(int iDim, int stride, int fileIndex, int vecIndex, int xCol, byte[] dataBuf) {
        try {
            int nread = 0;
            //int skips = fileIndex * tbytes + xCol * 4 * 2;
            int skips = fileIndex * stride + xCol * 4 * 2;
            //System.out.println(fileIndex + " " + xCol + " " + (skips/4));
            ByteBuffer buf = ByteBuffer.wrap(dataBuf, vecIndex * 4 * 2, 4 * 2);
            nread = fc.read(buf, skips);
        } catch (EOFException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage());
                }
            }
        }
    }

    // read i'th data block
    private void readVecBlock(int i, byte[] dataBuf) {
        try {
            int skips = i * tbytes;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf);
            int nread = fc.read(buf, skips);
            if (nread < tbytes) // nread < tbytes, nread < np
            {
                throw new ArrayIndexOutOfBoundsException("file index " + i + " out of bounds " + nread + " " + tbytes);
            }
            //System.out.println("readVecBlock read "+nread+" bytes");
        } catch (EOFException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage());
                }
            }
        }
    }  // end readVecBlock

    @Override
    public void resetAcqOrder() {
        acqOrder = null;
    }

    @Override
    public String[] getAcqOrder() {
        if (acqOrder == null) {
            int idNDim = getNDim() - 1;
            acqOrder = new String[idNDim * 2];
            // p1,d1,p2,d2
            for (int i = 0; i < idNDim; i++) {
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
            int nIDim = nDim - 1;
            if ((len == nDim) || (len == nIDim)) {
                acqOrder = new String[nIDim * 2];
                int j = 0;
                for (int i = (len - 1); i >= 0; i--) {
                    String dimStr = s.substring(i, i + 1);
                    if (!dimStr.equals(nDim + "")) {
                        acqOrder[j] = "p" + dimStr;
                        j++;
                    }
                }
                for (int i = 0; i < nIDim; i++) {
                    acqOrder[i + nIDim] = "d" + (i + 1);
                }
            }
        } else {
            this.acqOrder = new String[newOrder.length];
            System.arraycopy(newOrder, 0, this.acqOrder, 0, newOrder.length);
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

}
