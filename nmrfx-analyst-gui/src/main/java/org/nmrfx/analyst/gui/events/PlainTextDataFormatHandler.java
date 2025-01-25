package org.nmrfx.analyst.gui.events;

import javafx.application.Platform;
import org.nmrfx.analyst.gui.molecule.MoleculeUtils;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
import org.nmrfx.processor.gui.events.DataFormatEventHandler;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.OpenChemLibConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Class to handle clipboard PLAIN_TEXT DataFormat
 */
public class PlainTextDataFormatHandler implements DataFormatEventHandler {
    private static final Logger log = LoggerFactory.getLogger(PlainTextDataFormatHandler.class);

    @Override
    public boolean handlePaste(Object o, PolyChart chart) {
        String content = (String) o;
        // Attempt to parse a molecule from the provided string
        if (pasteMolecule(content, chart)) {
            log.info("Parsed molecule from clipboard string.");
            return true;
        } else if (pasteDataset(content, chart)) {
            log.info("Parsed dataset from clipboard string.");
            return true;
        }
        return false;
    }

    /**
     * Attempts to paste a molecule parsed from molString to the canvas.
     *
     * @param molString A string containing a molecule file contents.
     * @return True if molecule is parsed successfully, false otherwise.
     */
    private boolean pasteMolecule(String molString, PolyChart chart) {
        if (chart.getDataset() == null) {
            return false;
        }
        if (SDFile.inMolFileFormat(molString)) {
            // Use the first line of the string as the molecule name if it is not blank, else prompt for a name
            String moleculeName = molString.split("\n")[0].trim();
            if (moleculeName.isEmpty()) {
                moleculeName = MoleculeUtils.moleculeNamePrompt();
            }
            try {
                SDFile.read(moleculeName, molString);
            } catch (MoleculeIOException e) {
                log.error("Unable to read molecule file. {}", e.getMessage());
                return false;
            }
        } else {
            try {
                Molecule molecule = OpenChemLibConverter.parseSmiles("", molString);
                String moleculeName = MoleculeUtils.moleculeNamePrompt();
                MoleculeFactory.renameMolecule(molecule, moleculeName);
                molecule.setActive();
            } catch (IllegalArgumentException iAE) {
                log.error("Unable to parse as SMILES. {}", iAE.getMessage());
                return false;
            }
        }
        PolyChartManager.getInstance().setActiveChart(chart);
        MoleculeUtils.addActiveMoleculeToCanvas();
        return true;
    }

    /**
     * Attempts to paste a dataset parsed from a datasetString to the canvas.
     *
     * @param datasetString A string containing a dataset filename on the first line.
     * @return True if dataset is parsed successfully, false otherwise.
     */
    private boolean pasteDataset(String datasetString, PolyChart chart) {
        String[] items = datasetString.split("\n");
        if (items.length > 0) {
            Dataset dataset = Dataset.getDataset(items[0]);
            if (dataset != null) {
                Platform.runLater(() -> {
                    PolyChartManager.getInstance().setActiveChart(chart);
                    Set<Integer> dimensions = chart.getDatasetAttributes().stream().map(attr -> (Dataset) attr.getDataset()).map(Dataset::getNDim).collect(Collectors.toSet());
                    List<Dataset> datasetsToAdd = Arrays.stream(items).map(Dataset::getDataset).toList();
                    datasetsToAdd.forEach(d -> dimensions.add(d.getNDim()));
                    if (dimensions.size() == 1) {
                        for (Dataset datasetToAdd : datasetsToAdd) {
                            chart.getFXMLController().addDataset(chart, datasetToAdd, true, false);
                        }
                    } else {
                        List<String> datasetNames = chart.getDatasetAttributes().stream().map(attr -> (Dataset) attr.getDataset()).map(Dataset::getName).collect(Collectors.toList());
                        datasetNames.addAll(Arrays.asList(items));
                        chart.updateDatasetsByNames(datasetNames);
                        chart.updateProjections();
                        chart.updateProjectionBorders();
                    }
                    chart.updateProjectionScale();
                    try {
                        // TODO NMR-6048: remove sleep once threading issue fixed
                        TimeUnit.MILLISECONDS.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    chart.refresh();
                });
                return true;
            }
        }
        return false;
    }
}
