/*
 * NMRFx: A Program for Processing NMR Data
 * Copyright (C) 2004-2022 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.datasets.vendor.rs2d;

import com.nanalysis.spinlab.dataset.Header;
import com.nanalysis.spinlab.dataset.HeaderParser;
import com.nanalysis.spinlab.dataset.HeaderWriter;
import com.nanalysis.spinlab.dataset.enums.NumberType;
import com.nanalysis.spinlab.dataset.enums.Order;
import com.nanalysis.spinlab.dataset.enums.Parameter;
import com.nanalysis.spinlab.dataset.enums.Unit;
import com.nanalysis.spinlab.dataset.values.ListNumberValue;
import com.nanalysis.spinlab.dataset.values.NumberValue;
import com.nanalysis.spinlab.dataset.values.Value;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.datasets.DatasetLayout;
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
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nanalysis.spinlab.dataset.enums.Parameter.*;
import static org.nmrfx.processor.datasets.vendor.rs2d.XmlUtil.readDocument;
import static org.nmrfx.processor.datasets.vendor.rs2d.XmlUtil.writeDocument;

/**
 * RS2D data support.
 */
public class RS2DData implements NMRData {
    public static final DatasetType DATASET_TYPE = DatasetType.SPINit;
    public static final String DATA_FILE_NAME = "data.dat";
    public static final String HEADER_FILE_NAME = "header.xml";
    public static final String SERIES_FILE_NAME = "Serie.xml";
    public static final String NUS_SCHEDULE_FILENAME = "nus-schedule.txt";
    public static final String PROC_DIR = "Proc";

    private static final int MAXDIM = 4;

    private static final Logger log = LoggerFactory.getLogger(RS2DData.class);


    private final String fpath;
    private FileChannel fc = null;
    private Header header;
    private Document seriesDocument;

    File nusFile;

    int nDim = 0;
    private final int[] tdsize = new int[MAXDIM];
    private final Double[] Ref = new Double[MAXDIM];
    private final Double[] Sw = new Double[MAXDIM];
    private final Double[] Sf = new Double[MAXDIM];
    private final AcquisitionType[] symbolicCoefs = new AcquisitionType[MAXDIM];
    private final boolean[] negateImag = new boolean[MAXDIM];
    private final String[] obsNuc = new String[MAXDIM];
    private final boolean[] complexDim = new boolean[MAXDIM];
    private final int[] groupSizes = new int[MAXDIM];
    private final double[][] f1coef = new double[MAXDIM][];
    private final String[] f1coefS = new String[MAXDIM];
    private final String[] fttype = new String[MAXDIM];
    private final String[] sfNames = new String[MAXDIM];
    private double groupDelay = 0.0;
    private SampleSchedule sampleSchedule = null;
    private final List<DatasetGroupIndex> datasetGroupIndices = new ArrayList<>();
    private DatasetType preferredDatasetType = DatasetType.NMRFX;
    private ZonedDateTime zonedDateTime = null;

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
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        this.fpath = path;
        this.nusFile = nusFile;
        openNusFile();
        openParFile(path);
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
        int numFreqDomain = 0;
        for (int i = 0; i < nDim; i++) {
            resetSW(i);
            dataset.setComplex(i, isComplex(i));
            dataset.setSf(i, getSF(i));
            dataset.setSw(i, getSW(i));
            dataset.setRefValue(i, getRef(i));
            dataset.setRefPt(i, dataset.getSizeReal(i) / 2.0);
            // State can be used to set the freq domain
            dataset.setFreqDomain(i, getState(i) == 1);
            if (dataset.getFreqDomain(i)) {
                numFreqDomain++;
            }
            String nucLabel = getTN(i);
            dataset.setNucleus(i, nucLabel);
            dataset.setLabel(i, nucLabel + (i + 1));
            dataset.syncPars(i);
        }
        dataset.setNFreqDims(numFreqDomain);
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
    public static boolean findFID(StringBuilder bpath) {
        return findFiles(bpath, false);
    }

    /**
     * Finds either fid or processed data, given a path to search for vendor-specific files and
     * directories.
     *
     * @param bpath Full path for processed data.
     * @param findDataset If True, search for processed data, otherwise search for fid.
     * @return If processed data was successfully found or not.
     */
    private static boolean findFiles(StringBuilder bpath, boolean findDataset) {
        boolean found = false;
        if (findFIDFiles(bpath.toString())) {
            found = findDataset == isValidDatasetPath(Path.of(bpath.toString()));
        } else {
            File f = new File(bpath.toString());
            File parent = f.getParentFile();
            if (findFIDFiles(parent.getAbsolutePath())) {
                String fileName = f.getName();
                if (fileName.equals(DATA_FILE_NAME)) {
                    found = findDataset == isValidDatasetPath(parent.toPath());
                }
                bpath.setLength(0);
                bpath.append(parent);
            }
        }
        return found;
    }

    /**
     * Finds processed data, given a path to search for vendor-specific files and
     * directories.
     *
     * @param bpath full path for processed data
     * @return if processed data was successfully found or not
     */
    public static boolean findData(StringBuilder bpath) {
        return findFiles(bpath, true);
    }

    @Override
    public boolean isFID() {
        return getState(0) == 0;
    }

    public int getState(int dim) {
        if (header.contains(STATE)) {
            List<Integer> states = header.get(STATE).intListValue();
            if (states != null && !states.isEmpty()) {
                return states.get(dim);
            }
        }

        log.debug("Unable to find state parameter. Setting state to FID.");
        return 0;
    }

    private static boolean findFIDFiles(String dirPath) {
        Path headerPath = Paths.get(dirPath, HEADER_FILE_NAME);
        Path dataPath = Paths.get(dirPath, DATA_FILE_NAME);
        return headerPath.toFile().exists() && dataPath.toFile().exists();
    }

    private void openNusFile() throws IOException {
        if (nusFile == null) {
            nusFile = new File(fpath + File.separator + NUS_SCHEDULE_FILENAME);
        }
        if (!nusFile.exists()) {
            return;
        }

        log.info("Opening NUS file: {}", nusFile.getPath());
        readSampleSchedule(nusFile.getPath(), true, true);
    }

    private void openParFile(String parpath) throws IOException {
        log.info("Opening RS2D file: {}", parpath);
        Path headerPath = Paths.get(parpath, HEADER_FILE_NAME);
        Path seriesPath = Paths.get(parpath, SERIES_FILE_NAME);
        try (InputStream input = Files.newInputStream(headerPath)) {
            header = new HeaderParser().parse(input);
            if (seriesPath.toFile().exists()) {
                seriesDocument = readDocument(seriesPath);
            }
            groupDelay = readGroupDelay();
            obsNucleus = header.get(OBSERVED_NUCLEUS).stringValue();
            Double obsFreq = null;
            double obsSW = getSpectralWidthAsHertz();

            if (header.contains(SAMPLE_TEMPERATURE)) {
                tempK = header.get(SAMPLE_TEMPERATURE).doubleValue();
            }

            nDim = 0;

            for (int i = 0; i < MAXDIM; i++) {
                String nucleus = header.get(NUCLEUS_PARAMS.get(i)).stringValue();
                if (nucleus.equals(obsNucleus)) {
                    obsFreq = getSF(i);
                    break;
                }
            }
            if (!isFID()) {
                List<String> dataModes = header.get(DATA_REPRESENTATION).stringListValue();
                if (!dataModes.isEmpty()) {
                    for (int i = 0; i < MAXDIM; i++) {
                        setComplex(i, dataModes.get(i).equals("COMPLEX"));
                    }
                }
            }

            for (int i = 0; i < MAXDIM; i++) {
                int dimSize = header.get(DIMENSION_PARAMS.get(i)).intValue();
                String nucleus = header.get(NUCLEUS_PARAMS.get(i)).stringValue();
                if (nucleus.equals(obsNucleus)) {
                    Sf[i] = obsFreq;
                    Sw[i] = obsSW;  // fixme  this is kluge to fis some files that have wrong SPECTRAL_WIDTH_2D
                    sfNames[i] = "";
                } else {
                    Sf[i] = getSF(i);
                    sfNames[i] = "";
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
            setZonedDateTime();

            if (isFID()) {
                setFTParams();
            }
        } catch (ParserConfigurationException | SAXException | NullPointerException ex) {
            throw new IOException(ex.getMessage());
        }
    }

    private void setZonedDateTime() {
        Value<String> dateTimeValue = header.get("ACQUISITION_DATE");
        if (dateTimeValue == null) {
            log.warn("Unable to find date. Setting date to now.");
            zonedDateTime = ZonedDateTime.now();
            return;
        }
        String dateTimeStr = dateTimeValue.stringValue();
        try {
            zonedDateTime = ZonedDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException dtpe) {
            log.warn("Unable to parse date. Setting date to now. {}", dtpe.getMessage(), dtpe);
            zonedDateTime = ZonedDateTime.now();
        }
    }

    public Header getHeader() {
        return header;
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
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    private double readGroupDelay() {
        boolean digitalFilterRemoved = header.optional(DIGITAL_FILTER_REMOVED).map(Value::booleanValue).orElse(false);
        if (header.contains(DIGITAL_FILTER_SHIFT) && !digitalFilterRemoved) {
            double digitalFilterShift = header.get(DIGITAL_FILTER_SHIFT).doubleValue();
            log.info("Using group delay: {}", digitalFilterShift);
            return digitalFilterShift;
        }

        return 0d;
    }

    private void setFTParams() {
        List<PhaseMod> phaseMod;
        if (header.contains(PHASE_MOD)) {
            phaseMod = header.get(PHASE_MOD).stringListValue()
                    .stream().map(PhaseMod::fromName)
                    .collect(Collectors.toList());
            log.info("Setting FT params from PHASE_MOD: {}", phaseMod);
        } else if (header.contains(ACQUISITION_MODE)) {
            phaseMod = header.get(ACQUISITION_MODE).stringListValue().stream()
                    .map(PhaseMod::fromAcquisitionMode)
                    .collect(Collectors.toList());
            phaseMod.set(0, PhaseMod.NONE);
            log.info("Setting FT params from ACQUISITION_MODE: {}", phaseMod);
        } else {
            phaseMod = Collections.emptyList();
            log.warn("No PHASE_MOD or ACQUISITION_MODE found, FT params will have default values.");
        }

        setFtParamsFromPhaseMod(phaseMod);
    }

    private void setFtParamsFromPhaseMod(List<PhaseMod> phaseMod) {
        // first dimension is handled separately
        complexDim[0] = true;
        exchangeXY = true;
        negatePairs = false;
        fttype[0] = "ft";
        groupSizes[0] = 2;

        // other dimensions depends on the PHASE_MOD parameter
        for (int i = 1; i < MAXDIM; i++) {
            PhaseMod mode = i < phaseMod.size() ? phaseMod.get(i) : PhaseMod.NONE;
            complexDim[i] = mode.isComplex();
            fttype[i] = mode.getFtType();
            f1coefS[i] = mode.getSymbolicCoefs();
            f1coef[i] = mode.getCoefs();
            groupSizes[i] = mode.getGroupSize();

            if (mode == PhaseMod.TPPI || mode == PhaseMod.ECHO_ANTIECHO) {
                negateImag[i] = true;
            }

            if (mode.isComplex()) {
                // size is expressed as number of complex pairs
                tdsize[i] /= mode.getGroupSize();
            }
        }
    }

    @Override
    public void setPreferredDatasetType(DatasetType datasetType) {
        this.preferredDatasetType = datasetType;
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
    public String getFilePath() {
        return fpath;
    }

    /**
     * Get a parameter string value from its name.
     * May include some implicit conversion for specific parameters (such as SPECTRAL_WIDTH when stored in ppm).
     *
     * @param name The parameter name
     * @return the string representation of its value.
     */
    @Override
    public String getPar(String name) {
        Number convertedNumber = getConvertedNumberValue(name);
        if (convertedNumber != null) {
            return convertedNumber.toString();
        }

        List<? extends Number> convertedListNumber = getConvertedListNumberValue(name);
        if (convertedListNumber != null) {
            return convertedListNumber.toString();
        }

        return header.get(name).stringValue();
    }

    /**
     * Get a parameter numeric value from its name.
     * May include some implicit conversion for specific parameters (such as SPECTRAL_WIDTH when stored in ppm).
     *
     * @param name The parameter name
     * @return the floating point representation of its value.
     */
    @Override
    public Double getParDouble(String name) {
        Number convertedNumber = getConvertedNumberValue(name);
        if (convertedNumber != null) {
            return convertedNumber.doubleValue();
        }

        List<? extends Number> convertedListNumber = getConvertedListNumberValue(name);
        if (convertedListNumber != null && convertedListNumber.size() == 1) {
            return convertedListNumber.get(0).doubleValue();
        }

        return header.get(name).doubleValue();
    }

    /**
     * Get a parameter integer value from its name.
     * May include some implicit conversion for specific parameters (such as SPECTRAL_WIDTH when stored in ppm).
     *
     * @param name The parameter name
     * @return the integer representation of its value.
     */
    @Override
    public Integer getParInt(String name) {
        Number converted = getConvertedNumberValue(name);
        if (converted != null) {
            return converted.intValue();
        }

        List<? extends Number> convertedListNumber = getConvertedListNumberValue(name);
        if (convertedListNumber != null && convertedListNumber.size() == 1) {
            return convertedListNumber.get(0).intValue();
        }

        return header.get(name).intValue();
    }

    /**
     * List all parameters and their string representation.
     * Some of them may be converted (ex: from ppm to Hertz) before being returned here.
     *
     * @return a list of vendor parameters.
     */
    @Override
    public List<VendorPar> getPars() {
        return header.all().stream()
                // it is necessary to call getPar() to convert specific parameters such as SPECTRAL_WIDTH or OFFSETs
                .map(value -> new VendorPar(value.getName(), getPar(value.getName())))
                .collect(Collectors.toList());
    }

    /**
     * Some parameter may need internal conversion before use.
     * This is needed for parameters which could be stored in Hz or ppm, but can only be used in Hz internally.
     * This includes SPECTRAL_WIDTH* and OFFSET_FREQ_* parameters.
     *
     * @param name the parameter name
     * @return a converted value, or null if there is no need for conversion.
     */
    private Number getConvertedNumberValue(String name) {
        boolean needsConversion = SW_PARAMS.stream().anyMatch(p -> p.name().equals(name))
                || OFFSET_FREQ_PARAMS.stream().anyMatch(p -> p.name().equals(name));
        return needsConversion ? getParameterAsHertz(name) : null;
    }

    /**
     * Some parameter may need internal conversion before use.
     * This is needed for parameters which could be stored in Hz or ppm, but can only be used in Hz internally.
     * This includes only SR (Spectral Reference).
     *
     * @param name the parameter name
     * @return a converted list of values, or null if there is no need for conversion.
     */
    private List<? extends Number> getConvertedListNumberValue(String name) {
        boolean needsConversion = SR.name().equals(name);
        return needsConversion ? getParameterAsHertzList(name) : null;
    }

    @Override
    public String getFTType(int iDim) {
        return fttype[iDim];
    }

    @Override
    public int getNVectors() {
        int num = 1;
        for (int i = 1; i < getNDim(); i++) {
            num *= getSize(i) * (isComplex(i) ? 2 : 1);
        }
        return num;
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
        return header.optional(SOLVENT).map(Value::stringValue).orElse("");
    }

    @Override
    public double getTempK() {
        return tempK;
    }

    @Override
    public String getSequence() {
        return header.optional(SEQUENCE_NAME).map(Value::stringValue).orElse("");
    }

    @Override
    public double getSF(int iDim) {
        // try cache value first
        if (Sf[iDim] != null) {
            return Sf[iDim];
        }

        // otherwise, read from header
        Optional<Value<?>> value = header.optional(BASE_FREQ_PARAMS.get(iDim));
        if (value.isPresent()) {
            double baseFreq = value.get().doubleValue();
            double sf = (baseFreq + getOffsetAsHertz(iDim)) / 1.0e6;
            Sf[iDim] = sf;
            return sf;
        }

        // fallback default value
        return 1.0;
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
        // try cache value first
        if (Sw[iDim] != null) {
            return Sw[iDim];
        }

        // otherwise, read from header
        Parameter swParam = SW_PARAMS.get(iDim);
        if (header.contains(swParam)) {
            double sw = getParameterAsHertz(swParam);
            Sw[iDim] = sw;
            return sw;
        }

        // fallback default value
        return 1.0;
    }

    @Override
    public void setSW(int dim, double value) {
        Sw[dim] = value;
    }

    @Override
    public void resetSW(int dim) {
        Sw[dim] = null;
    }

    private double getSpectralWidthAsHertz() {
        return getParameterAsHertz(SPECTRAL_WIDTH);
    }

    private double getOffsetAsHertz(int iDim) {
        return getParameterAsHertz(OFFSET_FREQ_PARAMS.get(iDim));
    }

    private double getParameterAsHertz(Parameter param) {
        return header.<NumberValue>get(param).getValueAs(Unit.Hertz, header);
    }

    private double getParameterAsHertz(String paramName) {
        return header.<NumberValue>get(paramName).getValueAs(Unit.Hertz, header);
    }

    private List<Double> getParameterAsHertzList(Parameter param) {
        return header.<ListNumberValue>get(param).getValueAs(Unit.Hertz, header);
    }

    private List<Double> getParameterAsHertzList(String paramName) {
        return header.<ListNumberValue>get(paramName).getValueAs(Unit.Hertz, header);
    }

    @Override
    public double getRef(int iDim) {
        // try cache value first
        if (Ref[iDim] != null) {
            return Ref[iDim];
        }

        // otherwise, read from header if present
        Optional<List<Double>> srValues = header.contains(SR) ? Optional.of(getParameterAsHertzList(SR)) : Optional.empty();
        Optional<Double> value = srValues.filter(list -> list.size() > iDim).map(list -> list.get(iDim));
        if (value.isPresent()) {
            double offset = getOffsetAsHertz(iDim);
            double sf = getSF(iDim);
            double sr = value.get();
            Ref[iDim] = (sr + offset) / (sf - offset / 1.0e6);
            return Ref[iDim];
        }

        // fallback default value
        return 1.0;
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
        return getSize(dim) / 2.0;
    }

    @Override
    public String getTN(int iDim) {
        if (obsNuc[iDim] != null) {
            return obsNuc[iDim];
        }

        String tn = header.optional(NUCLEUS_PARAMS.get(iDim)).map(Value::stringValue).orElse("");
        if (!tn.isEmpty()) {
            obsNuc[iDim] = tn;
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
    public int getGroupSize(int dim) {
        return groupSizes[dim];
    }

    @Override
    public boolean getNegateImag(int iDim) {
        return negateImag[iDim];
    }

    @Override
    public boolean getNegatePairs(int dim) {
        return "negate".equals(getFTType(dim));
    }

    @Override
    public double[] getCoefs(int iDim) {
        return f1coef[iDim];
    }

    @Override
    public String getSymbolicCoefs(int iDim) {
        return f1coefS[iDim];
    }

    public void setUserSymbolicCoefs(int iDim, AcquisitionType coefs) {
        symbolicCoefs[iDim] = coefs;
    }

    public AcquisitionType getUserSymbolicCoefs(int iDim) {
        return symbolicCoefs[iDim];
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
        return new FPMult();
    }

    @Override
    public LPParams getLPParams(int iDim) {
        return new LPParams();
    }

    @Override
    public String[] getSFNames() {
        String[] names = new String[nDim];
        System.arraycopy(sfNames, 0, names, 0, nDim);
        return names;
    }

    @Override
    public String[] getSWNames() {
        return SW_PARAMS.stream().map(Enum::name).toArray(String[]::new);
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
        dvec.setGroupDelay(groupDelay);
        if (dvec.isComplex()) {
            if (dvec.useApache()) {
                readVector(iVec, dvec.getCvec());
            } else {
                readVector(iVec, dvec.rvec, dvec.ivec);
            }
        } else {
            readVector(iVec, dvec.rvec);
            // cannot dspPhase
        }
        dvec.dwellTime = 1.0 / getSW(0);
        dvec.centerFreq = getSF(0);

        dvec.setRefValue(getRef(0));
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
        int nPer = getGroupSize(iDim);
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
            int skips = fileIndex * stride + xCol * 4 * 2;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf, vecIndex * 4 * nPer, 4 * nPer);
            int nread = fc.read(buf, skips);
            if (nread != 4 * nPer) {
                log.warn("Could not read requested bytes");
                if (fc != null) {
                    try {
                        fc.close();
                    } catch (IOException ex) {
                        log.warn(ex.getMessage(), ex);
                    }
                }
            }
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
            log.warn(e.getMessage(), e);
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    log.warn(ex.getMessage(), ex);
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

    @Override
    public List<DatasetGroupIndex> getSkipGroups() {
        return datasetGroupIndices;
    }

    @Override
    public DatasetType getPreferredDatasetType() {
        return preferredDatasetType;
    }

    @Override
    public long getDate() {
        return zonedDateTime.toEpochSecond();
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
        byte[] array = vec.toFloatBytes(ByteOrder.BIG_ENDIAN);
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
                byte[] array = vec.toFloatBytes(ByteOrder.BIG_ENDIAN);
                fOut.write(array);
            } else {
                int[] sizes = new int[dataset.getNDim() - 1];
                for (int i = 1; i < dataset.getNDim(); i++) {
                    sizes[nDim - i - 1] = dataset.getSizeReal(i);
                }
                Vec vec = new Vec(dataset.getSizeReal(0), dataset.getComplex(0));
                MultidimensionalCounter counter = new MultidimensionalCounter(sizes);
                var counterIterator = counter.iterator();
                int[] pt = new int[sizes.length];
                while (counterIterator.hasNext()) {
                    counterIterator.next();
                    int[] counts = counterIterator.getCounts();
                    for (int i = 0; i < counts.length; i++) {
                        pt[i] = counts[counts.length - i - 1];
                    }
                    int lastRow = sizes[sizes.length - 1] - 1;
                    pt[0] = lastRow - pt[0];
                    writeRow(dataset, vec, pt, fOut);
                }
            }
        }
    }

    public void setHeaderMatrixDimensions(Dataset dataset) throws XPathExpressionException {
        for (int iDim = 0; (iDim < RS2DData.MAXDIM) && (iDim < dataset.getNDim()); iDim++) {
            Parameter parameter = DIMENSION_PARAMS.get(iDim);
            if (!header.contains(parameter)) {
                header.put(new NumberValue(parameter.name(), 1));
            }

            NumberValue value = header.get(parameter);
            value.setValue(dataset.getSizeReal(iDim));
        }
    }

    public void setHeaderPhases(Dataset dataset) throws XPathExpressionException {
        if (!header.contains(PHASE_0)) {
            header.put(new ListNumberValue(PHASE_0.name(), Collections.emptyList()));
        }
        if (!header.contains(PHASE_1)) {
            header.put(new ListNumberValue(PHASE_1.name(), Collections.emptyList()));
        }

        List<Number> phase0Values = new ArrayList<>();
        List<Number> phase1Values = new ArrayList<>();
        for (int i = 0; i < dataset.getNDim(); i++) {
            phase0Values.add(dataset.getPh0(i));
            phase1Values.add(dataset.getPh1(i));
        }

        header.<ListNumberValue>get(PHASE_0).setValue(phase0Values);
        header.<ListNumberValue>get(PHASE_1).setValue(phase1Values);
    }

    public void setHeaderState(Dataset dataset) {
        if (!header.contains(STATE)) {
            header.put(new ListNumberValue(STATE.name(), Collections.emptyList()));
            ListNumberValue stateParam = header.get(STATE);
            stateParam.setUuid(UUID.randomUUID().toString());
            stateParam.setNumberEnum(NumberType.Integer);
            stateParam.setMinValue(Integer.MIN_VALUE);
            stateParam.setMaxValue(Integer.MAX_VALUE);
            Order order = Stream.of(Order.values()).filter(o -> o.toString().equals(dataset.getNDim() + "D")).findFirst().orElse(Order.Unknown);
            stateParam.setOrder(order);
        }
        List<Number> stateValues = new ArrayList<>(Collections.nCopies(4, 0));
        for (int i = 0; i < dataset.getNDim(); i++) {
            int state = dataset.getFreqDomain(i) ? 1 : 0;
            stateValues.set(i, state);
        }
        header.<ListNumberValue>get(STATE).setValue(stateValues);

    }

    public static boolean isValidDatasetPath(Path procNumPath) {
        return StringUtils.isNumeric(procNumPath.getFileName().toString())
                && procNumPath.getParent().getFileName().toString().equals(PROC_DIR);
    }

    public Path saveDataset(Dataset dataset) throws IOException {
        File file = dataset.getFile();
        try {
            setHeaderMatrixDimensions(dataset);
            setHeaderState(dataset);
            setHeaderPhases(dataset);
        } catch (XPathExpressionException e) {
            throw new IOException(e.getMessage());
        }
        Path procNumPath = file.getParentFile().toPath();
        writeOutputFile(dataset, procNumPath);
        return procNumPath;
    }

    public void writeOutputFile(Dataset dataset, Path procNumPath) throws IOException {
        if (!isValidDatasetPath(procNumPath)) {
            throw new IllegalArgumentException("Invalid Spinit Path " + procNumPath);
        }

        Files.createDirectories(procNumPath);
        File dataFile = procNumPath.resolve(DATA_FILE_NAME).toFile();
        File headerFile = procNumPath.resolve(HEADER_FILE_NAME).toFile();
        File seriesFile = procNumPath.resolve(SERIES_FILE_NAME).toFile();
        saveToRS2DFile(dataset, dataFile.toString());

        try (OutputStream output = new FileOutputStream(headerFile)) {
            new HeaderWriter().writeXml(header, output);
            if (seriesDocument != null) {
                writeDocument(seriesDocument, seriesFile);
            }
        } catch (TransformerException | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }
}
