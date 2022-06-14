/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.datasets;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

    public static DatasetBase newLinkDataset(String name, String fullName) throws IOException {
        DatasetBase dataset;
        try {
            Class c = Class.forName("org.nmrfx.processor.datasets.Dataset");
            Class[] argTypes = {String.class, String.class};
            Method m = c.getDeclaredMethod("newLinkDataset", argTypes);
            String[] args = {name, fullName};
            System.out.println("new link " + fullName);
            dataset = (DatasetBase) m.invoke(null, args);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            System.out.println("exc " + ex.getMessage());
            dataset = null;
        }

        return dataset;
    }

}
