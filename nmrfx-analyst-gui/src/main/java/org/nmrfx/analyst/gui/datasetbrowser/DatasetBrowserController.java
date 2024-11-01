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

package org.nmrfx.analyst.gui.datasetbrowser;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.AnalystPrefs;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.processor.gui.FXMLController;


import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * @author brucejohnson
 */
public class DatasetBrowserController implements Initializable, StageBasedController {

    private Stage stage;
    @FXML
    private TabPane datasetBrowserTabPane;
    List<DatasetBrowserTabController> tabControllers = new ArrayList<>();
    private RemoteDatasetBrowserTabController remoteDatasetBrowserTabController;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        LocalDatasetBrowserTabController localDatasetBrowserTabController = new LocalDatasetBrowserTabController(stageTitle -> stage.setTitle(stageTitle), this);
        getTabs().add(localDatasetBrowserTabController.getTab());
        tabControllers.add(localDatasetBrowserTabController);
        remoteDatasetBrowserTabController = new RemoteDatasetBrowserTabController();
        getTabs().add(remoteDatasetBrowserTabController.getTab());
        tabControllers.add(remoteDatasetBrowserTabController);
        remoteDatasetBrowserTabController.getTab().setDisable(remotePreferencesUnavailable());
        AnalystPrefs.getRemoteHostNameProperty().addListener(e -> remotePreferencesListener());
        AnalystPrefs.getRemoteUserNameProperty().addListener(e -> remotePreferencesListener());

    }

    private void remotePreferencesListener() {
        boolean remoteUnavailable = remotePreferencesUnavailable();
        List<Tab> browserTabs = getTabs();
        if (remoteUnavailable) {
            browserTabs.remove(remoteDatasetBrowserTabController.getTab());
        } else if (!browserTabs.contains(remoteDatasetBrowserTabController.getTab())) {
            browserTabs.add(remoteDatasetBrowserTabController.getTab());
        }
    }

    private boolean remotePreferencesUnavailable() {
        String hostname = AnalystPrefs.getRemoteHostName();
        String username = AnalystPrefs.getRemoteUserName();
        return isEmpty(hostname) || isEmpty(username);
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public void show() {
        stage.toFront();
        stage.show();
    }

    public void setPosition() {
        FXMLController fxmlController = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        Stage controllerStage = fxmlController.getStage();
        Window window = controllerStage.getScene().getWindow();
        double x = window.getX();
        double y = window.getY();
        Bounds boundsInScene = fxmlController.getGridPaneCanvas().localToScene(fxmlController.getGridPaneCanvas().getBoundsInLocal());
        stage.setX(x + boundsInScene.getMinX() + 20);
        stage.setY(y);
        double width = Math.min(boundsInScene.getWidth(),900);
        double height = Math.max(boundsInScene.getHeight()/2, 500);
        stage.setWidth(width);
        stage.setHeight(height);
    }

    public static DatasetBrowserController create() {
        return Fxml.load(DatasetBrowserController.class, "DatasetBrowserScene.fxml")
                .withNewStage("Dataset Browser")
                .getController();
    }

    private List<Tab> getTabs() {
        return datasetBrowserTabPane.getTabs();
    }

}
