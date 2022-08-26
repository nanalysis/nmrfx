/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.embed.swing.SwingFXUtils;

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
        return result.orElse("");
    }

    public static double getTextWidth(String s, Font font) {
        Text text = new Text(s);
        text.setFont(font);
        return text.getLayoutBounds().getWidth();
    }

    /**
     * Utility function to adjust the height of toolbars used in ControllerTools. The height of each toolbar is adjusted
     * to the largest prefHeight. The height of all the toolbar items are adjusted to the prefHeight of the largest
     * item in the first tool bar in the list.
     * @param toolBarList A list of toolbars to adjust
     */
    public static void toolbarAdjustHeights(List<ToolBar> toolBarList) {
        if (toolBarList.isEmpty()) {
            return;
        }
        List<List<Node>> toolBarsItems = new ArrayList<>();
        for (ToolBar toolBar: toolBarList) {
            toolBarsItems.add(toolBar.getItems());
        }
        // Set height of all toolbars to be the same
        double heightToolBar = Collections.max(toolBarList.stream().map(node -> node.prefHeight(Region.USE_COMPUTED_SIZE)).collect(Collectors.toList()));
        for (ToolBar toolBar : toolBarList) {
            toolBar.setPrefHeight(heightToolBar);
        }
        // Set height of controls within a toolbar to be the same.
        Optional<Double> height = toolBarsItems.get(0).stream().map(node -> node.prefHeight(Region.USE_COMPUTED_SIZE)).max(Double::compare);
        if (height.isEmpty()) {
            return;
        }
        for (List<Node> toolBarItems: toolBarsItems) {
            for (Node node: toolBarItems) {
                if (node instanceof Control) {
                    ((Control) node).setPrefHeight(height.get());
                }
            }
        }
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

    public static void snapNode(Node node, File file) throws IOException {
        double scale = 4.0;
        final Bounds bounds = node.getLayoutBounds();
        final WritableImage image = new WritableImage(
                (int) Math.round(bounds.getWidth() * scale),
                (int) Math.round(bounds.getHeight() * scale));
        final SnapshotParameters spa = new SnapshotParameters();
        spa.setTransform(javafx.scene.transform.Transform.scale(scale, scale));
        node.snapshot(spa, image);
        javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
    }

}
