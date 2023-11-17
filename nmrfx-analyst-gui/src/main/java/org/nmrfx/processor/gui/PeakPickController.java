package org.nmrfx.processor.gui;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nmrfx.processor.datasets.peaks.PeakPickParameters;

public class PeakPickController {
    FXMLController fxmlController;
    PolyChart chart;

    CheckBox autoNameCheckBox;
    TextField nameField;

    ChoiceBox<String> modeChoiceBox;
    ChoiceBox<String> regionChoiceBox;

    ChoiceBox<String> thicknessChoiceBox;

    public void setup(FXMLController fxmlController, TitledPane annoPane) {
        this.fxmlController = fxmlController;
        this.chart = fxmlController.getActiveChart();
        VBox vBox = new VBox();
        annoPane.setContent(vBox);
        ToolBar toolBar = new ToolBar();
        vBox.getChildren().add(toolBar);
        Button peakPickButton = new Button("Pick");
        Label listName = new Label("List Name");
        double prefWidth = 100.0;
        listName.setPrefWidth(prefWidth);
        autoNameCheckBox = new CheckBox("AutoName");
        nameField = new TextField();
        autoNameCheckBox.setSelected(true);
        modeChoiceBox = new ChoiceBox<>();
        modeChoiceBox.getItems().addAll("New", "Replace", "Append");
        modeChoiceBox.setValue("New");

        regionChoiceBox = new ChoiceBox<>();
        regionChoiceBox.getItems().addAll("Window", "Box");
        regionChoiceBox.setValue("Box");

        thicknessChoiceBox = new ChoiceBox<>();
        thicknessChoiceBox.getItems().addAll("All", "0", "1", "2", "3", "4", "5");
        thicknessChoiceBox.setValue("All");

        peakPickButton.setOnAction(e -> peakPick());
        toolBar.getItems().addAll(peakPickButton);
        nameField.editableProperty().bind(autoNameCheckBox.selectedProperty().not());

        HBox nameBox = new HBox();
        nameBox.getChildren().addAll(listName, autoNameCheckBox, nameField);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        nameBox.setSpacing(10);

        HBox regionBox = new HBox();
        Label regionLabel = new Label("Region");
        regionLabel.setPrefWidth(prefWidth);
        regionBox.getChildren().addAll(regionLabel, regionChoiceBox);
        regionBox.setAlignment(Pos.CENTER_LEFT);
        regionBox.setSpacing(10);

        HBox modeBox = new HBox();
        Label modeLabel = new Label("Mode");
        modeLabel.setPrefWidth(prefWidth);
        modeBox.getChildren().addAll(modeLabel, modeChoiceBox);
        modeBox.setAlignment(Pos.CENTER_LEFT);
        modeBox.setSpacing(10);

        HBox thicknessBox = new HBox();
        Label thicknessLabel = new Label("Thickness");
        thicknessLabel.setPrefWidth(prefWidth);
        thicknessBox.getChildren().addAll(thicknessLabel, thicknessChoiceBox);
        thicknessBox.setAlignment(Pos.CENTER_LEFT);
        thicknessBox.setSpacing(10);




        vBox.getChildren().addAll(nameBox, regionBox, modeBox, thicknessBox);
    }

    void peakPick() {
        String name = autoNameCheckBox.isSelected() ? null : nameField.getText();
        String mode = modeChoiceBox.getValue().toLowerCase();
        String region = regionChoiceBox.getValue().toLowerCase();
        String thicknessStr = thicknessChoiceBox.getValue();
        PeakPickParameters peakPickParameters = new PeakPickParameters();
        peakPickParameters.listName = name;
        peakPickParameters.region = region;
        peakPickParameters.mode = mode;
        if (thicknessStr.equals("All")) {
            peakPickParameters.thickness = 0;
            peakPickParameters.useAll = true;
        } else {
            peakPickParameters.thickness = Integer.parseInt(thicknessStr);
            peakPickParameters.useAll = false;
        }

        PeakPicking.peakPickActive(fxmlController, peakPickParameters);

    }
}
