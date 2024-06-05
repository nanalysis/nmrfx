package org.nmrfx.chemistry.relax;

import java.util.List;
import java.util.Set;

public interface ValueSet {
    public String name();

    public Set<ResonanceSource> resonanceSources();

    public List<? extends  RelaxationValues> rValues();

    public void active(boolean state);

    public boolean active();

}
