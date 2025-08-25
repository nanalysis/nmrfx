package org.nmrfx.analyst.gui.peaks;

import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.MenuActions;
import org.nmrfx.analyst.gui.PeakGeneratorGUI;
import org.nmrfx.analyst.gui.ZZPlotTool;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.project.ProjectBase;

import java.util.List;

public class PeakMenuActions extends MenuActions {
    private static PeakTableController peakTableController;
    private static PeakGeneratorGUI peakGeneratorGUI;
    private LigandScannerController scannerController;
    private AtomBrowser atomBrowser;
    private CheckMenuItem assignOnPick;
    private PeakAtomPicker peakAtomPicker;

    public PeakMenuActions(AnalystApp app, Menu menu) {
        super(app, menu);
    }

    @Override
    public void basic() {
        MenuItem peakAttrMenuItem = new MenuItem("Show Peak Tool");
        peakAttrMenuItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().showPeakAttrAction(e));

        MenuItem peakTableMenuItem = new MenuItem("Show Peak Table");
        peakTableMenuItem.setOnAction(e -> showPeakTable());

        MenuItem peakListsTableItem = new MenuItem("Show PeakLists Table");
        peakListsTableItem.setOnAction(e -> showPeakListsTable());

        menu.getItems().addAll(peakAttrMenuItem, peakTableMenuItem, peakListsTableItem);
    }

    @Override
    protected void advanced() {

        MenuItem peakGeneratorMenuItem = new MenuItem("Simulate Peaks");
        peakGeneratorMenuItem.setOnAction(e -> showPeakGeneratorGUI());

        MenuItem linkPeakDimsMenuItem = new MenuItem("Link by Labels");
        linkPeakDimsMenuItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().linkPeakDims());

        Menu assignCascade = new Menu("Assign Tools");

        assignOnPick = new CheckMenuItem("Assign on Pick");

        MenuItem atomBrowserMenuItem = new MenuItem("Show Atom Browser");
        atomBrowserMenuItem.disableProperty().bind(AnalystApp.getFXMLControllerManager().activeControllerProperty().isNull());
        atomBrowserMenuItem.setOnAction(e -> showAtomBrowser());

        MenuItem zzMenuItem = new MenuItem("ZZ Fitting");
        zzMenuItem.setOnAction(e -> showZZTool());


        assignCascade.getItems().addAll(assignOnPick,
                atomBrowserMenuItem);
        menu.getItems().addAll(peakGeneratorMenuItem, linkPeakDimsMenuItem,
                zzMenuItem,
                assignCascade);

    }

    public void showPeakTable() {
        showPeakTable(null);
    }

    public static void showPeakTable(PeakList peakList) {
        if (peakTableController == null) {
            peakTableController = PeakTableController.create();
        }
        if (peakList == null) {
            List<String> names = ProjectBase.getActive().getPeakListNames();
            if (!names.isEmpty()) {
                peakList = ProjectBase.getActive().getPeakList(names.get(0));
            }
        }
        if (peakTableController != null) {
            if (peakList != null) {
                peakTableController.setPeakList(peakList);
            }
            peakTableController.getStage().show();
            peakTableController.getStage().toFront();
        }
    }

    private void showPeakListsTable() {
        PeakListsTableController pltc = PeakListsTableController.getPeakListsTableController();
        pltc.show();
    }

    public void showAtomBrowser() {
        if (atomBrowser == null) {
            ToolBar navBar = new ToolBar();
            FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
            controller.getBottomBox().getChildren().add(navBar);
            atomBrowser = new AtomBrowser(controller, this::removeAtomBrowser);
            atomBrowser.initSlider(navBar);
        }
    }

    public void removeAtomBrowser(Object o) {
        if (atomBrowser != null) {
            FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
            controller.getBottomBox().getChildren().remove(atomBrowser.getToolBar());
            atomBrowser = null;
        }
    }

    public void pickedPeakAction(Peak peak) {
        if (assignOnPick.isSelected()) {
            PolyChart chart = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getActiveChart();
            double x = chart.getMouseX();
            double y = chart.getMouseY();
            Canvas canvas = chart.getCanvas();
            Point2D sXY = canvas.localToScreen(x, y);
            if (peakAtomPicker == null) {
                peakAtomPicker = new PeakAtomPicker();
                peakAtomPicker.create();
            }
            peakAtomPicker.show(sXY.getX(), sXY.getY(), peak);
        }
    }

    public void assignPeak() {
        if (peakAtomPicker == null) {
            peakAtomPicker = new PeakAtomPicker();
            peakAtomPicker.create();
        }
        peakAtomPicker.show(300, 300, null);
    }

    public static void showPeakGeneratorGUI() {
        if (peakGeneratorGUI == null) {
            peakGeneratorGUI = new PeakGeneratorGUI();
            peakGeneratorGUI.create();
        }
        peakGeneratorGUI.show(300, 300);
    }

    public void showZZTool() {
        ZZPlotTool zzPlotTool = new ZZPlotTool();
        zzPlotTool.show("Time", "Intensity");
    }


}
