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

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import org.nmrfx.processor.datasets.vendor.NMRData;
import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

/**
 *
 * @author brucejohnson
 */
public class ReferenceMenuTextField extends MenuTextField {

    ProcessorController processorController;
    String[] mainMenuLabels = {"default", "H2O", "0.0"};
    String[] nucleusMenuLabels = {"C", "D", "H", "N", "P"};
    String[] crosshairMenuItems = {"0.0", "H2O", "DSS", "Acetone", "DMSO"};

    public ReferenceMenuTextField(ProcessorController processorController) {
        super();
        this.processorController = processorController;

        Menu cascadeMenu = new Menu("PPM at Center");
        mButton.getItems().add(cascadeMenu);
        for (String label : mainMenuLabels) {
            MenuItem menuItem = new MenuItem(label);
            cascadeMenu.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> menuAction(event));
        }

        cascadeMenu = new Menu("PPM at Crosshair");
        mButton.getItems().add(cascadeMenu);
        for (String label : crosshairMenuItems) {
            MenuItem menuItem = new MenuItem(label);
            cascadeMenu.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> crossHairMenuAction(event));
        }

        cascadeMenu = new Menu("Indirect Reference");
        mButton.getItems().add(cascadeMenu);
        for (String label : nucleusMenuLabels) {
            MenuItem menuItem = new MenuItem(label);
            cascadeMenu.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> menuAction(event));
        }
    }

    private void menuAction(ActionEvent event) {
        MenuItem menuItem = (MenuItem) event.getSource();
        String menuLabel = menuItem.getText();
        PolyChart chart = processorController.chartProcessor.chart;
        double ppm = chart.crossHairPositions[0][1];
        System.out.println(ppm);
        if (menuLabel.equals("default")) {
            textField.setText("");
        } else {
            textField.setText(menuLabel);
        }

    }

    double getWaterPPM(double temp) {
        double a = -0.009552;
        double b = 5.011718;
        double ppm = a * (temp - 273.0) + b;
        return ppm;

    }

    private void crossHairMenuAction(ActionEvent event) {
        MenuItem menuItem = (MenuItem) event.getSource();
        String menuLabel = menuItem.getText();
        PolyChart chart = processorController.chartProcessor.chart;
        NMRData nmrData = processorController.chartProcessor.getNMRData();
        double ppm = chart.crossHairPositions[0][1];
        System.out.println(ppm);
        double newCenter = 0.0;
        if (menuLabel.equals("0.0")) {
            newCenter = chart.getRefPositionFromCrossHair(0.0);
        } else if (menuLabel.equals("DSS")) {
            newCenter = chart.getRefPositionFromCrossHair(0.0);
        } else if (menuLabel.equals("DMSO")) {
            newCenter = chart.getRefPositionFromCrossHair(2.49);
        } else if (menuLabel.equals("Acetone")) {
            newCenter = chart.getRefPositionFromCrossHair(2.04);
        } else if (menuLabel.equals("H2O")) {
            double waterPPM = 4.73;
            if (nmrData != null) {
                double temp = nmrData.getTempK();
                waterPPM = getWaterPPM(temp);
            }
            newCenter = chart.getRefPositionFromCrossHair(waterPPM);
        }
        textField.setText(String.format("%.4f", newCenter));

    }

}
