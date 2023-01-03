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

import org.nmrfx.utils.properties.DirectoryOperationItem;
import org.nmrfx.utils.properties.ChoiceOperationItem;
import org.nmrfx.utils.properties.IntRangeOperationItem;
import org.nmrfx.utils.properties.FileOperationItem;
import org.nmrfx.processor.operations.NESTANMREx;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.prefs.Preferences;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.PropertySheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author johnsonb
 */
public class PreferencesController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(PreferencesController.class);

    @FXML
    PropertySheet prefSheet;
    ChangeListener<String> stringListener;
    ChangeListener<String> datasetListener;
    ChangeListener<String> locationListener;
    ChangeListener<Integer> nprocessListener;
    Stage stage;

    static File nestaNMR = null;
    static File datasetDir = null;
    private static Map<String, String> recentMap = new HashMap<>();
    static String location = null;
    static Integer nProcesses = null;
    static IntegerProperty tickFontSizeProp = null;
    static IntegerProperty labelFontSizeProp = null;
    static IntegerProperty peakFontSizeProp = null;

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
        nprocessListener = (ObservableValue<? extends Integer> observableValue, Integer n1, Integer n2) -> {
            setNProcesses(n2);
        };
        FileOperationItem nestaFileItem = new FileOperationItem(stringListener, getNESTANMR().getPath(), "External Programs", "NESTA-NMR", "desc");
        ArrayList<String> locationChoices = new ArrayList<>();
        locationChoices.add("FID directory");
        locationChoices.add("Dataset directory");
        ChoiceOperationItem locationTypeItem = new ChoiceOperationItem(locationListener, getLocation(), locationChoices, "File Locations", "location", "Directory Location for Dataset");

        DirectoryOperationItem locationFileItem = new DirectoryOperationItem(datasetListener, getDatasetDirectory().getPath(), "File Locations", "Datasets", "desc");

        int nProcessesDefault = Runtime.getRuntime().availableProcessors() / 2;
        IntRangeOperationItem nProcessesItem = new IntRangeOperationItem(nprocessListener,
                nProcessesDefault, 1, 32, "Processor", "NProcesses",
                "How many parallel processes to run during processing");

        IntRangeOperationItem ticFontSizeItem = new IntRangeOperationItem(
                (a, b, c) -> {
                    tickFontSizeProp.setValue((Integer) c);
                },
                getTickFontSize(), 1, 32, "Spectra", "TicFontSize", "Font size for tic mark labels");

        IntRangeOperationItem labelFontSizeItem = new IntRangeOperationItem(
                (a, b, c) -> {
                    labelFontSizeProp.setValue((Integer) c);
                },
                getLabelFontSize(), 1, 32, "Spectra", "LabelFontSize", "Font size for axis labels");

        IntRangeOperationItem peakFontSizeItem = new IntRangeOperationItem(
                (a, b, c) -> {
                    peakFontSizeProp.setValue((Integer) c);
                },
                getPeakFontSize(), 1, 32, "Spectra", "PeakFontSize", "Font size for peak box labels");

        prefSheet.getItems().addAll(nestaFileItem, locationTypeItem, locationFileItem,
                nProcessesItem, ticFontSizeItem, labelFontSizeItem, peakFontSizeItem);

    }

    public static PreferencesController create(Stage parent) {
        FXMLLoader loader = new FXMLLoader(PreferencesController.class.getResource("/fxml/PreferencesScene.fxml"));
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
            log.warn(ioE.getMessage(), ioE);
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

    public PropertySheet getPrefSheet() {
        return prefSheet;
    }

    /**
     * Returns the NESTA-NMR preference, i.e. the executable external program
     * for NESTA-NMR The preference is read from the OS specific registry. If no
     * such preference can be found, NESTA-NMR is returned.
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
     * Sets the file path of the NESTA-NMR program. The path is persisted in the
     * OS specific registry.
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

    public static void setupRecentMenus(Menu recentFileMenuItem) {
        List<Path> recentFiles = PreferencesController.getRecentFiles();
        Set<Path> filesSet = new LinkedHashSet<>();
        filesSet.addAll(recentFiles);
        for (Path path : filesSet) {
            int count = path.getNameCount();
            int first = count - 3;
            first = Math.max(first, 0);
            Path subPath = path.subpath(first, count);
            MenuItem datasetMenuItem = new MenuItem(subPath.toString());
            datasetMenuItem.setOnAction(e -> FXMLController.getActiveController().openFile(path.toString(), false, false));
            recentFileMenuItem.getItems().add(datasetMenuItem);
        }
    }

    public static List<Path> getRecentFiles() {
        return getRecentFileItem("RECENT-FILES");
    }

    public static List<Path> getRecentProjects() {
        return getRecentFileItem("RECENT-PROJECTS");
    }

    public static void saveRecentProjects(String fileName) {
        saveRecentFileItems(fileName, "RECENT-PROJECTS");
    }

    public static void saveRecentFiles(String fileName) {
        saveRecentFileItems(fileName, "RECENT-FILES");
    }

    public static void saveRecentFileItems(String fileName, String mode) {
        Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
        String recentFileString = recentMap.get(mode);
        if (recentFileString == null) {
            recentFileString = prefs.get(mode, "");
            recentMap.put(mode, recentFileString);
        }
        String[] recentDatasets = recentFileString.split("\n");
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
        recentFileString = sBuilder.toString();
        recentMap.put(mode, recentFileString);
        prefs.put(mode, recentFileString);
    }

    public static List<Path> getRecentFileItem(String mode) {
        String recentFileString = recentMap.get(mode);
        if (recentFileString == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            recentFileString = prefs.get(mode, "");
            recentMap.put(mode, recentFileString);
        }
        String[] recentDatasets = recentFileString.split("\n");
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

    /**
     * Returns the Directory for datasets,
     *
     * @return
     */
    public static Integer getNProcesses() {
        if (nProcesses == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            String value = prefs.get("NPROCESSES", null);
            if (value != null) {
                nProcesses = Integer.parseInt(value);
            } else {
                nProcesses = Runtime.getRuntime().availableProcessors() / 2;
            }
        }
        return nProcesses;
    }

    /**
     * Returns the Directory for datasets,
     *
     * @param file the file or null to remove the path
     */
    public static void setNProcesses(Integer value) {
        Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
        if (value != null) {
            nProcesses = value;
            prefs.put("NPROCESSES", String.valueOf(value));
        } else {
            nProcesses = null;
            prefs.remove("NPROCESSES");
        }

    }

    public static Integer getTickFontSize() {
        tickFontSizeProp = getInteger(tickFontSizeProp, "TICK_FONT_SIZE", 12);
        return tickFontSizeProp.getValue();
    }

    public static Integer getLabelFontSize() {
        labelFontSizeProp = getInteger(labelFontSizeProp, "LABEL_FONT_SIZE", 12);
        return labelFontSizeProp.getValue();
    }

    public static Integer getPeakFontSize() {
        peakFontSizeProp = getInteger(peakFontSizeProp, "PEAK_FONT_SIZE", 12);
        return peakFontSizeProp.getValue();
    }

    public static IntegerProperty getInteger(IntegerProperty prop, String name, int defValue) {
        if (prop == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            String value = prefs.get(name, null);
            if (value != null) {
                prop = new SimpleIntegerProperty(Integer.parseInt(value));
            } else {
                prop = new SimpleIntegerProperty(defValue);
            }
        }
        return prop;
    }

    public static BooleanProperty getBoolean(BooleanProperty prop, String name, boolean defValue) {
        if (prop == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            String value = prefs.get(name, null);
            if (value != null) {
                prop = new SimpleBooleanProperty(Boolean.parseBoolean(value));
            } else {
                prop = new SimpleBooleanProperty(defValue);
            }
        }
        return prop;
    }

    public static void setBoolean(String name, Boolean value) {
        Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
        if (value != null) {
            prefs.put(name, value.toString());
        } else {
            prefs.remove(name);
        }
    }

    

    public static DoubleProperty getDouble(DoubleProperty prop, String name, double defValue) {
        if (prop == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            String value = prefs.get(name, null);
            if (value != null) {
                prop = new SimpleDoubleProperty(Double.parseDouble(value));
            } else {
                prop = new SimpleDoubleProperty(defValue);
            }
        }
        return prop;
    }

    public static StringProperty getString(StringProperty prop, String name, String defValue) {
        if (prop == null) {
            Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
            String value = prefs.get(name, null);
            if (value != null) {
                prop = new SimpleStringProperty(value);
            } else {
                prop = new SimpleStringProperty(defValue);
            }
        }
        return prop;
    }

    public static void setString(String name, String value) {
        Preferences prefs = Preferences.userNodeForPackage(MainApp.class);
        if (value != null) {
            prefs.put(name, value);
        } else {
            prefs.remove(name);
        }

    }

}
