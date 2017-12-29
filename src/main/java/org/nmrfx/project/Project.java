/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.project;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetParameterFile;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.io.PeakReader;
import org.nmrfx.processor.datasets.peaks.io.PeakWriter;

/**
 *
 * @author Bruce Johnson
 */
public class Project {

    static final Pattern INDEX_PATTERN = Pattern.compile("^([0-9]+)_.*");
    static final Predicate<String> INDEX_PREDICATE = INDEX_PATTERN.asPredicate();
    static String[] SUB_DIR_TYPES = {"datasets", "molecules", "peaks", "shifts", "refshifts", "windows"};
    static final Map<String, Project> projects = new HashMap<>();
    static Project activeProject = null;
    Path projectDir = null;
    final String name;

    public Project(String name) {
        this.name = name;
        setActive();
    }

    class FileComparator implements Comparator<Path> {

        @Override
        public int compare(Path p1, Path p2) {
            String s1 = p1.getFileName().toString();
            String s2 = p2.getFileName().toString();
            int result;
            Optional<Integer> f1 = getIndex(s1);
            Optional<Integer> f2 = getIndex(s2);
            if (f1.isPresent() && !f2.isPresent()) {
                result = 1;
            } else if (!f1.isPresent() && f2.isPresent()) {
                result = -1;
            } else if (f1.isPresent() && f2.isPresent()) {
                int i1 = f1.get();
                int i2 = f2.get();
                result = Integer.compare(i1, i2);
            } else {
                if (s1.endsWith(".seq") && !s2.endsWith(".seq")) {
                    result = 1;
                } else if (!s1.endsWith(".seq") && s2.endsWith(".seq")) {
                    result = -1;
                } else if (s1.endsWith(".pdb") && !s2.endsWith(".pdb")) {
                    result = 1;
                } else if (!s1.endsWith(".pdb") && s2.endsWith(".pdb")) {
                    result = -1;
                } else {
                    result = s1.compareTo(s2);
                }

            }
            return result;
        }

    }
    
    public boolean hasDirectory() {
        return projectDir != null;
    }

    static Optional<Integer> getIndex(String s) {
        Optional<Integer> fileNum = Optional.empty();
        Matcher matcher = INDEX_PATTERN.matcher(s);
        if (matcher.matches()) {
            fileNum = Optional.of(Integer.parseInt(matcher.group(1)));
        }
        return fileNum;
    }

    static String getName(String s) {
        Matcher matcher = INDEX_PATTERN.matcher(s);
        String name;
        if (matcher.matches()) {
            name = matcher.group(1);
        } else {
            name = s;
        }
        return name;
    }

    public final void setActive() {
        activeProject = this;
    }
    
    public static Project getActive() {
        return activeProject;
    }

    public void createProject(Path projectDir) throws IOException {
        if (Files.exists(projectDir)) {
            throw new IllegalArgumentException("Project directory \"" + projectDir + "\" already exists");
        }
        FileSystem fileSystem = FileSystems.getDefault();
        Files.createDirectory(projectDir);
        for (String subDir : SUB_DIR_TYPES) {
            Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
            Files.createDirectory(subDirectory);
        }
        this.projectDir = projectDir;
    }

    public void loadProject(Path projectDir) throws IOException, IllegalStateException {
        FileSystem fileSystem = FileSystems.getDefault();

        String[] subDirTypes = {"datasets", "peaks"};
        if (projectDir != null) {
            for (String subDir : subDirTypes) {
                Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
                if (Files.exists(subDirectory) && Files.isDirectory(subDirectory) && Files.isReadable(subDirectory)) {
                    switch (subDir) {
                        case "datasets":
                            loadDatasets(subDirectory);
                            break;
                        case "peaks":
                            loadPeaks(subDirectory);
                            break;
                        default:
                            throw new IllegalStateException("Invalid subdir type");
                    }
                }

            }
        }
        this.projectDir = projectDir;
    }

    public void saveProject() throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        savePeakLists();
        saveDatasets();
    }

    void loadDatasets(Path directory) throws IOException {
        Pattern pattern = Pattern.compile("(.+)\\.(nv|ucsf)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            Files.list(directory).sequential().filter(path -> predicate.test(path.getFileName().toString())).
                    forEach(path -> {
                        System.out.println("read dataset: " + path.toString());
                        String pathName = path.toString();
                        String fileName = path.getFileName().toString();

                        try {
                            Dataset dataset = new Dataset(pathName, fileName, true);
                        } catch (IOException ex) {
                            Logger.getLogger(Project.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
        }
    }

    void saveDatasets() throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        List<Dataset> datasets = Dataset.datasets();
        Path datasetDir = projectDir.resolve("datasets");

        for (Dataset dataset : datasets) {
            File datasetFile = dataset.getFile();
            if (datasetFile != null) {
                Path currentPath = datasetFile.toPath();
                Path fileName = currentPath.getFileName();
                Path pathInProject = datasetDir.resolve(fileName);
                // fixme should we have option to copy file, rather than make  link
                // or add text file with path to original
                if (!Files.exists(pathInProject)) {
                    try {
                        Files.createLink(pathInProject, currentPath);
                    } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                        Files.createSymbolicLink(pathInProject, currentPath);
                    }
                }
                String parFilePath = DatasetParameterFile.getParameterFileName(pathInProject.toString());
                dataset.writeParFile(parFilePath);
            }
        }
    }

    void loadPeaks(Path directory) throws IOException {
        FileSystem fileSystem = FileSystems.getDefault();
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(directory, "*.xpk2")) {
                for (Path f : fileStream) {
                    String filePath = f.toString();
                    System.out.println("read peaks: " + f.toString());
                    PeakList peakList = PeakReader.readXPK2Peaks(f.toString());
                    String mpk2File = filePath.substring(0, filePath.length() - 4) + "mpk2";
                    Path mpk2Path = fileSystem.getPath(mpk2File);
                    if (Files.exists(mpk2Path)) {
                        PeakReader.readMPK2(peakList, mpk2Path.toString());
                    }

                }
            } catch (DirectoryIteratorException | IOException ex) {
                throw new IOException(ex.getMessage());
            }
        }
    }

    void savePeakLists() throws IOException {
        FileSystem fileSystem = FileSystems.getDefault();

        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        Path projectDir = this.projectDir;
        Path peakDirPath = Paths.get(projectDir.toString(), "peaks");
        Files.list(peakDirPath).forEach(path -> {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith(".xpk2") || fileName.endsWith(".mpk2")) {
                String listName = fileName.substring(0, fileName.length() - 5);
                if (PeakList.get(listName) == null) {
                    try {
                        Files.delete(path);
                    } catch (IOException ex) {
                        Logger.getLogger(Project.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            }
        });

        PeakList.peakListTable.values().stream().forEach(peakList -> {
            Path peakFilePath = fileSystem.getPath(projectDir.toString(), "peaks", peakList.getName() + ".xpk2");
            Path measureFilePath = fileSystem.getPath(projectDir.toString(), "peaks", peakList.getName() + ".mpk2");
            // fixme should only write if file doesn't already exist or peaklist changed since read
            try {
                try (FileWriter writer = new FileWriter(peakFilePath.toFile())) {
                    PeakWriter peakWriter = new PeakWriter();
                    peakWriter.writePeaksXPK2(writer, peakList);
                    writer.close();
                }
                if (peakList.hasMeasures()) {
                    try (FileWriter writer = new FileWriter(measureFilePath.toFile())) {
                        PeakWriter peakWriter = new PeakWriter();
                        peakWriter.writePeakMeasures(writer, peakList);
                        writer.close();
                    }
                }
            } catch (IOException | InvalidPeakException ioE) {
            }
        });
    }

}
