package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.fxutil.Fxml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;

public class ToolController implements Initializable, NmrControlRightSideContent {
    private static final Logger log = LoggerFactory.getLogger(ToolController.class);
    static final DecimalFormat FORMATTER = new DecimalFormat();

    static {
        FORMATTER.setMaximumFractionDigits(3);
    }
    @FXML
    private VBox attributesVBox;


    @FXML
    VBox applyVBox;
    @FXML
    ScrollPane toolScrollPane;
    @FXML
    Accordion attributesAccordion;
    @FXML
    TitledPane annoPane;
    PolyChart chart;
    PolyChart boundChart = null;

    FXMLController fxmlController;

    public static ToolController create(FXMLController fxmlController) {
        Fxml.Builder builder = Fxml.load(ToolController.class, "ToolController.fxml");
        ToolController controller = builder.getController();
        controller.fxmlController = fxmlController;
//        controller.setChart(fxmlController.getActiveChart());
        return controller;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        VBox vBox = new VBox();
        annoPane.setContent(vBox);
        ToolBar toolBar = new ToolBar();
        vBox.getChildren().add(toolBar);
        Button arrowButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_LEFT, "Arrow", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        arrowButton.setOnAction(e -> createArrow());
        toolBar.getItems().add(arrowButton);
    }

    void createArrow() {

    }

    public Pane getPane() {
        return attributesVBox;
    }

    public void update() {

    }

}
