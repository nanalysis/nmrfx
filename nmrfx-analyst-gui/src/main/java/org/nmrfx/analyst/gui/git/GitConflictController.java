/*
 * CoMD/NMR Software : A Program for Analyzing NMR Dynamics Data
 * Copyright (C) 2018-2019 Bruce A Johnson
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
package org.nmrfx.analyst.gui.git;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * This class shows an interface for resolving Git file conflicts for a project.
 *
 * @author mbeckwith
 *
 */
public class GitConflictController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(GitConflictController.class);
    GitManager gitManager;
    @FXML
    ChoiceBox<String> fileMenu = new ChoiceBox<>();
    Stage stage;
    @FXML
    TextArea fileText = new TextArea();
    
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        fileMenu.getSelectionModel().selectedItemProperty().addListener((ov, value, newValue) -> viewFile(newValue));
    }

    public static GitConflictController create(GitManager gitManager) {
        FXMLLoader loader = new FXMLLoader(GitConflictController.class.getResource("/fxml/ConflictScene.fxml"));
        GitConflictController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);

            controller = loader.getController();
            controller.stage = stage;
            controller.gitManager = gitManager;
            stage.setTitle("Resolve Git Conflicts");
            stage.show();
            Screen screen = Screen.getPrimary();
            Rectangle2D screenSize = screen.getBounds();
            stage.toFront();
            stage.setY(screenSize.getHeight() - stage.getHeight());
        } catch (IOException ioE) {
            log.error(ioE.getMessage(), ioE);
        }

        return controller;

    }
    
    public void viewFile(String fileName)  {   
        try {
            String text;
            try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
                text = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
            fileText.setText(text);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }
    
    public void saveChanges(ActionEvent event) {
        String fileName = fileMenu.getValue();
        String contents = fileText.getText();
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                writer.write(contents);
                writer.flush();
            }
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }
    
    public void close() {
        stage.hide();
    }

    public void show() {
        stage.show();
        stage.toFront();
    }
    
    public Stage getStage() {
        return stage;
    }
    
    public ChoiceBox<String> getFileMenu() {
        return fileMenu;
    }
    
}
