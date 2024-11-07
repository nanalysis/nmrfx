package org.nmrfx.processor.gui;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakPickParameters;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.utils.GUIUtils;

import static org.nmrfx.processor.datasets.peaks.PeakPickParameters.PickMode.*;

public class PeakPickController {
    FXMLController fxmlController;
    PolyChart chart;

    CheckBox autoNameCheckBox;
    TextField nameField;

    ChoiceBox<PeakPickParameters.PickMode> modeChoiceBox;
    ChoiceBox<String> regionChoiceBox;
    ChoiceBox<String> thicknessChoiceBox;

    CheckBox noiseCheckBox;
    Slider noiseRatioSlider;

    CheckBox filterCheckBox = null;
    ChoiceBox<PeakList> filterListChoiceBox;
    Slider filterWidthSlider;

    public void setup(FXMLController fxmlController, TitledPane annoPane) {
        this.fxmlController = fxmlController;
        this.chart = fxmlController.getActiveChart();
        VBox vBox = new VBox();
        vBox.setSpacing(10);
        annoPane.setContent(vBox);
        ToolBar toolBar = new ToolBar();
        vBox.getChildren().add(toolBar);
        Button peakPickButton = new Button("Pick");
        Label listName = new Label("List Name");
        double labelWidth = 100.0;
        listName.setPrefWidth(labelWidth);
        autoNameCheckBox = new CheckBox("AutoName");
        nameField = new TextField();
        autoNameCheckBox.setSelected(true);
        modeChoiceBox = new ChoiceBox<>();
        modeChoiceBox.getItems().addAll(NEW, REPLACE, APPEND);
        modeChoiceBox.setValue(NEW);

        regionChoiceBox = new ChoiceBox<>();
        regionChoiceBox.getItems().addAll("Window", "Box");
        regionChoiceBox.setValue("Box");

        thicknessChoiceBox = new ChoiceBox<>();
        thicknessChoiceBox.getItems().addAll("All", "0", "1", "2", "3", "4", "5");
        thicknessChoiceBox.setValue("0");

        peakPickButton.setOnAction(e -> peakPick());
        toolBar.getItems().addAll(peakPickButton);
        nameField.editableProperty().bind(autoNameCheckBox.selectedProperty().not());

        HBox nameBox = new HBox();
        nameBox.getChildren().addAll(listName, autoNameCheckBox, nameField);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        nameBox.setSpacing(10);

        HBox regionBox = new HBox();
        Label regionLabel = new Label("Region");
        regionLabel.setPrefWidth(labelWidth);
        regionBox.getChildren().addAll(regionLabel, regionChoiceBox);
        regionBox.setAlignment(Pos.CENTER_LEFT);
        regionBox.setSpacing(10);

        HBox modeBox = new HBox();
        Label modeLabel = new Label("Mode");
        modeLabel.setPrefWidth(labelWidth);
        modeBox.getChildren().addAll(modeLabel, modeChoiceBox);
        modeBox.setAlignment(Pos.CENTER_LEFT);
        modeBox.setSpacing(10);

        HBox thicknessBox = new HBox();
        Label thicknessLabel = new Label("Thickness");
        thicknessLabel.setPrefWidth(labelWidth);
        thicknessBox.getChildren().addAll(thicknessLabel, thicknessChoiceBox);
        thicknessBox.setAlignment(Pos.CENTER_LEFT);
        thicknessBox.setSpacing(10);

        HBox noiseBox = new HBox();
        Label noiseRatioLabel = new Label("Local Noise");
        noiseCheckBox = new CheckBox();
        noiseRatioSlider = new Slider(0, 40, 10.0);
        TextField noiseField = new TextField();
        noiseField.setPrefWidth(40);
        GUIUtils.bindSliderField(noiseRatioSlider, noiseField,"0.#");
        noiseRatioSlider.setShowTickLabels(true);
        noiseRatioLabel.setPrefWidth(labelWidth);
        noiseBox.getChildren().addAll(noiseRatioLabel, noiseCheckBox, noiseRatioSlider, noiseField);
        noiseBox.setAlignment(Pos.CENTER_LEFT);
        noiseBox.setSpacing(10);

        GridPane filterBox = new GridPane();
        Label filterLabel = new Label("Filter List");
        filterLabel.setPrefWidth(labelWidth);
        filterCheckBox = new CheckBox();
        filterListChoiceBox = new ChoiceBox<>();
        filterListChoiceBox.setPrefWidth(100);
        filterListChoiceBox.getItems().addAll(GUIProject.getActive().getPeakLists());
        filterWidthSlider = new Slider(0.5, 4.0, 1.0);
        TextField filterWidthText = new TextField();
        filterWidthText.setPrefWidth(40);
        GUIUtils.bindSliderField(filterWidthSlider, filterWidthText,"0.#");
        Label filterWidthLabel = new Label("Filter Width");
        filterWidthLabel.setPrefWidth(labelWidth);

        filterWidthSlider.setValue(1.0);

        filterBox.add(filterLabel, 0, 0);
        filterBox.add(filterCheckBox, 1, 0);
        filterBox.add(filterListChoiceBox, 2, 0);
        filterBox.add(filterWidthLabel, 0, 1);
        filterBox.add(filterWidthSlider, 1, 1, 2, 1);
        filterBox.add(filterWidthText, 3,1);

        filterBox.setAlignment(Pos.CENTER_LEFT);
        filterBox.setHgap(10);
        filterBox.setVgap(10);
        filterListChoiceBox.setOnShowing(e -> updateFilterChoices());

        vBox.getChildren().addAll(nameBox, regionBox, modeBox, thicknessBox, noiseBox, filterBox);
    }

    void updateFilterChoices() {
        filterListChoiceBox.getItems().clear();
        filterListChoiceBox.getItems().addAll(GUIProject.getActive().getPeakLists());
    }
    void peakPick() {
        String name = autoNameCheckBox.isSelected() ? null : nameField.getText();
        PeakPickParameters.PickMode mode = modeChoiceBox.getValue();
        String region = regionChoiceBox.getValue().toLowerCase();
        String thicknessStr = thicknessChoiceBox.getValue();
        PeakPickParameters peakPickParameters = new PeakPickParameters();
        peakPickParameters.listName = name;
        peakPickParameters.region = region;
        peakPickParameters.mode = mode;
        peakPickParameters.useNoise = noiseCheckBox.isSelected();
        peakPickParameters.noiseLimit(noiseRatioSlider.getValue());
        if (thicknessStr.equals("All")) {
            peakPickParameters.thickness = 0;
            peakPickParameters.useAll = true;
        } else {
            peakPickParameters.thickness = Integer.parseInt(thicknessStr);
            peakPickParameters.useAll = false;
        }
        peakPickParameters.filterList = filterListChoiceBox.getValue();
        peakPickParameters.filter = filterCheckBox.isSelected();
        peakPickParameters.filterWidth = filterWidthSlider.getValue();

        PeakPicking.peakPickActive(fxmlController, peakPickParameters);

    }
}
