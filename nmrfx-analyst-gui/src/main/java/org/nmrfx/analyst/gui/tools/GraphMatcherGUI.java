package org.nmrfx.analyst.gui.tools;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
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
    Button assignButton;
    Button stopButton;
    SimpleIntegerProperty popSizeProp = new SimpleIntegerProperty(1000);
    SimpleIntegerProperty nGenProp = new SimpleIntegerProperty(2000);
    SimpleIntegerProperty eliteNumberProp = new SimpleIntegerProperty(100);
    SimpleIntegerProperty maxPhenoTypeAgeProp = new SimpleIntegerProperty(50);
    SimpleIntegerProperty steadyLimitProp = new SimpleIntegerProperty(200);
    SimpleDoubleProperty mutationRateProp = new SimpleDoubleProperty(0.7);
    SimpleBooleanProperty mutationProfileProp = new SimpleBooleanProperty(true);
    SimpleDoubleProperty crossOverRateProp = new SimpleDoubleProperty(0.7);
    SimpleDoubleProperty sdevRatioProp = new SimpleDoubleProperty(1.5);
    SimpleIntegerProperty nTriesProp = new SimpleIntegerProperty(25);

    Label statusLabel = new Label();
    Label tryLabel = new Label();
    Label genLabel = new Label();
    Label currentLabel = new Label();
    Label bestLabel = new Label();


    double best = 0.0;

    public GraphMatcherGUI(RunAboutGUI runAboutGUI, RunAbout runAbout) {
        this.runAboutGUI = runAboutGUI;
        this.resSeqMatcher = new ResSeqMatcher();
        this.runAbout = runAbout;
        this.resSeqMatcher.setUpdater((d,i) -> updateProgress(d, i));
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

        int fieldWidth  = 100;
        statusLabel.setPrefWidth(320);
        tryLabel.setPrefWidth(fieldWidth);
        genLabel.setPrefWidth(fieldWidth);
        bestLabel.setPrefWidth(fieldWidth);
        currentLabel.setPrefWidth(fieldWidth);
        borderPane.setTop(toolBar);
        GridPane gridPane = new GridPane(20, 10);
        Insets insets = new Insets(5,20,5,20);
        gridPane.setPadding(insets);

        gridPane.add(new Label("Status"), 0, 0);
        gridPane.add(statusLabel, 1, 0, 3, 1);
        gridPane.add(new Label("Try"),0, 1);
        gridPane.add(tryLabel, 1, 1);
        gridPane.add(new Label("Gen"), 2, 1);
        gridPane.add(genLabel, 3, 1);
        gridPane.add(new Label("Current"),0, 2);
        gridPane.add(currentLabel, 1, 2);
        gridPane.add(new Label("Best"), 2, 2);
        gridPane.add(bestLabel, 3, 2);

        borderPane.setBottom(gridPane);
        borderPane.setCenter(makeParPane());

        stage.setTitle("Graph Matcher");
        stage.setAlwaysOnTop(true);
        stage.show();
    }

    Pane makeParPane() {
        GridPane gridPane = new GridPane();
        Insets insets = new Insets(5,20,5,20);
        gridPane.setPadding(insets);

        int row = 0;
        gridPane.add(new Label("N Tries"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(nTriesProp), 1, row++);

        gridPane.add(new Label("Population Size"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(popSizeProp), 1, row++);

        gridPane.add(new Label("N Generations"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(nGenProp), 1, row++);

        gridPane.add(new Label("Mutation Rate"),0, row);
        gridPane.add(GUIUtils.getDoubleTextField(mutationRateProp), 1, row++);

        gridPane.add(new Label("Use Mutation Profile"),0, row);
        CheckBox checkBox = new CheckBox();
        checkBox.selectedProperty().bindBidirectional(mutationProfileProp);
        gridPane.add(checkBox, 1, row++);

        gridPane.add(new Label("Crossover Rate"),0, row);
        gridPane.add(GUIUtils.getDoubleTextField(crossOverRateProp), 1, row++);

        gridPane.add(new Label("N Steady"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(steadyLimitProp), 1, row++);

        gridPane.add(new Label("SDev Ratio"),0, row);
        gridPane.add(GUIUtils.getDoubleTextField(sdevRatioProp), 1, row++);

        gridPane.add(new Label("Max Age"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(maxPhenoTypeAgeProp), 1, row++);

        gridPane.add(new Label("Elite Number"),0, row);
        gridPane.add(GUIUtils.getIntegerTextField(eliteNumberProp), 1, row++);

        return gridPane;
    }
    void stop() {
        resSeqMatcher.stopWork();
    }


    void resetMatcher() {
        resSeqMatcher = new ResSeqMatcher();
        resSeqMatcher.setUpdater((d,i) -> updateProgress(d, i));
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

    synchronized void updateProgress(SeqGeneticAlgorithm.Progress d, int iTry) {
        best = Math.min(d.best(), best);
        Fx.runOnFxThread(() -> {
            statusLabel.setText("Evolving");
            tryLabel.setText(String.valueOf(iTry + 1));
            genLabel.setText(String.valueOf(d.generation()));
            currentLabel.setText(String.format("%8.1f", d.best()));
            bestLabel.setText(String.format("%8.1f", best));
        });

    }
    void bipartiteAnalyze() {
        best = 0.0;
        assignButton.setDisable(true);
        stopButton.setDisable(false);
        statusLabel.setText("Initializing");
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
            Fx.runOnFxThread(() -> {
                assignButton.setDisable(false);
                stopButton.setDisable(true);
                statusLabel.setText("Matched");
            });
        });

        new Thread(task).start();
    }
}
