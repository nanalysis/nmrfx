/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

module org.nmrfx.utils {
    exports org.nmrfx.utils;
    exports org.nmrfx.utils.properties;
    exports org.nmrfx.chart;
    exports org.nmrfx.console;
    exports org.nmrfx.graphicsio;
    exports org.nmrfx.ribbon;

    requires java.logging;
    requires java.desktop;
    requires java.xml;
    requires org.controlsfx.controls;
    requires javafx.graphicsEmpty;
    requires javafx.graphics;
    requires fontawesomefx;
    requires pdfbox;
    requires fontbox;
    requires commons.logging;
    requires commons.math3;
    requires javafx.controlsEmpty;
    requires javafx.controls;
    requires javafx.baseEmpty;
    requires javafx.base;
    requires javafx.fxml;
    requires javafx.swing;
    requires jython.slim;
    requires org.slf4j;
    requires fxribbon;
}
