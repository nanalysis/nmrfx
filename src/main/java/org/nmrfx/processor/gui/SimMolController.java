package org.nmrfx.processor.gui;

/*
 * @author Bruce Johnson
 */
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.nmrfx.processor.compoundLib.CompoundMatcher;
import org.nmrfx.processor.compoundLib.CompoundData;
import org.nmrfx.processor.dataops.SimData;
import org.nmrfx.processor.dataops.SimDataVecPars;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.utils.ColorSchemes;
import org.nmrfx.processor.math.Vec;

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

    public ToolBar getToolBar() {
        return browserToolBar;
    }

    public void close() {
        closeAction.accept(this);
    }

    void initialize(ToolBar toolBar) {
        this.browserToolBar = toolBar;
        toolBar.setPrefWidth(900.0);

        String iconSize = "16px";
        String fontSize = "7pt";
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", iconSize, fontSize, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());

        toolBar.getItems().add(closeButton);
        addFiller(toolBar);

        atomFieldLabel = new Label("Molecule:");
        molNameField = new TextField();
        molNameField.setPrefWidth(300);
        molNameField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                // setMol();
            }
        });
        toolBar.getItems().add(atomFieldLabel);
        toolBar.getItems().add(molNameField);
        addFiller(toolBar);
        Button addButton = new Button("Show");
        addButton.setOnAction(e -> setMol());
        toolBar.getItems().add(addButton);

        Button stdButton = new Button("Std");
        stdButton.setOnAction(e -> createCmpdData());
        toolBar.getItems().add(stdButton);

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
                    currData = pChart.getDataset();
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
            controller.getStatusBar().setMode(1);
            chart.setDataset(newDataset, true);

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

    public void createCmpdData() {
        String name = molNameField.getText().toLowerCase();
        String[] names = {"sum", "current"};
        Dataset[] datasets = new Dataset[names.length];
        System.out.println("Mol Name: " + name);
        if (SimData.contains(name)) {
            PolyChart chart = controller.getActiveChart();
            for (int iDataset = 0; iDataset < datasets.length; iDataset++) {
                datasets[iDataset] = Dataset.getDataset(names[iDataset]);
            }

            Dataset currData = null;
            for (PolyChart pChart : controller.getCharts()) {
                currData = pChart.getDataset();
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

            /*
                public static CompoundData genCompoundData(String cmpdID, String name, int n,
            double refConc, double cmpdConc,
            double sf, double sw, double centerPPM, double lb, double frac) {

             */
            double refConc = 1.0;
            double cmpdConc = 1.0;
            double frac = 1.0e-3;
            CompoundData cData = SimData.genCompoundData(name, name, pars, lb, refConc, cmpdConc, frac);
            CompoundData.put(cData, name);
            cmpdMatcher.addMatch(cData);
            for (int iDataset = 0; iDataset < datasets.length; iDataset++) {
                if (datasets[iDataset] == null) {
                    Vec vec = SimData.prepareVec(names[iDataset], pars);
                    vec.setFreqDomain(true);
                    vec.setRef(pars.getVref());
                    datasets[iDataset] = new Dataset(vec);
                    datasets[iDataset].setLabel(0, pars.getLabel());
                }
            }
            Vec sumVec = datasets[0].getVec();
            cmpdMatcher.updateVec(sumVec);
            Vec currentVec = datasets[1].getVec();
            currentVec.zeros();
            cData.addToVec(currentVec, 1.0);
            if (currData != null) {
                Vec expVec = currData.getVec();
            }

            controller.getStatusBar().setMode(1);
            for (Dataset dataset : datasets) {
                if (!chart.containsDataset(dataset)) {
                    chart.setDataset(dataset, true);
                }
            }
            updateColors(chart);
            molNameField.setText("");
            chart.refresh();
        }
    }

    void updateColors(PolyChart chart) {
        if (chart.getDatasetAttributes().size() > 1) {
            chart.chartProps.setTitles(true);
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
