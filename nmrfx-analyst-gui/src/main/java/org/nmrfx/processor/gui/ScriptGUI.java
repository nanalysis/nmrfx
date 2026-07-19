package org.nmrfx.processor.gui;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.nmrfx.utils.GUIUtils;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;


public class ScriptGUI {
    Stage stage = null;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 500, 500);

    CodeTextModel model = new CodeTextModel();
    CodeArea codeArea = new CodeArea(model);

    void showStage() {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Processing Script");
            stage.setWidth(500);
            stageScene.getStylesheets().add(ScriptGUI.class.getResource("/styles/python.css").toExternalForm());

            GUIUtils.applyTheme(stageScene);
            stage.setScene(stageScene);
            borderPane.setCenter(codeArea);
            codeArea.setSyntaxDecorator(new PythonSyntaxDecorator());
            codeArea.setEditable(false);
            codeArea.setWrapText(true);
        }
        stage.show();
        stage.toFront();
    }

    public void replaceText(String text) {
        codeArea.setText(text);
    }

    public String getText() {
        return codeArea.getText();
    }
}
