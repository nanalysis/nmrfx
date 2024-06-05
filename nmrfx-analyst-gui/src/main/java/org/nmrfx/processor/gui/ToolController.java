package org.nmrfx.processor.gui;

import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.nmrfx.analyst.gui.plugin.PluginLoader;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.plugin.api.EntryPoint;

import java.text.DecimalFormat;

public class ToolController implements NmrControlRightSideContent {
    static final DecimalFormat FORMATTER = new DecimalFormat();

    static {
        FORMATTER.setMaximumFractionDigits(3);
    }

    @FXML
    ScrollPane toolScrollPane;
    @FXML
    Accordion toolAccordion;
    @FXML
    TitledPane annoPane;
    @FXML
    TitledPane peakPickPane;
    PolyChart chart;
    FXMLController fxmlController;
    AnnotationController annotationController;

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
        controller.peakPickController.setup(fxmlController,controller.peakPickPane);
        controller.setChart(fxmlController.getActiveChart());
        PluginLoader.getInstance().registerPluginsOnEntryPoint(EntryPoint.RIGHT_TOOLS, controller);

        return controller;
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

    public Accordion getAccordion() {
        return toolAccordion;
    }
    public AnnotationController getAnnotationController(){
        return annotationController;
    }

}
