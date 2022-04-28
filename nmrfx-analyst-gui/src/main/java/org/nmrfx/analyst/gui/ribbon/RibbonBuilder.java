package org.nmrfx.analyst.gui.ribbon;

import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.Column;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonTab;
import de.jensd.fx.glyphs.GlyphIcons;
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
import org.nmrfx.processor.gui.PreferencesController;

import java.net.URL;
import java.util.Arrays;
import java.util.Optional;

/**
 * Ribbon structure:
 * - QuickAccess on top to display always visible icons (ex in office: Save, Undo, Redo)
 * - RibbonTab to set top level menus. Each tab can contain several groups
 * - RibbonGroup inside a tab, each group is a block of icons or other widgets
 */
public class RibbonBuilder  {
    private final AnalystApp app;
    private final RibbonActions actions = new RibbonActions();

    public RibbonBuilder(AnalystApp app) {
        this.app = app;
    }

    public Ribbon create() {
        //TODO find a way to go to CHART when a data is opened, and back to HOME when everything is closed

        Ribbon ribbon = new Ribbon();
        ribbon.getTabs().add(createHomeTab());
        ribbon.getTabs().add(createChartTab());

        return ribbon;
    }

    //-- home

    private RibbonTab createHomeTab() {
        RibbonTab home = new RibbonTab("HOME");
        home.getRibbonGroups().add(createHomeOpenGroup());
        home.getRibbonGroups().add(createHomeExportGroup());
        home.getRibbonGroups().add(createHomeViewGroup());
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

    private RibbonGroup createHomePreferencesGroup() {
        Button preferences = createButton("Preferences...", "48x48/settings.png", app::showPreferences);
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
        Button full = createButton( "Full", FontAwesomeIcon.EXPAND, e -> FXMLController.getActiveController().doFull(e));
        Button expand = createButton("Expand", FontAwesomeIcon.SEARCH, e -> FXMLController.getActiveController().doExpand(e));

        Button zoomIn = createButton("In", FontAwesomeIcon.SEARCH_MINUS, e -> FXMLController.getActiveController().doZoom(e, 1.2));
        zoomIn.setOnScroll(actions::zoomOnScroll);

        Button zoomOut =createButton("Out", FontAwesomeIcon.SEARCH_PLUS, e -> FXMLController.getActiveController().doZoom(e, 0.8));
        zoomOut.setOnScroll(actions::zoomOnScroll);

        return createGroup("Zoom",
                column(full, zoomIn),
                column(expand, zoomOut));
    }

    private RibbonGroup createChartScaleGroup() {
        Button auto = createButton( "Auto", FontAwesomeIcon.ARROWS_V, e -> FXMLController.getActiveController().doScale(e, 0.0));
        Button higher = createButton( "Higher", FontAwesomeIcon.ARROW_UP, e -> FXMLController.getActiveController().doScale(e, 0.8));
        higher.setOnScroll(actions::scaleOnScroll);
        Button lower = createButton( "Lower", FontAwesomeIcon.ARROW_DOWN, e -> FXMLController.getActiveController().doScale(e, 1.2));
        lower.setOnScroll(actions::scaleOnScroll);

        return createGroup("Scale", auto, higher, lower);
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
        return button;
    }


    public Button createButton(String title, GlyphIcons icon, EventHandler<MouseEvent> onClick) {
        Text text = new Text(icon.characterToString());
        text.getStyleClass().add("glyph-icon");
        text.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %s;", icon.getFontFamily(), "16px"));

        Button button = new Button(title, text);
        button.setContentDisplay(ContentDisplay.TOP);
        button.setStyle("-fx-font-weight: normal;");
        button.setOnMouseClicked(onClick);
        return button;
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