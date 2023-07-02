/*
 * MX - Essential Cheminformatics
 *
 * Copyright (c) 2007-2009 Metamolecular, LLC
 *
 * http://metamolecular.com/mx
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.nmrfx.chemistry;

import java.util.List;

/**
 * @author Bruce A. Johnson <bruce at onemoonscientific.com>
 */
public class Ring {

    private List<Atom> atoms;
    private int ringNumber;

    public Ring(List<Atom> atoms) {
        this.atoms = atoms;
    }

    public List<Atom> getAtoms() {
        return atoms;
    }

    public Atom getAtom(final int i) {
        return atoms.get(i);
    }

    public int size() {
        return atoms.size() - 1;
    }

    public void setRingNumber(int i) {
        this.ringNumber = i;
    }

    public int getRingNumber() {
        return this.ringNumber;
    }
}
