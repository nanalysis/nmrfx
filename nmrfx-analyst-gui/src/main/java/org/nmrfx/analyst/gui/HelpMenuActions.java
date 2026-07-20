package org.nmrfx.analyst.gui;

import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.utilities.WebConnect;

import java.io.IOException;

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

        MenuItem tutorialsMenuItem = new MenuItem("Video Tutorials");
        tutorialsMenuItem.setOnAction(this::showTutorialsAction);

        MenuItem versionMenuItem = new MenuItem("Check Version");
        versionMenuItem.setOnAction(this::showVersionAction);

        MenuItem mailingListItem = new MenuItem("Mailing List Site");
        mailingListItem.setOnAction(this::showMailingListAction);

        MenuItem refMenuItem = new MenuItem("NMRFx Publication");
        refMenuItem.setOnAction(e -> showPublicationAction());

        MenuItem openSourceItem = new MenuItem("Open Source Libraries");
        openSourceItem.setOnAction(this::showOpenSourceAction);

        MenuItem slackChannelItem = new MenuItem("Join Slack Channel");
        slackChannelItem.setOnAction(this::joinSlackChannelAction);

        menu.getItems().addAll(slackChannelItem, docsMenuItem, tutorialsMenuItem, webSiteMenuItem, mailingListItem, versionMenuItem, refMenuItem, openSourceItem);
    }

    private void showWebSiteAction(ActionEvent event) {
        app.getHostServices().showDocument("http://nmrfx.org");
    }

    private void showPublicationAction() {
        app.getHostServices().showDocument("https://rdcu.be/eVnKQ");
    }

    private void showDocAction(ActionEvent event) {
        app.getHostServices().showDocument("http://docs.nmrfx.org");
    }

    private void showTutorialsAction(ActionEvent event) {
        app.getHostServices().showDocument("https://nmrfx.org/tutorials/analyst");
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

    private void joinSlackChannelAction(ActionEvent event) {
        String url = "https://join.slack.com/t/nmrfx/shared_invite/zt-42tyhwuo4-0Rp2vZezUL_HEPR9fxGJyA";
        try {
            new ProcessBuilder("open", url).start();
        } catch (IOException e) {
            ExceptionDialog exceptionDialog = new ExceptionDialog(e);
            exceptionDialog.showAndWait();
        }
    }

}
