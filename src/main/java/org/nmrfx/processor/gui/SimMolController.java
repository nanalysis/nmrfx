package org.nmrfx.processor.gui;

/*
 * @author Bruce Johnson
 */
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.util.ArrayList;
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
import org.nmrfx.processor.dataops.SimData;

public class SimMolController implements ControllerTool {

    ToolBar browserToolBar;
    FXMLController controller;
    Consumer closeAction;
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

    public SimMolController(FXMLController controller, Consumer closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public SimMolController(FXMLController controller) {
        this.controller = controller;
    }

    public static SimMolController create() {
        System.out.println("create");
        FXMLController controller = FXMLController.getActiveController();

        ToolBar navBar = new ToolBar();
        controller.getBottomBox().getChildren().add(navBar);
        SimMolController simMolController = new SimMolController(controller);

        simMolController.initialize(navBar);
        simMolController.controller = controller;
        return simMolController;
    }

    public ToolBar getToolBar() {
        return browserToolBar;
    }

    public void close() {
        controller.getBottomBox().getChildren().remove(browserToolBar);
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
            String datasetName = SimData.genDataset(name, 32768, 600.0, 10000.0, 4.73);
            List<String> names = new ArrayList<>();
            names.add(datasetName);
            controller.getActiveChart().updateDatasets(names);
            controller.getActiveChart().refresh();
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
