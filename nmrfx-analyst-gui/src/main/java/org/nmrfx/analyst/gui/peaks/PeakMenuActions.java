package org.nmrfx.analyst.gui.peaks;

import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import org.nmrfx.analyst.gui.*;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.peaks.Peak;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.project.Project;

import java.util.Collection;
import java.util.List;

public class PeakMenuActions extends MenuActions {
    private PeakTableController peakTableController;
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
        peakAttrMenuItem.setOnAction(e -> FXMLController.getActiveController().showPeakAttrAction(e));

        MenuItem peakTableMenuItem = new MenuItem("Show Peak Table");
        peakTableMenuItem.setOnAction(e -> showPeakTable());
        menu.getItems().addAll(peakAttrMenuItem, peakTableMenuItem);
    }

    @Override
    protected void advanced() {
        MenuItem linkPeakDimsMenuItem = new MenuItem("Link by Labels");
        linkPeakDimsMenuItem.setOnAction(e -> FXMLController.getActiveController().linkPeakDims());

        MenuItem ligandScannerMenuItem = new MenuItem("Show Ligand Scanner");
        ligandScannerMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        ligandScannerMenuItem.setOnAction(e -> showLigandScanner());

        MenuItem noeTableMenuItem = new MenuItem("Show NOE Table");
        noeTableMenuItem.setOnAction(e -> showNOETable());

        Menu assignCascade = new Menu("Assign Tools");

        assignOnPick = new CheckMenuItem("Assign on Pick");

        MenuItem atomBrowserMenuItem = new MenuItem("Show Atom Browser");
        atomBrowserMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        atomBrowserMenuItem.setOnAction(e -> showAtomBrowser());

        MenuItem runAboutMenuItem = new MenuItem("Show RunAboutX");
        runAboutMenuItem.setOnAction(e -> showRunAbout());

        assignCascade.getItems().addAll(assignOnPick,
                atomBrowserMenuItem, runAboutMenuItem);
        menu.getItems().addAll(linkPeakDimsMenuItem,
                ligandScannerMenuItem,
                noeTableMenuItem,
                assignCascade);

    }

    private void showPeakTable() {
        if (peakTableController == null) {
            peakTableController = PeakTableController.create();
            List<String> names = Project.getActive().getPeakListNames();
            if (!names.isEmpty()) {
                peakTableController.setPeakList(Project.getActive().getPeakList(names.get(0)));
            }
        }
        if (peakTableController != null) {
            peakTableController.getStage().show();
            peakTableController.getStage().toFront();
        } else {
            System.out.println("Couldn't make peak table controller");
        }
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
            FXMLController controller = FXMLController.getActiveController();
            controller.getBottomBox().getChildren().add(navBar);
            atomBrowser = new AtomBrowser(controller, this::removeAtomBrowser);
            atomBrowser.initSlider(navBar);
        }
    }

    public void removeAtomBrowser(Object o) {
        if (atomBrowser != null) {
            FXMLController controller = FXMLController.getActiveController();
            controller.getBottomBox().getChildren().remove(atomBrowser.getToolBar());
            atomBrowser = null;
        }
    }
    public void pickedPeakAction(Object peakObject) {
        if (assignOnPick.isSelected()) {
            Peak peak = (Peak) peakObject;
            System.out.println(peak.getName());
            PolyChart chart = FXMLController.getActiveController().getActiveChart();
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

}
