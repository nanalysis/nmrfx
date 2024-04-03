/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui.spectra;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.python.AnalystPythonInterpreter;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.GUIScripter;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utilities.FileWatchListener;
import org.nmrfx.utilities.NMRFxFileWatcher;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author brucejohnson
 */
public class WindowIO implements FileWatchListener {

    private static final Logger log = LoggerFactory.getLogger(WindowIO.class);
    final private static Pattern STAGE_PATTERN1 = Pattern.compile("([0-9]+_stage)\\.(yaml)");
    final private static Pattern STAGE_PATTERN2 = Pattern.compile("(stage_[0-9]+)\\.(yaml)");

    Stage stage;
    BorderPane borderPane;
    ListView<String> listView;
    Path dir;
    private NMRFxFileWatcher watcher;

    public static void loadWindow() {
        FileChooser fileChooser = new FileChooser();
        if (ProjectBase.getActive() != null) {
            fileChooser.setInitialDirectory(ProjectBase.getActive().getDirectory().toFile());
        }
        fileChooser.setTitle("Open NMRFx Window file");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("NMR Window", "*.yaml")
        );
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                loadWindow(file);
            } catch (IOException ex) {
                GUIUtils.warn("Error reading window file", ex.getMessage());
            }
        }
    }

    public static List<String> findFavorites(Path directory) throws IOException {
        Pattern pattern = Pattern.compile("(.*)_fav\\.yaml");
        Predicate<String> predicate = pattern.asPredicate();
        List<String> files = null;
        if (Files.isDirectory(directory)) {
            try (Stream<Path> filePaths = Files.list(directory)) {
                files = filePaths.sequential().
                        map(path -> path.getFileName().toString()).
                        filter(fileName -> predicate.test(fileName)).
                        sorted().
                        map(fileName -> {
                            Matcher matcher = pattern.matcher(fileName);
                            System.out.println("match " + fileName + " " + matcher.matches());
                            return matcher.group(1);
                        }).
                        collect(Collectors.toList());
            }
        }
        return files;
    }

    public static void loadFavorite(String favName) {
        if (ProjectBase.getActive() != null) {
            Path projectDir = ProjectBase.getActive().getDirectory();
            Path path = projectDir.getFileSystem().getPath(projectDir.toString(), "windows", favName + "_fav.yaml");
            try {
                loadWindow(path.toFile());
            } catch (IOException ex) {
                GUIUtils.warn("Error reading window file", ex.getMessage());
            }
        }

    }

    public static void saveFavorite() {
        if (ProjectBase.getActive() == null) {
            GUIUtils.warn("Favorites", "No active project");
            return;
        }
        String favName = GUIUtils.input("Favorite name").trim();
        if (favName.length() > 0) {
            saveFavorite(favName);
        }
    }

    public static void saveFavorite(String favName) {
        if (ProjectBase.getActive() != null) {
            Path projectDir = ProjectBase.getActive().getDirectory();
            Path path = projectDir.getFileSystem().getPath(projectDir.toString(), "windows", favName + "_fav.yaml");
            try {
                saveWindow(AnalystApp.getFXMLControllerManager().getOrCreateActiveController(), path);
            } catch (IOException ex) {
                GUIUtils.warn("Error saving window file", ex.getMessage());
            }
        }

    }

    public static void loadWindow(File file) throws IOException {
        AnalystPythonInterpreter.exec("import nwyaml\\n");
        String fileContent = Files.readString(file.toPath());
        AnalystPythonInterpreter.set("yamlContents", fileContent);
        AnalystPythonInterpreter.set("yamlFileName", file.toString());
        AnalystPythonInterpreter.set("yamlFileNum", 1);
        AnalystPythonInterpreter.exec("nwyaml.loadYamlWin(yamlFileName, yamlContents, yamlFileNum)");
    }

    public static void loadWindows(Path directory) throws IOException {
        Predicate<String> predicate = STAGE_PATTERN1.asPredicate();
        Predicate<String> predicate2 = STAGE_PATTERN2.asPredicate();
        AnalystPythonInterpreter.exec("import nwyaml\\n");
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.sequential().filter(path
                                -> predicate.test(path.getFileName().toString()) || predicate2.test(path.getFileName().toString())).
                        sorted(new ProjectBase.FileComparator()).
                        forEach(path -> {
                            String fileName = path.getFileName().toString();
                            Optional<Integer> fileNum = ProjectBase.getIndex(fileName);
                            if (fileNum.isPresent()) {
                                try {
                                    String fileContent = Files.readString(path);
                                    AnalystPythonInterpreter.set("yamlContents", fileContent);
                                    AnalystPythonInterpreter.set("yamlFileName", path.toString());
                                    AnalystPythonInterpreter.set("yamlFileNum", fileNum.get());
                                    AnalystPythonInterpreter.exec("nwyaml.loadYamlWin(yamlFileName, yamlContents, yamlFileNum)");
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
            }
        }
    }

    public static void cleanWindows(Path projectDir) {
        Path directory = Paths.get(projectDir.toString(), "windows");
        Predicate<String> predicate = STAGE_PATTERN1.asPredicate();
        Predicate<String> predicate2 = STAGE_PATTERN2.asPredicate();

        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.sequential().filter(path
                                -> predicate.test(path.getFileName().toString()) || predicate2.test(path.getFileName().toString())).
                        forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ex) {
                                log.warn(ex.getMessage(), ex);
                            }
                        });
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
    }

    public static void saveWindow(FXMLController controller, Path path) throws IOException {
        AnalystPythonInterpreter.exec("import nwyaml\\n");
        FXMLController activeController = GUIScripter.getController();
        GUIScripter.setController(controller);
        AnalystPythonInterpreter.set("yamlFileName", path.toString());
        AnalystPythonInterpreter.exec("nwyaml.dumpYamlWin(yamlFileName)");
        GUIScripter.setController(activeController);
    }

    public static void saveWindows(Path projectDir) {
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        cleanWindows(projectDir);
        int i = 0;
        AnalystPythonInterpreter.exec("import nwyaml\\n");
        FXMLController activeController = GUIScripter.getController();
        for (FXMLController controller : AnalystApp.getFXMLControllerManager().getControllers()) {
            GUIScripter.setController(controller);
            String fileName = "stage_" + i + ".yaml";
            Path path = Paths.get(projectDir.toString(), "windows", fileName);
            AnalystPythonInterpreter.set("yamlFileName", path.toString());
            AnalystPythonInterpreter.exec("nwyaml.dumpYamlWin(yamlFileName)");
            i++;
        }
        GUIScripter.setController(activeController);
    }

    public void create() {
        stage = new Stage(StageStyle.DECORATED);
        borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("Favorites");
        ToolBar toolBar = new ToolBar();
        borderPane.setTop(toolBar);
        Button openButton = new Button("Open");
        openButton.setOnAction(e -> openSelectedFavorite());
        toolBar.getItems().add(openButton);
        listView = new ListView<>();
        borderPane.setCenter(listView);
        stage.show();
        listView.setOnMouseClicked(e -> listClicked(e));
        openButton.disableProperty().bind(listView.getSelectionModel().selectedIndexProperty().lessThan(0));
    }

    public void setupWatcher(Path dir) throws IOException {
        if ((watcher == null) || !this.dir.equals(dir)) {
            if (watcher != null) {
                Path path = Paths.get(this.dir.toFile().getAbsolutePath());
                NMRFxFileWatcher.remove(path.toString());
            }
            this.dir = dir;
            this.watcher = new NMRFxFileWatcher(dir.toFile());
            this.watcher.addListener(this);
            watcher.monitor();
        }
    }

    public Stage getStage() {
        return stage;
    }

    void listClicked(MouseEvent e) {
        if (e.getClickCount() > 1) {
            openSelectedFavorite();
        }
    }

    void openSelectedFavorite() {
        String item = listView.getSelectionModel().getSelectedItem();
        if (item != null) {
            loadFavorite(item);
        }
    }

    public void updateFavorites() {
        ProjectBase project = ProjectBase.getActive();
        if ((project != null) && project.hasDirectory()) {
            Path projectDir = project.getDirectory();
            Path path = projectDir.getFileSystem().getPath(projectDir.toString(), "windows");
            try {
                listView.getItems().clear();
                List<String> names = findFavorites(path);
                listView.getItems().addAll(names);
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
    }

    void updateFavoritesOnFxThread() {
        Fx.runOnFxThread(this::updateFavorites);
    }

    @Override
    public void onCreated(File file) {
        updateFavoritesOnFxThread();
    }

    @Override
    public void onModified(File file) {
        updateFavoritesOnFxThread();
    }

    @Override
    public void onDeleted(File file) {
        updateFavoritesOnFxThread();
    }

}
