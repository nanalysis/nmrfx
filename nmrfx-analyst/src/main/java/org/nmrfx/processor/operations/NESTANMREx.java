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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.operations;

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Bruce Johnson
 */
@PythonAPI("pyproc")
public class NESTANMREx extends MatrixOperation {
    private static final String ROOT_NAME = "test";
    private static final String IN_SUFFIX = "nestain";
    private static final String OUT_SUFFIX = "nestaout";

    private static File nestaExecutable = new File("NESTANMR");
    private final int iterations;
    private final int rwIterations;
    private final int method;
    private final double cutoff;
    private final double scaling;

    private final File nestDir;
    private final String schedFile;
    private final double[] phases;  // init zero values


    FileSystem fileSys = FileSystems.getDefault();
    ProcessBuilder pb = new ProcessBuilder();
    List<String> baseCmds = new ArrayList<>();

    /**
     * 2D phase array: [f1ph0, f1ph1, f2ph0, f2ph1].
     */
    /**
     * @param iterations   The number of NESTA iterations to execute
     * @param rwIterations The number of NESTA re-weighted iterations to execute
     * @param nestDirName  The name of the file in which to execute the code
     * @param schedFile    The schedule file
     * @param phaseList    List of phases to apply before processing
     */
    public NESTANMREx(int iterations, int rwIterations, String nestDirName, String schedFile, ArrayList phaseList) {
        this.iterations = iterations;
        this.rwIterations = rwIterations;
        this.method = 1; // L1
        this.scaling = 0.98;
        this.cutoff = 0.1;
        this.nestDir = new File(nestDirName);
        this.schedFile = schedFile;
        if ((phaseList != null) && (phaseList.size() != 0)) {
            this.phases = new double[phaseList.size()];
            for (int i = 0; i < phaseList.size(); i++) {
                this.phases[i] = (Double) phaseList.get(i);
            }
        } else {
            phases = null;
        }

        baseCmds.add(nestaExecutable.getPath());
        baseCmds.add("-q"); // quiet
        baseCmds.add("-D"); // read/write working files in double precision
        baseCmds.add("-b"); // keep working files
        baseCmds.add("-i"); // set number of iterations
        baseCmds.add(String.valueOf(iterations));
        baseCmds.add("-r"); // set number of re-weighted iterations
        baseCmds.add(String.valueOf(rwIterations));
        baseCmds.add("-m"); // set method 
        baseCmds.add(String.valueOf(method));
        baseCmds.add("-t"); // number of threads to use (only need 1 since we handle parallelization in Java
        baseCmds.add("1");
        baseCmds.add("-n"); // schedule file
        baseCmds.add(schedFile);
        baseCmds.add("-d"); // directory for NEST
        baseCmds.add(nestDir.getName());
        pb.directory(nestDir.getParentFile());
    }

    /**
     * @param iterations   The number of NESTA iterations to execute
     * @param rwIterations The number of NESTA re-weighted iterations to execute
     * @param nestDirName  The name of the file in which to execute the code
     * @param schedule     The sampling schedule object which contains a reference to the schedule file
     */
    public NESTANMREx(int iterations, double scaling, double cutoff, String nestDirName, String schedFile, ArrayList phaseList) {
        this.iterations = iterations;
        this.scaling = scaling;
        this.cutoff = cutoff;
        this.method = 0; // L0
        this.rwIterations = 1;
        this.nestDir = new File(nestDirName);
        this.schedFile = schedFile;
        if ((phaseList != null) && (phaseList.size() != 0)) {
            this.phases = new double[phaseList.size()];
            for (int i = 0; i < phaseList.size(); i++) {
                this.phases[i] = (Double) phaseList.get(i);
            }
        } else {
            phases = null;
        }

        baseCmds.add(nestaExecutable.getPath());
        baseCmds.add("-q"); // quiet
        baseCmds.add("-D"); // read/write working files in double precision
        baseCmds.add("-b"); // keep working files
        baseCmds.add("-i"); // set number of iterations
        baseCmds.add(String.valueOf(iterations));
        baseCmds.add("-c"); // Cutoff
        baseCmds.add(String.valueOf(cutoff));
        baseCmds.add("-s"); // Scaling
        baseCmds.add(String.valueOf(scaling));
        baseCmds.add("-m"); // set method
        baseCmds.add(String.valueOf(method));
        baseCmds.add("-t"); // number of threads to use (only need 1 since we handle parallelization in Java
        baseCmds.add("1");
        baseCmds.add("-n"); // schedule file
        baseCmds.add(schedFile);
        baseCmds.add("-d"); // directory for NEST
        baseCmds.add(nestDir.getName());
        pb.directory(nestDir.getParentFile());
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        return evalMatrix(vector);
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) throws ProcessingException {
        int fileIndex = matrix.getIndex();
        Path fileRoot = fileSys.getPath(nestDir.getPath(), ROOT_NAME);
        String matFileName;
        if (phases != null) {
            matrix.phase(phases);
        }
        try {
            matFileName = matrix.exportData(fileRoot.toString(), IN_SUFFIX, true);
        } catch (IOException ioE) {
            throw new ProcessingException(ioE.getMessage());
        }
        List<String> cmds = new ArrayList<>(baseCmds);
        cmds.add("-e");
        cmds.add(String.valueOf(fileIndex + 1));  // must add 1 as 0 means don't use existing
        cmds.add("-f");
        cmds.add(matFileName);
        int retValue;
        try {
            pb.command(cmds);
            pb.inheritIO();
            Process process = pb.start();
            retValue = process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new ProcessingException(e.getLocalizedMessage());
        }
        try {
            String outFileName = matrix.importData(fileRoot.toString(), OUT_SUFFIX, true);
            Files.delete(fileSys.getPath(matFileName));
            Files.delete(fileSys.getPath(matFileName + ".par"));
            Files.delete(fileSys.getPath(outFileName));
        } catch (IOException ioE) {
            throw new ProcessingException(ioE.getMessage());
        }
        return this;
    }

    public NESTANMREx clone() {
        ArrayList phaseList = null;
        if (phases != null) {
            phaseList = new ArrayList();
            for (double phase : phases) {
                phaseList.add(phase);
            }
        }
        if (method == 0) {
            return new NESTANMREx(iterations, scaling, cutoff, nestDir.toString(), schedFile, phaseList);
        } else {
            return new NESTANMREx(iterations, rwIterations, nestDir.toString(), schedFile, phaseList);
        }
    }

    public static void setExecutable(String path) {
        nestaExecutable = new File(path);
    }

    public static void setExecutable(File file) {
        nestaExecutable = new File(file.getPath());
    }

    public static File getExecutable() {
        return nestaExecutable;
    }
}
