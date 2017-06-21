/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.gui;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.fxmisc.richtext.InlineCssTextArea;
import org.python.util.InteractiveInterpreter;
import org.renjin.parser.RParser;
import org.renjin.sexp.Environment;
import org.renjin.sexp.SEXP;
import org.renjin.studiofx.StudioSession;
import org.renjin.studiofx.console.ConsoleFx;

/**
 * FXML Controller class
 *
 * @author Bruce Johnson
 */
public class ConsoleController implements Initializable {

    @FXML
    InlineCssTextArea consoleArea;
    StudioSession session = null;
    Stage stage;
    ConsoleFx consoleFx;

    static String initialRCode = "import(org.jfxplot.PlotApp)\n"
            + "import(org.jfxplot.PlotManager)\n"
            + "import(org.jfxplot.StageManager)\n"
            + "import(org.jfxplot.GraphicsState)\n"
            + "import(org.nmrfx.processor.datasets.Dataset)\n"
            + "library(org.jfxplot.plot)\n";

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeConsole();
        MainApp.setConsoleController(this);
    }

    public static ConsoleController create() {
        FXMLLoader loader = new FXMLLoader(SpecAttrWindowController.class.getResource("/fxml/ConsoleScene.fxml"));
        ConsoleController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/consolescene.css");

            controller = loader.<ConsoleController>getController();
            controller.stage = stage;
            stage.setTitle("NMRFx Console");
            stage.show();
            Screen screen = Screen.getPrimary();
            Rectangle2D screenSize = screen.getBounds();
            stage.toFront();
            System.out.println(screenSize.getHeight() + " " + stage.getHeight());
            stage.setY(screenSize.getHeight() - stage.getHeight());
            ConsoleController consoleController = controller;
            stage.setOnCloseRequest(e -> consoleController.close());
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    public Environment getREnvironment() {
        return session.getTopLevelContext().getEnvironment();
    }

    public void initializeConsole() {
        consoleFx = new ConsoleFx();
        consoleFx.setOutputArea(consoleArea);
        consoleFx.addHandler();
        consoleArea.setEditable(true);

        session = new StudioSession();
        session.setStdOut(new PrintWriter(consoleFx.getOut()));
        consoleFx.initInterpreter(session);
        InteractiveInterpreter interpreter = MainApp.getInterpreter();
        interpreter.setOut(consoleFx.getOut());
        interpreter.setErr(consoleFx.getErr());
        consoleFx.addInterpreter("jython", this::runJython);
        Font font = new Font("monospace", 12);
        consoleArea.setFont(font);
        consoleArea.appendText("Logging to:\n" + System.getProperty("java.io.tmpdir") + "/dcengine.log\n");
        SEXP expr = RParser.parseInlineSource(initialRCode);
        session.getTopLevelContext().evaluate(expr);

    }

    void close() {
        stage.hide();
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    public String runJython(String command) {
        InteractiveInterpreter interpreter = MainApp.getInterpreter();
        interpreter.runsource(command);
        return "";
    }

    public void writeOutput(String text) {
        consoleArea.appendText(text);
    }

    public void clearOutput() {
        consoleArea.clear();
        consoleArea.appendText("> ");
    }

    @FXML
    public void toggleInterp(ActionEvent e) {
        String interpName = ((ToggleButton) e.getSource()).getText().toLowerCase();
        consoleFx.setInterpreter(interpName);

    }

}
