package org.nmrfx.analyst.gui.events;

import javafx.scene.input.DataFormat;
import org.nmrfx.processor.gui.spectra.KeyBindings;

public class DataFormatHandlerUtil {

    private DataFormatHandlerUtil() {}

    public static void addHandlersToController() {
        KeyBindings.registerCanvasDataFormatHandler(getDataFormat("MDLCT"), new MoleculeDataFormatHandler());
        KeyBindings.registerCanvasDataFormatHandler(DataFormat.PLAIN_TEXT, new PlainTextDataFormatHandler());
    }

    private static DataFormat getDataFormat(String format) {
        DataFormat dataFormat = DataFormat.lookupMimeType(format);
        if (dataFormat == null) {
            dataFormat = new DataFormat(format);
        }
        return dataFormat;
    }

}