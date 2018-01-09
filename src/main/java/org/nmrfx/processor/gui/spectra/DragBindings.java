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

import java.io.File;
import java.io.IOException;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.PolyChart;

/**
 *
 * @author Bruce Johnson
 */
public class DragBindings {

    PolyChart chart;

    public DragBindings(PolyChart chart) {
        this.chart = chart;
    }

    public void mouseDragDropped(final DragEvent e) {
        final Dragboard db = e.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;
            // Only get the first file from the list
            final List<File> files = db.getFiles();
            chart.setActiveChart();
            Platform.runLater(() -> {
                try {
                    boolean isDataset = NMRDataUtil.isDatasetFile(files.get(0).getAbsolutePath()) != null;
                    PolyChart dropChart = (PolyChart) e.getGestureTarget();
                    dropChart.setActiveChart();
                    if (isDataset) {
                        boolean appendFile = true;

                        for (File file : files) {
                            chart.getController().openFile(file.getAbsolutePath(), false, appendFile);
                            appendFile = true;
                        }
                    } else {
                        chart.getController().openFile(files.get(0).getAbsolutePath(), true, false);
                    }
                } catch (IOException e1) {
                    ExceptionDialog dialog = new ExceptionDialog(e1);
                    dialog.showAndWait();
                }
            });
        } else if (db.hasString()) {
            String contentString = db.getString();
            String[] items = contentString.split("\n");
            if (items.length > 0) {
                Dataset dataset = Dataset.getDataset(items[0]);
                if (dataset != null) {
                    success = true;
                    Platform.runLater(() -> {
                        PolyChart dropChart = (PolyChart) e.getGestureTarget();
                        dropChart.setActiveChart();
                        for (String item : items) {
                            Dataset dataset1 = Dataset.getDataset(item);
                            if (dataset1 != null) {
                                chart.getController().addDataset(dataset1, true, false);
                            }
                        }
                    });
                }
            }
        } else {
            System.out.println("no files");
        }
        e.setDropCompleted(success);
        e.consume();
    }

    public void mouseDragOver(final DragEvent e) {
        final Dragboard db = e.getDragboard();

        List<File> files = db.getFiles();
        if (db.hasFiles()) {
            if (files.size() > 0) {
                boolean isAccepted;
                try {
                    isAccepted = NMRDataUtil.isFIDDir(files.get(0).getAbsolutePath()) != null;
                    if (!isAccepted) {
                        isAccepted = NMRDataUtil.isDatasetFile(files.get(0).getAbsolutePath()) != null;
                    }
                } catch (IOException ex) {
                    isAccepted = false;
                }
                if (isAccepted) {
                    chart.setStyle("-fx-border-color: green;"
                            + "-fx-border-width: 1;");
                    e.acceptTransferModes(TransferMode.COPY);
                }
            }
        } else if (db.hasString()) {
            String contentString = db.getString();
            String[] items = contentString.split("\n");
            if (items.length > 0) {
                Dataset dataset = Dataset.getDataset(items[0]);
                if (dataset != null) {
                    chart.setStyle("-fx-border-color: green;"
                            + "-fx-border-width: 1;");
                    e.acceptTransferModes(TransferMode.COPY);
                }
            }
        } else {
            e.consume();
        }
    }
}
