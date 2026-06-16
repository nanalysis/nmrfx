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

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.processor.datasets.AcquisitionType;
import org.nmrfx.processor.datasets.DatasetGroupIndex;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.ReferenceCalculator;
import org.nmrfx.processor.datasets.parameters.FPMult;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.LPParams;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.datasets.vendor.bruker.BrukerData;
import org.nmrfx.processor.datasets.vendor.varian.VarianData;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;
import org.nmrfx.utilities.DatasetSummary;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The <i>NMRData</i> interface contains methods for retrieving data and
 * parameters from a vendor-supplied NMR data set. The method signatures are
 * generic, but the method implementations are vendor-specific. A valid
 * <i>NMRData</i>
 * instance may be obtained using the <i>getFID</i>
 * method in the <i>NMRDataUtil</i> helper class. An <i>NMRData</i>
 * class should implement a constructor with a <i>String</i> path argument,
 * which reads a parameter file and a data header. The class should also
 * implement two static boolean methods: <i>findFID</i> and <i>findFIDFiles</i>.
 * See source code for examples.
 * <p>
 * If an argument is specified, e.g. <i>getParameter(int dim)</i>, parameter
 * values are returned for a dimension
 * <i>dim</i>, which is 0-based, e.g. 0, 1, 2, 3, 4. </p>
 *
 * @author bfetler
 * @see NMRDataUtil
 * @see BrukerData
 * @see VarianData
 * @see SinebellWt
 * @see GaussianWt
 * @see FPMult
 * @see LPParams
 */
@PythonAPI("pyproc")
public interface NMRData {

    /**
     * Return the size of a dimension in a data set.
     */
    void close();

    /**
     * Get path to the NMR data file
     *
     * @return the file path
     */
    String getFilePath();

    /**
     * Return a parameter value as a String
     *
     * @param parname The parameter name
     * @return the parameter value
     */
    String getPar(String parname);

    /**
     * Return a parameter value as a Double
     *
     * @param parname The parameter name
     * @return the parameter value
     */
    Double getParDouble(String parname);

    /**
     * Return a parameter value as a Integer
     *
     * @param parname The parameter name
     * @return the parameter value
     */
    Integer getParInt(String parname);

    /**
     * REeturn a list of all the vendor specific pars for this dataset
     *
     * @return the List of VendorPar objects
     */
    List<VendorPar> getPars();

    /**
     * Return the number of vectors in the direct dimension.
     *
     * @return number of vectors
     */
    int getNVectors();

    /**
     * Return the number of points per vector in the direct dimension.
     *
     * @return number of points
     */
    int getNPoints();

    /**
     * Return the number of dimensions in a data set.
     *
     * @return number of dimensions
     */
    int getNDim();

    /**
     * Return the size of a specified dimension in a data set.
     *
     * @param dim Dimension index
     * @return size
     */
    int getSize(int dim);

    /**
     * Return the max size of a dimension in a data set.
     *
     * @param dim dimension
     * @return size
     */
    default int getMaxSize(int dim) {
        return getSize(dim);
    }

    /**
     * Set the acquisition size of a dimension. This should correspond to the
     * actual size of measured dataset and is used when the data was obtained
     * before completetion of experiment so the actual size is less than that
     * indicated by parameters.
     *
     * @param dim  dimension
     * @param size the acquisition size of data
     */
    void setSize(int dim, int size);

    /**
     * Return the arrayed size of a specified dimension in a data set.
     *
     * @param dim Dimension index
     * @return size
     */
    default int getArraySize(int dim) {
        return 0;
    }

    /**
     * Set the acquisition array size of a dimension.
     *
     * @param dim  dimension
     * @param size the acquisition size of data
     */
    default void setArraySize(int dim, int size) {

    }

    /**
     * Return the solvent used for the sample
     *
     * @return solvent name
     */
    String getSolvent();

    /**
     * Return the temperature (Kelvin) of sample Kelvin
     *
     * @return temperature
     */
    double getTempK();

    /**
     * Return the pulse sequence
     *
     * @return sequence name
     */
    String getSequence();

    /**
     * Return the spectrometer frequency for the specified dimension
     *
     * @param dim data dimension index
     * @return spectrometer frequency in MHz
     */
    double getSF(int dim);

    default double getZeroFreq() {
        double sf = getSF(0);
        double ref = getRef(0);
        return  sf / (1.0 + ref / 1.0e6);
    }

    default void setZeroFreq(Double value) {

    }

    /**
     * Set the spectrometer frequency for the specified dimension. Used to
     * overwrite a value loaded by analysis of parameter files.
     *
     * @param dim   data dimension index
     * @param value new value for spectrometer frequency in MHz
     */
    void setSF(int dim, double value);

    default void setSF(int dim, int value) {
        setSF(dim, (double) value);
    }

    default void setSF(int dim, String string) {
        Double value = NMRDataUtil.parsePar(this, dim, string);
        if (value == null) {
            resetSF(dim);
        } else {
            if (getVendor().equals("rs2d")) {
                value = value / 1.0e6;
            }
            setSF(dim, value);
        }
    }

    /**
     * Reset object so next call to getSF will return the spectrometer frequency
     * stored in par file.
     *
     * @param dim data dimension index
     */
    void resetSF(int dim);

    /**
     * Return the sweep width for the specified dimension
     *
     * @param dim data dimension index
     * @return sweep width
     */
    double getSW(int dim);

    /**
     * Set the sweep width for the specified dimension. Used to overwrite a
     * value loaded by analysis of parameter files.
     *
     * @param dim   data dimension index
     * @param value new value for sweep width
     */
    void setSW(int dim, double value);

    default void setSW(int dim, int value) {
        setSW(dim, (double) value);
    }

    default void setSW(int dim, String string) {
        Double value = NMRDataUtil.parsePar(this, dim, string);
        if (value == null) {
            resetSW(dim);
        } else {
            setSW(dim, value);
        }

    }

    /**
     * Reset object so next call to getSW will return the sweep width stored in
     * par file.
     *
     * @param dim data dimension index
     */
    void resetSW(int dim);

    /**
     * Return the reference value for the specified dimension
     *
     * @param dim data dimension index
     * @return reference value in ppm
     */
    double getRef(int dim);

    /**
     * Set the reference value for the specified dimension. Used to overwrite a
     * value loaded by analysis of parameter files.
     *
     * @param dim data dimension index
     * @param ref new value for reference value
     */
    void setRef(int dim, double ref);

    default void setRef(int dim, int ref) {
        setRef(dim, (double) ref);
    }

    default void setRef(int dim, String string) {
        Double value;
        if (string != null) {
            string = string.trim();
        }
        if ((string == null) || string.isBlank()) {
            value = null;
        } else if (NumberUtils.isCreatable(string)) {
            value = Double.parseDouble(string);
        } else {
            if (string.equalsIgnoreCase("H2O")) {
                value = ReferenceCalculator.getH2ORefPPM(getTempK());
            } else if (string.equalsIgnoreCase("AUTOZERO")) {
                value = NMRDataUtil.autoRef(this, 0.0);
            } else if ("NCPDH".contains(string)) {
                double refZero = getZeroFreq();
                Nuclei nuclei = Nuclei.findNuclei(string);
                value = ReferenceCalculator.refByRatio(refZero, getSF(dim), nuclei, getSolvent());
            } else {
                value = null;
            }
        }
        if (value == null) {
            resetRef(dim);
        } else {
            setRef(dim, value);
        }
    }


    /**
     * Reset object so next call to getRef will return the reference value
     * stored in par file.
     *
     * @param dim data dimension index
     */
    void resetRef(int dim);

    /**
     * Return the reference point for the specified dimension
     *
     * @param dim data dimension index
     * @return reference point
     */
    double getRefPoint(int dim);

    /**
     * Return the transmitter nucleus for the specified dimension
     *
     * @param dim data dimension index
     * @return transmitter nucleus
     */
    String getTN(int dim);

    /**
     * Return the sweep width for the specified dimension
     *
     * @return sweep width
     */
    default boolean getFixDSP() {
        return false;
    }

    /**
     * Set the sweep width for the specified dimension.Used to overwrite a value
     * loaded by analysis of parameter files.
     *
     * @param value new value for sweep width
     */
    default void setFixDSP(boolean value) {

    }

    /**
     * Return whether the specified dimension has complex data
     *
     * @param dim data dimension index
     * @return true if data is complex
     */
    boolean isComplex(int dim);

    /**
     * Set whether the specified dimension has complex data
     *
     * @param dim   data dimension index
     * @param value if data is complex
     */
    default void setComplex(int dim, boolean value) {

    }

    /**
     * Return the number of members in each group of data that are collected with the same dwell time
     * Typically this is 2 for phase sensitive (hyper-complex or echo-antiecho) data in indirect dimensions
     * and 1 for data that will be processed in absolute value mode
     *
     * @param dim data dimension index
     * @return the number of data values or rows for each dwell time
     */

    default int getGroupSize(int dim) {
        return isComplex(dim) ? 2 : 1;
    }

    /**
     * Return whether alternate real/imaginary pairs of data should be negated
     * during processing.
     *
     * @param dim data dimension index
     * @return true if pairs should be negated
     */
    default boolean getNegatePairs(int dim) {
        return false;
    }

    /**
     * Return type of FT (FT or RFT) to perform in specified dimension
     *
     * @param dim data dimension index
     * @return type of
     */
    String getFTType(int dim);

    /**
     * Return whether imaginary values should be negated. Negating the imaginary
     * value reverses the spectrum.
     *
     * @param dim data dimension index
     * @return true if imaginary values should be negated.
     */
    default boolean getNegateImag(int dim) {
        return false;
    }

    /**
     * Return the array of coefficients that should be used for combining
     * vectors during processing.
     *
     * @param dim data dimension index
     * @return array of coefficients
     */
    double[] getCoefs(int dim);

    /**
     * Return a symbolic name (like hyper, echo-antiecho) for the data
     * combination that should be done during processing.
     *
     * @param dim data dimension index
     * @return symbolic coefficients name
     */
    String getSymbolicCoefs(int dim);

    /**
     * Return a symbolic name (like hyper, echo-antiecho) (set by the user) for the data
     * combination that should be done during processing.
     *
     * @param dim data dimension index
     * @return symbolic coefficients name
     */
    AcquisitionType getUserSymbolicCoefs(int dim);

    /**
     * Set the symbolic coefficients for the specified dimension
     *
     * @param dim  data dimension index
     * @param coef symbolic coefficients name
     */
    void setUserSymbolicCoefs(int dim, AcquisitionType coef);

    default void setAcqMode(int i, String mode) {
        AcquisitionType acqType = AcquisitionType.fromLabel(mode);
        setUserSymbolicCoefs(i, acqType);
    }

    /**
     * Return the name of the vendor of instrument used to collect data.
     *
     * @return vendor name
     */
    String getVendor();

    /**
     * Return the name of the user (operator).
     *
     * @return user name
     */
    default String getUser() {
        return "";
    }

     /**
     * Returns true if both the zero and first order phases for a specific dimension are not 0.0 before any conversions
      * are applied
     * @return
     */
    public default boolean arePhasesSet(int dim) {
        return false;
    }

    /**
     * Return the zeroth order phase value from parameter file.
     *
     * @param dim data dimension index
     * @return phase value
     */
    double getPH0(int dim);

    /**
     * Return the first order phase value from parameter file.
     *
     * @param dim data dimension index
     * @return spectrometer frequency
     */
    double getPH1(int dim);

    /**
     * Return the left shift value from parameter file.
     *
     * @param dim data dimension index
     * @return spectrometer frequency
     */
    int getLeftShift(int dim);

    /**
     * Return the exponential decay value (line broadening) value from parameter
     * file.
     *
     * @param dim data dimension index
     * @return spectrometer frequency
     */
    double getExpd(int dim);

    /**
     * Return the sine bell value from parameter file.
     *
     * @param dim data dimension index
     * @return exponential decay value
     */
    SinebellWt getSinebellWt(int dim);

    /**
     * Return the gaussian value from parameter file.
     *
     * @param dim data dimension index
     * @return sine bell value
     */
    GaussianWt getGaussianWt(int dim);

    /**
     * Return the first point multiplier from parameter file.
     *
     * @param dim data dimension index
     * @return spectrometer frequency
     */
    FPMult getFPMult(int dim);

    /**
     * Return an object describing Linear Prediction parameters for the
     * specified dimension
     *
     * @param dim data dimension index
     * @return LP parameters
     */
    LPParams getLPParams(int dim);

    /**
     * Return an array of names of parameters used to store spectrometer
     * frequencies in vendor parameter files.
     *
     * @return array of parameter names
     */
    String[] getSFNames();

    /**
     * Return an array of names of parameters used to store sweep widths in
     * vendor parameter files.
     *
     * @return array of parameter names
     */
    String[] getSWNames();

    /**
     * Return an array of names for the dimensions.
     *
     * @return array of dimension labels
     */
    String[] getLabelNames();

    /**
     * Return the text file assocciated with this data file.
     *
     * @return text file contents
     */
    default String getText() {
        return "";
    }

    /**
     * Return the type of data, spectrum ("S") or FID ("F")
     *
     * @return data type
     */
    default String getType() {
        return "S";
    }

    /**
     * Return a string describing the location of the sample in a sample
     * changer. The format of the returned string depends on vendor and sample
     * changer.
     *
     * @return sample position descriptor
     */
    default String getSamplePosition() {
        return "";
    }

    /**
     * Return the data of data acquisition, in typical Unix format (seconds
     * since January 1, 1910).
     *
     * @return date and time of acquistion
     */
    default long getDate() {
        return 0;
    }

    /**
     * Return a LocalDateTime object representing the time of aquisition.
     *
     * @return date and time
     */
    default LocalDateTime getZonedDate() {
        long epochSeconds = getDate();
        Instant instant = Instant.ofEpochSecond(epochSeconds);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /**
     * Return an optional list of doubles associated with a dimension of
     * dataset. This might be, for example, relaxation delays.
     *
     * @param dim data dimension index
     * @return list of values
     */
    default List<Double> getValues(int dim) {
        return new ArrayList<>();
    }

    /**
     * Read i'th vector from an <i>NMRData</i> file. This method is the main
     * entry point to read data. Other
     * <i>readVector</i> method signatures are used by this method, depending on
     * the <i>Vec</i> data storage type.
     *
     * @param iVec integer index of vector to read
     * @param dvec <i>Vec</i> vector to store data
     * @see Vec
     */
    void readVector(int iVec, Vec dvec);

    /**
     * Read i'th vector from an <i>NMRData</i> file and store in Complex array.
     *
     * @param iVec  integer index of vector to read
     * @param cdata complex array to store values in
     */
    void readVector(int iVec, Complex[] cdata);

    /**
     * Read i'th vector from an <i>NMRData</i> file and store in two double
     * arrays.
     *
     * @param iVec  integer index of vector to read
     * @param idata array of double to put real values in
     * @param rdata array of double to put imaginary values in
     */
    void readVector(int iVec, double[] rdata, double[] idata);

    /**
     * Read i'th vector from an <i>NMRData</i> file and store in double array.
     *
     * @param iVec integer index of vector to read
     * @param data array of double to put values in
     */
    void readVector(int iVec, double[] data);

    /**
     * Read i'th vector along iDim from an <i>NMRData</i> file and store in Vec
     * object.
     *
     * @param iDim dimension index to read data from
     * @param iVec integer index of vector to read
     * @param dvec Vec object used to store values in
     */
    void readVector(int iDim, int iVec, Vec dvec);

    /**
     * Get FID flags. Return null except for Bruker data.
     *
     * @return a Map of String / boolean key value pairs
     */
    default Map getFidFlags() {
        return null;
    }

    /**
     * Set flags before FID data is read using readVector. A no-op except for
     * Bruker data.
     *
     * @param flags a Map of String / boolean key value pairs
     */
    default void setFidFlags(Map flags) {
    }

    /**
     * reset acquisition order to default value
     */
    void resetAcqOrder();

    /**
     * Return an array of values indicating the order in which data dimensions
     * were acquired.
     *
     * @return the acquisition order of the various data dimensions
     */
    String[] getAcqOrder();

    /**
     * Set an array of values indicating the order in which data dimensions were
     * acquired.
     *
     * @param acqOrder the array of acquisition order values
     */
    void setAcqOrder(String[] acqOrder);

    /**
     * Get a summary string (like "321") indicating the order in which the data
     * dimensions were acquired. The format is vendor specific.
     *
     * @return the acquisition order string
     */
    default String getAcqOrderShort() {
        String[] acqOrderArray = getAcqOrder();
        StringBuilder builder = new StringBuilder();
        for (int i = acqOrderArray.length - 1; i >= 0; i--) {
            String elem = acqOrderArray[i];
            if (elem.charAt(0) == 'a') {
                return ("");
            }
            if (elem.length() == 0) {
                return ("");
            }
            if (elem.charAt(0) == 'p') {
                builder.append(elem.charAt(1));
            }
        }
        return builder.toString();
    }

    /**
     * Return a boolean indicating if this dataset is a raw (FID) dataset.
     *
     * @return true if the dataset is an FID
     */
    default boolean isFID() {
        return true;
    }

    /**
     * Return a boolean indicating if this dimension is in the frequency domain
     *
     * @param iDim data dimension index
     * @return true if the specified dimension is in the frequency domain
     */
    default boolean isFrequencyDim(int iDim) {
        return true;
    }

    /**
     * Return a double number indicating the fraction of spectrum that should be
     * trimmed off. Typically used to remove edges that are distored because of
     * artifacts from the digital filtering.
     *
     * @return the fraction to be trimmed
     */
    default double getTrim() {
        return 0.0;
    }

    /**
     * Return an object describing the sample schedule used for non-uniform
     * sampling.
     *
     * @return the sample schedule
     */
    SampleSchedule getSampleSchedule();

    /**
     * Set a sample schedule object for the data.
     *
     * @param sampleSchedule the sample schedule
     */
    void setSampleSchedule(SampleSchedule sampleSchedule);

    /**
     * Read a text file containing the sample schedule and return a new
     * SampleSchedule object.
     *
     * @param path The path to the file containing the sampling schedule
     * @param demo set to true to indicate that dataset actually has full
     *             sampling. We just want to simulate NUS.
     * @return the SampleSchedule object
     * @throws IOException         if an I/O error occurs
     * @throws ProcessingException if a processing error occurs
     */
    default SampleSchedule readSampleSchedule(String path, boolean demo, boolean phaseMode)
            throws IOException, ProcessingException {
        if ((new java.io.File(path)).exists()) {
            SampleSchedule schedule = new SampleSchedule(path, demo, phaseMode);
            setSampleSchedule(schedule);
            return schedule;
        } else {
            throw new IOException("Cannot find sample schedule file " + path);
        }
    }

    /**
     * Create a sample schedule corresponding to uniform sampling
     *
     * @param nSizes the sizes of each indirect dimension
     * @return the SampleSchedule
     */
    default SampleSchedule createUniformSchedule(List<Integer> nSizes) {
        int[] nSArray = new int[nSizes.size()];
        for (int i = 0; i < nSArray.length; i++) {
            nSArray[i] = nSizes.get(i);
        }
        SampleSchedule schedule = SampleSchedule.createUniformSchedule(nSArray);
        setSampleSchedule(schedule);
        return schedule;
    }

    /**
     * Create a sample schedule corresponding to zero filling
     *
     * @param nSizes the sizes of each indirect dimension
     * @return the SampleSchedule
     */
    default SampleSchedule createZFSchedule(List<Integer> nSizes, List<Integer> newSizes) {
        int[] nSArray = new int[nSizes.size()];
        int[] newArray = new int[newSizes.size()];
        for (int i = 0; i < nSArray.length; i++) {
            nSArray[i] = nSizes.get(i);
            newArray[i] = newSizes.get(i);
        }
        SampleSchedule schedule = SampleSchedule.createZFSchedule(nSArray, newArray);
        setSampleSchedule(schedule);
        return schedule;
    }


    /**
     * Create a sample schedule (with Poisson - Gap sampling)
     *
     * @param z        Full size of acquistion
     * @param fraction fraction of total points actually sampled
     * @param path     Path to a sample file to create
     * @param demo     set to true to indicate that dataset actually has full
     *                 sampling. We just want to simulate NUS.
     * @param nmrdata  The NMRData object that this schedule will be associated
     *                 with
     * @return the SampleSchedule object
     * @throws ProcessingException if a processing error occurs
     */
    static SampleSchedule createSampleSchedule(int z, double fraction, String path,
                                               boolean demo, NMRData nmrdata) throws ProcessingException {
        int p = (int) (fraction * z);
        SampleSchedule schedule = new SampleSchedule(p, z, path, demo);
        if (nmrdata != null) {
            nmrdata.setSampleSchedule(schedule);
            return schedule;
        } else {
            throw new ProcessingException("Sample schedule created, but no FID object found.");
        }
    }

    /**
     * Get the values as a name-value map for a specified set of parameter
     * names.
     *
     * @param parNames List of parameter names to get values for.
     * @param values   Map in which to put parameter name and parameter value
     */
    default void getPars(ArrayList<String> parNames, LinkedHashMap<String, Object> values) {
        for (String parName : parNames) {
            switch (parName) {
                case "text":
                    values.put(parName, getText());
                    break;
                case "sol":
                    values.put(parName, getSolvent());
                    break;
                case "pos":
                    values.put(parName, getSamplePosition());
                    break;
                case "seq":
                    values.put(parName, getSequence());
                    break;
                case "sf":
                    values.put(parName, getSF(0));
                    break;
                case "sw":
                    values.put(parName, getSW(0));
                    break;
                case "te":
                    values.put(parName, getTempK());
                    break;
                case "nd":
                    values.put(parName, getNDim());
                    break;
                case "tn":
                    values.put(parName, getTN(0));
                    break;
                case "vnd":
                    values.put(parName, getVendor());
                    break;
                case "nv":
                    values.put(parName, getNVectors());
                    break;
                case "time":
                    String dateTime = getZonedDate().format(DateTimeFormatter.ISO_DATE_TIME);
                    values.put(parName, dateTime);
                    break;
                default:
                    String value = getPar(parName);
                    if (value == null) {
                        value = "";
                    }
                    values.put(parName, value);
            }
        }
    }

    /**
     * Get a DatasetSummary object with parameters from this dataset. Used when
     * indexing remote or local data repositories.
     *
     * @return the DatasetSummary object constructed from this dataset parameters.
     */
    default DatasetSummary getDatasetSummary() {
        DatasetSummary datasetSummary = new DatasetSummary();
        datasetSummary.setUser(getUser());
        datasetSummary.setText(getText());
        datasetSummary.setSol(getSolvent());
        datasetSummary.setPos(getSamplePosition());
        datasetSummary.setSeq(getSequence());
        datasetSummary.setSf(getSF(0));
        datasetSummary.setTe(getTempK());
        datasetSummary.setNd(getNDim());
        datasetSummary.setTn(getTN(0));
        datasetSummary.setVnd(getVendor());
        datasetSummary.setNv(getNVectors());
        datasetSummary.setTime(getZonedDate().format(DateTimeFormatter.ISO_DATE_TIME));
        return datasetSummary;
    }

    DatasetType getPreferredDatasetType();

    void setPreferredDatasetType(DatasetType datasetType);

    List<DatasetGroupIndex> getSkipGroups();

    default List<int[]> getSkipIndices() {
        List<int[]> result = new ArrayList<>();
        List<DatasetGroupIndex> groups = getSkipGroups();
        for (var group : groups) {
            result.addAll(group.groupToIndices());
        }
        return result;
    }

    default void addSkipGroup(int[] indices, String realImaginaryChoice) {
        DatasetGroupIndex newGroup = new DatasetGroupIndex(indices, realImaginaryChoice);
        List<DatasetGroupIndex> currentIndices = getSkipGroups();
        if (!currentIndices.contains(newGroup)) {
            currentIndices.add(newGroup);
        }
    }

    default void addSkipGroup(DatasetGroupIndex newGroup) {
        List<DatasetGroupIndex> currentIndices = getSkipGroups();
        if (!currentIndices.contains(newGroup)) {
            currentIndices.add(newGroup);
        }
    }

    default void clearSkipGroups() {
        getSkipGroups().clear();
    }
}
