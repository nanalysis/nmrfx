package org.nmrfx.analyst.gui.tools.fittools;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import org.controlsfx.control.CheckComboBox;
import org.nmrfx.analyst.gui.TablePlotGUI;
import org.nmrfx.utils.GUIUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiffusionFitGUI extends FitGUI {
    enum DiffusionMode {
        BASIC("Δ' = Δ - δ / 3") {
            double getDeltaPrime(double[] v) {
                return v[0] - v[1] / 3.0;
            }

        },


        BIPOLAR("Δ' = Δ - δ / 3 - τ / 2") {
            double getDeltaPrime(double[] v) {
                return v[0] - v[1] / 3.0 - v[2] / 2.0;
            }

        },

        ONESHOT("Δ' = Δ + δ (α^2 - 2) / 6 + τ (α^2 - 1) / 2") {
            double getDeltaPrime(double[] v) {
                return v[0] + v[1] * (v[3] * v[3] - 2.0) / 6.0 + v[2] * (v[3] * v[3] - 1.0) / 2.0;
            }

        };

        final String equation;

        DiffusionMode(String equation) {
            this.equation = equation;
        }

        abstract double getDeltaPrime(double[] v);
    }

    public double[] getVariables() {
        double Delta = variableMap.get("DELTA").doubleValue();
        double delta = variableMap.get("delta").doubleValue();
        double tau = variableMap.get("tau").doubleValue();
        double alpha = variableMap.get("alpha").doubleValue();
        return new double[]{Delta, delta, tau, alpha};

    }

    Map<String, SimpleDoubleProperty> variableMap = new HashMap<>();
    ChoiceBox<DiffusionMode> diffusionModeChoiceBox;
    public  void setXYChoices(TableView tableView, ChoiceBox<String> xArrayChoice, CheckComboBox<String> yArrayChoice) {
        List<TableColumn> columns = tableView.getColumns();
        for (var column : columns) {
            String columnName = column.getText();
            if (columnName.equalsIgnoreCase("value")) {
                xArrayChoice.setValue(columnName);
            } else if (columnName.contains(":")) {
                String name = columnName.substring(0, columnName.indexOf(":"));

                yArrayChoice.getCheckModel().check(name);
            }
        }
    }

    @Override
    public void setupGridPane(VBox extraBox) {
        GridPane gridPane = new GridPane();
        extraBox.getChildren().add(gridPane);


        String[] labels = {"Δ ms", "δ ms", "τ ms", "α", "Δ' ms"};
        String[] sLabels = {"DELTA", "delta", "tau", "alpha", "DELTA'"};
        for (int i = 0; i < labels.length; i++) {
            Label label = new Label(labels[i]);
            label.setPrefWidth(70);
            label.setTextAlignment(TextAlignment.CENTER);
            gridPane.add(label, i, 0);
            SimpleDoubleProperty simpleDoubleProperty = new SimpleDoubleProperty(0.0);
            int nDecimals = 3;
            TextField textField = GUIUtils.getDoubleTextField(simpleDoubleProperty, nDecimals);
            textField.setPrefWidth(50);
            variableMap.put(sLabels[i], simpleDoubleProperty);
            gridPane.add(textField, i, 1);
            if (!sLabels[i].equalsIgnoreCase("Delta'")) {
                simpleDoubleProperty.addListener(e -> updateDeltaPrime());
            }
        }
        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setSpacing(10);
        diffusionModeChoiceBox = new ChoiceBox<>();
        diffusionModeChoiceBox.getItems().addAll(DiffusionMode.values());
        diffusionModeChoiceBox.setPrefWidth(100);
        Label label = new Label();
        label.setTextAlignment(TextAlignment.CENTER);
        label.setAlignment(Pos.CENTER);
        hBox.getChildren().addAll(diffusionModeChoiceBox, label);
        diffusionModeChoiceBox.setValue(DiffusionMode.BASIC);
        label.setText(DiffusionMode.BASIC.equation);
        diffusionModeChoiceBox.setOnAction(e -> {
            label.setText(diffusionModeChoiceBox.getValue().equation);
            updateDeltaPrime();
        });
        extraBox.getChildren().add(hBox);
    }

    void updateDeltaPrime() {
        double[] v = getVariables();
        double deltaPrime = diffusionModeChoiceBox.getValue().getDeltaPrime(v);
        variableMap.get("DELTA'").set(deltaPrime);

    }
    @Override
    public List<TablePlotGUI.ParItem> addDerivedPars(List<TablePlotGUI.ParItem> parItems) {
        double[] v = getVariables();
        double deltaPrime = variableMap.get("DELTA'").doubleValue() * 1.0e-3;

        double gamma = 2.6752218744e8;
        List<TablePlotGUI.ParItem> newItems = new ArrayList<>();
        for (TablePlotGUI.ParItem parItem : parItems) {
            if (parItem.parName().equalsIgnoreCase("B")) {
                double b = parItem.value();
                double bErr = parItem.error();
                double delta = v[1] * 1.0e-3;
                double scale = (gamma * gamma * delta * delta * deltaPrime) / 1.0e4;
                double D = Math.round(b / scale * 1.0e13) / 1000.0;
                double Derr = Math.round(bErr / scale * 1.0e13) / 1000.0;
                TablePlotGUI.ParItem newItem = new TablePlotGUI.ParItem(parItem.columnName(), 0, "D", D, Derr);
                newItems.add(newItem);
            }
        }
        return newItems;

    }

}
