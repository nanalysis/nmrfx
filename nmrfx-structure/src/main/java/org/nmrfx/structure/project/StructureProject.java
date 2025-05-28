/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.project;

import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.*;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.project.GitBase;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.ParseException;
import org.nmrfx.structure.chemistry.Molecule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Bruce Johnson
 */
public class StructureProject extends ProjectBase {

    private static final Logger log = LoggerFactory.getLogger(StructureProject.class);
    static StructureProject activeProject = null;

    public StructureProject(String name) {
        super(name);
        activeProject = this;
        activeMol = null;
    }

    public static StructureProject getActive() {
        if (activeProject == null) {
            activeProject = new StructureProject("Untitled 1");
        }
        return activeProject;
    }

    @Override
    public Molecule getMolecule(String name) {
        return (Molecule) molecules.get(name);
    }

    public Molecule activeMol() {
        return (Molecule) MoleculeFactory.getActive();
    }

    public void loadStructureProject(String projectDir) throws IOException, MoleculeIOException, IllegalStateException {
        loadStructureProject(Paths.get(projectDir));
    }

    public void loadStructureSubDirs(Path projectDir) throws IOException, MoleculeIOException, IllegalStateException {
        String[] subDirTypes = {"star", "peaks", "molecules", "shifts", "refshifts"};
        if (projectDir != null) {
            FileSystem fileSystem = FileSystems.getDefault();
            boolean readSTAR3 = false;
            for (String subDir : subDirTypes) {
                Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
                if (Files.exists(subDirectory) && Files.isDirectory(subDirectory) && Files.isReadable(subDirectory)) {
                    switch (subDir) {
                        case "star" -> readSTAR3 = loadSTAR3(subDirectory);
                        case "molecules" -> {
                            if (!readSTAR3) {
                                loadMolecules(subDirectory);
                            }
                        }
                        case "peaks" -> {
                            if (!readSTAR3) {
                                loadProject(projectDir, "peaks");
                            } else {
                                loadProject(projectDir, "mpk2");
                            }
                        }
                        case "shifts" -> {
                            if (!readSTAR3) {
                                loadShiftFiles(subDirectory, false);
                            }
                        }
                        case "refshifts" -> loadShiftFiles(subDirectory, true);
                        default -> throw new IllegalStateException("Invalid subdir type");
                    }
                }

            }
        }
    }
    public void loadStructureProject(Path projectDir) throws IOException, MoleculeIOException, IllegalStateException {
        ProjectBase currentProject = getActive();
        setActive();

        loadProject(projectDir, "datasets");
        loadStructureSubDirs(projectDir);
        gitManager = new GitBase(this);

        setProjectDir(projectDir);
        gitManager.setProject(this);
        gitManager.gitOpen();
        currentProject.setActive();
    }

    public File getSTAR3FileName() {
        File directory = FileSystems.getDefault().getPath(projectDir.toString(), "star").toFile();
        Path starFile = FileSystems.getDefault().getPath(directory.toString(), projectDir.getFileName().toString() + ".str");
        return starFile.toFile();
    }

    public File getSTAR3FileName(Path directory) {
        Path starFile = FileSystems.getDefault().getPath(directory.toString(), projectDir.getFileName().toString() + ".str");
        return starFile.toFile();

    }

    @Override
    public void saveProject() throws IOException {
        ProjectBase currentProject = getActive();
        setActive();
        try {
            if (projectDir == null) {
                throw new IllegalArgumentException("Project directory not set");
            }
            super.saveProject();
            NMRStarWriter.writeAll(getSTAR3FileName());
            saveShifts(false);
            saveShifts(true);
        } catch (ParseException | InvalidPeakException | InvalidMoleculeException ex) {
            throw new IOException(ex.getMessage());
        }
        currentProject.setActive();
    }

    public boolean loadSTAR3(Path directory) throws IOException {
        File starFile = getSTAR3FileName(directory);
        boolean result = false;
        if (starFile.exists()) {
            try {
                NMRStarReader.read(starFile);
                result = true;
            } catch (ParseException ex) {
                throw new IOException(ex.getMessage());
            }
        }
        return result;
    }

    public void loadMolecules(Path directory) throws MoleculeIOException {
        Path sstructPath = null;
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(directory)) {
                for (Path path : fileStream) {
                    if (Files.isDirectory(path)) {
                        loadMoleculeEntities(path);
                    } else {
                        if (path.toString().endsWith(".2str")) {
                            sstructPath = path;
                        } else {
                            loadMolecule(path);
                        }
                    }

                }
            } catch (DirectoryIteratorException | IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        if (sstructPath != null) {
            try {
                List<String> content = Files.readAllLines(sstructPath);
                if (MoleculeFactory.getActive() != null) {
                    loadSecondaryStructure((Molecule) MoleculeFactory.getActive(), content);
                }
            } catch (IOException ioE) {
                throw new MoleculeIOException(ioE.getMessage());
            }
        }
    }

    public void loadSecondaryStructure(Molecule molecule, List<String> fileContent) {
        // fixme use yaml ?
        for (String s : fileContent) {
            if (s.contains(("vienna"))) {
                String[] fields = s.split(":");
                if (fields.length == 2) {
                    molecule.setDotBracket(fields[1].trim());
                }
            }
        }
    }

    public static void loadMolecule(Path file) throws MoleculeIOException {
        if (file.toString().endsWith(".pdb")) {
            PDBFile pdbReader = new PDBFile();
            pdbReader.readSequence(file.toString(), 0);
        } else if (file.toString().endsWith(".sdf")) {
            SDFile.read(file.toString(), null);
        } else if (file.toString().endsWith(".mol")) {
            SDFile.read(file.toString(), null);
        } else if (file.toString().endsWith(".seq")) {
            Sequence seq = new Sequence();
            seq.read(file.toString());
        }
        if (MoleculeFactory.getActive() == null) {
            throw new MoleculeIOException("Couldn't open any molecules");
        }
        log.info("active mol {}", MoleculeFactory.getActive().getName());
    }

    public void loadMoleculeEntities(Path directory) throws IOException {
        String molName = directory.getFileName().toString();
        Molecule mol = (Molecule) MoleculeFactory.newMolecule(molName);
        PDBFile pdbReader = new PDBFile();
        Pattern pattern = Pattern.compile("(.+)\\.(seq|pdb|mol|sdf)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.sequential().filter(path -> predicate.test(path.getFileName().toString())).
                        sorted(new FileComparator()).
                        forEach(path -> {
                            String pathName = path.toString();
                            String fileName = path.getFileName().toString();
                            Matcher matcher = pattern.matcher(fileName);
                            String baseName = matcher.group(1);

                            try {
                                if (fileName.endsWith(".seq")) {
                                    Sequence sequence = new Sequence();
                                    sequence.read(pathName);
                                } else if (fileName.endsWith(".pdb")) {
                                    if (mol.entities.isEmpty()) {
                                        pdbReader.readSequence(pathName, 0);
                                    } else {
                                        PDBFile.readResidue(pathName, null, mol, baseName);
                                    }
                                } else if (fileName.endsWith(".sdf")) {
                                    SDFile.read(pathName, null, mol, baseName);
                                } else if (fileName.endsWith(".mol")) {
                                    SDFile.read(pathName, null, mol, baseName);
                                }
                            } catch (MoleculeIOException molE) {
                                log.warn(molE.getMessage(), molE);
                            }

                        });
            }
        }
    }

    public void loadShiftFiles(Path directory, boolean refMode) throws IOException {
        Molecule mol = activeMol();
        Pattern pattern = Pattern.compile("(.+)\\.(txt|ppm)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.sequential().filter(path -> predicate.test(path.getFileName().toString())).
                        sorted(new FileComparator()).
                        forEach(path -> {
                            String fileName = path.getFileName().toString();
                            Optional<Integer> fileNum = getIndex(fileName);
                            int ppmSet = fileNum.orElse(0);
                            PPMFiles.readPPM(mol, path, ppmSet, refMode);
                        });
            }
        }
    }

   public void saveShifts(boolean refMode) throws IOException {
        Molecule mol = activeMol();
        if (mol == null) {
            return;
        }
        FileSystem fileSystem = FileSystems.getDefault();

        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        int nSets = refMode ? mol.getRefPPMSetCount() : mol.getPPMSetCount();
        for (int ppmSet = 0; ppmSet < nSets; ppmSet++) {
            String fileName = "ppm_" + ppmSet + ".txt";
            String subDir = refMode ? "refshifts" : "shifts";
            Path peakFilePath = fileSystem.getPath(projectDir.toString(), subDir, fileName);
            try (FileWriter writer = new FileWriter(peakFilePath.toFile())) {
                PPMFiles.writePPM(mol, writer, ppmSet, refMode);
            } catch (IOException ioE) {
                throw ioE;
            }
        }
    }

    @Override
    public void saveMemoryFile(DatasetBase datasetBase) {

    }
}
