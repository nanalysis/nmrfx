package org.nmrfx.analyst.gui.events;

import javafx.application.Platform;
import org.nmrfx.analyst.gui.molecule.MoleculeUtils;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.events.DataFormatEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class FilesDataFormatHandler implements DataFormatEventHandler {
    private static final Logger log = LoggerFactory.getLogger(FilesDataFormatHandler.class);


    /**
     * Attempt to add one or more Files to the canvas.
     * If the file(s) are molecule files, all the files
     * will be loaded into memory and the last file of the list will be copied to the chart (provided there is a dataset
     * loaded)
     * If the file(s) are a dataset, then all the files will be copied to the chart in append mode.
     * If the file(s) are a FID, then only the first file will be copied to the chart.
     * @param o List of Files to add.
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
            chart.setActiveChart();
            // Only display the last added molecule if there is a dataset already displayed
            if (chart.getDataset() != null) {
                MoleculeUtils.addActiveMoleculeToCanvas();
            }
        } else {
            Platform.runLater(() -> {
                chart.setActiveChart();
                boolean isDataset = NMRDataUtil.isDatasetFile(files.get(0).getAbsolutePath()) != null;
                if (isDataset) {
                    for (File file : files) {
                        chart.getController().openDataset(file, true);
                    }
                } else {
                    chart.getController().openFile(files.get(0).getAbsolutePath(), true, false);
                }

            });
        }
        return true;
    }
}