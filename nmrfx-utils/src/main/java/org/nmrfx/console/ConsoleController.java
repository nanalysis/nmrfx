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
package org.nmrfx.console;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.*;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.utils.FormatUtils;
import org.python.util.InteractiveInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class extends from OutputStream to redirect output to a TextArea
 *
 * @author Martha Beckwith
 */
//TODO uncomment when core & utils are regrouped
//@PluginAPI("ring")
public class ConsoleController extends OutputStream implements Initializable, StageBasedController {
    private static final Logger log = LoggerFactory.getLogger(ConsoleController.class);

    private static ConsoleController consoleController;
    private static InteractiveInterpreter interpreter;
    // Store streamed bytes until flush is called, and all characters are written (required for utf-8 multibyte characters)
    private final List<Byte> bytes = new ArrayList<>();

    protected ScheduledThreadPoolExecutor schedExecutor = new ScheduledThreadPoolExecutor(2);

    @FXML
    private TextArea textArea;
    Stage stage;
    ArrayList<String> history = new ArrayList<>();
    int historyInd = 0;
    KeyCode prevKey = null;
    ScheduledFuture futureUpdate = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeConsole();
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public static ConsoleController getConsoleController() {
        return consoleController;
    }

    public static ConsoleController create(InteractiveInterpreter interpreter, String title) {
        ConsoleController.interpreter = interpreter;

        Stage stage = new Stage(StageStyle.DECORATED);
        stage.setTitle(title);

        consoleController = Fxml.load(ConsoleController.class, "ConsoleScene.fxml")
                .withStage(stage)
                .getController();
        return consoleController;
    }

    public void initializeConsole() {
        textArea.setEditable(true);
        textArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            KeyCode keyCode = event.getCode();
            if (keyCode == KeyCode.BACK_SPACE) {
                filterBackSpace(event);
            } else if (keyCode == KeyCode.ENTER) {
                filterEnter(event);
            } else if (keyCode == KeyCode.DOWN) {
                doHistory(event);
            } else if (keyCode == KeyCode.UP) {
                doHistory(event);
            } else if (keyCode == KeyCode.ALPHANUMERIC || keyCode == KeyCode.DIGIT0) {
                prevKey = keyCode;
            } else if (event.isControlDown()) {
                filterControl(event);
            }
        });
        textArea.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
        });
        textArea.addEventFilter(KeyEvent.KEY_TYPED, event -> {
        });

        PrintStream printStream = new PrintStream(this);

        interpreter.setOut(printStream);
        interpreter.setErr(printStream);

        textArea.appendText("> ");

    }

    public void writeAndRun(String text) {
        KeyEvent dummy = new KeyEvent(null, Event.NULL_SOURCE_TARGET, KeyEvent.KEY_TYPED, "\n", text, KeyCode.ENTER, false, false, false, false);
        Fx.runOnFxThread(() -> {
            textArea.appendText(text);
            filterEnter(dummy);
        });
    }

    public void write(String text) {
        Fx.runOnFxThread(() -> {
            textArea.appendText(text);
            textArea.appendText("");
        });
        startTimer();
    }

    @Override
    public void write(int b) throws IOException {
        bytes.add((byte) b);
    }

    @Override
    public void flush() throws IOException {
        byte[] byteArray = new byte[bytes.size()];
        int i = 0;
        for (Byte b : bytes) {
            byteArray[i] = b;
            i++;
        }
        bytes.clear();
        String newText = new String(byteArray, StandardCharsets.UTF_8);
        super.flush();

        Fx.runOnFxThread(() -> {
            textArea.appendText(newText);
            textArea.appendText("");
        });
        startTimer();
    }

    public void clearConsole() {
        textArea.setText("> ");
        textArea.end();
    }

    public void execScript() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                interpreter.execfile(file.toString());
            } catch (Exception e) {
                ExceptionDialog exceptionDialog = new ExceptionDialog(e);
                exceptionDialog.show();
            }
        }
    }

    public void pwd() {
        try {
            interpreter.exec("print(os.getcwd()),");
        } catch (Exception e) {
            textArea.appendText("\n" + e.getMessage());
        }

        textArea.appendText("\n> ");
    }

    public void cd(String path) {
        try {
            interpreter.set("cdPath", path);
            interpreter.exec("os.chdir(cdPath)");
        } catch (Exception e) {
            textArea.appendText("\n" + e.getMessage());
        }
        textArea.appendText("\n> ");
    }

    public void ls(String filter) {
        if (filter.isBlank()) {
            filter = "*";
        }
        try {
            interpreter.set("filterText", filter);
            interpreter.exec("for f in glob.glob(filterText.encode('utf-8')):\n  print f\n");
        } catch (Exception e) {
            textArea.appendText("\n" + e.getMessage());
        }
        textArea.appendText("\n> ");
    }

    @Override
    public void close() {
        stage.hide();
    }

    public void show() {
        stage.show();
        // if the stage has been minimized, show it
        stage.setIconified(false);
        stage.toFront();
    }

    private void filterControl(KeyEvent keyEvent) {
        keyEvent.consume();
        KeyCode keyCode = keyEvent.getCode();
        if (keyCode == KeyCode.A) {
            String text = textArea.getText();
            int lastLineStart = text.lastIndexOf(">") + 2;
            textArea.positionCaret(lastLineStart);
        } else if (keyCode == KeyCode.E) {
            textArea.end();
        }
    }

    private void filterBackSpace(KeyEvent keyEvent) {
        String text = textArea.getText();
        int lastLineStart = text.lastIndexOf(">") + 2;
        int caretPosition = textArea.getCaretPosition();
        if (caretPosition <= lastLineStart) {
            keyEvent.consume();
        }
    }

    private void filterEnter(KeyEvent keyEvent) {
        keyEvent.consume();
        String typed = getLastTyped();
        textArea.appendText("\n");
        if (!typed.isEmpty()) {
            if (typed.equals("clear")) {
                clearConsole();
                return;
            } else if (typed.equals("pwd")) {
                pwd();
                return;
            } else if (typed.equals("cd")) {
                String homeDir = System.getProperty("user.home");
                cd(homeDir);
                return;
            } else if (typed.startsWith("cd ")) {
                cd(typed.substring(3).trim());
                return;
            } else if (typed.equals("ls")) {
                ls("");
                return;
            } else if (typed.startsWith("ls ")) {
                ls(typed.substring(3).trim());
                return;
            } else {
                history.add(typed);
                historyInd = history.size();
                if (history.size() == 1 || prevKey == KeyCode.UP || prevKey == KeyCode.DOWN) {
                    textArea.appendText("\n");
                }
                interpreter.runsource(FormatUtils.formatStringForPythonInterpreter(typed));
            }
        }
        textArea.appendText("> ");
    }

    void doHistory(KeyEvent keyEvent) {
        KeyCode key = keyEvent.getCode();
        keyEvent.consume();
        if (!history.isEmpty()) {
            prevKey = key;
            String command = "";
            if (key == KeyCode.UP) {
                historyInd -= 1;
                if (historyInd < 0) {
                    historyInd = 0;
                }
                command = history.get(historyInd);
            } else {
                historyInd += 1;
                if (historyInd <= history.size() - 1) {
                    command = history.get(historyInd);
                } else {
                    historyInd = history.size();
                }
            }
            String text = textArea.getText();
            int lastLineStart = text.lastIndexOf(">") + 2;
            int lastLineEnd = textArea.getLength();
            textArea.replaceText(lastLineStart, lastLineEnd, command);
            textArea.end();
        }

    }

    private String getLastTyped() {
        String text = textArea.getText();
        int lastLineStart = text.lastIndexOf(">") + 2;
        return text.substring(lastLineStart).trim();
    }

    private boolean isPromptPresent() {
        String text = textArea.getText();
        int lastLineStart = text.lastIndexOf(">") + 2;
        int lastLineEnd = textArea.getLength();
        return lastLineStart == lastLineEnd;
    }

    class UpdateTask implements Runnable {
        @Override
        public void run() {
            Fx.runOnFxThread(() -> {
                if (!isPromptPresent()) {
                    textArea.appendText("\n> ");
                    textArea.end();
                }
            });
        }
    }

    synchronized void startTimer() {
        if ((schedExecutor != null) && ((futureUpdate == null) || futureUpdate.isDone())) {
            UpdateTask updateTask = new UpdateTask();
            futureUpdate = schedExecutor.schedule(updateTask, 500, TimeUnit.MILLISECONDS);
        }
    }
}
