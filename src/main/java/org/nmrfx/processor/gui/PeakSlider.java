/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.FreezeListener;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.processor.optimization.PeakClusterMatcher;
import org.nmrfx.processor.optimization.PeakCluster;
import javafx.collections.ObservableList;
import org.nmrfx.processor.gui.spectra.ConnectPeakAttributes;

/**
 *
 * @author Bruce Johnson
 */
public class PeakSlider {

    ToolBar sliderToolBar;
    FXMLController controller;
    Consumer closeAction;
    Button shiftFreezeButton;
    Button freezeButton;
    Button thawButton;
    Button tweakFreezeButton;
    Button linkButton;
    Button unlinkButton;
    Label atomXFieldLabel;
    Label atomYFieldLabel;
    Label intensityFieldLabel;
    Label atomXLabel;
    Label atomYLabel;
    Label intensityLabel;
    List<Peak> selPeaks;
    List<FreezeListener> listeners = new ArrayList<>();
    PeakClusterMatcher[] matchers = new PeakClusterMatcher[2];

    public PeakSlider(FXMLController controller, Consumer closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
        setupLists(true);
    }

    public ToolBar getToolBar() {
        return sliderToolBar;
    }

    public void close() {
        setupLists(false);
        closeAction.accept(this);
    }

    void initSlider(ToolBar toolBar) {
        this.sliderToolBar = toolBar;

        String iconSize = "16px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", iconSize, fontSize, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());

        shiftFreezeButton = GlyphsDude.createIconButton(FontAwesomeIcon.CODE_FORK, "Shift+Freeze", iconSize, fontSize, ContentDisplay.TOP);
        shiftFreezeButton.setOnMouseClicked(e -> evalMatchCriteria(selPeaks));
        buttons.add(shiftFreezeButton);

        freezeButton = GlyphsDude.createIconButton(FontAwesomeIcon.LOCK, "Freeze", iconSize, fontSize, ContentDisplay.TOP);
        freezeButton.setOnAction(e -> freezePeaks(e));
        freezeButton.setOnMouseClicked(e -> freezePeaks(e));
        buttons.add(freezeButton);

        thawButton = GlyphsDude.createIconButton(FontAwesomeIcon.UNLOCK, "Thaw", iconSize, fontSize, ContentDisplay.TOP);
        thawButton.setOnAction(e -> thawPeaks(e));
        thawButton.setOnMouseClicked(e -> thawPeaks(e));
        buttons.add(thawButton);

        tweakFreezeButton = GlyphsDude.createIconButton(FontAwesomeIcon.BULLSEYE, "Tweak+Freeze", iconSize, fontSize, ContentDisplay.TOP);
        tweakFreezeButton.setOnAction(e -> tweakPeaks());
        buttons.add(tweakFreezeButton);

        linkButton = GlyphsDude.createIconButton(FontAwesomeIcon.CHAIN, "Link", iconSize, fontSize, ContentDisplay.TOP);
        linkButton.setOnAction(e -> linkDims());
        buttons.add(linkButton);

        unlinkButton = GlyphsDude.createIconButton(FontAwesomeIcon.CHAIN_BROKEN, "UnLink", iconSize, fontSize, ContentDisplay.TOP);
        unlinkButton.setOnAction(e -> unlinkDims());
        buttons.add(unlinkButton);

        buttons.forEach((button) -> {
            button.getStyleClass().add("toolButton");
        });

        atomXFieldLabel = new Label("X:");
        atomYFieldLabel = new Label("Y:");
        intensityFieldLabel = new Label("I:");
        atomXLabel = new Label();
        atomXLabel.setMinWidth(75);
        atomYLabel = new Label();
        atomYLabel.setMinWidth(75);
        intensityLabel = new Label();
        intensityLabel.setMinWidth(75);

        MenuButton actionMenu = new MenuButton("Actions");
        MenuItem thawAllItem = new MenuItem("Thaw All");
        thawAllItem.setOnAction(e -> thawAllPeaks());
        MenuItem restoreAllItem = new MenuItem("Restore All Peaks");
        restoreAllItem.setOnAction(e -> restoreAllPeaks());
        MenuItem restoreItem = new MenuItem("Restore Peaks");
        restoreItem.setOnAction(e -> restorePeaks());
        Menu matchingMenu = new Menu("Perform Match");
        MenuItem matchAllRowItem = new MenuItem("Match Row");
        MenuItem matchAllColItem = new MenuItem("Match Column");
        matchAllRowItem.setOnAction(e -> matchClusters(1));
        matchAllColItem.setOnAction(e -> matchClusters(0));
        MenuItem clearMatchItem = new MenuItem("Clear Matches");
        clearMatchItem.setOnAction(e -> clearMatches());
        matchingMenu.getItems().addAll(matchAllRowItem, matchAllColItem, clearMatchItem);

        actionMenu.getItems().addAll(thawAllItem, restoreItem, restoreAllItem, matchingMenu);

        Pane filler1 = new Pane();
        HBox.setHgrow(filler1, Priority.ALWAYS);
        Pane filler2 = new Pane();
        filler2.setMinWidth(50);
        Pane filler3 = new Pane();
        filler3.setMinWidth(50);
        Pane filler4 = new Pane();
        filler4.setMinWidth(50);
        Pane filler5 = new Pane();
        HBox.setHgrow(filler5, Priority.ALWAYS);

        toolBar.getItems().add(closeButton);
        toolBar.getItems().add(filler1);
        toolBar.getItems().add(actionMenu);
        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(filler2);
        toolBar.getItems().addAll(atomXFieldLabel, atomXLabel, filler3, atomYFieldLabel, atomYLabel, filler4, intensityFieldLabel, intensityLabel);

        toolBar.getItems().add(filler5);

        controller.selPeaks.addListener(e -> setActivePeaks(controller.selPeaks.get()));
    }

    public final void setupLists(final boolean state) {
        controller.charts.stream().forEach(chart -> {
            chart.getPeakListAttributes().stream().forEach(peakListAttr -> {
                // XXX unclear why the following cast is necessary
                ((PeakListAttributes) peakListAttr).getPeakList().setSlideable(state);
            });
        });
    }

    boolean getAltState(Event event) {
        boolean altState = false;
        if (event instanceof MouseEvent) {
            MouseEvent mEvent = (MouseEvent) event;
            if (mEvent.isAltDown()) {
                altState = true;
            }
        }
        return altState;
    }

    boolean shouldRespond(Event event) {
        boolean shouldRespond = event instanceof ActionEvent;
        if (event instanceof MouseEvent) {
            MouseEvent mEvent = (MouseEvent) event;
            if (mEvent.isAltDown()) {
                shouldRespond = true;
            }
        }
        return shouldRespond;
    }

    public void freezePeaks(Event event) {
        if (shouldRespond(event)) {
            freezePeaks(getAltState(event));
        }
    }

//    public void freezePeaks(MouseEvent event) {
//        System.out.println(event.getEventType().toString() + " " + event.getSource());
//        
//        freezePeaks(event.isAltDown());
//        event.consume();
//        
//    }
    public void freezePeaks(boolean useAllConditions) {
        // do setup because we could have added a peak list after adding slider controller.  Should be a better way
        setupLists(true);
        controller.charts.stream().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach((peak) -> {
                peak.setFrozen(true, useAllConditions);
                PeakList.notifyFreezeListeners(peak, true);
            });
        });

    }

    public void thawPeaks(Event event) {
        if (shouldRespond(event)) {
            thawPeaks(getAltState(event));
        }
    }

    public void thawPeaks(boolean useAllConditions) {
        // do setup because we could have added a peak list after adding slider controller.  Should be a better way
        setupLists(true);
        controller.charts.stream().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach((peak) -> {
                peak.setFrozen(false, useAllConditions);
                PeakList.notifyFreezeListeners(peak, false);
            });
        });

    }

    public void thawAllPeaks() {
        controller.charts.stream().forEach(chart -> {
            chart.getPeakListAttributes().stream().forEach(peakListAttr -> {
                // XXX unclear why the following cast is necessary
                PeakList peakList = ((PeakListAttributes) peakListAttr).getPeakList();
                peakList.peaks().stream().forEach(peak -> {
                    peak.setFrozen(false, true);
                });
            });
        });
    }

    public void tweakPeaks() {
        // do setup because we could have added a peak list after adding slider controller.  Should be a better way
        setupLists(true);
        controller.charts.stream().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach((peak) -> {
                tweakPeak(peak);
            });
        });
    }

    public void tweakPeak(Peak peak) {
        Set<Peak> peakSet = new HashSet<>();
        int nDim = peak.getPeakList().getNDim();
        for (int i = 0; i < nDim; i++) {
            List<Peak> peaks = PeakList.getLinks(peak, i);
            peakSet.addAll(peaks);
        }

        List<Peak> peaksB = new ArrayList<>();
        // find all peaks that are linked in all dimensions to original peak
        // These are the peaks that should be tweaked when original peak is tweaked.
        // fixme add test for condition, if not useAllConditions (and pass this in as arg)
        for (Peak speak : peakSet) {
            if (speak == peak) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < nDim; i++) {
                if (!PeakList.isLinked(peak, i, speak)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                peaksB.add(speak);
            }
        }

        PeakList peakList = peak.getPeakList();
        Dataset dataset = Dataset.getDataset(peakList.fileName);
        int[] planes = new int[0];
        if (dataset != null) {
            int[] pdim = peakList.getDimsForDataset(dataset, true);

            try {
                peak.tweak(dataset, pdim, planes);
                peak.setFrozen(true, false);
                PeakList.notifyFreezeListeners(peak, true);
                for (Peak lPeak : peaksB) {
                    peakList = lPeak.getPeakList();
                    dataset = Dataset.getDataset(peakList.fileName);
                    if (dataset != null) {
                        lPeak.tweak(dataset, pdim, planes);
                        lPeak.setFrozen(true, false);
                        PeakList.notifyFreezeListeners(lPeak, true);
                    }
                }
            } catch (IOException ioE) {

            }
        }
    }

    public void restorePeaks() {
        // do setup because we could have added a peak list after adding slider controller.  Should be a better way
        setupLists(true);
        controller.charts.stream().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach((peak) -> {
                for (PeakDim peakDim : peak.getPeakDims()) {
                    String label = peakDim.getLabel();
                    Atom atom = Molecule.getAtomByName(label);
                    if (atom != null) {
                        Double refPPM = atom.getRefPPM();
                        if (refPPM != null) {
                            peakDim.setChemShift(refPPM.floatValue());
                        }
                    }
                }
                peak.setFrozen(false, true);
            });
        });
    }

    public void restoreAllPeaks() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Move all peaks back to predicted positions?");
        alert.showAndWait().ifPresent(response -> {
            controller.charts.stream().forEach(chart -> {
                chart.getPeakListAttributes().stream().forEach(peakListAttr -> {
                    // XXX unclear why the following cast is necessary
                    PeakList peakList = ((PeakListAttributes) peakListAttr).getPeakList();
                    peakList.peaks().stream().forEach(peak -> {
                        for (PeakDim peakDim : peak.getPeakDims()) {
                            String label = peakDim.getLabel();
                            Atom atom = Molecule.getAtomByName(label);
                            if (atom != null) {
                                Double refPPM = atom.getRefPPM();
                                if (refPPM != null) {
                                    peakDim.setChemShift(refPPM.floatValue());
                                }
                            }
                        }
                        peak.setFrozen(false, true);
                    });
                });
            });
        });

    }

    public void unlinkDims() {
        if (selPeaks.size() == 1) {
            PeakDim peakDim00 = selPeaks.get(0).getPeakDim(0);
            PeakDim peakDim10 = selPeaks.get(0).getPeakDim(1);
            peakDim00.unLink();
            peakDim00.setFrozen(false);
            peakDim10.unLink();
            peakDim10.setFrozen(false);
        }
    }

    public void linkDims() {
        if (selPeaks.size() == 2) {
            // figure out which dimension to link
            PeakDim peakDim00 = selPeaks.get(0).getPeakDim(0);
            PeakDim peakDim01 = selPeaks.get(1).getPeakDim(0);
            PeakDim peakDim10 = selPeaks.get(0).getPeakDim(1);
            PeakDim peakDim11 = selPeaks.get(1).getPeakDim(1);
            double delta0 = Math.abs(peakDim00.getChemShift() - peakDim01.getChemShift());
            double delta1 = Math.abs(peakDim10.getChemShift() - peakDim11.getChemShift());
            // scale delta ppm between peaks by the bounds of the peaks so we can 
            //  compare axes with different nuclei
            double delta0F = Math.abs(delta0 / peakDim00.getBounds());
            double delta1F = Math.abs(delta1 / peakDim10.getBounds());
            PeakDim peakDim0;
            PeakDim peakDim1;

            // the peak dimension with smallest delta between peak is used for linking
            double delta;
            if (delta0F < delta1F) {
                peakDim0 = peakDim00;
                peakDim1 = peakDim01;
                delta = delta0F;
            } else {
                peakDim0 = peakDim10;
                peakDim1 = peakDim11;
                delta = delta1F;
            }

            // fixme  should query user if link should be made
            if (Math.abs(delta) > 2.0) {
                return;

            }

            // format link peak dims should link the unlabelled to the labelled
            if (peakDim1.getLabel().equals("")) {
                PeakList.linkPeakDims(peakDim0, peakDim1);
                // force a reset of shifts so new peak gets shifted to the groups shift
                peakDim0.setChemShift(peakDim0.getChemShift());
                peakDim0.setFrozen(peakDim0.isFrozen());
            } else {
                PeakList.linkPeakDims(peakDim1, peakDim0);
                peakDim1.setChemShift(peakDim1.getChemShift());
                peakDim1.setFrozen(peakDim1.isFrozen());
            }
        }
    }

    public void setActivePeaks(List<Peak> peaks) {
        System.out.println("setActivePeaks(" + peaks + ")");
        selPeaks = peaks;
        if ((peaks == null) || peaks.isEmpty()) {
            atomXLabel.setText("");
            atomYLabel.setText("");
            intensityLabel.setText("");
            freezeButton.setDisable(true);
            thawButton.setDisable(true);
            tweakFreezeButton.setDisable(true);
            linkButton.setDisable(true);
            unlinkButton.setDisable(false);
        } else {
            // fixme axes could be swapped
            Peak peak = peaks.get(peaks.size() - 1);
            atomXLabel.setText(peak.getPeakDim(0).getLabel());
            intensityLabel.setText(String.format("%.2f", peak.getIntensity()));
            if (peak.getPeakDims().length > 1) {
                atomYLabel.setText(peak.getPeakDim(1).getLabel());
            }
            freezeButton.setDisable(false);
            thawButton.setDisable(false);
            tweakFreezeButton.setDisable(false);
            if (peaks.size() == 2) {
                linkButton.setDisable(false);
            }
            if (peaks.size() == 1) {
                unlinkButton.setDisable(false);
            }
        }

        if ((peaks != null) && !peaks.isEmpty()) {
            controller.charts.stream()
                    .forEach(chart -> {
                        chart.clearPeakPaths();
                        for (PeakClusterMatcher matcher : matchers) {
                            if (matcher != null) {
                                Peak p0 = peaks.get(0);
                                PeakCluster clus = matcher.getCluster(p0);
                                List<ConnectPeakAttributes> matchingPeaks = getPeakMatchingAttrs(clus);
                                if (matchingPeaks != null) {
                                    matchingPeaks.forEach(pairedPeaksAttrs -> {
                                        if (pairedPeaksAttrs != null) {
                                            chart.addPeakPath(pairedPeaksAttrs);
                                        }
                                    });
                                }
                                chart.drawPeakLists(true);
                            }
                        }
                    });
        }
    }

    List<ConnectPeakAttributes> getPeakMatchingAttrs(PeakCluster clus) {
        if (clus == null) {
            return null;
        }
        List<ConnectPeakAttributes> matchingPeaks = new ArrayList<>();
        PeakCluster pairedClus = clus.getPairedTo();
        if (pairedClus != null) {
            clus.getLinkedPeaks().forEach((pInClus) -> {
                for (PeakClusterMatcher matcher : matchers) {
                    if (matcher != null) {
                        Peak mPeak = matcher.getMatchingPeak(pInClus);
                        List<Peak> pair = new ArrayList<>(Arrays.asList(pInClus, mPeak));
                        boolean isColumn = matcher.getMatchDim() == 0;
                        ConnectPeakAttributes connPair = setPeakPairAttrs(isColumn, pair);
                        matchingPeaks.add(connPair);
                    }
                }
            });
        }
        return matchingPeaks;
    }

    ConnectPeakAttributes setPeakPairAttrs(boolean isColumn, List<Peak> pairedPeaks) {
        if (pairedPeaks.isEmpty()) {
            return null;
        }
        Color color = (isColumn) ? Color.ORANGE : Color.BLUE;
        double opacity = (isColumn) ? 0.7 : 0.4;
        double width = (isColumn) ? 3.0 : 4.0;
        ConnectPeakAttributes connPeakAttrs = new ConnectPeakAttributes(pairedPeaks);
        connPeakAttrs.setWidth(width);
        connPeakAttrs.setColor(color.toString(), opacity);
        return connPeakAttrs;
    }

    public void evalMatchCriteria(List<Peak> peaks) {
        System.out.println("overlapMatches(" + peaks + ")");
        if (isMatcherNull()) {
            System.out.println("Both matchers must be valid (none should be null)");
            return;
        }
        if (peaks != null && !peaks.isEmpty()) {
            Peak clickedPeak = peaks.get(0);
            boolean c1ClickedPeak = satisfyCriteria1(clickedPeak);
            boolean c2ClickedPeak = satisfyCriteria2(clickedPeak, c1ClickedPeak);
            System.out.println(String.format("\tClicked peak '%s' meets criteria 1 (%s) and 2 (%s)", clickedPeak, c1ClickedPeak, c2ClickedPeak));

            if (c1ClickedPeak && c2ClickedPeak) {
                List<Peak> peaksToFreeze = new ArrayList<>();
                peaksToFreeze.add(clickedPeak);
                shiftAndFreezePeak(clickedPeak);

                for (PeakClusterMatcher matcher : matchers) {
                    PeakCluster simClus = matcher.getCluster(clickedPeak);

                    simClus.getLinkedPeaks().forEach((p) -> { // sim peak
                        if (!p.equals(clickedPeak)) {
                            boolean c1AssocPeak = satisfyCriteria1(p);
                            boolean c2AssocPeak = satisfyCriteria2(p, c1AssocPeak);
                            System.out.println(String.format("\tPeak '%s' linked to the clicked peak in dimension '%d' satisfies criteria 1 (%s) and 2 (%s)", p, matcher.getMatchDim(), c1AssocPeak, c2AssocPeak));
                            int evalDim = (matcher.getMatchDim() == 0) ? 1 : 0;
                            if (c1AssocPeak && c2AssocPeak) {
                                peaksToFreeze.add(p);
                                freezeMatchPeakDim(p, matcher.getMatchingPeak(p), evalDim);
                            } else {
                                PeakCluster bestCluster = calcClusterScores(p, clickedPeak, evalDim);
                                if (bestCluster == null) {
                                    return; // continues to next iteration
                                }
                                System.out.println(String.format("\tThe cluster containing peak '%s' best matches cluster containing '%s'", p, bestCluster.rootPeak));
                                Peak matchingPeak = matcher.getMatchingPeak(p); // experimental peak matching sim peak
                                if (bestCluster.contains(matchingPeak) && !p.getPeakDim(evalDim).isFrozen()) {
                                    freezeMatchPeakDim(p, matchingPeak, evalDim);
                                }

                            }
                        }
                    });
                }
                System.out.println("All peaks to freeze: " + peaksToFreeze);
                printClusterScore(clickedPeak, 0);
                printClusterScore(clickedPeak, 1);
            }
        }
    }

    void freezeMatchPeakDim(Peak peakToFreeze, Peak assocPeak, int iDim) {
        if (peakToFreeze == null || assocPeak == null) {
            return;
        }
        PeakDim peakDim = peakToFreeze.getPeakDim(iDim);
        PeakDim assocPeakDim = assocPeak.getPeakDim(iDim);
        if (peakDim != null && assocPeakDim != null && !peakDim.isFrozen()) {
            peakDim.setChemShiftValue(assocPeakDim.getChemShiftValue());
            peakDim.setFrozen(true);
        }
        boolean allDimsFrozen = true;
        for (PeakDim p : peakToFreeze.getPeakDims()) {
            if (!p.isFrozen()) {
                allDimsFrozen = false;
                break;
            }
        }
        if (allDimsFrozen) {
            PeakList.notifyFreezeListeners(peakToFreeze, false);
        }
    }

    void shiftMatchPeakDim(Peak peak) {
        if (isMatcherNull()) {
            return;
        }
        for (PeakClusterMatcher matcher : matchers) {
            PeakCluster pClus = matcher.getCluster(peak);
            int evalDim = pClus.iDim;
            pClus.getLinkedPeaks().forEach((p) -> {
                if (!p.equals(peak)) {
                    freezeMatchPeakDim(p, peak, evalDim);
                }
            });
        }
    }

    // FIXME: ASSUMES CRITERIA 1 AND 2 ARE MET. SHOULDN'T RUN INDEPENDENTLY.
    public void shiftAndFreezePeak(Peak p0) {
        System.out.println("shiftAndFrezePeak('" + p0 + "')");
        if (isMatcherNull()) {
            return;
        }
        PeakClusterMatcher colMatcher = matchers[0];
        Peak p0MatchPeak = colMatcher.getMatchingPeak(p0);
        // shift and freeze the selected peak
        for (int i = 0; i < p0.peakDims.length; i++) {
            freezeMatchPeakDim(p0, p0MatchPeak, i);
        }
        // shift all other peaks based on selected peak
        shiftMatchPeakDim(p0);
    }

    /**
     * Criteria #1: Given a selected peak 1) Peak must be a simulated peak 2)
     * Both row and column matchers must've been instantiated 3) Both the row
     * and column peak matches for the given peak must be the same, e.g:
     * simulated peak must match the same experimental peak in both row and
     * column peak matches
     *
     */
    boolean satisfyCriteria1(Peak peak) {
        System.out.println("\tsatisfyCriteria1('" + peak + "')");
        boolean criteria1Met = false;
        if (peak.getPeakList().isSimulated() && matchers.length == 2) {
            Peak tempMatchingPeak = null;
            for (PeakClusterMatcher matcher : matchers) {
                Peak currentMatchingPeak = matcher.getMatchingPeak(peak);
                if (currentMatchingPeak != null) {
                    if (tempMatchingPeak == null) {
                        tempMatchingPeak = currentMatchingPeak;
                        continue;
                    }
                    if (currentMatchingPeak.equals(tempMatchingPeak)) {
                        criteria1Met = true;
                    }
                }
            }
        }
        return criteria1Met;
    }

    /**
     * Criteria #2: Given a selected peak 1) Peak must have passed criteria #1
     * 2) Experimental (exp) peak matched to given simulated (sim) peak doesn't
     * have other exp peaks within a 0.10 ppm tolerance 3) If #2 is false, then
     * calculate the weights b/t sim peak and the exp peaks within tol and
     * display them
     */
    boolean satisfyCriteria2(Peak peak, boolean criteria1Met) {
        System.out.println("\tsatisfyCriteria2('" + peak + "', '" + criteria1Met + "')");
        boolean criteria2Met = false;
        if (criteria1Met) {
            for (PeakClusterMatcher matcher : matchers) {
                double tolerance = 0.10;
                int matchDim = matcher.getMatchDim();
                int dim = (matchDim == 0) ? 1 : 0;
                Peak matchingPeak = matcher.getMatchingPeak(peak); // experimental peak
                if (matchingPeak != null) {
                    List<Peak> peaksInClus = matcher.getCluster(matchingPeak).getLinkedPeaks();
                    List<Peak> peaksWithinTol = new ArrayList<>();
                    double matchPeakShift = matchingPeak.getPeakDim(dim).getChemShift();
                    peaksInClus.forEach((p) -> {
                        double peakShift = p.getPeakDim(dim).getChemShift();
                        double shiftDiff = Math.abs(peakShift - matchPeakShift);
                        System.out.println(String.format("\t\tIn dimension '%d', shift difference b/t experimental peak '%s' compared to peak '%s' is '%f'.", matchDim, matchingPeak, p, shiftDiff));
                        if (shiftDiff != 0.0 && shiftDiff < tolerance) {
                            peaksWithinTol.add(p);
                        }
                    });
                    if (peaksWithinTol.isEmpty()) {
                        criteria2Met = true;
                    } else {
                        peaksWithinTol.forEach(peakInTol -> {
                            double w = PeakCluster.calcWeight(peak, peakInTol, peak.getPeakList().scale);
                            System.out.println(String.format("\t\t* Predicted peak '%s' has experimental peak '%s' within a '%f' ppm tolerance. The weight b/t the 2 peaks is '%f'.", peak, peakInTol, tolerance, w));
                        });
                    }
                }
            }
        }
        return criteria2Met;
    }

    void printClusterScore(Peak predPeak, int iDim) {
        if (isMatcherNull()) {
            return;
        }
        String label = (iDim == 1) ? "Row" : "Column";
        int jDim = (iDim == 1) ? 0 : 1;
        PeakClusterMatcher iMatcher = matchers[iDim];
        PeakClusterMatcher jMatcher = matchers[jDim];
        System.out.println("\n\n" + label + " Cluster Score Calculation");
        PeakCluster iPredClus = iMatcher.getCluster(predPeak);
        Peak expPeak = iMatcher.getMatchingPeak(predPeak);
        List<Peak> peaksPairedToExp = jMatcher.getCluster(expPeak).getLinkedPeaks();

        peaksPairedToExp.stream()
                .map((assocExpPeak) -> {
                    PeakCluster jAssocExpClus = iMatcher.getCluster(assocExpPeak);
                    System.out.println(String.format("Weights b/t predicted " + label + " cluster of '%s' and experimental cluster containing '%s'", predPeak, assocExpPeak));

                    iPredClus.getLinkedPeaks()
                            .forEach((predP) -> {
                                if (jAssocExpClus != null) {
                                    jAssocExpClus.getLinkedPeaks()
                                            .forEach((eP) -> {
                                                double w = PeakCluster.calcWeight(predP, eP, predP.getPeakList().scale);
                                                System.out.println(String.format("\tPred Peak ('%s') and Exp Peak ('%s'), weight : '%f'", predP, eP, w));
                                            });
                                } else {
                                    System.out.println("Associated peak '" + assocExpPeak + "' returned a null cluster.");
                                }
                            });
                    return jAssocExpClus;
                })
                .map((iExpClus) -> iPredClus.comparisonScore(iExpClus))
                .forEachOrdered((clusScore) -> {
                    System.out.println("Cluster score: " + clusScore);
                });
    }

    /**
     * Given a peak, calculate scores between clusters and retrieve the cluster
     * with best score.
     *
     *
     * @param assocPeak (simulated peak)
     * @param iDim (Dimension [0 (column) or 1 (row)])
     *
     * @return bestPairedPeakClus (Cluster with best score, or null)
     */
    PeakCluster calcClusterScores(Peak assocPeak, Peak clickedPeak, int iDim) {
        if (isMatcherNull()) {
            return null;
        }
        PeakCluster bestPairedPeakClus = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        PeakClusterMatcher colMatcher = matchers[0];
        PeakClusterMatcher rowMatcher = matchers[1];
        boolean doRow = iDim == 1;
        PeakCluster p1Clus = (doRow)
                ? rowMatcher.getCluster(assocPeak) : colMatcher.getCluster(assocPeak);
        Peak clickedMatchPeak = (doRow)
                ? rowMatcher.getMatchingPeak(clickedPeak) : colMatcher.getMatchingPeak(clickedPeak);
        if (p1Clus != null && clickedMatchPeak != null) {
            PeakCluster matchPeakClus = (doRow)
                    ? colMatcher.getCluster(clickedMatchPeak) : rowMatcher.getCluster(clickedMatchPeak);
            List<Peak> matchPeakLinkedList = (matchPeakClus != null)
                    ? matchPeakClus.getLinkedPeaks() : (new ArrayList<>());
            for (Peak p : matchPeakLinkedList) {
                PeakCluster pClus = (doRow)
                        ? rowMatcher.getCluster(p) : colMatcher.getCluster(p);
                double currScore = p1Clus.comparisonScore(pClus);
                if (bestPairedPeakClus == null || currScore > bestScore) {
                    bestScore = currScore;
                    bestPairedPeakClus = pClus;
                }
            }
        }
        return ((bestScore >= 0) ? bestPairedPeakClus : null);
    }

    public void matchClusters(int iDim) {
        System.out.println("matchClusters(" + iDim + ")");
        // storing peak list attributes which contain information about the peak lists
        // in each chart.
        List<PeakList> predLists = new ArrayList<>();
        List<PeakList> expLists = new ArrayList<>();
        controller.charts.stream()
                .filter(chart -> chart.getPeakListAttributes().size() == 2)
                .forEach(chart -> {
                    double xMin = chart.getXAxis().getLowerBound();
                    double xMax = chart.getXAxis().getUpperBound();
                    double yMin = chart.getYAxis().getLowerBound();
                    double yMax = chart.getYAxis().getUpperBound();
                    double[][] limits = {{xMin, xMax}, {yMin, yMax}};
                    ObservableList<PeakListAttributes> peakListAttrs = chart.getPeakListAttributes();
                    PeakListAttributes peakAttr1 = peakListAttrs.get(0);
                    PeakListAttributes peakAttr2 = peakListAttrs.get(1);
                    PeakList pl1 = peakAttr1.getPeakList();
                    PeakList pl2 = peakAttr2.getPeakList();
                    // FIXME: Should change the way we check if simulated peak list exists
                    boolean pl1ContainsSim = pl1.isSimulated();
                    boolean pl2ContainsSim = pl2.isSimulated();
                    boolean oneIsSim = (pl1ContainsSim || pl2ContainsSim);
                    PeakList exp = (!pl1ContainsSim) ? pl1 : (oneIsSim) ? pl2 : null;
                    PeakList pred = (pl2ContainsSim) ? pl2 : (oneIsSim) ? pl1 : null;

                    if (exp != null && pred != null) {
                        expLists.add(exp);
                        predLists.add(pred);
                        PeakCluster.prepareList(exp, limits);
                        PeakCluster.prepareList(pred, limits);
                    }
                });
        matchers[iDim] = new PeakClusterMatcher(expLists, predLists, iDim);
        matchers[iDim].runMatch();
        drawMatches();
    }

    void drawMatches() {
        controller.charts.stream()
                .filter(chart -> chart.getPeakListAttributes().size() == 2)
                .forEach(chart -> {
                    for (PeakClusterMatcher matcher : matchers) {
                        if (matcher == null) {
                            continue;
                        }
                        List<PeakCluster[]> clusterMatches = matcher.getClusterMatch(); // list of matched clusters
                        if (clusterMatches != null) {
                            clusterMatches.stream()
                                    .map((clusMatch) -> {
                                        PeakCluster expCluster = clusMatch[0];
                                        PeakCluster predCluster = clusMatch[1];
                                        List<List<Peak>> matchingPeaks = expCluster.getPeakMatches(predCluster);
                                        return matchingPeaks;
                                    })
                                    .forEachOrdered((matchingPeaks) -> {
                                        matchingPeaks.forEach(pairedPeaks -> {
                                            boolean isColumn = matcher.getMatchDim() == 0;
                                            ConnectPeakAttributes connPeakAttrs = setPeakPairAttrs(isColumn, pairedPeaks);
                                            if (connPeakAttrs != null) {
                                                chart.addPeakPath(connPeakAttrs);
                                            }
                                        }
                                        );
                                    });
                            chart.refresh();
                        }
                    }
                });
    }

    public void clearMatches() {
        for (int i = 0; i < matchers.length; i++) {
            matchers[i] = null;
        }

        controller.charts.stream().forEach(chart -> {
            chart.clearPeakPaths();
            chart.refresh();
        });
    }

    boolean isMatcherNull() {
        boolean isNull = false;
        for (PeakClusterMatcher matcher : matchers) {
            if (matcher == null) {
                isNull = true;
                break;
            }
        }
        return isNull;
    }
}
