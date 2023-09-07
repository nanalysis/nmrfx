package org.nmrfx.processor.gui.annotations;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AnnoLineTest {
    @Test
    public void testAnnoLineYamlNoConstructor() throws IOException {
        String initialString = """
                    !!org.nmrfx.processor.gui.annotations.AnnoLine
                    x1: 0.0
                    x2: 4.0
                    y1: 1.0
                    y2: 10.0
                    XPosType : FRACTION
                """;
        Yaml yaml = new Yaml();
        AnnoLine line = yaml.load(initialString);
        Assert.assertTrue(line.getXPosType() == CanvasAnnotation.POSTYPE.FRACTION);
    }

    @Test
    public void testAnnoLineYamlMultipleObjects() throws IOException {
        String initialString = """
                - !!org.nmrfx.processor.gui.annotations.AnnoLine {XPosType: WORLD, YPosType: WORLD,
                  clipInAxes: false, fill: '0x000000ff', lineWidth: 1.0, stroke: '0x000000ff',
                  x1: 0.0, x2: 4.0, y1: 1.0, y2: 10.0}
                - !!org.nmrfx.processor.gui.annotations.AnnoLine {XPosType: WORLD, YPosType: WORLD,
                  clipInAxes: false, fill: '0x000000ff', lineWidth: 1.0, stroke: '0x000000ff',
                  x1: 0.0, x2: 4.0, y1: 1.0, y2: 10.0}
                                """;
        Yaml yaml = new Yaml();
        List<Object> annotations = yaml.load(initialString);
        System.out.print(annotations);
        Assert.assertTrue(annotations.get(0) instanceof AnnoLine);
        Assert.assertTrue(annotations.get(1) instanceof AnnoLine);
    }

    @Test
    public void testAnnoLineDumpYaml() throws IOException {
        AnnoLine annoLine = new AnnoLine(0, 1.0, 4.0, 10.0);
        AnnoLine annoLine2 = new AnnoLine(0, 1.0, 4.0, 10.0);
        ArrayList lines = new ArrayList();
        lines.add(annoLine);
        lines.add(annoLine2);
        Yaml yaml = new Yaml();
        String output = yaml.dump(lines);
        String validOutput = """
                - !!org.nmrfx.processor.gui.annotations.AnnoLine {XPosType: WORLD, YPosType: WORLD,
                  arrowFirst: false, arrowLast: false, clipInAxes: false, fill: '0x000000ff', lineWidth: 1.0,
                  stroke: '0x000000ff', x1: 0.0, x2: 4.0, y1: 1.0, y2: 10.0}
                - !!org.nmrfx.processor.gui.annotations.AnnoLine {XPosType: WORLD, YPosType: WORLD,
                  arrowFirst: false, arrowLast: false, clipInAxes: false, fill: '0x000000ff', lineWidth: 1.0,
                  stroke: '0x000000ff', x1: 0.0, x2: 4.0, y1: 1.0, y2: 10.0}""";

        Assert.assertTrue(output.trim().equals(validOutput.trim()));
    }

}