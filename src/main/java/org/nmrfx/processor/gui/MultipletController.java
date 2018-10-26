/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.processor.datasets.peaks.Analyzer;
import org.nmrfx.processor.datasets.peaks.ComplexCoupling;
import org.nmrfx.processor.datasets.peaks.Coupling;
import org.nmrfx.processor.datasets.peaks.CouplingPattern;
import org.nmrfx.processor.datasets.peaks.Multiplet;
import org.nmrfx.processor.datasets.peaks.Multiplets;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.Singlet;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.MultipletSelection;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;

/**
 *
 * @author brucejohnson
 */
public class MultipletController implements Initializable {

    Stage stage = null;
    ToolBar navigatorToolBar;
    TextField multipletIdField;
    @FXML
    ToolBar toolBar;
    @FXML
    GridPane gridPane;
    ChoiceBox<String>[] patternChoices;
    TextField[] couplingFields;
    TextField[] slopeFields;
    private PolyChart chart;
    Optional<Multiplet> activeMultiplet = Optional.empty();

    public MultipletController() {
        System.out.println("ne con");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("init");
        String[] patterns = {"d", "t", "q", "p", "h", "dd", "ddd", "dddd"};
        int nCouplings = 5;
        double width1 = 30;
        double width2 = 80;
        double width3 = 60;
        patternChoices = new ChoiceBox[nCouplings];
        couplingFields = new TextField[nCouplings];
        slopeFields = new TextField[nCouplings];
        for (int iRow = 0; iRow < nCouplings; iRow++) {
            Label rowLabel = new Label(String.valueOf(iRow + 1));
            rowLabel.setPrefWidth(width1);
            rowLabel.setTextAlignment(TextAlignment.CENTER);
            patternChoices[iRow] = new ChoiceBox<>();
            patternChoices[iRow].setPrefWidth(width2);
            if (iRow == 0) {
                patternChoices[iRow].getItems().add("");
                patternChoices[iRow].getItems().add("m");
                patternChoices[iRow].getItems().add("s");
            } else {
                patternChoices[iRow].getItems().add("");
            }
            patternChoices[iRow].getItems().addAll(patterns);
            patternChoices[iRow].setValue(patternChoices[iRow].getItems().get(0));
            couplingFields[iRow] = new TextField();
            slopeFields[iRow] = new TextField();
            couplingFields[iRow].setPrefWidth(width3);
            slopeFields[iRow].setPrefWidth(width3);
            gridPane.add(rowLabel, 0, iRow);
            gridPane.add(patternChoices[iRow], 1, iRow);
            gridPane.add(couplingFields[iRow], 2, iRow);
            gridPane.add(slopeFields[iRow], 3, iRow);
        }
        initNavigator(toolBar);
        System.out.println("get init from chart " + chart);
    }

    public void initNavigator(ToolBar toolBar) {
        this.navigatorToolBar = toolBar;
        multipletIdField = new TextField();
        multipletIdField.setMinWidth(75);
        multipletIdField.setMaxWidth(75);

        String iconSize = "12px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstMultiplet(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousMultiplet(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextMultiplet(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastMultiplet(e));
        buttons.add(bButton);
        Button deleteButton = GlyphsDude.createIconButton(FontAwesomeIcon.BAN, "", fontSize, iconSize, ContentDisplay.GRAPHIC_ONLY);
        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(e -> e.consume());
        deleteButton.setOnAction(e -> deleteMultiplet());

        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(multipletIdField);
        toolBar.getItems().add(deleteButton);

        multipletIdField.setOnKeyReleased(kE -> {
            if (null != kE.getCode()) {
                switch (kE.getCode()) {
                    case ENTER:
                        gotoPeakId(multipletIdField);
                        break;
                    default:
                        break;
                }
            }
        });

    }

    void deleteMultiplet() {

    }

    List<Multiplet> getMultiplets() {
        List<Multiplet> multiplets = Collections.EMPTY_LIST;
        Optional<PeakList> peakListOpt = getPeakList();
        if (peakListOpt.isPresent()) {
            PeakList peakList = peakListOpt.get();
            if ((peakList.getMultiplets() != null)) {
                multiplets = peakList.getMultiplets();
            }
        }
        return multiplets;
    }

    void updateMultipletField() {
        if (activeMultiplet.isPresent()) {
            multipletIdField.setText(String.valueOf(activeMultiplet.get().getIDNum()));
            refreshPeakView(activeMultiplet.get());
            String mult = activeMultiplet.get().getMultiplicity();
            System.out.println(mult);
            Coupling coup = activeMultiplet.get().getCoupling();
            updateCouplingChoices(coup);
        } else {
            multipletIdField.setText("");
        }
    }

    void clearCouplingChoices() {
        for (int i = 0; i < patternChoices.length; i++) {
            patternChoices[i].setValue("");
            couplingFields[i].setText("");
            slopeFields[i].setText("");
        }
    }

    void updateCouplingChoices(Coupling coup) {
        String[] couplingNames = {"", "s", "d", "t", "q", "p", "h"};
        clearCouplingChoices();
        if (coup instanceof ComplexCoupling) {
            patternChoices[0].setValue("m");
        } else if (coup instanceof CouplingPattern) {
            CouplingPattern couplingPattern = (CouplingPattern) coup;
            double[] values = couplingPattern.getValues();
            double[] slopes = couplingPattern.getSin2Thetas();
            int[] nCoup = couplingPattern.getNValues();
            for (int i = 0; i < values.length; i++) {
                couplingFields[i].setText(String.format("%.2f", values[i]));
                slopeFields[i].setText(String.format("%.2f", slopes[i]));
                patternChoices[i].setValue(couplingNames[nCoup[i]]);
            }
        } else if (coup instanceof Singlet) {
            patternChoices[0].setValue("s");
        }
    }

    void firstMultiplet(ActionEvent e) {
        System.out.println("get first from chart " + chart + " " + this);
        List<Multiplet> multiplets = getMultiplets();
        if (!multiplets.isEmpty()) {
            activeMultiplet = Optional.of(multiplets.get(0));
        } else {
            activeMultiplet = Optional.empty();
        }
        updateMultipletField();
    }

    void previousMultiplet(ActionEvent e) {
        if (activeMultiplet.isPresent()) {
            int id = activeMultiplet.get().getIDNum();
            id--;
            if (id < 0) {
                id = 0;
            }
            List<Multiplet> multiplets = getMultiplets();
            activeMultiplet = Optional.of(multiplets.get(id));
        }
        updateMultipletField();

    }

    void nextMultiplet(ActionEvent e) {
        if (activeMultiplet.isPresent()) {
            List<Multiplet> multiplets = getMultiplets();
            int id = activeMultiplet.get().getIDNum();
            int last = multiplets.size() - 1;
            id++;
            if (id > last) {
                id = last;
            }
            activeMultiplet = Optional.of(multiplets.get(id));
        }
        updateMultipletField();
    }

    void lastMultiplet(ActionEvent e) {
        List<Multiplet> multiplets = getMultiplets();
        if (!multiplets.isEmpty()) {
            activeMultiplet = Optional.of(multiplets.get(multiplets.size() - 1));
        }
        updateMultipletField();
    }

    void gotoPeakId(TextField textField) {

    }

    public static MultipletController create() {
        FXMLLoader loader = new FXMLLoader(MinerController.class.getResource("/fxml/MultipletScene.fxml"));
        MultipletController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((BorderPane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<MultipletController>getController();
            controller.stage = stage;
            stage.setTitle("Multiplets");
            stage.setScene(scene);
            stage.show();
            stage.toFront();
            controller.chart = controller.getChart();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }
        return controller;
    }

    public Stage getStage() {
        return stage;
    }

    Optional<PeakList> getPeakList() {
        System.out.println("get peak from chart " + chart);
        Optional<PeakList> peakListOpt = Optional.empty();
        List<PeakListAttributes> attrs = chart.getPeakListAttributes();
        if (!attrs.isEmpty()) {
            peakListOpt = Optional.of(attrs.get(0).getPeakList());
        }
        return peakListOpt;

    }

    PolyChart getChart() {
        FXMLController controller = FXMLController.getActiveController();
        PolyChart activeChart = controller.getActiveChart();
        System.out.println("get chart " + this + " " + activeChart);
        return activeChart;
    }

    void refresh() {
        chart.refresh();

    }

    List<MultipletSelection> getMultipletSelection() {
        FXMLController controller = FXMLController.getActiveController();
        List<MultipletSelection> multiplets = chart.getSelectedMultiplets();
        return multiplets;
    }

    public void fitSelected() {
        Analyzer analyzer = MainApp.mainApp.getAnalyzer();
        activeMultiplet.ifPresent(m -> {
            analyzer.fitMultiplet(m);
        });
        refresh();
    }

    public void splitSelected() {
        activeMultiplet.ifPresent(m -> {
            Multiplets.addOuterCoupling(2, m);
            Multiplets.guessMultiplicityFromGeneric(m);
        });
        refresh();
    }

    public void guessGeneric() {
        activeMultiplet.ifPresent(m -> {
            Multiplets.guessMultiplicityFromGeneric(m);
        });
        refresh();
    }

    public void refreshPeakView(Multiplet multiplet) {
        if (multiplet != null) {
            double bounds = multiplet.getBoundsValue();
            double center = multiplet.measureCenter();
            System.out.println(bounds + " " + center);
            double widthScale = 1.5;
            if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);
                Double[] ppms = {center};
                Double[] widths = {bounds * widthScale};
                if (widthScale > 0.0) {
                    chart.moveTo(ppms, widths);
                } else {
                    chart.moveTo(ppms);
                }
            }
        }
    }
}
