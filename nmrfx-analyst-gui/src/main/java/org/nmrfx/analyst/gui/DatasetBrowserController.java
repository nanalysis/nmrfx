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
package org.nmrfx.analyst.gui;

import com.jcraft.jsch.JSchException;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.controlsfx.control.tableview2.TableView2;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.utilities.RemoteDataset;
import org.nmrfx.utilities.RemoteDatasetAccess;
import org.nmrfx.utilities.UnZipper;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.ResourceBundle;

/**
 * @author brucejohnson
 */
public class DatasetBrowserController implements Initializable, StageBasedController {

    private static final Logger log = LoggerFactory.getLogger(DatasetBrowserController.class);

    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TableView2<RemoteDataset> tableView;
    @FXML
    private ChoiceBox<String> dirTypeChoiceBox;
    @FXML
    private TextField directoryTextField;
    @FXML
    private HBox hBox;
    String remoteDir = "";
    String localDir = "";
    FileSystem fileSystem = FileSystems.getDefault();
    RemoteDatasetAccess rdA = null;
    Button datasetButton;
    Button fetchButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        localDir = AnalystPrefs.getLocalDirectory();
        initToolBar();
        initTable();
        remoteDir = AnalystPrefs.getRemoteDirectory();
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    boolean initRemoteDatasetAccess() {
        if (rdA == null) {
            String remoteHost = AnalystPrefs.getRemoteHostName();
            String remoteUser = AnalystPrefs.getRemoteUserName();
            if (remoteHost.isEmpty()) {
                GUIUtils.warn("Remote Access", "No host set in preferences");
                return false;
            }
            if (remoteUser.isEmpty()) {
                GUIUtils.warn("Remote Access", "No remote user set in preferences");
                return false;
            }
            if (remoteDir.isEmpty()) {
                GUIUtils.warn("Remote Access", "No remote directory in preferences");
                return false;
            }

            rdA = new RemoteDatasetAccess(remoteUser, remoteHost);
            boolean usePassword = AnalystPrefs.getUseRemotePassword();
            if (usePassword && !rdA.passwordValid()) {
                String pw = GUIUtils.getPassword();
                if (pw != null) {
                    rdA.setPassword(pw);
                }
            }
            try {
                rdA.connect();
            } catch (JSchException ex) {
                GUIUtils.warn("Remote Access", "Can't open session " + ex.getMessage());
                return false;
            }
        }
        return true;
    }

    public static DatasetBrowserController create() {
        Stage stage = new Stage(StageStyle.DECORATED);
        stage.setTitle("Dataset Browser");
        DatasetBrowserController controller = Fxml.load(DatasetBrowserController.class, "DatasetBrowserScene.fxml")
                .withStage(stage)
                .getController();
        stage.show();
        return controller;
    }

    void initToolBar() {
        Button retrieveIndexButton = new Button("Index");
        retrieveIndexButton.setOnAction(e -> retrieveIndex());
        Button fidButton = new Button("FID");
        fidButton.setOnAction(e -> fetchDataset(true));
        datasetButton = new Button("Dataset");
        datasetButton.setOnAction(e -> fetchDataset(false));
        fetchButton = new Button("Fetch");
        fetchButton.setOnAction(e -> cacheDatasets());
        fetchButton.setDisable(true);
        toolBar.getItems().addAll(retrieveIndexButton, fidButton, datasetButton, fetchButton);
        Button button = GlyphsDude.createIconButton(FontAwesomeIcon.FOLDER_OPEN);
        button.setOnAction(e -> browseDirectory());
        hBox.getChildren().add(button);
    }

    void initTable() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ListChangeListener listener = (ListChangeListener) (ListChangeListener.Change c) -> {
            RemoteDataset rData = tableView.getSelectionModel().getSelectedItem();
            updateButtons(rData);

        };
        dirTypeChoiceBox.getItems().addAll("Local", "Remote");
        dirTypeChoiceBox.setValue("Local");
        directoryTextField.setText(localDir);
        directoryTextField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (localMode()) {
                    localDir = directoryTextField.getText();
                } else {
                    remoteDir = directoryTextField.getText();
                }
                setDirPrefs();
            }
        });
        dirTypeChoiceBox.valueProperty().addListener(e -> updateDirType());
        updateColumns();

        tableView.getSelectionModel().getSelectedIndices().addListener(listener);
    }

    boolean localMode() {
        return dirTypeChoiceBox.getValue().equals("Local");
    }

    void updateDirType() {
        if (!localMode()) {
            fetchButton.setDisable(false);
            directoryTextField.setText(remoteDir);
        } else {
            fetchButton.setDisable(true);
            directoryTextField.setText(localDir);
        }
        updateColumns();
        loadIndex();
    }

    void setDirPrefs() {
        if (localMode()) {
            AnalystPrefs.setLocalDirectory(localDir);
        } else {
            AnalystPrefs.setRemoteDirectory(remoteDir);
        }
    }

    void updateButtons(RemoteDataset rData) {
        boolean hasDataset = false;
        if ((rData != null) && !rData.getProcessed().isEmpty()) {
            String fileName = rData.getPath();
            File localDataset = fileSystem.getPath(getLocalDir().toString(), fileName, rData.getProcessed()).toFile();
            if (localDataset.exists()) {
                hasDataset = true;
            }
        }
        datasetButton.setDisable(!hasDataset);

    }

    void updateColumns() {
        tableView.getColumns().clear();
        StringConverter sConverter = new DefaultStringConverter();

        TableColumn<RemoteDataset, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(new PropertyValueFactory("Path"));

        TableColumn<RemoteDataset, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory("Time"));

        TableColumn<RemoteDataset, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory("User"));

        TableColumn<RemoteDataset, Boolean> presentCol = new TableColumn<>("InCache");
        presentCol.setCellValueFactory(new PropertyValueFactory("Present"));

        TableColumn<RemoteDataset, String> processedCol = new TableColumn<>("Dataset");
        processedCol.setCellValueFactory(new PropertyValueFactory("Processed"));

        TableColumn<RemoteDataset, String> sequenceCol = new TableColumn<>("Sequence");
        sequenceCol.setCellValueFactory(new PropertyValueFactory("Seq"));

        TableColumn<RemoteDataset, Integer> ndCol = new TableColumn<>("NDim");
        ndCol.setCellValueFactory(new PropertyValueFactory("nd"));

        TableColumn<RemoteDataset, Double> sfCol = new TableColumn<>("SF");
        sfCol.setCellValueFactory(new PropertyValueFactory("sf"));

        tableView.getColumns().addAll(pathCol, userCol, dateCol);
        if (!localMode()) {
            tableView.getColumns().add(presentCol);
        }
        tableView.getColumns().addAll(processedCol, sequenceCol, ndCol, sfCol);
    }

    void browseDirectory() {
        DirectoryChooser fileChooser = new DirectoryChooser();
        String curDir = directoryTextField.getText();
        String userDir = System.getProperty("user.home");
        File initialDir;
        if (curDir.isEmpty()) {
            initialDir = new File(userDir);
        } else {
            initialDir = new File(curDir);
            if (!initialDir.exists()) {
                initialDir = new File(userDir);
            }
        }
        fileChooser.setInitialDirectory(initialDir);
        File file = fileChooser.showDialog(null);
        if (file != null) {
            directoryTextField.setText(file.toString());
        }
        loadIndex();

    }

    Path getLocalDir() {
        Path path;
        if (dirTypeChoiceBox.getValue().equals("Local")) {
            File file = new File(directoryTextField.getText());
            path = file.toPath();
        } else {
            String userdir = System.getProperty("user.home");
            path = fileSystem.getPath(userdir, "NMRFx_Remote_Datasets", "data");
        }
        return path;
    }

    File getLocalIndexFile() {
        Path path = getLocalDir();
        File localFile = fileSystem.getPath(path.getParent().toString(), "nmrfx_index.json").toFile();
        return localFile;
    }

    void scanAndLoad() {
        localDir = directoryTextField.getText();
        Path jsonPath = Paths.get(localDir, "nmrfx_index.json");
        ObservableList<RemoteDataset> items = FXCollections.observableArrayList();
        if (jsonPath.toFile().exists()) {
            try {
                RemoteDataset.loadListFromFile(jsonPath.toFile());
                items.addAll(RemoteDataset.getDatasets());
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        tableView.setItems(items);
        scanTask();
    }

    void scanTask() {
        final String scanDir = directoryTextField.getText();
        final Path outPath = Paths.get(scanDir, "nmrfx_index.json");
        Task<List<RemoteDataset>> task = new Task<List<RemoteDataset>>() {
            @Override
            protected List<RemoteDataset> call() throws Exception {
                ConsoleUtil.runOnFxThread(() -> stage.setTitle("Dataset Browser: Scanning"));

                List<RemoteDataset> results = NMRDataUtil.scanDirectory(scanDir, outPath);
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        ObservableList<RemoteDataset> items = FXCollections.observableArrayList();
                        items.addAll(results);
                        tableView.setItems(items);
                    }
                });
                ConsoleUtil.runOnFxThread(() -> stage.setTitle("Dataset Browser"));

                return results;
            }
        };
        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
    }

    void retrieveIndex() {
        if (localMode()) {
            scanAndLoad();
            return;
        }
        Path path = getLocalDir();
        File dir = path.toFile();
        System.out.println("dir is " + dir.toString());
        if (!dir.exists()) {
            System.out.println("create");
            try {
                Files.createDirectories(path);
            } catch (IOException ex) {
                GUIUtils.warn("Fetch", "Can't create directory");
                return;
            }
        }
        File localFile = fileSystem.getPath(path.getParent().toString(), "nmrfx_index.json").toFile();
        System.out.println("local " + localFile);
        String remoteFile = remoteDir + "/scripts/test.json";
        if (initRemoteDatasetAccess()) {
            boolean ok = rdA.fetchFile(remoteFile, localFile);
            if (ok) {
                loadIndex();
            }
        }
    }

    void loadIndex() {
        setDirPrefs();
        if (localMode()) {
            scanAndLoad();
            return;
        }
        File localFile = getLocalIndexFile();
        ObservableList<RemoteDataset> items = FXCollections.observableArrayList();
        if (localFile.exists()) {
            try {
                RemoteDataset.loadListFromFile(localFile);
                items.addAll(RemoteDataset.getDatasets());
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
            scanDirectory(RemoteDataset.getDatasets());
        }
        tableView.setItems(items);
    }

    void cacheDatasets() {
        if (localMode()) {
            return;
        }
        var rDataSets = tableView.getSelectionModel().getSelectedItems();
        for (var rData : rDataSets) {
            if (rData != null) {
                String fileName = rData.getPath();
                File file = new File(fileName);
                String fileRoot = file.getParent();
                File localFileDir = fileSystem.getPath(getLocalDir().toString(), fileRoot).toFile();
                if (!rData.isPresent()) {
                    try {
                        if (initRemoteDatasetAccess()) {
                            String remoteFile = remoteDir + "/data/" + fileRoot + ".zip";
                            File localZipFile = fileSystem.getPath(getLocalDir().toString(), fileRoot + ".zip").toFile();
                            rdA.fetchFile(remoteFile, localZipFile);
                            UnZipper unZipper = new UnZipper(localFileDir, localZipFile.toString());
                            unZipper.unzip();
                            Files.delete(localZipFile.toPath());
                            rData.setPresent(true);
                            tableView.refresh();
                        } else {
                            return;
                        }
                    } catch (IOException ex) {
                        var title = "Retrieve Selected Data";
                        GUIUtils.warn(title, "Error: " + ex.getMessage());
                        break;
                    }
                }

            }
        }
    }

    void fetchDataset(boolean useFID) {
        RemoteDataset rData = tableView.getSelectionModel().getSelectedItem();
        if (rData != null) {
            String fileName = rData.getPath();
            File localFile = fileSystem.getPath(getLocalDir().toString(), fileName).toFile();
            if (localMode() && !localFile.exists()) {
                GUIUtils.warn("Fetch", "File doesn't exist: " + localFile.toString());
                return;
            }
            FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
            try {
                if (!useFID && !rData.getProcessed().isEmpty()) {
                    File localDataset = fileSystem.getPath(getLocalDir().toString(), fileName, rData.getProcessed()).toFile();
                    if (localDataset.exists()) {
                        controller.openDataset(localDataset, false, true);
                    }
                } else {
                    if (!rData.isPresent()) {
                        if (initRemoteDatasetAccess()) {
                            File file = new File(fileName);
                            String fileRoot = file.getParent();
                            String remoteFile = remoteDir + "/data/" + fileRoot + ".zip";
                            File localZipFile = fileSystem.getPath(getLocalDir().toString(), fileRoot + ".zip").toFile();
                            rdA.fetchFile(remoteFile, localZipFile);
                            File localFileDir = fileSystem.getPath(getLocalDir().toString(), fileRoot).toFile();
                            UnZipper unZipper = new UnZipper(localFileDir, localZipFile.toString());
                            unZipper.unzip();
                            localZipFile.delete();
                            rData.setPresent(true);
                            tableView.refresh();
                        } else {
                            return;
                        }
                    }
                    controller.openFile(localFile.toString(), true, false);
                }
            } catch (IOException ex) {
                String mode = useFID ? "FID" : "Dataset";
                GUIUtils.warn("Open " + mode, "Error opening: " + ex.getMessage());
            }
        }
    }

    void scanDirectory(List<RemoteDataset> items
    ) {
        String localPathString = getLocalDir().toString();
        for (RemoteDataset rData : items) {
            String fileName = rData.getPath();
            File localFile = fileSystem.getPath(localPathString, fileName).toFile();
            rData.setProcessed(NMRDataUtil.getProcessedDataset(localFile));
            rData.setPresent(localFile.exists());
        }
    }

}
