package org.nmrfx.analyst.gui.ribbon;

import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.Column;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonItem;
import com.pixelduke.control.ribbon.RibbonTab;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
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
        Ribbon ribbon = new Ribbon();
        ribbon.getTabs().add(createHomeTab());
        ribbon.getTabs().add(createDemoTab());

        return ribbon;
    }

    private RibbonTab createDemoTab() {
        RibbonTab demo = new RibbonTab("DEMO");
        demo.getRibbonGroups().add(createDemoGroup("Test"));
        demo.getRibbonGroups().add(createDemoMenuGroup());
        return demo;
    }

    private RibbonTab createHomeTab() {
        RibbonTab home = new RibbonTab("HOME");
        home.getRibbonGroups().add(createHomeOpenGroup());
        home.getRibbonGroups().add(createHomeExportGroup());
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

    private RibbonGroup createHomePreferencesGroup() {
        Button preferences = createButton("Preferences...", "48x48/settings.png", app::showPreferences);
        return createGroup("Settings", preferences);
    }

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

    private RibbonGroup createDemoMenuGroup() {
        RibbonGroup ribbonGroup = new RibbonGroup();
        MenuButton number = new MenuButton("Number");
        number.getItems().addAll(new MenuItem("test1"), new MenuItem("test2"), new MenuItem("test3"), new MenuItem("test4"));
        ribbonGroup.getNodes().add(number);
        return ribbonGroup;
    }

    private RibbonGroup createDemoGroup(String title) {
        RibbonGroup ribbonGroup = new RibbonGroup();
        ribbonGroup.setTitle(title);

        Image image = new Image(RibbonBuilder.class.getResource("/images/bc.png").toExternalForm());
        ImageView imageView = new ImageView(image);
        Button iconButton = new Button("Bold", imageView);
        iconButton.setContentDisplay(ContentDisplay.TOP);
        ribbonGroup.getNodes().add(iconButton);

        image = new Image(RibbonBuilder.class.getResource("/images/extract.png").toExternalForm());
        imageView = new ImageView(image);
        iconButton = new Button("Italic", imageView);
        iconButton.setContentDisplay(ContentDisplay.TOP);
        ribbonGroup.getNodes().add(iconButton);


        RibbonItem item = new RibbonItem();
        item.setLabel("Filter: ");
        image = new Image(RibbonBuilder.class.getResource("/images/merge.png").toExternalForm());
        imageView = new ImageView(image);
        item.setGraphic(imageView);
        item.setItem(new TextField());
        ribbonGroup.getNodes().add(item);


        return ribbonGroup;
    }
}