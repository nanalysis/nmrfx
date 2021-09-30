/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.datasets;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.nmrfx.math.VecBase;

/**
 *
 * @author brucejohnson
 */
public class DatasetFactory {

    public static DatasetBase newDataset(String fullName, String name, boolean writable, boolean useCacheFile) throws IOException {
        DatasetBase dataset;
        try {
            Class c = Class.forName("org.nmrfx.processor.datasets.Dataset");
            Class[] parameterTypes = {String.class, String.class, boolean.class, boolean.class};
            Constructor constructor = c.getDeclaredConstructor(parameterTypes);
            dataset = (DatasetBase) constructor.newInstance(fullName, name, writable, useCacheFile);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            dataset = new DatasetBase(fullName, name, writable, useCacheFile);
        }
        return dataset;
    }

    public static DatasetBase newDataset(String fullName, String title,
            int[] dimSizes, boolean closeDataset) {
        DatasetBase dataset = null;
        try {
            Class c = Class.forName("org.nmrfx.processor.dataets.Dataset");
            Class[] parameterTypes = {String.class, String.class, int[].class, boolean.class};
            Constructor constructor = c.getDeclaredConstructor(parameterTypes);
            dataset = (DatasetBase) constructor.newInstance(fullName, title, dimSizes, closeDataset);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
        }
        return dataset;
    }

    public static DatasetBase newDataset(VecBase vec) {
        DatasetBase dataset = null;
        try {
            Class c = Class.forName("org.nmrfx.processor.dataets.Dataset");
            Class[] parameterTypes = {VecBase.class};
            Constructor constructor = c.getDeclaredConstructor(parameterTypes);
            dataset = (DatasetBase) constructor.newInstance(vec);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
        }
        return dataset;
    }

}
