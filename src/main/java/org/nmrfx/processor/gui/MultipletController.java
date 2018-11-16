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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.processor.datasets.peaks.Analyzer;
import org.nmrfx.processor.datasets.peaks.ComplexCoupling;
import org.nmrfx.processor.datasets.peaks.Coupling;
import org.nmrfx.processor.datasets.peaks.CouplingPattern;
import org.nmrfx.processor.datasets.peaks.Multiplet;
import org.nmrfx.processor.datasets.peaks.Multiplets;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.Singlet;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.MultipletSelection;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;

/**
 *
 * @author brucejohnson
 */
public class MultipletController implements Initializable, SetChangeListener<MultipletSelection> {

    Stage stage = null;
    HBox navigatorToolBar;
    TextField multipletIdField;
    @FXML
    HBox toolBar;
    @FXML
    HBox regionToolBar;
    @FXML
    HBox peakToolBar;
    @FXML
    HBox multipletToolBar;
    @FXML
    HBox fittingToolBar;
    @FXML
    GridPane gridPane;
    @FXML
    Button splitButton;
    @FXML
    Button splitRegionButton;
    ChoiceBox<String>[] patternChoices;
    TextField[] couplingFields;
    TextField[] slopeFields;
    private PolyChart chart;
    Optional<Multiplet> activeMultiplet = Optional.empty();
    boolean ignoreCouplingChanges = false;
    ChangeListener<String> patternListener;

    public MultipletController() {
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String[] patterns = {"d", "t", "q", "p", "h", "dd", "ddd", "dddd"};
        int nCouplings = 5;
        double width1 = 30;
        double width2 = 80;
        double width3 = 60;
        patternChoices = new ChoiceBox[nCouplings];
        couplingFields = new TextField[nCouplings];
        slopeFields = new TextField[nCouplings];
        Button doubletButton = new Button("To Doublets");
        doubletButton.setOnAction(e -> toDoublets());
        gridPane.add(doubletButton, 0, 0, 2, 1);
        for (int iRow = 0; iRow < nCouplings; iRow++) {
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
            gridPane.add(patternChoices[iRow], 0, iRow + 1);
            gridPane.add(couplingFields[iRow], 1, iRow + 1);
            gridPane.add(slopeFields[iRow], 2, iRow + 1);
        }
        initNavigator(toolBar);
        initTools();
        patternListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                couplingChanged();
            }
        };
        addPatternListener();

    }

    public void initNavigator(HBox toolBar) {
        this.navigatorToolBar = toolBar;
        multipletIdField = new TextField();
        multipletIdField.setMinWidth(35);
        multipletIdField.setMaxWidth(35);
        multipletIdField.setPrefWidth(35);

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

        toolBar.getChildren().addAll(buttons);
        toolBar.getChildren().add(multipletIdField);
        toolBar.getChildren().add(deleteButton);
        HBox spacer = new HBox();
        toolBar.getChildren().add(spacer);
        HBox.setHgrow(spacer, Priority.ALWAYS);

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

    ImageView getIcon(String name) {
        Image imageIcon = new Image("/images/" + name + ".png", true);
        ImageView imageView = new ImageView(imageIcon);
        return imageView;
    }

    void initTools() {
        Font font = new Font(7);
        List<Button> peakButtons = new ArrayList<>();
        List<Button> regionButtons = new ArrayList<>();
        List<Button> multipletButtons = new ArrayList<>();
        List<Button> fitButtons = new ArrayList<>();
        Button button;

        button = new Button("Add", getIcon("region_add"));
        button.setOnAction(e -> addRegion());
        regionButtons.add(button);

        button = new Button("Adjust", getIcon("region_adjust"));
        button.setOnAction(e -> adjustRegion());
        regionButtons.add(button);

        button = new Button("Split", getIcon("region_split"));
        button.setOnAction(e -> splitRegion());
        regionButtons.add(button);

        button = new Button("Delete", getIcon("region_delete"));
        button.setOnAction(e -> removeRegion());
        regionButtons.add(button);

        button = new Button("Add 1", getIcon("peak_add1"));
        button.setOnAction(e -> addPeak());
        peakButtons.add(button);

        button = new Button("Add 2", getIcon("peak_add2"));
        button.setOnAction(e -> addTwoPeaks());
        peakButtons.add(button);

        button = new Button("AutoAdd", getIcon("peak_auto"));
        button.setOnAction(e -> addAuto());
        peakButtons.add(button);

        button = new Button("Delete", getIcon("editdelete"));
        button.setOnAction(e -> removeWeakPeak());
        peakButtons.add(button);

        button = new Button("Extract", getIcon("extract"));
        button.setOnAction(e -> extractMultiplet());
        multipletButtons.add(button);

        button = new Button("Merge", getIcon("merge"));
        button.setOnAction(e -> mergePeaks());
        multipletButtons.add(button);

        button = new Button("Transfer", getIcon("transfer"));
        button.setOnAction(e -> transferPeaks());
        multipletButtons.add(button);

        button = new Button("Fit", getIcon("reload"));
        button.setOnAction(e -> fitSelected());
        fitButtons.add(button);

        for (Button button1 : regionButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            regionToolBar.getChildren().add(button1);
        }
        for (Button button1 : peakButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            peakToolBar.getChildren().add(button1);
        }
        for (Button button1 : multipletButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            multipletToolBar.getChildren().add(button1);
        }
        for (Button button1 : fitButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            fittingToolBar.getChildren().add(button1);
        }

        /*
extract.png				region_add.png		wizard
merge.png				region_adjust.png
		region_delete.png


         */
    }

    void deleteMultiplet() {

    }

    public void initMultiplet() {
        if (!gotoSelectedMultiplet()) {
            List<Multiplet> multiplets = getMultiplets();
            if (!multiplets.isEmpty()) {
                Multiplet m = multiplets.get(0);
                activeMultiplet = Optional.of(m);
                updateMultipletField(false);
            }
        }
    }

    public boolean gotoSelectedMultiplet() {
        boolean result = false;
        List<MultipletSelection> multiplets = chart.getSelectedMultiplets();
        if (!multiplets.isEmpty()) {
            Multiplet m = multiplets.get(0).getMultiplet();
            activeMultiplet = Optional.of(m);
            updateMultipletField(false);
            result = true;
        }
        return result;
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
        updateMultipletField(true);
    }

    void updateMultipletField(boolean resetView) {
        if (activeMultiplet.isPresent()) {
            Multiplet multiplet = activeMultiplet.get();
            multipletIdField.setText(String.valueOf(multiplet.getIDNum()));
            if (resetView) {
                refreshPeakView(multiplet);
            }
            String mult = multiplet.getMultiplicity();
            System.out.println(multiplet.getIDNum() + " " + multiplet.getCenter() + " " + mult);
            Coupling coup = multiplet.getCoupling();
            updateCouplingChoices(coup);
//            if (multiplet.isGenericMultiplet()) {
//                splitButton.setDisable(true);
//            } else {
//                splitButton.setDisable(false);
//            }
        } else {
            multipletIdField.setText("");
        }
    }

    void clearPatternListener() {
        for (ChoiceBox<String> cBox : patternChoices) {
            cBox.valueProperty().removeListener(patternListener);
        }
    }

    void addPatternListener() {
        for (ChoiceBox<String> cBox : patternChoices) {
            cBox.valueProperty().addListener(patternListener);
        }
    }

    void clearCouplingChoices() {
        for (int i = 0; i < patternChoices.length; i++) {
            patternChoices[i].setValue("");
            couplingFields[i].setText("");
            slopeFields[i].setText("");
        }
        ignoreCouplingChanges = false;
    }

    void updateCouplingChoices(Coupling coup) {
        clearPatternListener();
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
        addPatternListener();
    }

    void firstMultiplet(ActionEvent e) {
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
            updateMultipletField();
        } else {
            firstMultiplet(e);
        }

    }

    void gotoPrevious(int id) {
        id--;
        if (id < 0) {
            id = 0;
        }
        List<Multiplet> multiplets = getMultiplets();
        activeMultiplet = Optional.of(multiplets.get(id));
        updateMultipletField(false);

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
            updateMultipletField();
        } else {
            firstMultiplet(e);
        }
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
            stage.setMinWidth(200);
            stage.setMinHeight(400);
            stage.show();
            stage.toFront();
            controller.chart = controller.getChart();
            controller.chart.addMultipletListener(controller);
            controller.initMultiplet();

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
        return activeChart;
    }

    void refresh() {
        chart.refresh();
        updateMultipletField(false);

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
            rms();
        });
        refresh();
    }

    public void splitSelected() {
        activeMultiplet.ifPresent(m -> {
            if (m.isGenericMultiplet()) {
            } else {
                Multiplets.splitToMultiplicity(m, "d");
                Multiplets.updateAfterMultipletConversion(m);
            }
        });
        refresh();
    }

    public void splitRegion() {
        double ppm = chart.getVerticalCrosshairPositions()[0];
        Analyzer analyzer = MainApp.mainApp.getAnalyzer();
        try {
            activeMultiplet = analyzer.splitRegion(ppm);
            updateMultipletField(false);
        } catch (IOException ex) {
        }
        chart.refresh();
    }

    public void adjustRegion() {
        Analyzer analyzer = MainApp.mainApp.getAnalyzer();
        double ppm0 = chart.getVerticalCrosshairPositions()[0];
        double ppm1 = chart.getVerticalCrosshairPositions()[1];
        analyzer.removeRegion((ppm0 + ppm1) / 2);
        analyzer.addRegion(ppm0, ppm1);
        try {
            activeMultiplet = analyzer.analyzeRegion((ppm0 + ppm1) / 2);
            updateMultipletField(false);
            chart.refresh();
        } catch (IOException ex) {
        }
    }

    public void addRegion() {
        Analyzer analyzer = MainApp.mainApp.getAnalyzer();
        double ppm0 = chart.getVerticalCrosshairPositions()[0];
        double ppm1 = chart.getVerticalCrosshairPositions()[1];
        analyzer.addRegion(ppm0, ppm1);
        try {
            activeMultiplet = analyzer.analyzeRegion((ppm0 + ppm1) / 2);
            System.out.println("update " + activeMultiplet.get().getIDNum());
            updateMultipletField(false);
            chart.refresh();

        } catch (IOException ex) {
        }
    }

    public void removeRegion() {
        activeMultiplet.ifPresent(m -> {
            int id = m.getIDNum();
            double ppm = m.measureCenter();
            Analyzer analyzer = MainApp.mainApp.getAnalyzer();
            analyzer.removeRegion(ppm);
            gotoPrevious(id);
            chart.refresh();
        });
    }

    public void rms() {
        activeMultiplet.ifPresent(m -> {
            Optional<Double> result = Multiplets.rms(m);
            if (result.isPresent()) {
                System.out.println("rms " + result.get());
            }
        });
    }

    public void addAuto() {
        activeMultiplet.ifPresent(m -> {
            Optional<Double> result = Multiplets.deviation(m);
            if (result.isPresent()) {
                System.out.println("dev pos " + result.get());
                Multiplets.addPeaksToMutliplet(m, result.get());
                chart.refresh();
                refresh();

            }

        });

    }

    public void addPeak() {
        addPeaks(false);
    }

    public void addTwoPeaks() {
        addPeaks(true);
    }

    public void addPeaks(boolean both) {
        activeMultiplet.ifPresent(m -> {
            double ppm1 = chart.getVerticalCrosshairPositions()[0];
            double ppm2 = chart.getVerticalCrosshairPositions()[1];
            if (both) {
                Multiplets.addPeaksToMutliplet(m, ppm1, ppm2);
            } else {
                Multiplets.addPeaksToMutliplet(m, ppm1);

            }
            chart.refresh();
            refresh();
        });
    }

    void removeWeakPeak() {
        activeMultiplet.ifPresent(m -> {
            Multiplets.removeWeakPeaksInMultiplet(m, 1);
            refresh();
        });
    }

    public void toDoublets() {
        activeMultiplet.ifPresent(m -> {
            Multiplets.toDoublets(m);
        });
        refresh();
    }

    public void guessGeneric() {
        activeMultiplet.ifPresent(m -> {
            Multiplets.guessMultiplicityFromGeneric(m);
        });
        refresh();
    }

    public void extractMultiplet() {
        List<Peak> peaks = chart.getSelectedPeaks();
        if (peaks.size() > 0) {
            Peak peak0 = peaks.get(0);
            List<PeakDim> peakDims = peak0.getPeakDim(0).getCoupledPeakDims();
            if (peaks.size() == peakDims.size()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Can't extract all peaks in multiplet");
                alert.showAndWait();
                return;
            }
            activeMultiplet = Multiplets.extractMultiplet(peaks);
            refresh();
        }
    }

    public void transferPeaks() {
        List<Peak> peaks = chart.getSelectedPeaks();
        if (peaks.size() > 0) {
            activeMultiplet.ifPresent(m -> {
                activeMultiplet = Multiplets.transferPeaks(m, peaks);
            });
            refresh();
        }
    }

    public void mergePeaks() {
        List<Peak> peaks = chart.getSelectedPeaks();
        if (peaks.size() > 0) {
            activeMultiplet = Multiplets.mergePeaks(peaks);
            refresh();
        }
    }

    public void refreshPeakView(Multiplet multiplet) {
        if (multiplet != null) {
            double bounds = multiplet.getBoundsValue();
            double center = multiplet.measureCenter();
            double widthScale = 2.5;
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

    private void couplingChanged() {
        activeMultiplet.ifPresent(m -> {
            StringBuilder sBuilder = new StringBuilder();
            for (ChoiceBox<String> choice : patternChoices) {
                String value = choice.getValue().trim();
                if (value.length() > 0) {
                    sBuilder.append(value);
                }
            }
            String multNew = sBuilder.toString();

            String multOrig = m.getMultiplicity();
            System.out.println("convert " + multOrig + " " + multNew);
            if (!multNew.equals(multOrig)) {
                Analyzer analyzer = MainApp.mainApp.getAnalyzer();
                Multiplets.convertMultiplicity(m, multOrig, multNew);
                analyzer.fitMultiplet(m);
                refresh();
            }
        });
    }

    @Override
    public void onChanged(Change<? extends MultipletSelection> change) {
        ObservableSet<MultipletSelection> mSet = (ObservableSet<MultipletSelection>) change.getSet();
        boolean allreadyPresent = false;
        if (!mSet.isEmpty()) {
            if (activeMultiplet.isPresent()) {
                for (MultipletSelection mSel : mSet) {
                    if (mSel.getMultiplet() == activeMultiplet.get()) {
                        // current active multiplet in selection so don't change anything
                        allreadyPresent = true;
                        break;
                    }
                }

            }
            if (!allreadyPresent) {
                for (MultipletSelection mSel : mSet) {
                    activeMultiplet = Optional.of(mSel.getMultiplet());
                    updateMultipletField(false);
                    break;
                }
            }
        }
    }
}
