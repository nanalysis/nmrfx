package org.nmrfx.processor.gui.log;

/**
 * Names for the different sections of the code.
 */
public enum LogSection {
    // ordering is important when subpackages can match several sections,
    // therefore, more general ones should be at the end
    PLUGIN("Plugin", "org.nmrfx.plugin.api"),
    STRUCTURE("Structure", "org.nmrfx.structure"),
    JMX_CONNECTOR("JMXConnector", "org.nmrfx.jmx"),
    ANALYST_GUI("AnalystGui", "org.nmrfx.analyst.gui"),
    ANALYST("Analyst", "org.nmrfx.analyst"),
    PROCESSOR_GUI("ProcessorGui", "org.nmrfx.processor.gui"),
    PROCESSOR("Processor", "org.nmrfx.processor"),
    UTILS("Utils", "org.nmrfx.chart", "org.nmrfx.console", "org.nmrfx.graphicsio", "org.nmrfx.utils"),
    CORE("Core", "org.nmrfx.chemistry", "org.nmrfx.datasets", "org.nmrfx.math", "org.nmrfx.peaks",
            "org.nmrfx.project", "org.nmrfx.star", "org.nmrfx.utilities"),
    GENERAL("General");

    private String sectionName;
    private final String[] prefixes;

    LogSection(String sectionName, String... prefixes) {
        this.sectionName = sectionName;
        this.prefixes = prefixes;
    }

    /**
     * Finds a match for loggerName based on the prefixes.
     *
     * @param loggerName The String Logger name
     * @return True if a match is found.
     */
    private boolean matches(String loggerName) {
        for (String p : prefixes) {
            if (loggerName.startsWith(p))
                return true;
        }

        return false;
    }

    public String getSectionName() {
        return sectionName;
    }

    /**
     * Gets the LogSection from the logger name string. The string is expected to be in the format
     * xxx.xxx.xxx etc where '.' separates package names.
     *
     * @param loggerName The string logger name
     * @return The matching LogSection or the GENERAL section if no match is found.
     */
    public static LogSection fromLoggerNameString(String loggerName) {
        for (LogSection section : values()) {
            if (section.matches(loggerName))
                return section;
        }
        return GENERAL;
    }

    /**
     * Get the LogSection from the log section string. This string is expected
     * to be one of the LogSection.sectionName options.
     *
     * @param logSectionName The log section name string.
     * @return The matching LogSection or the GENERAL section if no match is found.
     */
    public static LogSection fromLogSectionNameString(String logSectionName) {
        for (LogSection section : values()) {
            if (section.getSectionName().matches(logSectionName))
                return section;
        }
        return GENERAL;
    }

}
