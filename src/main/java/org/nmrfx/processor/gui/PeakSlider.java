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
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.Peak;
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

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.LOCK, "Freeze", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> freezePeaks());
        buttons.add(bButton);

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.UNLOCK, "Thaw", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> thawPeaks());
        buttons.add(bButton);

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BULLSEYE, "Tweak+Freeze", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> tweakPeaks());
        buttons.add(bButton);

        buttons.forEach((button) -> {
            button.getStyleClass().add("toolButton");
        });

        Pane filler1 = new Pane();
        HBox.setHgrow(filler1, Priority.ALWAYS);
        Pane filler2 = new Pane();
        HBox.setHgrow(filler2, Priority.ALWAYS);

        toolBar.getItems().add(closeButton);
        toolBar.getItems().add(filler1);
        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(filler2);

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

}
