package org.nmrfx.peaks.io;

import org.nmrfx.peaks.types.PeakListTypes;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class PeakPatternReader {
    public static PeakListTypes loadYaml() throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        try (InputStream istream = cl.getResourceAsStream("peakpat.yaml")) {
            Yaml yaml = new Yaml(new Constructor(PeakListTypes.class));
            return yaml.load(istream);
        }
    }
}
