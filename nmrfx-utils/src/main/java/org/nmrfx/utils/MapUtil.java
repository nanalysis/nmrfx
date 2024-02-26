package org.nmrfx.utils;

import java.util.HashMap;
import java.util.Map;

public class MapUtil {
    /**
     * Create a new Map with content initialized from the variable arguments. Equivalent to Map.of(..) but will ignore null keys and null values instead of throwing an exception.
     *
     * @param keyValues the key/value pairs to set in the map
     * @return a new Map.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> newMapWithoutNulls(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Key/values must be passed in pair to create a Map!");
        }

        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            K key = (K) keyValues[i];
            V value = (V) keyValues[i + 1];
            if (key != null && value != null) {
                map.put(key, value);
            }
        }

        return map;
    }
}
