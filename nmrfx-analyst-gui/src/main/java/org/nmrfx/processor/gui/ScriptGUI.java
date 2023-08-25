package org.nmrfx.processor.gui;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.nmrfx.processor.gui.controls.ProcessingCodeAreaUtil;

public class ScriptGUI {
    Stage stage = null;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 500, 500);
    CodeArea textArea = new CodeArea();
    ProcessingCodeAreaUtil codeAreaUtil;

    void showStage() {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Processing Script");
            stage.setWidth(500);
            stage.setScene(stageScene);
            borderPane.setCenter(textArea);
            codeAreaUtil = new ProcessingCodeAreaUtil(textArea);
            textArea.setEditable(false);
            textArea.setWrapText(true);

        }
        stage.show();
        stage.toFront();
    }

    public void replaceText(String text) {
        textArea.replaceText(text);
    }

    public String getText() {
        return textArea.getText();
    }
}
