package org.nmrfx.chemistry;

/**
 * @author Bruce Johnson
 */
public interface IBond {

    public IAtom getConnectedAtom(IAtom atom);

    public IAtom getAtom(int index);

    public Order getOrder();

    public void setFlag(int flag, boolean status);

    public boolean getFlag(int flag);

}
