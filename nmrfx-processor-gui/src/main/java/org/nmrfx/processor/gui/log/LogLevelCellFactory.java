package org.nmrfx.processor.gui.log;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.scene.control.TableCell;
import javafx.scene.paint.Color;
import org.controlsfx.glyphfont.Glyph;

import ch.qos.logback.classic.Level;

/**
 * Formatter for LogLevel table cells in a LogRecord Table
 */
public class LogLevelCellFactory extends TableCell<LogRecord, LogLevel> {
    private static final String FONT_FAMILY = "FontAwesome";

    @Override
    protected void updateItem(LogLevel logLevel, boolean empty) {
        super.updateItem(logLevel, empty);
        if (empty) {
            setGraphic(null);
        } else {
            // Only set the graphic, as the text is not displayed
            setGraphic(LogLevelCellFactory.selectIcon(logLevel));
        }
    }

    /**
     * Creates a Glyph based on the provided log level.
     * @param logLevel The LogLevel to use
     * @return a Glyph
     */
    public static Glyph selectIcon(LogLevel logLevel) {
        int intValue = logLevel.getLogbackLevel().toInteger();
        if (intValue >= Level.ERROR_INT)
            return new Glyph(FONT_FAMILY, FontAwesomeIcon.CLOSE).color(Color.RED);
        if (intValue >= Level.WARN_INT)
            return new Glyph(FONT_FAMILY, FontAwesomeIcon.WARNING).color(Color.GOLD);
        if (intValue >= Level.INFO_INT)
            return new Glyph(FONT_FAMILY, FontAwesomeIcon.INFO_CIRCLE).color(Color.DEEPSKYBLUE);
        if (intValue >= Level.DEBUG_INT)
            return new Glyph(FONT_FAMILY, "d").color(Color.GREY);
        return new Glyph(FONT_FAMILY, FontAwesomeIcon.ELLIPSIS_H).color(Color.DARKGREY);

    }
}
