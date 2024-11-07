package org.nmrfx.chemistry;

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Map;

@PluginAPI("ring")
public class MoleculeFactory {
    private static final Logger log = LoggerFactory.getLogger(MoleculeFactory.class);
    private static Constructor constructor = null;

    private MoleculeFactory() {

    }

    public static MoleculeBase getActive() {
        return ProjectBase.getActive().getActiveMolecule();
    }

    public static void setActive(MoleculeBase molecule) {
        ProjectBase.getActive().setActiveMolecule(molecule);
    }

    public static void putMolecule(MoleculeBase molecule) {
        ProjectBase.getActive().putMolecule(molecule);
    }

    public static MoleculeBase getMolecule(String name) {
        return ProjectBase.getActive().getMolecule(name);
    }

    public static Collection<MoleculeBase> getMolecules() {
        return ProjectBase.getActive().getMolecules();
    }

    public static Collection<String> getMoleculeNames() {
        return ProjectBase.getActive().getMoleculeNames();
    }

    public static void setMoleculeMap(Map<String, MoleculeBase> newMap) {
        ProjectBase.getActive().setMoleculeMap(newMap);
    }

    public static void removeMolecule(String name) {
        ProjectBase.getActive().removeMolecule(name);
    }

    public static void clearAllMolecules() {
        ProjectBase.getActive().clearAllMolecules();
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
        return moleculeBase;
    }

    public static void renameMolecule(MoleculeBase molecule, String newName) {
        removeMolecule(molecule.getName());
        molecule.name = newName;
        putMolecule(molecule);
    }
}
