/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.utils;

import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 *
 * @author brucejohnson
 */
public class GUIUtils {

    public static boolean affirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.YES);
        boolean result = false;
        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && (response.get() == ButtonType.YES)) {
            result = true;
        }
        return result;
    }

    public static void warn(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static String input(String message) {
        return input(message, "");
    }

    public static String input(String message, String defaultValue) {
        TextInputDialog textDialog = new TextInputDialog(defaultValue);
        textDialog.setHeaderText(message);
        textDialog.setContentText("Value:");
        Optional<String> result = textDialog.showAndWait();
        if (result.isPresent()) {
            return result.get();
        } else {
            return "";
        }

    }

    public static double getTextWidth(String s, Font font) {
        Text text = new Text(s);
        text.setFont(font);
        final double width = text.getLayoutBounds().getWidth();
        return width;
    }

    public static String getPassword() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Password");
        dialog.setHeaderText("");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        PasswordField pwd = new PasswordField();
        HBox content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setSpacing(10);
        content.getChildren().addAll(new Label("Password:"), pwd);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return pwd.getText();
            }
            return null;
        });
        String pw = null;
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            pw = result.get();
        }
        return pw;
    }
}
