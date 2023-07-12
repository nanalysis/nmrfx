package org.nmrfx.processor.gui.tools;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Bruce Johnson
 */
public class SpectrumComparator {

    private static final Logger log = LoggerFactory.getLogger(SpectrumComparator.class);
    VBox vBox;
    PolyChart chart;
    ToolBar toolBar1;
    ToolBar toolBar2;
    Consumer closeAction;
    TextField[] datasetFields;
    TextField[] sampleFields;
    private ColorPicker[] datasetColorPickers = new ColorPicker[2];

    public SpectrumComparator(FXMLController controller, Consumer closeAction) {
        this.closeAction = closeAction;
        this.chart = controller.getActiveChart();
    }

    public VBox getToolBar() {
        return vBox;
    }

    public void close() {
        closeAction.accept(this);
    }

    public void initPathTool(VBox vBox) {
        toolBar1 = new ToolBar();
        toolBar2 = new ToolBar();
        this.vBox = vBox;
        vBox.getChildren().addAll(toolBar1, toolBar2);

        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.LEFT);
        closeButton.setOnAction(e -> close());

        ArrayList<Button> dataset1Buttons = new ArrayList<>();
        ArrayList<Button> dataset2Buttons = new ArrayList<>();

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstDataset(0));
        dataset1Buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousDataset(0));
        dataset1Buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextDataset(0));
        dataset1Buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastDataset(0));
        dataset1Buttons.add(bButton);

        datasetFields = new TextField[2];
        sampleFields = new TextField[2];
        for (int i = 0; i < datasetFields.length; i++) {
            datasetFields[i] = new TextField();
            datasetFields[i].setMinWidth(40);
            datasetFields[i].setMaxWidth(40);
            sampleFields[i] = new TextField();
            sampleFields[i].setMinWidth(100);
            sampleFields[i].setMaxWidth(200);
            Color color = i == 0 ? Color.BLACK : Color.BLUE;
            datasetColorPickers[i] = new ColorPicker(color);
            datasetColorPickers[i].getStyleClass().add("button");
            datasetColorPickers[i].valueProperty().addListener(c -> setDatasetState());
        }

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstDataset(1));
        dataset2Buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousDataset(1));
        dataset2Buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextDataset(1));
        dataset2Buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastDataset(1));
        dataset2Buttons.add(bButton);

        Pane filler1a = new Pane();
        HBox.setHgrow(filler1a, Priority.ALWAYS);

        toolBar1.getItems().add(closeButton);
        toolBar1.getItems().addAll(buttons);
        toolBar1.getItems().add(datasetColorPickers[0]);
        toolBar1.getItems().addAll(dataset1Buttons);
        toolBar1.getItems().addAll(datasetFields[0]);
        toolBar1.getItems().addAll(sampleFields[0]);

        Pane filler2 = new Pane();
        // filler2 is placeholder width for close button in toolbar2, bind to width so color pickers in
        // both toolbars align
        filler2.minWidthProperty().bind(closeButton.widthProperty());
        Pane filler1b = new Pane();
        HBox.setHgrow(filler1b, Priority.ALWAYS);

        toolBar2.getItems().add(filler2);
        toolBar2.getItems().add(datasetColorPickers[1]);
        toolBar2.getItems().addAll(dataset2Buttons);
        toolBar2.getItems().addAll(datasetFields[1]);
        toolBar2.getItems().addAll(sampleFields[1]);

        Pane filler5a = new Pane();
        HBox.setHgrow(filler5a, Priority.ALWAYS);

        Pane filler5b = new Pane();
        HBox.setHgrow(filler5b, Priority.ALWAYS);

        toolBar1.getItems().add(filler5a);
        toolBar2.getItems().add(filler5b);
        toolBar1.heightProperty().addListener((observable, oldValue, newValue) -> GUIUtils.toolbarAdjustHeights(Arrays.asList(toolBar1, toolBar2)));
    }

    void setDatasetState() {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        int[] actives = {getActiveDataset(0), getActiveDataset(1)};
        for (int i = 0; i < actives.length; i++) {
            if (actives[i] < 0) {
                actives[i] = 0;
            }
            if (actives[i] >= dataAttrs.size()) {
                actives[i] = dataAttrs.size() - 1;
            }
        }
        for (DatasetAttributes dataAttr : dataAttrs) {
            dataAttr.setPos(false);
        }
        int i = 0;

        for (DatasetAttributes dataAttr : dataAttrs) {
            for (int j = 0; j < actives.length; j++) {
                boolean state = (i == actives[j]);
                if (state) {
                    dataAttr.setPos(true);
                    Color color = datasetColorPickers[j].getValue();
                    dataAttr.setPosColor(color);
                }
            }
            i++;
        }

        chart.refresh();
    }

    int getActiveDataset(int iSet) {
        int active = 0;
        try {
            active = Integer.parseInt(datasetFields[iSet].getText().trim());
        } catch (NumberFormatException nfE) {
            log.warn("Unable to parse active dataset.", nfE);
        }
        return active;
    }

    void firstDataset(int iSet) {
        datasetFields[iSet].setText("0");
        setDatasetState();
    }

    void lastDataset(int iSet) {
        int i = chart.getDatasetAttributes().size() - 1;
        datasetFields[iSet].setText(String.valueOf(i));
        setDatasetState();
    }

    void previousDataset(int iSet) {
        int active = getActiveDataset(iSet);
        active = active > 0 ? active - 1 : 0;
        datasetFields[iSet].setText(String.valueOf(active));
        setDatasetState();
    }

    void nextDataset(int iSet) {
        int last = chart.getDatasetAttributes().size() - 1;
        int active = getActiveDataset(iSet);
        active = active < last ? active + 1 : last;
        datasetFields[iSet].setText(String.valueOf(active));
        setDatasetState();
    }

}
