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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
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
    Label atomXFieldLabel;
    Label atomYFieldLabel;
    Label intensityFieldLabel;
    Label atomXLabel;
    Label atomYLabel;
    Label intensityLabel;
    List<Peak> selPeaks;
    List<FreezeListener> listeners = new ArrayList<>();

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
        actionMenu.getItems().add(thawAllItem);

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
            try {
                peak.tweak(dataset, planes);
                peak.setFrozen(true, false);
                for (Peak lPeak : peaksB) {
                    peakList = lPeak.getPeakList();
                    dataset = Dataset.getDataset(peakList.fileName);
                    if (dataset != null) {
                        lPeak.tweak(dataset, planes);
                        lPeak.setFrozen(true, false);
                    }
                }
            } catch (IOException ioE) {

            }
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

        }
    }

}
