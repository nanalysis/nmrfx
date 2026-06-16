package org.nmrfx.processor.gui.annotations;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.ArrayList;
import java.util.List;

public class AnnoLineTest {

    static Yaml getYaml() {
        LoaderOptions opts = new LoaderOptions();

        opts.setTagInspector(tag -> {
            String className = tag.getClassName();
            return className != null
                    && className.startsWith("org.nmrfx.processor.gui.annotations.");
        });

        Constructor constructor = new Constructor(Object.class, opts);

        Yaml yaml = new Yaml(constructor);
        return yaml;

    }

    @Test
    public void testAnnoLineYamlNoConstructor() {
        String initialString = """
                    !!org.nmrfx.processor.gui.annotations.AnnoLine
                    x1: 0.0
                    x2: 4.0
                    y1: 1.0
                    y2: 10.0
                    XPosType : FRACTION
                """;

        Yaml yaml = getYaml();
        AnnoLine line = yaml.load(initialString);
        Assert.assertSame(CanvasAnnotation.POSTYPE.FRACTION, line.getXPosType());
    }

    @Test
    public void testAnnoLineYamlMultipleObjects() {
        String initialString = """
                - !!org.nmrfx.processor.gui.annotations.AnnoLine {XPosType: WORLD, YPosType: WORLD,
                  clipInAxes: false, fill: '0x000000ff', lineWidth: 1.0, stroke: '0x000000ff',
                  x1: 0.0, x2: 4.0, y1: 1.0, y2: 10.0}
                - !!org.nmrfx.processor.gui.annotations.AnnoLine {XPosType: WORLD, YPosType: WORLD,
                  clipInAxes: false, fill: '0x000000ff', lineWidth: 1.0, stroke: '0x000000ff',
                  x1: 0.0, x2: 4.0, y1: 1.0, y2: 10.0}
                                """;
        Yaml yaml = getYaml();
        List<Object> annotations = yaml.load(initialString);
        System.out.print(annotations);
        Assert.assertTrue(annotations.get(0) instanceof AnnoLine);
        Assert.assertTrue(annotations.get(1) instanceof AnnoLine);
    }

    @Test
    public void testAnnoLineDumpYaml() {
        AnnoLine annoLine = new AnnoLine(0, 1.0, 4.0, 10.0);
        AnnoLine annoLine2 = new AnnoLine(0, 1.0, 4.0, 10.0);
        List<AnnoLine> lines = new ArrayList<>();
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

        Assert.assertEquals(output.trim(), validOutput.trim());
    }

}