package org.nmrfx.graphicsio;

import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.chart.Axis;

public class PDFWriterTest {

    @Test
    public void testXPKReader() {
        PDFGraphicsContext pdfGC = new PDFGraphicsContext();
        try {
            pdfGC.create(true, 600, 800.0, "test.pdf");
            pdfGC.setFill(Color.GREEN);
            //pdfGC.fillRect(300.0, 300.0, 100.0, 100.0);
//            Axis axisY = new Axis(Orientation.VERTICAL, 0.0, 10.0, 100.0, 600.0);
//            Axis axisX = new Axis(Orientation.HORIZONTAL, 0.0, 10.0, 600.0, 100.0);
//            axisX.draw(pdfGC);
//            axisY.draw(pdfGC);
            //pdfGC.strokeLine(200.0, 500.0, 250.0, 500.0);
            pdfGC.strokeLineNoTrans(0.0, 0.0, 612.0, 612.0);
            pdfGC.translate(120.0, 120.0);
            pdfGC.rotate(45.0);
            pdfGC.setStroke(Color.RED);
            pdfGC.strokeLineNoTrans(0.0, 0.0, 100.0, 0.0);
            pdfGC.setTextAlign(TextAlignment.CENTER);
            pdfGC.setTextBaseline(VPos.BASELINE);
//
            pdfGC.fillText("test", 0.0, 0.0);
            pdfGC.saveFile();
        } catch (GraphicsIOException ex) {
        }
    }

}
