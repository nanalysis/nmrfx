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
package org.nmrfx.analyst.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.MapChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.controls.FractionPane;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.processor.project.Project;

/**
 *
 * @author brucejohnson
 */
public class StripController implements ControllerTool {

    final static int X = 0;
    final static int Z = 1;
    FXMLController controller;
    Consumer<StripController> closeAction;
    ToolBar toolBar;
    ToolBar setupToolBar;
    MenuButton peakListMenuButton;
    MenuButton[] dimMenus = new MenuButton[2];
    MenuButton actionMenu = new MenuButton("Actions");
    VBox vBox;
    TextField widthBox = new TextField();
    Slider posSlider = new Slider();
    Slider nSlider = new Slider();

    Spinner<Integer> itemSpinner;
    ChoiceBox<Integer> offsetBox;
    ChoiceBox<Integer> rowBox;
    MenuButton itemPeakListMenuButton;
    MenuButton itemDatasetMenuButton;
    Label peakLabel;
    Label dataLabel;

    List<StripItem> items = new ArrayList<>();

    List<Cell> cells = new ArrayList<>();
    String[] dimNames = new String[2];
    ChangeListener limitListener;
    Pattern resPat = Pattern.compile("([A-Z]*)([0-9]+)\\.(.*)");
    PeakList controlList = null;
    int currentRows = 0;
    int currentColumns = 0;
    int currentLow = 0;
    int currentHigh = 0;
    int frozen = -1;
    double xWidth = 0.2;

    public StripController(FXMLController controller, Consumer<StripController> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public VBox getBox() {
        return vBox;
    }

    public void close() {
        closeAction.accept(this);
    }

    void initialize(VBox vBox) {
        this.vBox = vBox;
        this.toolBar = new ToolBar();
        this.setupToolBar = new ToolBar();
        this.vBox.getChildren().addAll(toolBar, setupToolBar);

        String iconSize = "16px";
        String fontSize = "7pt";

        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", iconSize, fontSize, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());
        toolBar.getItems().add(closeButton);

        peakListMenuButton = new MenuButton("List");
        toolBar.getItems().add(peakListMenuButton);

        dimMenus[X] = new MenuButton("X");
        dimMenus[Z] = new MenuButton("Z");
        widthBox.setMaxWidth(50);
        widthBox.setText(String.format("%.1f", xWidth));
        widthBox.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                xWidth = Double.parseDouble(widthBox.getText());
            }
        });

        toolBar.getItems().add(dimMenus[X]);
        toolBar.getItems().add(dimMenus[Z]);
        toolBar.getItems().add(widthBox);
        toolBar.getItems().add(actionMenu);

        Menu addMenu = new Menu("Add");
        actionMenu.getItems().add(addMenu);

        MenuItem addAllMenuItem = new MenuItem("All");
        addMenu.getItems().add(addAllMenuItem);
        addAllMenuItem.setOnAction(e -> addAll());

        MenuItem addAssignedMenuItem = new MenuItem("Assigned");
        addMenu.getItems().add(addAssignedMenuItem);
        addAssignedMenuItem.setOnAction(e -> addAssigned());

        Menu freezeThawMenu = new Menu("Freeze/Thaw");
        actionMenu.getItems().add(freezeThawMenu);

        MenuItem freezeMenuItem = new MenuItem("Freeze");
        freezeThawMenu.getItems().add(freezeMenuItem);
        freezeMenuItem.setOnAction(e -> freezeChart());

        MenuItem thawMenuItem = new MenuItem("Thaw");
        freezeThawMenu.getItems().add(thawMenuItem);
        thawMenuItem.setOnAction(e -> thawChart());

        Menu sortMenu = new Menu("Sort");

        MenuItem sortByResidueMenuItem = new MenuItem("By Residue");
        sortMenu.getItems().add(sortByResidueMenuItem);
        sortByResidueMenuItem.setOnAction(e -> sortPeaksByResidue());

        MenuItem sortByIndex = new MenuItem("By Index");
        sortMenu.getItems().add(sortByIndex);
        sortByIndex.setOnAction(e -> sortPeaksByIndex());

        actionMenu.getItems().add(sortMenu);

        ToolBarUtils.addFiller(toolBar, 25, 50);
        Label startLabel = new Label("Start:");
        toolBar.getItems().add(startLabel);
        posSlider.setBlockIncrement(1);
        posSlider.setSnapToTicks(true);
        posSlider.setMinWidth(250);
        toolBar.getItems().add(posSlider);

        Label nLabel = new Label("N:");
        toolBar.getItems().add(nLabel);
        nSlider.setBlockIncrement(1);
        nSlider.setMinWidth(100);
        toolBar.getItems().add(nSlider);

        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            updatePeakListMenu();
        };

        limitListener = new ChangeListener() {
            @Override
            public void changed(ObservableValue observable, Object oldValue, Object newValue) {
                updateView();
            }
        };

        Button addButton = GlyphsDude.createIconButton(FontAwesomeIcon.PLUS);
        addButton.setOnAction(e -> addItem());
        Button removeButton = GlyphsDude.createIconButton(FontAwesomeIcon.REMOVE);
        removeButton.setOnAction(e -> removeItem());
        peakLabel = new Label();
        peakLabel.setMinWidth(150);

        dataLabel = new Label();
        dataLabel.setMinWidth(150);
        itemSpinner = new Spinner(0, 0, 0);
        itemSpinner.setMaxWidth(75);
        itemSpinner.getValueFactory().valueProperty().addListener(e -> showItem());
        itemPeakListMenuButton = new MenuButton("List");
        itemDatasetMenuButton = new MenuButton("Dataset");
        Project.getActive().addDatasetListListener((MapChangeListener) (e -> updateDatasetNames()));

        Label offsetLabel = new Label("Offset:");
        offsetBox = new ChoiceBox();
        offsetBox.getItems().addAll(0, 1, 2, 3, 4);
        offsetBox.setValue(0);
        offsetBox.setOnAction(e -> updateItem());
        Label rowLabel = new Label("Row:");
        rowBox = new ChoiceBox();
        rowBox.getItems().addAll(0, 1, 2, 3, 4);
        rowBox.setValue(0);
        rowBox.setOnAction(e -> updateItem());
        setupToolBar.getItems().addAll(addButton, removeButton, itemSpinner,
                itemDatasetMenuButton, dataLabel,
                itemPeakListMenuButton, peakLabel,
                offsetLabel, offsetBox, rowLabel, rowBox);

        Project.getActive().addPeakListListener(mapChangeListener);
        updatePeakListMenu();
        updateDatasetNames();
        StripItem item = new StripItem();
        items.add(item);

    }

    void updateDimMenus(String rowName, Dataset dataset) {
        int nDim = dataset.getNDim();
        final int jDim = rowName.equals("X") ? X : Z;
        MenuButton dimMenu = dimMenus[jDim];
        dimMenu.getItems().clear();
        for (int iDim = 0; iDim < nDim; iDim++) {
            String dimName = dataset.getLabel(iDim);
            MenuItem menuItem = new MenuItem(String.valueOf(iDim + 1) + ":" + dimName);
            menuItem.setOnAction(e -> updateDimMenu(jDim, dimName));
            dimMenu.getItems().add(menuItem);
            System.out.println("add menu item " + rowName + " " + dimName);
        }
    }

    void updateDimMenu(int jDim, String dimName) {
        dimNames[jDim] = dimName;
        updateCells();
        currentLow = -1;
        currentHigh = -1;
        currentRows = 0;
        currentColumns = 0;
        updateView();
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();
        itemPeakListMenuButton.getItems().clear();
        MenuItem emptyPeakListMenuItem = new MenuItem("");
        emptyPeakListMenuItem.setOnAction(e -> {
            setItemPeakList("");
        });
        itemPeakListMenuButton.getItems().add(emptyPeakListMenuItem);

        for (String peakListName : Project.getActive().getPeakListNames()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                setPeakList(peakListName);
            });
            peakListMenuButton.getItems().add(menuItem);

            MenuItem itemPeakListMenuItem = new MenuItem(peakListName);
            itemPeakListMenuItem.setOnAction(e -> {
                setItemPeakList(peakListName);
            });
            itemPeakListMenuButton.getItems().add(itemPeakListMenuItem);
        }
    }

    public void updateDatasetNames() {
        itemDatasetMenuButton.getItems().clear();
        Dataset.names().stream().forEach(nm -> {
            MenuItem item = new MenuItem(nm);
            itemDatasetMenuButton.getItems().add(item);
            item.setOnAction(e -> setItemDataset(nm));
        });
    }

    void setPeakList(String peakListName) {
        controlList = PeakList.get(peakListName);
        StripItem item = getCurrentItem();
        item.peakList = controlList;
        controlList.getDatasetName();
        item.dataset = Dataset.getDataset(controlList.getDatasetName());
        item.row = 0;
        item.offset = 0;
        showItem();
        addPeaks(controlList.peaks());
        updateView();
    }

    void setItemPeakList(String peakListName) {
        peakLabel.setText(peakListName);
        updateItem();
    }

    void setItemDataset(String datasetName) {
        dataLabel.setText(datasetName);
        updateItem();
    }

    StripItem getCurrentItem() {
        int item = itemSpinner.getValue();
        return items.get(item);
    }

    void removeItem() {
        int item = itemSpinner.getValue();
        if (items.size() > 1) {
            items.remove(item);
            SpinnerValueFactory.IntegerSpinnerValueFactory factory
                    = (SpinnerValueFactory.IntegerSpinnerValueFactory) itemSpinner.getValueFactory();
            factory.setMax(items.size() - 1);
            if (item >= items.size()) {
                item--;
            }
            factory.setValue(item);
            showItem();
        }
    }

    void addItem() {
        items.add(new StripItem());
        SpinnerValueFactory.IntegerSpinnerValueFactory factory
                = (SpinnerValueFactory.IntegerSpinnerValueFactory) itemSpinner.getValueFactory();
        factory.setMax(items.size() - 1);
        factory.setValue(items.size() - 1);
        clearItem();
    }

    void clearItem() {
        StripItem item = getCurrentItem();
        item.peakList = null;
        item.dataset = null;
        item.row = 0;
        item.offset = 0;
        peakLabel.setText("");
        dataLabel.setText("");
        rowBox.setValue(0);
        offsetBox.setValue(0);

    }

    void showItem() {
        StripItem item = getCurrentItem();
        dataLabel.setText(item.dataset == null ? "" : item.dataset.getName());
        peakLabel.setText(item.peakList == null ? "" : item.peakList.getName());
        rowBox.setValue(item.row);
        offsetBox.setValue(item.offset);
    }

    void updateItem() {
        StripItem item = getCurrentItem();
        item.peakList = PeakList.get(peakLabel.getText());
        item.dataset = Dataset.getDataset(dataLabel.getText());
        item.row = rowBox.getValue();
        item.offset = offsetBox.getValue();
    }

    int getMaxOffset() {
        int maxOffset = 0;
        for (StripItem item : items) {
            int offset = item.offset;
            maxOffset = Math.max(offset, maxOffset);
        }
        return maxOffset;
    }

    int getMaxRow() {
        int maxRow = 0;
        for (StripItem item : items) {
            int row = item.row;
            maxRow = Math.max(row, maxRow);
        }
        return maxRow;
    }

    int getDim(DatasetBase dataset, String dimName) {
        int datasetDim = -1;
        for (int i = 0; i < dataset.getNDim(); i++) {
            if (dimName.equals(dataset.getLabel(i))) {
                datasetDim = i;
            }
        }
        return datasetDim;
    }

    int[] getDims(DatasetBase dataset) {
        int[] dims = new int[dataset.getNDim()];
        if ((dimNames[X] == null) || (dimNames[Z] == null)) {
            for (int i = 0; i < dims.length; i++) {
                dims[i] = i;
            }
        } else {
            for (int i = 0; i < dims.length; i++) {
                dims[i] = -1;
            }
            dims[0] = getDim(dataset, dimNames[X]);
            dims[2] = getDim(dataset, dimNames[Z]);

            for (int i = 0; i < dims.length; i++) {
                if (dims[i] == -1) {
                    for (int k = 0; k < dims.length; k++) {
                        boolean unused = true;
                        for (int j = 0; j < dims.length; j++) {
                            if (dims[j] == k) {
                                unused = false;
                                break;
                            }
                        }
                        if (unused) {
                            dims[i] = k;
                        }
                    }
                }
            }
        }
        return dims;
    }

    double[] getPositions(Peak peak, String[] dimNames) {
        double[] positions = new double[dimNames.length];
        for (int i = 0; i < positions.length; i++) {
            PeakDim peakDim = peak.getPeakDim(dimNames[i]);
            positions[i] = peakDim.getChemShiftValue();

        }
        return positions;
    }

    void addAll() {
        if (controlList != null) {
            addPeaks(controlList.peaks());
            updateView();
        }
    }

    void addAssigned() {
        if (controlList != null) {
            List<Peak> peaks = controlList.peaks().stream().filter(p -> {
                String label = p.getPeakDim(dimNames[0]).getLabel();
                Matcher matcher = resPat.matcher(label);
                return matcher.matches();
            }
            ).collect(Collectors.toList());
            addPeaks(peaks);
            updateView();
        }
    }

    public void addPeaks(List<Peak> peaks) {
        posSlider.valueProperty().removeListener(limitListener);
        nSlider.valueProperty().removeListener(limitListener);
        cells.clear();
        posSlider.setMin(0);
        posSlider.setMax(peaks.size() - 1);
        posSlider.setValue(0);
        nSlider.setMin(1);
        nSlider.setMax(30);
        nSlider.setValue(5);

        int nPeaks = peaks.size();
        if (nPeaks < 10) {
            posSlider.setMajorTickUnit(1);
            posSlider.setMinorTickCount(0);
        } else {
            posSlider.setMajorTickUnit(10);
            posSlider.setMinorTickCount(8);
        }
        int majorTick = peaks.size() < 10 ? peaks.size() - 1 : 10;
        posSlider.setMajorTickUnit(10);
        posSlider.setMinorTickCount(majorTick);
        double highValue = peaks.size() >= 10 ? 9 : peaks.size() - 1;
        boolean firstPeak = true;
        for (Peak peak : peaks) {
            Dataset dataset = Dataset.getDataset(peak.getPeakList().getDatasetName());
            if (dataset != null) {
                if (firstPeak) {
                    updateDimMenus("X", dataset);
                    updateDimMenus("Z", dataset);
                    if (dimNames[0] == null) {
                        dimNames[0] = dataset.getLabel(0);
                    }
                    if (dimNames[1] == null) {
                        dimNames[1] = dataset.getLabel(1);
                    }
                    firstPeak = false;
                }
                double[] positions = getPositions(peak, dimNames);
                Cell cell = new Cell(peak, positions);
                cells.add(cell);
            }
        }
        currentLow = 0;
        currentHigh = 0;
        posSlider.valueProperty().addListener(limitListener);
        nSlider.valueProperty().addListener(limitListener);

    }

    void updateCells() {
        for (Cell cell : cells) {
            cell.updateCell();
        }
    }

    class StripItem {

        Dataset dataset;
        PeakList peakList;
        int offset = 0;
        int row = 0;

        public StripItem() {

        }

        public StripItem(Dataset dataset, PeakList peakList, int offset, int row) {
            this.dataset = dataset;
            this.peakList = peakList;
            this.offset = offset;
            this.row = row;
        }
    }

    class Cell {

        Peak peak;
        double[] positions;

        public Cell(Dataset dataset, Peak peak) {

        }

        public Cell(Peak peak, double[] positions) {
            this.peak = peak;
            this.positions = positions;
        }

        void updateCell() {
            positions = getPositions(peak, dimNames);
        }

        void updateChart(PolyChart chart, StripItem item, boolean init) {
            controller.setActiveChart(chart);
            if (item.dataset != null) {
                if (init) {
                    controller.addDataset(item.dataset, false, false);
                }
                chart.setDataset(item.dataset);
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                int[] dims = getDims(dataAttr.getDataset());
                for (int i = 0; i < dims.length; i++) {
                    dataAttr.setDim(i, dims[i]);
                }
                chart.setAxis(0, positions[0] - xWidth / 2.0, positions[0] + xWidth / 2.0);
                if (item.peakList != null) {
                    PeakListAttributes peakAttr = chart.setupPeakListAttributes(item.peakList);
                    peakAttr.setLabelType(PeakDisplayParameters.LabelTypes.SglResidue);
                }
                chart.full(1);
                for (int i = 1; i < positions.length; i++) {
                    chart.setAxis(1 + i, positions[i], positions[i]);
                }
            }
            chart.useImmediateMode(true);
        }
    }

    public void updateView() {
        int low = (int) posSlider.getValue();
        int nActive = (int) nSlider.getValue();
        if (nActive > cells.size()) {
            nActive = cells.size();
        }
        if (low < 0) {
            low = 0;
        }
        int high = low + nActive - 1;
        if (high >= cells.size()) {
            high = cells.size() - 1;
            low = high - nActive + 1;
        }
        if ((low != currentLow) || (high != currentHigh)) {
            controller.setChartDisable(true);
            int nItems = high - low + 1;
            if (frozen >= 0) {
                nItems++;
            }
            int maxOffset = getMaxOffset();
            int maxRow = getMaxRow();
            int nCols = nItems * (maxOffset + 1);
            boolean updated = grid(maxRow + 1, nCols);
            List<PolyChart> charts = controller.getCharts();
            for (int iCell = low; iCell <= high; iCell++) {
                Cell cell = cells.get(iCell);
                int jCell = iCell - low;
                if (frozen >= 0) {
                    if (jCell >= frozen) {
                        jCell++;
                    }
                }
                for (StripItem item : items) {
                    int iCol = jCell * (maxOffset + 1) + item.offset;
                    int iRow = item.row;
                    int iChart = iRow * nCols + iCol;
                    PolyChart chart = charts.get(iChart);
                    cell.updateChart(chart, item, updated && (iCol == 0));
                    if (iCol == 0) {
                        chart.updateAxisType();
                    }
                }
                updated = false;
            }
            currentLow = low;
            currentHigh = high;
            if (frozen >= 0) {
                charts.get(frozen).setActiveChart();
            }
            controller.setChartDisable(false);
            controller.draw();
        }
    }

    public boolean grid(int rows, int columns) {
        boolean result = false;
        if ((currentRows != rows) || (currentColumns != columns)) {
            int nCharts = rows * columns;
            if (nCharts > 0) {
                FractionPane.ORIENTATION orient = FractionPane.getOrientation("grid");
                controller.setNCharts(nCharts);
                controller.arrange(rows);
                controller.setBorderState(true);
                PolyChart chartActive = controller.getCharts().get(0);
                controller.setActiveChart(chartActive);
                currentRows = rows;
                currentColumns = columns;
                result = true;
            }
        }
        return result;
    }

    void freezeChart() {
        PolyChart activeChart = controller.getActiveChart();
        frozen = controller.getCharts().indexOf(activeChart) / (getMaxOffset() + 1);
        currentLow = -1;
        currentHigh = -1;
        updateView();
    }

    void thawChart() {
        frozen = -1;
        currentLow = -1;
        currentHigh = -1;
        updateView();
    }

    class PeakSortComparator implements Comparator<Cell> {

        @Override
        public int compare(Cell o1, Cell o2) {
            String lab1 = o1.peak.getPeakDim(dimNames[X]).getLabel();
            String lab2 = o2.peak.getPeakDim(dimNames[X]).getLabel();
            Matcher match1 = resPat.matcher(lab1);
            Matcher match2 = resPat.matcher(lab2);
            int res1 = -9999;
            int res2 = -9999;
            if (match1.matches()) {
                res1 = Integer.parseInt(match1.group(2));
            }
            if (match2.matches()) {
                res2 = Integer.parseInt(match2.group(2));
            }
            return Integer.compare(res1, res2);
        }
    }

    class PeakIndexSortComparator implements Comparator<Cell> {

        @Override
        public int compare(Cell o1, Cell o2) {
            int i1 = o1.peak.getIdNum();
            int i2 = o2.peak.getIdNum();
            return Integer.compare(i1, i2);
        }
    }

    void sortPeaksByResidue() {
        Collections.sort(cells, new PeakSortComparator());
        updateView();
    }

    void sortPeaksByIndex() {
        Collections.sort(cells, new PeakIndexSortComparator());
        updateView();
    }
}
