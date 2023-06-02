package org.nmrfx.processor.datasets.vendor;

/**
 * @author brucejohnson
 */
public class VendorPar {

    final String name;
    final String value;

    public VendorPar(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }

}
