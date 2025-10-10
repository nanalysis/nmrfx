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
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.datasets.vendor.bruker.BrukerData;
import org.nmrfx.processor.datasets.vendor.jcamp.JCAMPData;
import org.nmrfx.processor.datasets.vendor.jeol.JeolDelta;
import org.nmrfx.processor.datasets.vendor.nmrpipe.NMRPipeData;
import org.nmrfx.processor.datasets.vendor.nmrview.NMRViewData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.datasets.vendor.varian.VarianData;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.operations.Expd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Base64.Encoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility helper class for NMRData interface.
 *
 * @author bfetler
 */
@PythonAPI({"autoscript", "pyproc"})
public final class NMRDataUtil {

    private static final Logger log = LoggerFactory.getLogger(NMRDataUtil.class);

    private static NMRData currentNMRData = null;

    private NMRDataUtil() {
    }

    /**
     * Get the currently active NMRData object. Used by nvfx to make object
     * available to python
     *
     * @return the NMRData object that was set with setCurrentData
     */
    public static NMRData getCurrentData() {
        return currentNMRData;
    }

    /**
     * Set the currently active NMRData object. Used by nvfx to make object
     * available to python
     *
     * @param nmrData an NMRData object to set as the active one
     */
    public static void setCurrentData(NMRData nmrData) {
        currentNMRData = nmrData;
    }

    /**
     * Get the FID parameters and data from an absolute file path <b>fpath</b>.
     * Data may be in any vendor format, e.g. Bruker or Varian, and in any
     * directory or subdirectory allowable by a vendor.
     * <p>
     * For example, if $dir is a full path directory, <b>fpath</b> may be
     * $dir/HMQC, $dir/HMQC/, $dir/HMQC/4, $dir/HMQC/4/ser, $dir/cosy.fid or
     * $dir/cosy.fid/fid.
     * </p>
     *
     * @param file file
     * @return an NMRData object
     * @throws IOException if an I/O error occurs
     * @see NMRData
     */
    public static NMRData getFID(File file) throws IOException {
        return getFID(file, null);
    }

    public static NMRData getFID(File file, File nusFile) throws IOException {
        try {
            Optional<File> fileOpt;
            if (NMRViewData.findFID(file)) {
                return new NMRViewData(file,  false);
            }
            if (RS2DData.findFID(file)) {
                return new RS2DData(file, nusFile);
            }

            fileOpt = BrukerData.findFID(file);
            if (fileOpt.isPresent()) {
                return new BrukerData(fileOpt.get(), nusFile);
            }

            fileOpt = VarianData.findFID(file);
            if (fileOpt.isPresent()) {
                return new VarianData(fileOpt.get());
            }

            if (JCAMPData.findFID(file)) {
                return new JCAMPData(file);
            }
            if (NMRPipeData.findFID(file)) {
                return new NMRPipeData(file.toString(), nusFile);
            }
            if (JeolDelta.findFID(file)) {
                return new JeolDelta(file);
            }
            throw new IOException("FID not found: " + file);

        } catch (NullPointerException nullE) {
            throw new IOException("Null pointer when reading " + file + " " + nullE.getMessage());
        }
    } // end getFID

    /**
     * Get the parameters and data from an absolute file path <b>fpath</b>. Data
     * may be in any vendor format, e.g. Bruker or Varian, and in any directory
     * or subdirectory allowable by a vendor. Maybe spectrum or FID
     * <p>
     * For example, if $dir is a full path directory, <b>fpath</b> may be
     * $dir/HMQC, $dir/HMQC/, $dir/HMQC/4, $dir/HMQC/4/ser, $dir/cosy.fid or
     * $dir/cosy.fid/fid.
     * </p>
     *
     * @param file absolute file path
     * @return an NMRData object
     * @throws IOException if an I/O error occurs
     * @see NMRData
     */
    public static NMRData getNMRData(File file) throws IOException {
        StringBuilder bpath = new StringBuilder(file.toString());
        try {
            if (NMRViewData.findFID(file)) {
                return new NMRViewData(file, false);
            }
            if (RS2DData.findFID(file)) {
                return new RS2DData(file, null);
            }
            if (BrukerData.findData(file)) {
                return new BrukerData(file);
            }

            var fileOpt = VarianData.findFID(file);
            if (fileOpt.isPresent()) {
                return new VarianData(fileOpt.get());
            }

            if (JCAMPData.findData(file)) {
                return new JCAMPData(file);
            }
            throw new IOException("Dataset not found: " + file);

        } catch (NullPointerException nullE) {
            return null;
        }
    } // end getFID

    /**
     * Load an NMRData object from the fpath. The NMRData will be loaded as either a dataset or an FID, depending on
     * the fpath.
     *
     * @param file   absolute file
     * @param nusFile
     * @return An NMRData object
     * @throws IOException
     */
    public static NMRData loadNMRData(File file, File nusFile, boolean saveToProject) throws IOException {
        StringBuilder bpath = new StringBuilder(file.toString());
        try {
            Optional<File> fileOpt;
            if (NMRViewData.findFID(file)) {
                return new NMRViewData(file, saveToProject);
            }
            if (RS2DData.findFID(file) || RS2DData.findData(file)) {
                return new RS2DData(file, nusFile);
                // Most processed Bruker files would also have the fid present and pass the findFID check,
                // so must check if it's a dataset before checking for FID
            }
            if (BrukerData.findData(file)) {
                return new BrukerData(file);
            }

            fileOpt = BrukerData.findFID(file);
            if (fileOpt.isPresent()) {
                return new BrukerData(fileOpt.get(), nusFile);
            }

            fileOpt = VarianData.findFID(file);
            if (fileOpt.isPresent()) {
                return new VarianData(fileOpt.get());
            }
            if (JCAMPData.findFID(file) || JCAMPData.findData(file)) {
                return new JCAMPData(file);
            }
            if (NMRPipeData.findFID(file)) {
                return new NMRPipeData(file.toString(), nusFile);
            }
            if (JeolDelta.findFID(file)) {
                return new JeolDelta(file);
            }
            throw new IOException("File could not be read: " + file);

        } catch (NullPointerException nullE) {
            throw new IOException("Null pointer when reading " + file + " " + nullE.getMessage());
        }
    }

    /**
     * Check if specified path represents an NMR data file
     *
     * @param file absolute file path
     * @return a standardized file path
     * @see NMRData
     */
    public static File isFIDDir(File file) {
        StringBuilder bpath = new StringBuilder(file.toString());
        Optional<File> fileOpt;
        fileOpt = BrukerData.findFID(file);
        if (fileOpt.isPresent()) {
            return fileOpt.get();
        }
        fileOpt = VarianData.findFID(file);
        if (fileOpt.isPresent()) {
            return fileOpt.get();
        }
        if (RS2DData.findFID(file)) {
            return file;
        }
        if (JCAMPData.findFID(file)) {
            return file;
        }
        if (JeolDelta.findFID(file)) {
            return file;
        }
        return null;

    }

    /**
     * Check if specified path represents an NMR data file
     *
     * @param file absolute file path
     * @return a standardized file path
     * @see NMRData
     */
    public static String isDatasetFile(File file) {
        StringBuilder bpath = new StringBuilder(file.toString());
        if (NMRViewData.findFID(file) || RS2DData.findFID(file) || BrukerData.findData(file)
                || VarianData.findFID(file).isPresent() || JCAMPData.findData(file)) {
            return file.toString();
        } else {
            return null;
        }
    }

    /**
     * Guess nucleus from frequency <b>freq</b>. Nuclei searched are 1H, 13C,
     * 15N, 31P. 1H frequencies searched span 300 to 1000 MHz.
     *
     * @param freq frequency
     * @return Arraylist of four elements: first is a nucleus String, second is
     * a minimum frequency difference, third is a 1H frequency, fourth is a
     * bracketing minimum frequency difference.
     */
    public static ArrayList guessNucleusFromFreq(final double freq) {
        final double[] Hfreqs = {1000.0, 950.0, 900.0, 800.0, 750.0,
                700.0, 600.0, 500.0, 400.0, 300.0, 100.0, 60.0};
        HashMap<String, Double> ratio = new LinkedHashMap<>(4);
        ratio.put("1H", 1.0);
        ratio.put("13C", 0.25145004);
        ratio.put("15N", 0.10136783);
        ratio.put("31P", 0.40480737);
        final java.util.Set<String> nuclei = ratio.keySet();

        double min = Hfreqs[0];
        double min2 = 0.0;
        double h1Freq = 0.0;
        String nucleus = "1H";

        for (double Hfreq : Hfreqs) {
            for (String nuc : nuclei) {
                double nucfreq = ratio.get(nuc) * Hfreq;
                double deltaf = Math.abs(freq - nucfreq);
                if (deltaf < min) {
                    min2 = min;
                    min = deltaf;
                    nucleus = nuc;
                    h1Freq = Hfreq;
                }
            }
        }

        ArrayList alist = new ArrayList<>(4);
        alist.add(nucleus);
        alist.add(min);
        alist.add(h1Freq);
        alist.add(min2);
        return alist;
    } // end guessNucleusFromFreq

    /**
     *
     */
    private abstract static class PeekFiles extends SimpleFileVisitor<Path> {
        ArrayList<String> fileList = new ArrayList<>();

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
            handleVisit(file, attr);
            return FileVisitResult.CONTINUE;
        }

        protected abstract void handleVisit(Path file, BasicFileAttributes attr);

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
            log.warn(e.getMessage(), e);
            return FileVisitResult.CONTINUE;
        }

        /**
         * Get the files that were found while scanning
         *
         * @return a list of file names
         */
        public List<String> getFiles() {
            return fileList;
        }
    } // end class PeekFiles

    public static class PeekFidFiles extends PeekFiles {

        @Override
        protected void handleVisit(Path path, BasicFileAttributes attr) {
            if (attr.isRegularFile() && (path.endsWith("fid") || (path.endsWith("ser")) || path.toString().toLowerCase().endsWith(".jdx") || path.toString().toLowerCase().endsWith(".dx") || path.toString().endsWith(RS2DData.DATA_FILE_NAME))) {
                File file = path.toFile();
                File fidFile = NMRDataUtil.isFIDDir(file);
                if (fidFile != null) {
                    fileList.add(fidFile.toString());
                }
            }
        }
    }

    public static class PeekProcessedFiles extends PeekFiles {
        Pattern pattern = Pattern.compile("\\.nv$|\\.ucsf$|Proc.*data\\.dat$");

        @Override
        protected void handleVisit(Path file, BasicFileAttributes attr) {
            String name = file.getFileName().toString();
            Matcher m = pattern.matcher(file.toString());
            if (m.find() || BrukerData.isProcessedFile(name)) {
                fileList.add(file.toString());
            }
        }
    }

    /**
     * Scan the specified directory to find sub-directories that are NMR data
     * sets.
     *
     * @param path the path of the directory to scan
     * @return An ArrayList containing a list of NMR dataset paths.
     */
    public static List<String> findNMRDirectories(String path) {
        List<String> fileList = null;
        Path autodir = Paths.get(path);
        try {
            PeekFiles filePeeker = new PeekFidFiles();
            Files.walkFileTree(autodir, filePeeker);
            fileList = filePeeker.getFiles();
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return fileList;
    }

    public static List<Path> findProcessedFiles(Path path) throws IOException {
        try {
            PeekFiles filePeeker = new PeekProcessedFiles();
            if (path.toFile().isFile()) {
                path = path.toFile().getParentFile().toPath();
            }
            Files.walkFileTree(path, filePeeker);
            return filePeeker.getFiles().stream().map(Path::of).collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return new ArrayList<>();
    }

    public static File findNewestFile(Path dirPath) {
        File lastFile = null;
        try {
            List<Path> paths = findProcessedFiles(dirPath);
            long lastMod = 0;
            for (Path path : paths) {
                File file = path.toFile();
                long modTime = file.lastModified();
                if (modTime > lastMod) {
                    lastMod = modTime;
                    lastFile = file;
                }
            }
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return lastFile;
    }

    public static String calculateHash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(input.getBytes());
        Encoder base64 = Base64.getEncoder();
        return base64.encodeToString(digest.digest());
    }

    /**
     * Attempt to get the phases from the NMRData object. If dimension is 0, then if the phases are not present or are both zero, then
     * the phases from an auto phase are returned. Otherwise, if the phases are not present or are both zero,
     * phases will be returned as 0.0.
     * @param nmrData The NMRData to retrieve phases from
     * @param dim The dimension to get the phases for
     * @return An array of phases
     */
    public static double[] getPhases(NMRData nmrData, int dim, boolean usePhases, boolean doAutoPhase, boolean doAutoPhase1) {
        double[] phases = new double[2];
        if (nmrData.arePhasesSet(dim) && usePhases) {
            phases[0] = nmrData.getPH0(dim);
            phases[1] = nmrData.getPH1(dim);
        }
        if ((dim == 0) && doAutoPhase) {
            phases = autoPhase(nmrData, phases, doAutoPhase1);
        }

        return phases;
    }

    public static double autoRef(NMRData nmrData, double ref) {
        int n = nmrData.getNPoints();
        Vec vec = new Vec(n, true);
        nmrData.resetRef(0);
        double curRef = nmrData.getRef(0);
        nmrData.readVector(0, vec);
        double lb = nmrData.getTN(0).contains("H") ? 2.0 : 10.0;
        Expd expD = new Expd(lb, 1.0, false);
        expD.eval(vec);
        vec.fft(false, false, true);
        int pt0 = vec.refToPt(ref + 0.1);
        int pt1 = vec.refToPt(ref - 0.1);
        int maxPt = vec.maxIndex(pt0, pt1).getIndex();
        double refPos = vec.pointToPPM(maxPt);
        double deltaRef = refPos - ref;
        return curRef - deltaRef;
    }


    public static double[] autoPhase(NMRData nmrData, double[] phases, boolean doAutoPhase1) {
        int n = nmrData.getNPoints();
        Vec vec = new Vec(n, true);
        nmrData.readVector(0, vec);
        double lb = nmrData.getTN(0).contains("H") ? 2.0 : 10.0;
        Expd expD = new Expd(lb, 1.0, false);
        expD.eval(vec);
        vec.fft(false, false, true);
        vec.phase(phases[0], phases[1]);
        int winSize = vec.getSize() / 256;
        winSize = Math.max(4, winSize);
        winSize = Math.min(64, winSize);
        double[] autoPhases = vec.autoPhase(doAutoPhase1, winSize, 25.0, 2, 360.0, 50.0);
        phases[0] += autoPhases[0];
        phases[1] += autoPhases[1];

        return phases;
    }

    public static Double parsePar(NMRData nmrData, int dim, String string) {
        Double value;
        if ((string == null) || string.isBlank()) {
            value = null;
        } else if (NumberUtils.isCreatable(string)) {
            value = Double.parseDouble(string);
        } else {
            value = nmrData.getParDouble(string);
        }
        return value;
    }

    public static String getApodizationString(NMRData nmrData, int dim, boolean arrayed, boolean useApodize) {
        String fPointVal = "";
        if (dim > 0) {
            double ph1 = nmrData.getPH1(dim);
            double fPoint = 0.5 + 0.5 * Math.abs(ph1 / 180.0);
            fPointVal =  String.format("%.2f", fPoint);
        }
        String apodizationString = "";

        if (useApodize) {
            GaussianWt gaussianWt = nmrData.getGaussianWt(dim);
            if ((gaussianWt != null) && gaussianWt.exists()) {
                double gb = gaussianWt.gf();
                double lb = gaussianWt.lb();
                String fPointStr = fPointVal.isEmpty() ? "" : ", fPoint=" + fPointVal;
                apodizationString = "GMB("
                        + "lb=" + String.format("%.2f", lb)
                        + ", gb=" + String.format("%.2f", gb)
                        + fPointStr
                        + ")";

            } else {
                double lb = nmrData.getExpd(dim);
                if (Math.abs(lb) >= 1.0e-6) {
                    String fPointStr = fPointVal.isEmpty() ? "" : ", fPoint=" + fPointVal;
                    apodizationString = "EXPD("
                            + "lb=" + String.format("%.2f", lb)
                            + fPointStr
                            + ")";
                }
            }
            SinebellWt sinebellWt = nmrData.getSinebellWt(dim);
            if ((sinebellWt != null) && sinebellWt.exists()) {
                String fPointStr = fPointVal.isEmpty() ? "" : ", c=" + fPointVal;
                String sbString = "SB("
                        + "offset=" + String.format("%.2f", sinebellWt.offset())
                        + ", power=" + String.format("%d", sinebellWt.power())
                        + fPointStr
                        + ")";
                if (apodizationString.isEmpty()) {
                    apodizationString = sbString;
                } else {
                    apodizationString += "\n" + sbString;
                }
            }
        } else {
            if ((nmrData.getNDim() == 1) || (arrayed && nmrData.getNDim() == 2)) {
                apodizationString = "EXPD("
                        + "lb=" + String.format("%.2f", 0.3)
                        + ")";
            } else {
                String fPointStr = fPointVal.isEmpty() ? "" : ", c=" + fPointVal;
                apodizationString = "SB("
                        + "offset=" + String.format("%.2f", 0.5)
                        + ", power=" + String.format("%d", 2)
                        + fPointStr
                        + ")";
            }

        }
        return apodizationString;
    }
}
