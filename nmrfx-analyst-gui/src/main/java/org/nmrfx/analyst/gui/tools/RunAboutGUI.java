package org.nmrfx.analyst.gui.tools;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.io.AtomParser;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.peaks.events.PeakCountEvent;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakListAlign;
import org.nmrfx.processor.gui.*;
import org.nmrfx.processor.gui.annotations.AnnoSimpleLine;
import org.nmrfx.processor.gui.annotations.AnnoText;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.spectra.PeakMenu;
import org.nmrfx.processor.gui.utils.ColorSchemes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.seqassign.*;
import org.nmrfx.structure.seqassign.FragmentScoring.AAScore;
import org.nmrfx.structure.seqassign.RunAbout.TypeInfo;
import org.nmrfx.structure.seqassign.SpinSystem.AtomPresent;
import org.nmrfx.structure.seqassign.SpinSystem.PeakMatch;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import static org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE.PPM;


/**
 * @author Bruce Johnson
 */
public class RunAboutGUI implements PeakListener, ControllerTool {

    private static final Logger log = LoggerFactory.getLogger(RunAboutGUI.class);
    private static final Font REGULAR_FONT = Font.font(null, FontWeight.NORMAL, 14);

    private static final double HEIGHT_WIDTH = 0.8;
    private static final Background DELETE_BACKGROUND = new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY));

    private static Map<Nuclei, SimpleDoubleProperty> defaultToleranceMap = Map.of(
            Nuclei.H1, new SimpleDoubleProperty(RunAbout.defaultTolearnces.get(Nuclei.H1)),
            Nuclei.N15, new SimpleDoubleProperty(RunAbout.defaultTolearnces.get(Nuclei.N15)),
            Nuclei.C13, new SimpleDoubleProperty(RunAbout.defaultTolearnces.get(Nuclei.C13))
    );
    private static Map<Nuclei, SimpleDoubleProperty> defaultWidthMap = Map.of(
            Nuclei.H1, new SimpleDoubleProperty(0.5),
            Nuclei.N15, new SimpleDoubleProperty(5.0),
            Nuclei.C13, new SimpleDoubleProperty(5.0)
    );

    private Map<Nuclei, SimpleDoubleProperty> toleranceMap = new HashMap<>(defaultToleranceMap);
    private Map<Nuclei, SimpleDoubleProperty> widthMap = new HashMap<>(defaultWidthMap);
    FXMLController controller;
    FXMLController refController;
    VBox vBox;
    TabPane tabPane;
    ToolBar navigatorToolBar;
    TextField peakIdField;
    MenuButton peakListMenuButton;
    MenuButton arrangeMenuButton;
    ToggleButton deleteButton;
    ChoiceBox<SpinSystems.ClusterModes> clusterModesChoiceBox;
    PeakList navigationPeakList;
    SimpleObjectProperty<PeakList> refListObj = new SimpleObjectProperty<>();

    CheckBox unifyLimitsCheckBox;
    Peak currentPeak;
    Background defaultBackground = null;
    List<Peak> matchPeaks = new ArrayList<>();
    int matchIndex = 0;
    Consumer<RunAboutGUI> closeAction;
    boolean showAtoms = false;
    Label atomXFieldLabel;
    Label atomYFieldLabel;
    Label intensityFieldLabel;
    Label atomXLabel;
    Label atomYLabel;
    Label intensityLabel;
    RunAbout runAbout;
    String currentArrangement;
    SpinSystem currentSpinSystem = null;
    SpinStatus spinStatus;
    ClusterStatus clusterStatus;
    SeqPane seqPane;
    Group drawingGroup;
    ClusterPane clusterPane;
    Group clusterGroup;
    Map<String, ResidueLabel> residueLabelMap = new HashMap<>();
    Map<PolyChart, String> chartTypes = new HashMap<>();
    RunAboutArrangements runAboutArrangements;
    TableView<PeakListSelection> peakTableView = new TableView<>();
    boolean useSpinSystem = false;
    Double[][] widths;

    HashMap<String, double[]> plotLimits;

    HashMap<Integer, String> rowMap;
    int[] resOffsets = null;
    Map<PolyChart, List<String>> winPatterns = new HashMap<>();
    boolean[] intraResidue = null;
    int minOffset = 0;

    Molecule currentMolecule = null;
    int nResidues = 0;

    String currentSequence = "";

    double fontWidth = 20.0;

    double seqCharHeight = 20;
    static double clusterCharHeight = 20.0;


    List<Color> paletteColors = new ArrayList<>();
    Map<SpinSystem, Color> spinSystemColorMap = new HashMap<>();

    ResSeqMatcher resSeqMatcher = null;

    public RunAboutGUI(FXMLController controller, Consumer<RunAboutGUI> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
        paletteColors.addAll(ColorSchemes.getColors("set3", 12));
        Color gray = paletteColors.get(8);
        Color last = paletteColors.get(11);
        paletteColors.set(8, last);
        paletteColors.set(11, gray);
    }

    public void clear() {
        unregisterPeakLists();
        runAbout = null;
    }

    public RunAbout getRunAbout() {
        return runAbout;
    }

    public Map<Nuclei, SimpleDoubleProperty> getWidthMap() {
        return widthMap;
    }

    public void setWidthMap(Map<String, Double> map) {
        for (var entry : map.entrySet()) {
            widthMap.put(Nuclei.findNuclei(entry.getKey()), new SimpleDoubleProperty(entry.getValue()));
        }
    }

    public void setToleranceMap(Map<String, Double> map) {
        for (var entry : map.entrySet()) {
            toleranceMap.put(Nuclei.findNuclei(entry.getKey()), new SimpleDoubleProperty(entry.getValue()));
        }
    }

    public ToolBar getToolBar() {
        return navigatorToolBar;
    }

    public FXMLController getController() {
        return controller;
    }

    public void close() {
        closeAction.accept(this);
    }

    public TabPane getTabPane() {
        return tabPane;
    }

    public String getArrangement() {
        return currentArrangement;
    }

    int getNResidues() {
        if ((nResidues == 0) || (Molecule.getActive() != currentMolecule)) {
            currentMolecule = Molecule.getActive();
            nResidues = 0;
            if (currentMolecule != null) {
                StringBuilder stringBuilder = new StringBuilder();
                for (Polymer polymer : currentMolecule.getPolymers()) {
                    if (polymer.isPeptide()) {
                        for (Residue residue : polymer.getResidues()) {
                            stringBuilder.append(residue.getOneLetter());
                        }
                        int nRes = polymer.getResidues().size();
                        nResidues += nRes;
                    }
                }
                currentSequence = stringBuilder.toString();
                Font testFont20 = Font.font(null, FontWeight.NORMAL, 20);
                fontWidth = GUIUtils.getTextWidth("H", testFont20);
            }
        }
        return nResidues;
    }

    class SeqPane extends Pane {
        double gap = 2.0;

        double getResHeight() {
            Molecule mol = Molecule.getActive();
            double rectHeight = 20.0;
            if (mol != null) {
                int nRes = getNResidues();
                int nRectangles = nRes + (nRes / 10) * 3;
                double cWidth = getWidth() - 30.0;
                double cHeight = getHeight() - 10.0;
                for (int iRectHeight = 30; iRectHeight >= 10; iRectHeight--) {
                    int iRectWidth = (int) (iRectHeight * HEIGHT_WIDTH);
                    int rows = (int) Math.floor(cHeight / iRectHeight);
                    int cols = (int) Math.floor(cWidth / iRectWidth);
                    if (rows * cols > nRectangles) {
                        rectHeight = iRectHeight - gap;

                        break;
                    }
                }
            } else {
                log.info("No active molecule. Unable to get rows.");
            }
            return rectHeight;

        }

        @Override
        public void layoutChildren() {
            seqCharHeight = getResHeight();
            super.layoutChildren();
            updateSeqCanvas();
        }

    }

    public class PeakListSelection {
        PeakList peakList;
        private BooleanProperty active;

        public BooleanProperty activeProperty() {
            if (active == null) {
                active = new SimpleBooleanProperty(this, "+on", false);
            }
            return active;
        }

        public void setActive(boolean value) {
            log.info("set value {}", value);
            activeProperty().set(value);
        }

        public boolean getActive() {
            return activeProperty().get();
        }

        PeakListSelection(PeakList peakList) {
            this.peakList = peakList;
        }

        public String getName() {
            return peakList.getName();
        }

        public int getSize() {
            return peakList.peaks().size();
        }

        public String getType() {
            return peakList.getExperimentType();
        }

        public int getCount() {
            String typeName = peakList.getExperimentType();
            int count = 0;
            if (runAbout != null) {
                count = runAbout.getTypeCount(typeName);
            }
            return count;
        }

        public String getPattern() {
            String pattern = "";
            boolean first = true;
            for (var sDim : peakList.getSpectralDims()) {
                if (!first) {
                    pattern += " : ";
                } else {
                    first = false;
                }
                pattern += sDim.getPattern();
            }
            return pattern;
        }

        public String getTolerance() {
            String tol = "";
            boolean first = true;
            for (var sDim : peakList.getSpectralDims()) {
                if (!first) {
                    tol += " : ";
                } else {
                    first = false;
                }
                double tolValue = sDim.getIdTol();
                tol += String.format("%.2f", tolValue);
            }
            return tol;
        }


    }

    class ClusterPane extends Pane {

        double gap = 2;

        double getRectHeight() {
            int nSys = runAbout.getSpinSystems().getSize();

            int nRectangles = 10;
            if (nSys > 9) {
                nRectangles += (nSys - 9) * 2;
            }
            if (nSys > 99) {
                nRectangles += nSys - 99;
            }
            double rectHeight = 20.0;
            double cWidth = getWidth() - 30.0;
            double cHeight = getHeight() - 10.0;
            for (int iRectHeight = 30; iRectHeight >= 10; iRectHeight--) {
                int iRectWidth = (int) (iRectHeight * HEIGHT_WIDTH);
                int rows = (int) Math.floor(cHeight / iRectHeight);
                int cols = (int) Math.floor(cWidth / iRectWidth);
                if (rows * cols > nRectangles) {
                    rectHeight = iRectHeight - gap;
                    break;
                }
            }
            return rectHeight;

        }

        @Override
        public void layoutChildren() {
            clusterCharHeight = getRectHeight();
            super.layoutChildren();
            updateClusterCanvas();
        }

    }

    public void initialize(TabPane tabPane) {
        this.tabPane = tabPane;
        tabPane.setSide(Side.LEFT);
        runAbout = RunAbout.getRunAbout(1);
        if (runAbout != null) {
            registerPeakLists();
        } else {
            runAbout = new RunAbout();
        }
        Tab helmTab = new Tab("Helm");
        helmTab.setClosable(false);
        tabPane.getTabs().add(helmTab);
        VBox helmVBox = new VBox();
        helmTab.setContent(helmVBox);
        initHelm(helmVBox);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        tabPane.heightProperty().addListener(e -> updateTabSize());
        tabPane.widthProperty().addListener(e -> updateTabSize());


        Tab prefTab = new Tab("Configure");
        prefTab.setClosable(false);
        tabPane.getTabs().add(prefTab);
        BorderPane borderPane = new BorderPane();
        prefTab.setContent(borderPane);
        initPreferences(borderPane);

        try {
            runAboutArrangements = RunAboutYamlReader.loadYaml();
            setupArrangements();
        } catch (IOException e) {
            GUIUtils.warn("RunAbout:load yaml", e.getMessage());
        }
        PeakPicking.registerSinglePickSelectionAction(this::pickedPeakAction);
        PolyChart.registerPeakDeleteAction(this::peakDeleteAction);
        if (runAbout.getSpinSystems().getSize() != 0) {
            clusterStatus.refresh();
            useSpinSystem = true;
        }
    }

    void initPreferences(BorderPane borderPane) {
        GUIProject.getActive().getPeakLists();
        ObservableList<PeakList> peakLists = FXCollections.observableArrayList(new ArrayList<>(PeakList.peakLists()));
        ToolBar buttonBar = new ToolBar();

        peakTableView.setStyle("-fx-font-size: 14");
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.LEFT);
        closeButton.setOnAction(e -> close());
        if (closeAction != null) {
            buttonBar.getItems().add(closeButton);
        }
        Label refLabel = new Label("Ref List");
        ChoiceBox<PeakList> referenceChoice = new ChoiceBox<>();
        buttonBar.getItems().add(refLabel);
        buttonBar.getItems().add(referenceChoice);
        referenceChoice.getItems().addAll(peakLists);
        if (!peakLists.isEmpty()) {
            refListObj.set(peakLists.get(0));
        } else {
            log.warn("Peaks list is empty, unable to set peaks.");
        }
        referenceChoice.valueProperty().bindBidirectional(refListObj);


        peakTableView.setMinHeight(100);
        peakTableView.setPrefHeight(100);
        peakTableView.setEditable(true);
        VBox.setVgrow(peakTableView, Priority.ALWAYS);
        TableColumn<PeakListSelection, String> peakListNameColumn = new TableColumn<>("Name");
        peakListNameColumn.setCellValueFactory(new PropertyValueFactory<>("Name"));
        peakListNameColumn.setEditable(false);
        peakListNameColumn.setPrefWidth(150);

        TableColumn<PeakListSelection, String> peakTypeColumn = new TableColumn<>("Type");
        peakTypeColumn.setCellValueFactory(new PropertyValueFactory<>("Type"));
        peakTypeColumn.setEditable(false);
        peakTypeColumn.setPrefWidth(125);

        TableColumn<PeakListSelection, String> peakListPatternColumn = new TableColumn<>("Pattern");
        peakListPatternColumn.setCellValueFactory(new PropertyValueFactory<>("pattern"));
        peakListPatternColumn.setEditable(false);
        peakListPatternColumn.setPrefWidth(150);

        TableColumn<PeakListSelection, Integer> typeCountColumn = new TableColumn<>("Count");
        typeCountColumn.setCellValueFactory(new PropertyValueFactory<>("count"));
        typeCountColumn.setEditable(false);
        typeCountColumn.setPrefWidth(60);

        TableColumn<PeakListSelection, String> tolColumn = new TableColumn<>("Tolerance");
        tolColumn.setCellValueFactory(new PropertyValueFactory<>("tolerance"));
        tolColumn.setEditable(false);
        tolColumn.setPrefWidth(125);

        TableColumn<PeakListSelection, Integer> peakListSizeColumn = new TableColumn<>("Size");
        peakListSizeColumn.setCellValueFactory(new PropertyValueFactory<>("Size"));
        peakListSizeColumn.setEditable(false);

        TableColumn<PeakListSelection, Boolean> peakListSelectedColumn = new TableColumn<>("Active");
        peakListSelectedColumn.setCellValueFactory(new PropertyValueFactory<>("active"));
        peakListSelectedColumn.setEditable(true);
        peakListSelectedColumn.setCellFactory(param -> new CheckBoxTableCell<>());
        var peakListSelectors = peakLists.stream().map(PeakListSelection::new).toList();

        peakTableView.getColumns().addAll(peakListNameColumn, peakTypeColumn, peakListPatternColumn, typeCountColumn, tolColumn,
                peakListSizeColumn, peakListSelectedColumn);
        if (runAbout != null) {
            var runaboutLists = runAbout.getPeakLists();
            for (var selector : peakListSelectors) {
                if (runaboutLists.contains(selector.peakList)) {
                    selector.setActive(true);
                }
            }
        }
        peakTableView.getItems().addAll(peakListSelectors);

        GUIProject.getActive().addPeakListSubscription(this::updatePeakTableView);

        Button configureButton = new Button("Inspector");
        configureButton.setOnAction(e -> inspectPeakList());

        Button setupButton = new Button("Setup");
        setupButton.setOnAction(e -> setupRunAbout());
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> peakTableView.refresh());
        Button arrangementsButton = new Button("Arrangements...");
        arrangementsButton.setOnAction(e -> loadArrangements());

        MenuButton toleranceButton = new MenuButton("Tolerances");
        MenuItem autoTolItem = new MenuItem("Calculate");
        autoTolItem.setOnAction(e -> autoSetTolerances());
        MenuItem defaultTolItem = new MenuItem("Set To Defaults");
        defaultTolItem.setOnAction(e -> setDefaultTolerances());
        MenuItem tolItem = new MenuItem("Set To User Values");
        tolItem.setOnAction(e -> setTolerances());
        toleranceButton.getItems().addAll(autoTolItem, defaultTolItem, tolItem);

        Button addListButton = new Button("Add Lists");
        addListButton.setOnAction(e -> addLists());

        unifyLimitsCheckBox = new CheckBox("Unify Limits");
        unifyLimitsCheckBox.setSelected(false);

        buttonBar.getItems().addAll(configureButton, refreshButton, setupButton, arrangementsButton, toleranceButton, addListButton, unifyLimitsCheckBox);
        borderPane.setTop(buttonBar);
        HBox hBox = new HBox();
        borderPane.setCenter(hBox);
        GridPane gridPane = new GridPane();
        gridPane.setPrefWidth(300);
        gridPane.setPadding(new Insets(20, 20, 20, 20));
        gridPane.setVgap(5);
        gridPane.setHgap(10);
        gridPane.add(new Label("Nucleus"), 0, 0);
        gridPane.add(new Label("Tolerance"), 1, 0);
        gridPane.add(new Label("Width"), 2, 0);
        int row = 1;
        for (Nuclei nucleus : toleranceMap.keySet()) {
            gridPane.add(new Label(nucleus.getNumberName()), 0, row);
            SimpleDoubleProperty toleranceProp = toleranceMap.get(nucleus);
            TextField tolField = GUIUtils.getDoubleTextField(toleranceProp, 2);
            tolField.setPrefWidth(70);
            gridPane.add(tolField, 1, row);
            SimpleDoubleProperty widthProp = widthMap.get(nucleus);
            TextField widthField = GUIUtils.getDoubleTextField(widthProp, 2);
            widthField.setPrefWidth(70);
            gridPane.add(widthField, 2, row);
            row++;
        }

        hBox.getChildren().addAll(gridPane, peakTableView);
        HBox.setHgrow(peakTableView, Priority.ALWAYS);
        var model = peakTableView.getSelectionModel();
        configureButton.setDisable(true);
        model.selectedIndexProperty().addListener(e -> {
            log.info("selected {}", model.getSelectedIndices());
            configureButton.setDisable(model.getSelectedIndices().isEmpty());
        });
    }

    private void updatePeakTableView() {
        if (runAbout == null) {
            return;
        }
        var peakListSelectors = PeakList.peakLists().stream().map(PeakListSelection::new).toList();
        peakTableView.getItems().setAll(peakListSelectors);
        for (var peakListSelector : peakListSelectors) {
            if (runAbout.getPeakLists().contains(peakListSelector.peakList)) {
                peakListSelector.setActive(true);
            }
        }
        registerPeakLists();
    }

    private void inspectPeakList() {
        var items = peakTableView.getSelectionModel().getSelectedItems();
        if (!items.isEmpty()) {
            PeakListSelection item = items.get(0);
            PeakList peakList = item.peakList;
            FXMLController.showPeakAttr();
            FXMLController.getPeakAttrController().setPeakList(peakList);
            FXMLController.getPeakAttrController().getStage().toFront();
            FXMLController.getPeakAttrController().selectTab("Reference");
        }
    }

    void initHelm(VBox vBox) {
        this.vBox = vBox;

        ToolBar navBar = new ToolBar();
        vBox.getChildren().add(navBar);
        spinStatus = new SpinStatus();
        vBox.getChildren().add(spinStatus.build());

        seqPane = new SeqPane();

        drawingGroup = new Group();
        seqPane.getChildren().add(drawingGroup);
        seqPane.setMinHeight(25.0);

        clusterPane = new ClusterPane();
        clusterGroup = new Group();

        clusterPane.getChildren().add(clusterGroup);
        clusterPane.setMinHeight(25.0);

        clusterStatus = new ClusterStatus();

        vBox.getChildren().add(clusterStatus.build());

        vBox.getChildren().add(clusterPane);
        vBox.getChildren().add(seqPane);
        vBox.heightProperty().addListener(e -> updateVBoxSize());
        vBox.widthProperty().addListener(e -> updateVBoxSize());
        initPeakNavigator(navBar);
    }

    void updateVBoxSize() {
        seqPane.layoutChildren();
        double vBoxHeight = vBox.getHeight();
        double paneHeight = vBoxHeight - 120.0;
        clusterPane.setPrefHeight(paneHeight / 2.0);
        seqPane.setPrefHeight(paneHeight / 2.0);
        clusterPane.layoutChildren();
        updateSeqCanvas();
        updateClusterCanvas();
    }

    void updateTabSize() {
    }

    public boolean unifyLimits() {
        return unifyLimitsCheckBox.isSelected();
    }

    public void unifyLimits(boolean value) {
        unifyLimitsCheckBox.setSelected(value);
    }

    void initPeakNavigator(ToolBar toolBar) {
        this.navigatorToolBar = toolBar;
        peakIdField = new TextField();
        peakIdField.setMinWidth(75);
        peakIdField.setMaxWidth(75);
        RunAboutGUI navigator = this;

        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.LEFT);
        closeButton.setOnAction(e -> close());

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::firstPeak);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::previousPeak);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::nextPeak);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::lastPeak);
        buttons.add(bButton);
        deleteButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.BAN, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(Event::consume);
        deleteButton.setOnAction(e -> delete());


        if (closeAction != null) {
            navigatorToolBar.getItems().add(closeButton);
        }
        peakListMenuButton = new MenuButton("List");
        navigatorToolBar.getItems().add(peakListMenuButton);
        updatePeakListMenu();

        arrangeMenuButton = new MenuButton("Arrangements");
        navigatorToolBar.getItems().add(arrangeMenuButton);

        MenuButton actionMenuButton = new MenuButton("Actions");
        navigatorToolBar.getItems().add(actionMenuButton);
        ToolBarUtils.addFiller(navigatorToolBar, 40, 300);

        MenuItem refDisplayItem = new MenuItem("Show Ref Chart");
        refDisplayItem.setOnAction(e -> showRefChart());
        actionMenuButton.getItems().add(refDisplayItem);

        MenuItem alignItem = new MenuItem("Align");
        alignItem.setOnAction(e -> alignCenters());
        actionMenuButton.getItems().add(alignItem);

        MenuItem filterItem = new MenuItem("Filter");
        filterItem.setOnAction(e -> filter());
        actionMenuButton.getItems().add(filterItem);

        MenuItem assembleItem = new MenuItem("Assemble");
        assembleItem.setOnAction(e -> {
            assemble();
            clusterStatus.refresh();
        });
        actionMenuButton.getItems().add(assembleItem);

        MenuItem calcCombItem = new MenuItem("Combinations");
        calcCombItem.setOnAction(e -> runAbout.calcCombinations());
        actionMenuButton.getItems().add(calcCombItem);
        MenuItem compareItem = new MenuItem("Compare");
        compareItem.setOnAction(e -> runAbout.compare());
        actionMenuButton.getItems().add(compareItem);

        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(peakIdField);
        toolBar.getItems().add(deleteButton);

        clusterModesChoiceBox = new ChoiceBox<>();
        clusterModesChoiceBox.getItems().addAll(SpinSystems.ClusterModes.values());
        clusterModesChoiceBox.setValue(SpinSystems.ClusterModes.ALL);
        toolBar.getItems().add(clusterModesChoiceBox);

        clusterModesChoiceBox.valueProperty().addListener(e -> updateClusterCanvas());


        MenuButton spinSysMenuButton = new MenuButton("SpinSys Actions");

        MenuItem splitItem = new MenuItem("Split");
        splitItem.setOnAction(e -> splitSystem());
        spinSysMenuButton.getItems().add(splitItem);

        MenuItem moveItem = new MenuItem("Move to Cluster");
        moveItem.setOnAction(e -> moveToThisCluster());
        spinSysMenuButton.getItems().add(moveItem);

        MenuItem analyzeItem = new MenuItem("Analyze");
        analyzeItem.setOnAction(e -> analyzeSystem());
        spinSysMenuButton.getItems().add(analyzeItem);

        MenuItem bipartiteItem = new MenuItem("GraphMatching");
        bipartiteItem.setOnAction(e -> makeGraphMatcher());
        spinSysMenuButton.getItems().add(bipartiteItem);

        MenuItem trimItem = new MenuItem("Trim");
        trimItem.setOnAction(e -> trimSystem());
        spinSysMenuButton.getItems().add(trimItem);

        MenuItem trimAllItem = new MenuItem("Trim All");
        trimAllItem.setOnAction(e -> trimSystems());
        spinSysMenuButton.getItems().add(trimAllItem);

        MenuItem extendItem = new MenuItem("Extend");
        extendItem.setOnAction(e -> extendSystem());
        spinSysMenuButton.getItems().add(extendItem);

        MenuItem extendAllItem = new MenuItem("Extend All");
        extendAllItem.setOnAction(e -> extendAllSystems());
        spinSysMenuButton.getItems().add(extendAllItem);

        MenuItem freezeItem = new MenuItem("Freeze");
        freezeItem.setOnAction(e -> freezeSystem());
        spinSysMenuButton.getItems().add(freezeItem);

        MenuItem thawItem = new MenuItem("Thaw");
        thawItem.setOnAction(e -> thawSystem());
        spinSysMenuButton.getItems().add(thawItem);

        MenuItem thawAllItem = new MenuItem("Thaw All");
        thawAllItem.setOnAction(e -> thawAllSystems());
        spinSysMenuButton.getItems().add(thawAllItem);

        toolBar.getItems().add(spinSysMenuButton);

        Slider probSlider = new Slider(1, 100, 10.0);
        probSlider.setBlockIncrement(1);
        Label probField = new Label();
        probField.setPrefWidth(100);
        probField.setText("0.1");
        toolBar.getItems().addAll(probSlider, probField);
        probSlider.valueProperty().addListener(v -> probSliderChanged(probSlider, probField));

        ToolBarUtils.addFiller(navigatorToolBar, 40, 300);
        ToolBarUtils.addFiller(navigatorToolBar, 70, 70);

        if (showAtoms) {
            atomXFieldLabel = new Label("X:");
            atomYFieldLabel = new Label("Y:");
            intensityFieldLabel = new Label("I:");
            atomXLabel = new Label();
            atomXLabel.setMinWidth(75);
            atomYLabel = new Label();
            atomYLabel.setMinWidth(75);
            intensityLabel = new Label();
            intensityLabel.setMinWidth(75);

            Pane filler2 = new Pane();
            filler2.setMinWidth(20);
            Pane filler3 = new Pane();
            filler3.setMinWidth(20);
            Pane filler4 = new Pane();
            filler4.setMinWidth(20);
            Pane filler5 = new Pane();
            HBox.setHgrow(filler5, Priority.ALWAYS);

            toolBar.getItems().addAll(filler2, atomXFieldLabel, atomXLabel, filler3, atomYFieldLabel, atomYLabel, filler4, intensityFieldLabel, intensityLabel, filler5);

        }

        peakIdField.setOnKeyReleased(kE -> {
            if (null != kE.getCode()) {
                switch (kE.getCode()) {
                    case ENTER -> navigator.gotoPeakId(peakIdField);
                    case UP -> navigator.gotoNextMatch(1);
                    case DOWN -> navigator.gotoNextMatch(-1);
                    default -> {
                    }
                }
            }
        });
        GUIProject.getActive().addPeakListSubscription(this::updatePeakListMenu);

        // The different control items end up with different heights based on font and icon size,
        // set all the items to use the same height
        this.navigatorToolBar.heightProperty().addListener((observable, oldValue, newValue) -> GUIUtils.toolbarAdjustHeights(List.of(navigatorToolBar)));
    }

    class ClusterStatus {

        HBox hBox;
        GridPane pane1;
        Pane leftPane;
        Group leftGroup;
        Pane rightPane;
        Group rightGroup;
        Map<String, Label> labelMap = new HashMap<>();
        List<ResidueLabel> leftResidues = new ArrayList<>();
        List<ResidueLabel> rightResidues = new ArrayList<>();

        HBox build() {
            hBox = new HBox();
            hBox.setPrefHeight(50.0);
            pane1 = new GridPane();
            leftPane = new Pane();
            rightPane = new Pane();
            leftPane.setMinWidth(220);
            rightPane.setMinWidth(220);
            leftPane.setPrefHeight(50);
            rightPane.setPrefHeight(50);
            rightPane.setPrefWidth(40);
            leftGroup = new Group();
            rightGroup = new Group();
            leftPane.getChildren().add(leftGroup);
            rightPane.getChildren().add(rightGroup);
            Pane filler0 = new Pane();
            HBox.setHgrow(filler0, Priority.ALWAYS);
            Pane filler1 = new Pane();
            HBox.setHgrow(filler1, Priority.ALWAYS);
            Pane filler2 = new Pane();
            HBox.setHgrow(filler2, Priority.ALWAYS);
            Pane filler3 = new Pane();
            HBox.setHgrow(filler3, Priority.ALWAYS);
            hBox.getChildren().addAll(filler0, leftPane, filler1, pane1, filler2, rightPane, filler3);
            return hBox;
        }

        void refresh() {
            pane1.getChildren().clear();
            labelMap.clear();
            int col = 0;
            for (int k = 0; k < 2; k++) {
                for (SpinSystem.AtomEnum atomEnum : SpinSystem.AtomEnum.values()) {
                    int n = runAbout.getExpected(k, atomEnum);
                    if (n != 0) {
                        String aName = atomEnum.name();
                        if (k == 0) {
                            aName = aName.toLowerCase();
                        } else {
                            aName = aName.toUpperCase();
                        }
                        Label label = new Label(aName);
                        label.setMinWidth(45.0);
                        pane1.add(label, col, 0);
                        Label labelPPM = new Label(String.valueOf(n));
                        labelPPM.setMinWidth(45.0);
                        labelMap.put(aName, labelPPM);
                        pane1.add(labelPPM, col, 1);
                        col++;
                    }
                }
            }
            double x = 10.0;
            double y = 15.0;
            int i = 0;
            leftResidues.clear();
            rightResidues.clear();
            double width = 20.0;
            for (String aaName : AtomParser.getAANames()) {
                String aaChar = AtomParser.convert3To1(aaName);
                ResidueLabel leftLabel = new ResidueLabel(aaChar.charAt(0));
                ResidueLabel rightLabel = new ResidueLabel(aaChar.charAt(0));
                leftResidues.add(leftLabel);
                rightResidues.add(rightLabel);
                leftGroup.getChildren().add(leftLabel);
                rightGroup.getChildren().add(rightLabel);
                leftLabel.place(x, y, width, width);
                rightLabel.place(x, y, width, width);
                x += leftLabel.width;
                i++;
                if (i >= 10) {
                    i = 0;
                    x = 10.0;
                    y = y + 20.0;
                }
            }

        }

        void clearLabels() {
            for (Label label : labelMap.values()) {
                label.setText("");
                Tooltip toolTip = label.getTooltip();
                if (toolTip == null) {
                    toolTip = new Tooltip();
                    label.setTooltip(toolTip);
                    toolTip.setText("");
                }
            }
        }

        void setLabel(String aName, double value, double range, int nValues) {
            Label label = labelMap.get(aName);
            if (!Double.isNaN(value)) {
                label.setText(String.format("%.1f", value));
                Tooltip toolTip = label.getTooltip();
                if (toolTip == null) {
                    toolTip = new Tooltip();
                    label.setTooltip(toolTip);
                }
                toolTip.setText(String.format("%.1f : %2d", range, nValues));
            }
        }

        void setLabels() {
            clearLabels();
            SpinSystem spinSystem = currentSpinSystem;
            double[][] ppms = new double[2][2];
            for (int k = 0; k < 2; k++) {
                ppms[k][0] = Double.NaN;
                ppms[k][1] = Double.NaN;
                for (SpinSystem.AtomEnum atomEnum : SpinSystem.AtomEnum.values()) {
                    int n = runAbout.getExpected(k, atomEnum);
                    if (n != 0) {
                        String aName = atomEnum.name();
                        if (k == 0) {
                            aName = aName.toLowerCase();
                        } else {
                            aName = aName.toUpperCase();
                        }
                        Optional<Double> valueOpt = spinSystem.getValue(k, atomEnum);
                        Optional<Double> range = spinSystem.getRange(k, atomEnum);
                        Optional<Integer> nValues = spinSystem.getNValues(k, atomEnum);
                        if (valueOpt.isPresent() && range.isPresent() && nValues.isPresent()) {
                            double value = valueOpt.get();
                            if (aName.equalsIgnoreCase("ca")) {
                                ppms[k][0] = value;
                            } else if (aName.equalsIgnoreCase("cb")) {
                                ppms[k][1] = value;
                            }
                            setLabel(aName, value, range.get(), nValues.get());
                        }
                    }
                }
                List<AAScore> scores = FragmentScoring.scoreAA(ppms[k]);
                int iScore = 0;
                for (AAScore aaScore : scores) {
                    String name = AtomParser.convert3To1(aaScore.getName());
                    if (!name.equalsIgnoreCase("p") || (k == 0)) {
                        ResidueLabel resLabel = k == 0 ? leftResidues.get(iScore) : rightResidues.get(iScore);
                        Color color = Color.WHITE;
                        Color color2 = Color.LIGHTGRAY;
                        color = color2.interpolate(color, aaScore.getNorm());
                        if (aaScore.getNorm() < 1.0e-3) {
                            name = name.toLowerCase();
                            color = Color.DARKGRAY;
                        }
                        resLabel.setText(name);
                        resLabel.setColor(color);
                        iScore++;
                    }
                }
                for (int i = iScore; i < leftResidues.size(); i++) {
                    ResidueLabel resLabel = k == 0 ? leftResidues.get(i) : rightResidues.get(i);
                    resLabel.setText("");
                    resLabel.setColor(Color.DARKGRAY);
                }
            }
        }
    }

    class SpinStatus {

        int nFields = 2;
        HBox hBox;
        Spinner<Integer>[] spinners = new Spinner[2];
        Label[] scoreFields = new Label[nFields];
        Button[] sysFields = new Button[nFields];
        Label[] nMatchFields = new Label[nFields];
        Label[] recipLabels = new Label[nFields];
        Label[] availLabels = new Label[nFields];
        Label[] viableLabels = new Label[nFields];
        CheckBox[] selectedButtons = new CheckBox[nFields];
        InvalidationListener[] listeners = new InvalidationListener[nFields];

        List<SpinSystem> spinSystems;
        SpinSystem spinSys;

        void valueChanged(Spinner<Integer> spinner) {
            gotoSpinSystems(spinners[0].getValue(), spinners[1].getValue(), false);
        }

        void activationChanged(CheckBox checkBox, Spinner<Integer> spinner, boolean prevState) {
            List<SpinSystemMatch> matches = prevState ? spinSys.getMatchToPrevious() : spinSys.getMatchToNext();
            int index = spinner.getValue();
            SpinSystemMatch spinMatch = matches.get(index);
            if (checkBox.isSelected()) {
                spinSys.confirm(spinMatch, prevState);
            } else {
                spinSys.unconfirm(spinMatch, prevState);
            }
            updateFragment(spinSys);
            updateClusterCanvas();
        }

        void updateFragment(SpinSystem spinSys) {
            for (ResidueLabel resLabel : residueLabelMap.values()) {
                resLabel.setTopLineVisible(false);
                var optResidue = resLabel.getResidue();
                resLabel.hideBottomLine();
                optResidue.ifPresent(residue -> {
                    Atom atom = residue.getAtom("CA");
                    if (atom != null) {
                        Double ppm = atom.getPPM();
                        if (ppm != null) {
                            resLabel.setTopLineVisible(true);
                        }
                    }
                });
            }
            Optional<SeqFragment> fragmentOpt = spinSys.getFragment();
            fragmentOpt.ifPresent(frag -> {
                Molecule molecule = Molecule.getActive();
                if (!frag.isFrozen()) {
                    List<ResidueSeqScore> resSeqScores = frag.scoreShifts(molecule);
                    if (resSeqScores.size() == 1) {
                        frag.setResSeqScore(resSeqScores.get(0));
                    }
                    resSeqScores.stream().sorted(Comparator.comparingDouble(ResidueSeqScore::getScore)).forEach(resSeqScore -> {
                        Residue residue = resSeqScore.getFirstResidue();
                        double score = resSeqScore.getScore();
                        for (int iRes = 0; iRes < resSeqScore.getNResidues(); iRes++) {
                            String key = residue.getPolymer().getName() + residue.getNumber();
                            ResidueLabel resLabel = residueLabelMap.get(key);
                            Color color = Color.YELLOW.interpolate(Color.GREEN, score);
                            resLabel.showBottomLine(color);
                            if (resLabel.spinSystem == spinSys) {
                                resLabel.setColor(Color.WHITE);
                            }
                            SpinSystem iSpinSystem = frag.getSpinSystem(iRes);
                            resLabel.setSpinSystem(iSpinSystem);
                            resLabel.setTooltip(residue.getNumber() + " " + String.format("%.3f", score));
                            residue = residue.getNext();
                        }
                    });
                }
            });
        }

        void gotoSystem(int index) {
            int id = Integer.parseInt(sysFields[index].getText());
            var optSys = runAbout.getSpinSystems().find(id);
            if (optSys.isPresent()) {
                currentSpinSystem = optSys.get();
                gotoSpinSystems();
            }
        }

        HBox build() {
            hBox = new HBox();
            for (int i = 0; i < nFields; i++) {
                final int index = i;
                Pane pane1 = new Pane();
                HBox.setHgrow(pane1, Priority.ALWAYS);
                hBox.getChildren().add(pane1);
                Spinner<Integer> spinner = new Spinner<>(0, 0, 0);
                spinners[i] = spinner;
                listeners[i] = observable -> valueChanged(spinner);
                spinners[i].valueProperty().addListener(listeners[i]);
                scoreFields[i] = new Label();
                sysFields[i] = new Button();
                sysFields[i].setOnAction(e -> gotoSystem(index));
                nMatchFields[i] = new Label();
                recipLabels[i] = new Label();
                recipLabels[i].setTextAlignment(TextAlignment.CENTER);
                recipLabels[i].setAlignment(Pos.CENTER);
                availLabels[i] = new Label();
                availLabels[i].setTextAlignment(TextAlignment.CENTER);
                availLabels[i].setAlignment(Pos.CENTER);
                viableLabels[i] = new Label();
                viableLabels[i].setTextAlignment(TextAlignment.CENTER);
                viableLabels[i].setAlignment(Pos.CENTER);
                scoreFields[i].setTextAlignment(TextAlignment.CENTER);
                nMatchFields[i].setTextAlignment(TextAlignment.CENTER);
                CheckBox checkBox = new CheckBox();
                selectedButtons[i] = checkBox;
                final boolean prevState = i == 0;
                selectedButtons[i].setOnAction(e -> activationChanged(checkBox, spinner, prevState));

                spinners[i].setMaxWidth(60);

                sysFields[i].setMaxWidth(50);
                scoreFields[i].setMaxWidth(50);
                nMatchFields[i].setMaxWidth(25);
                recipLabels[i].setMaxWidth(40);
                availLabels[i].setMaxWidth(20);
                viableLabels[i].setMaxWidth(20);

                sysFields[i].setMinWidth(50);
                scoreFields[i].setMinWidth(50);
                nMatchFields[i].setMinWidth(25);
                recipLabels[i].setMinWidth(40);
                availLabels[i].setMinWidth(20);
                viableLabels[i].setMinWidth(20);

                hBox.getChildren().add(spinners[i]);
                Label sysLabel = new Label(" Sys:");
                hBox.getChildren().addAll(sysLabel, sysFields[i]);
                Label scoreLabel = new Label(" Score:");
                hBox.getChildren().addAll(scoreLabel, scoreFields[i]);
                Label nLabel = new Label(" N:");
                hBox.getChildren().addAll(nLabel, nMatchFields[i]);
                Label recipLabel = new Label(" RAV:");
                hBox.getChildren().addAll(recipLabel, recipLabels[i], availLabels[i], viableLabels[i]);
                hBox.getChildren().add(selectedButtons[i]);

                Pane pane2 = new Pane();
                HBox.setHgrow(pane2, Priority.ALWAYS);
                hBox.getChildren().add(pane2);
            }

            return hBox;
        }

        void setValue(int iSpinner, int value) {
            spinners[iSpinner].valueProperty().removeListener(listeners[iSpinner]);
            SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                    (SpinnerValueFactory.IntegerSpinnerValueFactory) spinners[iSpinner].getValueFactory();

            factory.setValue(value);
            spinners[iSpinner].valueProperty().addListener(listeners[iSpinner]);
        }

        void showScore(List<SpinSystem> spinSystems, boolean useBest) {
            this.spinSystems = spinSystems;
            if (!spinSystems.isEmpty()) {
                spinSys = spinSystems.size() > 2 ? spinSystems.get(2) : spinSystems.get(0);
                for (int i = 0; i < nFields; i++) {
                    if (spinSys != null) {
                        List<SpinSystemMatch> matches = i == 0 ? spinSys.getMatchToPrevious() : spinSys.getMatchToNext();
                        SpinnerValueFactory.IntegerSpinnerValueFactory factory = (SpinnerValueFactory.IntegerSpinnerValueFactory) spinners[i].getValueFactory();
                        factory.setMax(matches.size() - 1);
                        if (useBest) {
                            int index = Math.max(0, i == 0 ? spinSys.getConfirmedPrevious() : spinSys.getConfirmedNext());
                            setValue(i, index);
                        }

                        showScore(spinSys, useBest);
                    }
                }
            }
        }

        void showScore(SpinSystem spinSys, boolean useBest) {
            for (int i = 0; i < nFields; i++) {
                boolean ok = false;
                if (spinSys != null) {
                    List<SpinSystemMatch> matches = i == 0 ? spinSys.getMatchToPrevious() : spinSys.getMatchToNext();
                    int index;
                    if (useBest) {
                        index = Math.max(0, i == 0 ? spinSys.getConfirmedPrevious() : spinSys.getConfirmedNext());
                    } else {
                        index = spinners[i].getValue();
                    }

                    if (matches.size() > index) {
                        selectedButtons[i].setDisable(false);
                        sysFields[i].setDisable(false);
                        spinners[i].setDisable(false);
                        SpinSystem otherSys;
                        SpinSystem matchSys;
                        SpinSystemMatch spinMatch = matches.get(index);
                        boolean viable = SeqFragment.testFrag(spinMatch);

                        if (i == 0) {
                            otherSys = spinMatch.getSpinSystemA();
                            var otherMatches = otherSys.getMatchToNext();
                            matchSys = otherMatches.isEmpty() ? null : otherMatches.get(0).getSpinSystemB();
                        } else {
                            otherSys = spinMatch.getSpinSystemB();
                            var otherMatches = otherSys.getMatchToPrevious();
                            matchSys = otherMatches.isEmpty() ? null : otherMatches.get(0).getSpinSystemA();
                        }
                        boolean confirmed = spinSys.confirmed(spinMatch, i == 0);
                        boolean available = confirmed || !otherSys.confirmed(i == 1);
                        selectedButtons[i].setSelected(confirmed);

                        boolean reciprocal = matchSys == spinSys;
                        if (reciprocal) {
                            recipLabels[i].setText("R");
                            recipLabels[i].setStyle("-fx-background-color:LIGHTGREEN");
                        } else {
                            String matchLabel = "";
                            if (matchSys != null) {
                                matchLabel = String.valueOf(matchSys.getRootPeak().getIdNum());
                            }
                            recipLabels[i].setText(matchLabel);
                            recipLabels[i].setStyle("-fx-background-color:YELLOW");
                        }
                        if (available) {
                            availLabels[i].setText("A");
                            availLabels[i].setStyle("-fx-background-color:LIGHTGREEN");
                        } else {
                            availLabels[i].setText("a");
                            availLabels[i].setStyle("-fx-background-color:YELLOW");
                        }
                        if (viable) {
                            viableLabels[i].setText("V");
                            viableLabels[i].setStyle("-fx-background-color:LIGHTGREEN");
                        } else {
                            viableLabels[i].setText("v");
                            viableLabels[i].setStyle("-fx-background-color:YELLOW");
                        }

                        sysFields[i].setText(String.valueOf(otherSys.getId()));
                        nMatchFields[i].setText(String.valueOf(spinMatch.getN()));
                        scoreFields[i].setText(String.format("%5.0f", spinMatch.getScore()));
                        ok = true;
                    } else {
                        sysFields[i].setText("");
                        sysFields[i].setDisable(true);
                        nMatchFields[i].setText("0");
                        scoreFields[i].setText("0.0");
                        selectedButtons[i].setSelected(false);
                        selectedButtons[i].setDisable(true);
                        spinners[i].setDisable(true);
                        recipLabels[i].setText("");
                        recipLabels[i].setStyle("");
                        availLabels[i].setText("");
                        availLabels[i].setStyle("");
                        viableLabels[i].setText("");
                        viableLabels[i].setStyle("");
                    }
                }
                if (!ok) {
                    sysFields[i].setText("");
                }
            }
        }

    }


    class ResidueLabel extends StackPane {

        boolean active = false;
        double width = clusterCharHeight;
        Rectangle rect;
        Line line;
        Line bottomLine;
        Text textItem;
        Residue residue;
        SpinSystem spinSystem;
        Tooltip tooltip;

        boolean isNumLabel = false;

        Color color = Color.LIGHTGRAY;


        ResidueLabel(Residue residue) {
            this(residue.getOneLetter());
            this.residue = residue;
        }

        ResidueLabel(char resChar) {
            this(String.valueOf(resChar));
        }

        ResidueLabel(String label) {
            super();
            StackPane stack = this;
            textItem = new Text(label);
            rect = new Rectangle(width, width, Color.WHITE);
            line = new Line(0, 0, width, 0);
            line.setStrokeWidth(2);
            line.setFill(Color.BLACK);
            bottomLine = new Line(0, width - 2, width, width - 2);
            bottomLine.setStrokeWidth(5);
            bottomLine.setFill(Color.BLACK);

            rect.setStroke(null);
            rect.setStrokeWidth(3);
            rect.setFill(Color.WHITE);
            textItem.setFill(Color.BLACK);
            textItem.setFont(REGULAR_FONT);
            rect.setMouseTransparent(true);
            line.setMouseTransparent(true);
            bottomLine.setMouseTransparent(true);
            textItem.setMouseTransparent(true);
            stack.getChildren().addAll(rect, textItem, line, bottomLine);
            stack.setAlignment(Pos.CENTER);
            StackPane.setAlignment(line, Pos.TOP_CENTER);
            StackPane.setAlignment(bottomLine, Pos.BOTTOM_CENTER);
            line.setVisible(false);
            bottomLine.setVisible(false);
            tooltip = new Tooltip(label);
            Tooltip.install(this, tooltip);
        }

        ResidueLabel(String label, boolean isNumLabel) {
            this(label);
            this.isNumLabel = isNumLabel;
        }

        void place(double x, double y, double width, double height) {
            rect.setWidth(width);
            rect.setHeight(height);
            line.setEndX(width);
            bottomLine.setStartY(height + height / 2 - 2);
            bottomLine.setEndY(height + height / 2 - 2);
            setTranslateX(x - width / 2 + 1);
            setTranslateY(y - height / 2 + 1);
        }

        void setColor(Color color) {
            rect.setFill(color);
        }

        void setSavedColor() {
            rect.setFill(color);
        }

        void setAndSaveColor(Color color) {
            setColor(color);
            this.color = color;
        }

        void setText(String text) {
            textItem.setText(text);
        }

        String getTextLabel() {
            return textItem.getText();
        }

        void setTooltip(String text) {
            tooltip.setText(text);
        }

        Font getFont(boolean active) {
            FontWeight fontWeight = active ? FontWeight.BOLD : FontWeight.NORMAL;
            return Font.font(null, fontWeight, (int) (seqCharHeight * 0.9));
        }

        void updateFontSize(double size) {
            Font currentFont = textItem.getFont();
            double currentSize = currentFont.getSize();
            if (Math.abs(size - currentSize) > 0.1) {
                Font font = Font.font(null, active ? FontWeight.BOLD : FontWeight.NORMAL, size);
                textItem.setFont(font);
            }
        }

        void setActive() {
            active = true;
            textItem.setFont(getFont(true));
            textItem.setFill(Color.BLUE);
            setColor(Color.WHITE);
        }

        void setInactive() {
            active = false;
            textItem.setFont(getFont(false));
            textItem.setFill(Color.BLACK);
            setColor(Color.LIGHTGRAY);
        }

        void setSpinSystem(SpinSystem spinSystem) {
            this.spinSystem = spinSystem;
        }

        void setTopLineVisible(boolean value) {
            line.setVisible(value);
        }

        void setRectangleFrame(boolean value) {
            rect.setStroke(value ? Color.BLACK : null);
        }

        void setTextColor(Color color) {
            textItem.setFill(color);
        }

        void showBottomLine(Color color) {
            bottomLine.setVisible(true);
            bottomLine.setFill(color);
            bottomLine.setStroke(color);
        }

        void hideBottomLine() {
            bottomLine.setVisible(false);
        }

        Optional<Residue> getResidue() {
            return Optional.ofNullable(residue);
        }
    }

    void gotoResidue(MouseEvent e, ResidueLabel resLabel) {
        if (resLabel.spinSystem != null) {
            currentSpinSystem = resLabel.spinSystem;
            gotoSpinSystems();

            if (e.getClickCount() == 2) {
                resLabel.getResidue().ifPresent(this::freezeSystemAtPosition);
                e.consume();
            }
        }
    }

    void gotoCluster(ResidueLabel resLabel) {
        int id = Integer.parseInt(resLabel.textItem.getText());
        var optSys = runAbout.getSpinSystems().find(id);
        if (optSys.isPresent()) {
            currentSpinSystem = optSys.get();
            gotoSpinSystems();
        }
    }

    void updateCluster(ResidueLabel resLabel, XY xy, SpinSystem spinSys) {
        resLabel.setText(String.valueOf(spinSys.getRootPeak().getIdNum()));
        resLabel.setOnMouseClicked(e -> gotoCluster(resLabel));
        double width = clusterCharHeight * HEIGHT_WIDTH * resLabel.getTextLabel().length();

        double xoffset = (resLabel.getTextLabel().length() - 1) / 2.0 * clusterCharHeight * HEIGHT_WIDTH;
        resLabel.updateFontSize(clusterCharHeight * 0.9);


        resLabel.place(xy.x + xoffset, xy.y, width, clusterCharHeight);
        resLabel.setSpinSystem(spinSys);
    }

    int countSpinSysItems(List<SpinSystem> sortedSystems, boolean fragmentMode) {
        int n = 0;
        for (SpinSystem spinSys : sortedSystems) {
            Optional<SeqFragment> fragmentOpt = spinSys.getFragment();
            if (fragmentMode && fragmentOpt.isPresent()) {
                SeqFragment fragment = fragmentOpt.get();
                n += fragment.getSpinSystemMatches().size() + 1;
            } else {
                n++;
            }
        }
        return n;
    }

    void updateActiveSystem() {
        for (var node : clusterGroup.getChildren()) {
            if (node instanceof ResidueLabel residueLabel) {
                boolean activeSystem = residueLabel.spinSystem == currentSpinSystem;
                residueLabel.setColor(activeSystem ? Color.WHITE : residueLabel.color);
            }
        }
    }

    Color getPaletteColor(int i) {
        return paletteColors.get(i % (paletteColors.size() - 1));
    }

    record XY(double x, double y) {

    }

    XY updateXY(ResidueLabel residueLabel, XY xy, double height) {
        double width = clusterCharHeight * HEIGHT_WIDTH * residueLabel.getTextLabel().length();
        double x = xy.x;
        double y = xy.y;
        x += width;
        if (x > (clusterPane.getWidth() - 2.0 * width)) {
            x = 25.0;
            y += height + clusterPane.gap;
        }
        return new XY(x, y);

    }

    void updateClusterCanvas() {
        List<SpinSystem> sortedSystems;
        boolean fragmentMode;
        if (clusterModesChoiceBox.getValue() != SpinSystems.ClusterModes.ALL) {
            fragmentMode = false;
            sortedSystems = runAbout.getSpinSystems().getSystemsByType(clusterModesChoiceBox.getValue());
        } else {
            fragmentMode = true;
            sortedSystems = runAbout.getSpinSystems().getSortedSystems();
        }
        int n = countSpinSysItems(sortedSystems, fragmentMode);
        if (clusterGroup.getChildren().size() != n) {
            clusterGroup.getChildren().clear();
            for (int i = 0; i < n; i++) {
                ResidueLabel resLabel = new ResidueLabel(String.valueOf(i));
                clusterGroup.getChildren().add(resLabel);
            }
        }
        List<Node> nodes = clusterGroup.getChildren();

        int i = 0;
        XY xy = new XY(25.0, 10.0);
        double height = clusterCharHeight;
        int iColor = 0;
        spinSystemColorMap.clear();
        for (SpinSystem spinSys : sortedSystems) {
            Optional<SeqFragment> fragmentOpt = spinSys.getFragment();
            if (fragmentMode && fragmentOpt.isPresent()) {
                SeqFragment fragment = fragmentOpt.get();
                boolean frozen = fragment.isFrozen();
                List<SpinSystemMatch> spinMatches = fragment.getSpinSystemMatches();
                ResidueLabel resLabel = (ResidueLabel) nodes.get(i);
                SpinSystem thisSystem = spinMatches.get(0).getSpinSystemA();
                updateCluster(resLabel, xy, thisSystem);
                xy = updateXY(resLabel, xy, height);
                i++;
                resLabel.setAndSaveColor(getPaletteColor(iColor));
                resLabel.setTopLineVisible(frozen);
                resLabel.setSpinSystem(thisSystem);
                spinSystemColorMap.put(thisSystem, getPaletteColor(iColor));

                for (SpinSystemMatch spinMatch : spinMatches) {
                    resLabel = (ResidueLabel) nodes.get(i);
                    SpinSystem aSystem = spinMatch.getSpinSystemB();
                    updateCluster(resLabel, xy, aSystem);
                    xy = updateXY(resLabel, xy, height);
                    i++;
                    resLabel.setAndSaveColor(getPaletteColor(iColor));
                    resLabel.setTopLineVisible(frozen);
                    resLabel.setSpinSystem(aSystem);
                    spinSystemColorMap.put(aSystem, getPaletteColor(iColor));
                }
                iColor++;
            } else {
                ResidueLabel resLabel = (ResidueLabel) nodes.get(i);
                updateCluster(resLabel, xy, spinSys);
                xy = updateXY(resLabel, xy, height);
                i++;
                resLabel.setAndSaveColor(paletteColors.get(paletteColors.size() - 1));
                resLabel.setTopLineVisible(false);
                resLabel.setSpinSystem(spinSys);
            }
        }
        updateSeqCanvas();
    }

    void generateSeqCanvasItems(Molecule molecule) {
        int i = 0;
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isPeptide()) {
                for (Residue residue : polymer.getResidues()) {
                    if ((i % 10) == 0) {
                        ResidueLabel resLabel = new ResidueLabel(String.valueOf(residue.getResNum()), true);
                        drawingGroup.getChildren().add(resLabel);
                    }
                    ResidueLabel resLabel = new ResidueLabel(residue);


                    drawingGroup.getChildren().add(resLabel);
                    String key = polymer.getName() + residue.getNumber();
                    residueLabelMap.put(key, resLabel);
                    resLabel.setTooltip(residue.getNumber());
                    i++;
                }
            }
        }
    }

    void updateSeqCanvas() {
        runAbout.mapSpinSystemToResidue();
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            double height = seqCharHeight;
            if (drawingGroup.getChildren().isEmpty()) {
                generateSeqCanvasItems(molecule);
            }
            double y = seqCharHeight * 0.5;
            double x = 25.0;
            int i = 0;
            for (Node node : drawingGroup.getChildren()) {
                ResidueLabel resLabel = (ResidueLabel) node;
                SpinSystem spinSystem = runAbout.getSpinSystemForResidue(resLabel.residue);
                resLabel.setAndSaveColor(paletteColors.get(paletteColors.size() - 1));
                if (spinSystem != null) {
                    spinSystem.getFragment().ifPresent(fragment -> {
                        if (fragment.isFrozen()) {
                            resLabel.setSpinSystem(spinSystem);
                            Color color = spinSystemColorMap.get(spinSystem);
                            if (spinSystem == currentSpinSystem) {
                                color = Color.WHITE;
                            }
                            resLabel.setAndSaveColor(color);
                        }
                    });
                }
                double width = resLabel.getTextLabel().length() * seqCharHeight * HEIGHT_WIDTH;
                double xoffset = (resLabel.getTextLabel().length() - 1) / 2.0 * seqCharHeight * HEIGHT_WIDTH;
                if (resLabel.isNumLabel) {
                    resLabel.updateFontSize(seqCharHeight * 0.8 * 0.8);
                } else {
                    resLabel.setOnMouseClicked(e -> gotoResidue(e, resLabel));
                    resLabel.updateFontSize(seqCharHeight * 0.8);
                }
                if ((i > 0)) {
                    ResidueLabel previousLabel = (ResidueLabel) drawingGroup.getChildren().get(i - 1);
                    if (previousLabel.isNumLabel) {
                        previousLabel.setAndSaveColor(resLabel.color);
                    }
                }
                resLabel.place(x + xoffset, y, width, height);

                x += width;
                if (x > (seqPane.getWidth() - 2.0 * width)) {
                    x = 25.0;
                    y += height + seqPane.gap;
                }
                i++;
            }
        }
        if (currentSpinSystem != null) {
            spinStatus.updateFragment(currentSpinSystem);
        }
    }

    void probSliderChanged(Slider slider, Label probField) {
        if (useSpinSystem) {
            double prob = slider.getValue() / 100.0;
            probField.setText(String.format("%.3f", prob));
            SeqFragment.setFragmentScoreProbability(prob);
            spinStatus.showScore(currentSpinSystem, false);
            scoreFragment();
        }
    }

    void filter() {
        var filterResult = runAbout.filterPeaks();
        if (filterResult.isEmpty()) {
            GUIUtils.warn("Runabout Filter", "No peaklists set up");
            return;
        }
        var console = AnalystApp.getConsoleController();
        console.write("Filter Peaks\n");
        for (var entry : filterResult.entrySet()) {
            console.write(entry.getKey() + " " + entry.getValue() + "\n");
        }
    }

    void assemble() {
        runAbout.assemble();
        updatePeakListMenu();
        useSpinSystem = true;
    }

    public void updatePeakListMenu() {
        if (runAbout == null) {
            return;
        }
        peakListMenuButton.getItems().clear();
        if (runAbout.getSpinSystems().getSize() > 0) {
            MenuItem spinSysMenuItem = new MenuItem("spinsystems");
            spinSysMenuItem.setOnAction(e -> RunAboutGUI.this.useSpinSystem = true);
            peakListMenuButton.getItems().add(spinSysMenuItem);
        }

        for (String peakListName : ProjectBase.getActive().getPeakListNames()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                RunAboutGUI.this.setPeakList(peakListName);
                RunAboutGUI.this.useSpinSystem = false;
            });
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    public void setPeakList() {
        if (navigationPeakList == null) {
            PeakList testList = null;
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                ObservableList<PeakListAttributes> attr = chart.getPeakListAttributes();
                if (!attr.isEmpty()) {
                    testList = attr.get(0).getPeakList();
                }
            }
            Optional<PeakList> firstListOpt = PeakList.getFirst();
            if (firstListOpt.isPresent()) {
                testList = firstListOpt.get();
            }
            setPeakList(testList);
        }
    }

    public void removePeakList() {
        if (navigationPeakList != null) {
            navigationPeakList.removePeakChangeListener(this);
        }
        navigationPeakList = null;
        currentPeak = null;
    }

    public void setPeakList(String listName) {
        navigationPeakList = PeakList.get(listName);
        PeakList.clusterOrigin = navigationPeakList;
        RunAboutGUI.this.setPeakList(navigationPeakList);
    }

    public void setPeakList(PeakList newPeakList) {
        navigationPeakList = newPeakList;
        if (navigationPeakList != null) {
            currentPeak = navigationPeakList.getPeak(0);
            setPeakIdField();
            navigationPeakList.registerPeakChangeListener(this);
        }
    }

    public Peak getPeak() {
        return currentPeak;
    }

    void analyzeSystem() {
        var spinSys = currentSpinSystem;
        spinSys.calcCombinations(false);
        gotoSpinSystems();
    }



    void makeGraphMatcher() {
        resSeqMatcher = new ResSeqMatcher();
        GraphMatcherGUI graphMatcherGUI = new GraphMatcherGUI(this, resSeqMatcher, runAbout);

    }

    public void setSpinSystems(List<SpinSystem> spinSystems, boolean useBest) {
        drawSpinSystems(spinSystems, useBest);
        setPeakIdField();
    }

    public void setPeaks(List<Peak> peaks) {
        if ((peaks == null) || peaks.isEmpty()) {
            currentPeak = null;
            updateAtomLabels(null);
        } else {
            int iPeak = peaks.size() > 1 ? 1 : 0;
            currentPeak = peaks.get(iPeak);
            drawWins(peaks);
            updateDeleteStatus();
            updateAtomLabels(peaks.get(iPeak));
        }
        setPeakIdField();
    }

    public void setPeak(Peak peak) {
        currentPeak = peak;
        if (peak != null) {
            drawWins(Collections.singletonList(peak));
            updateDeleteStatus();
            refreshRefChart(peak);
        }
        updateAtomLabels(peak);
        setPeakIdField();
    }

    void updateAtomLabels(Peak peak) {
        if (showAtoms) {
            if (peak != null) {
                atomXLabel.setText(peak.getPeakDim(0).getLabel());
                intensityLabel.setText(String.format("%.2f", peak.getIntensity()));
                if (peak.getPeakDims().length > 1) {
                    atomYLabel.setText(peak.getPeakDim(1).getLabel());
                }
            } else {
                atomXLabel.setText("");
                atomYLabel.setText("");
                intensityLabel.setText("");
            }
        }
    }

    void updateDeleteStatus() {
        if (defaultBackground == null) {
            defaultBackground = peakIdField.getBackground();
        }
        if (!useSpinSystem && (currentPeak != null)) {
            if (currentPeak.getStatus() < 0) {
                deleteButton.setSelected(true);
                peakIdField.setBackground(DELETE_BACKGROUND);
            } else {
                deleteButton.setSelected(false);
                peakIdField.setBackground(defaultBackground);
            }
        }
    }

    private void setPeakIdField() {
        if (useSpinSystem) {
            if (currentSpinSystem == null) {
                peakIdField.setText("");
            } else {
                peakIdField.setText(String.valueOf(currentSpinSystem.getId()));
            }
        } else {
            if (currentPeak == null) {
                peakIdField.setText("");
            } else {
                peakIdField.setText(String.valueOf(currentPeak.getIdNum()));
            }

        }

    }

    public void firstPeak(ActionEvent ignoredEvent) {
        if (useSpinSystem) {
            firstSpinSystem();
        } else {
            firstPeak();
        }
    }

    public void previousPeak(ActionEvent event) {
        if (useSpinSystem) {
            previousSpinSystem();
        } else {
            previousPeak();
        }
    }

    public void nextPeak(ActionEvent event) {
        if (useSpinSystem) {
            nextSpinSystem();
        } else {
            nextPeak();
        }
    }

    public void lastPeak(ActionEvent event) {
        if (useSpinSystem) {
            lastSpinSystem();
        } else {
            lastPeak();
        }
    }

    public void firstSpinSystem() {
        currentSpinSystem = runAbout.getSpinSystems().firstSpinSystem();
        gotoSpinSystems();
    }

    public void lastSpinSystem() {
        currentSpinSystem = runAbout.getSpinSystems().lastSpinSystem();
        gotoSpinSystems();
    }

    public void nextSpinSystem() {
        currentSpinSystem = runAbout.getSpinSystems().nextSpinSystem(currentSpinSystem);
        gotoSpinSystems();
    }

    public void previousSpinSystem() {
        currentSpinSystem = runAbout.getSpinSystems().previousSpinSystem(currentSpinSystem);
        gotoSpinSystems();
    }

    public void gotoSpinSystems() {
        if (currentSpinSystem != null) {
            int pIndex = Math.max(0, currentSpinSystem.getConfirmedPrevious());
            int sIndex = Math.max(0, currentSpinSystem.getConfirmedNext());

            gotoSpinSystems(pIndex, sIndex, true);
            clusterStatus.setLabels();
            var spinSys = currentSpinSystem;
            spinStatus.updateFragment(spinSys);
            refreshRefChart(currentSpinSystem.getRootPeak());
            updateActiveSystem();
            if ((spinSys != null) && spinSys.getFragment().isEmpty()) {
                spinSys.score();
            }
        }

    }

    public void scoreFragment() {
        var spinSys = currentSpinSystem;
        spinStatus.updateFragment(spinSys);
    }

    public void gotoSpinSystems(int pIndex, int sIndex, boolean useBest) {
        if (notArranged()) {
            return;
        }
        List<SpinSystem> spinSystems = new ArrayList<>();
        for (int resOffset : resOffsets) {
            SpinSystem spinSystem = runAbout.getSpinSystems().get(currentSpinSystem, resOffset, pIndex, sIndex);
            spinSystems.add(spinSystem);
        }
        setSpinSystems(spinSystems, useBest);
    }

    void delete() {
        if (!useSpinSystem) {
            PeakList currList = currentPeak.getPeakList();
            int peakIndex = currentPeak.getIndex();
            currentPeak.setStatus(-1);
            currList.compress();
            currList.reNumber();
            if (peakIndex >= navigationPeakList.size()) {
                peakIndex = navigationPeakList.size() - 1;
            }
            setPeak(currList.getPeak(peakIndex));
        } else {
            if (GUIUtils.affirm("Delete cluster and its peaks")) {
                var spinSystem = currentSpinSystem;
                spinSystem.delete();
                gotoSpinSystems();
            }
        }
    }

    void firstPeak() {
        if (navigationPeakList != null) {
            Peak peak = navigationPeakList.getPeak(0);
            setPeak(peak);
        }
    }

    public void previousPeak() {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex--;
            if (peakIndex < 0) {
                peakIndex = 0;
            }
            Peak peak = navigationPeakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void nextPeak() {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex++;
            if (peakIndex >= navigationPeakList.size()) {
                peakIndex = navigationPeakList.size() - 1;
            }
            Peak peak = navigationPeakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void lastPeak() {
        if (navigationPeakList != null) {
            int peakIndex = navigationPeakList.size() - 1;
            Peak peak = navigationPeakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public List<Peak> matchPeaks(String pattern) {
        List<Peak> result;
        if (pattern.startsWith("re")) {
            pattern = pattern.substring(2).trim();
            if (pattern.contains(":")) {
                String[] matchStrings = pattern.split(":");
                result = navigationPeakList.matchPeaks(matchStrings, true, true);
            } else {
                String[] matchStrings = pattern.split(",");
                result = navigationPeakList.matchPeaks(matchStrings, true, false);
            }
        } else {
            if (pattern.contains(":")) {
                if (pattern.charAt(0) == ':') {
                    pattern = " " + pattern;
                }
                if (pattern.charAt(pattern.length() - 1) == ':') {
                    pattern = pattern + " ";
                }

                String[] matchStrings = pattern.split(":");
                result = navigationPeakList.matchPeaks(matchStrings, false, true);
            } else {
                if (pattern.charAt(0) == ',') {
                    pattern = " " + pattern;
                }
                if (pattern.charAt(pattern.length() - 1) == ',') {
                    pattern = pattern + " ";
                }
                String[] matchStrings = pattern.split(",");
                result = navigationPeakList.matchPeaks(matchStrings, false, false);
            }
        }
        return result;
    }

    public void gotoPeakId(TextField idField) {
        String idString = idField.getText().trim();
        if (useSpinSystem) {
            try {
                int id = Integer.parseInt(idString);
                var optSys = runAbout.getSpinSystems().find(id);
                if (optSys.isPresent()) {
                    currentSpinSystem = optSys.get();
                    gotoSpinSystems();
                }
            } catch (NumberFormatException nfE) {
                GUIUtils.warn("SpinSystem", "Value not integer");
            }
        } else {
            if (navigationPeakList != null) {
                matchPeaks.clear();
                int id = Integer.MIN_VALUE;
                if (!idString.isEmpty()) {
                    try {
                        id = Integer.parseInt(idString);
                    } catch (NumberFormatException nfE) {
                        List<Peak> peaks = matchPeaks(idString);
                        if (!peaks.isEmpty()) {
                            setPeak(peaks.get(0));
                            matchPeaks.addAll(peaks);
                            matchIndex = 0;
                        } else {
                            idField.setText("");
                        }
                    }
                    if (id != Integer.MIN_VALUE) {
                        if (id < 0) {
                            id = 0;
                        } else if (id >= navigationPeakList.size()) {
                            id = navigationPeakList.size() - 1;
                        }
                        Peak peak = navigationPeakList.getPeakByID(id);
                        setPeak(peak);
                    }
                }
            }
        }
    }

    void gotoNextMatch(int dir) {
        if (!matchPeaks.isEmpty()) {
            matchIndex += dir;
            if (matchIndex >= matchPeaks.size()) {
                matchIndex = 0;
            } else if (matchIndex < 0) {
                matchIndex = matchPeaks.size() - 1;
            }
            Peak peak = matchPeaks.get(matchIndex);
            setPeak(peak);
        }
    }

    void setDeleteStatus(ToggleButton button) {
        if (currentPeak != null) {
            if (button.isSelected()) {
                currentPeak.setStatus(-1);
            } else {
                currentPeak.setStatus(0);
            }
        }
        updateDeleteStatus();
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        if (peakEvent.getSource() instanceof PeakList) {
            if ((peakEvent instanceof PeakListEvent) || (peakEvent instanceof PeakCountEvent)) {
                peakTableView.refresh();
            }
        }
    }

    void setupArrangements() {
        runAboutArrangements.getArrangements().forEach((name, arrangment) -> {
            MenuItem item = new MenuItem(name);
            arrangeMenuButton.getItems().add(item);
            item.setOnAction(e -> genWin(name));
        });
    }

    List<List<String>> getAtomsFromPatterns(List<String> patElems) {
        List<List<String>> allAtomPats = new ArrayList<>();
        for (String patElem : patElems) {
            String pattern = patElem.trim();
            String[] resAtoms = pattern.split("\\.");
            String[] atomPats = resAtoms[1].split(",");
            List<String> atomPatList = new ArrayList<>();
            allAtomPats.add(atomPatList);
            for (String atomPat : atomPats) {
                if (atomPat.endsWith("-") || atomPat.endsWith("+")) {
                    int len = atomPat.length();
                    atomPat = atomPat.substring(0, len - 1);
                }
                atomPatList.add(atomPat.toUpperCase());
            }
        }
        return allAtomPats;
    }

    Optional<String> getDatasetName(Map<String, String> typeMap, String typeName) {
        Optional<String> dName = Optional.empty();
        if (typeMap.containsKey(typeName)) {
            dName = Optional.of(typeMap.get(typeName));
        }
        return dName;
    }

    Double[] getDimWidth(PeakList peakList, Dataset dataset, List<String> dimNames, int[] iDims, List<String> widthTypes,
                         String row) {
        Double[] dimWidths = new Double[dataset.getNDim()];
        for (int i = 0; i < dimNames.size(); i++) {
            Double width;
            String widthType = widthTypes.get(i);
            int iDim = iDims[i];
            String dataDimName = dataset.getLabel(iDim);
            if (widthType.equals(("peak"))) {
                SpectralDim sDim = peakList.getSpectralDim(dataDimName);
                if (sDim != null) {
                    width = widthMap.get(Nuclei.findNuclei(sDim.getNucleus())).get();
//                    var widthStats = peakList.widthStatsPPM(sDim.getIndex());
//                    width = 10.0 * widthStats.getAverage();
                } else {
                    width = null;
                }
            } else if (widthType.equals(("plane"))) {
                width = 0.0;
            } else {
                width = null;
                if (i == 1) {
                    double max = dataset.pointToPPM(iDim, 0);
                    double min = dataset.pointToPPM(iDim, dataset.getSizeReal(iDim));
                    double[] plotLim = plotLimits.computeIfAbsent(row, key ->
                            new double[]{min, max}
                    );
                    plotLim[0] = Math.min(plotLim[0], min);
                    plotLim[1] = Math.max(plotLim[1], max);
                }
            }
            dimWidths[i] = width;

        }
        return dimWidths;
    }

    void setupRunAbout() {
        if (!runAbout.isActive() ||
                GUIUtils.affirm("Peak lists already setup, Setup again?")) {
            var peakLists = peakTableView.getItems().stream().
                    filter(PeakListSelection::getActive).
                    map(p -> p.peakList).toList();
            if (peakLists.isEmpty()) {
                GUIUtils.warn("RunAbout", "No peak lists selected");
            } else {
                runAbout.setPeakLists(peakLists);
                runAbout.setRefList(refListObj.get());
                registerPeakLists();
                peakTableView.refresh();
            }
        }
    }

    void loadArrangements() {
        try {
            FileChooser fileChooser = new FileChooser();
            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                runAboutArrangements = RunAboutYamlReader.loadYaml(file);
                setupArrangements();
            }
        } catch (IOException e) {
            GUIUtils.warn("RunAbout:load yaml", e.getMessage());
        }
    }

    void autoSetTolerances() {
        if (runAbout.isActive()) {
            runAbout.autoSetTolerance(1.0);
            peakTableView.refresh();
        }
    }

    void setDefaultTolerances() {
        if (runAbout.isActive()) {
            runAbout.setDefaultTolerances();
            peakTableView.refresh();
        }
    }

    void setTolerances() {
        if (runAbout.isActive()) {
            runAbout.setTolerances(toleranceMap);
            peakTableView.refresh();
        }
    }

    void addLists() {
        if (!runAbout.isActive()) {
            GUIUtils.warn("RunAbout", "Can't add more lists before setup");
            return;
        }
        var currentLists = runAbout.getPeakLists();
        var peakLists = peakTableView.getItems().stream().
                filter(PeakListSelection::getActive).
                map(p -> p.peakList).
                filter(p -> !currentLists.contains(p)).toList();
        runAbout.addLists(peakLists);
        clusterStatus.refresh();
        genWin(currentArrangement);
        gotoSpinSystems();
        registerPeakLists();
    }

    void showRefChart() {
        if (runAbout.isActive()) {
            refController = AnalystApp.getFXMLControllerManager().newController();
            PeakList refList = runAbout.getPeakLists().get(0);
            String datasetName = refList.getDatasetName();
            Dataset dataset = Dataset.getDataset(datasetName);
            if (dataset != null) {
                PolyChart chart = refController.getActiveChart();
                chart.updateDatasetsByNames(List.of(dataset.getName()));
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                int[] iDims = runAbout.getIDims(dataset, refList, refList.getExperimentType(), List.of("H", "N"));
                dataAttr.setDims(iDims);
                List<String> peakLists = Collections.singletonList(refList.getName());
                chart.updatePeakListsByName(peakLists);
            }
        }
    }

    void refreshRefChart(Peak peak) {
        if (refController != null) {
            refController.refreshPeakView(peak);
            for (var chart : refController.getCharts()) {
                chart.clearAnnotations();
                var dataAttr = chart.getDatasetAttributes().get(0);
                for (int iDim = 0; iDim < 2; iDim++) {
                    String dataLabel = dataAttr.getLabel(iDim);
                    PeakDim peakDim = peak.getPeakDim(dataLabel);
                    if (peakDim != null) {
                        double ppm = peakDim.getChemShiftValue();
                        AnnoSimpleLine annoSimpleLine = iDim == 0 ?
                                new AnnoSimpleLine(ppm, 0.0, ppm, 1.0, CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.FRACTION) :
                                new AnnoSimpleLine(0.0, ppm, 1.0, ppm, CanvasAnnotation.POSTYPE.FRACTION, CanvasAnnotation.POSTYPE.WORLD);
                        var color = Color.BLUEVIOLET;
                        annoSimpleLine.setStroke(color);
                        chart.addAnnotation(annoSimpleLine);
                    }
                }
                chart.refresh();
            }
        }
    }

    void registerPeakLists() {
        for (var peakList : PeakList.peakLists()) {
            peakList.registerPeakListChangeListener(this);
            peakList.registerPeakCountChangeListener(this);
        }
    }

    void unregisterPeakLists() {
        for (var peakList : PeakList.peakLists()) {
            peakList.removePeakListChangeListener(this);
            peakList.removePeakCountChangeListener(this);
        }
    }

    boolean notArranged() {
        if (resOffsets == null) {
            GUIUtils.warn("RunAbout", "Please select an arrangment first");
            return true;
        } else {
            return false;
        }
    }


    public void genWin(String arrangeName) {
        if (runAbout.isActive()) {
            currentArrangement = arrangeName;
            RunAboutArrangement arrangement = runAboutArrangements.getArrangements().get(arrangeName);
            List<String> rows = arrangement.getRows();
            List<RunAboutDim> cols = arrangement.getColumnArrangement();
            int nCharts = rows.size() * cols.size();
            controller.setNCharts(nCharts);
            controller.arrange(rows.size());
            List<PolyChart> charts = controller.getCharts();
            widths = new Double[nCharts][];
            plotLimits = new HashMap<>();
            rowMap = new HashMap<>();
            minOffset = 0;
            resOffsets = new int[cols.size()];
            intraResidue = new boolean[cols.size()];
            int iCol = 0;

            for (RunAboutDim col : cols) {
                String dir = col.getDir();
                char resChar = dir.charAt(0);
                int del = resChar - 'i';
                intraResidue[iCol] = !dir.endsWith("-1");
                resOffsets[iCol++] = del;
                minOffset = Math.min(del, minOffset);
            }
            int iChart = 0;
            winPatterns.clear();
            for (String row : rows) {
                for (RunAboutDim col : cols) {
                    PolyChart chart = charts.get(iChart);
                    chart.clearDataAndPeaks();
                    String dir = col.getDir();
                    List<String> widthTypes = col.getWidths();

                    Optional<PeakList> peakListOptional = runAbout.getPeakListForCell(row, dir);
                    rowMap.put(iChart, row);
                    final int jChart = iChart;
                    peakListOptional.ifPresent(peakList -> {
                        String datasetName = peakList.getDatasetName();
                        Dataset dataset = Dataset.getDataset(datasetName);
                        if (dataset != null) {
                            String typeName = peakList.getExperimentType();
                            chartTypes.put(chart, typeName);
                            dataset.setTitle(typeName);
                            String dName = dataset.getName();
                            List<String> datasets = Collections.singletonList(dName);
                            PolyChartManager.getInstance().setActiveChart(chart);
                            chart.updateDatasetsByNames(datasets);
                            List<String> dimNames = col.getDims();
                            DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                            int[] iDims = runAbout.getIDims(dataset, peakList, typeName, dimNames);
                            var sDims = runAbout.getPeakListDims(peakList, dataset, iDims);
                            widths[jChart] = getDimWidth(peakList, dataset, dimNames, iDims, widthTypes, row);
                            dataAttr.setDims(iDims);
                            List<String> peakLists = Collections.singletonList(peakList.getName());
                            chart.updatePeakListsByName(peakLists);
                            updateChartPeakMenu(chart);
                            winPatterns.put(chart, sDims.stream().map(SpectralDim::getPattern).toList());
                        }
                    });
                    iChart++;
                }
            }
            controller.setChartDisable(false);

            if (currentPeak != null) {
                drawWins(Collections.singletonList(currentPeak));
            } else {
                firstPeak(null);
            }

        }
    }

    void updateChartPeakMenu(PolyChart chart) {
        if ((chart.getPeakMenu() instanceof PeakMenu peakMenu) && (peakMenu.chartMenu.getItems().size() < 3)) {
            if (peakMenu.chartMenu.getItems().size() == 2) {
                peakMenu.chartMenu.getItems().remove(1);
            }
            Menu menuItem = new Menu("Pattern");
            peakMenu.chartMenu.getItems().add(menuItem);
            peakMenu.chartMenu.setOnShown(e -> updateChartPeakMenu(chart, peakMenu, menuItem));

        }
    }


    void updateChartPeakMenu(PolyChart chart, PeakMenu peakMenu, Menu menu) {
        Peak peak = peakMenu.getPeak();
        menu.getItems().clear();
        int[] peakDims = chart.getPeakListAttributes().get(0).getPeakDim();
        int yDim = peakDims[1];
        if (peak != null) {
            PeakList peakList = peak.getPeakList();
            SpectralDim sDim = peakList.getSpectralDim(yDim);
            var pats = RunAbout.getPatterns(sDim);
            if (pats.size() > 1) {
                for (String pattern : pats) {
                    PeakDim peakDim = peak.getPeakDim(sDim.getIndex());
                    String patternChoice = sDim.getDimName() + " " + pattern.toLowerCase();
                    String currentPattern = peakDim.getUser();
                    String testPattern = pattern.endsWith("-") || pattern.endsWith("+")
                            ? pattern.substring(0, pattern.length() - 1) : pattern;

                    if (testPattern.equalsIgnoreCase(currentPattern)) {
                        patternChoice += " <<";
                    }
                    MenuItem menuItem = new MenuItem(patternChoice);
                    menu.getItems().add(menuItem);
                    menuItem.setOnAction(e -> updatePeakPattern(peak, sDim, pattern.toLowerCase()));
                }
            }
        }
    }

    void updatePeakPattern(Peak peak, SpectralDim sDim, String pattern) {
        if (pattern.endsWith("-")) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }
        peak.getPeakDim(sDim.getIndex()).setUser(pattern);
        currentSpinSystem.updateSpinSystem();
        currentSpinSystem.compare();
        gotoSpinSystems();
    }

    void drawWins(List<Peak> peaks) {
        if (notArranged()) {
            return;
        }
        List<PolyChart> charts = controller.getCharts();
        int iChart = 0;
        for (PolyChart chart : charts) {
            chart.getChartProperties().setTitles(true);
            if (!chart.getPeakListAttributes().isEmpty()) {
                PeakListAttributes peakAttr = chart.getPeakListAttributes().get(0);
                peakAttr.setLabelType(PeakDisplayParameters.LabelTypes.Number);
            }
            int iCol = iChart % resOffsets.length;
            iCol = iCol >= peaks.size() ? 0 : iCol;
            Peak peak = peaks.get(iCol);
            chart.clearAnnotations();
            if (peak != null && !chart.getDatasetAttributes().isEmpty()) {
                refreshChart(chart, iChart, peak, true);
            }
            iChart++;
        }
    }

    void drawSpinSystems(List<SpinSystem> spinSystems, boolean useBest) {
        updateSeqCanvas();
        if (notArranged()) {
            return;
        }
        List<PolyChart> charts = controller.getCharts();
        int iChart = 0;

        spinStatus.showScore(spinSystems, useBest);

        for (PolyChart chart : charts) {
            chart.getChartProperties().setTopBorderSize(25);
            chart.getChartProperties().setTitles(true);
            int iCol = iChart % resOffsets.length;
            iCol = iCol >= spinSystems.size() ? 0 : iCol;
            SpinSystem spinSystem = spinSystems.get(iCol);
            if (spinSystem == null) {
                iChart++;
                continue;
            }
            Peak peak = spinSystem.getRootPeak();
            chart.clearAnnotations();
            if (peak != null && !chart.getDatasetAttributes().isEmpty()) {
                refreshChart(chart, iChart, peak, false);
                PeakList currentList = null;
                if (!chart.getPeakListAttributes().isEmpty()) {
                    PeakListAttributes peakAttr = chart.getPeakListAttributes().get(0);
                    peakAttr.setLabelType(PeakDisplayParameters.LabelTypes.Cluster);
                    currentList = peakAttr.getPeakList();
                }
                if (winPatterns.containsKey(chart)) {
                    drawPeakTypeAnnotations(chart, spinSystem, iCol);
                }
                if (currentList != null) {
                    drawAnno(chart, currentList, spinSystem);
                }
                chart.refresh();
            }
            iChart++;
        }
    }

    void drawPeakTypeAnnotations(PolyChart chart, SpinSystem spinSystem, int iCol) {
        DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
        List<List<String>> atomPatterns = getAtomsFromPatterns(winPatterns.get(chart));
        for (PeakMatch peakMatch : spinSystem.peakMatches()) {
            PeakDim peakDim = peakMatch.getPeak().getPeakDim(dataAttr.getLabel(1));
            if (peakDim != null) {
                int iDim = peakDim.getSpectralDim();
                SpinSystem.AtomEnum atomEnum = peakMatch.getIndex(iDim);
                if (atomEnum == null) {
                    continue;
                }
                String aName = atomEnum.name().toUpperCase();
                if ((iDim >= atomPatterns.size()) || !atomPatterns.get(iDim).contains(aName)) {
                    continue;

                }
                boolean isIntra = peakMatch.getIntraResidue(iDim);
                final double f1;
                final double f2;
                Color color;
                if (intraResidue[iCol]) {
                    if (isIntra) {
                        color = Color.BLUE;
                        f1 = 0.5;
                        f2 = 1.0;
                    } else {
                        color = Color.GREEN;
                        f1 = 0.0;
                        f2 = 0.5;
                    }

                } else {
                    if (isIntra) {
                        continue;
                    } else {
                        color = Color.GREEN;
                        f1 = 0.0;
                        f2 = 1.0;
                    }
                }
                if (isIntra && !intraResidue[iCol]) {
                    continue;
                }

                Optional<Double> ppmOpt = spinSystem.getValue(isIntra ? 1 : 0, atomEnum);
                ppmOpt.ifPresent(ppm -> {
                    AnnoSimpleLine annoSimpleLine = new AnnoSimpleLine(f1, ppm, f2, ppm, CanvasAnnotation.POSTYPE.FRACTION, CanvasAnnotation.POSTYPE.WORLD);
                    annoSimpleLine.setStroke(color);
                    chart.addAnnotation(annoSimpleLine);
                });
            }
        }
    }

    void drawAnno(PolyChart chart, PeakList currentList, SpinSystem spinSystem) {
        DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
        Font font = Font.font(12.0);
        double textWidth = GUIUtils.getTextWidth("CB", font);
        String typeName = chartTypes.get(chart);
        int nPeaks = spinSystem.getNPeaksWithList(currentList);
        if (typeName != null) {
            int nExpected = runAbout.getTypeCount(typeName);
            TypeInfo typeInfo = runAbout.getTypeInfo(typeName);
            var sDim = currentList.getSpectralDim(dataAttr.getLabel(1));
            if (sDim != null) {
                int dim = sDim.getIndex();
                List<AtomPresent> typesPresent = spinSystem.getTypesPresent(typeInfo, currentList, dim);
                double x = 100.0;
                double delta = textWidth + 5.0;
                for (AtomPresent typePresent : typesPresent) {
                    String text = typePresent.getName();
                    if (!typePresent.isIntraResidue()) {
                        text = text.toLowerCase();
                    }
                    AnnoText annoText = new AnnoText(x, -8, delta, text, 12.0, CanvasAnnotation.POSTYPE.PIXEL, CanvasAnnotation.POSTYPE.PIXEL);
                    annoText.setFont(font);
                    chart.addAnnotation(annoText);
                    Color presentColor = typePresent.isPresent() ? Color.LIGHTGREEN : Color.RED;
                    AnnoSimpleLine annoSimpleLine2 = new AnnoSimpleLine(x, -2, x + textWidth, -2, CanvasAnnotation.POSTYPE.PIXEL, CanvasAnnotation.POSTYPE.PIXEL);
                    annoSimpleLine2.setStroke(presentColor);
                    annoSimpleLine2.setLineWidth(6);
                    chart.addAnnotation(annoSimpleLine2);
                    x += delta;
                }
                AnnoText annoText = new AnnoText(x, -8, textWidth, String.valueOf(nPeaks - nExpected), 12.0, CanvasAnnotation.POSTYPE.PIXEL, CanvasAnnotation.POSTYPE.PIXEL);
                annoText.setFont(font);
                chart.addAnnotation(annoText);
            }
        }
    }

    void refreshChart(PolyChart chart, int iChart, Peak peak, boolean annoHorizontal) {
        DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
        int cDim = chart.getNDim();
        int aDim = dataAttr.nDim;
        Double[] ppms = new Double[cDim];
        for (int i = 0; i < aDim; i++) {
            PeakDim peakDim = peak.getPeakDim(dataAttr.getLabel(i));
            if ((widths[iChart] != null) && (peakDim != null)) {
                ppms[i] = (double) peakDim.getChemShiftValue();
                if (widths[iChart][i] == null) {
                    String row = rowMap.get(iChart);
                    double[] limits = plotLimits.get(row);
                    if (unifyLimits() && (i == 1) && (limits != null)) {
                        chart.getAxes().setMinMax(i, limits[0], limits[1]);
                    } else {
                        chart.full(i);
                    }
                } else {
                    double pos;
                    if (chart.getAxes().getMode(i) == PPM) {
                        pos = ppms[i];
                    } else {
                        int dDim = dataAttr.getDim(i);
                        pos = dataAttr.getDataset().ppmToDPoint(dDim, ppms[i]);
                    }
                    chart.moveTo(i, pos, widths[iChart][i]);
                }
                if (i == 0) {
                    AnnoSimpleLine annoSimpleLine = new AnnoSimpleLine(ppms[0], 0.0, ppms[0], 1.0, CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.
                            FRACTION);
                    annoSimpleLine.setStroke(Color.BLUEVIOLET);
                    annoSimpleLine.setLineWidth(0.0);
                    chart.addAnnotation(annoSimpleLine);
                } else if (annoHorizontal && (i == 1)) {
                    AnnoSimpleLine annoSimpleLine = new AnnoSimpleLine(0.0, ppms[1], 1.0, ppms[1], CanvasAnnotation.POSTYPE.FRACTION, CanvasAnnotation.POSTYPE.
                            WORLD);
                    annoSimpleLine.setStroke(Color.BLUEVIOLET);
                    annoSimpleLine.setLineWidth(0.0);
                    chart.addAnnotation(annoSimpleLine);
                }
            } else {
                chart.full(i);
            }
        }
        chart.refresh();
    }

    void alignCenters() {
        List<String> dimNames = new ArrayList<>();
        for (var sDim : refListObj.get().getSpectralDims()) {
            if (sDim.getPattern().equalsIgnoreCase("i.h") || sDim.getPattern().equalsIgnoreCase("i.n")) {
                dimNames.add(sDim.getDimName());
            }
        }
        if (dimNames.size() != 2) {
            GUIUtils.warn("Alignment", "Can't find H and N dims");
        } else {
            List<PeakList> movingLists = runAbout.getPeakLists();
            PeakListAlign.alignCenters(refListObj.get(), dimNames, movingLists);
        }
    }

    void peakDeleteAction(PeakDeleteEvent event) {
        var modifiedSpinSystems = new HashSet<SpinSystem>();
        for (Peak peak : event.getPeaks()) {
            runAbout.getSpinSystems().findSpinSystem(peak).ifPresent(spinSys -> {
                spinSys.removePeak(peak);
                modifiedSpinSystems.add(spinSys);
            });
        }
        for (var spinSys : modifiedSpinSystems) {
            if (spinSys.userFieldsSet()) {
                spinSys.calcCombinations(false);
                if (!spinSys.confirmed(false) && !spinSys.confirmed(true)) {
                    spinSys.compare();
                }
            }
        }
    }

    private void pickedPeakAction(Peak peak) {
        if (useSpinSystem) {
            SpinSystems spinSystems = runAbout.getSpinSystems();
            var spinSys = currentSpinSystem;
            PeakList rootList = spinSys.getRootPeak().getPeakList();
            if (peak.getPeakList() == rootList) {
                if (GUIUtils.affirm("Add new spinsystem")) {
                    SpinSystem spinSystem = new SpinSystem(peak, spinSystems);
                    spinSystems.add(spinSystem);
                    currentSpinSystem = spinSystem;
                    gotoSpinSystems();
                }
            } else {
                if (GUIUtils.affirm("Add to cluster " + currentSpinSystem.getId())) {
                    spinSystems.addPeak(spinSys, peak);
                    int[] aMatch = SpinSystems.matchDims(rootList, peak.getPeakList());
                    for (int iDim = 0; iDim < aMatch.length; iDim++) {
                        if (aMatch[iDim] >= 0) {
                            PeakList.linkPeakDims(spinSys.getRootPeak().getPeakDim(iDim), peak.getPeakDim(aMatch[iDim]));
                        }
                    }
                    if (spinSys.userFieldsSet()) {
                        spinSys.calcCombinations(false);
                        if (!spinSys.confirmed(false) && !spinSys.confirmed(true)) {
                            spinSys.compare();
                        }
                    }
                    gotoSpinSystems();
                }
            }
        }
    }

    void splitSystem() {
        var spinSys = currentSpinSystem;
        spinSys.split();
        controller.draw();
    }

    void moveToThisCluster() {
        SpinSystems spinSystems = runAbout.getSpinSystems();
        var spinSys = currentSpinSystem;
        for (var chart : controller.getCharts()) {
            var selPeaks = chart.getSelectedPeaks();
            for (Peak peak : selPeaks) {
                PeakList.unLinkPeak(peak);
                var spinSysOpt = spinSystems.findSpinSystem(peak);
                spinSysOpt.ifPresent(s -> s.removePeak(peak));
                spinSystems.addPeak(spinSys, peak);
            }
        }
        controller.getCharts().get(0).getSelectedPeaks();
    }

    void trimSystem() {
        var spinSys = currentSpinSystem;
        runAbout.trim(spinSys);
        gotoSpinSystems();
        updateClusterCanvas();
    }

    void trimSystems() {
        if (GUIUtils.affirm("Trim all systems")) {
            runAbout.getSpinSystems().trimAll();
            gotoSpinSystems();
            updateClusterCanvas();
        }
    }

    void extendSystem() {
        SpinSystem.extend(currentSpinSystem, -50.0);
        gotoSpinSystems();
        updateClusterCanvas();
    }

    void extendAllSystems() {
        runAbout.getSpinSystems().extendAll(-50.0);
        gotoSpinSystems();
        updateClusterCanvas();
    }

    void freezeSystem() {
        currentSpinSystem.getFragment().ifPresent(frag -> {
            if (frag.getResSeqScore() != null) {
                frag.freezeFragment(frag.getResSeqScore());
            }
            spinStatus.updateFragment(currentSpinSystem);
        });
        updateClusterCanvas();
    }

    void freezeSystemAtPosition(Residue residue) {
        currentSpinSystem.getFragment().ifPresent(frag -> {
            frag.setResidueSeqScoreAtPosition(currentSpinSystem, residue);
            if (frag.getResSeqScore() != null) {
                frag.freezeFragment(frag.getResSeqScore());
            }
            spinStatus.updateFragment(currentSpinSystem);
        });
        updateClusterCanvas();
    }

    void thawSystem() {
        currentSpinSystem.getFragment().ifPresent(frag -> {
            if (frag.getResSeqScore() != null) {
                frag.thawFragment();
            }
            spinStatus.updateFragment(currentSpinSystem);
        });
        updateClusterCanvas();
    }

    void thawAllSystems() {
        if (GUIUtils.affirm("Thaw all fragments, this will unassign atoms and peaks")) {
            runAbout.getSpinSystems().thawAll();
            spinStatus.updateFragment(currentSpinSystem);
            gotoSpinSystems();
            updateClusterCanvas();
        }
    }
}
