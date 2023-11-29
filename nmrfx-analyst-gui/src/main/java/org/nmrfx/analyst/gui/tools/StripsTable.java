package org.nmrfx.analyst.gui.tools;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.tableview2.FilteredTableColumn;
import org.controlsfx.control.tableview2.FilteredTableView;
import org.controlsfx.control.tableview2.TableColumn2;
import org.controlsfx.control.textfield.TextFields;
import org.nmrfx.analyst.gui.spectra.StripController;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakLabeller;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.structure.seqassign.SeqFragment;
import org.nmrfx.structure.seqassign.SpinSystem;
import org.nmrfx.utils.GUIUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class StripsTable {
    VBox vBox;
    FilteredTableView<Peak> tableView = new FilteredTableView<>();
    TableView<StripController.PeakMatchResult> sortTable = new TableView<>();
    ToolBar toolBar = new ToolBar();
    FXMLController fxmlController;
    StripController stripController;
    ObservableList<Peak> tablePeaks = FXCollections.observableArrayList();
    ObservableList<StripController.PeakMatchResult> sortTablePeaks = FXCollections.observableArrayList();
    SortedList<Peak> sortedPeaks;
    FilteredTableColumn<Peak, Number> fragmentColumn;
    FilteredTableColumn<Peak, String> atomNameColumn;
    TextField peakFields = null;
    ChoiceBox<String> fragmentChoice = new ChoiceBox<>();
    ChoiceBox<String> assignedChoice = new ChoiceBox<>();
    Peak targetPeak = null;
    ChoiceBox<String> positionChoiceBox;

    public StripsTable(FXMLController fxmlController, StripController stripController, VBox vBox) {
        this.vBox = vBox;
        vBox.setPrefWidth(320);
        this.fxmlController = fxmlController;
        this.stripController = stripController;
        FilteredList<Peak> filteredPeaks = new FilteredList<>(tablePeaks);
        filteredPeaks.predicateProperty().bind(tableView.predicateProperty());
        sortedPeaks = new SortedList<>(filteredPeaks);
        sortedPeaks.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedPeaks);

        SortedList<StripController.PeakMatchResult> sortTableSortedPeaks = new SortedList<>(sortTablePeaks);
        sortTableSortedPeaks.comparatorProperty().bind(sortTable.comparatorProperty());
        sortTable.setItems(sortTableSortedPeaks);
        initTools();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        HBox sortBox = initSortTools();
        vBox.getChildren().addAll(tableView, sortBox, sortTable);
    }

    void initTools() {

        fragmentChoice = new ChoiceBox<>();
        toolBar.getItems().addAll(new Label("State:"), assignedChoice, new Label("Fragment:"), fragmentChoice);
        fragmentChoice.valueProperty().addListener(e -> setFragmentPredicate());
        assignedChoice.getItems().addAll("All", "Assigned", "Unassigned");
        assignedChoice.valueProperty().addListener(e -> setAssignedPredicate());
        HBox hBox = new HBox();
        Label peakLabel = new Label("Peaks:");
        peakFields = TextFields.createClearableTextField();
        HBox.setHgrow(peakFields, Priority.ALWAYS);
        hBox.getChildren().addAll(peakLabel, peakFields);
        peakFields.textProperty().addListener(e -> peakFieldChanged());
        vBox.getChildren().addAll(toolBar, hBox);
    }

    HBox initSortTools() {
        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER);
        hBox.setSpacing(10.0);
        Label insertLabel = new Label("Replace peak ");
        positionChoiceBox = new ChoiceBox<>();
        positionChoiceBox.getItems().addAll("", "before", "after");
        positionChoiceBox.setValue("");
        insertLabel.disableProperty().bind(positionChoiceBox.valueProperty().asString().isEqualTo(""));
        Label peakLabel = new Label();
        peakLabel.setMinWidth(70);
        tableView.getSelectionModel().selectedItemProperty().addListener((observableValue, oldPeak, newPeak) -> {
            String label = newPeak != null ? newPeak.getName() : "";
            targetPeak = newPeak;
            peakLabel.setText(label);
            if (targetPeak != null) {
                int index = sortedPeaks.indexOf(targetPeak);
                stripController.setCenter(index);
            }
        });

        hBox.getChildren().addAll(insertLabel, positionChoiceBox, peakLabel);
        sortTable.getSelectionModel().selectedIndexProperty().addListener(e -> sortedSelectionChanged());
        Button findMatchButton = new Button("Search");
        hBox.getChildren().add(findMatchButton);
        findMatchButton.setOnAction(e -> scoreMatches(targetPeak));
        return hBox;
    }

    private void scoreMatches(Peak peak) {
        var matches = stripController.matchPeaks(peak);
        sortTablePeaks.clear();
        sortTablePeaks.addAll(matches);
    }

    private void sortedSelectionChanged() {
        if (tableView.getSortOrder().isEmpty()) {
            StripController.PeakMatchResult selectedMatchResult = sortTable.getSelectionModel().getSelectedItem();
            if (selectedMatchResult != null) {
                Peak selectedPeak = selectedMatchResult.peak();
                if ((targetPeak != selectedPeak) && (targetPeak != null) && (selectedPeak != null)) {
                    int targetIndex = tablePeaks.indexOf(targetPeak);
                    String positionChoice = positionChoiceBox.getValue();
                    if (positionChoice.equals("before")) {
                        if (targetIndex == 0) {
                            tablePeaks.add(0, selectedPeak);
                        } else {
                            tablePeaks.set(targetIndex - 1, selectedPeak);
                        }
                    } else if (positionChoice.equals("after")) {
                        if (targetIndex == (tablePeaks.size() - 1)) {
                            tablePeaks.add(selectedPeak);
                        } else {
                            tablePeaks.set(targetIndex + 1, selectedPeak);
                        }

                    }
                }
            }
        }
    }

    public Integer getResidue(PeakDim peakDim) {
        Integer result = Integer.MIN_VALUE;
        var resonance = peakDim.getResonance();
        Atom atom = resonance.getAtom();
        if (atom != null) {
            Entity entity = atom.getEntity();
            if (entity instanceof Residue residue) {
                result = residue.getResNum();
            }
        } else {
            String label = peakDim.getLabel();
            Optional<PeakLabeller.ChainResAtomSpecifier> optionalChainResAtomSpecifier = PeakLabeller.parse(label);
            if (optionalChainResAtomSpecifier.isPresent()) {
                result = optionalChainResAtomSpecifier.get().resNum();
            }
        }
        return result;
    }

    public String getAtomName(PeakDim peakDim) {
        String result = "";
        var resonance = peakDim.getResonance();
        Atom atom = resonance.getAtom();
        if (atom != null) {
            result = atom.getName();
        } else {
            String label = peakDim.getLabel();
            Optional<PeakLabeller.ChainResAtomSpecifier> optionalChainResAtomSpecifier = PeakLabeller.parse(label);
            if (optionalChainResAtomSpecifier.isPresent()) {
                result = optionalChainResAtomSpecifier.get().atomName();
            }
        }
        return result;
    }

    public Integer getFragment(Peak peak) {
        Optional<SpinSystem> spinSys = SpinSystem.spinSystemFromPeak(peak);
        if (spinSys.isPresent()) {
            Optional<SeqFragment> fragment = spinSys.get().getFragment();
            if (fragment.isPresent()) {
                return fragment.get().getId();
            }
        }
        return -1;
    }

    void initTable(int nDim) {
        initSortTable(sortTable, nDim);
        initTable(tableView, nDim);
    }

    void initTable(TableView<Peak> table, int nDim) {
        TableColumn2<Peak, Number> idColumn = new TableColumn2<>("ID");
        idColumn.setPrefWidth(50);
        double columnWidths = (vBox.getPrefWidth() - 50 - 10) / 3;
        idColumn.setCellValueFactory(e -> new SimpleIntegerProperty(e.getValue().getIdNum()));
        table.getColumns().clear();
        table.getColumns().add(idColumn);
        for (int i = 0; i < nDim; i++) {
            final int iDim = i;
            FilteredTableColumn<Peak, Number> residueColumn = new FilteredTableColumn<>("Residue");
            residueColumn.setPrefWidth(columnWidths);

            residueColumn.setCellValueFactory(e -> new SimpleIntegerProperty(getResidue(e.getValue().getPeakDim(iDim))));
            residueColumn.setCellFactory(tableColumn -> new TableCell<>() {
                @Override
                protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);
                    if (!empty && (item.intValue() != Integer.MIN_VALUE)) {
                        this.setText(String.valueOf(item));
                    }
                }
            });

            table.getColumns().add(residueColumn);

            FilteredTableColumn<Peak, String> label1Column = new FilteredTableColumn<>("Atom");
            label1Column.setPrefWidth(columnWidths);
            label1Column.setCellValueFactory(e -> new SimpleStringProperty(getAtomName(e.getValue().getPeakDim(iDim))));

            table.getColumns().add(label1Column);
            if (table == tableView) {
                sortedPeaks.addListener((ListChangeListener<? super Peak>) e -> stripController.updatePeaks());
                if (i == 0) {
                    atomNameColumn = label1Column;
                }
            }
        }

        fragmentColumn = new FilteredTableColumn<>("Fragment");
        fragmentColumn.setPrefWidth(columnWidths);
        fragmentColumn.setCellValueFactory(e -> new SimpleIntegerProperty(getFragment(e.getValue())));
        fragmentColumn.setCellFactory(tableColumn -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                this.setText(null);
                this.setGraphic(null);
                if (!empty && (item.intValue() != -1)) {
                    this.setText(String.valueOf(item));
                }
            }
        });

        table.getColumns().add(fragmentColumn);
    }

    void initSortTable(TableView<StripController.PeakMatchResult> table, int nDim) {
        TableColumn<StripController.PeakMatchResult, Number> idColumn = new TableColumn<>("ID");
        idColumn.setPrefWidth(50);
        idColumn.setCellValueFactory(e -> new SimpleIntegerProperty(e.getValue().peak().getIdNum()));
        table.getColumns().clear();
        table.getColumns().add(idColumn);
        double columnWidths = (vBox.getPrefWidth() - 50 - 10) / 4;
        for (int i = 0; i < nDim; i++) {
            final int iDim = i;
            TableColumn<StripController.PeakMatchResult, Number> residueColumn = new TableColumn<>("Residue");
            residueColumn.setPrefWidth(columnWidths);
            residueColumn.setCellValueFactory(e -> new SimpleIntegerProperty(getResidue(e.getValue().peak().getPeakDim(iDim))));
            residueColumn.setCellFactory(tableColumn -> new TableCell<>() {
                @Override
                protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);
                    if (!empty && (item.intValue() != Integer.MIN_VALUE)) {
                        this.setText(String.valueOf(item));
                    }
                }
            });

            table.getColumns().add(residueColumn);

            TableColumn<StripController.PeakMatchResult, String> label1Column = new TableColumn<>("Atom");
            label1Column.setPrefWidth(columnWidths);
            label1Column.setCellValueFactory(e -> new SimpleStringProperty(getAtomName(e.getValue().peak().getPeakDim(iDim))));

            table.getColumns().add(label1Column);
        }
        TableColumn<StripController.PeakMatchResult, Number> scoreColumn = new TableColumn<>("Score");
        scoreColumn.setCellValueFactory(e -> new SimpleDoubleProperty(Math.round(e.getValue().score() * 100.0) / 100.0));

        TableColumn<StripController.PeakMatchResult, Number> column = new TableColumn<>("Fragment");
        column.setPrefWidth(columnWidths);
        column.setCellValueFactory(e -> new SimpleIntegerProperty(getFragment(e.getValue().peak())));
        column.setCellFactory(tableColumn -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                this.setText(null);
                this.setGraphic(null);
                if (!empty && (item.intValue() != -1)) {
                    this.setText(String.valueOf(item));
                }
            }
        });

        table.getColumns().add(column);
        table.getColumns().add(scoreColumn);

    }

    private void setFragmentPredicate() {
        String fragmentStr = fragmentChoice.getValue();
        final int fragmentID;
        if ((fragmentStr == null) || fragmentStr.equals("All")) {
            fragmentID = -2;
        } else if (fragmentStr.equals("No")) {
            fragmentID = -1;
        } else {
            fragmentID = Integer.parseInt(fragmentStr);
        }
        fragmentColumn.setPredicate(e -> (fragmentID == -2) || e.intValue() == fragmentID);
    }

    private void setAssignedPredicate() {
        String assignedMode = assignedChoice.getValue();
        atomNameColumn.setPredicate(e -> assignedMode.equals("All") || (assignedMode.equals("Assigned")
                && !e.isBlank()) || (assignedMode.equals("Unassigned") && e.isBlank()));
    }

    public void updatePeakSorterPeaks(ObservableList<StripController.PeakMatchResult> peaks) {
        sortTablePeaks.clear();
        sortTablePeaks.addAll(peaks);
    }

    public void updatePeaks(ObservableList<Peak> peaks) {
        int currentNColumns = tableView.getColumns().size() - 2;
        if (!peaks.isEmpty()) {
            int nDim = 1;
            if ((nDim * 2) != currentNColumns) {
                initTable(nDim);
            }
        }
        var fragmentIds = peaks.stream().map(this::getFragment).sorted().distinct().filter(id -> id >= 0).map(String::valueOf).toList();
        fragmentChoice.getItems().clear();
        fragmentChoice.getItems().addAll("All", "No");
        fragmentChoice.getItems().addAll(fragmentIds);
        tablePeaks.clear();
        tablePeaks.addAll(peaks);
    }

    void peakFieldChanged() {
        String peakString = peakFields.getText().trim();
        String[] fields = peakString.split("\\s+");
        List<Integer> peakIDs = new ArrayList<>();
        ObservableList<Peak> peaks = FXCollections.observableArrayList();

        PeakList peakList = stripController.getControlList();
        if (peakString.isBlank() || (fields.length == 0)) {
            peaks.addAll(peakList.peaks());
        } else {
            for (var field : fields) {
                if (!field.isBlank()) {
                    if (field.contains("-")) {
                        String[] rangeFields = field.split("-");
                        try {
                            int start = Integer.parseInt(rangeFields[0]);
                            int end = rangeFields.length == 2 ? Integer.parseInt(rangeFields[1]) : start;
                            for (int iPeak = start; iPeak <= end; iPeak++) {
                                peakIDs.add(iPeak);
                            }
                        } catch (NumberFormatException ignored) {
                            peakFields.setBackground(GUIUtils.getErrorBackground());
                            return;
                        }
                    } else {
                        try {
                            int iPeak = Integer.parseInt(field);
                            peakIDs.add(iPeak);
                        } catch (NumberFormatException ignored) {
                            peakFields.setBackground(GUIUtils.getErrorBackground());
                            return;
                        }
                    }
                }
            }
            peaks.addAll(peakIDs.stream().sorted().distinct().map(peakList::getPeakByID).filter(Objects::nonNull).toList());
        }
        peakFields.setBackground(GUIUtils.getDefaultBackground());
        updatePeaks(peaks);
        peakFields.requestFocus();
    }

    public ObservableList<Peak> getSortedPeaks() {
        return sortedPeaks;
    }
}
