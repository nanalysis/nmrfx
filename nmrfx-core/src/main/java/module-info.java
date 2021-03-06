module org.nmrfx.core {
    exports org.nmrfx.datasets;
    exports org.nmrfx.peaks;
    exports org.nmrfx.chemistry;
    exports org.nmrfx.chemistry.io;
    exports org.nmrfx.chemistry.utilities;
    exports org.nmrfx.chemistry.constraints;
    exports org.nmrfx.chemistry.protein;
    exports org.nmrfx.chemistry.relax;
    exports org.nmrfx.chemistry.search;
    exports org.nmrfx.math;
    exports org.nmrfx.math.units;
    exports org.nmrfx.peaks.io;
    exports org.nmrfx.star;
    exports org.nmrfx.project;
    exports org.nmrfx.utilities;
    exports org.nmrfx.peaks.types;
    exports org.nmrfx.peaks.events;
    requires commons.math3;
    requires org.apache.commons.lang3;
    requires org.apache.commons.collections4;
    requires java.logging;
    requires java.desktop;
    requires vecmath;
    requires com.google.common;
    requires jython.slim;
    requires jsch;
    requires com.google.gson;
    requires org.yaml.snakeyaml;
    requires org.slf4j;
}
