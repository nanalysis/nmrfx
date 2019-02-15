package org.nmrfx.processor.gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.fxmisc.richtext.InlineCssTextArea;
import org.nmrfx.server.Server;

/**
 *
 * @author Martha Beckwith
 */
public class NMRFxServer implements Initializable {

    MainApp mainApp;
    Consumer<String> socketFunction = null;

    Stage serverStage = new Stage();
    @FXML
    InlineCssTextArea portField;
    
    int port = 8021;

    public NMRFxServer() {
        mainApp = MainApp.getMainApp();
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        MainApp.setServer(this);
    }
    
    public static NMRFxServer create() {
        FXMLLoader loader = new FXMLLoader(SpecAttrWindowController.class.getResource("/fxml/NMRFxServerScene.fxml"));
        NMRFxServer server = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        Button startButton = new Button();

        try {
            Scene scene = new Scene((Pane) loader.load(), 400, 100);
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/consolescene.css");

            server = loader.<NMRFxServer>getController();
            server.serverStage = stage;
            stage.setTitle("CoMD Server");

            startButton.setText("Start Server");
            server.portField.appendText(String.valueOf(server.port));
            

            stage.show();
            stage.toFront();
            NMRFxServer CoMDserver = server;
            stage.setOnCloseRequest(e -> CoMDserver.close());
            startButton.setOnAction(e -> CoMDserver.startServer(e));
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return server;

    }
    
    void close() {
        serverStage.hide();
    }

    public void startServer(ActionEvent event) {
        port = Integer.parseInt(portField.getText());
        makePortFile(port, false);
        startSocketListener(port);
    }

    public void startSocketListener(int port) {
        socketFunction = s -> invokeListenerFunction(s);
        Server.startServer(port, socketFunction);
    }

    void invokeListenerFunction(String s) {
        System.out.println("invoke " + s);
        GUIScripter.showPeak(s.split(" ")[2]);
    }
    
    public static int makePortFile(int inputPort, boolean useRandom) {
        String homeDir = System.getProperty("user.home");
        File f = new File(homeDir + "/ports.txt");
        int port = inputPort;
        if (!f.exists() && !f.isDirectory()) {
            try {
                f.createNewFile();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        try {
            f.deleteOnExit();
            FileWriter writer = new FileWriter(f);
            if (useRandom) {
                port = Server.getRandomPort();
            }
            writer.write(String.valueOf(port));
            writer.close();
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String text = null;
            while ((text = reader.readLine()) != null) {
                port = Integer.parseInt(text);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return port;
    }
    
}
