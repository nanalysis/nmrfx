/*
 * CoMD/NMR Software : A Program for Analyzing NMR Dynamics Data
 * Copyright (C) 2018-2019 Bruce A Johnson
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
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;

import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.python.util.InteractiveInterpreter;

/**
 * This class extends from OutputStream to redirect output to a TextArea
 *
 * @author Martha Beckwith
 *
 */
public class ConsoleController extends OutputStream implements Initializable {

    @FXML
    private TextArea textArea;
    Stage stage;
    ArrayList<String> history = new ArrayList<>();
    InteractiveInterpreter interpreter = MainApp.getInterpreter();
    int historyInd = 0;
    KeyCode prevKey = null;

//    public ConsoleRedirect(TextArea textArea) {
//        this.textArea = textArea;
//    }
    public void initialize(URL url, ResourceBundle rb) {
        initializeConsole();
        MainApp.setConsoleController(this);
    }

    public static ConsoleController create() {
        FXMLLoader loader = new FXMLLoader(FXMLController.class.getResource("/fxml/ConsoleScene.fxml"));
        ConsoleController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
//            scene.getStylesheets().add("/styles/consolescene.css");

            controller = loader.<ConsoleController>getController();
            controller.stage = stage;
            stage.setTitle("CoMD/NMR Console");
            stage.show();
            Screen screen = Screen.getPrimary();
            Rectangle2D screenSize = screen.getBounds();
            stage.toFront();
            stage.setY(screenSize.getHeight() - stage.getHeight());
            ConsoleController consoleController = controller;
            stage.setOnCloseRequest(e -> consoleController.close());
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    public void initializeConsole() {

        textArea.setEditable(true);
        textArea.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent keyEvent) {
                consoleInteraction(keyEvent);
            }
        });
        textArea.setOnKeyReleased(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent keyEvent) {
                KeyCode code = keyEvent.getCode();
                if (code == KeyCode.ENTER || code == KeyCode.UP || code == KeyCode.DOWN) {
                    textArea.positionCaret(textArea.getText().lastIndexOf(">") + 2);
                }
            }
        });

        PrintStream printStream = new PrintStream(this);
        // re-assigns standard output stream and error output stream
        System.setOut(printStream);
        System.setErr(printStream);

        interpreter.setOut(printStream);
        interpreter.setErr(printStream);

        textArea.appendText("> ");

    }

    public void write(String text) {
        if (Platform.isFxApplicationThread()) {
            textArea.appendText(text);
            // scrolls the text area to the end of data
            //textArea.positionCaret(String.valueOf((char)b).length());
            textArea.appendText("");
        } else {
            Platform.runLater(() -> {
                textArea.appendText(text);
                // scrolls the text area to the end of data
                //textArea.positionCaret(String.valueOf((char)b).length());
                textArea.appendText("");
            });
        }
    }

    @Override
    public void write(int b) throws IOException {
        // redirects data to the text area
        if (Platform.isFxApplicationThread()) {
            textArea.appendText(String.valueOf((char) b));
            // scrolls the text area to the end of data
            //textArea.positionCaret(String.valueOf((char)b).length());
            textArea.appendText("");
        } else {
            Platform.runLater(() -> {
                textArea.appendText(String.valueOf((char) b));
                // scrolls the text area to the end of data
                //textArea.positionCaret(String.valueOf((char)b).length());
                textArea.appendText("");
            });
        }
    }

    public void clearConsole() {
        textArea.setText("> ");
    }

    public void close() {
        stage.hide();
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    public void consoleInteraction(KeyEvent keyEvent) {
        KeyCode key = keyEvent.getCode();
        String text = textArea.getText();
        int lastLineStart = text.lastIndexOf(">") + 2;
        int lastLineEnd = textArea.getLength();
        String lastLine = text.substring(lastLineStart - 2, lastLineEnd).trim();
        String typed = text.substring(lastLineStart).trim();
        if (key == KeyCode.ENTER & lastLine.equals("> " + typed)) {
            history.add(typed);
            historyInd = history.size();
            if (history.size() == 1 || prevKey == KeyCode.UP || prevKey == KeyCode.DOWN) {
                textArea.appendText("\n");
            }
            interpreter.runsource(typed);
            textArea.appendText("> ");
        } else if (key == KeyCode.ENTER & typed.equals("")) {
            textArea.appendText("> ");
        } else if (key == KeyCode.UP) {
            if (history.size() > 0) {
                prevKey = key;
                historyInd -= 1;
                if (historyInd < 0) {
                    historyInd = 0;
                }
                String command = history.get(historyInd);
                textArea.replaceText(lastLineStart, lastLineEnd, command);
            }
        } else if (key == KeyCode.DOWN) {
            if (history.size() > 0) {
                prevKey = key;
                historyInd += 1;
                if (historyInd > history.size() - 1) {
                    historyInd = history.size() - 1;
                }
                String command = history.get(historyInd);
                textArea.replaceText(lastLineStart, lastLineEnd, command);
            }
        } else if (key == KeyCode.ALPHANUMERIC || key == KeyCode.DIGIT0) {
            prevKey = key;
        }
    }
}
