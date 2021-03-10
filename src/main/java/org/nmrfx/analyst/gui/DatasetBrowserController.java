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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.nmrfx.utilities.RemoteDataset;
import org.controlsfx.control.tableview2.TableView2;
import org.nmrfx.utilities.RemoteDatasetAccess;
import org.nmrfx.utils.GUIUtils;

/**
 *
 * @author brucejohnson
 */
public class DatasetBrowserController implements Initializable {

    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TableView2<RemoteDataset> tableView;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initToolBar();
        initTable();
    }

    public Stage getStage() {
        return stage;
    }

    public static DatasetBrowserController create() {
        FXMLLoader loader = new FXMLLoader(DatasetBrowserController.class.getResource("/fxml/DatasetBrowserScene.fxml"));
        DatasetBrowserController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<DatasetBrowserController>getController();
            controller.stage = stage;
            stage.setTitle("Remote Datasets");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    void initToolBar() {
        Button fetchButton = new Button("Fetch");
        fetchButton.setOnAction(e -> fetchDatasets());
        toolBar.getItems().addAll(fetchButton);
    }

    void initTable() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateColumns();
        ListChangeListener listener = (ListChangeListener) (ListChangeListener.Change c) -> {
            int nSelected = tableView.getSelectionModel().getSelectedItems().size();
            boolean state = nSelected == 1;
        };
        tableView.getSelectionModel().getSelectedIndices().addListener(listener);
//        tableView.setOnMouseClicked(e -> {
//            if (e.getClickCount() == 2) {
//                if (!tableView.getSelectionModel().getSelectedItems().isEmpty()) {
//                    Noe peak = tableView.getSelectionModel().getSelectedItems().get(0);
//                    showPeakInfo(peak);
//                }
//            }
//        });
    }

    void updateColumns() {
        tableView.getColumns().clear();
        StringConverter sConverter = new DefaultStringConverter();

        TableColumn<RemoteDataset, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(new PropertyValueFactory("Path"));

        TableColumn<RemoteDataset, String> sequenceCol = new TableColumn<>("Sequence");
        sequenceCol.setCellValueFactory(new PropertyValueFactory("Seq"));

        TableColumn<RemoteDataset, Integer> ndCol = new TableColumn<>("NDim");
        ndCol.setCellValueFactory(new PropertyValueFactory("nd"));

        TableColumn<RemoteDataset, Double> sfCol = new TableColumn<>("SF");
        sfCol.setCellValueFactory(new PropertyValueFactory("sf"));

        tableView.getColumns().addAll(pathCol, sequenceCol, ndCol, sfCol);
    }

    void fetchDatasets() {
        FileSystem fileSystem = FileSystems.getDefault();
        String userdir = System.getProperty("user.home");
        Path path = fileSystem.getPath(userdir, "NMRFx_Remote_Datasets");
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
        File localFile = fileSystem.getPath(path.toString(), "index.json").toFile();
        System.out.println("local " + localFile);

        RemoteDatasetAccess rdA = new RemoteDatasetAccess();
        boolean ok = rdA.fetchIndex(localFile);
        System.out.println("ok " + ok);
        if (ok) {
            RemoteDataset.loadFromFile(localFile);
            ObservableList<RemoteDataset> items = FXCollections.observableArrayList();
            items.addAll(RemoteDataset.getDatasets());
            tableView.setItems(items);
        }
    }

}
