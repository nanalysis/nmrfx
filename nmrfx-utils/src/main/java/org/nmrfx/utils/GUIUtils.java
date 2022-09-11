/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.utils;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 *
 * @author brucejohnson
 */
public class GUIUtils {

    private GUIUtils() {
    }

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

    public static List<String> splitToWidth(double regionWidth, String segment, Font font) {
        double width = GUIUtils.getTextWidth(segment, font);

        double charWidth = width / segment.length();
        int start = 0;
        int end;
        List<String> result = new ArrayList<>();
        do {
            end = start + (int) (regionWidth / charWidth);
            if (end > segment.length()) {
                end = segment.length();
            }
            double testWidth = GUIUtils.getTextWidth(segment.substring(start, end), font);
            while (testWidth > regionWidth) {
                if (end < 2) {
                    break;
                }
                end--;
                testWidth = GUIUtils.getTextWidth(segment.substring(start, end), font);
            }
            while (testWidth < regionWidth - charWidth) {
                end++;
                if (end > segment.length()) {
                    end = segment.length();
                    break;
                }
                testWidth = GUIUtils.getTextWidth(segment.substring(start, end), font);
            }
            String subStr = segment.substring(start, end);
            result.add(subStr);
            start = end;
        } while (start < segment.length());
        return result;
    }

    /**
     * Utility function to adjust the height of toolbars used in ControllerTools. The height of each toolbar is adjusted
     * to the largest prefHeight. The height of all the toolbar items are adjusted to the prefHeight of the largest
     * item in the first toolbar in the list.
     * @param toolBarList A list of toolbars to adjust
     */
    public static void toolbarAdjustHeights(List<ToolBar> toolBarList) {
        if (toolBarList.isEmpty()) {
            return;
        }
        // Set height of all toolbars to be the same
        double heightToolBar = Collections.max(toolBarList.stream().map(node -> node.prefHeight(Region.USE_COMPUTED_SIZE)).toList());
        for (ToolBar toolBar : toolBarList) {
            toolBar.setPrefHeight(heightToolBar);
        }

        List<Node> toolBarsItems = new ArrayList<>();
        for (ToolBar toolBar: toolBarList) {
            toolBarsItems.addAll(toolBar.getItems());
        }
        nodeAdjustHeights(toolBarsItems);
    }

    public static void nodeAdjustHeights(List<Node> nodeList) {
        // Set height of controls within a toolbar to be the same.
        Optional<Double> height = nodeList.stream().map(node -> node.prefHeight(Region.USE_COMPUTED_SIZE)).max(Double::compare);
        if (height.isEmpty()) {
            return;
        }
        for (Node node : nodeList) {
            if (node instanceof Control control) {
                control.setPrefHeight(height.get());
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
