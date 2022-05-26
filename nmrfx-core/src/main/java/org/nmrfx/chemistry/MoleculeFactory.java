package org.nmrfx.chemistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MoleculeFactory {

    private static final Logger log = LoggerFactory.getLogger(MoleculeFactory.class);

    static Constructor constructor = null;
    static MoleculeBase activeMol = null;
    private static  Map<String, MoleculeBase> molecules = new HashMap<>();

    public static MoleculeBase getActive() {
        return activeMol;
    }

    public static void setActive(MoleculeBase molecule) {
        activeMol = molecule;
    }

    public static void putMolecule(MoleculeBase molecule) {
        molecules.put(molecule.getName(), molecule);
    }

    public static MoleculeBase getMolecule(String name) {
        return molecules.get(name);
    }

    public static Collection<MoleculeBase> getMolecules() {
        return molecules.values();
    }

    public static void setMoleculeMap(Map<String, MoleculeBase> newMap) {
        molecules = newMap;
    }

    public static void removeMolecule(String name) {
        molecules.remove(name);
    }

    public static void clearAllMolecules() {
        activeMol = null;
        molecules.clear();
    }

    public static MoleculeBase newMolecule(String molName) {
        Class molClass = null;
        if (constructor == null) {
            try {
                molClass
                        = Class.forName("org.nmrfx.structure.chemistry.Molecule");
            } catch (ClassNotFoundException e) {
                try {
                    molClass
                            = Class.forName("org.nmrfx.chemistry.MoleculeBase");
                } catch (ClassNotFoundException classNotFoundException) {
                    log.warn(classNotFoundException.getMessage(), classNotFoundException);
                }
            }
            if (molClass != null) {
                Class[] parameterType = {String.class};
                try {
                    constructor = molClass.getDeclaredConstructor(parameterType);
                } catch (NoSuchMethodException e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
        MoleculeBase moleculeBase = null;
        if (constructor != null) {
            try {
                moleculeBase = (MoleculeBase) constructor.newInstance(molName);
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                log.warn(e.getMessage(), e);
            }
        }
        if (moleculeBase != null) {
            molecules.put(molName, moleculeBase);
            setActive(moleculeBase);
        }
        return moleculeBase;
    }
}
