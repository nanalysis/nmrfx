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
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;

import java.util.Collection;
import java.util.List;

public class PeakMenuActions extends MenuActions {
    private static PeakTableController peakTableController;
    private static PeakGeneratorGUI peakGeneratorGUI;
    private LigandScannerController scannerController;
    private NOETableController noeTableController;
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

        MenuItem ligandScannerMenuItem = new MenuItem("Show Ligand Scanner");
        ligandScannerMenuItem.disableProperty().bind(AnalystApp.getFXMLControllerManager().activeControllerProperty().isNull());
        ligandScannerMenuItem.setOnAction(e -> showLigandScanner());

        MenuItem noeTableMenuItem = new MenuItem("Show NOE Table");
        noeTableMenuItem.setOnAction(e -> showNOETable());

        Menu assignCascade = new Menu("Assign Tools");

        assignOnPick = new CheckMenuItem("Assign on Pick");

        MenuItem atomBrowserMenuItem = new MenuItem("Show Atom Browser");
        atomBrowserMenuItem.disableProperty().bind(AnalystApp.getFXMLControllerManager().activeControllerProperty().isNull());
        atomBrowserMenuItem.setOnAction(e -> showAtomBrowser());

        MenuItem runAboutMenuItem = new MenuItem("Show RunAboutX");
        runAboutMenuItem.setOnAction(e -> showRunAbout());

        MenuItem zzMenuItem = new MenuItem("ZZ Fitting");
        zzMenuItem.setOnAction(e -> showZZTool());


        assignCascade.getItems().addAll(assignOnPick,
                atomBrowserMenuItem, runAboutMenuItem);
        menu.getItems().addAll(peakGeneratorMenuItem, linkPeakDimsMenuItem,
                ligandScannerMenuItem,
                noeTableMenuItem, zzMenuItem,
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

    @FXML
    private void showLigandScanner() {
        if (scannerController == null) {
            scannerController = LigandScannerController.create();
        }
        if (scannerController != null) {
            scannerController.getStage().show();
            scannerController.getStage().toFront();
        } else {
            System.out.println("Couldn't make atom controller");
        }
    }

    private void showNOETable() {
        if (noeTableController == null) {
            noeTableController = NOETableController.create();
            if (noeTableController == null) {
                return;
            }
            Collection<NoeSet> noeSets = MoleculeFactory.getActive().getMolecularConstraints().noeSets();

            noeSets.stream().findFirst().ifPresent(noeTableController::setNoeSet);
        }
        noeTableController.getStage().show();
        noeTableController.getStage().toFront();
        noeTableController.updateNoeSetMenu();
    }

    void showRunAbout() {
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

    public void showPeakGeneratorGUI() {
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
