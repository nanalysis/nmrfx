
package org.nmrfx.chemistry;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Bond;

import java.util.List;

/**
 *
 * @author callk
 */
public interface ITree {
    
    public List<Atom> getAtomArray();
    public List<Bond> getBondList();
}
