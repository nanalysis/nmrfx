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
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.nmrfx.processor.datasets.Dataset;
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
    Label atomXLabel;
    Label atomYLabel;
    List<Peak> selPeaks;

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
        freezeButton.setOnAction(e -> freezePeaks());
        buttons.add(freezeButton);

        thawButton = GlyphsDude.createIconButton(FontAwesomeIcon.UNLOCK, "Thaw", iconSize, fontSize, ContentDisplay.TOP);
        thawButton.setOnAction(e -> thawPeaks());
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
        atomXLabel = new Label();
        atomXLabel.setMinWidth(75);
        atomYLabel = new Label();
        atomYLabel.setMinWidth(75);

        Pane filler1 = new Pane();
        HBox.setHgrow(filler1, Priority.ALWAYS);
        Pane filler2 = new Pane();
        filler2.setMinWidth(50);
        Pane filler3 = new Pane();
        filler3.setMinWidth(50);
        Pane filler4 = new Pane();
        HBox.setHgrow(filler4, Priority.ALWAYS);

        toolBar.getItems().add(closeButton);
        toolBar.getItems().add(filler1);
        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(filler2);
        toolBar.getItems().addAll(atomXFieldLabel, atomXLabel, filler3, atomYFieldLabel, atomYLabel);

        toolBar.getItems().add(filler4);

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

    public void freezePeaks() {
        controller.charts.stream().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach((peak) -> {
                peak.setFrozen(true);
            });
        });

    }

    public void thawPeaks() {
        controller.charts.stream().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach((peak) -> {
                peak.setFrozen(false);
            });
        });

    }

    public void tweakPeaks() {
        controller.charts.stream().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach((peak) -> {
                PeakList peakList = peak.getPeakList();
                Dataset dataset = Dataset.getDataset(peakList.fileName);
                if (dataset != null) {
                    try {
                        peak.tweak(dataset);
                        peak.setFrozen(true);
                    } catch (IOException ioE) {

                    }
                }
            });
        });
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
            freezeButton.setDisable(true);
            thawButton.setDisable(true);
            tweakFreezeButton.setDisable(true);
            linkButton.setDisable(true);
        } else {
            // fixme axes could be swapped
            Peak peak = peaks.get(peaks.size() - 1);
            atomXLabel.setText(peak.getPeakDim(0).getLabel());
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
