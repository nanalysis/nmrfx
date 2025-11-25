package org.nmrfx.analyst.gui.peaks;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.processor.datasets.peaks.PeakPPMGetter;
import org.nmrfx.project.ProjectBase;

import java.util.*;

public class PeakPPMGetterGUI {
    final static String REFERENCE = "Reference";
    final static String ASSIGNED = "Assigned";
    Stage stage = null;
    GridPane gridPane;
    MenuButton peakListMenuButton;
    ChoiceBox<String> assignOrRefChoice;
    ChoiceBox<Integer> refSetChoice;
    PeakPPMGetter peakPPMGetter = new PeakPPMGetter();

    Map<PeakList, PeakPPMGetter.PeakListPPMState> peakListStateMap;

    public void showPeakPPMGetter() {
        if (stage == null) {
            peakListStateMap = new HashMap<>();
            stage = new Stage(StageStyle.DECORATED);
            stage.setMinHeight(200);
            BorderPane borderPane = new BorderPane();
            Scene scene = new Scene(borderPane);
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");
            stage.setTitle("Peak Generator");
            ToolBar toolBar = new ToolBar();
            peakListMenuButton = new MenuButton("Lists");
            toolBar.getItems().add(peakListMenuButton);
            toolBar.setPrefWidth(350);
            assignOrRefChoice = new ChoiceBox<>();
            assignOrRefChoice.getItems().addAll(ASSIGNED, REFERENCE);
            refSetChoice = new ChoiceBox<>();
            for (int iSet = 0; iSet < 5; iSet++) {
                refSetChoice.getItems().add(iSet);
            }
            assignOrRefChoice.setValue(REFERENCE);
            refSetChoice.setValue(0);
            toolBar.getItems().addAll(new Label("Set:"), assignOrRefChoice,
                    new Label("Index:"), refSetChoice);

            Button getButton = new Button("Get PPMs");
            getButton.setOnAction(e -> getPPMs());
            toolBar.getItems().add(getButton);

            borderPane.setTop(toolBar);
            gridPane = new GridPane();
            Insets insets = new Insets(20, 20, 20, 20);
            gridPane.setPadding(insets);
            ColumnConstraints columnConstraints = new ColumnConstraints(100);
            gridPane.getColumnConstraints().addAll(columnConstraints, columnConstraints, columnConstraints);

            borderPane.setCenter(gridPane);
            updatePeakListMenu();
            peakListMenuButton.setOnShowing(e -> updatePeakListMenu());
        }
        stage.show();
        stage.toFront();
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();
        for (PeakList peakList1 : ProjectBase.getActive().getPeakLists()) {
            String peakListName = peakList1.getName();
            CheckMenuItem menuItem = new CheckMenuItem(peakListName);
            peakListMenuButton.getItems().add(menuItem);
            menuItem.setSelected(isActive(peakList1));
            menuItem.setOnAction(e -> showPeakListOptions(peakList1));
        }
    }

    private boolean isActive(PeakList peakList) {
        return ((peakListStateMap != null) && peakListStateMap.containsKey(peakList) && !peakListStateMap.get(peakList).active().isEmpty());
    }

    private void showPeakListOptions(PeakList peakList) {
        gridPane.getChildren().clear();
        PeakPPMGetter.PeakListPPMState peakListPPMState = peakListStateMap.computeIfAbsent(peakList,
                peakList1 -> new PeakPPMGetter.PeakListPPMState(new HashSet<>(), new HashSet<>()));

        Label listLabel = new Label(peakList.getName());
        gridPane.add(listLabel, 0, 0, 3, 1);
        int row = 1;
        gridPane.add(new Label("Dim Name"), 0, row);
        gridPane.add(new Label("Active"), 1, row);
        gridPane.add(new Label("UnFold"), 2, row);
        row++;
        for (SpectralDim spectralDim : peakList.getSpectralDims()) {
            gridPane.add(new Label(spectralDim.getDimName()), 0, row);
            CheckBox checkBox = new CheckBox();
            gridPane.add(checkBox, 1, row);
            checkBox.setSelected(peakListPPMState.active().contains(spectralDim));
            CheckBox foldBox = new CheckBox();
            gridPane.add(foldBox, 2, row);
            foldBox.setSelected(peakListPPMState.folded().contains(spectralDim));
            checkBox.selectedProperty().subscribe(c -> updateMap(peakList, spectralDim, c, true));
            foldBox.selectedProperty().subscribe(c -> updateMap(peakList, spectralDim, c, false));
            row++;
        }
    }

    private void updateMap(PeakList peakList, SpectralDim spectralDim, boolean selected, boolean activeMode) {
        PeakPPMGetter.PeakListPPMState peakListPPMState = peakListStateMap.computeIfAbsent(peakList,
                peakList1 -> new PeakPPMGetter.PeakListPPMState(new HashSet<>(), new HashSet<>()));
        Set<SpectralDim> set = activeMode ? peakListPPMState.active() : peakListPPMState.folded();
        if (selected) {
            set.add(spectralDim);
        } else {
            set.remove(spectralDim);
        }
    }

    private void getPPMs() {
        boolean refMode = assignOrRefChoice.getValue().equals(REFERENCE);
        int index = refSetChoice.getValue();
        peakPPMGetter.getPPMs(peakListStateMap, refMode, index);
    }
}
