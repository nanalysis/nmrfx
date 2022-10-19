package org.nmrfx.analyst.gui;

import org.nmrfx.analyst.gui.spectra.StripController;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.GUIScripter;

import java.util.HashMap;
import java.util.Map;

public class GUIScripterAdvanced extends GUIScripter {
    public Map<String, String> strips() {
        AnalystApp app = AnalystApp.getAnalystApp();
        StripController stripController = app.getStripsTool();
        Map<String, String> result = new HashMap<>();
        if (stripController != null) {
            PeakList peakList = stripController.getControlList();
            if (peakList != null) {
                String[] dimNames = stripController.getDimNames();
                String xDim = dimNames[0];
                String zDim = dimNames[1];
                result.put("peaklist", peakList.getName());
                result.put("xdim", xDim);
                result.put("zdim", zDim);
            }
        }
        return result;
    }

   public void strips(String peakListName, String xDim, String zDim) {
       AnalystApp app = AnalystApp.getAnalystApp();
       StripController stripController = app.showStripsBar();
       PeakList peakList = PeakList.get(peakListName);
       stripController.loadFromCharts(peakList, xDim, zDim);
   }
}
