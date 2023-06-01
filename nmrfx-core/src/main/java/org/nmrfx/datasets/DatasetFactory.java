/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.datasets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author brucejohnson
 */
public class DatasetFactory {
    private static final Logger log = LoggerFactory.getLogger(DatasetFactory.class);

    private DatasetFactory() {
    }

    public static DatasetBase newDataset(String fullName, String name, boolean writable, boolean useCacheFile) throws IOException {
        DatasetBase dataset;
        try {
            Class<?> c = Class.forName("org.nmrfx.processor.datasets.Dataset");
            var parameterTypes = new Class[]{String.class, String.class, boolean.class, boolean.class};
            Constructor<?> constructor = c.getDeclaredConstructor(parameterTypes);
            dataset = (DatasetBase) constructor.newInstance(fullName, name, writable, useCacheFile);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            dataset = new DatasetBase(fullName, name, writable, useCacheFile);
        }
        return dataset;
    }

    public static DatasetBase newLinkDataset(String name, String fullName) {
        DatasetBase dataset;
        try {
            Class<?> c = Class.forName("org.nmrfx.processor.datasets.Dataset");
            var argTypes = new Class[]{String.class, String.class};
            Method m = c.getDeclaredMethod("newLinkDataset", argTypes);
            String[] args = {name, fullName};
            log.debug("new link {}", fullName);
            dataset = (DatasetBase) m.invoke(null, (Object[]) args);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException |
                 IllegalArgumentException | InvocationTargetException ex) {
            log.warn(ex.getMessage(), ex);
            dataset = null;
        }

        return dataset;
    }

}
