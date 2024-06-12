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
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * This class shows an interface for looking at Git file differences for a project.
 *
 * @author mbeckwith
 *
 */
public class GitDiffController implements Initializable {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(GitDiffController.class);
    GitManager gitManager;
    @FXML
    ChoiceBox<DiffEntry> diffMenu = new ChoiceBox<>();
    Stage stage;
    @FXML
    TextArea fileText = new TextArea();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    DiffFormatter formatter = new DiffFormatter(outputStream);
    
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {        
        diffMenu.getSelectionModel().selectedItemProperty().addListener((ov, value, newValue) -> viewEntry(newValue));
    }

    public static GitDiffController create(GitManager gitManager) {
        FXMLLoader loader = new FXMLLoader(GitDiffController.class.getResource("/fxml/DiffScene.fxml"));
        GitDiffController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);

            controller = loader.getController();
            controller.stage = stage;
            controller.gitManager = gitManager;
            Screen screen = Screen.getPrimary();
            Rectangle2D screenSize = screen.getBounds();
            stage.toFront();
            stage.setY(screenSize.getHeight() - stage.getHeight());
        } catch (IOException ioE) {
            log.error(ioE.getMessage(), ioE);
        }

        return controller;

    }
    
    public void viewEntry(DiffEntry entry)  {   
        try {
            if (entry != null) {
                formatter.format(entry);
                String text = "Entry:" + entry + "\nfrom: " + entry.getOldPath() + "\nto: " + entry.getNewPath() + "\n\n";
                text += outputStream.toString();
                fileText.setText(text);
                formatter.flush();
                outputStream.reset();
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
    
    public ChoiceBox<DiffEntry> getEntryMenu() {
        return diffMenu;
    }
    
    public DiffFormatter getFormatter() {
        return formatter;
    }    
}
