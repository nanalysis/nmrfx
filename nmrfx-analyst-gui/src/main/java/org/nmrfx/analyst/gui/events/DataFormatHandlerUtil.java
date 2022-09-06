package org.nmrfx.analyst.gui.events;

import javafx.scene.input.DataFormat;
import org.nmrfx.processor.gui.FXMLController;

public class DataFormatHandlerUtil {

    private DataFormatHandlerUtil() {}

    public static void addHandlersToController(FXMLController controller) {
        controller.addCanvasDataFormatHandler(getDataFormat("MDLCT"), new MoleculeDataFormatHandler());
        controller.addCanvasDataFormatHandler(DataFormat.PLAIN_TEXT, new PlainTextDataFormatHandler());
    }

    private static DataFormat getDataFormat(String format) {
        DataFormat dataFormat = DataFormat.lookupMimeType(format);
        if (dataFormat == null) {
            dataFormat = new DataFormat(format);
        }
        return dataFormat;
    }

}
