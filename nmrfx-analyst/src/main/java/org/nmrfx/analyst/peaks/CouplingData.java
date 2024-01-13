package org.nmrfx.analyst.peaks;

import org.nmrfx.peaks.CouplingItem;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class CouplingData {

    final List<CouplingItem> couplingItems = new ArrayList<>();
    final double centerPPM;
    final int nPeaks;

    CouplingData(List<CouplingItem> couplingItems, double center, int nPeaks) {
        this.couplingItems.addAll(couplingItems);
        this.centerPPM = center;
        this.nPeaks = nPeaks;
    }

    CouplingData(double center) {
        this.centerPPM = center;
        this.nPeaks = 1;
    }

    CouplingData(double center, int nPeaks) {
        this.centerPPM = center;
        this.nPeaks = nPeaks;
    }

    CouplingData(double center, double coupling, int nSplits, int nPeaks) {
        this.centerPPM = center;
        CouplingItem item = new CouplingItem(coupling, nSplits);
        couplingItems.add(item);
        this.nPeaks = nPeaks;
    }

    public double getCenter() {
        return centerPPM;
    }

    public double getNPeaks() {
        return nPeaks;
    }

    public String getCouplingString() {
        if (couplingItems.size() == 0) {
            if (nPeaks == 1) {
                return "";
            } else {
                return "m";
            }
        } else {
            StringBuilder sBuilder = new StringBuilder();
            couplingItems.forEach((couplingItem) -> {
                sBuilder.append(" ").append(couplingItem.coupling()).append(" ").append(couplingItem.nSplits() - 1);
            });
            return sBuilder.toString().trim();
        }
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(centerPPM);
        couplingItems.forEach((couplingItem) -> {
            sBuilder.append(" ").append(couplingItem.coupling()).append(" ").append(couplingItem.nSplits());
        });
        return sBuilder.toString();
    }

}
