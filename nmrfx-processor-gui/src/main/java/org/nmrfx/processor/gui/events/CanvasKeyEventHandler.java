package org.nmrfx.processor.gui.events;

import javafx.event.EventHandler;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.HashMap;
import java.util.Map;

public class CanvasKeyEventHandler implements EventHandler<KeyEvent> {
    private final KeyCodeCombination pasteKeyCodeCombination = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
    Map<DataFormat, DataFormatEventHandler> dataFormatHandlers = new HashMap<>();

    @Override
    public void handle(KeyEvent event) {
        if (pasteKeyCodeCombination.match(event)) {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard.getContentTypes().contains(getDataFormat("MDLCT"))) {
                DataFormat df = getDataFormat("MDLCT");
                dataFormatHandlers.get(df).handlePaste(clipboard.getContent(df));
            } else if (clipboard.getContentTypes().contains(DataFormat.PLAIN_TEXT)) {
                dataFormatHandlers.get(DataFormat.PLAIN_TEXT).handlePaste(clipboard.getContent(DataFormat.PLAIN_TEXT));
            }

        } else if (event.getCode() == KeyCode.DELETE) {
            // Iterate over Handlers and break on first successful delete
            for (DataFormatEventHandler e : dataFormatHandlers.values()) {
                if (e.handleDelete()) {
                    break;
                }
            }
        }
    }

    private DataFormat getDataFormat(String format) {
        DataFormat dataFormat = DataFormat.lookupMimeType(format);
        if (dataFormat == null) {
            dataFormat = new DataFormat(format);
        }
        return dataFormat;
    }

    public void addDataFormatHandler(DataFormat dataFormat, DataFormatEventHandler handler) {
        dataFormatHandlers.put(dataFormat, handler);
    }

}
