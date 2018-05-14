package org.nmrfx.processor.datasets.peaks;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Bruce Johnson
 */
public class Solvents {

    private static final String SOLVENT_FILE_NAME = "solvents.yaml";
    Map<String, Solvent> solvents = new HashMap<>();

    public void loadYaml() {
        int max = 0;
        Yaml yaml = new Yaml();
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(SOLVENT_FILE_NAME);
        List dataMap = (List) yaml.load(stream);
        for (Object map : dataMap) {
            Map<String, Object> solventMap = (Map<String, Object>) map;
            List<String> synonyms = (List<String>) solventMap.get("synonyms");
            String name = (String) solventMap.get("name");
            String isoname = (String) solventMap.get("isocanon");
            Double h2oShift = (Double) solventMap.get("h2oShift");
            Solvent solvent = new Solvent(name, isoname, synonyms, h2oShift);
            max = Math.max(max, name.length());

            if (solventMap.containsKey("Hshifts")) {
                List<Map<String, Number>> hShifts = (List<Map<String, Number>>) solventMap.get("Hshifts");
                solvent.addShifts("H", hShifts);
            }
            if (solventMap.containsKey("Cshifts")) {
                List<Map<String, Number>> cShifts = (List<Map<String, Number>>) solventMap.get("Cshifts");
                solvent.addShifts("C", cShifts);
            }
            solvents.put(name, solvent);
        }
    }

    public Solvent getSolvent(String name) {
        return solvents.get(name);
    }

}
