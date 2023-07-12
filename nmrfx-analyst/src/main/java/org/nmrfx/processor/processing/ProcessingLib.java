package org.nmrfx.processor.processing;

import org.nmrfx.annotations.PythonAPI;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author brucejohnson
 */
@PythonAPI("autoscript")
public class ProcessingLib {
    private static final String FILE_NAME = "resource:psglib/bruker.yaml";
    private static final Map<String, SequenceScript> sequences = new HashMap<>();

    public static class SequenceScript {
        final String name;
        final int nDim;
        final String vendor;
        final String script;
        final List<String> aliases;
        final Map<String, Object> vars;

        public String getName() {
            return name;
        }

        public String getVendor() {
            return vendor;
        }

        public int getNDim() {
            return nDim;
        }

        public String getScript() {
            return script;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public Map<String, Object> getVars() {
            return vars;
        }

        public SequenceScript(String name, List<String> aliases, String vendor, int nDim, String script,
                              Map<String, Object> vars) {
            this.name = name;
            this.vendor = vendor;
            this.nDim = nDim;
            this.script = script;
            this.aliases = aliases;
            this.vars = vars;

        }

    }

    public static void loadYaml(String fileName) throws FileNotFoundException, IOException {
        InputStream input;
        if (fileName.startsWith("resource:")) {
            input = ProcessingLib.class.getClassLoader().getResourceAsStream(fileName.substring(9));
        } else {
            input = new FileInputStream(fileName);
        }
        if (input == null) {
            System.out.println("Couldn't find file " + fileName);

        } else {
            Yaml yaml = new Yaml();
            Map<String, Object> yamlData = (Map<String, Object>) yaml.load(input);
            List<Object> maps = (List<Object>) yamlData.get("sequences");
            process(maps);
        }
    }

    private final static void process(List<Object> maps) {
        for (Object obj : maps) {
            Map<String, Object> map = (Map<String, Object>) obj;
            String name = (String) map.get("name");
            String script = (String) map.get("script");
            String vendor = (String) map.get("vendor");
            int nDim = (Integer) map.get("ndim");
            List<String> aliases = (List<String>) map.get("aliases");
            System.out.println("name " + name);
            System.out.println("aliases " + aliases);
            Map<String, Object> vars = (Map<String, Object>) map.get("vars");
            SequenceScript seqScript = new SequenceScript(name, aliases, vendor, nDim, script, vars);
            sequences.put(name, seqScript);
        }

    }

    private static boolean checkPars(SequenceScript seqScript, String seqName, String vendor, int nDim, int matchMode) {

        boolean ok = seqScript.getVendor().equalsIgnoreCase(vendor) && seqScript.getNDim() == nDim;
        if (ok) {
            if (matchMode == 0) {
                ok = seqScript.getName().equals(seqName);
            } else {
                if (matchMode <= seqScript.aliases.size()) {
                    String alias = seqScript.aliases.get(matchMode - 1);
                    ok = seqName.matches(alias);
                }

            }
        }
        System.out.println(seqScript.getVendor() + " " + vendor + " " + seqScript.getNDim() + " " + nDim + " " + ok);
        return ok;
    }

    public static SequenceScript findSequence(String seqName, String vendor, int nDim) throws IOException {
        if (sequences.isEmpty()) {
            loadYaml(FILE_NAME);
        }
        int matchMode = 0;
        while (true) {
            boolean gotAlias = false;
            for (SequenceScript seqScript : sequences.values()) {
                if (seqScript.aliases.size() >= matchMode) {
                    gotAlias = true;
                    if (checkPars(seqScript, seqName, vendor, nDim, matchMode)) {
                        return seqScript;
                    }
                }
            }
            if (!gotAlias) {
                break;
            }
            matchMode++;
        }
        return null;
    }

}
