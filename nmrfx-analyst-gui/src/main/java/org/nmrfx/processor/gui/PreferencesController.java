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

import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.controlsfx.control.PropertySheet;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.operations.NESTANMREx;
import org.nmrfx.utils.properties.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * @author johnsonb
 */
public class PreferencesController implements Initializable, StageBasedController {
    private static final Logger log = LoggerFactory.getLogger(PreferencesController.class);
    private static final Map<String, String> recentMap = new HashMap<>();

    private static File nestaNMR = null;
    private static File datasetDir = null;
    private static String location = null;
    private static Integer nProcesses = null;
    private static BooleanProperty useImmediateModeProp = null;
    private static BooleanProperty useNVJMouseModeProp = null;
    private static BooleanProperty autoAddPeaksProp = null;
    private static IntegerProperty tickFontSizeProp = null;
    private static IntegerProperty labelFontSizeProp = null;
    private static IntegerProperty peakFontSizeProp = null;
    private static BooleanProperty fitPeakShapeProp = null;
    private static BooleanProperty constrainPeakShapeProp = null;
    private static DoubleProperty peakShapeDirectFactorProp = null;
    private static DoubleProperty peakShapeIndirectFactorProp = null;
    private static BooleanProperty projectSaveProp = null;
    private static IntegerProperty projectSaveIntervalProp = null;
    private static StringProperty rnaModelProp = null;

    private static BooleanProperty autoProcessData = null;
    private static BooleanProperty useFIDParApodization = null;
    private static BooleanProperty useFIDParPhases = null;
    private static BooleanProperty doAutoPhase = null;
    private static BooleanProperty doAutoPhase1 = null;
    private static BooleanProperty loadMoleculeIfPresent = null;


    @FXML
    PropertySheet prefSheet;
    ChangeListener<String> stringListener;
    ChangeListener<String> datasetListener;
    ChangeListener<String> locationListener;
    ChangeListener<Integer> nprocessListener;
    Stage stage;


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
        FileOperationItem nestaFileItem = new FileOperationItem(prefSheet, stringListener, getNESTANMR().getPath(), "External Programs", "NESTA-NMR", "desc");
        ArrayList<String> locationChoices = new ArrayList<>();
        locationChoices.add("FID directory");
        locationChoices.add("Dataset directory");
        ChoiceOperationItem locationTypeItem = new ChoiceOperationItem(prefSheet, locationListener, getLocation(), locationChoices, "File Locations", "location", "Directory Location for Dataset");

        DirectoryOperationItem locationFileItem = new DirectoryOperationItem(prefSheet, datasetListener, getDatasetDirectory().getPath(), "File Locations", "Datasets", "desc");

        int nProcessesDefault = Runtime.getRuntime().availableProcessors() / 2;
        IntRangeOperationItem nProcessesItem = new IntRangeOperationItem(prefSheet, nprocessListener,
                nProcessesDefault, 1, 32, "Processor", "NProcesses",
                "How many parallel processes to run during processing");

        IntRangeOperationItem ticFontSizeItem = new IntRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    tickFontSizeProp.setValue((Integer) c);
                },
                getTickFontSize(), 1, 32, "Spectra", "TicFontSize", "Font size for tic mark labels");

        IntRangeOperationItem labelFontSizeItem = new IntRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    labelFontSizeProp.setValue((Integer) c);
                },
                getLabelFontSize(), 1, 32, "Spectra", "LabelFontSize", "Font size for axis labels");

        IntRangeOperationItem peakFontSizeItem = new IntRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    peakFontSizeProp.setValue((Integer) c);
                },
                getPeakFontSize(), 1, 32, "Spectra", "PeakFontSize", "Font size for peak box labels");

        BooleanOperationItem useImmediateModeItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    useImmediateModeProp.setValue((Boolean) c);
                    PolyChart.updateImmediateModes((Boolean) c);
                    setBoolean("IMMEDIATE_MODE", (Boolean) c);
                },
                getUseImmediateMode(), "Spectra", "UseImmediateMode", "Don't multi-thread drawing");

        BooleanOperationItem useNvJMouseItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    useNVJMouseModeProp.setValue((Boolean) c);
                    setBoolean("NVJ_SCROLL_MODE", (Boolean) c);
                },
                getUseNvjMouseMode(), "Spectra", "UseNvJMouseMode", "Use original NvJ scrolling modes");

        BooleanOperationItem autoAddPeaksItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    autoAddPeaksProp.setValue((Boolean) c);
                    setBoolean("AUTOADD_PEAKS_MODE", (Boolean) c);
                },
                getAutoAddPeakMode(), "Spectra", "Auto Add Peaks", "Add peak list when adding dataset");



        BooleanOperationItem fitPeakShapeItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    fitPeakShapeProp.setValue((Boolean) c);
                },
                getFitPeakShape(), "Peak", "FitPeakShape", "Fit Non-Lorentzian Peak Shapes");

        BooleanOperationItem constrainPeakShapeItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    constrainPeakShapeProp.setValue((Boolean) c);
                },
                getConstrainPeakShape(), "Peak", "ConstrainPeakShape", "Constrain Non-Lorentzian Peak Shapes");

        DoubleRangeOperationItem peakShapeDirectItem = new DoubleRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    peakShapeDirectFactorProp.setValue((Double) c);
                },
                getPeakShapeDirectFactor(), 0.0, 1.5, 0.0, 1.5, "Peak", "PeakShapeDirect", "Shape factor for direct dimension");

        DoubleRangeOperationItem peakShapeInirectItem = new DoubleRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    peakShapeIndirectFactorProp.setValue((Double) c);
                },
                getPeakShapeIndirectFactor(), 0.0, 1.5, 0.0, 1.5, "Peak", "PeakShapeIndirect", "Shape factor for indirect dimension");

        IntRangeOperationItem projectSaveIntervalItem = new IntRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    projectSaveIntervalProp.setValue((Integer) c);
                    setInteger("PROJECT_SAVE_INTERVAL", (Integer) c);
                    GUIProject.projectSaveInterval((Integer) c);
                },
                getProjectSaveInterval(), 1, 120, "Project", "SaveInterval", "Project save interval (minutes)");

        BooleanOperationItem projectSaveItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    projectSaveProp.setValue((Boolean) c);
                    setBoolean("PROJECT_SAVE", (Boolean) c);
                    GUIProject.projectSave((Boolean)c);
                },
                getProjectSave(), "Project", "AutoSave", "Auto Save Project on changes");

        DirectoryOperationItem rnaSSModelItem = new DirectoryOperationItem(prefSheet,
                (a, b, c) -> {
                    setString("RNA-MODEL", (String) c);
                    rnaModelProp.setValue((String) c);
                }

                , getRNAModelDirectory(), "RNA", "SS Model", "Directory for secondary predictino model");

        BooleanOperationItem useFIDParApodizationItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    useFIDParApodization.setValue((Boolean) c);
                    setBoolean("USE-APODIZATION", (Boolean) c);
                },
                getUseFIDParApodization(), "Load FID Actions", "Use Apodization", "Load apodization from FID parameters");

        BooleanOperationItem useFIDParPhasesItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    useFIDParPhases.setValue((Boolean) c);
                    setBoolean("USE-PHASES", (Boolean) c);
                },
                getUseFIDParPhases(), "Load FID Actions", "Use Phases", "Load phases from FID parameters");

        BooleanOperationItem autoProcessDataItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    autoProcessData.setValue((Boolean) c);
                    setBoolean("AUTO-PROCESS-DATA", (Boolean) c);
                },
                getAutoProcessData(), "Load FID Actions", "Auto Process", "Auto process data when loading (only 1D/2D)");

        BooleanOperationItem  doAutoPhaseItem = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    doAutoPhase.setValue((Boolean) c);
                    setBoolean("DO-AUTOPHASE", (Boolean) c);
                },
                getDoAutoPhase(), "Load FID Actions", "AutoPhase", "Autophase spectrum");

        BooleanOperationItem  doAutoPhase1Item = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    doAutoPhase1.setValue((Boolean) c);
                    setBoolean("DO-AUTOPHASE1", (Boolean) c);
                },
                getDoAutoPhase1(), "Load FID Actions", "AutoPhase1", "Include first order in autophasing");

        BooleanOperationItem  doLoadMoleculeIfPresent = new BooleanOperationItem(prefSheet,
                (a, b, c) -> {
                    loadMoleculeIfPresent.setValue((Boolean) c);
                    setBoolean("LOAD-MOLECULE", (Boolean) c);
                },
                getLoadMoleculeIfPresent(), "Load FID Actions", "Load Molecule", "Load molecule if present");


        prefSheet.getItems().addAll(nestaFileItem, locationTypeItem, locationFileItem,
                nProcessesItem, ticFontSizeItem, labelFontSizeItem, peakFontSizeItem, useImmediateModeItem, useNvJMouseItem,
                autoAddPeaksItem,
                fitPeakShapeItem, constrainPeakShapeItem, peakShapeDirectItem, peakShapeInirectItem, rnaSSModelItem,
                projectSaveItem, projectSaveIntervalItem,
                autoProcessDataItem, useFIDParApodizationItem, useFIDParPhasesItem, doAutoPhaseItem, doAutoPhase1Item,
                doLoadMoleculeIfPresent);
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public static PreferencesController create(Stage parent) {
        PreferencesController controller = Fxml.load(PreferencesController.class, "PreferencesScene.fxml")
                .withNewStage("Preferences", parent)
                .getController();
        controller.stage.show();
        return controller;
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
            Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
        Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
            Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
        Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
        if (file != null) {
            datasetDir = new File(file.getPath());
            prefs.put("DATASET-DIR", datasetDir.getPath());
        } else {
            datasetDir = null;
            prefs.remove("DATASET-DIR");
        }

    }

    public static String getRNAModelDirectory() {
        rnaModelProp = getString(rnaModelProp, "RNA-MODEL", "");
        return rnaModelProp.getValue();
    }

    public static void setRNAModelDirectory(String directory) {
        rnaModelProp.setValue(directory);
        setString("RNA-MODEL", directory);
    }


    /**
     * Returns the Directory for datasets,
     *
     * @return
     */
    public static String getLocation() {
        if (location == null) {
            Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
     * Sets the preferences location,
     *
     * @param value set preference location
     */
    public static void setLocation(String value) {
        Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
            datasetMenuItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().openFile(path.toString(), false, false));
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
        Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
            Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
            Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
     * Set the number of processes to use when processing,
     *
     * @param value the number of processes
     */
    public static void setNProcesses(Integer value) {
        Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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

    public static Boolean getUseImmediateMode() {
        useImmediateModeProp = getBoolean(useImmediateModeProp, "IMMEDIATE_MODE", false);
        return useImmediateModeProp.getValue();
    }

    public static Boolean getUseNvjMouseMode() {
        useNVJMouseModeProp = getBoolean(useNVJMouseModeProp, "NVJ_SCROLL_MODE", false);
        return useNVJMouseModeProp.getValue();
    }

    public static Boolean getAutoAddPeakMode() {
        autoAddPeaksProp = getBoolean(autoAddPeaksProp, "AUTOADD_PEAKS_MODE", false);
        return autoAddPeaksProp.getValue();
    }

    public static Boolean getFitPeakShape() {
        fitPeakShapeProp = getBoolean(fitPeakShapeProp, "FIT_PEAK_SHAPE", false);
        return fitPeakShapeProp.getValue();
    }

    public static Boolean getConstrainPeakShape() {
        constrainPeakShapeProp = getBoolean(constrainPeakShapeProp, "CONSTRAIN_PEAK_SHAPE", false);
        return constrainPeakShapeProp.getValue();
    }

    public static Double getPeakShapeDirectFactor() {
        peakShapeDirectFactorProp = getDouble(peakShapeDirectFactorProp, "PEAK_SHAPE_DIRECT", 0.0);
        return peakShapeDirectFactorProp.getValue();
    }

    public static Double getPeakShapeIndirectFactor() {
        peakShapeIndirectFactorProp = getDouble(peakShapeIndirectFactorProp, "PEAK_SHAPE_INDIRECT", 0.0);
        return peakShapeIndirectFactorProp.getValue();
    }

    public static Integer getProjectSaveInterval() {
        projectSaveIntervalProp = getInteger(projectSaveIntervalProp, "PROJECT_SAVE_INTERVAL", 30);
        return projectSaveIntervalProp.getValue();
    }
    public static Boolean getProjectSave() {
        projectSaveProp = getBoolean(projectSaveProp, "PROJECT_SAVE", false);
        return projectSaveProp.getValue();
    }
    public static Boolean getAutoProcessData() {
        autoProcessData = getBoolean(autoProcessData, "AUTO-PROCESS-DATA", true);
        return autoProcessData.getValue();
    }

    public static Boolean getUseFIDParApodization() {
        useFIDParApodization = getBoolean(useFIDParApodization, "USE-APODIZATION", true);
        return useFIDParApodization.getValue();
    }
    public static Boolean getUseFIDParPhases() {
        useFIDParPhases = getBoolean(useFIDParPhases, "USE-PHASES", true);
        return useFIDParPhases.getValue();
    }
    public static Boolean getDoAutoPhase() {
        doAutoPhase = getBoolean(doAutoPhase, "DO-AUTOPHASE", true);
        return doAutoPhase.getValue();
    }
    public static Boolean getDoAutoPhase1() {
        doAutoPhase1 = getBoolean(doAutoPhase1, "DO-AUTOPHASE1", true);
        return doAutoPhase1.getValue();
    }

    public static Boolean getLoadMoleculeIfPresent() {
        loadMoleculeIfPresent = getBoolean(loadMoleculeIfPresent, "LOAD-MOLECULE", true);
        return loadMoleculeIfPresent.getValue();
    }


    public static IntegerProperty getInteger(IntegerProperty prop, String name, int defValue) {
        if (prop == null) {
            Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
            Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
        Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
        if (value != null) {
            prefs.put(name, value.toString());
        } else {
            prefs.remove(name);
        }
    }

    public static void setInteger(String name, Integer value) {
        Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
        if (value != null) {
            prefs.put(name, value.toString());
        } else {
            prefs.remove(name);
        }
    }


    public static DoubleProperty getDouble(DoubleProperty prop, String name, double defValue) {
        if (prop == null) {
            Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
            Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
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
        Preferences prefs = Preferences.userNodeForPackage(AnalystApp.class);
        if (value != null) {
            prefs.put(name, value);
        } else {
            prefs.remove(name);
        }

    }

}
