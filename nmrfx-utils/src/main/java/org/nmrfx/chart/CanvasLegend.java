package org.nmrfx.chart;

import javafx.collections.ObservableList;
import javafx.geometry.VPos;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsIOException;

/**
 * @author brucejohnson
 */
public class CanvasLegend {

    XYCanvasChart chart;
    Font font;
    double fontSize;
    Text text = new Text();

    public CanvasLegend(XYCanvasChart chart) {
        this.chart = chart;
        fontSize = 14;
        font = Font.font(fontSize);
    }

    double getStringWidth(String s) {
        text.setText(s);
        text.setFont(font);
        final double width = text.getLayoutBounds().getWidth();
        return width;
    }

    public double getLegendHeight() {
        ObservableList<DataSeries> data = chart.getData();
        int nSeries = data.size();
        double width = chart.getXAxis().getWidth();
        double x = 0;
        int nLines = 1;
        for (int i = 0; i < nSeries; i++) {
            DataSeries series = data.get(i);
            String name = series.getName();
            double xIncr = getStringWidth(name) + series.radius * 2 + fontSize;
            if ((x + xIncr) > width) {
                nLines++;
                x = 0.0;
            }
            x += xIncr;
        }
        return nLines * fontSize * 1.2;

    }

    public void draw(GraphicsContextInterface gC) throws GraphicsIOException {
        ObservableList<DataSeries> data = chart.getData();
        int nSeries = data.size();
        double xPos = chart.xPos;
        double yPos = chart.yPos;
        Axis axis = chart.getXAxis();
        double xOrigin = axis.getXOrigin();
        double width = chart.widthProperty().get();
        double height = chart.heightProperty().get();
        gC.setFont(font);
        double xEdge = xPos + xOrigin;
        double yEdge = yPos + height;

        double x = xEdge;
        double y = yEdge - getLegendHeight() + fontSize / 2.0 + 3.0;
        for (int i = 0; i < nSeries; i++) {
            DataSeries series = data.get(i);
            String name = series.getName();
            double xIncr = getStringWidth(name) + series.radius * 2 + fontSize;
            if ((x + xIncr) > width) {
                x = xEdge;
                y += fontSize * 1.2;
            }

            gC.setFill(series.fill);
            gC.setStroke(series.stroke);
            if (series.strokeSymbol || series.fillSymbol) {
                series.symbol.draw(gC, x, y, series.radius, series.stroke, series.fill);
            } else {
                gC.strokeLine(x - 5, y, x + 5, y);
            }
            gC.setTextBaseline(VPos.CENTER);
            gC.setTextAlign(TextAlignment.LEFT);
            gC.fillText(name, x + series.radius * 2, y);
            x += xIncr;
        }
    }

}
