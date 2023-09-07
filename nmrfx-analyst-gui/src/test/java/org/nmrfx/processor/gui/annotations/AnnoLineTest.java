package org.nmrfx.processor.gui.annotations;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class AnnoLineTest {
     @Test
     public void testAnnoLineYaml() throws IOException {
         ClassLoader cl = ClassLoader.getSystemClassLoader();
         String initialString = """
                     x1: 0.0
                     x2: 4.0
                     y1: 1.0
                     y2: 10.0
                     xPosType : FRACTION
                 """;
         try (InputStream istream = new ByteArrayInputStream(initialString.getBytes())) {
             Yaml yaml = new Yaml(new Constructor(AnnoLine.class));
             AnnoLine line =  yaml.load(istream);
             System.out.println(line.getLineWidth());
         }

     }
@Test
    public void testAnnoLineYamlNoConstructor() throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        String initialString = """
                     !!org.nmrfx.processor.gui.annotations.AnnoLine
                     x1: 0.0
                     x2: 4.0
                     y1: 1.0
                     y2: 10.0
                     xPosType : FRACTION
                 """;
        try (InputStream istream = new ByteArrayInputStream(initialString.getBytes())) {
            Yaml yaml = new Yaml();
            AnnoLine line =  yaml.load(istream);
            System.out.println(line.getXPosType());
            System.out.println(line.getLineWidth());
        }

    }

    @Test
    public void testAnnoLineYamlMultipleObjects() throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        String initialString = """
- !!org.nmrfx.processor.gui.annotations.AnnoLine {XPosType: WORLD, YPosType: WORLD,
  clipInAxes: false, fill: '0x000000ff', lineWidth: 1.0, selectable: false, stroke: '0x000000ff',
  x1: 0.0, x2: 4.0, y1: 1.0, y2: 10.0}
- !!org.nmrfx.processor.gui.annotations.AnnoLine {XPosType: WORLD, YPosType: WORLD,
  clipInAxes: false, fill: '0x000000ff', lineWidth: 1.0, selectable: false, stroke: '0x000000ff',
  x1: 0.0, x2: 4.0, y1: 1.0, y2: 10.0}
                """;
        try (InputStream istream = new ByteArrayInputStream(initialString.getBytes())) {
            Yaml yaml = new Yaml();
            var line =  yaml.load(istream);
            System.out.print(line);
        }

    }
    @Test
    public void testAnnoLineDumpYaml() throws IOException {
         AnnoLine annoLine = new AnnoLine(0,1.0,4.0,10.0);
        AnnoLine annoLine2 = new AnnoLine(0,1.0,4.0,10.0);
        ArrayList lines = new ArrayList();
        lines.add(annoLine);
        lines.add(annoLine2);
        Yaml yaml = new Yaml();
        String output = yaml.dump(lines);
        System.out.println(output);
    }

    }