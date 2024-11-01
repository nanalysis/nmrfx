package org.nmrfx.analyst.gui;

import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.nmrfx.processor.utilities.WebConnect;

public class HelpMenuActions extends MenuActions {

    public HelpMenuActions(AnalystApp app, Menu menu) {
        super(app, menu);
    }

    @Override
    public void basic() {
        MenuItem webSiteMenuItem = new MenuItem("NMRFx Web Site");
        webSiteMenuItem.setOnAction(this::showWebSiteAction);

        MenuItem docsMenuItem = new MenuItem("Online Documentation");
        docsMenuItem.setOnAction(this::showDocAction);

        MenuItem versionMenuItem = new MenuItem("Check Version");
        versionMenuItem.setOnAction(this::showVersionAction);

        MenuItem mailingListItem = new MenuItem("Mailing List Site");
        mailingListItem.setOnAction(this::showMailingListAction);

        MenuItem refMenuItem = new MenuItem("NMRFx Publication");
        refMenuItem.setOnAction(e -> showPublicationAction());

        MenuItem openSourceItem = new MenuItem("Open Source Libraries");
        openSourceItem.setOnAction(this::showOpenSourceAction);

        menu.getItems().addAll(docsMenuItem, webSiteMenuItem, mailingListItem, versionMenuItem, refMenuItem, openSourceItem);
    }

    private void showWebSiteAction(ActionEvent event) {
        app.getHostServices().showDocument("http://nmrfx.org");
    }

    private void showPublicationAction() {
        app.getHostServices().showDocument("http://link.springer.com/article/10.1007/s10858-016-0049-6");
    }
    private void showDocAction(ActionEvent event) {
        app.getHostServices().showDocument("http://docs.nmrfx.org");
    }

    private void showVersionAction(ActionEvent event) {
        String onlineVersion = WebConnect.getVersion();
        onlineVersion = onlineVersion.replace('_', '.');
        String currentVersion = AnalystApp.getVersion();
        String text;
        if (onlineVersion.isEmpty()) {
            text = "Sorry, couldn't reach web site";
        } else if (onlineVersion.equals(currentVersion)) {
            text = "You're running the latest version: " + currentVersion;
        } else {
            text = "You're running " + currentVersion;
            text += "\nbut the latest is: " + onlineVersion;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, text);
        alert.setTitle("NMRFx Analyst Version");
        alert.showAndWait();
    }

    private void showMailingListAction(ActionEvent event) {
        app.getHostServices().showDocument("https://groups.io/g/NMRFx");
    }

    private void showOpenSourceAction(ActionEvent event) {
        app.getHostServices().showDocument("https://nmrfx.org/downloads/oss/dependencies.html");
    }
}
