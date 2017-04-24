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

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui.controls;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import static javafx.scene.input.KeyCode.ENTER;
import static javafx.scene.input.KeyCode.S;
import javafx.scene.input.KeyCombination;
import static javafx.scene.input.KeyCombination.CONTROL_DOWN;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.Paragraph;
import org.fxmisc.wellbehaved.event.EventHandlerHelper;
import static org.fxmisc.wellbehaved.event.EventPattern.keyPressed;
import static org.fxmisc.wellbehaved.event.EventPattern.keyTyped;
import org.python.util.InteractiveConsole;
import org.python.util.InteractiveInterpreter;

/**
 *
 * @author brucejohnson
 */
public class ConsoleUtil {

    static Clipboard clipBoard = Clipboard.getSystemClipboard();

    InteractiveInterpreter interpreter;
    CodeArea outputArea;

    protected final List<String> history = new ArrayList<>();
    protected int historyPointer = 0;
    String prompt = ">>>";

    class ConsoleOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            runOnFxThread(() -> outputArea.appendText(String.valueOf((char) b)));
        }

    }

    public static void runOnFxThread(final Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    public void banner() {
        String banner = InteractiveConsole.getDefaultBanner();
        runOnFxThread(() -> outputArea.appendText(banner + "\n"));
    }

    public void prompt() {
        runOnFxThread(() -> outputArea.appendText(prompt));
    }

    public void save() {
        System.out.println("save");
    }

    public void typed(KeyEvent keyEvent) {
        if (keyEvent.isShortcutDown()) {
            return;
        }
        String keyString = keyEvent.getCharacter();
        if ((keyString != null) && (keyString.length() > 0)) {
            char keyChar = keyString.charAt(0);
            if (!Character.isISOControl(keyChar)) {
                outputArea.appendText(keyString);
            }
        }
    }

    public void delete() {
        int nChar = outputArea.getLength();
        int col = outputArea.getCaretColumn();
        if (col > prompt.length()) {
            outputArea.deleteText(nChar - 1, nChar);
        }
    }

    public void historyUp() {
        historyPointer--;
        if (historyPointer < 0) {
            historyPointer = 0;
        }
        getHistory();
    }

    public void historyDown() {
        historyPointer++;
        if (historyPointer > history.size()) {
            historyPointer = history.size();
        }
        getHistory();
    }

    public void getHistory() {
        int nParagraphs = outputArea.getParagraphs().size();
        Paragraph para = outputArea.getParagraph(nParagraphs - 1);
        int nChar = para.length();
        para.delete(prompt.length(), nChar);

        String historyString = "";
        if (historyPointer < history.size()) {
            historyString = history.get(historyPointer);
        }
        int nChars = outputArea.getLength();
        int paraStart = nChars - para.length();

        outputArea.replaceText(paraStart + prompt.length(), nChars, historyString);

    }

    public void paste(KeyEvent event) {
        String string = clipBoard.getString();
        System.out.println("paste " + string);
        if (string != null) {
            outputArea.appendText(string);
        }
    }

    public void copy(KeyEvent event) {
        String text = outputArea.getSelectedText();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipBoard.setContent(content);
    }

    public void enter() {
        int nParagraphs = outputArea.getParagraphs().size();
        Paragraph para = outputArea.getParagraph(nParagraphs - 1);
        String command = para.toString().trim();
        if (command.startsWith(prompt)) {
            command = command.substring(prompt.length());
        }
        command = command.replace("\0", "");
        if (command.length() > 0) {
            history.add(command);
            historyPointer = history.size();
        }
        outputArea.appendText("\n");
        if (command.equals("clear()")) {
            outputArea.clear();
        } else {
            try {
                boolean more = interpreter.runsource(command);
            } catch (Exception e) {
                System.out.println("err " + e.getMessage());
                e.printStackTrace();
                // outputArea.appendText(e.getMessage());
            }
        }
        prompt();
    }

    public void addHandler(CodeArea outputArea, InteractiveInterpreter interpreter) {
        this.outputArea = outputArea;
        this.interpreter = interpreter;
        outputArea.setEditable(false);
        interpreter.setOut(new ConsoleOutputStream());
        interpreter.setErr(new ConsoleOutputStream());

        EventHandler<? super KeyEvent> ctrlS = EventHandlerHelper
                .on(keyPressed(S, CONTROL_DOWN)).act(event -> save())
                .create();

        EventHandlerHelper.install(outputArea.onKeyPressedProperty(), ctrlS);

        EventHandler<? super KeyEvent> enter = EventHandlerHelper
                .on(keyPressed(ENTER)).act(event -> enter())
                .create();

        EventHandlerHelper.install(outputArea.onKeyPressedProperty(), enter);
        EventHandler<? super KeyEvent> backSpace = EventHandlerHelper
                .on(keyPressed(KeyCode.BACK_SPACE)).act(event -> delete())
                .create();
        EventHandler<? super KeyEvent> delete = EventHandlerHelper
                .on(keyPressed(KeyCode.DELETE)).act(event -> delete())
                .create();

        EventHandlerHelper.install(outputArea.onKeyPressedProperty(), delete);
        EventHandlerHelper.install(outputArea.onKeyPressedProperty(), backSpace);

        EventHandler<? super KeyEvent> paste = EventHandlerHelper
                .on(keyPressed(KeyCode.V, KeyCombination.SHORTCUT_DOWN)).act(event -> paste(event))
                .create();

        EventHandlerHelper.install(outputArea.onKeyPressedProperty(), paste);

        EventHandler<? super KeyEvent> copy = EventHandlerHelper
                .on(keyPressed(KeyCode.C, KeyCombination.SHORTCUT_DOWN)).act(event -> copy(event))
                .create();

        EventHandlerHelper.install(outputArea.onKeyPressedProperty(), copy);

        EventHandler<? super KeyEvent> historyUp = EventHandlerHelper
                .on(keyPressed(KeyCode.UP)).act(event -> historyUp())
                .create();

        EventHandlerHelper.install(outputArea.onKeyPressedProperty(), historyUp);

        EventHandler<? super KeyEvent> historyDown = EventHandlerHelper
                .on(keyPressed(KeyCode.DOWN)).act(event -> historyDown())
                .create();

        EventHandlerHelper.install(outputArea.onKeyPressedProperty(), historyDown);

        EventHandler<? super KeyEvent> charTyped = EventHandlerHelper
                .on(keyTyped()).act(event -> typed(event))
                .create();
        EventHandlerHelper.install(outputArea.onKeyTypedProperty(), charTyped);

    }
}
