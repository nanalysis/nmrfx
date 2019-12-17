package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.datasets.Dataset;

/**
 *
 * @author brucejohnson
 */
public class AbsMultipletComponent extends MultipletComponent {

    public void setOffset(double offset) {
        this.offset = offset;
    }

    public AbsMultipletComponent(Multiplet multiplet, double offset, double intensity, double volume, double lw) {
        super(multiplet, offset, intensity, volume, lw);
    }

    public RelMultipletComponent toRelative(double center, double sf) {
        return toRelative(multiplet, center, sf);
    }

    public RelMultipletComponent toRelative(Multiplet newMultiplet, double center, double sf) {
        double relOffset = (offset - center) * sf;
        return new RelMultipletComponent(newMultiplet, relOffset, intensity, volume, lineWidth * sf);
    }

    public void getRegion(Dataset theFile, int[] pdim, int[][] p,
            int[] cpt, double[] width) {

        double pc = getOffset();
        double lw = getLineWidth();

        double p1 = pc + Math.abs(lw);
        double p2 = pc - Math.abs(lw);
        p[0][0] = theFile.ppmToFoldedPoint(0, p1);
        p[0][1] = theFile.ppmToFoldedPoint(0, p2);

        cpt[0] = theFile.ppmToFoldedPoint(0, pc);

        width[0] = Math.abs(p[0][0] - p[0][1]);

    }

}
