package org.nmrfx.chemistry.relax;

public enum RelaxTypes {
    R1("R1"), R2("R2"), R1RHO("R1rho"),
    NOE("NOE"), S2("S2"),
    RQ("RQ"), RAP("RAP");

    private final String name;

    RelaxTypes(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public RelaxTypes get(String name) {
        name = name.toUpperCase();
        if (name.charAt(0) == 'T') {
            name = "R" + name.substring(1);
        }
        return valueOf(name);
    }
}
