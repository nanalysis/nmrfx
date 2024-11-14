package org.nmrfx.analyst.gui.tools;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class SliderLayoutReader {
    private SliderLayoutReader() {

    }
    public static SliderLayoutGroup loadYaml() throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        try (InputStream istream = cl.getResourceAsStream("sliderlayouts.yaml")) {
            Yaml yaml = new Yaml(new Constructor(SliderLayoutGroup.class));
            return yaml.load(istream);
        }
    }
    public static SliderLayoutGroup loadYaml(File file) throws IOException {
        try (InputStream istream = new FileInputStream(file)) {
            Yaml yaml = new Yaml(new Constructor(SliderLayoutGroup.class));
            return yaml.load(istream);
        }
    }
}
