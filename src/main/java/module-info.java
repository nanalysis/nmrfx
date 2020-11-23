module org.nmrfx.core {
    exports org.nmrfx.datasets;
    exports org.nmrfx.peaks;
    exports org.nmrfx.peaks.io;
    exports org.nmrfx.star;
    exports org.nmrfx.project;
    exports org.nmrfx.server;
    exports org.nmrfx.utilities;
    requires commons.math3;
    requires org.apache.commons.lang3;
    requires org.apache.commons.collections4;
    requires io.netty.all;
    requires java.logging;
    requires java.desktop;
    requires com.google.common;
    requires jython.slim;
}
