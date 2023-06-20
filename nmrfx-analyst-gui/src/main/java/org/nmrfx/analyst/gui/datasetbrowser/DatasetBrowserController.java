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
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.nmrfx.analyst.gui.AnalystPrefs;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;


import java.net.URL;
import java.util.ResourceBundle;

import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * @author brucejohnson
 */
public class DatasetBrowserController implements Initializable, StageBasedController {

    private Stage stage;
    @FXML
    private TabPane datasetBrowserTabPane;
    private RemoteDatasetBrowserTabController remoteDatasetBrowserTab;
    private LocalDatasetBrowserTabController localDatasetBrowserTab;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        localDatasetBrowserTab = new LocalDatasetBrowserTabController(stageTitle -> stage.setTitle(stageTitle));
        datasetBrowserTabPane.getTabs().add(localDatasetBrowserTab.getTab());
        remoteDatasetBrowserTab = new RemoteDatasetBrowserTabController();
        datasetBrowserTabPane.getTabs().add(remoteDatasetBrowserTab.getTab());
        remoteDatasetBrowserTab.getTab().setDisable(remotePreferencesUnavailable());
        AnalystPrefs.getRemoteHostNameProperty().addListener(e -> remotePreferencesListener());
        AnalystPrefs.getRemoteUserNameProperty().addListener(e -> remotePreferencesListener());
    }

    private void remotePreferencesListener() {
        boolean remoteUnavailable = remotePreferencesUnavailable();
        remoteDatasetBrowserTab.getTab().setDisable(remoteUnavailable);
        if (remoteUnavailable && datasetBrowserTabPane.getSelectionModel().getSelectedItem() == remoteDatasetBrowserTab.getTab()) {
            datasetBrowserTabPane.getSelectionModel().select(localDatasetBrowserTab.getTab());
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

    public static DatasetBrowserController create() {
        return Fxml.load(DatasetBrowserController.class, "DatasetBrowserScene.fxml")
                .withNewStage("Dataset Browser")
                .getController();
    }
}
