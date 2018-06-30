/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.Nuclei;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.SpectralDim;
import org.nmrfx.processor.gui.controls.FractionPane;
import org.nmrfx.processor.utilities.Util;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Compound;
import org.nmrfx.structure.chemistry.Molecule;

/**
 *
 * @author Bruce Johnson
 */
public class AtomBrowser {

    ToolBar browserToolBar;
    FXMLController controller;
    Consumer closeAction;
    Button freezeButton;
    Button thawButton;
    Button tweakFreezeButton;
    Button linkButton;
    Label atomFieldLabel;
    TextField atomField;
    int centerDim = 0;
    int rangeDim = 1;
    ToggleGroup toggleGroup = new ToggleGroup();
    Set<String> datasetBlackList = new HashSet<>();
    Map<String, RangeItem> rangeItems = new HashMap<>();
    ChoiceBox<String> entityChoiceBox;
    ChoiceBox<String> rangeSelector;
    TextField minField;
    TextField maxField;
    SimpleBooleanProperty showActives = new SimpleBooleanProperty(true);
    SimpleBooleanProperty showBreakThroughs = new SimpleBooleanProperty(true);
    SimpleBooleanProperty showInActives = new SimpleBooleanProperty(true);
    Map<String, Map<String, Boolean>> datasetAtomMap = new HashMap<>();
    Background defaultBackground = null;
    Background errorBackground = new Background(new BackgroundFill(Color.YELLOW, null, null));

    public AtomBrowser(FXMLController controller, Consumer closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public ToolBar getToolBar() {
        return browserToolBar;
    }

    public void close() {
        closeAction.accept(this);
    }

    void initSlider(ToolBar toolBar) {
        this.browserToolBar = toolBar;
        toolBar.setPrefWidth(900.0);

        String iconSize = "16px";
        String fontSize = "7pt";
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", iconSize, fontSize, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());

        toolBar.getItems().add(closeButton);
        addFiller(toolBar);
        Molecule mol = Molecule.getActive();
        ObservableList<String> entityNames = FXCollections.observableArrayList();
        entityNames.add("*");
        if (mol != null) {
            entityNames.addAll(mol.entities.keySet());
        }

        entityChoiceBox = new ChoiceBox(entityNames);
        toolBar.getItems().add(entityChoiceBox);
        entityChoiceBox.setValue("*");
        entityChoiceBox.valueProperty().addListener(e -> setAtom());

        atomFieldLabel = new Label("Atom:");
        atomField = new TextField();
        atomField.setMaxWidth(75);
        atomField.textProperty().addListener(e -> checkAtom());
        atomField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                setAtom();
            }
        });
        toolBar.getItems().add(atomFieldLabel);
        toolBar.getItems().add(atomField);
        addFiller(toolBar);

        rangeSelector = new ChoiceBox<>();
        toolBar.getItems().add(rangeSelector);
        minField = new TextField();
        maxField = new TextField();
        minField.setMaxWidth(50);
        maxField.setMaxWidth(50);
        minField.setPrefWidth(50);
        maxField.setPrefWidth(50);
        minField.setMinWidth(40);
        maxField.setMinWidth(40);
        toolBar.getItems().add(minField);
        toolBar.getItems().add(maxField);
        rangeSelector.setOnAction(b -> updateRange());

        addRangeControl("Aro", 6.5, 8.2);
        addRangeControl("H2'", 3.8, 5.1);
        addRangeControl("H1'", 5.1, 6.2);
        addRangeControl("User", -1000.0, 1000.0);
        rangeSelector.setValue("H1'");
        setRange("H1'");
        minField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                updateRangeValue();
            }
        });
        maxField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                updateRangeValue();
            }
        });
        CheckBox activeSelection = new CheckBox("Active");
        CheckBox btSelection = new CheckBox("B/Through");
        CheckBox inactiveSelection = new CheckBox("All Entities");
        addFiller(toolBar);
        toolBar.getItems().addAll(activeSelection, btSelection, inactiveSelection);
        activeSelection.selectedProperty().bindBidirectional(showActives);
        inactiveSelection.selectedProperty().bindBidirectional(showInActives);
        btSelection.selectedProperty().bindBidirectional(showBreakThroughs);
        activeSelection.setOnAction(e -> update());
        btSelection.setOnAction(e -> update());
        inactiveSelection.setOnAction(e -> update());

        MenuButton displayModeMenu = new MenuButton("Modes");
        CheckMenuItem checkItem;
        // fixme need to do this somewhere else in case we add datasets
        Dataset.setMinimumTitles();

    }

    public void addFiller(ToolBar toolBar) {
        Pane filler = new Pane();
        HBox.setHgrow(filler, Priority.ALWAYS);
        filler.setMinWidth(25);
        filler.setMaxWidth(50);
        toolBar.getItems().add(filler);
    }

    public void update() {
        setAtom();
    }

    public boolean checkAtom() {
        String entity = entityChoiceBox.getValue();
        String current = entity + ":" + atomField.getText();
        boolean validAtom = false;
        if (defaultBackground == null) {
            defaultBackground = atomField.getBackground();
        }
        if (Molecule.getActive() != null) {
            Atom atom = Molecule.getAtomByName(current);
            if (atom != null) {
                validAtom = true;
            }
        }
        if (validAtom) {
            atomField.setBackground(defaultBackground);
        } else {
            atomField.setBackground(errorBackground);
            System.out.println("invalid " + current);
        }
        return validAtom;
    }

    public void setAtom() {
        String entity = entityChoiceBox.getValue();
        String atomSpec = entity + ":" + atomField.getText();
        if (checkAtom()) {
            setAtom(atomSpec);
        }
    }

    public void setAtom(String atomSpec) {
        if (Molecule.getActive() != null) {
            Atom atom = Molecule.getAtomByName(atomSpec);
            if (atom == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("No atom found " + atomSpec);
                alert.showAndWait();
                return;
            }
            // fixme  Jan does a resonance merge first
            List<Peak> peaks = findPeaksWithLabel(true, "*", atomSpec, "*");
            List<DrawItem> items = getDrawItems(peaks, atomSpec, true);
            controller.setBorderState(true);
            controller.setNCharts(items.size());
            controller.arrange(FractionPane.ORIENTATION.HORIZONTAL);
            String activeRange = rangeSelector.getValue();
            RangeItem rangeItem = rangeItems.get(activeRange);
            double rMin = rangeItem.min;
            double rMax = rangeItem.max;
            for (int i = 0; i < items.size(); i++) {
                PolyChart chart = controller.charts.get(i);
                chart.setActiveChart();
                DrawItem item = items.get(i);
                List<String> datasetList = new ArrayList<>();
                datasetList.add(item.dataset.getName());
                List<String> peakListList = new ArrayList<>();
                peakListList.add(item.peakList.getName());
                chart.setTitle(item.dataset.getTitle());
                chart.updateDatasets(datasetList);
                chart.updatePeakLists(peakListList);
                chart.setStyle("-fx-padding: 0px");
                chart.getChildrenUnmodifiable().stream().filter((node) -> (node instanceof Label)).forEachOrdered((node) -> {
                    node.setOnMouseClicked(e -> System.out.println("Clicked node " + item.dataset.getName()));
                });

                Double ppm = item.getShift();
                double delta = 0.1;
                if (ppm != null) {
                    double cMin = ppm - delta;
                    double cMax = ppm + delta;
                    chart.getAxis(centerDim).setLowerBound(cMin);
                    chart.getAxis(centerDim).setUpperBound(cMax);
                    chart.getAxis(rangeDim).setLowerBound(rMin);
                    chart.getAxis(rangeDim).setUpperBound(rMax);
                    chart.refresh();
                }
            }
        }
    }

    class RangeItem {

        double min;
        double max;

        RangeItem(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }

    public void addRangeControl(String name, double min, double max) {
        RangeItem rangeItem = new RangeItem(min, max);
        rangeItems.put(name, rangeItem);
        rangeSelector.getItems().add(name);
    }

    public void updateRangeValue() {
        String mode = rangeSelector.getValue();
        RangeItem rangeItem = rangeItems.get(mode);
        try {
            double min = Double.parseDouble(minField.getText());
            double max = Double.parseDouble(maxField.getText());
            rangeItem.min = min;
            rangeItem.max = max;
        } catch (NumberFormatException nfE) {

        }
        updateRange();
    }

    public void updateRange() {
        String mode = rangeSelector.getValue();
        RangeItem rangeItem = rangeItems.get(mode);
        if (rangeItem != null) {
            double min = rangeItem.min;
            double max = rangeItem.max;
            minField.setText(String.format("%.1f", min));
            maxField.setText(String.format("%.1f", max));

            controller.charts.stream().forEach(chart -> {
                try {
                    chart.getAxis(rangeDim).setLowerBound(min);
                    chart.getAxis(rangeDim).setUpperBound(max);
                } catch (NumberFormatException nfE) {

                }
            });
        }
    }

    public void setRange(String name) {
        RangeItem rangeItem = rangeItems.get(name);
        if (rangeItem != null) {
            double min = rangeItem.min;
            double max = rangeItem.max;
            minField.setText(String.format("%.1f", min));
            maxField.setText(String.format("%.1f", max));
        }
    }

    public void updateRange(TextField minField, TextField maxField) {
        controller.charts.stream().forEach(chart -> {
            try {
                double min = Double.parseDouble(minField.getText());
                double max = Double.parseDouble(maxField.getText());
                chart.getAxis(rangeDim).setLowerBound(min);
                chart.getAxis(rangeDim).setUpperBound(max);
            } catch (NumberFormatException nfE) {

            }
        });
    }

    boolean sameAtom(String aSpec1, String aSpec2, boolean requireEntity) {
        Atom atom1 = Molecule.getAtomByName(aSpec1);
        Atom atom2 = Molecule.getAtomByName(aSpec2);
        boolean result = false;
        if ((atom1 != null) && (atom2 != null)) {
            Compound cmpd1 = (Compound) atom1.entity;
            Compound cmpd2 = (Compound) atom2.entity;
            if (!requireEntity || (cmpd1 == cmpd2)) {
                if (cmpd1.getNumber().equals(cmpd2.getNumber())) {
                    if (atom1.getName().equals(atom2.getName())) {
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    boolean isDatasetActive(Dataset dataset) {
        return dataset.getProperty("active").equals("1");
    }

    boolean isListActve(PeakList peakList) {
        return peakList.getProperty("active").equals("1");
    }

    boolean isAtomActive(Dataset dataset, Atom atom) {
        // needs to be dataset specific
        boolean active = false;
        if (isDatasetActive(dataset)) {
            Map<String, Boolean> atomMap = datasetAtomMap.get(dataset.getName());
            if (atomMap != null) {
                Boolean value = atomMap.get(atom.getFullName());
                active = value == null ? false : value.booleanValue();
            }
        }
        return active;
    }

    /*
        variable labels
    set active 0

    if {[info exists labels(activedatasets)]} {
        foreach dataset $labels(activedatasets) {
            if {$labels($dataset,active) && $labels($dataset,peaklist)==$list} {
                set active 1
            }
        }
    }


     */
    List<Peak> findPeaksWithLabel(boolean requireEntity, String listPattern, String... args) {
        String[] matchStrings = new String[args.length];
        int i = 0;
        boolean hasWildCard = false;
        for (String arg : args) {
            if (arg.charAt(0) == '*') {
                matchStrings[i++] = "." + arg;
                hasWildCard = true;
            } else {
                matchStrings[i++] = arg;
            }
        }
        final boolean useRegexp = hasWildCard;
        final boolean useOrder = true;
        List<Peak> peaks = new ArrayList<>();

        PeakList.peakListTable.values().stream().forEach(peakList -> {
            if (Util.stringMatch(peakList.getName(), listPattern)) {
                List<Peak> listPeaks = peakList.matchPeaks(matchStrings, useRegexp, useOrder);
                peaks.addAll(listPeaks);
            }
        });
        return peaks;
    }

    /*
        foreach pklist [nv_peak list] {
        set results [list]
        foreach dim [nv_peak label $pklist] {
            nv_peak template $pklist $dim $tol
            set pkmatches [nv_peak find $pklist $ppm]
            foreach peak $pkmatches {
                set atom [nv_peak elem $dim.L $peak]
                if {[[namespace current]::isAtomActive $dataset $atom] || $order($pklist)=="first"} {
                    lappend results $atom
                }
            }
        }

     */
    public static class AtomDelta {

        final String name;
        final double fDelta;
        final int listType;
        final PeakDim peakDim;

        AtomDelta(String name, double fDelta, int listType, PeakDim peakDim) {
            this.name = name;
            this.fDelta = fDelta;
            this.listType = listType;
            this.peakDim = peakDim;
        }

        public double getScore() {
            return listType + fDelta;
        }

        public String getName() {
            return name;
        }

        public PeakDim getPeakDim() {
            return peakDim;
        }

        public double getFDelta() {
            return fDelta;
        }

        public String toString() {
            return String.format("%s %2d%% %1d",name, Math.round(99.0 * fDelta), listType);
        }
    }

    public static List<AtomDelta> getMatchingAtomNames(Dataset dataset, double ppm, double tol) {
        double[] ppms = {ppm};
        Map<String, AtomDelta> atomDeltaMap = new HashMap<>();
        for (PeakList peakList : PeakList.getLists()) {
            // maybe check if dataset active too
            int listType;
            if ((peakList.getDatasetName() != null) && !peakList.getDatasetName().equals("")) {
                if (peakList.getDatasetName().equals(dataset.getName())) {
                    listType = 0;
                } else {
                    listType = 2;
                }
            } else {
                listType = 3;
            }
            for (int i = 0; i < peakList.getNDim(); i++) {
                SpectralDim spectralDim = peakList.getSpectralDim(i);
                peakList.clearSearchDims();
                peakList.addSearchDim(spectralDim.getDimName(), tol);
                List<Peak> sPeaks = peakList.findPeaks(ppms);
                for (Peak sPeak : sPeaks) {
                    double delta = Math.abs(sPeak.getPeakDim(i).getChemShiftValue() - ppm) / tol;
                    String aName = sPeak.getPeakDim(i).getLabel();
                    if (!aName.equals("")) {
                        AtomDelta aDelta = new AtomDelta(aName, delta, listType, sPeak.getPeakDim(i));
                        AtomDelta current = atomDeltaMap.get(aName);
                        if ((current == null) || aDelta.getScore() < current.getScore()) {
                            atomDeltaMap.put(aName, aDelta);
                        }
                    }
                }
            }
        }
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            List<Atom> atoms = molecule.getAtoms();
            for (Atom atom : atoms) {
                if (atom.getElementNumber() == 1) { // fixme only working with H
                    Double shift = atom.getPPM();
                    if (shift != null) {
                        double delta = Math.abs(shift - ppm) / tol;
                        String aName = atom.getName();
                        AtomDelta aDelta = new AtomDelta(aName, delta, 1, null);
                        AtomDelta current = atomDeltaMap.get(aName);
                        if ((current == null) || aDelta.getScore() < current.getScore()) {
                            atomDeltaMap.put(aName, aDelta);
                        }
                    }
                }
            }
        }
        List<AtomDelta> result = atomDeltaMap.values().stream().sorted(Comparator.comparing(AtomDelta::getScore)).collect(Collectors.toList());
        return result;
    }

    class DrawItem implements Comparator<DrawItem> {

        final Dataset dataset;
        final PeakList peakList;
        final Double shift;
        final boolean active;
        final boolean breakThrough;
        final boolean frozen;

        DrawItem(Dataset dataset, PeakList peakList, double shift, boolean active, boolean breakThrough, boolean frozen) {
            this.dataset = dataset;
            this.peakList = peakList;
            this.shift = shift;
            this.active = active;
            this.breakThrough = breakThrough;
            this.frozen = frozen;

        }

        Double getShift() {
            return shift;
        }

        @Override
        public int compare(DrawItem o1, DrawItem o2) {
            return o1.shift.compareTo(o2.shift);
        }
    }

    List<DrawItem> getDrawItems(List<Peak> peaks, String aSpec, boolean requireEntity) {
        Set<String> usedResSet = new HashSet<>();
        Map<String, List<DrawItem>> datasetShifts = new HashMap<>();
        Atom atom = Molecule.getAtomByName(aSpec);
        for (Peak peak : peaks) {
            PeakList peakList = peak.getPeakList();
            if (peakList.getNDim() != 2) {
                continue;
            }
            String matchLabel = "";
            PeakDim peakDim;
            if (sameAtom(aSpec, peak.getPeakDim(0).getLabel(), requireEntity)) {
                peakDim = peak.getPeakDim(0);
                matchLabel = peakList.getSpectralDim(0).getDimName();
            } else if (sameAtom(aSpec, peak.getPeakDim(1).getLabel(), requireEntity)) {
                peakDim = peak.getPeakDim(1);
                matchLabel = peakList.getSpectralDim(1).getDimName();
            } else {
                continue;
            }
            double shift = peakDim.getChemShift();
            long resID = peakDim.getResonance().getID();
            String key = peakList.getName() + "." + resID;
            if (usedResSet.contains(key)) {
                continue;
            }
            usedResSet.add(key);
            String datasetName = peakList.getDatasetName();
            Dataset dataset = Dataset.getDataset(datasetName);
            if ((dataset == null) || datasetBlackList.contains(dataset.getName()) || (dataset.getNDim() != 2)) {
                continue;
            }
            if ((dataset.getNucleus(0) != Nuclei.H1) || (dataset.getNucleus(1) != Nuclei.H1)) {
                continue;
            }
            // fixme add blacklist for peak

            // check for list active
            // check for atom active in dataset
            if (!datasetShifts.containsKey(dataset.getName())) {
                List<DrawItem> items = new ArrayList<>();
                datasetShifts.put(dataset.getName(), items);
            }
            boolean active = isListActve(peakList);
            boolean breakThrough = false;
            if (active) {
                breakThrough = !isAtomActive(dataset, atom);
            }
            if ((active && showActives.get()) || (breakThrough && showBreakThroughs.get()) || (!active && showInActives.get())) {
                List<DrawItem> items = datasetShifts.get(dataset.getName());
                DrawItem item = new DrawItem(dataset, peakList, shift, active, breakThrough, peakDim.isFrozen());
                items.add(item);
            }
        }
        List<DrawItem> drawList = new ArrayList<>();
        for (String datasetName : datasetShifts.keySet()) {
            List<DrawItem> items = datasetShifts.get(datasetName);
            if (!items.isEmpty()) {
                items.sort(null);
                DrawItem lastItem = items.get(0);
                drawList.add(lastItem);
                for (int i = 1; i < items.size(); i++) {
                    if (Math.abs(items.get(i).getShift() - lastItem.getShift()) > 0.1) {
                        lastItem = items.get(i);
                        drawList.add(lastItem);
                    }
                }
            }
        }
        return drawList;
    }
    /*

     */
}
