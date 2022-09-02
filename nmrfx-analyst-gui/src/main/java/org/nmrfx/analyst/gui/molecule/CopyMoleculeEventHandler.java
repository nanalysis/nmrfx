package org.nmrfx.analyst.gui.molecule;

import javafx.event.EventHandler;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class CopyMoleculeEventHandler implements EventHandler<KeyEvent> {
    private static final Logger log = LoggerFactory.getLogger(CopyMoleculeEventHandler.class);
    private final KeyCodeCombination keyCodeCombination = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);

    /**
     * Checks for paste (shortcut + v) KeyEvent and delete KeyEvents.Paste will attempt
     * to add a molecule to the canvas from the clipboard. Delete will remove a molecule from the
     * canvas if it exists.
     * @param event the event which occurred
     */
    @Override
    public void handle(KeyEvent event) {
        if (keyCodeCombination.match(event)) {
            // Don't try to paste the molecule if there is no dataset displayed
            PolyChart activeChart = FXMLController.getActiveController().getActiveChart();
            if (activeChart.getDataset() == null) {
                return;
            }
            String molContents = parseClipboard();
            // Not a mol file without multiple lines
            if (molContents.indexOf('\n') < 0) {
                return;
            }
            // Use the first line of the string as the filename
            String filename = molContents.split("\n")[0];
            try {
                SDFile.read(filename, molContents);
            } catch (MoleculeIOException e) {
                log.error("Unable to read molecule file.");
                return;
            }
            MoleculeUtils.addActiveMoleculeToCanvas();
        } else if (event.getCode() == KeyCode.DELETE) {
            MoleculeUtils.removeMoleculeFromCanvas();
        }
    }

    /**
     * Attempts to find a mol file on the clipboard in first MDLCT format or in plain text.
     * The MDLCT format contains the length of each line on the first byte of the line. Lines will be converted
     * to a new line separated string. If no MDLCT format is found, the clipboard is checked
     * for plain text format.
     * @return A string with the clipboard contents or an empty string.
     */
    private String parseClipboard() {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        DataFormat mdlctFormat = DataFormat.lookupMimeType("MDLCT");
        if (mdlctFormat == null) {
            mdlctFormat = new DataFormat("MDLCT");
        }
        ByteBuffer mdlct = (ByteBuffer) clipboard.getContent(mdlctFormat);
        String molContents = "";
        if (mdlct != null) {
            // mdlct format has first byte of each line as number of characters in line, no line separators included
            StringBuilder molStringBuilder = new StringBuilder();
            int index = 0;
            int charsPerLine;
            while (index < mdlct.limit()) {
                charsPerLine = mdlct.get(index);
                molStringBuilder.append(StandardCharsets.UTF_8.decode(mdlct.slice(++index, charsPerLine))).append("\n");
                index += charsPerLine;
            }
            molContents = molStringBuilder.toString();
        } else {
            // Check if mol contents are saved as a plain text
            molContents = (String) clipboard.getContent(DataFormat.PLAIN_TEXT);
            if (molContents == null) {
                log.info("Unable to locate a molecule on clipboard.");
                return "";
            }
        }
        return molContents;
    }
}
