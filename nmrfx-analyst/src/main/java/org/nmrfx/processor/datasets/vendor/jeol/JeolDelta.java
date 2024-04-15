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
package org.nmrfx.processor.datasets.vendor.jeol;

import org.apache.commons.math3.complex.Complex;
import org.nmrfx.processor.datasets.AcquisitionType;
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
import org.nmrfx.utilities.ByteConversion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * This class implements the "JeolDelta" command in Vendor.
 */
public class JeolDelta implements NMRData {
    private static final Logger log = LoggerFactory.getLogger(JeolDelta.class);

    private final JeolDeltaAxis[] axes;
    private final File file;
    private final RandomAccessFile raFile;
    private final int dataStart;
    private int nSections = 1;
    private Strip[] strips;
    private int subMatrixPointCount;
    private int sectionByteCount;
    private DatasetType preferredDatasetType = DatasetType.NMRFX;
    private final int nPoints;                   // TD,1
    private final int nVectors;             // NS,1
    private final int nDim;                // from acqu[n]s files
    private Double[] sw;
    private Double[] sf;
    private String[] swNames;
    private String[] sfNames;
    private Double[] refValue;
    private Double[] refPoint;
    private AcquisitionType[] symbolicCoefs;

    private String[] nucleiNames;
    private int[] dimSizes;
    private double dspPh = 0.0;
    private int dspShift = 0;
    private double dspTrim;
    private String solvent = "";
    private String sequence = "";
    private double tempK;
    private final List<DatasetGroupIndex> datasetGroupIndices = new ArrayList<>();

    private Map<String, JeolPar> parMap = new HashMap<>();

    @Override
    public String getFilePath() {
        return file.getAbsolutePath();
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
    public List<VendorPar> getPars() {
        List<VendorPar> vendorPars = new ArrayList<>();
        for (Map.Entry<String, JeolPar> par : parMap.entrySet()) {
            vendorPars.add(new VendorPar(par.getKey(), par.getValue().getString()));
        }
        for (int i = 0; i < nDim; i++) {
            VendorPar axPar = new VendorPar("dtype," + (i + 1), axes[i].type.toString());
            vendorPars.add(axPar);
            VendorPar sPar = new VendorPar("sections," + (i + 1), String.valueOf(axes[i].getSectionCount()));
            vendorPars.add(sPar);
            VendorPar sizePar = new VendorPar("size," + (i + 1), String.valueOf(axes[i].nPoints));
            vendorPars.add(sizePar);
        }
        return vendorPars;
    }

    @Override
    public String getPar(String parname) {
        return parMap.get(parname).getString();
    }

    @Override
    public Double getParDouble(String parname) {
        if (parMap.containsKey(parname)) {
            double value = parMap.get(parname).getDouble();
            if (parname.endsWith("_FREQ")) { // special case to convert ot MHz
                value /= 1.0e6;
            }
            return value;
        } else {
            return null;
        }
    }

    @Override
    public Integer getParInt(String parname) {
        if (parMap.containsKey(parname)) {
            return parMap.get(parname).getInteger();
        } else {
            return null;
        }
    }

    @Override
    public String getFTType(int iDim) {
        return "ft";
    }

    @Override
    public int getNVectors() {
        return nVectors;
    }

    @Override
    public int getNPoints() {
        return nPoints;
    }

    @Override
    public int getNDim() {
        return nDim;
    }

    @Override
    public int getSize(int dim) {
        return axes[dim].nPoints;
    }

    @Override
    public void setSize(int dim, int size) {
    }

    @Override
    public String getSolvent() {
        return solvent;
    }

    @Override
    public double getTempK() {
        return tempK;
    }

    @Override
    public String getSequence() {
        return sequence;
    }

    @Override
    public double getSF(int dim) {
        if (sf[dim] == null) {
            sf[dim] = getParDouble(axisNames[dim] + "_FREQ") / 1.0e6;
        }
        return sf[dim];
    }

    @Override
    public void setSF(int dim, double value) {
        sf[dim] = value;
    }

    @Override
    public void resetSF(int dim) {
        sf[dim] = null;

    }

    @Override
    public double getSW(int dim) {
        if (sw[dim] == null) {
            sw[dim] = getParDouble(axisNames[dim] + "_SWEEP");
        }
        return sw[dim];
    }

    @Override
    public void setSW(int dim, double value) {
        sw[dim] = value;

    }

    @Override
    public void resetSW(int dim) {
        sw[dim] = null;
    }

    @Override
    public double getRef(int dim) {
        double ref;
        if (refValue[dim] != null) {
            ref = refValue[dim];
        } else {
            ref = parMap.get(axisNames[dim] + "_OFFSET").getDouble();
            // ref = ref + sw[dim] / sf[dim] / 2.0;
            refValue[dim] = ref;

        }
        return ref;
    }

    @Override
    public void setRef(int dim, double ref) {
        refValue[dim] = ref;
    }

    @Override
    public void resetRef(int dim) {
        refValue[dim] = null;
    }

    @Override
    public double getRefPoint(int dim) {
        double refpt = getSize(dim) / 2;
        return refpt;
    }

    @Override
    public String getTN(int dim) {
        return nucleiNames[dim];
    }

    @Override
    public boolean isComplex(int dim) {
        return axes[dim].isComplex(dim);
    }

    @Override
    public int getGroupSize(int dim) {
        return isComplex(dim) ? 2 : 1;
    }

    @Override
    public double[] getCoefs(int dim) {
        return AcquisitionType.HYPER.getCoefficients();
    }

    public void setUserSymbolicCoefs(int iDim, AcquisitionType coefs) {
        symbolicCoefs[iDim] = coefs;
    }

    public AcquisitionType getUserSymbolicCoefs(int iDim) {
        return symbolicCoefs[iDim];
    }

    @Override
    public String getSymbolicCoefs(int dim) {
        return AcquisitionType.HYPER.getLabel();
    }

    @Override
    public String getVendor() {
        return "Jeol";
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
        return 0.0;
    }

    @Override
    public SinebellWt getSinebellWt(int dim) {
        return null;
    }

    @Override
    public GaussianWt getGaussianWt(int dim) {
        return null;
    }

    @Override
    public FPMult getFPMult(int dim) {
        return new FPMult();
    }

    @Override
    public LPParams getLPParams(int dim) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String[] getSFNames() {
        return sfNames;
    }

    @Override
    public String[] getSWNames() {
        return swNames;
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

    public void readToVector(int iVec, Vec dvec) {
        readVector(iVec, dvec);
    }

    @Override
    public void readVector(int iVec, Vec dvec) {
        int section = 2 * (iVec % 2);
        int jVec = iVec / 2;
        if (nSections == 2) {
            section = 0;
            jVec = iVec;
        } else if (nSections == 1) {
            section = 0;
            jVec = iVec;
        }
        double[] values = getVector(jVec, section);
        double[] ivalues = null;
        if (nSections > 1) {
            ivalues = getVector(jVec, section + 1);
        }
        dvec.resize(values.length, true);
        for (int i = 0; i < values.length; i++) {
            double imag = ivalues == null ? 0.0 : ivalues[i];
            dvec.set(i, values[i], imag);
        }
        if (nSections > 1) {
            dspPhase(dvec);
        }
        dvec.dwellTime = 1.0 / getSW(0);
        dvec.centerFreq = getSF(0);
        dvec.setRefValue(getRef(0));

    }

    @Override
    public void readVector(int iVec, Complex[] cdata) {
        double[] values = getVector(iVec, 0);
        double[] ivalues = getVector(iVec, 1);
        for (int i = 0; i < cdata.length; i++) {
            cdata[i] = new Complex(values[i], ivalues[i]);
        }

    }

    @Override
    public void readVector(int iVec, double[] rdata, double[] idata) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void readVector(int iVec, double[] data) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void readVector(int iDim, int iVec, Vec dvec) {
        int[] position = new int[2];
        position[1] = iVec;
        double[] values = getVector(position, iDim, 0);
        double[] ivalues = getVector(position, iDim, 1);
        dvec.resize(values.length, true);
        for (int i = 0; i < values.length; i++) {
            dvec.set(i, values[i], ivalues[i]);
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
        return null;
    }

    @Override
    public void setSampleSchedule(SampleSchedule sampleSchedule) {
    }

    static class JeolPar {

        final String name;
        final JeolUnits units;
        final Number number;
        final String string;

        JeolPar(String name, Number number, JeolUnits units) {
            this.name = name;
            this.number = number;
            this.units = units;
            this.string = number.toString();
        }

        JeolPar(String name, String string) {
            this.name = name;
            this.number = null;
            this.units = null;
            this.string = string;
        }

        double getDouble() {
            if (number != null) {
                return number.doubleValue();
            } else {
                return 0.0;
            }
        }

        int getInteger() {
            if (number != null) {
                return number.intValue();
            } else {
                return 0;
            }
        }

        String getString() {
            return string;
        }

        public String toString() {
            return ">" + name + "<" + " " + string;
        }
    }

    static class JeolUnits {

        final int prefix;
        final int power;
        final int unit;

        JeolUnits(int prefix, int power, int unit) {
            this.prefix = prefix;
            this.power = power;
            this.unit = unit;
        }

        static JeolUnits convert(byte b0, byte b1) {
            int siPrefix = b0 >> 4;
            int unitPower = b0 & 15;
            return new JeolUnits(siPrefix, unitPower, b1);
        }

        @Override
        public String toString() {
            return prefix + " " + power + " " + unit;
        }
    }

    enum JeolPars {
        File_Identifier(0, "a8", 1, "String", ""),
        Endian(8, "B", 1, "Enum", ""),
        Major_Version(9, "B", 1, "Unsigned", ""),
        Minor_Version(10, "s", 1, "Unsigned", ""),
        Data_Dimension_Number(12, "B", 1, "Unsigned", ""),
        Data_Dimension_Exist(13, "b", 8, "1-Bit Boolean per axis", ""),
        Data_Format(14, "B", 1, "Enum", ""),
        Instrument(15, "B", 1, "Enum", ""),
        Translate(16, "B", 8, "1-Byte Unsigned per axis", ""),
        Data_Axis_Type(24, "B", 8, "1-Byte Enum per axis", ""),
        Data_Units(32, "S", 8, "2-Byte Unit Structure per axis", ""),
        Title(48, "a124", 1, "String", ""),
        Data_Axis_Ranged(172, "b4", 8, "4-Bit Enum per axis", ""),
        Data_Points(176, "i", 8, "4-Byte Unsigned per axis", ""),
        Data_Offset_Start(208, "i", 8, "4-Byte Unsigned per axis", ""),
        Data_Offset_Stop(240, "i", 8, "4-Byte Unsigned per axis", ""),
        Data_Axis_Start(272, "d", 8, "Double per axis", ""),
        Data_Axis_Stop(336, "d", 8, "Double per axis", ""),
        Creation_Time(400, "T", 1, "Time Structure", ""),
        Revision_Time(404, "T", 1, "Time Structure", ""),
        Node_Name(408, "a16", 1, "String", ""),
        Site(424, "a128", 1, "String", ""),
        Author(552, "a128", 1, "String", ""),
        Comment(680, "a128", 1, "String", ""),
        Data_Axis_Titles(808, "a32", 8, "32-Byte String per axis", ""),
        Base_Freq(1064, "d", 8, "Double per Axis", ""),
        Zero_Point(1128, "d", 8, "Double per Axis", ""),
        Reversed(1192, "b", 8, "1-Bit Boolean per axis", ""),
        reserved(1200, "B3", 1, "Reserved", ""),
        Annotation_Ok(1203, "b1", 1, "/8  1-Bit boolean", ""),
        History_Used(1204, "i", 1, "Unsigned", ""),
        History_Length(1208, "i", 1, "Unsigned", ""),
        Param_Start(1212, "i", 1, "Unsigned", ""),
        Param_Length(1216, "i", 1, "Unsigned", ""),
        List_Start(1220, "i", 8, "4-Byte Unsigned per axis", "u4"),
        List_Length(1252, "i", 8, "4-Byte Unsigned per axis", "u4"),
        Data_Start(1284, "i", 1, "Unsigned", ""),
        Data_Length(1288, "l", 1, "wUnsigned", ""),
        Context_Start(1296, "l", 1, "wUnsigned", ""),
        Context_Length(304, "i", 1, "wUnsigned", ""),
        Annote_Start(1308, "l", 1, "wUnsigned", ""),
        Annote_Length(1316, "i", 1, "Unsigned", ""),
        Total_Size(1320, "l", 1, "wUnsigned", ""),
        Unit_Location(1328, "B", 8, "1-Byte Unsigned per axis", ""),
        Extended_Units(1336, "B24", 1, "2 12-Byte Unit Structures", "");

        final int start;
        final String type;
        final int n;
        final String annotation;
        final String type2;

        JeolPars(int start, String type, int n, String annotation, String type2) {
            this.start = start;
            this.type = type;
            this.n = n;
            this.annotation = annotation;
            this.type2 = type2;
        }

        int getInteger(byte[] values) {
            if (type.equals("B")) {
                return values[start];
            } else {
                Number[] nums = convertNumber(values);
                return nums[0].intValue();
            }
        }

        int getInteger(byte[] values, int i) {
            if (type.equals("B")) {
                return values[start + i];
            } else {
                Number[] nums = convertNumber(values);
                return nums[i].intValue();
            }
        }

        Number[] convertNumber(byte[] values) {
            // Note: method was incomplete for conversion of other datatypes and no data available at the time to check
            // functionality so incomplete code was removed in commit 832d627beb1f505f12cbaef5210c6d75c7cf8ef0
            return ByteConversion.convert(values, type, start, n);
        }

        String[] convertStr(byte[] values) {
            String[] result = new String[n];
            int nChars = Integer.parseInt(type.substring(1));
            byte[] charBytes = new byte[nChars];
            int length = nChars;
            int offset = start;
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < nChars; i++) {
                    charBytes[i] = values[offset + i];
                    if (charBytes[i] == 0) {
                        length = i;
                        break;
                    }
                }
                result[j] = new String(charBytes, 0, length);
            }
            return result;
        }

        byte[][] convertBytes(byte[] values) {
            if (type.length() == 1) {
                byte[][] bytes = new byte[n][1];
                int offset = start;
                for (int j = 0; j < n; j++) {
                    bytes[j][0] = values[offset];
                    offset++;
                }

                return bytes;
            } else {
                int nBytes = Integer.parseInt(type.substring(1));
                byte[][] bytes = new byte[n][nBytes];
                int index = start;
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < nBytes; i++) {
                        bytes[j][i] = values[index + i];
                    }
                    index += nBytes;
                }
                return bytes;
            }
        }

        int[] convertBits(byte[] values) {
            int nBits = 1;
            if (type.length() > 1) {
                nBits = Integer.parseInt(type.substring(1));
            }
            int[] bytes = new int[n];
            for (int j = 0; j < n; j++) {
                int offset = (j * nBits) / 8;
                int shift = (j * nBits) % 8;
                int bValue = values[start + offset];
                int value = ((bValue & 0xff) >> (7 - shift)) & (int) Math.round(Math.pow(2, nBits) - 1);
                bytes[j] = value;
            }
            return bytes;
        }

        void convertUnits(byte[] values) {
            int offset = start;
            for (int i = 0; i < n; i++) {
                byte v0 = values[offset];
                offset++;
                byte v1 = values[offset];
                offset++;
                JeolUnits jUnit = JeolUnits.convert(v0, v1);
            }

        }

        void convertTime(byte[] values) {
            int offset = start;
            Number[] shorts = ByteConversion.convert(values, "s", offset, 1);
            short v0 = shorts[0].shortValue();
            int year = ((v0 >> 9) & 127) + 1990;
            int month = (v0 >> 5) & 15;
            int day = (v0 >> 9) & 31;
            offset += 2;
            Number[] shortsF = ByteConversion.convert(values, "s", offset, 1);
            short v1 = shorts[0].shortValue();
            double seconds = (v1 & 0xffff) * 1.318379;
            int hours = (int) Math.round(Math.floor(seconds / 3600));
            seconds = Math.round(seconds - hours * 3600);
            int minutes = (int) Math.round(Math.floor(seconds / 60));
            seconds = Math.round(seconds - minutes * 60);

        }

    }

    class Strip {

        ByteBuffer byteBuffer;
        DoubleBuffer doubleBuffer;
        int start = -1;

        Strip() {
        }
    }

    static final private int[] subMatrixEdges = {8, 32, 8, 8, 4, 4, 2, 2};
    static final String[] axisNames = {"X", "Y", "Z", "A", "B", "C", "D", "E"}; // are these right (after Z)

    public JeolDelta(final File file) throws IOException {
        this.file = file;
        if (!file.exists()) {
            throw new IOException("File " + file + " doesn't exist");
        }

        raFile = new RandomAccessFile(file, "r");
        byte[] header = new byte[1360];
        readBytes(header, 0, 1360);
        parseHeader(header);
        //    public JeolDeltaAxis(final int nPoints, final int subMatrixEdge, final int start, final int stop, final AxisType type) {

        dataStart = JeolPars.Data_Start.getInteger(header);
        int dataLength = JeolPars.Data_Length.getInteger(header);
        int dataStop = dataStart + dataLength - 1;
        nDim = JeolPars.Data_Dimension_Number.getInteger(header);
        nPoints = JeolPars.Data_Points.convertNumber(header)[0].intValue();
        subMatrixPointCount = subMatrixEdges[nDim - 1];
        axes = new JeolDeltaAxis[nDim];
        dimSizes = new int[nDim];
        int nV = 1;
        Number[] headerDimSizes = JeolPars.Data_Points.convertNumber(header);
        for (int iDim = 0; iDim < nDim; iDim++) {
            int dimSize = headerDimSizes[iDim].intValue();
            dimSizes[iDim] = dimSize;
            int dType = JeolPars.Data_Axis_Type.getInteger(header, iDim);
            axes[iDim] = new JeolDeltaAxis(iDim, dimSize, subMatrixPointCount, 0, 0, dType);
            if (iDim > 0) {
                nV *= dimSize * 2;
            }
        }
        /*
    for {set iDim 1} {$iDim <= $nDim} {incr iDim} {
        set f(dataAxisType,$iDim) [$cmd get $parH Data_Axis_Type,$iDim]
        set nPoints [$cmd get $parH Data_Points,$iDim]
        set f(subCount,$iDim) [expr {$nPoints/$f(subMatrixEdge)}]
    }

         */
        standardize();
        nVectors = nV;
        setup();

    }

    /**
     * Finds FID data, given a path to search for vendor-specific files and
     * directories.
     *
     * @param bpath full path for FID data
     * @return if FID data was successfully found or not
     */
    public static boolean findFID(File file) {
        return file.getName().endsWith(".jdf");
    }

    private static boolean findFIDFiles(File file) {
        return file.getName().endsWith(".jdf");
    }

    public void close() {
        try {
            raFile.close();
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    private void setup() {
        nSections = 1;
        sectionByteCount = 8;
        subMatrixPointCount = 1;
        for (JeolDeltaAxis axe : axes) {
            if (axe.nPoints != 0) {
                subMatrixPointCount *= axe.subMatrixEdge;
                sectionByteCount *= axe.nPoints;
            }
            nSections *= axe.getSectionCount();
        }

        strips = new Strip[nSections];
        for (int i = 0; i < nSections; i++) {
            strips[i] = new Strip();
        }

    }

    void parseHeader(byte[] bytes) {
        int parStart = JeolPars.Param_Start.getInteger(bytes);
        int parLength = JeolPars.Param_Length.getInteger(bytes);
        loadParams(parStart, parLength);
    }

    @Override
    public List<DatasetGroupIndex> getSkipGroups() {
        return datasetGroupIndices;
    }

    String getString(ByteBuffer byteBuffer, int valueStart, int nChars) {
        int length = 0;
        byte[] charBytes = new byte[nChars];
        int i = 0;
        for (int j = 0; j < nChars; j++) {
            byte charByte = byteBuffer.get(valueStart + j);
            if (charByte != 0) {
                charBytes[i] = charByte;
                i++;
                length = i;
            }
        }
        String sValue = new String(charBytes, 0, length);
        return sValue.trim();

    }

    void loadParams(int parStart, int parLength) {
        byte[] header = new byte[parLength];
        readBytes(header, parStart, parLength);
        ByteBuffer byteBuffer = ByteBuffer.wrap(header);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int nPars = byteBuffer.getInt(8);
        int offset = 16;
        for (int i = 0; i < nPars; i++) {
            int unit0Start = offset + 6;
            int unit1Start = offset + 7;
            int typeStart = offset + 32;
            int valueStart = offset + 16;
            int nameStart = offset + 36;

            int type = byteBuffer.getInt(typeStart);
            byte uv0 = byteBuffer.get(unit0Start);
            byte uv1 = byteBuffer.get(unit1Start);
            JeolUnits jUnit = JeolUnits.convert(uv0, uv1);
            String name = getString(byteBuffer, nameStart, 16);
            JeolPar par;
            switch (type) {
                case 0:
                    String sValue = getString(byteBuffer, valueStart, 16);
                    par = new JeolPar(name, sValue);
                    parMap.put(name, par);
                    break;
                case 1:
                    int iValue = byteBuffer.getInt(valueStart);
                    par = new JeolPar(name, iValue, jUnit);
                    parMap.put(name, par);
                    break;
                case 2:
                    double dValue = byteBuffer.getDouble(valueStart);
                    par = new JeolPar(name, dValue, jUnit);
                    parMap.put(name, par);
                    break;
            }
            offset += 64;
        }
    }

    public void dumpPars() {
        if (log.isDebugEnabled()) {
            StringBuilder parString = new StringBuilder();
            for (String key : parMap.keySet()) {
                parString.append(parMap.get(key)).append(System.lineSeparator());
            }
            log.debug(parString.toString());
        }
    }

    void standardize() {
        sw = new Double[nDim];
        sf = new Double[nDim];
        symbolicCoefs = new AcquisitionType[nDim];

        swNames = new String[nDim];
        sfNames = new String[nDim];
        nucleiNames = new String[nDim];
        refValue = new Double[nDim];
        refPoint = new Double[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            String aName = axisNames[iDim];
            String key = aName + "_SWEEP";
            sw[iDim] = parMap.get(aName + "_SWEEP").getDouble();
            swNames[iDim] = key;
            sf[iDim] = parMap.get(aName + "_FREQ").getDouble() / 1.0e6;
            sfNames[iDim] = aName + "_FREQ";
            nucleiNames[iDim] = parMap.get(aName + "_DOMAIN").string;
        }

        if (parMap.containsKey("DIGITAL_FILTER")) {
            String dF = parMap.get("DIGITAL_FILTER").string;
            if (dF.toLowerCase().startsWith("t")) {
                int filterFactor = parMap.get("FILTER_FACTOR").getInteger();
                switch (filterFactor) {
                    case 2:
                        dspPh = -17 * 360.0;
                        dspShift = 8;
                        break;
                    case 4:
                        dspPh = -19 * 360.0;
                        dspShift = 16;
                        break;
                    case 8:
                        dspPh = -19.7 * 360.0;
                        dspShift = 32;
                        break;
                    default:
                        dspPh = 0.0;
                        dspShift = 0;
                }
                if (parMap.containsKey("X_SWEEP_CLIPPED")) {
                    double swClip = parMap.get("X_SWEEP_CLIPPED").getDouble();
                    dspTrim = (1.0 - swClip / sw[0]) / 2.0;
                }
                dimSizes[0] -= dspShift;

            }

        }
        solvent = parMap.get("solvent").getString();
        tempK = parMap.get("temp_get").getDouble() + 273.15;

    }

    private void dspPhase(Vec vec) {
        if (dspPh != 0.0) {  // check DMX flag?
            int origSize = vec.getSize();
            vec.checkPowerOf2(); // resize
            vec.fft();
            vec.phase(0.0, dspPh, false, false);
            vec.ifft();
            vec.resize(dimSizes[0], true);
            vec.setGroupDelay(0.0);
            vec.setPh0(0.0);
            vec.setPh1(0.0);
        }
    }

    public void readBytes(byte[] dataBytes, long newPos, int length) {
        try {
            raFile.seek(newPos);
            raFile.read(dataBytes, 0, length);
        } catch (IOException e) {
            log.warn("Unable to read from dataset. {}", e.getMessage(), e);
        }
    }

    public double[] getVector(final int iSection) {
        int[] position = new int[1];
        return getVector(position, 0, iSection);
    }

    public double[] getVector(final int iVec, final int iSection) {
        int[] position = new int[nDim];
        position[nDim - 1] = iVec;
        return getVector(position, 0, iSection);
    }

    double[] getVector(final int[] position, final int iDim, final int iSection) {
        int n = axes[iDim].stop - axes[iDim].start + 1;
        double[] vector = new double[n];
        int j = 0;
        getStrip(position, iSection);
        for (int i = axes[iDim].start; i <= axes[iDim].stop; i++) {
            position[iDim] = i;
            int pos = getPositionInStrip(position);
            vector[j++] = strips[iSection].doubleBuffer.get(pos);
        }
        return vector;
    }

    void getStrip(final int[] startPos, final int[] endPos, final int iDim, final int iSection) {
        int start = getPositionInFile(startPos, iSection);
        int end = getPositionInFile(endPos, iSection);
        int nBytes = start - end + 1;

        Strip strip = strips[iSection];
        if ((strip.byteBuffer == null) || (strip.byteBuffer.capacity() != nBytes)) {
            strip.byteBuffer = ByteBuffer.allocate(nBytes);
            strip.byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        readBytes(strip.byteBuffer.array(), start, nBytes);
        strip.doubleBuffer = strip.byteBuffer.asDoubleBuffer();
    }

    void getStrip(final int[] position, final int iSection) {
        int start = getSubmatrixStart(position);
        start = dataStart + sectionByteCount * iSection + 8 * start;
        Strip strip = strips[iSection];
        if (strip.start != start) {
            int nBytes = subMatrixPointCount * 8 * axes[0].nSubMatrices;
            if ((strip.byteBuffer == null) || (strip.byteBuffer.capacity() != nBytes)) {
                strip.byteBuffer = ByteBuffer.allocate(nBytes);
                strip.byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            readBytes(strip.byteBuffer.array(), start, nBytes);
            strip.doubleBuffer = strip.byteBuffer.asDoubleBuffer();
            strip.start = start;
        }
    }

    int getPositionInStrip(final int[] position) {
        int nPositions = position.length;
        int pnt_off = 0;
        int sub_off = 0;
        for (int i = (nPositions - 1); i >= 1; i--) {
            int posi = position[i] + axes[i].start;
            pnt_off = (pnt_off + posi % axes[i].subMatrixEdge) * axes[i].subMatrixEdge;
        }
        int posi = position[0] + axes[0].start;
        pnt_off = pnt_off + posi % axes[0].subMatrixEdge;
        sub_off = sub_off + posi / axes[0].subMatrixEdge;
        int offset = sub_off * subMatrixPointCount + pnt_off;
        return offset;
    }

    int getPositionInSection(final int[] position) {
        int nPositions = position.length;
        int pnt_off = 0;
        int sub_off = 0;
        for (int i = (nPositions - 1); i >= 1; i--) {
            int posi = position[i] + axes[i].start;
            pnt_off = (pnt_off + posi % axes[i].subMatrixEdge) * axes[i].subMatrixEdge;
            sub_off = (sub_off + posi / axes[i].subMatrixEdge) * axes[i - 1].nSubMatrices;
        }
        int posi = position[0] + axes[0].start;
        pnt_off = pnt_off + posi % axes[0].subMatrixEdge;
        sub_off = sub_off + posi / axes[0].subMatrixEdge;
        int offset = sub_off * subMatrixPointCount + pnt_off;
        return offset;
    }

    int getSubmatrixStart(final int[] position) {
        int nPositions = position.length;
        int sub_off = 0;
        for (int i = (nPositions - 1); i >= 1; i--) {
            int posi = position[i] + axes[i].start;
            sub_off = (sub_off + posi / axes[i].subMatrixEdge) * axes[i - 1].nSubMatrices;
        }
        int posi = position[0] + axes[0].start;
        sub_off = sub_off + posi / axes[0].subMatrixEdge;
        int offset = sub_off * subMatrixPointCount;
        return offset;
    }

    int getPositionInFile(final int[] position, final int iSection) {
        int offset = getPositionInSection(position);
        offset = dataStart + sectionByteCount * iSection + 8 * offset;
        return offset;
    }

    public double getTrim() {
        return dspTrim;
    }

}
