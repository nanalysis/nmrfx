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
package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.VBox;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.controls.FractionPane;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;

/**
 *
 * @author brucejohnson
 */
public class StripController implements ControllerTool {

    final static int X = 0;
    final static int Y = 1;
    FXMLController controller;
    Consumer<StripController> closeAction;
    ToolBar toolBar;
    MenuButton peakListMenuButton;
    MenuButton[] dimMenus = new MenuButton[2];
    MenuButton actionMenu = new MenuButton("Actions");
    VBox vBox;
    Slider posSlider = new Slider();
    Slider nSlider = new Slider();
    List<Cell> cells = new ArrayList<>();
    String[] dimNames = new String[2];
    ChangeListener limitListener;
    Pattern resPat = Pattern.compile("([A-Z]*)([0-9]+)\\.(.*)");
    int currentLow = 0;
    int currentHigh = 0;
    int frozen = -1;

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
        this.vBox.getChildren().add(toolBar);

        String iconSize = "16px";
        String fontSize = "7pt";

        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", iconSize, fontSize, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());
        toolBar.getItems().add(closeButton);

        peakListMenuButton = new MenuButton("List");
        toolBar.getItems().add(peakListMenuButton);

        dimMenus[X] = new MenuButton("X");
        dimMenus[Y] = new MenuButton("Y");
        toolBar.getItems().add(dimMenus[X]);
        toolBar.getItems().add(dimMenus[Y]);
        toolBar.getItems().add(actionMenu);

        MenuItem freezeMenuItem = new MenuItem("Freeze");
        actionMenu.getItems().add(freezeMenuItem);
        freezeMenuItem.setOnAction(e -> freezeChart());

        MenuItem thawMenuItem = new MenuItem("Thaw");
        actionMenu.getItems().add(thawMenuItem);
        thawMenuItem.setOnAction(e -> thawChart());

        MenuItem sortMenuItem = new MenuItem("Sort");
        actionMenu.getItems().add(sortMenuItem);
        sortMenuItem.setOnAction(e -> sortPeaks());

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

        PeakList.peakListTable.addListener(mapChangeListener);
        updatePeakListMenu();
        limitListener = new ChangeListener() {
            @Override
            public void changed(ObservableValue observable, Object oldValue, Object newValue) {
                updateView();
            }
        };
    }

    void updateDimMenus(String rowName, Dataset dataset) {
        int nDim = dataset.getNDim();
        final int jDim = rowName.equals("X") ? X : Y;
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
        updateView();
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

    void setPeakList(String peakListName) {
        PeakList peakList = PeakList.get(peakListName);
        addPeaks(peakList.peaks());
    }

    int getDim(Dataset dataset, String dimName) {
        int datasetDim = -1;
        for (int i = 0; i < dataset.getNDim(); i++) {
            if (dimName.equals(dataset.getLabel(i))) {
                datasetDim = i;
            }
        }
        return datasetDim;
    }

    int[] getDims(Dataset dataset) {
        int[] dims = new int[dataset.getNDim()];
        if ((dimNames[X] == null) || (dimNames[Y] == null)) {
            for (int i = 0; i < dims.length; i++) {
                dims[i] = i;
            }
        } else {
            dims[0] = getDim(dataset, dimNames[X]);
            dims[1] = getDim(dataset, dimNames[Y]);

            for (int i = 2; i < dims.length; i++) {
                for (int k = 0; k < dims.length; k++) {
                    boolean unused = true;
                    for (int j = 0; j < i; j++) {
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
        return dims;
    }

    double[] getPositions(Dataset dataset, Peak peak, int[] dims) {
        double[] positions = new double[dataset.getNDim()];
        for (int i = 0; i < positions.length; i++) {
            String dimName = dataset.getLabel(dims[i]);
            PeakDim peakDim = peak.getPeakDim(dimName);
            positions[i] = peakDim.getChemShiftValue();

        }
        return positions;
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
                    System.out.println("update menus");
                    updateDimMenus("X", dataset);
                    updateDimMenus("Y", dataset);
                    dimNames[0] = dataset.getLabel(0);
                    dimNames[1] = dataset.getLabel(1);
                    firstPeak = false;
                }
                int[] dims = getDims(dataset);
                double[] positions = getPositions(dataset, peak, dims);
                Cell cell = new Cell(dataset, peak, dims, positions);
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

    class Cell {

        Dataset dataset;
        Peak peak;
        int[] dims;
        double[] positions;

        public Cell(Dataset dataset, Peak peak, int[] dims) {

        }

        public Cell(Dataset dataset, Peak peak, int[] dims, double[] positions) {
            this.dataset = dataset;
            this.peak = peak;
            this.dims = dims;
            this.positions = positions;
        }

        void updateCell() {
            dims = getDims(dataset);
            positions = getPositions(dataset, peak, dims);
        }

        void updateChart(PolyChart chart) {
            controller.setActiveChart(chart);
            controller.addDataset(dataset, false, false);
            DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
            dataAttr.setDim("X", dimNames[X]);
            dataAttr.setDim("Y", dimNames[Y]);
            double width = 0.1;
            chart.setAxis(0, positions[0] - width, positions[0] + width);
            chart.full(1);
            for (int i = 2; i < positions.length; i++) {
                chart.setAxis(i, positions[i], positions[i]);
            }
            chart.useImmediateMode = true;
        }
    }

    public void updateView() {
        int low = (int) posSlider.getValue();
        int nActive = (int) nSlider.getValue();
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
            int nCharts = high - low + 1;
            if (frozen >= 0) {
                nCharts++;
            }
            grid(1, nCharts);
            List<PolyChart> charts = controller.getCharts();
            for (int iCell = low; iCell <= high; iCell++) {
                Cell cell = cells.get(iCell);
                int iChart = iCell - low;
                if (frozen >= 0) {
                    if (iChart >= frozen) {
                        iChart++;
                    }
                }
                PolyChart chart = charts.get(iChart);
                cell.updateChart(chart);
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

    public void grid(int rows, int columns) {
        int nCharts = rows * columns;
        if (nCharts > 0) {
            FractionPane.ORIENTATION orient = FractionPane.getOrientation("grid");
            controller.setNCharts(nCharts);
            controller.arrange(rows);
            controller.setBorderState(true);
            PolyChart chartActive = controller.charts.get(0);
            controller.setActiveChart(chartActive);
        }
    }

    void freezeChart() {
        PolyChart activeChart = controller.getActiveChart();
        frozen = controller.getCharts().indexOf(activeChart);
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

    void sortPeaks() {
        Collections.sort(cells, new PeakSortComparator());
        updateView();
    }
}
