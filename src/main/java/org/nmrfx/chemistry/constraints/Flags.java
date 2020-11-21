package org.nmrfx.chemistry.constraints;

@SuppressWarnings({"UnusedDeclaration"})
public
enum Flags {

    REDUNDANT("redundant", 'r') {
    },
    FIXED("fixed", 'f') {
    },
    MAXAMBIG("maxamb", 'a') {
    },
    MINCONTRIB("mincontrib", 'c') {
    },
    DIAGONAL("diagonal", 'd') {
    },
    MINPPM("minppm", 'p') {
    },
    MAXVIOL("maxviol", 'v') {
    },
    LABEL("label", 'l') {
    },
    USER("user", 'u') {
    };
    private String description;
    private char charDesc;

    Flags(String description, char charDesc) {
        this.description = description;
        this.charDesc = charDesc;
    }

    void set(Noe noe) {
        noe.inactivate(this);
    }

    public String getDescription() {
        return description;
    }

    public char getCharDesc() {
        return charDesc;
    }
}
