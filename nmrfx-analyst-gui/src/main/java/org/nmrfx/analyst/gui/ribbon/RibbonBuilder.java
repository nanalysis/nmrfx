package org.nmrfx.analyst.gui.ribbon;

import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.Column;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonTab;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PreferencesController;

/**
 * Ribbon structure:
 * - QuickAccess on top to display always visible icons (ex in office: Save, Undo, Redo)
 * - RibbonTab to set top level menus. Each tab can contain several groups
 * - RibbonGroup inside a tab, each group is a block of icons or other widgets
 */
public class RibbonBuilder  {
    private RibbonActions actions = new RibbonActions();

    public Ribbon create() {
        Ribbon ribbon = new Ribbon();
        ribbon.getTabs().add(createHomeTab());
        ribbon.getTabs().add(createDemoTab());

        return ribbon;
    }

    private RibbonTab createDemoTab() {
        RibbonTab demo = new RibbonTab("DEMO");
        demo.getRibbonGroups().add(createDemoGroup("Test"));
        demo.getRibbonGroups().add(createDemoGroup("Two"));
        demo.getRibbonGroups().add(createDemoMenuGroup());
        return demo;
    }

    private RibbonTab createHomeTab() {
        RibbonTab home = new RibbonTab("HOME");
        home.getRibbonGroups().add(createHomeOpenGroup());
        home.getRibbonGroups().add(createHomeExportGroup());

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

        Button browser = new Button("Browser...");
        browser.setOnAction(e -> actions.showDataBrowser());

        Column column = new Column();
        column.getChildren().addAll(openFid, openDataset, browser);

        return createGroup("Open", column);
    }

    private RibbonGroup createHomeExportGroup() {
        MenuButton export = new MenuButton("Export");
        export.disableProperty().bind(FXMLController.activeController.isNull());
        export.getItems().addAll(
                createMenuItem("Export PDF...", e -> FXMLController.getActiveController().exportPDFAction(e)),
                createMenuItem("Export SVG...", e -> FXMLController.getActiveController().exportSVGAction(e)),
                createMenuItem("Export PNG...", e -> FXMLController.getActiveController().exportPNG(e)));

        return createGroup("Export", export);
    }

    private RibbonGroup createGroup(String title, Node... nodes) {
        RibbonGroup group = new RibbonGroup();
        group.setTitle(title);
        group.getNodes().addAll(nodes);
        return group;
    }

    private MenuItem createMenuItem(String title, EventHandler<ActionEvent> onAction) {
        MenuItem exportPdf = new MenuItem(title);
        exportPdf.setOnAction(onAction);
        return exportPdf;
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

        image = new Image(RibbonBuilder.class.getResource("/images/merge.png").toExternalForm());
        imageView = new ImageView(image);
        iconButton = new Button("Underline", imageView);
        iconButton.setContentDisplay(ContentDisplay.TOP);
        iconButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
        ribbonGroup.getNodes().add(iconButton);
        return ribbonGroup;
    }
}