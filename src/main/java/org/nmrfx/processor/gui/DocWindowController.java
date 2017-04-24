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
package org.nmrfx.processor.gui;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 *
 * @author johnsonb
 */
public class DocWindowController implements Initializable {

    Stage stage = null;
    @FXML
    Button closeButton;

    @FXML
    WebView webView;

    private AutoComplete autoComplete;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        System.out.println("initializing doc window controller");
        System.out.println("init ");

        autoComplete = new AutoComplete();

        webView.getEngine().loadContent(FXMLController.getHTMLDocs());
    }

    public void load() {
        System.out.println("load docview");
        try {
            if (stage != null) {
                stage.show();
            } else {
                MainApp.docWindowController = this;

                Parent root = FXMLLoader.load(DocWindowController.class.getResource("/fxml/DocScene.fxml"));
                System.out.println("made root");

                Scene scene = new Scene(root);
                scene.getStylesheets().add("/styles/Styles.css");

                stage = new Stage();

                stage.setTitle("Documentation");
                stage.setScene(scene);
                stage.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("load error!");
        }
    }

    @FXML
    private void closeAction(ActionEvent event) {
        stage.close();
    }
}
