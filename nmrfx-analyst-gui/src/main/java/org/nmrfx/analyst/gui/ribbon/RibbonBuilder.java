package org.nmrfx.analyst.gui.ribbon;

import com.pixelduke.control.ribbon.Column;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonTab;
import de.jensd.fx.glyphs.GlyphIcons;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.ribbon.NmrFxRibbon;

import java.net.URL;
import java.util.Arrays;
import java.util.Optional;

/**
 * Ribbon structure:
 * - QuickAccess on top to display always visible icons (ex in office: Save, Undo, Redo)
 * - RibbonTab to set top level menus. Each tab can contain several groups
 * - RibbonGroup inside a tab, each group is a block of icons or other widgets
 */
public class RibbonBuilder {
    private final AnalystApp app;
    private final RibbonActions actions = new RibbonActions();

    public RibbonBuilder(AnalystApp app) {
        this.app = app;
    }

    public NmrFxRibbon create() {
        //TODO find a way to go to CHART when a data is opened, and back to HOME when everything is closed

        NmrFxRibbon ribbon = new NmrFxRibbon();
        ribbon.getTabs().add(createHomeTab());
        ribbon.getTabs().add(createChartTab());
        ribbon.getTabs().add(createSpectraTab());

        return ribbon;
    }


    //-- home

    private RibbonTab createHomeTab() {
        RibbonTab home = new RibbonTab("HOME");
        home.getRibbonGroups().add(createHomeOpenGroup());
        home.getRibbonGroups().add(createHomeExportGroup()); //XXX looks strange to have exports here
        home.getRibbonGroups().add(createHomeViewGroup());
        home.getRibbonGroups().add(createHomeWindowGroup());
        home.getRibbonGroups().add(createHomePreferencesGroup());

        //TODO handle easy/advanced mode. Missing advanced options
        /*
        MenuItem addMenuItem = new MenuItem("Open Dataset (No Display) ...");
        addMenuItem.setOnAction(e -> FXMLController.getActiveController().addNoDrawAction(e));
        menu.getItems().addAll(addMenuItem);
         */

        return home;
    }

    private RibbonGroup createHomeOpenGroup() {
        // load recent in temporary menus, they will be copied to split menu buttons
        Menu recentFids = new Menu();
        Menu recentDatasets = new Menu();
        PreferencesController.setupRecentMenus(recentFids, recentDatasets);

        SplitMenuButton openFid = new SplitMenuButton();
        openFid.setText("FID...");
        openFid.setOnAction(e -> FXMLController.getActiveController().openFIDAction(e));
        openFid.getItems().setAll(recentFids.getItems());

        SplitMenuButton openDataset = new SplitMenuButton();
        openDataset.setText("Dataset...");
        openDataset.setOnAction(e -> FXMLController.getActiveController().openDatasetAction(e));
        openDataset.getItems().setAll(recentDatasets.getItems());

        Button browser = createSmallButton("Browser...", "16x16/data-browser.png", e -> actions.showDataBrowser());

        return createGroup("Open", column(openFid, openDataset, browser));
    }

    private RibbonGroup createHomeExportGroup() {
        Button pdf = createSmallButton("PDF", "16x16/export-pdf.png", e -> FXMLController.getActiveController().exportPDFAction(e));
        pdf.disableProperty().bind(FXMLController.activeController.isNull());

        Button svg = createSmallButton("SVG", "16x16/export-svg.png", e -> FXMLController.getActiveController().exportSVGAction(e));
        svg.disableProperty().bind(FXMLController.activeController.isNull());

        Button png = createSmallButton("PNG", "16x16/export-png.png", e -> FXMLController.getActiveController().exportPNG(e));
        png.disableProperty().bind(FXMLController.activeController.isNull());

        return createGroup("Export", column(pdf, svg, png));
    }

    private RibbonGroup createHomeViewGroup() {
        Button datasets = createSmallButton("Datasets...", null, app::showDatasetsTable);
        Button attributes = createSmallButton("Attributes...", null, e -> FXMLController.getActiveController().showSpecAttrAction(e));
        Button processor = createSmallButton("Processor", null, e -> actions.toggleProcessorVisibility());
        Button console = createSmallButton("Console...", null, e -> actions.toggleConsoleVisibility());
        Column left = column(datasets, attributes);
        Column right = column(processor, console);
        return createGroup("View", left, right);
    }

    private RibbonGroup createHomeWindowGroup() {
        Button newWindow = createButton("New", "32x32/new_window.png", e -> actions.createNewWindow());
        return createGroup("Window", newWindow);
    }

    private RibbonGroup createHomePreferencesGroup() {
        Button preferences = createButton("Preferences...", "32x32/interface_preferences.png", app::showPreferences);
        return createGroup("Settings", preferences);
    }

    //-- chart tab

    private RibbonTab createChartTab() {
        RibbonTab chart = new RibbonTab("CHART");
        chart.getRibbonGroups().add(createChartZoomGroup());
        chart.getRibbonGroups().add(createChartScaleGroup());
        //TODO replace toolbar actions
        //Refresh
        //Halt
        //Undo/Redo

        return chart;
    }

    private RibbonGroup createChartZoomGroup() {
        Button full = createButton("Full", FontAwesomeIcon.EXPAND, e -> FXMLController.getActiveController().doFull(e));
        Button expand = createButton("Expand", FontAwesomeIcon.SEARCH, e -> FXMLController.getActiveController().doExpand(e));

        Button zoomIn = createButton("In", FontAwesomeIcon.SEARCH_MINUS, e -> FXMLController.getActiveController().doZoom(e, 1.2));
        zoomIn.setOnScroll(actions::zoomOnScroll);

        Button zoomOut = createButton("Out", FontAwesomeIcon.SEARCH_PLUS, e -> FXMLController.getActiveController().doZoom(e, 0.8));
        zoomOut.setOnScroll(actions::zoomOnScroll);

        return createGroup("Zoom",
                column(full, zoomIn),
                column(expand, zoomOut));
    }

    private RibbonGroup createChartScaleGroup() {
        Button auto = createButton("Auto", FontAwesomeIcon.ARROWS_V, e -> FXMLController.getActiveController().doScale(e, 0.0));
        Button higher = createButton("Higher", FontAwesomeIcon.ARROW_UP, e -> FXMLController.getActiveController().doScale(e, 0.8));
        higher.setOnScroll(actions::scaleOnScroll);
        Button lower = createButton("Lower", FontAwesomeIcon.ARROW_DOWN, e -> FXMLController.getActiveController().doScale(e, 1.2));
        lower.setOnScroll(actions::scaleOnScroll);

        return createGroup("Scale", auto, higher, lower);
    }

    //-- spectra tab

    private RibbonTab createSpectraTab() {
        RibbonTab spectra = new RibbonTab("SPECTRA");
        //TODO looks like this could be part of CHART / VIEW tab

        spectra.getRibbonGroups().add(createSpectraGridGroup());
        spectra.getRibbonGroups().add(createSpectraLayoutGroup());
        spectra.getRibbonGroups().add(createSpectraFavoriteGroup());
        spectra.getRibbonGroups().add(createSpectraMiscGroup());

        return spectra;
    }

    private RibbonGroup createSpectraGridGroup() {
        Button define = createSmallButton("Define...", "16x16/grid.png", e -> FXMLController.getActiveController().addGrid());
        Button delete = createSmallButton("Delete selected", "16x16/application_delete.png", e -> FXMLController.getActiveController().getActiveChart().removeSelected());
        Button overlay = createSmallButton("Overlay", null, e -> FXMLController.getActiveController().overlay());
        return createGroup("Grid", column(define, delete, overlay));
    }

    private RibbonGroup createSpectraLayoutGroup() {
        Button horizontal = createSmallButton("Horizontal", "16x16/layout_horizontal.png", e -> FXMLController.getActiveController().arrange(GridPaneCanvas.ORIENTATION.HORIZONTAL));
        Button vertical = createSmallButton("Vertical", "16x16/layout_vertical.png", e -> FXMLController.getActiveController().arrange(GridPaneCanvas.ORIENTATION.VERTICAL));
        Button grid = createSmallButton("Grid", "16x16/layout_grid.png", e -> FXMLController.getActiveController().arrange(GridPaneCanvas.ORIENTATION.GRID));

        //XXX this should be a toggle button instead
        Button minimizeBorders = createSmallButton("Minimize Borders", null, e -> FXMLController.getActiveController().setBorderState(true));
        Button normalBorders = createSmallButton("Normal Borders", null, e -> FXMLController.getActiveController().setBorderState(false));

        return createGroup("Grid Layout",
                column(horizontal, vertical, grid),
                column(minimizeBorders, normalBorders));
    }

    private RibbonGroup createSpectraFavoriteGroup() {
        // XXX saving only works when a project is opened/saved. Shouldn't this be in a different tab then?
        Button save = createButton("Save", FontAwesomeIcon.HEART, e -> actions.saveAsFavorite());
        Button select = createSmallButton("Select...", null, e -> actions.showFavorites());
        return createGroup("Favorites", column(save, select));
    }

    private RibbonGroup createSpectraMiscGroup() {
        //XXX any way to unsync them later on?
        Button syncAxes = createSmallButton("Sync Axes", null, e -> PolyChart.getActiveChart().syncSceneMates());

        //XXX why isn't this in export?
        Button copyAsSvg = createSmallButton("Copy as SVG", null, e -> FXMLController.getActiveController().copySVGAction(e));

        //XXX not sure what these do
        Button showStrips = createSmallButton("Show Strips", null, e -> actions.showStripsBar());
        Button alignSpectra = createSmallButton("Align Spectra", null, e -> FXMLController.getActiveController().alignCenters());

        return createGroup("Misc.",
                column(syncAxes, copyAsSvg),
                column(showStrips, alignSpectra));
    }

    //-- utility functions

    private RibbonGroup createGroup(String title, Node... nodes) {
        RibbonGroup group = new RibbonGroup();
        group.setTitle(title);
        group.getNodes().addAll(nodes);
        return group;
    }

    private Button createSmallButton(String title, String resource, EventHandler<ActionEvent> onAction) {
        Button button = createButton(title, resource, onAction);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setStyle(button.getStyle() + " -fx-alignment: center-left;");
        return button;
    }

    public Button createButton(String title, GlyphIcons icon, EventHandler<MouseEvent> onClick) {
        Button button = new Button(title, glyphIconToNode(icon));
        button.setContentDisplay(ContentDisplay.TOP);
        button.setStyle("-fx-font-weight: normal;");
        button.setOnMouseClicked(onClick);
        return button;
    }

    private Node glyphIconToNode(GlyphIcons icon) {
        Text text = new Text(icon.characterToString());
        text.getStyleClass().add("glyph-icon");
        text.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %s;", icon.getFontFamily(), "16px"));
        return text;
    }

    private Button createButton(String title, String resource, EventHandler<ActionEvent> onAction) {
        Button button = Optional.ofNullable(resource)
                .map(getClass()::getResource)
                .map(URL::toExternalForm)
                .map(Image::new)
                .map(ImageView::new)
                .map(imageView -> new Button(title, imageView))
                .orElseGet(() -> new Button(title));

        button.setContentDisplay(ContentDisplay.TOP);
        button.setStyle("-fx-font-weight: normal;");
        button.setOnAction(onAction);
        return button;
    }

    private MenuItem createMenuItem(String title, EventHandler<ActionEvent> onAction) {
        MenuItem exportPdf = new MenuItem(title);
        exportPdf.setOnAction(onAction);
        return exportPdf;
    }

    private Column column(Region... nodes) {
        Column column = new Column();
        Arrays.stream(nodes).forEach(node -> node.setMaxWidth(Double.MAX_VALUE));
        column.getChildren().addAll(nodes);
        return column;
    }
}