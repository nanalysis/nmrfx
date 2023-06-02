package org.nmrfx.chemistry;

import javax.vecmath.Point2d;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public interface IAtom {

    public int getAtomicNumber();

    public void setFlag(int flag, boolean status);

    public boolean getFlag(int flag);

    public Point2d getPoint2d();

    public void setPoint2d(Point2d pt);

    public void setID(int value);

    public int getID();

    public void setProperty(String name, Object value);

    public Object getProperty(String name);

    public String getSymbol();

    public String getHybridization();

    public List<IBond> getBonds();

    Atom add(String name, String elementName, Order order);

    String getType();

    void setType(String name);

}
