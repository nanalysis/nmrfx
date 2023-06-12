package org.nmrfx.analyst.gui.tools;

/*
 * @author Bruce Johnson
 */

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.nmrfx.analyst.compounds.CompoundMatcher;
import org.nmrfx.analyst.dataops.SimData;
import org.nmrfx.analyst.dataops.SimDataVecPars;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.AnalystPrefs;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.SpectrumStatusBar;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.utils.ColorSchemes;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class SimMolController implements ControllerTool {

    ToolBar browserToolBar;
    FXMLController controller;
    Consumer<SimMolController> closeAction;
    Label atomFieldLabel;
    TextField molNameField;
    int centerDim = 0;
    int rangeDim = 1;
    ChoiceBox<String> entityChoiceBox;
    ChoiceBox<String> rangeSelector;
    TextField minField;
    TextField maxField;
    Background defaultBackground = null;
    Background errorBackground = new Background(new BackgroundFill(Color.YELLOW, null, null));
    CompoundMatcher cmpdMatcher = new CompoundMatcher();

    public SimMolController(FXMLController controller, Consumer<SimMolController> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public SimMolController(FXMLController controller) {
        this.controller = controller;
    }

    public SimMolController() {
    }

    public ToolBar getToolBar() {
        return browserToolBar;
    }

    public void close() {
        closeAction.accept(this);
    }

    public void initialize(ToolBar toolBar) {
        this.browserToolBar = toolBar;
        toolBar.setPrefWidth(900.0);

        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.LEFT);
        closeButton.setOnAction(e -> close());

        toolBar.getItems().add(closeButton);
        addFiller(toolBar);

        atomFieldLabel = new Label("Molecule:");
        molNameField = new TextField();
        molNameField.setPrefWidth(300);
        molNameField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                setMol();
            }
        });
        toolBar.getItems().add(atomFieldLabel);
        toolBar.getItems().add(molNameField);
        addFiller(toolBar);

        Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>> suggestionProvider = new Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>>() {
            @Override
            public Collection<String> call(AutoCompletionBinding.ISuggestionRequest param) {
                List<String> suggestions = getMatchingNames(param.getUserText());
                return suggestions;
            }
        };
        TextFields.bindAutoCompletion(molNameField, suggestionProvider);

    }

    public void addFiller(ToolBar toolBar) {
        Pane filler = new Pane();
        HBox.setHgrow(filler, Priority.ALWAYS);
        filler.setMinWidth(25);
        filler.setMaxWidth(50);
        toolBar.getItems().add(filler);
    }

    public void update() {
    }

    public void setMol() {
        String name = molNameField.getText().toLowerCase();
        System.out.println("Mol Name: " + name);
        if (SimData.contains(name)) {
            Dataset newDataset = Dataset.getDataset(name);
            PolyChart chart = controller.getActiveChart();
            if (newDataset == null) {
                Dataset currData = null;
                for (PolyChart pChart : controller.getCharts()) {
                    currData = (Dataset) pChart.getDataset();
                    if (currData != null) {
                        break;
                    }
                }
                SimDataVecPars pars;
                if (currData != null) {
                    pars = new SimDataVecPars(currData);
                } else {
                    pars = defaultPars();
                }
                double lb = AnalystPrefs.getLibraryVectorLB();
                newDataset = SimData.genDataset(name, pars, lb);
                newDataset.addProperty("SIM", name);
            }
            controller.getStatusBar().setMode(SpectrumStatusBar.DataMode.DATASET_1D);
            chart.setDataset(newDataset, true, false);

            updateColors(chart);
            molNameField.setText("");
            chart.refresh();
        }
    }

    SimDataVecPars defaultPars() {
        String label = "1H";
        double sf = AnalystPrefs.getLibraryVectorSF();
        double sw = AnalystPrefs.getLibraryVectorSW();
        int size = (int) Math.pow(2, AnalystPrefs.getLibraryVectorSize());
        double ref = AnalystPrefs.getLibraryVectorREF();
        SimDataVecPars pars = new SimDataVecPars(sf, sw, size, ref, label);
        return pars;

    }

    void updateColors(PolyChart chart) {
        if (chart.getDatasetAttributes().size() > 1) {
            chart.getChartProperties().setTitles(true);
            int nData = chart.getDatasetAttributes().size() - 1;
            String colScheme = (nData <= 10) ? "category10" : "category20";
            List<Color> colors = ColorSchemes.getColors(colScheme, nData);
            int i = 0;
            for (DatasetAttributes dataAttr : chart.getActiveDatasetAttributes()) {
                if (i > 0) {
                    dataAttr.setPosColor(colors.get(i - 1));
                }
                i++;
            }
        }

    }

    List<String> getMatchingNames(String pattern) {
        if (!SimData.loaded()) {
            SimData.load();
        }
        List<String> names = SimData.getNames(pattern);
        return names;

    }
}
