package org.nmrfx.analyst.gui.tools;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.structure.seqassign.*;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphMatcherGUI {
    private static final Logger log = LoggerFactory.getLogger(GraphMatcherGUI.class);
    Stage stage;
    RunAboutGUI runAboutGUI;
    ResSeqMatcher resSeqMatcher;
    RunAbout runAbout;
    Task<Double> task;
    TextField progressField = new TextField();
    Button assignButton;
    Button stopButton;
    SimpleIntegerProperty popSizeProp = new SimpleIntegerProperty(500);
    SimpleIntegerProperty nGenProp = new SimpleIntegerProperty(2000);
    SimpleIntegerProperty eliteNumberProp = new SimpleIntegerProperty(100);
    SimpleIntegerProperty maxPhenoTypeAgeProp = new SimpleIntegerProperty(50);
    SimpleIntegerProperty steadyLimitProp = new SimpleIntegerProperty(200);
    SimpleDoubleProperty mutationRateProp = new SimpleDoubleProperty(0.1);
    SimpleBooleanProperty mutationProfileProp = new SimpleBooleanProperty(true);
    SimpleDoubleProperty crossOverRateProp = new SimpleDoubleProperty(0.1);
    SimpleDoubleProperty sdevRatioProp = new SimpleDoubleProperty(1.5);
    SimpleIntegerProperty nTriesProp = new SimpleIntegerProperty(25);



    double best = 0.0;

    public GraphMatcherGUI(RunAboutGUI runAboutGUI, RunAbout runAbout) {
        this.runAboutGUI = runAboutGUI;
        this.resSeqMatcher = new ResSeqMatcher();
        this.runAbout = runAbout;
        this.resSeqMatcher.setUpdater((d) -> updateProgress(d));
        build();
    }

    public void build() {
        stage = new Stage();
        BorderPane borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        ToolBar toolBar = new ToolBar();
        Button matchButton = new Button("Match");
        matchButton.setOnAction(e -> bipartiteAnalyze());
        stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stop());
        assignButton = new Button("Assign");
        assignButton.setOnAction(e -> assignFragments());
        assignButton.setDisable(true);
        stopButton.setDisable(true);

        Button detailButton = new Button("Details");
        detailButton.setOnAction(e -> dumpResSeqMatcher());

        Button resetButton = new Button("Reset");
        resetButton.setOnAction(e -> resetMatcher());

        toolBar.getItems().addAll(matchButton, stopButton, detailButton, resetButton, assignButton);
        progressField.setPrefWidth(200);
        borderPane.setTop(toolBar);
        borderPane.setBottom(progressField);
        borderPane.setCenter(makeParPane());

        stage.setTitle("Graph Matcher");
        stage.setAlwaysOnTop(true);
        stage.show();
    }

    Pane makeParPane() {
        GridPane gridPane = new GridPane();
        int row = 0;
        gridPane.add(new Label("N Tries"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(nTriesProp), 1, row++);
        gridPane.add(new Label("Population Size"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(popSizeProp), 1, row++);
        gridPane.add(new Label("N Generations"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(nGenProp), 1, row++);
        gridPane.add(new Label("Max Age"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(maxPhenoTypeAgeProp), 1, row++);
        gridPane.add(new Label("Elite Number"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(eliteNumberProp), 1, row++);
        gridPane.add(new Label("N Steady"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(steadyLimitProp), 1, row++);
        gridPane.add(new Label("Mutation Rate"),0, row);
        gridPane.add(GUIUtils.getDoubleTextField(mutationRateProp), 1, row++);
        gridPane.add(new Label("Use Mutation Profile"),0, row);
        CheckBox checkBox = new CheckBox();
        checkBox.selectedProperty().bindBidirectional(mutationProfileProp);
        gridPane.add(checkBox, 1, row++);
        gridPane.add(new Label("Crossover Rate"),0, row);
        gridPane.add(GUIUtils.getDoubleTextField(crossOverRateProp), 1, row++);
        gridPane.add(new Label("SDev Ratio"),0, row);
        gridPane.add(GUIUtils.getDoubleTextField(sdevRatioProp), 1, row++);
        return gridPane;
    }
    void stop() {
        resSeqMatcher.stopWork();
    }


    void resetMatcher() {
        resSeqMatcher = new ResSeqMatcher();
        resSeqMatcher.setUpdater((d) -> updateProgress(d));
    }
    void dumpResSeqMatcher() {
        SpinSystem currentSpinSystem = runAboutGUI.getCurrentSpinSystem();
        if (currentSpinSystem != null) {
            if ((runAbout != null) && (resSeqMatcher == null)) {
                resSeqMatcher = new ResSeqMatcher();
                resSeqMatcher.compareMatrix(runAbout.getSpinSystems(), 1.5);
            }
            resSeqMatcher.dumpScores(currentSpinSystem.getId());
            currentSpinSystem.printScores(1.5);
        }
    }

    void assignFragments() {
        if (resSeqMatcher != null) {
            resSeqMatcher.assignMatches(runAbout.getSpinSystems());
            runAboutGUI.gotoSpinSystems();
            runAboutGUI.updateClusterCanvas();
        }
    }

    synchronized void updateProgress(SeqGeneticAlgorithm.Progress d) {
        best = Math.min(d.best(), best);
        Fx.runOnFxThread(() -> {
            progressField.setText(String.format("n %5d current %8.1f best %8.1f", d.generation(), d.best(), best));
        });

    }
    void bipartiteAnalyze() {
        best = 0.0;
        assignButton.setDisable(true);
        stopButton.setDisable(false);
        progressField.setText("Initializing");
        resSeqMatcher.compareMatrix(runAbout.getSpinSystems(), sdevRatioProp.get());

            SeqGenParameters seqGenParameters = new SeqGenParameters(popSizeProp.get(), nGenProp.get(),
                    mutationRateProp.get(),mutationProfileProp.get(), crossOverRateProp.get(),eliteNumberProp.get(),maxPhenoTypeAgeProp.get(),
                    steadyLimitProp.get());

        task = new Task<>() {
            @Override
            protected Double call() throws Exception {
                try {
                    return resSeqMatcher.graphMatch(nTriesProp.get(), seqGenParameters);
                } catch (Exception e) {
                    log.error("Error in graph match", e);
                    e.printStackTrace();
                    throw (e);
                }
            }
        };
        task.setOnFailed(event -> {
            System.out.println("failed");
        });

        task.setOnSucceeded(event -> {
            double result = task.getValue();
            System.out.println("result " + result);
            Fx.runOnFxThread(() -> {
                assignButton.setDisable(false);
                stopButton.setDisable(true);
            });
        });

        new Thread(task).start();
    }
}
