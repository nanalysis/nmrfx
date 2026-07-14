package org.nmrfx.chemistry;

import org.nmrfx.utilities.NMRFxColor;

public record SelectionPoint(Atom atom, Point3 pt1, int selectionLevel) {
}
