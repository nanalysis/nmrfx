package org.nmrfx.processor.gui.utils;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.util.Optional;

public class FileNameDialog {

    private FileNameDialog() {

    }

    public static Optional<File> getFileName(String initialName, Stage stage) {
        Dialog<File> dialog = new Dialog<>();
        dialog.setTitle("Select Dataset File");
        dialog.setHeaderText("Enter a file name or choose Browse");
        ButtonType browseButton = new ButtonType("Browse");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL, browseButton);
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        dialog.getDialogPane().setContent(grid);
        int row = 0;
        TextField fileNameField = new TextField(initialName);
        fileNameField.setPrefWidth(200);
        grid.add(new Label("Name: "), 0, row);
        grid.add(fileNameField, 1, row);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                // The value set in the formatter may not have been set yet so commit the value before retrieving
                fileNameField.commitValue();
                String fileName = "";
                if (!fileNameField.isDisable()) {
                    try {
                        fileName = fileNameField.getText();
                    } catch (Exception ex) {
                        GUIUtils.warn("Invalid Entry", "Numerical entries only");
                        return null;
                    }
                }
                return new File(fileName);
            } else if (dialogButton == browseButton) {
                FileChooser fileChooser = new FileChooser();
                File file = fileChooser.showSaveDialog(null);
                if (file != null) {
                    return file;
                }

            }
            return new File("");
        });
        File file = null;
        double x= stage.getX();
        double width = stage.getWidth();
        double y = stage.getY();
        double height = stage.getHeight();
        dialog.setX(x + width / 2.0);
        dialog.setY(y + height / 2.0);
        Optional<File> result = dialog.showAndWait();
        if (result.isPresent()) {
            file = result.get();
        }
        return Optional.ofNullable(file);
    }

}
