/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.vendor;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.datasets.DatasetLayout;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.parameters.FPMult;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.LPParams;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author brucejohnson
 */
public class RS2DData implements NMRData {
    public static final String DATASET_TYPE = "SPINit";
    public static final String DATA_FILE_NAME = "data.dat";
    public static final String HEADER_FILE_NAME = "header.xml";
    public static final String SERIES_FILE_NAME = "Serie.xml";
    static final String PROC_DIR = "Proc";
    static final String BASE_FREQ_PAR = "BASE_FREQ_";
    static final Logger LOGGER = Logger.getLogger(RS2DData.class.getCanonicalName());

    static final int MAXDIM = 4;

    private final String fpath;
    private FileChannel fc = null;
    private Document headerDocument;
    private Document seriesDocument;

    private HashMap<String, List<String>> parMap = null;
    File nusFile;

    int nDim = 0;
    private final int[] tdsize = new int[MAXDIM];
    private final Double[] Ref = new Double[MAXDIM];
    private final Double[] Sw = new Double[MAXDIM];
    private final Double[] Sf = new Double[MAXDIM];
    private final boolean[] negateImag = new boolean[MAXDIM];
    private final String[] obsNuc = new String[MAXDIM];
    private final boolean[] complexDim = new boolean[MAXDIM];
    private final double[][] f1coef = new double[MAXDIM][];
    private final String[] f1coefS = new String[MAXDIM];
    private final String[] fttype = new String[MAXDIM];
    private final String[] sfNames = new String[MAXDIM];
    private double groupDelay = 0.0;
    private SampleSchedule sampleSchedule = null;

    private String[] acqOrder;
    int nvectors = 1;
    int np = 1;
    int tbytes = 1;
    boolean exchangeXY = false;
    boolean negatePairs = false;
    String obsNucleus = "";
    double scale = 1.0;
    double tempK = 298.15;

    public RS2DData(String path, File nusFile) throws IOException {
        this(path, nusFile, false);
    }

    public RS2DData(String path, File nusFile, boolean processed) throws IOException {
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        this.fpath = path;
        this.nusFile = nusFile;
        openParFile(path, processed);
        openDataFile(path);

    }

    public Dataset toDataset(String datasetName) throws IOException {
        File file = new File(fpath);
        Path path;
        if (file.isDirectory()) {
            path = Paths.get(file.getAbsolutePath(), DATA_FILE_NAME);
        } else {
            path = file.toPath();
        }

        DatasetLayout layout = new DatasetLayout(nDim);
        for (int i = 0; i < nDim; i++) {
            if (i == 0) {
                layout.setSize(i, 2 * getSize(i));
                layout.setBlockSize(i, 2 * getSize(0));
            } else {
                layout.setSize(i, getSize(i));
                layout.setBlockSize(i, 1);
            }
        }
        layout.dimDataset();
        if (datasetName == null) {
            datasetName = suggestName(path.toFile());
        }
        Dataset dataset = new Dataset(path.toString(), datasetName, layout,
                false, ByteOrder.BIG_ENDIAN, 0);
        dataset.newHeader();
        for (int i = 0; i < nDim; i++) {
            dataset.setSf(i, getSF(i));
            dataset.setSw(i, getSW(i));
            dataset.setRefValue(i, getRef(i));
            dataset.setRefPt(i, dataset.getSizeReal(i));
            dataset.setComplex(i, i == 0);
            dataset.setFreqDomain(i, true);
            String nucLabel = getTN(i);
            dataset.setNucleus(i, nucLabel);
            dataset.setLabel(i, nucLabel + (i + 1));
            dataset.syncPars(i);
        }
        if (nDim > 1) {
            dataset.setAxisReversed(1, true);
        }
        dataset.setScale(scale);
        dataset.setDataType(0);
        return dataset;
    }

    public String suggestName(File file) {
        if (file.isDirectory()) {
            file = Paths.get(file.getAbsolutePath(), DATA_FILE_NAME).toFile();
        }

        File procNumFile = file.getParentFile();
        File numFile = procNumFile.getParentFile().getParentFile();
        File rootFile = numFile.getParentFile();
        String rootName = rootFile != null ? rootFile.getName() : "";
        rootName = rootName.replace(" ", "_");
        return rootName + "_" + numFile.getName() +
                "_" + procNumFile.getName();

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
                if (fileName.equals(DATA_FILE_NAME)) {
                    found = true;
                }
                bpath.setLength(0);
                bpath.append(parent);
            }
        }
        return found;
    }

    public static Optional<Path> getLastProcPath(Path datasetDir) {
        return findLastProcId(datasetDir).stream()
                .mapToObj(id -> datasetDir.resolve(PROC_DIR).resolve(String.valueOf(id)))
                .findFirst();
    }

    public static List<Integer> listProcIds(Path datasetDir) {
        try {
            return Files.list(datasetDir.resolve(PROC_DIR))
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(StringUtils::isNumeric)
                    .mapToInt(Integer::parseInt)
                    .sorted()
                    .boxed()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to list processed directories", e);
            return Collections.emptyList();
        }
    }

    public static int findNextProcId(Path datasetDir) {
        OptionalInt max = findLastProcId(datasetDir);
        return max.isPresent() ? max.getAsInt() + 1 : 0;
    }

    public static Path findNextProcPath(Path datasetDir) {
        return datasetDir.resolve(PROC_DIR).resolve(String.valueOf(findNextProcId(datasetDir)));
    }

    /**
     * Get the last process id for this dataset.
     * Returns empty (not null) if no valid process dir was found.
     */
    public static OptionalInt findLastProcId(Path datasetDir) {
        try {
            return Files.list(datasetDir.resolve(PROC_DIR))
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(StringUtils::isNumeric)
                    .mapToInt(Integer::parseInt)
                    .max();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to list processed directories", e);
            return OptionalInt.empty();
        }
    }

    private static boolean findFIDFiles(String dirPath) {
        Path headerPath = Paths.get(dirPath, HEADER_FILE_NAME);
        Path dataPath = Paths.get(dirPath, DATA_FILE_NAME);
        return headerPath.toFile().exists() && dataPath.toFile().exists();
    }

    private Document readDocument(Path filePath) throws ParserConfigurationException, IOException, SAXException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(filePath.toFile());
    }

    private void openParFile(String parpath, boolean processed) throws IOException {
        parMap = new LinkedHashMap<>(200);
        Path headerPath = Paths.get(parpath, HEADER_FILE_NAME);
        Path seriesPath = Paths.get(parpath, SERIES_FILE_NAME);
        try {
            headerDocument = readDocument(headerPath);
            if (seriesPath.toFile().exists()) {
                seriesDocument = readDocument(seriesPath);
            }
            var parNames = getParams(headerDocument);
            for (String parName : parNames) {
                List<String> parValues = getParamValue(headerDocument, parName);
                parMap.put(parName, parValues);
            }
            groupDelay = 0.0;
            obsNucleus = getPar("OBSERVED_NUCLEUS");
            Double obsFreq = null;
            Double obsSW = getParDouble("SPECTRAL_WIDTH");
            tempK = getParDouble("SAMPLE_TEMPERATURE");
            String baseSFName = "";
            nDim = 0;

            for (int i = 0; i < MAXDIM; i++) {
                String nucleus = getPar("NUCLEUS_" + (i + 1));
                if (nucleus.equals(obsNucleus)) {
                    obsFreq = getParDouble(BASE_FREQ_PAR + (i + 1)) / 1.0e6;
                    baseSFName = BASE_FREQ_PAR + (i + 1);
                    break;
                }
            }
            if (processed) {
                List<String> dataModes = parMap.get("DATA_REPRESENTATION");
                if (!dataModes.isEmpty()) {
                    for (int i = 0; i < MAXDIM; i++) {
                        setComplex(i, dataModes.get(i).equals("COMPLEX"));
                    }
                }
            }

            for (int i = 0; i < MAXDIM; i++) {
                int dimSize;
                if (processed) {
                    dimSize = getParInt("MATRIX_DIMENSION_" + (i + 1) + "D");
                } else {
                    dimSize = getParInt("ACQUISITION_MATRIX_DIMENSION_" + (i + 1) + "D");
                }
                String nucleus = getPar("NUCLEUS_" + (i + 1));
                if (nucleus.equals(obsNucleus)) {
                    Sf[i] = obsFreq;
                    Sw[i] = obsSW;  // fixme  this is kluge to fis some files that have wrong SPECTRAL_WIDTH_2D
                    sfNames[i] = baseSFName;
                } else {
                    Double baseFreq = getParDouble(BASE_FREQ_PAR + (i + 1));
                    Sf[i] = baseFreq / 1.0e6;
                    sfNames[i] = BASE_FREQ_PAR + (i + 1);
                }
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
            if (!processed) {
                setFTPars();
            }
        } catch (ParserConfigurationException | SAXException | XPathExpressionException | NullPointerException ex) {
            throw new IOException(ex.getMessage());
        }
    }
    public Document getHeaderDocument() {
        return headerDocument;
    }
    public Document getSeriesDocument() {
        return seriesDocument;
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

    static List<Node> getParamNode(Document xml, String paramName) throws XPathExpressionException {
        if (!paramName.contains("'")) {
            paramName = "'" + paramName + "'";
        } else if (!paramName.contains("\"")) {
            paramName = "\"" + paramName + "\"";
        } else {
            paramName = "concat('" + paramName.replace("'", "',\"'\",'") + "')";
        }
        String expression = "/header/params/entry/key[text()=" + paramName + "]/../value/value";
        XPath path = XPathFactory.newInstance().newXPath();
        XPathEvaluationResult<?> result = path.evaluateExpression(expression, xml.getDocumentElement());
        List<Node> nodeResult = new ArrayList<>();
        switch (result.type()) {
            case NODESET:
                XPathNodes nodes = (XPathNodes) result.value();
                for (Node node : nodes) {
                    nodeResult.add(node);
                }
                break;
            case NODE:
                Node node = (Node) result.value();
                nodeResult.add(node);
        }
        return nodeResult;
    }

    static List<String> getParamValue(Document xml, String paramName) throws XPathExpressionException {
        if (!paramName.contains("'")) {
            paramName = "'" + paramName + "'";
        } else if (!paramName.contains("\"")) {
            paramName = "\"" + paramName + "\"";
        } else {
            paramName = "concat('" + paramName.replace("'", "',\"'\",'") + "')";
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
            path = Paths.get(file.getAbsolutePath(), DATA_FILE_NAME);
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
        exchangeXY = true;
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
                    negateImag[i] = true;
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
    public String getFTType(int iDim) {
        return fttype[iDim];
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
            if ((dpar = getParDouble(BASE_FREQ_PAR + (iDim + 1))) != null) {
                sf = dpar / 1.0e6;
                Sf[iDim] = sf;
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
    public void setComplex(int dim, boolean value) {
        complexDim[dim] = value;
    }

    @Override
    public boolean getNegateImag(int iDim) {
        return negateImag[iDim];
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
        System.arraycopy(sfNames, 0, names, 0, nDim);
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
        return names.toArray(new String[0]);
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
    }

    private void copyFloatVecData(byte[] dataBuf, Complex[] cdata) {
        FloatBuffer dBuffer = ByteBuffer.wrap(dataBuf).asFloatBuffer();
        double px, py;
        for (int j = 0; j < np; j += 2) {
            px = dBuffer.get(j);
            py = dBuffer.get(j + 1);
            if (exchangeXY) {
                cdata[j / 2] = new Complex(py / scale, px / scale);
            } else {
                cdata[j / 2] = new Complex(px / scale, -(double) py / scale);
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
                rdata[j / 2] = py / scale;
                idata[j / 2] = px / scale;
            } else {
                rdata[j / 2] = px / scale;
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
                data[j] = py / scale;
                data[j + 1] = px / scale;
            } else {
                data[j] = px / scale;
                data[j + 1] = -py / scale;
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
        }
        if (dvec.isComplex()) {
            if (!dvec.useApache()) {
                dvec.makeApache();
            }
            readVector(iDim, iVec + shiftAmount, dvec.getCvec());
        } else {
            readVector(iDim, iVec, dvec.rvec);
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

    public void readVector(int iDim, int iVec, double[] data) {
        int size = getSize(iDim);
        int nPer = 1;
        if (isComplex(iDim)) {
            nPer = 2;
        }
        int nPoints = size * nPer;
        byte[] dataBuf = new byte[nPoints * Float.BYTES];
        FloatBuffer floatBuffer = ByteBuffer.wrap(dataBuf).asFloatBuffer();
        for (int j = 0; j < nPoints; j++) {
            floatBuffer.put(j, 0);
        }
        int stride = tbytes;
        for (int i = 1; i < iDim; i++) {
            stride *= getSize(i) * nPer;
        }

        for (int i = 0; i < nPoints; i++) {
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
        for (int j = 0; j < nPoints; j++) {
            double px = floatBuffer.get(j);
            data[j] = px / scale;
        }
    }
    // read value along dim
    // fixme only works for 2nd dim

    private void readValue(int iDim, int stride, int fileIndex, int vecIndex, int xCol, byte[] dataBuf) {
        try {
            int nPer = isComplex(iDim) ? 2 : 1;
            //int skips = fileIndex * tbytes + xCol * 4 * 2;
            int skips = fileIndex * stride + xCol * 4 * 2;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf, vecIndex * 4 * nPer, 4 * nPer);
            int nread = fc.read(buf, skips);
            if (nread != 4 * nPer) {
                LOGGER.log(Level.WARNING, "Could not read requested bytes");
                if (fc != null) {
                    try {
                        fc.close();
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING, ex.getMessage());
                    }
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
            int nDim = getNDim();
            int nIDim = nDim - 1;
            if ((len == nDim) || (len == nIDim)) {
                acqOrder = new String[nIDim * 2];
                int j = 0;
                if ((sampleSchedule != null) && !sampleSchedule.isDemo()) {
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
                if ((sampleSchedule != null) && !sampleSchedule.isDemo()) {
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
            System.arraycopy(newOrder, 0, this.acqOrder, 0, newOrder.length);
        }
    }

    @Override
    public String getAcqOrderShort() {
        String[] acqOrderArray = getAcqOrder();
        StringBuilder builder = new StringBuilder();
        int nDim = getNDim();
        if (acqOrderArray.length / 2 == nDim) {
            builder.append(acqOrderArray.length / 2 + 1);
        } else {
            builder.append(nDim);
        }
        for (int i = acqOrderArray.length - 1; i >= 0; i--) {
            String elem = acqOrderArray[i];
            if (elem.charAt(0) == 'p') {
                builder.append(elem.charAt(1));
            } else if (elem.charAt(0) == 'a') {
                return "";
            }
        }
        return builder.toString();
    }

    @Override
    public SampleSchedule getSampleSchedule() {
        return sampleSchedule;
    }

    @Override
    public void setSampleSchedule(SampleSchedule sampleSchedule) {
        this.sampleSchedule = sampleSchedule;
    }

    private static void writeRow(Dataset dataset, Vec vec, int[] pt, BufferedOutputStream fOut) throws IOException {
        if (dataset.getComplex(0)) {
            vec.makeComplex();
        } else {
            vec.makeReal();
        }
        dataset.readVector(vec, pt, 0);
        if (!dataset.getComplex(0)) {
            vec.makeComplex();
        }
        byte[] array = vec.toFloatBytes();
        fOut.write(array);

    }

    public static void saveToRS2DFile(Dataset dataset, String filePath) throws IOException {
        try (BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(filePath))) {
            int nDim = dataset.getNDim();
            if (dataset.getNDim() == 1) {
                Vec vec = dataset.readVector(0, 0);
                if (!vec.isComplex()) {
                    vec.hft();
                }
                byte[] array = vec.toFloatBytes();
                fOut.write(array);
            } else {
                int[] sizes = new int[dataset.getNDim() - 1];
                for (int i = 1; i < dataset.getNDim(); i++) {
                    sizes[i - 1] = dataset.getSizeReal(i);
                }
                Vec vec = new Vec(dataset.getSizeReal(0), dataset.getComplex(0));

                int[] pt = new int[dataset.getNDim() - 1];
                pt[0] = dataset.getSizeReal(0);

                while (true) {
                    int nRows = sizes[0];
                    if (dataset.getAxisReversed(1)) {
                        for (int k = 0; k < nRows; k++) {
                            pt[0] = k;
                            writeRow(dataset, vec, pt, fOut);
                        }
                    } else {
                        for (int k = nRows - 1; k >= 0; k--) {
                            pt[0] = k;
                            writeRow(dataset, vec, pt, fOut);
                        }
                    }

                    boolean done = true;
                    for (int j = 2; j < nDim; j++) {
                        pt[j - 1]++;
                        if (pt[j - 1] >= sizes[j - 1]) {
                            if (j == (nDim - 1)) {
                                break;
                            }
                            pt[j - 1] = 0;
                        } else {
                            done = false;
                            break;
                        }
                    }
                    if (done) {
                        break;
                    }
                }
            }
        }
    }

    public void setParam(String paramName, String paramValue) throws XPathExpressionException {
        var nodes = RS2DData.getParamNode(headerDocument, paramName);
        if (!nodes.isEmpty()) {
            nodes.get(0).setTextContent(paramValue);
        }

    }
    public void setHeaderMatrixDimensions(Dataset dataset) throws XPathExpressionException {
        for (int iDim = 1; (iDim <= RS2DData.MAXDIM) && (iDim <= dataset.getNDim()); iDim++) {
            setParam("MATRIX_DIMENSION_" + iDim + "D", String.valueOf(dataset.getSizeReal(iDim - 1)));
        }
    }
    public void setHeaderPhases(Dataset dataset) throws XPathExpressionException {
        setParam("PHASE_0", String.valueOf(dataset.getPh0(0)));
        setParam("PHASE_1", String.valueOf(dataset.getPh1(0)));
    }

    public boolean isValidDatasetPath(Path procNumPath) {
        return StringUtils.isNumeric(procNumPath.getFileName().toString())
                && procNumPath.getParent().getFileName().toString().equals(PROC_DIR);
    }

    public void writeOutputFile(Dataset dataset, Path procNumPath) throws IOException {
        if (!isValidDatasetPath(procNumPath)) {
            throw new IllegalArgumentException("Invalid Spinit Path " + procNumPath);
        }

        Files.createDirectories(procNumPath);
        File dataFile = procNumPath.resolve(DATA_FILE_NAME).toFile();
        File headerFile = procNumPath.resolve(HEADER_FILE_NAME).toFile();
        File seriesFile = procNumPath.resolve(SERIES_FILE_NAME).toFile();
        saveToRS2DFile(dataset,dataFile.toString());

        try {
            writeDocument(headerDocument, headerFile);
            if (seriesDocument != null) {
                writeDocument(seriesDocument,seriesFile);
            }
        } catch (TransformerException e) {
            throw new IOException(e);
        }
    }

    public void writeDocument(Document document, File outFile) throws TransformerException, IOException {
        DOMSource source = new DOMSource(document);
        StreamResult result =  new StreamResult(new StringWriter());
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(source, result);
        String xmlString = result.getWriter().toString();
        Files.writeString(outFile.toPath(),xmlString);
    }
}
