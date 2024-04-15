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
package org.nmrfx.processor.gui.spectra;

import javafx.scene.canvas.Canvas;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.events.DataFormatEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Bruce Johnson
 */
public class DragBindings {
    private static final Logger log = LoggerFactory.getLogger(DragBindings.class);
    private static final Map<DataFormat, DataFormatEventHandler> dataFormatHandlers = new HashMap<>();

    final FXMLController controller;
    final Canvas canvas;

    public DragBindings(FXMLController controller, Canvas canvas) {
        this.canvas = canvas;
        this.controller = controller;
    }

    /**
     * Adds the provided DataFormat event handler to the dataFormatHandler map.
     *
     * @param dataFormat The DataFormat.
     * @param handler    The DataFormat handler.
     */
    public static void registerCanvasDataFormatHandler(DataFormat dataFormat, DataFormatEventHandler handler) {
        dataFormatHandlers.put(dataFormat, handler);
    }

    public void mouseDragDropped(final DragEvent e) {
        final PolyChart chart;
        Optional<PolyChart> dropChart = controller.getChart(e.getX(), e.getY());
        if (dropChart.isPresent()) {
            chart = dropChart.get();
        } else {
            return;
        }
        final Dragboard db = e.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            final List<File> files = db.getFiles();
            if (dataFormatHandlers.containsKey(DataFormat.FILES)) {
                success = dataFormatHandlers.get(DataFormat.FILES).handlePaste(files, chart);
            }

        } else if (db.hasString()) {
            String contentString = db.getString();
            if (dataFormatHandlers.containsKey(DataFormat.PLAIN_TEXT)) {
                success = dataFormatHandlers.get(DataFormat.PLAIN_TEXT).handlePaste(contentString, chart);
            }

        } else {
            log.info("No compatible files dragged.");
        }
        e.setDropCompleted(success);
        e.consume();
    }

    public void mouseDragOver(final DragEvent e) {
        final Dragboard db = e.getDragboard();
        List<File> files = db.getFiles();
        boolean isAccepted = false;
        if (db.hasFiles()) {
            if (!files.isEmpty()) {
                isAccepted = NMRDataUtil.isFIDDir(files.get(0)) != null
                        || NMRDataUtil.isDatasetFile(files.get(0)) != null
                        || SDFile.isSDFFile(files.get(0).getName());
            }
        } else if (db.hasString()) {
            String contentString = db.getString();
            isAccepted = SDFile.inMolFileFormat(contentString) || Dataset.getDataset(contentString.split("\n")[0]) != null;
        } else {
            e.consume();
        }
        if (isAccepted) {
            e.acceptTransferModes(TransferMode.COPY);
        }
    }
}
