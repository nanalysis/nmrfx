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

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.datasets.vendor.bruker.BrukerData;
import org.nmrfx.processor.datasets.vendor.jcamp.JCAMPData;
import org.nmrfx.processor.datasets.vendor.jeol.JeolDelta;
import org.nmrfx.processor.datasets.vendor.nmrpipe.NMRPipeData;
import org.nmrfx.processor.datasets.vendor.nmrview.NMRViewData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.datasets.vendor.varian.VarianData;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.operations.Expd;
import org.nmrfx.utilities.DatasetSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Base64.Encoder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * @param fpath absolute file path
     * @return an NMRData object
     * @throws IOException if an I/O error occurs
     * @see NMRData
     */
    public static NMRData getFID(String fpath) throws IOException {
        return getFID(fpath, null);
    }

    public static NMRData getFID(String fpath, File nusFile) throws IOException {
        StringBuilder bpath = new StringBuilder(fpath);
        try {
            if (NMRViewData.findFID(bpath)) {
                return new NMRViewData(bpath.toString());
            } else if (RS2DData.findFID(bpath)) {
                return new RS2DData(bpath.toString(), nusFile);
            } else if (BrukerData.findFID(bpath)) {
                return new BrukerData(bpath.toString(), nusFile);
            } else if (VarianData.findFID(bpath)) {
                return new VarianData(bpath.toString());
            } else if (JCAMPData.findFID(bpath)) {
                return new JCAMPData(bpath.toString());
            } else if (NMRPipeData.findFID(bpath)) {
                return new NMRPipeData(bpath.toString(), nusFile);
            } else if (JeolDelta.findFID(bpath)) {
                return new JeolDelta(bpath.toString());
            } else {
                throw new IOException("FID not found: " + fpath);
            }
        } catch (NullPointerException nullE) {
            throw new IOException("Null pointer when reading " + fpath + " " + nullE.getMessage());
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
     * @param fpath absolute file path
     * @return an NMRData object
     * @throws IOException if an I/O error occurs
     * @see NMRData
     */
    public static NMRData getNMRData(String fpath) throws IOException {
        StringBuilder bpath = new StringBuilder(fpath);
        try {
            if (NMRViewData.findFID(bpath)) {
                return new NMRViewData(bpath.toString());
            } else if (RS2DData.findFID(bpath)) {
                return new RS2DData(bpath.toString(), null);
            } else if (BrukerData.findData(bpath)) {
                return new BrukerData(bpath.toString());
            } else if (VarianData.findFID(bpath)) {
                return new VarianData(bpath.toString());
            } else if (JCAMPData.findData(bpath)) {
                return new JCAMPData(bpath.toString());
            } else {
                throw new IOException("Dataset not found: " + fpath);
            }
        } catch (NullPointerException nullE) {
            return null;
        }
    } // end getFID

    /**
     * Load an NMRData object from the fpath. The NMRData will be loaded as either a dataset or an FID, depending on
     * the fpath.
     *
     * @param fpath   absolute file path
     * @param nusFile
     * @return An NMRData object
     * @throws IOException
     */
    public static NMRData loadNMRData(String fpath, File nusFile) throws IOException {
        StringBuilder bpath = new StringBuilder(fpath);
        try {
            if (NMRViewData.findFID(bpath)) {
                return new NMRViewData(bpath.toString());
            } else if (RS2DData.findFID(bpath)) {
                return new RS2DData(bpath.toString(), nusFile);
                // Most processed Bruker files would also have the fid present and pass the findFID check,
                // so must check if it's a dataset before checking for FID
            } else if (BrukerData.findData(bpath)) {
                return new BrukerData(bpath.toString());
            } else if (BrukerData.findFID(bpath)) {
                return new BrukerData(bpath.toString(), nusFile);
            } else if (VarianData.findFID(bpath)) {
                return new VarianData(bpath.toString());
            } else if (JCAMPData.findFID(bpath) || JCAMPData.findData(bpath)) {
                return new JCAMPData(bpath.toString());
            } else if (NMRPipeData.findFID(bpath)) {
                return new NMRPipeData(bpath.toString(), nusFile);
            } else if (JeolDelta.findFID(bpath)) {
                return new JeolDelta(bpath.toString());
            } else {
                throw new IOException("File could not be read: " + fpath);
            }
        } catch (NullPointerException nullE) {
            throw new IOException("Null pointer when reading " + fpath + " " + nullE.getMessage());
        }
    }

    /**
     * Check if specified path represents an NMR data file
     *
     * @param fpath absolute file path
     * @return a standardized file path
     * @see NMRData
     */
    public static String isFIDDir(String fpath) {
        StringBuilder bpath = new StringBuilder(fpath);
        if (BrukerData.findFID(bpath)) {
            return bpath.toString();
        } else if (VarianData.findFID(bpath)) {
            return bpath.toString();
        } else if (RS2DData.findFID(bpath)) {
            return bpath.toString();
        } else if (JCAMPData.findFID(bpath)) {
            return bpath.toString();
        } else if (JeolDelta.findFID(bpath)) {
            return bpath.toString();
        } else {
            return null;
        }
    }

    /**
     * Check if specified path represents an NMR data file
     *
     * @param fpath absolute file path
     * @return a standardized file path
     * @see NMRData
     */
    public static String isDatasetFile(String fpath) {
        StringBuilder bpath = new StringBuilder(fpath);
        if (NMRViewData.findFID(bpath) || RS2DData.findFID(bpath) || BrukerData.findData(bpath)
                || VarianData.findFID(bpath) || JCAMPData.findData(bpath)) {
            return bpath.toString();
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
    public static class PeekFiles extends SimpleFileVisitor<Path> {

        ArrayList<String> fileList = new ArrayList<>();

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
            if (attr.isRegularFile() && (file.endsWith("fid") || (file.endsWith("ser")) || file.toString().endsWith(".jdx") || file.toString().endsWith(".dx") || file.toString().endsWith(RS2DData.DATA_FILE_NAME))) {
                String fidPath = NMRDataUtil.isFIDDir(file.toString());
                if (fidPath != null) {
                    fileList.add(fidPath);
                }
            }
            return FileVisitResult.CONTINUE;
        }

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
        public ArrayList<String> getFiles() {
            return fileList;
        }
    } // end class PeekFiles

    /**
     * Scan the specified directory to find sub-directories that are NMR data
     * sets.
     *
     * @param path the path of the directory to scan
     * @return An ArrayList containing a list of NMR dataset paths.
     */
    public static ArrayList<String> findNMRDirectories(String path) {
        ArrayList<String> fileList = null;
        Path autodir = Paths.get(path);
        try {
            PeekFiles filePeeker = new PeekFiles();
            Files.walkFileTree(autodir, filePeeker);
            fileList = filePeeker.getFiles();

        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return fileList;

    }

    public static List<Path> findProcessedFiles(Path path) throws IOException {
        List<Path> result;
        try (Stream<Path> pathStream = Files.find(path, 1, (p, basicFileAttributes)
                        -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".nv") || name.endsWith(".ucsf")
                            || BrukerData.isProcessedFile(name);
                }
        )) {
            result = pathStream.collect(Collectors.toList());
        }
        return result;
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

    public static String getProcessedDataset(File localFile) {
        String datasetName = "";
        try {
            if (localFile.exists()) {
                List<Path> processed = findProcessedFiles(localFile.toPath());
                if (!processed.isEmpty()) {
                    processed.sort((o1, o2) -> {
                                try {
                                    FileTime time1 = Files.getLastModifiedTime(o1);
                                    FileTime time2 = Files.getLastModifiedTime(o2);
                                    return time1.compareTo(time2);
                                } catch (IOException ex) {
                                    return 0;
                                }
                            }
                    );
                    datasetName = processed.get(0).getFileName().toString();
                }
            }
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return datasetName;

    }

    public static List<DatasetSummary> scanDirectory(String scanDir, Path savePath) {
        List<DatasetSummary> items = new ArrayList<>();
        Path path1 = Paths.get(scanDir);
        if (path1.toFile().exists()) {
            var files = NMRDataUtil.findNMRDirectories(scanDir);
            for (String fileName : files) {
                try {
                    NMRData data = NMRDataUtil.getFID(fileName);
                    if (data != null) {
                        Path path2 = Paths.get(fileName);
                        Path path3 = path1.relativize(path2);
                        DatasetSummary datasetSummary = data.getDatasetSummary();
                        datasetSummary.setPath(path3.toString());
                        datasetSummary.setPresent(true);
                        datasetSummary.setProcessed(getProcessedDataset(path2.toFile()));
                        items.add(datasetSummary);
                    }
                } catch (IOException | IllegalArgumentException ex) {
                    log.warn(ex.getMessage(), ex);
                }
            }
            if (savePath != null) {
                DatasetSummary.saveItems(savePath, items);
            }
        }
        return items;
    }

    public static String calculateHash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(input.getBytes());
        Encoder base64 = Base64.getEncoder();
        return base64.encodeToString(digest.digest());
    }

    public static double[] autoPhase(NMRData nmrData) {
        int n = nmrData.getNPoints();
        Vec vec = new Vec(n, true);
        nmrData.readVector(0, vec);
        double lb = nmrData.getTN(0).contains("H") ? 2.0 : 10.0;
        Expd expD = new Expd(lb, 1.0, false);
        expD.eval(vec);
        vec.fft();
        int winSize = vec.getSize() / 256;
        winSize = Math.max(4, winSize);
        winSize = Math.min(64, winSize);
        double[] phases = vec.autoPhase(true, winSize, 25.0, 2, 360.0, 50.0);
        return phases;
    }
}
