package org.nmrfx.peaks;

import org.nmrfx.datasets.DatasetBase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class AbsMultipletComponent extends MultipletComponent {

    public void setOffset(double offset) {
        this.offset = offset;
    }

    public AbsMultipletComponent(Multiplet multiplet, double offset, double intensity, double volume, double lw) {
        super(multiplet, offset, intensity, volume, lw);
    }

    public AbsMultipletComponent(Multiplet multiplet, AbsMultipletComponent component) {
        super(multiplet, component);
    }

    public RelMultipletComponent toRelative(double center, double sf) {
        return toRelative(multiplet, center, sf);
    }

    public RelMultipletComponent toRelative(Multiplet newMultiplet, double center, double sf) {
        double relOffset = (center - offset) * sf;
        return new RelMultipletComponent(newMultiplet, relOffset, intensity, volume, lineWidth * sf);
    }

    public void getRegion(DatasetBase theFile, int[] pdim, int[][] p,
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

    public static List<AbsMultipletComponent> copyList(List<AbsMultipletComponent> comps) {
        return copyList(comps, -1);
    }

    public static List<AbsMultipletComponent> copyList(List<AbsMultipletComponent> comps, int skip) {
        List<AbsMultipletComponent> newList = new ArrayList<>();
        for (int i = 0; i < comps.size(); i++) {
            if (i != skip) {
                AbsMultipletComponent oldComp = comps.get(i);
                AbsMultipletComponent newComp = new AbsMultipletComponent(
                        oldComp.multiplet, oldComp.offset, oldComp.intensity,
                        oldComp.volume, oldComp.lineWidth);
                newList.add(newComp);
            }
        }

        return newList;
    }

}
