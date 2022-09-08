package org.nmrfx.analyst.gui.events;

import org.nmrfx.analyst.gui.molecule.MoleculeUtils;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.events.DataFormatEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to handle clipboard PLAIN_TEXT DataFormat
 */
public class PlainTextDataFormatHandler implements DataFormatEventHandler {
    private static final Logger log = LoggerFactory.getLogger(PlainTextDataFormatHandler.class);

    @Override
    public void handlePaste(Object o) {
        // Attempt to parse a molecule from the provided string
        if (pasteMolecule((String) o)) {
            log.info("Parsed molecule from clipboard.");
        }
    }

    /**
     * Attempts to paste a molecule parsed from molString to the canvas.
     * @param molString A string containing a molecule file contents.
     * @return True if molecule is parsed successfully, false otherwise.
     */
    private boolean pasteMolecule(String molString) {
        PolyChart activeChart = FXMLController.getActiveController().getActiveChart();
        if (activeChart.getDataset() == null) {
            return false;
        }

        // Not a mol file without multiple lines
        if (molString.indexOf('\n') < 0) {
            return false;
        }

        // Use the first line of the string as the molecule name if it is not blank, else prompt for a name
        String moleculeName = molString.split("\n")[0].trim();
        if (moleculeName.isEmpty()) {
            moleculeName = MoleculeUtils.moleculeNamePrompt();
        }
        try {
            SDFile.read(moleculeName, molString);
        } catch (MoleculeIOException e) {
            log.error("Unable to read molecule file.");
            return false;
        }
        MoleculeUtils.addActiveMoleculeToCanvas();
        return true;
    }
}
