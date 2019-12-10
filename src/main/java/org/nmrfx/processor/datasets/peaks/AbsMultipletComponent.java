package org.nmrfx.processor.datasets.peaks;

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
        double relOffset = (offset - center) / sf;
        return new RelMultipletComponent(newMultiplet, relOffset, intensity, volume, lineWidth);
    }
}
