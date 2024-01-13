package org.nmrfx.analyst.gui.annotations;

import org.nmrfx.analyst.peaks.JournalFormat;
import org.nmrfx.analyst.peaks.JournalFormatPeaks;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.annotations.AnnoText;

/**
 * @author brucejohnson
 */
public class AnnoJournalFormat extends AnnoText {

    String peakListName = null;
    String journalName = "JMedCh";
    JournalFormat format = null;

    public AnnoJournalFormat(double x1, double y1, double width,
                             POSTYPE xPosType, POSTYPE yPosType, String peakListName) {
        super(x1, y1, width, "", 12.0, xPosType, yPosType);

        this.text = "";
        this.peakListName = peakListName;
    }

    public void setJournalName(String journalName) {
        this.journalName = journalName;
        updateText();
    }

    void updateText() {
        text = "";
        if (peakListName != null) {
            PeakList peakList = PeakList.get(peakListName);
            if (peakList != null) {
                if ((format == null) || !format.getName().equals(journalName)) {
                    format = JournalFormatPeaks.getFormat(journalName);
                }
                String journalText = format.genOutput(peakList);
                text = JournalFormatPeaks.formatToPlain(journalText);
            }
        }
    }

    @Override
    public void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world) {
        updateText();
        if (text.length() > 0) {
            super.draw(gC, bounds, world);
        }

    }

}
