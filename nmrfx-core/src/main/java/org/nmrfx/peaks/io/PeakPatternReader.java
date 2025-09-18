package org.nmrfx.peaks.io;

import org.nmrfx.peaks.types.PeakListTypes;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;

public class PeakPatternReader {
    public static PeakListTypes loadYaml() throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        var loaderOptions = new LoaderOptions();
        try (InputStream istream = cl.getResourceAsStream("peakpat.yaml")) {
            Yaml yaml = new Yaml(new Constructor(PeakListTypes.class, loaderOptions));
            return yaml.load(istream);
        }
    }
}
