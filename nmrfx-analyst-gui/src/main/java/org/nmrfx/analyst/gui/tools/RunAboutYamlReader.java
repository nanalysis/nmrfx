package org.nmrfx.analyst.gui.tools;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class RunAboutYamlReader {

    public static RunAboutArrangements loadYaml() throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        try (InputStream istream = cl.getResourceAsStream("runabout.yaml")) {
            Yaml yaml = new Yaml(new Constructor(RunAboutArrangements.class));
            return yaml.load(istream);
        }
    }
}
