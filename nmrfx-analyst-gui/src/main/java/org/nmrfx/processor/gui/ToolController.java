package org.nmrfx.processor.gui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Accordion;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.nmrfx.fxutil.Fxml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;

public class ToolController implements Initializable, NmrControlRightSideContent {
    static final DecimalFormat FORMATTER = new DecimalFormat();
    private static final Logger log = LoggerFactory.getLogger(ToolController.class);

    static {
        FORMATTER.setMaximumFractionDigits(3);
    }

    @FXML
    VBox applyVBox;
    @FXML
    ScrollPane toolScrollPane;
    @FXML
    Accordion attributesAccordion;
    @FXML
    TitledPane annoPane;
    @FXML
    TitledPane peakPickPane;
    @FXML
    TitledPane libraryPane;
    PolyChart chart;
    PolyChart boundChart = null;
    FXMLController fxmlController;
    AnnotationController annotationController;
    ChemicalLibraryController libraryController;

    PeakPickController peakPickController;
    @FXML
    private VBox attributesVBox;

    public static ToolController create(FXMLController fxmlController) {
        Fxml.Builder builder = Fxml.load(ToolController.class, "ToolController.fxml");
        ToolController controller = builder.getController();
        controller.fxmlController = fxmlController;
        controller.annotationController = new AnnotationController();
        controller.annotationController.setup(fxmlController, controller.annoPane);
        controller.peakPickController = new PeakPickController();
        controller.libraryController = new ChemicalLibraryController();
        controller.libraryController.setup(fxmlController, controller.libraryPane);
        controller.peakPickController.setup(fxmlController,controller.peakPickPane);
        controller.setChart(fxmlController.getActiveChart());
        return controller;
    }
    public void initialize(URL url, ResourceBundle rb) {

    }
    public PolyChart getChart() {
        return chart;
    }

    private void setChart(PolyChart activeChart) {
        this.chart = activeChart;
    }

    public Pane getPane() {
        return attributesVBox;
    }

    public void update() {

    }
    public AnnotationController getAnnotationController(){
        return annotationController;
    }
    public PeakPickController getPeakPickController(){
        return peakPickController;
    }



}
