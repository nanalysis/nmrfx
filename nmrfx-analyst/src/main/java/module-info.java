module org.nmrfx.analyst {
    exports org.nmrfx.analyst.peaks;
    exports org.nmrfx.analyst.compounds;
    exports org.nmrfx.analyst.dataops;
    requires org.nmrfx.core;
    requires org.nmrfx.processor;
    requires ejml.fat;
    requires org.apache.commons.lang3;
    requires commons.math3;
    requires java.logging;
    requires org.yaml.snakeyaml;
    requires org.apache.commons.text;
    requires org.slf4j;
    requires org.nmrfx.structure;
}
