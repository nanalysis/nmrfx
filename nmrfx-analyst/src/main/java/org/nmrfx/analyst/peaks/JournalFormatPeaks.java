package org.nmrfx.analyst.peaks;

import org.apache.commons.text.TextStringBuilder;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author brucejohnson
 */
public class JournalFormatPeaks {

    private static final Map<String, JournalFormat> journalFormats = new HashMap<>();

    static JournalFormat parseYamlMap(Map<String, Object> map) {
        JournalFormat jformat = new JournalFormat();
        jformat.name = (String) map.get("name");
        jformat.solventMode = (String) map.get("solvent");
        jformat.header = (String) map.get("header");
        jformat.header = (String) map.get("header");
        jformat.m = (String) map.get("m");
        jformat.s = (String) map.get("s");
        jformat.o = (String) map.get("o");
        jformat.sep = (String) map.get("sep");
        jformat.jPrec = (Integer) map.get("jprec");
        jformat.ppmPrec = (Integer) map.get("ppmprec");
        jformat.broad = (Double) map.get("broad");
        return jformat;
    }

    public static void loadYaml() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream("jformat.yaml");
        loadYaml(istream);

    }

    public static void loadYaml(InputStream istream) {
        Yaml yaml = new Yaml();
        List<Map<String, Object>> yamlData = (List<Map<String, Object>>) yaml.load(istream);
        for (Map<String, Object> map : yamlData) {
            JournalFormat journalFormat = parseYamlMap(map);
            journalFormats.put(journalFormat.name, journalFormat);
        }
    }

    public static Set<String> getFormatNames() {
        return journalFormats.keySet();
    }

    public static JournalFormat getFormat(String journal) {
        if (journalFormats.isEmpty()) {
            loadYaml();
        }
        return journalFormats.get(journal);
    }

    public static String formatToRTF(String s) {
        TextStringBuilder sBuilder = new TextStringBuilder(s);
        sBuilder.replaceAll("<html>", "");
        sBuilder.replaceAll("</html>", "");
        sBuilder.replaceAll("<p>", "");
        sBuilder.replaceAll("</p>", "");
        sBuilder.replaceAll("<b>", "\\b ");
        sBuilder.replaceAll("</b>", "\\b0 ");
        sBuilder.replaceAll("<i>", "\\i ");
        sBuilder.replaceAll("</i>", "\\i0 ");
        sBuilder.replaceAll("<sup>", "\\super ");
        sBuilder.replaceAll("</sup>", "\\nosupersub ");
        sBuilder.replaceAll("<sub>", "\\sub ");
        sBuilder.replaceAll("</sub>", "\\nosupersub ");
        sBuilder.replaceAll("\u03b4", "\\uc0\\u948");
        sBuilder.replaceAll("\u03bc", "\\uc0\\u956");
        return sBuilder.toString();
    }

    public static String formatToPlain(String s) {
        TextStringBuilder sBuilder = new TextStringBuilder(s);
        sBuilder.replaceAll("<html>", "");
        sBuilder.replaceAll("</html>", "");
        sBuilder.replaceAll("<p>", "");
        sBuilder.replaceAll("</p>", "");
        sBuilder.replaceAll("<b>", "");
        sBuilder.replaceAll("</b>", "");
        sBuilder.replaceAll("<i>", "");
        sBuilder.replaceAll("</i>", "");
        sBuilder.replaceAll("<sup>", "");
        sBuilder.replaceAll("</sup>", "");
        sBuilder.replaceAll("<sub>", "");
        sBuilder.replaceAll("</sub>", "");
        return sBuilder.toString();
    }
}
