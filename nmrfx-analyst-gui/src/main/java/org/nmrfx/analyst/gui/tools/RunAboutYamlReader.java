package org.nmrfx.analyst.gui.tools;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RunAboutYamlReader {

    public static RunAboutArrangements loadYaml() throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        try (InputStream istream = cl.getResourceAsStream("runabout.yaml")) {
            Yaml yaml = new Yaml(new Constructor(RunAboutArrangements.class, new LoaderOptions()));
            return yaml.load(istream);
        }
    }

    public static RunAboutArrangements loadYaml(File file) throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        try (InputStream istream = new FileInputStream(file)) {
            Yaml yaml = new Yaml(new Constructor(RunAboutArrangements.class, new LoaderOptions()));
            return yaml.load(istream);
        }
    }
}
