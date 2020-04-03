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
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.GUIScripter;
import org.nmrfx.processor.gui.MainApp;
import org.nmrfx.project.Project;
import org.nmrfx.utilities.FileWatchListener;
import org.nmrfx.utilities.NMRFxFileWatcher;
import org.python.util.PythonInterpreter;
import org.nmrfx.utils.GUIUtils;

/**
 *
 * @author brucejohnson
 */
public class WindowIO implements FileWatchListener {

    Stage stage;
    BorderPane borderPane;
    ListView<String> listView;
    private NMRFxFileWatcher watcher;
    Path dir;

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
        Project project = Project.getActive();
        if ((project != null) && project.hasDirectory()) {
            Path projectDir = project.getDirectory();
            Path path = projectDir.getFileSystem().getPath(projectDir.toString(), "windows");
            try {
                listView.getItems().clear();
                List<String> names = findFavorites(path);
                listView.getItems().addAll(names);
            } catch (IOException ex) {

            }
        }
    }

    public static void loadWindow() {
        FileChooser fileChooser = new FileChooser();
        if (Project.getActive() != null) {
            fileChooser.setInitialDirectory(Project.getActive().getDirectory().toFile());
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
            files = Files.list(directory).sequential().
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
        return files;
    }

    public static void loadFavorite(String favName) {
        if (Project.getActive() != null) {
            Path projectDir = Project.getActive().getDirectory();
            Path path = projectDir.getFileSystem().getPath(projectDir.toString(), "windows", favName + "_fav.yaml");
            try {
                loadWindow(path.toFile());
            } catch (IOException ex) {
                GUIUtils.warn("Error reading window file", ex.getMessage());
            }
        }

    }

    public static void saveFavorite() {
        if (Project.getActive() == null) {
            GUIUtils.warn("Favorites", "No active project");
            return;
        }
        String favName = GUIUtils.input("Favorite name").trim();
        if (favName.length() > 0) {
            saveFavorite(favName);
        }
    }

    public static void saveFavorite(String favName) {
        if (Project.getActive() != null) {
            Path projectDir = Project.getActive().getDirectory();
            Path path = projectDir.getFileSystem().getPath(projectDir.toString(), "windows", favName + "_fav.yaml");
            try {
                saveWindow(FXMLController.getActiveController(), path);
            } catch (IOException ex) {
                GUIUtils.warn("Error saving window file", ex.getMessage());
            }
        }

    }

    public static void loadWindow(File file) throws IOException {
        final PythonInterpreter interp = MainApp.getInterpreter();
        interp.exec("import nwyaml\\n");
        interp.exec("nwyaml.loadYamlWin('" + file.toString() + "')");
    }

    public static void loadWindows(Path directory) throws IOException {
        Pattern pattern = Pattern.compile("([0-9]+_stage)\\.(yaml)");
        Predicate<String> predicate = pattern.asPredicate();
        final PythonInterpreter interp = MainApp.getInterpreter();
        interp.exec("import nwyaml\\n");
        if (Files.isDirectory(directory)) {
            Files.list(directory).sequential().filter(path -> predicate.test(path.getFileName().toString())).
                    sorted(new Project.FileComparator()).
                    forEach(path -> {
                        String fileName = path.getFileName().toString();
                        Optional<Integer> fileNum = Project.getIndex(fileName);
                        if (fileNum.isPresent()) {
                            interp.exec("nwyaml.loadYamlWin('" + path.toString() + "'" + "," + String.valueOf(fileNum.get()) + ")");
                        }
                    });
        }
    }

    public static void saveWindow(FXMLController controller, Path path) throws IOException {
        PythonInterpreter interp = MainApp.getInterpreter();
        interp.exec("import nwyaml\\n");
        FXMLController activeController = GUIScripter.getController();
        GUIScripter.setController(controller);
        interp.exec("nwyaml.dumpYamlWin('" + path.toString() + "')");
        GUIScripter.setController(activeController);
    }

    public static void saveWindows(Path projectDir) throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        PythonInterpreter interp = MainApp.getInterpreter();
        int i = 0;
        interp.exec("import nwyaml\\n");
        FXMLController activeController = GUIScripter.getController();
        List<FXMLController> controllers = FXMLController.getControllers();
        for (FXMLController controller : controllers) {
            GUIScripter.setController(controller);
            String fileName = i + "_stage.yaml";
            Path path = Paths.get(projectDir.toString(), "windows", fileName);
            interp.exec("nwyaml.dumpYamlWin('" + path.toString() + "')");
            i++;
        }
        GUIScripter.setController(activeController);
    }

    void updateFavoritesOnFxThread() {
        if (Platform.isFxApplicationThread()) {
            updateFavorites();
        } else {
            Platform.runLater(() -> {
                updateFavorites();
            }
            );
        }
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
