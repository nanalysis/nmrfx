package org.nmrfx.analyst.gui.events;

import org.nmrfx.analyst.gui.molecule.CanvasMolecule;
import org.nmrfx.analyst.gui.molecule.MoleculeUtils;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.events.DataFormatEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Class to handle clipboard MDLCT DataFormat.
 */
public class MoleculeDataFormatHandler implements DataFormatEventHandler {
    private static final Logger log = LoggerFactory.getLogger(MoleculeDataFormatHandler.class);

    /**
     * Attempt to add a molecule in MDCLT format to the canvas.
     * @param o ByteBuffer containing molecule information in MDCLT format.
     */
    @Override
    public void handlePaste(Object o) {

        // Don't try to paste the molecule if there is no dataset displayed
        PolyChart activeChart = FXMLController.getActiveController().getActiveChart();
        if (activeChart.getDataset() == null) {
            return;
        }
        String molContents = parseMdlctBuffer((ByteBuffer)  o);
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

    }

    /**
     * Converts MDLCT format into a String with newline line separations.
     * The MDLCT format contains the length of each line on the first byte of the line. Lines will be converted
     * to a new line separated string.
     * @return A string with the clipboard contents or an empty string.
     */
    private String parseMdlctBuffer(ByteBuffer mdlctContent) {

        // mdlct format has first byte of each line as number of characters in line, no line separators included
        StringBuilder molStringBuilder = new StringBuilder();
        int index = 0;
        int charsPerLine;
        while (index < mdlctContent.limit()) {
            charsPerLine = mdlctContent.get(index);
            molStringBuilder.append(StandardCharsets.UTF_8.decode(mdlctContent.slice(++index, charsPerLine))).append("\n");
            index += charsPerLine;
        }

        return molStringBuilder.toString();
    }

    /**
     * Deletes the molecule from the canvas if the mouse is currently hovering over the molecule.
     * @return True if a molecule is deleted, otherwise false.
     */
    @Override
    public boolean handleDelete() {
        PolyChart activeChart = FXMLController.getActiveController().getActiveChart();
        Optional<CanvasAnnotation> anno = activeChart.hitAnnotation(activeChart.getMouseX(), activeChart.getMouseY());
        if (anno.isPresent() && anno.get() instanceof CanvasMolecule) {
            MoleculeUtils.removeMoleculeFromCanvas();
            return true;
        }
        return false;
    }
}
