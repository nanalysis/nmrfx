package org.nmrfx.analyst.gui.events;

import javafx.application.Platform;
import org.nmrfx.analyst.gui.molecule.MoleculeUtils;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
import org.nmrfx.processor.gui.events.DataFormatEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FilesDataFormatHandler implements DataFormatEventHandler {
    private static final Logger log = LoggerFactory.getLogger(FilesDataFormatHandler.class);


    /**
     * Attempt to add one or more Files to the canvas.
     * If the file(s) are molecule files, all the files
     * will be loaded into memory and the last file of the list will be copied to the chart (provided there is a dataset
     * loaded)
     * If the file(s) are a dataset, then all the files will be copied to the chart in append mode.
     * If the file(s) are a FID, then only the first file will be copied to the chart.
     *
     * @param o     List of Files to add.
     * @param chart The chart to add the files to.
     */
    @Override
    public boolean handlePaste(Object o, PolyChart chart) {
        List<File> files = (List<File>) o;
        if (files.isEmpty()) {
            return false;
        }
        // Check the file type, if there are multiple files it is assumed they are all the same file type
        if (SDFile.isSDFFile(files.get(0).getName())) {
            for (File f : files) {
                try {
                    SDFile.read(f.getAbsolutePath(), null);
                } catch (MoleculeIOException e) {
                    log.error("Unable to read molecule file. {}", e.getMessage());
                    return false;
                }
            }
            PolyChartManager.getInstance().setActiveChart(chart);
            // Only display the last added molecule if there is a dataset already displayed
            if (chart.getDataset() != null) {
                MoleculeUtils.addActiveMoleculeToCanvas();
            }
        } else {
            Platform.runLater(() -> openDataFiles(files, chart));
        }
        return true;
    }

    /**
     * Checks if first file in list is dataset is FID or processed. If it is an FID, the first file is opened, if
     * it is processed, all the files are opened in dataset append mode.
     *
     * @param files The files to open.
     * @param chart The chart to display the files in.
     */
    private void openDataFiles(List<File> files, PolyChart chart) {
        PolyChartManager.getInstance().setActiveChart(chart);
        boolean isFID = false;
        try {
            isFID = NMRDataUtil.getFID(files.get(0).getAbsolutePath()).isFID();
        } catch (IOException e) {
            // If the file can't be opened as an FID NMRData, check if it is a datafile, since processed Bruker files
            // will not open with NMRDataUtil.getFID, otherwise return
            if (NMRDataUtil.isDatasetFile(files.get(0).getAbsolutePath()) == null) {
                log.warn("Unable to open datafiles. {}", e.getMessage(), e);
                return;
            }
        }
        FXMLController controller = chart.getFXMLController();
        if (isFID) {
            controller.openFile(files.get(0).getAbsolutePath(), true, false);
        } else {
            List<String> datasetNames = chart.getDatasetAttributes().stream().map(attr -> (Dataset) attr.getDataset()).map(Dataset::getName).collect(Collectors.toList());
            Set<Integer> dimensions = chart.getDatasetAttributes().stream().map(attr -> (Dataset) attr.getDataset()).map(Dataset::getNDim).collect(Collectors.toSet());
            Dataset dataset;
            List<Dataset> unaddedDatasets = new ArrayList<>();
            for (File file : files) {
                dataset = controller.openDataset(file, true, false);
                if (dataset == null) {
                    continue;
                }
                unaddedDatasets.add(dataset);
                dimensions.add(dataset.getNDim());
                datasetNames.add(dataset.getName());
            }
            // If all the datasets have the same dimension add them in append mode
            if (dimensions.size() == 1) {
                unaddedDatasets.forEach(datasetToAdd -> controller.addDataset(datasetToAdd, true, false));
            } else {
                chart.updateDatasets(datasetNames);
                chart.updateProjections();
                chart.updateProjectionBorders();
                chart.updateProjectionScale();
            }
            try {
                // TODO NMR-6048: remove sleep once threading issue fixed
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            chart.refresh();
        }
    }
}
