package org.nmrfx.analyst.peaks;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Bruce Johnson
 */
public class Solvents {

    private static final String SOLVENT_FILE_NAME = "solvents.yaml";
    private static final Map<String, Solvent> solvents = new HashMap<>();

    public static void loadYaml() {
        int max = 0;
        Yaml yaml = new Yaml();
        InputStream stream = Solvents.class.getClassLoader().getResourceAsStream(SOLVENT_FILE_NAME);
        List dataMap = (List) yaml.load(stream);
        for (Object map : dataMap) {
            Map<String, Object> solventMap = (Map<String, Object>) map;
            List<String> synonyms = (List<String>) solventMap.get("synonyms");
            String name = (String) solventMap.get("name");
            String isoname = (String) solventMap.get("isocanon");
            Double h2oShift = (Double) solventMap.get("h2oShift");
            Solvent solvent = new Solvent(name, isoname, synonyms, h2oShift);
            for (String synonym : synonyms) {
                solvents.put(synonym.toLowerCase(), solvent);
            }
            max = Math.max(max, name.length());

            if (solventMap.containsKey("Hshifts")) {
                List<Map<String, Number>> hShifts = (List<Map<String, Number>>) solventMap.get("Hshifts");
                solvent.addShifts("H", hShifts);
            }
            if (solventMap.containsKey("Cshifts")) {
                List<Map<String, Number>> cShifts = (List<Map<String, Number>>) solventMap.get("Cshifts");
                solvent.addShifts("C", cShifts);
            }
            solvents.put(name.toLowerCase(), solvent);
            for (String synonym : synonyms) {
                solvents.put(synonym.toLowerCase(), solvent);
            }
        }
    }

    public static Solvent getSolvent(String name) {
        return solvents.get(name.toLowerCase());
    }

    public static String canonicalIso(String name) {
        Solvent solvent = getSolvent(name);
        return solvent != null ? solvent.isoname : "";
    }

    public static String canonical(String name) {
        Solvent solvent = getSolvent(name);
        return solvent != null ? solvent.name : "";
    }

}
