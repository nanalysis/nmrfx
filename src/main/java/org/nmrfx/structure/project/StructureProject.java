/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.project;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.ParseException;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.chemistry.io.NMRStarWriter;
import org.nmrfx.chemistry.io.PDBFile;
import org.nmrfx.chemistry.io.PPMFiles;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.chemistry.io.Sequence;

/**
 *
 * @author Bruce Johnson
 */
public class StructureProject extends ProjectBase {

    public final Map<String, Molecule> molecules = new HashMap<>();
    public Molecule activeMol;
    static StructureProject activeProject = null;

    public final Map compoundMap;

    public StructureProject(String name) {
        super(name);
        activeProject = this;
        compoundMap = new HashMap();
        activeMol = null;
    }

    public static StructureProject getActive() {
        if (activeProject == null) {
            activeProject = new StructureProject("Untitled 1");
        }
        return activeProject;
    }

    public Collection<Molecule> getMolecules() {
        return molecules.values();
    }

    public Molecule getMolecule(String name) {
        return molecules.get(name);
    }

    public void putMolecule(Molecule molecule) {
        molecules.put(molecule.getName(), molecule);
    }

    public void clearAllMolecules() {
        molecules.clear();
    }

    public void removeMolecule(String name) {
        molecules.remove(name);
    }

    public Molecule activeMol() {
        return (Molecule) MoleculeFactory.getActive();
    }

    public void loadStructureProject(String projectDir) throws IOException, MoleculeIOException, IllegalStateException {
        loadStructureProject(Paths.get(projectDir));
    }

    public void loadStructureProject(Path projectDir) throws IOException, MoleculeIOException, IllegalStateException {
        ProjectBase currentProject = getActive();
        setActive();

        loadProject(projectDir, "datasets");
        FileSystem fileSystem = FileSystems.getDefault();

        String[] subDirTypes = {"star", "peaks", "molecules", "shifts", "refshifts"};
        if (projectDir != null) {
            boolean readSTAR3 = false;
            for (String subDir : subDirTypes) {
                System.out.println("read " + subDir + " " + readSTAR3);
                Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
                if (Files.exists(subDirectory) && Files.isDirectory(subDirectory) && Files.isReadable(subDirectory)) {
                    switch (subDir) {
                        case "star":
                            readSTAR3 = loadSTAR3(subDirectory);
                            break;
                        case "molecules":
                            if (!readSTAR3) {
                                loadMolecules(subDirectory);
                            }
                            break;
                        case "peaks":
                            if (!readSTAR3) {
                                System.out.println("readpeaks");
                                loadProject(projectDir, "peaks");
                            }
                            break;
                        case "shifts":
                            if (!readSTAR3) {
                                loadShiftFiles(subDirectory, false);
                            }
                            break;
                        case "refshifts":
                            loadShiftFiles(subDirectory, true);
                            break;
                        default:
                            throw new IllegalStateException("Invalid subdir type");
                    }
                }

            }
        }
        this.projectDir = projectDir;
        currentProject.setActive();
    }

    private File getSTAR3FileName() {
        File directory = FileSystems.getDefault().getPath(projectDir.toString(), "star").toFile();
        Path starFile = FileSystems.getDefault().getPath(directory.toString(), projectDir.getFileName().toString() + ".str");
        return starFile.toFile();
    }

    private File getSTAR3FileName(Path directory) {
        Path starFile = FileSystems.getDefault().getPath(directory.toString(), projectDir.getFileName().toString() + ".str");
        return starFile.toFile();

    }

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

    boolean loadSTAR3(Path directory) throws IOException {
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

    void loadMolecules(Path directory) throws MoleculeIOException {
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

    void loadSecondaryStructure(Molecule molecule, List<String> fileContent) {
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
            pdbReader.readSequence(file.toString(), false);
            System.out.println("read mol: " + file.toString());
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
        System.out.println("active mol " + MoleculeFactory.getActive().getName());
    }

    void loadMoleculeEntities(Path directory) throws MoleculeIOException, IOException {
        String molName = directory.getFileName().toString();
        Molecule mol = (Molecule) MoleculeFactory.newMolecule(molName);
        PDBFile pdbReader = new PDBFile();
        Pattern pattern = Pattern.compile("(.+)\\.(seq|pdb|mol|sdf)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            Files.list(directory).sequential().filter(path -> predicate.test(path.getFileName().toString())).
                    sorted(new FileComparator()).
                    forEach(path -> {
                        String pathName = path.toString();
                        String fileName = path.getFileName().toString();
                        Matcher matcher = pattern.matcher(fileName);
                        String baseName = matcher.group(1);
                        System.out.println("read mol: " + pathName);

                        try {
                            if (fileName.endsWith(".seq")) {
                                Sequence sequence = new Sequence();
                                sequence.read(pathName);
                            } else if (fileName.endsWith(".pdb")) {
                                if (mol.entities.isEmpty()) {
                                    pdbReader.readSequence(pathName, false);
                                } else {
                                    PDBFile.readResidue(pathName, null, mol, baseName);
                                }
                            } else if (fileName.endsWith(".sdf")) {
                                SDFile.read(pathName, null, mol, baseName);
                            } else if (fileName.endsWith(".mol")) {
                                SDFile.read(pathName, null, mol, baseName);
                            }
                        } catch (MoleculeIOException molE) {
                        }

                    });
        }
    }

    void loadShiftFiles(Path directory, boolean refMode) throws MoleculeIOException, IOException {
        Molecule mol = activeMol();
        Pattern pattern = Pattern.compile("(.+)\\.(txt|ppm)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            Files.list(directory).sequential().filter(path -> predicate.test(path.getFileName().toString())).
                    sorted(new FileComparator()).
                    forEach(path -> {
                        String fileName = path.getFileName().toString();
                        Optional<Integer> fileNum = getIndex(fileName);
                        int ppmSet = fileNum.isPresent() ? fileNum.get() : 0;
                        PPMFiles.readPPM(mol, path, ppmSet, refMode);
                    });
        }
    }

    void saveShifts(boolean refMode) throws IOException {
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
            String fileName = String.valueOf(ppmSet) + "_" + "ppm.txt";
            String subDir = refMode ? "refshifts" : "shifts";
            Path peakFilePath = fileSystem.getPath(projectDir.toString(), subDir, fileName);
            // fixme should only write if file doesn't already exist or peaklist changed since read
            try (FileWriter writer = new FileWriter(peakFilePath.toFile())) {
                PPMFiles.writePPM(mol, writer, ppmSet, refMode);
                writer.close();
            } catch (IOException ioE) {
                throw ioE;
            }
        }
    }

}
