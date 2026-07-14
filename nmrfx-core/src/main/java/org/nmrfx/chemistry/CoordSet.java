/*
 * NMRFx Structure : A Program for Calculating Structures
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * CoordSet.java
 *
 * Created on October 7, 2003, 11:13 AM
 */
package org.nmrfx.chemistry;


import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Johnbruc
 */
public class CoordSet {

    public Map<String, Entity> entities = new LinkedHashMap<String, Entity>();
    private final String name;
    private int id = 0;

    public CoordSet(String name, int id, Entity entity) {
        this.name = name;
        this.id = id;
        addEntity(entity);
        entity.coordSet = this;
    }

    /**
     * Creates a new instance of CoordSet
     */
    public Map<String, Entity> getEntities() {
        return entities;
    }

    public String getName() {
        return name;
    }

    public int getID() {
        return id;
    }

    public void addEntity(Entity entity) {
        entities.put(entity.getName(), entity);
        entity.coordSet = this;
    }

    public int removeEntity(Entity entity) {
        entities.remove(entity.getName());
        return entities.size();
    }
}
