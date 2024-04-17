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

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.nmrfx.fxutil.Fxml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * @author johnsonb
 */
public class DocWindowController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(DocWindowController.class);

    private Stage stage = null;

    @FXML
    private Button closeButton;

    @FXML
    private WebView webView;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        webView.getEngine().loadContent(FXMLController.getHTMLDocs());
    }

    public void load() {
        try {
            if (stage == null) {
                stage = new Stage();
                stage.setTitle("Documentation");
                Fxml.load(DocWindowController.class, "DocScene.fxml").withStage(stage);
            }
            stage.show();
        } catch (Exception e) {
            log.warn("Load error! {}", e.getMessage(), e);
        }
    }

    @FXML
    private void closeAction(ActionEvent event) {
        stage.close();
    }
}
