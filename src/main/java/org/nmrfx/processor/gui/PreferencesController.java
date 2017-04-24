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
package org.nmrfx.processor.gui;

import org.nmrfx.processor.operations.NESTANMREx;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.PropertySheet;

/**
 *
 * @author johnsonb
 */
public class PreferencesController implements Initializable {

    @FXML
    PropertySheet prefSheet;
    ChangeListener<String> stringListener;
    ChangeListener<String> datasetListener;
    ChangeListener<String> locationListener;
    Stage stage;

    static File nestaNMR = null;
    static File datasetDir = null;
    static String recentDatasetsString = null;
    static String location = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        prefSheet.setPropertyEditorFactory(new NvFxPropertyEditorFactory());
        prefSheet.setMode(PropertySheet.Mode.CATEGORY);
        prefSheet.setModeSwitcherVisible(false);
        prefSheet.setSearchBoxVisible(false);

        stringListener = (ObservableValue<? extends String> observableValue, String string, String string2) -> {
            setNESTANMR(new File(string2.trim()));
        };
        datasetListener = (ObservableValue<? extends String> observableValue, String string, String string2) -> {
            setDatasetDirectory(new File(string2.trim()));
        };
        locationListener = (ObservableValue<? extends String> observableValue, String string, String string2) -> {
            setLocation(string2.trim());
        };
        FileOperationItem nestaFileItem = new FileOperationItem(stringListener, getNESTANMR().getPath(), "External Programs", "NESTA-NMR", "desc");
        ArrayList<String> locationChoices = new ArrayList<>();
        locationChoices.add("FID directory");
        locationChoices.add("Dataset directory");
        ChoiceOperationItem locationTypeItem = new ChoiceOperationItem(locationListener, getLocation(), locationChoices, "File Locations", "location", "Directory Location for Dataset");

        DirectoryOperationItem locationFileItem = new DirectoryOperationItem(datasetListener, getDatasetDirectory().getPath(), "File Locations", "Datasets", "desc");

        prefSheet.getItems().addAll(nestaFileItem, locationTypeItem, locationFileItem);

    }

    public static PreferencesController create(Stage parent) {
        FXMLLoader loader = new FXMLLoader(SpecAttrWindowController.class.getResource("/fxml/PreferencesScene.fxml"));
        PreferencesController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<PreferencesController>getController();
            stage.setTitle("Preferences");

            stage.initOwner(parent);
            controller.stage = stage;
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;
    }

    public Stage getStage() {
        return stage;
    }

    @FXML
    private void closeAction(ActionEvent event) {
        stage.close();
    }

    /**
     * Returns the NESTA-NMR preference, i.e. the executable external program for NESTA-NMR The preference is read from
     * the OS specific registry. If no such preference can be found, NESTA-NMR is returned.
     *
     * @return
     */
    public static File getNESTANMR() {
        if (nestaNMR == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            String filePath = prefs.get("NESTA-NMR", null);
            if (filePath != null) {
                nestaNMR = new File(filePath);
            } else {
                nestaNMR = NESTANMREx.getExecutable();
            }
        }
        return nestaNMR;
    }

    /**
     * Sets the file path of the NESTA-NMR program. The path is persisted in the OS specific registry.
     *
     * @param file the file or null to remove the path
     */
    public static void setNESTANMR(File file) {
        Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
        if (file != null) {
            nestaNMR = new File(file.getPath());
            NESTANMREx.setExecutable(file);
            prefs.put("NESTA-NMR", nestaNMR.getPath());
        } else {
            NESTANMREx.setExecutable(file);
            nestaNMR = null;
            prefs.remove("NESTA-NMR");
        }

    }

    /**
     * Returns the Directory for datasets,
     *
     * @return
     */
    public static File getDatasetDirectory() {
        if (datasetDir == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            String filePath = prefs.get("DATASET-DIR", null);
            if (filePath != null) {
                datasetDir = new File(filePath);
            } else {
                datasetDir = new File("");
            }
        }
        return datasetDir;
    }

    /**
     * Returns the Directory for datasets,
     *
     * @param file the file or null to remove the path
     */
    public static void setDatasetDirectory(File file) {
        Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
        if (file != null) {
            datasetDir = new File(file.getPath());
            prefs.put("DATASET-DIR", datasetDir.getPath());
        } else {
            datasetDir = null;
            prefs.remove("DATASET-DIR");
        }

    }

    /**
     * Returns the Directory for datasets,
     *
     * @return
     */
    public static String getLocation() {
        if (location == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            String value = prefs.get("LOCATION-TYPE", null);
            if (value != null) {
                location = value;
            } else {
                location = "FID Directory";
            }
        }
        return location;
    }

    /**
     * Returns the Directory for datasets,
     *
     * @param file the file or null to remove the path
     */
    public static void setLocation(String value) {
        Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
        if (value != null) {
            location = value;
            prefs.put("LOCATION-TYPE", value);
        } else {
            location = null;
            prefs.remove("LOCATION-TYPE");
        }

    }

    /**
     * Saves recently opened datasets. The path is persisted in the OS specific registry.
     *
     */
    public static void saveRecentDatasets(String fileName) {
        Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
        if (recentDatasetsString == null) {
            recentDatasetsString = prefs.get("RECENT-DATASETS", "");
        }
        String[] recentDatasets = recentDatasetsString.split("\n");
        Map<String, Long> datasetMap = new HashMap<>();
        for (String recentDatasetEntry : recentDatasets) {
            String[] entry = recentDatasetEntry.split(";");
            File file = new File(entry[0]);
            if (file.exists()) {
                datasetMap.put(entry[0], Long.valueOf(entry[1]));
            }
        }
        datasetMap.put(fileName, System.currentTimeMillis());
        StringBuilder sBuilder = new StringBuilder();
        datasetMap.entrySet().stream().sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())).limit(15).forEach(e1 -> {
            sBuilder.append(e1.getKey());
            sBuilder.append(';');
            sBuilder.append(String.valueOf(e1.getValue()));
            sBuilder.append("\n");
        });
        recentDatasetsString = sBuilder.toString();
        prefs.put("RECENT-DATASETS", recentDatasetsString);
    }

    public static List<Path> getRecentDatasets() {
        if (recentDatasetsString == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            recentDatasetsString = prefs.get("RECENT-DATASETS", "");
        }
        String[] recentDatasets = recentDatasetsString.split("\n");
        List<Path> result = new ArrayList<>();
        for (String recentDatasetEntry : recentDatasets) {
            String[] entry = recentDatasetEntry.split(";");
            File file = new File(entry[0]);
            if (file.exists()) {
                Path path = file.toPath();
                result.add(path);
            }
        }
        return result;
    }
}
