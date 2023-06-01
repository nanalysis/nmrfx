package org.nmrfx.chemistry;

import java.util.List;

/**
 * @author Bruce Johnson
 */
public interface AtomContainer {

    public int getAtomCount();

    public int getBondCount();

    public IAtom getAtom(int i);

    public IBond getBond(int i);

    public IBond getBond(IAtom atom1, IAtom atom2);

    public List<IAtom> atoms();

    public List<IBond> getBonds(IAtom atom);

    public List<IBond> bonds();

    public List<IAtom> getConnectedAtomsList(IAtom atom);

    public List<IBond> getConnectedBondsList(IAtom atom);

    public int getAtomNumber(IAtom atom);

    public int getBondNumber(IBond bond);

}
