package org.nmrfx.chemistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

//public class MoleculeFactory<T extends MoleculeBase> {
/*  Class<T> molClass;
    public MoleculeFactory(Class reference) {
        molClass = reference;
    }

    public T newInstance(String molName)  {
        Class[] parameterType = {String.class};
        Constructor constructor = null;
        T mol = null;
        try {
            constructor = molClass.getDeclaredConstructor(parameterType);
            mol = (T) constructor.newInstance(molName);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return mol;
    }*/
public class MoleculeFactory {

    static Constructor constructor = null;
    static MoleculeBase activeMol = null;
    public static final Map<String, MoleculeBase> molecules = new HashMap<>();

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

    public static void removeMolecule(String name) {
        molecules.remove(name);
    }

    public static void clearAllMolecules() {
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
                    classNotFoundException.printStackTrace();
                }
            }
            if (molClass != null) {
                Class[] parameterType = {String.class};
                try {
                    constructor = molClass.getDeclaredConstructor(parameterType);
                } catch (NoSuchMethodException e) {
                }
            }
        }
        MoleculeBase moleculeBase = null;
        if (constructor != null) {
            try {
                moleculeBase = (MoleculeBase) constructor.newInstance(molName);
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            }
        }
        return moleculeBase;
    }
}
