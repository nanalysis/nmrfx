/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.converter.DoubleStringConverter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.util.converter.FloatStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakEvent;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakListener;

/**
 *
 * @author johnsonb
 */
public class PeakAttrController implements Initializable, PeakListener {

    static final DecimalFormat formatter = new DecimalFormat();

    private Stage stage;
    @FXML
    private ToolBar menuBar;
    @FXML
    private ToolBar peakNavigatorToolBar;
    @FXML
    private MenuButton peakListMenuButton;
    @FXML
    private TextField peakIdField;
    @FXML
    private TableView<PeakDim> peakTableView;
    @FXML
    private TextField intensityField;
    @FXML
    private TextField volumeField;
    @FXML
    private TextField commentField;

    @FXML
    Slider scaleSlider;

    PeakList peakList;
    Peak currentPeak;
    ToggleButton deleteButton;
    static Background deleteBackground = new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = null;
    Background defaultCellBackground = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initMenuBar();
        initPeakNavigator();
        initTable();
        peakIdField.setOnKeyReleased(kE -> {
            if (kE.getCode() == KeyCode.ENTER) {
                gotoPeakId();
            }
        });
        updatePeakListMenu();
        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            updatePeakListMenu();
        };

        PeakList.peakListTable.addListener(mapChangeListener);
//        peakListMenuButton.setOnMousePressed(e -> {
//            updatePeakListMenu();
//            peakListMenuButton.show();
//        });
//        ChangeListener<Dataset> listener = new ChangeListener<Dataset>() {
//            @Override
//            public void changed(ObservableValue<? extends Dataset> observable, Dataset oldValue, Dataset newValue) {
//                System.out.println("datasets changed");
//                PolyChart chart = PolyChart.activeChart;
//                if (chart != null) {
//                    setPeak(chart);
//                }
//            }
//        };
    }

    public Stage getStage() {
        return stage;
    }

    public static PeakAttrController create() {
        FXMLLoader loader = new FXMLLoader(PeakAttrController.class.getResource("/fxml/PeakAttrScene.fxml"));
        PeakAttrController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<PeakAttrController>getController();
            controller.stage = stage;
            stage.setTitle("Peak Attributes");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (String peakListName : PeakList.peakListTable.keySet()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                setPeakList(peakListName);
            });
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    public void setPeakList(String listName) {
        peakList = PeakList.get(listName);
        setPeakList(peakList);
    }

    public void setPeakList() {
        peakList = PeakList.get(0);
        setPeakList(peakList);
    }

    public void setPeakList(PeakList newPeakList) {
        peakList = newPeakList;
        if (peakList != null) {
            currentPeak = peakList.getPeak(0);
            stage.setTitle(peakList.getName());
            peakList.registerListener(this);
        } else {
            stage.setTitle("Peak Inspector");
        }
        updatePeakTableView();
    }

    public void updatePeakTableView() {
        if (peakTableView == null) {
            System.out.println("null table");
            return;
        }
        if (defaultBackground == null) {
            defaultBackground = peakIdField.getBackground();
        }

        boolean clearIt = true;
        if (peakList != null) {
            if (currentPeak != null) {
                ObservableList<PeakDim> peakDimList = FXCollections.observableArrayList();
                for (PeakDim peakDim : currentPeak.getPeakDims()) {
                    peakDimList.add(peakDim);
                }
                peakTableView.setItems(peakDimList);
                intensityField.setText(String.valueOf(currentPeak.getIntensity()));
                volumeField.setText(String.valueOf(currentPeak.getVolume1()));
                commentField.setText(currentPeak.getComment());
                if (currentPeak.getStatus() < 0) {
                    deleteButton.setSelected(true);
                    peakIdField.setBackground(deleteBackground);
                } else {
                    deleteButton.setSelected(false);
                    peakIdField.setBackground(defaultBackground);
                }
                clearIt = false;
            }
        }
        if (clearIt) {
            clearInsepctor();
        }
        setPeakIdField();
    }

    private void clearInsepctor() {
        peakTableView.getItems().clear();
        intensityField.setText("");
        volumeField.setText("");
        commentField.setText("");

    }

    @FXML
    public void previousPeak(ActionEvent event) {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex--;
            if (peakIndex < 0) {
                peakIndex = 0;
            }
            Peak peak = peakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    @FXML
    public void firstPeak(ActionEvent event) {
        Peak peak = peakList.getPeak(0);
        setPeak(peak);
    }

    @FXML
    public void nextPeak(ActionEvent event) {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex++;
            if (peakIndex >= peakList.size()) {
                peakIndex = peakList.size() - 1;
            }
            Peak peak = peakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    @FXML
    public void lastPeak(ActionEvent event) {
        int peakIndex = peakList.size() - 1;
        Peak peak = peakList.getPeak(peakIndex);
        setPeak(peak);
    }

    public void gotoPeakId() {
        if (peakList != null) {
            int id = -1;
            String idString = peakIdField.getText().trim();
            if (idString.length() != 0) {
                try {
                    id = Integer.parseInt(idString);
                } catch (NumberFormatException nfE) {
                    peakIdField.setText("");
                }
                if (id != -1) {
                    Peak peak = peakList.getPeakByID(id);
                    setPeak(peak);
                }
            }
        }
    }

    private void setPeakIdField() {
        if (currentPeak == null) {
            peakIdField.setText("");
        } else {
            peakIdField.setText(String.valueOf(currentPeak.getIdNum()));
        }

    }

    public void setPeak(Peak peak) {
        currentPeak = peak;
        if (peakList != peak.getPeakList()) {
            peakList = peak.getPeakList();
            stage.setTitle(peakList.getName());
        }
        setPeakIdField();
        updatePeakTableView();
    }

    void initPeakNavigator() {
        peakListMenuButton = new MenuButton("List");
        peakIdField = new TextField();
        peakIdField.setMinWidth(50);
        peakIdField.setMaxWidth(50);

        String iconSize = "12px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastPeak(e));
        buttons.add(bButton);
        deleteButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.BAN, fontSize, iconSize, ContentDisplay.GRAPHIC_ONLY);
        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(e -> e.consume());
        deleteButton.setOnAction(e -> setDeleteStatus(deleteButton));

        for (Button button : buttons) {
            button.getStyleClass().add("toolButton");
        }
        peakNavigatorToolBar.getItems().add(peakListMenuButton);
        peakNavigatorToolBar.getItems().addAll(buttons);
        peakNavigatorToolBar.getItems().add(peakIdField);
        peakNavigatorToolBar.getItems().add(deleteButton);

    }

    void initMenuBar() {
        MenuButton fileMenu = new MenuButton("File");
        MenuItem saveList = new MenuItem("Save...");
        saveList.setOnAction(e -> saveList());
        fileMenu.getItems().add(saveList);

        MenuItem readListItem = new MenuItem("Open...");
        readListItem.setOnAction(e -> readList());
        fileMenu.getItems().add(readListItem);

        menuBar.getItems().add(fileMenu);

        MenuButton editMenu = new MenuButton("Edit");

        MenuItem compressMenuItem = new MenuItem("Compress");
        compressMenuItem.setOnAction(e -> compressPeakList());
        editMenu.getItems().add(compressMenuItem);

        MenuItem degapMenuItem = new MenuItem("Degap");
        degapMenuItem.setOnAction(e -> renumberPeakList());
        editMenu.getItems().add(degapMenuItem);

        MenuItem compressAndDegapMenuItem = new MenuItem("Compress and Degap");
        compressAndDegapMenuItem.setOnAction(e -> compressAndDegapPeakList());
        editMenu.getItems().add(compressAndDegapMenuItem);

        MenuItem deleteMenuItem = new MenuItem("Delete List");
        deleteMenuItem.setOnAction(e -> deletePeakList());
        editMenu.getItems().add(deleteMenuItem);

        MenuItem unlinkPeakMenuItem = new MenuItem("Unlink List");
        unlinkPeakMenuItem.setOnAction(e -> unLinkPeakList());
        editMenu.getItems().add(unlinkPeakMenuItem);

        MenuItem duplicateMenuItem = new MenuItem("Duplicate");
        duplicateMenuItem.setOnAction(e -> duplicatePeakList());
        editMenu.getItems().add(duplicateMenuItem);

        MenuItem clusterMenuItem = new MenuItem("Cluster");
        clusterMenuItem.setOnAction(e -> clusterPeakList());
        editMenu.getItems().add(clusterMenuItem);

        menuBar.getItems().add(editMenu);

        MenuButton measureMenu = new MenuButton("Measure");
        MenuItem measureIntensityItem = new MenuItem("Intensities");
        measureIntensityItem.setOnAction(e -> measureIntensities());
        measureMenu.getItems().add(measureIntensityItem);

        MenuItem measureVolumeItem = new MenuItem("Volumes");
        measureVolumeItem.setOnAction(e -> measureVolumes());
        measureMenu.getItems().add(measureVolumeItem);

        MenuItem measureEVolumeItem = new MenuItem("EVolumes");
        measureEVolumeItem.setOnAction(e -> measureEVolumes());
        measureMenu.getItems().add(measureEVolumeItem);

        menuBar.getItems().add(measureMenu);
//        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.REFRESH, "Refresh", iconSize, fontSize, ContentDisplay.TOP);
//        bButton.setOnAction(e -> refreshAction());
//        buttons.add(bButton);
//        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.EXPAND, "Full", iconSize, fontSize, ContentDisplay.TOP);
//        bButton.setOnAction(e -> currentPeak.full());
//        buttons.add(bButton);
//        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH, "Expand", iconSize, fontSize, ContentDisplay.TOP);
//        bButton.setOnAction(e -> currentPeak.expand());
//        buttons.add(bButton);
//        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH_MINUS, "In", iconSize, fontSize, ContentDisplay.TOP);
//        bButton.setOnAction(e -> currentPeak.zoom(1.2));
//        buttons.add(bButton);
//        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH_PLUS, "Out", iconSize, fontSize, ContentDisplay.TOP);
//        bButton.setOnAction(e -> currentPeak.zoom(0.8));
//        buttons.add(bButton);
//        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROWS_V, "Auto", iconSize, fontSize, ContentDisplay.TOP);
//        bButton.setOnAction(e -> autoScale());
//        buttons.add(bButton);
//        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_UP, "Higher", iconSize, fontSize, ContentDisplay.TOP);
//        bButton.setOnAction(e -> adjustScale(0.8));
//        buttons.add(bButton);
//        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_DOWN, "Lower", iconSize, fontSize, ContentDisplay.TOP);
//        bButton.setOnAction(e -> adjustScale(1.2));
//        buttons.add(bButton);
//
//        for (Button button : buttons) {
//            button.getStyleClass().add("toolButton");
//        }

    }

    class FloatStringConverter2 extends FloatStringConverter {

        public Float fromString(String s) {
            Float v;
            try {
                v = Float.parseFloat(s);
            } catch (NumberFormatException nfE) {
                v = null;
            }
            return v;
        }

    }

    class TextFieldTableCellFloat extends TextFieldTableCell<PeakDim, Float> {

        public TextFieldTableCellFloat(StringConverter s) {
            super(s);
        }

        @Override
        public void updateItem(Float item, boolean empty) {
            super.updateItem(item, empty);
            if (item != null) {
                setText(String.valueOf(item));
            } else {
            }
        }
    };

    void initTable() {
        DoubleStringConverter dsConverter = new DoubleStringConverter();
        FloatStringConverter fsConverter = new FloatStringConverter2();

        IntegerStringConverter isConverter = new IntegerStringConverter();
        peakTableView.setEditable(true);
        TableColumn<PeakDim, String> dimNameCol = new TableColumn<>("Dim");
        dimNameCol.setCellValueFactory(new PropertyValueFactory("DimName"));
        dimNameCol.setEditable(false);

        TableColumn<PeakDim, String> labelCol = new TableColumn<>("Label");
        labelCol.setCellValueFactory(new PropertyValueFactory("Label"));
        labelCol.setCellFactory(TextFieldTableCell.forTableColumn());
        labelCol.setEditable(true);
        labelCol.setOnEditCommit((CellEditEvent<PeakDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setLabel(value == null ? "" : value);
        });

        TableColumn<PeakDim, Float> ppmCol = new TableColumn<>("PPM");
        ppmCol.setCellValueFactory(new PropertyValueFactory("ChemShift"));
        ppmCol.setCellFactory(tc -> new TextFieldTableCellFloat(fsConverter));
        ppmCol.setOnEditCommit(
                (CellEditEvent<PeakDim, Float> t) -> {
                    Float value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setChemShift(value);
                    }
                });

        ppmCol.setEditable(true);

        TableColumn<PeakDim, Float> widthCol = new TableColumn<>("Width");
        widthCol.setCellValueFactory(new PropertyValueFactory("LineWidthHz"));
        widthCol.setEditable(true);
        TableColumn<PeakDim, Float> boundsCol = new TableColumn<>("Bounds");
        boundsCol.setCellValueFactory(new PropertyValueFactory("BoundsHz"));
        boundsCol.setEditable(true);
        TableColumn<PeakDim, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory("User"));
        userCol.setEditable(true);

        peakTableView.getColumns().setAll(dimNameCol, labelCol, ppmCol, widthCol, boundsCol, userCol);

//        TableColumn<PeakDim, String> levelCol = new TableColumn<>("level");
//        levelCol.setCellValueFactory(new PropertyValueFactory("level"));
//        levelCol.setCellFactory(tc -> new TextFieldTableCell(dsConverter));
//
//        ContextMenu levelMenu = new ContextMenu();
//        MenuItem unifyLevelItem = new MenuItem("unify");
//        unifyLevelItem.setOnAction(e -> unifyLevel());
//        levelCol.setContextMenu(levelMenu);
//        levelMenu.getItems().addAll(unifyLevelItem);
//
//        TableColumn<PeakDim, String> offsetCol = new TableColumn<>("offset");
//        offsetCol.setCellValueFactory(new PropertyValueFactory("offset"));
//        offsetCol.setCellFactory(tc -> new TextFieldTableCell(dsConverter));
//
//        ContextMenu offsetMenu = new ContextMenu();
//        MenuItem unifyOffsetItem = new MenuItem("unify");
//        unifyLevelItem.setOnAction(e -> unifyOffset());
//        MenuItem rampOffsetItem = new MenuItem("ramp");
//        rampOffsetItem.setOnAction(e -> rampOffset());
//        offsetMenu.getItems().addAll(rampOffsetItem);
//        offsetCol.setContextMenu(offsetMenu);
//        offsetCol.setPrefWidth(50);
//
//        TableColumn<PeakDim, String> nLevelsCol = new TableColumn<>("nLvl");
//        nLevelsCol.setCellValueFactory(new PropertyValueFactory("nlevels"));
//        nLevelsCol.setCellFactory(tc -> new TextFieldTableCell(isConverter));
//        nLevelsCol.setPrefWidth(35);
//
//        TableColumn<PeakDim, String> clmCol = new TableColumn<>("clm");
//        clmCol.setCellValueFactory(new PropertyValueFactory("clm"));
//        clmCol.setCellFactory(tc -> new TextFieldTableCell(dsConverter));
//        clmCol.setPrefWidth(50);
//
//        TableColumn<PeakDim, Boolean> posDrawOnCol = new TableColumn<>("on");
//        posDrawOnCol.setCellValueFactory(new PropertyValueFactory("posDrawOn"));
//        posDrawOnCol.setCellFactory(tc -> new CheckBoxTableCell<>());
//        posDrawOnCol.setPrefWidth(25);
//        posDrawOnCol.setMaxWidth(25);
//        posDrawOnCol.setResizable(false);
//
//        ContextMenu posOnMenu = new ContextMenu();
//        MenuItem allPosOnItem = new MenuItem("all on");
//        allPosOnItem.setOnAction(e -> setDrawStatus(true, true));
//        MenuItem allPosOffItem = new MenuItem("all off");
//        allPosOffItem.setOnAction(e -> setDrawStatus(true, false));
//        posDrawOnCol.setContextMenu(posOnMenu);
//        posOnMenu.getItems().addAll(allPosOnItem, allPosOffItem);
//
//        TableColumn<PeakDim, String> posLineWidthCol = new TableColumn<>("width");
//        posLineWidthCol.setCellValueFactory(new PropertyValueFactory("posLineWidth"));
//        posLineWidthCol.setCellFactory(tc -> new TextFieldTableCell(dsConverter));
//        posLineWidthCol.setPrefWidth(50);
//
//        ContextMenu posWidthMenu = new ContextMenu();
//        MenuItem unifyPosWidthItem = new MenuItem("unify");
//        unifyPosWidthItem.setOnAction(e -> unifyWidth(true));
//        posLineWidthCol.setContextMenu(posWidthMenu);
//        posWidthMenu.getItems().addAll(unifyPosWidthItem);
//
//        TableColumn<PeakDim, Color> posColorCol = new TableColumn<>("color");
//        posColorCol.setPrefWidth(50);
//        posColorCol.setCellValueFactory(new PropertyValueFactory("posColor"));
//        posColorCol.setCellValueFactory(cellData -> cellData.getValue().posColorProperty());
//        posColorCol.setCellFactory(column -> {
//            return new TableCell<PeakDim, Color>() {
//                @Override
//
//                protected void updateItem(Color item, boolean empty) {
//                    super.updateItem(item, empty);
//                    if (item != null) {
//                        final ColorPicker cp = new ColorPicker();
//                        cp.setValue(item);
//                        setGraphic(cp);
//                        cp.setOnAction(new EventHandler<javafx.event.ActionEvent>() {
//                            public void
//                                    handle(javafx.event.ActionEvent t) {
//                                getTableView().edit(getTableRow().getIndex(), column);
//                                commitEdit(cp.getValue());
//                            }
//                        });
//                    }
//                }
//            };
//        });
//
//        posColorCol.setOnEditCommit(new EventHandler<CellEditEvent<PeakDim, Color>>() {
//            @Override
//            public void handle(CellEditEvent<PeakDim, Color> t) {
//                ((PeakDim) t.getTableView().getItems().get(t.getTablePosition().
//                        getRow())).setPosColor(t.getNewValue());
//            }
//        });
//        ContextMenu posColorMenu = new ContextMenu();
//        MenuItem unifyPosColorItem = new MenuItem("unify");
//        unifyPosColorItem.setOnAction(e -> unifyColor(true));
//        MenuItem interpPosColor = new MenuItem("interpolate");
//        interpPosColor.setOnAction(e -> interpolatePosColors());
//        posColorCol.setContextMenu(posColorMenu);
//        posColorMenu.getItems().addAll(unifyPosColorItem, interpPosColor);
//
//        TableColumn<PeakDim, Boolean> negDrawOnCol = new TableColumn<>("on");
//        negDrawOnCol.setCellValueFactory(new PropertyValueFactory("negDrawOn"));
//        negDrawOnCol.setCellFactory(tc -> new CheckBoxTableCell<>());
//        negDrawOnCol.setPrefWidth(25);
//        negDrawOnCol.setMaxWidth(25);
//        negDrawOnCol.setResizable(false);
//
//        ContextMenu negOnMenu = new ContextMenu();
//        MenuItem allNegOnItem = new MenuItem("all on");
//        allNegOnItem.setOnAction(e -> setDrawStatus(false, true));
//        MenuItem allNegOffItem = new MenuItem("all off");
//        allNegOffItem.setOnAction(e -> setDrawStatus(false, false));
//        negDrawOnCol.setContextMenu(negOnMenu);
//        negOnMenu.getItems().addAll(allNegOnItem, allNegOffItem);
//
//        TableColumn<PeakDim, Double> negLineWidthCol = new TableColumn<>("width");
//        negLineWidthCol.setCellValueFactory(new PropertyValueFactory("negLineWidth"));
//        negLineWidthCol.setPrefWidth(50);
//
//        ContextMenu negWidthMenu = new ContextMenu();
//        MenuItem unifyNegWidthItem = new MenuItem("unify");
//        unifyNegWidthItem.setOnAction(e -> unifyWidth(false));
//        negLineWidthCol.setContextMenu(negWidthMenu);
//        negWidthMenu.getItems().addAll(unifyNegWidthItem);
//
//        TableColumn<PeakDim, Color> negColorCol = new TableColumn<>("color");
//        negColorCol.setPrefWidth(50);
//        negColorCol.setCellValueFactory(cellData -> cellData.getValue().negColorProperty());
//        negColorCol.setCellFactory(column -> {
//            return new TableCell<PeakDim, Color>() {
//                @Override
//                protected void updateItem(Color item, boolean empty) {
//                    super.updateItem(item, empty);
//                    if (item != null) {
//                        final ColorPicker cp = new ColorPicker();
//                        cp.setValue(item);
//                        setGraphic(cp);
//                        cp.setOnAction(new EventHandler<javafx.event.ActionEvent>() {
//                            public void
//                                    handle(javafx.event.ActionEvent t) {
//                                getTableView().edit(getTableRow().getIndex(), column);
//                                commitEdit(cp.getValue());
//                            }
//                        });
//                    }
//                }
//            };
//        });
//
//        negColorCol.setOnEditCommit(new EventHandler<CellEditEvent<PeakDim, Color>>() {
//            @Override
//            public void handle(CellEditEvent<PeakDim, Color> t) {
//                ((PeakDim) t.getTableView().getItems().get(t.getTablePosition().
//                        getRow())).setNegColor(t.getNewValue());
//            }
//        });
//        ContextMenu negColorMenu = new ContextMenu();
//        MenuItem unifyNegColorItem = new MenuItem("unify");
//        unifyNegColorItem.setOnAction(e -> unifyColor(false));
//        MenuItem interpNegColorItem = new MenuItem("interpolate");
//        interpNegColorItem.setOnAction(e -> interpolateNegColors());
//        negColorCol.setContextMenu(negColorMenu);
//        negColorMenu.getItems().addAll(unifyNegColorItem, interpNegColorItem);
//
//        TableColumn positiveColumn = new TableColumn("Positive");
//        TableColumn negativeColumn = new TableColumn("Negative");
//        positiveColumn.getColumns().setAll(posDrawOnCol, posColorCol, posLineWidthCol);
//        negativeColumn.getColumns().setAll(negDrawOnCol, negColorCol, negLineWidthCol);
//        peakTableView.getColumns().setAll(dimNameCol, levelCol, offsetCol, nLevelsCol, clmCol, positiveColumn, negativeColumn);
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        peakTableView.refresh();
    }

    void saveList() {
        if (peakList != null) {
            try {
                FileChooser fileChooser = new FileChooser();
                File file = fileChooser.showSaveDialog(null);
                if (file != null) {
                    String listFileName = file.getPath();

                    try (FileWriter writer = new FileWriter(listFileName)) {
                        peakList.writePeaksXPK2(writer);
                        writer.close();
                    }
                }
            } catch (IOException | InvalidPeakException ioE) {
                ExceptionDialog dialog = new ExceptionDialog(ioE);
                dialog.showAndWait();
            }
        }
    }

    void readList() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            String listFileName = file.getPath();
            try {
                PeakList newPeakList = PeakList.readXPK2Peaks(listFileName);
                if (newPeakList != null) {
                    setPeakList(newPeakList);
                }
            } catch (IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    void measureIntensities() {
        peakList.quantifyPeaks("center");
        updatePeakTableView();
    }

    void measureVolumes() {
        peakList.quantifyPeaks("volume");
        updatePeakTableView();
    }

    void measureEVolumes() {
        peakList.quantifyPeaks("evolume");
        updatePeakTableView();
    }

    void compressPeakList() {
        if (peakList != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Permanently remove deleted peaks");
            alert.showAndWait().ifPresent(response -> {
                peakList.compress();
                updatePeakTableView();
            });
        }
    }

    void renumberPeakList() {
        if (peakList != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Renumber peak list (permanent!)");
            alert.showAndWait().ifPresent(response -> {
                peakList.reNumber();
                updatePeakTableView();
            });
        }
    }

    void compressAndDegapPeakList() {
        if (peakList != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Remove deleted peaks and renumber (permanent!)");
            alert.showAndWait().ifPresent(response -> {
                peakList.compress();
                peakList.reNumber();
                updatePeakTableView();
            });
        }
    }

    void deletePeakList() {
        if (peakList != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete Peak List");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    PeakList.remove(peakList.getName());
                    PeakList list = null;
                    setPeakList(list);
                }
            });
        }
    }

    void unLinkPeakList() {
        if (peakList != null) {
            peakList.unLinkPeaks();
        }
    }

    void clusterPeakList() {
        if (peakList != null) {
            if (peakList.hasSearchDims()) {
                peakList.clusterPeaks();
            } else {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setHeaderText("Enter dimensions and tolerances (like: HN 0.1 N 0.5)");
                Optional<String> result = dialog.showAndWait();
                if (result.isPresent()) {
                    try {
                        peakList.setSearchDims(result.get());
                        peakList.clusterPeaks();
                    } catch (IllegalArgumentException iE) {
                        ExceptionDialog edialog = new ExceptionDialog(iE);
                        edialog.showAndWait();
                    }
                }
            }
        }
    }

    void duplicatePeakList() {
        if (peakList != null) {
            TextInputDialog dialog = new TextInputDialog();
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                PeakList newPeakList = peakList.copy(result.get(), false, false);
                if (newPeakList != null) {
                    setPeakList(newPeakList);
                }
            }
        }
    }

    void setDeleteStatus(ToggleButton button) {
        if (currentPeak != null) {
            if (button.isSelected()) {
                currentPeak.setStatus(-1);
            } else {
                currentPeak.setStatus(0);
            }
            updatePeakTableView();
        }
    }

}
