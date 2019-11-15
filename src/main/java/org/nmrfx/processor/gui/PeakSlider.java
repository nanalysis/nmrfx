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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

/**
 *
 * @author Bruce Johnson
 */
public class PeakSlider {

    ToolBar sliderToolBar;
    FXMLController controller;
    Consumer closeAction;
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
    Map<PolyChart, PeakClusterMatcher> chartToMatchMap = new HashMap<>();

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
        /** FIXME: 
         *  Currently, Match Row doesn't work properly when Match Column runs first.
         *  Results of the Match Row won't show up because chart was placed inside
         *  of the mapToMatch HashMap. Need to correct:
         * 
         *  Potential Solution:
         *  chartToMatchMap<PolyChart, dimensionMatch> ==> dimensionMatch<Dim, PeakClusterMatcher>
         */
        MenuItem matchAllRowItem = new MenuItem("Match Row");
        MenuItem matchAllColItem = new MenuItem("Match Column");
        matchAllRowItem.setOnAction(e -> matchClusters(true, 1));
        matchAllColItem.setOnAction(e -> matchClusters(true, 0));
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
//        System.out.println(Arrays.toString(peaks.stream().map(p -> p.toString()).toArray()));

        if ((peaks != null) && !peaks.isEmpty()) {
            controller.charts.stream()
                    .forEach(chart -> {
                        PeakClusterMatcher matcher = getMatcher(chart);
                        if (matcher != null) {
                            List<List<Peak>> peakPairs = matcher.getPeakPairs(peaks.get(0));
                            chart.setPeakPaths(peakPairs);
                            chart.refresh();
                        }
                    });
        }
    }

    // Event handler to perform match of clusters
    public void matchClusters(boolean drawAll, int iDim) {
        // storing peak list attributes which contain information about the peak lists
        // in each chart.

        controller.charts.stream()
                .filter(chart -> chart.getPeakListAttributes().size() == 2)
                .forEach(chart -> {
                    ObservableList<PeakListAttributes> peakListAttrs = chart.getPeakListAttributes();
                    PeakListAttributes peakAttr1 = peakListAttrs.get(0);
                    PeakListAttributes peakAttr2 = peakListAttrs.get(1);
                    PeakList pl1 = peakAttr1.getPeakList();
                    PeakList pl2 = peakAttr2.getPeakList();
                    // FIXME: Should change the way we check if simulated peak list exists
                    boolean pl1ContainsSim = pl1.getSampleConditionLabel().endsWith("sim");
                    boolean pl2ContainsSim = pl2.getSampleConditionLabel().endsWith("sim");
                    boolean oneIsSim = (pl1ContainsSim || pl2ContainsSim);
                    PeakList exp = (!pl1ContainsSim) ? pl1 : (oneIsSim) ? pl2 : null;
                    PeakList pred = (pl2ContainsSim) ? pl2 : (oneIsSim) ? pl1 : null;
                    if (exp != null && pred != null) {
                        // TODO: generalizing for nDim
                        chart.clearPeakPaths();
                        setUpMatcher(chart, exp, pred, iDim);
                        if (drawAll) {
                            PeakClusterMatcher matcher = getMatcher(chart);
                            if (matcher != null) {
                                if (matcher.getMatch() == null) {
                                    matcher.runMatch();
                                }
                                List<PeakCluster[]> clusterMatches = matcher.getMatch();
                                // TODO: need to save the cluster matches made for the two peaks somewhere so that we dont have to run the matching algorithm again...
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
                                                    chart.peakPaths.add(pairedPeaks);
                                                });
                                            });
                                    chart.refresh();
                                }
                            }
                        }
                    }
                });
    }

    private void setUpMatcher(PolyChart chart, PeakList expPeakList, PeakList predPeakList, int iDim) {
        if (chartToMatchMap.containsKey(chart)) {
            return;
        }
        PeakClusterMatcher matcher = new PeakClusterMatcher(expPeakList, predPeakList, iDim);

        chartToMatchMap.put(chart, matcher);
    }

    private PeakClusterMatcher getMatcher(PolyChart chart) {
        if (chartToMatchMap.containsKey(chart)) {
            return chartToMatchMap.get(chart);
        }
        return null;
    }

    public void clearMatches() {

        controller.charts.stream()
                .forEach(chart -> {
                    chart.clearPeakPaths();
                    chart.refresh();
                    System.out.println("Matches cleared");
                });
    }

}
