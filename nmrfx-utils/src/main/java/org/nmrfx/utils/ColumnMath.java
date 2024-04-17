package org.nmrfx.utils;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.function.DoubleUnaryOperator;

public class ColumnMath {
    Dialog<DoubleUnaryOperator> dialog = null;
    TextField[] varFields = null;
    CheckBox invertCheckBox = null;
    double[] values;

    public double[] getValues() {
        values = new double[varFields.length];
        int i = 0;
        for (var varField : varFields) {
            try {
                values[i] = Double.parseDouble(varField.getText());
            } catch (NumberFormatException nfe) {
                values[i] = 0.0;
            }
            i++;
        }
        return values;
    }

    void checkValue() {
        boolean ok = true;
        for (var varField : varFields) {
            String s = varField.getText();
            try {
                Double.parseDouble(s);
                varField.setBackground(GUIUtils.getDefaultBackground());
            } catch (NumberFormatException nfe) {
                varField.setBackground(GUIUtils.getErrorBackground());
                ok = false;
            }
        }
        var applyButton = dialog.getDialogPane().lookupButton(ButtonType.APPLY);
        applyButton.setDisable(!ok);
    }

    public Dialog<DoubleUnaryOperator> getDialog() {
        if (dialog == null) {
            dialog = new Dialog<>();
            dialog.setTitle("Column Equation");
            dialog.setHeaderText("v = (v + A) * B + C");
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new
                    Insets(20, 150, 10, 10));

            String[] varNames = {"A", "B", "C"};
            varFields = new TextField[varNames.length];
            int iCol = 0;
            for (var varName : varNames) {
                Label label = new Label(varName);
                label.setPrefWidth(70.0);
                TextField varField = new TextField();
                varField.setOnKeyReleased(e -> checkValue());
                varField.setPrefWidth(70.0);
                varFields[iCol] = varField;
                if (varName.equals("B")) {
                    varField.setText("1.0");
                } else {
                    varField.setText("0.0");
                }
                grid.add(label, iCol, 0);
                grid.add(varField, iCol, 1);
                iCol++;
            }
            invertCheckBox = new CheckBox("Invert V");
            grid.add(invertCheckBox, iCol, 1);
            dialog.getDialogPane().setContent(grid);
        }
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.APPLY) {
                return getFunction();
            }
            return null;
        });
        return dialog;
    }

    DoubleUnaryOperator getFunction() {
        getValues();
        boolean invert = invertCheckBox.isSelected();
        if (invert) {
            return v -> {
                double vNew = 0.0;
                if (Math.abs(v) > 1.0e-9) {
                    vNew = (1.0 / v + values[0]) * values[1] + values[2];
                }
                return vNew;
            };
        } else {
            return v -> (v + values[0]) * values[1] + values[2];
        }
    }

}
