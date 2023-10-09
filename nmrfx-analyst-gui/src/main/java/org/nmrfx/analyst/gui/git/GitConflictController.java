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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;

import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.processor.gui.project.GUIProject;

/**
 * This class shows an interface for resolving Git file conflicts for a project.
 *
 * @author mbeckwith
 *
 */
public class GitConflictController implements Initializable {
    GitManager gitManager;
    @FXML
    ChoiceBox fileMenu = new ChoiceBox();
    Stage stage;
    GUIProject project = GUIProject.getActive();
    @FXML
    TextArea fileText = new TextArea();
    
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        fileMenu.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            public void changed(ObservableValue ov, String value, String newValue) {
                viewFile(newValue);
            }
        });
    }

    public static GitConflictController create(GitManager gitManager) {
        FXMLLoader loader = new FXMLLoader(GitConflictController.class.getResource("/fxml/ConflictScene.fxml"));
        GitConflictController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
//            scene.getStylesheets().add("/styles/consolescene.css");

            controller = loader.<GitConflictController>getController();
            controller.stage = stage;
            controller.gitManager = gitManager;
            stage.setTitle("Resolve Git Conflicts");
            stage.show();
            Screen screen = Screen.getPrimary();
            Rectangle2D screenSize = screen.getBounds();
            stage.toFront();
            stage.setY(screenSize.getHeight() - stage.getHeight());
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }
    
    public void viewFile(String fileName)  {   
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileName));
            String text = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            fileText.setText(text);
        } catch (IOException ex) { 
            ex.printStackTrace();
        }
    }
    
    public void saveChanges(ActionEvent event) {
        String fileName = fileMenu.getValue().toString();
        String contents = fileText.getText();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
            writer.write(contents);
            writer.flush();
            writer.close();
            System.out.println("Saved changes to file " + fileName);
        } catch (IOException ex) {
            ex.printStackTrace();
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
    
    public ChoiceBox getFileMenu() {
        return fileMenu;
    }
    
}
