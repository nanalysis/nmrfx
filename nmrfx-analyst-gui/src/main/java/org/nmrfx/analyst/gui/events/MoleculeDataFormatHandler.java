package org.nmrfx.analyst.gui.events;

import org.nmrfx.analyst.gui.molecule.MoleculeUtils;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
import org.nmrfx.processor.gui.events.DataFormatEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Class to handle clipboard MDLCT DataFormat.
 */
public class MoleculeDataFormatHandler implements DataFormatEventHandler {
    private static final Logger log = LoggerFactory.getLogger(MoleculeDataFormatHandler.class);

    /**
     * Attempt to add a molecule in MDCLT format to the canvas.
     *
     * @param o     ByteBuffer containing molecule information in MDCLT format.
     * @param chart The chart to add the molecule to.
     * @return
     */
    @Override
    public boolean handlePaste(Object o, PolyChart chart) {
        if (chart.getDataset() == null) {
            return false;
        }
        String molContents = parseMdlctBuffer((ByteBuffer) o);
        if (!SDFile.inMolFileFormat(molContents)) {
            return false;
        }
        // Use the first line of the string as the molecule name if it is not blank, else prompt for a name
        String moleculeName = molContents.split("\n")[0].trim();
        if (moleculeName.isEmpty()) {
            moleculeName = MoleculeUtils.moleculeNamePrompt();
        }
        try {
            SDFile.read(moleculeName, molContents);
        } catch (MoleculeIOException e) {
            log.error("Unable to read molecule file. {}", e.getMessage());
            return false;
        }
        PolyChartManager.getInstance().setActiveChart(chart);
        MoleculeUtils.addActiveMoleculeToCanvas();
        return true;
    }

    /**
     * Converts MDLCT format into a String with newline line separations.
     * The MDLCT format contains the length of each line on the first byte of the line. Lines will be converted
     * to a new line separated string.
     *
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
}
