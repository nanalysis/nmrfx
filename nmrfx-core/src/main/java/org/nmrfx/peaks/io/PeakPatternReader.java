package org.nmrfx.peaks.io;

import org.nmrfx.peaks.types.PeakListTypes;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class PeakPatternReader {

    private void processYaml(Map<String, Object> yamlData) {
        var typeList = (List<Map<String, Object>>) yamlData.get("types");
        for (Map<String, Object> typeMap : typeList) {
            String name = (String) typeMap.get("name");
            String dims = (String) typeMap.get("dims");
        }
    }

    public static PeakListTypes loadYaml() throws IOException {
        Map<String, Object> yamlData = null;
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        try (InputStream istream = cl.getResourceAsStream("peakpat.yaml")) {
            Yaml yaml = new Yaml(new Constructor(PeakListTypes.class));
            PeakListTypes types = yaml.load(istream);
            return types;
        }
    }

}
