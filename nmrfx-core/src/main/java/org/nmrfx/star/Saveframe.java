/*
 * NMRFx Processor : A Program for Processing NMR Data
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
 * Saveframe.java
 *
 * Created on February 6, 2007, 8:58 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.nmrfx.star;

import java.util.*;

/**
 * @author brucejohnson
 */
public class Saveframe {

    final STAR3Base star3;
    final String name;
    String saveframeCategory;
    final LinkedHashMap loops = new LinkedHashMap();
    final LinkedHashMap categoryMap = new LinkedHashMap();

    /**
     * Creates a new instance of Saveframe
     */
    public class Category {

        private boolean isLoop = false;
        private final String name;
        final LinkedHashMap<String, String> tagMap = new LinkedHashMap<>();

        private Category(String name) {
            this.name = name;
        }

        void addTag(String tag, String value) {
            tagMap.put(tag, value);
        }

        public String get(String tag) {
            return (String) tagMap.get(tag);
        }

        public List<String> getTags() {
            List<String> list = new ArrayList<String>();
            list.addAll(tagMap.keySet());
            return list;
        }

        public boolean isLoop() {
            return isLoop;
        }

        void setLoop(boolean value) {
            isLoop = value;
        }
    }

    public Category getCategory(String name) {
        Category category = (Category) categoryMap.get(name);
        if (category == null) {
            category = new Category(name);
            categoryMap.put(name, category);
        }
        return category;
    }

    public Saveframe(STAR3Base star3, String name) {
        this.star3 = star3;
        this.name = name;
    }

    public Saveframe(STAR3Base star3, String name, String saveframeCategory) {
        this.star3 = star3;
        this.name = name;
        this.saveframeCategory = saveframeCategory;
    }

    public STAR3Base getSTAR3() {
        return star3;
    }

    public String getName() {
        return name;
    }

    public String getCategoryName() {
        return saveframeCategory;
    }

    public void addValue(final String tagCategory, final String tag, final String value) {
        Category category = getCategory(tagCategory);
        category.addTag(tag, value);
    }

    public void processTokenMap(String tagCategory, Map tokenMap) {
        Category category = getCategory(tagCategory);

        Iterator iter = tokenMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            category.addTag(key, value);
        }
    }

    public void read() throws ParseException {
        if (star3 instanceof MMCIF) {
            saveframeCategory = name;
        }
        Map tokenMap = new LinkedHashMap();
        String currentTagCategory = "";
        while (true) {
            String token = star3.getToken();

            if (token == null) {
                if (star3 instanceof MMCIF) {
                    return;
                }
                throw new ParseException("File exhausted before all tokens found in \"" + name + "\"");
            }
            if (token.equals(STAR3Base.SAVE)) {
                if (saveframeCategory == null) {
                    throw new ParseException("No category for saveframe \"" + name + "\"");
                }
                if (tokenMap.size() != 0) {
                    processTokenMap(currentTagCategory, tokenMap);
                    tokenMap.clear();
                }
                break;
            } else if (token.equals(STAR3Base.LOOP)) {
                if (saveframeCategory == null) {
                    throw new ParseException("No category for saveframe \"" + name + "\"");
                }
                if (tokenMap.size() != 0) {
                    star3.usePrevious = true;
                    processTokenMap(currentTagCategory, tokenMap);
                    tokenMap.clear();
                } else {
                    Loop loop = new Loop(this);
                    String loopName = loop.processLoop();
                    Category category = getCategory(loopName);
                    category.setLoop(true);
                    loops.put(loopName, loop);

                }
            } else {
                String[] tokenPair = STAR3.getTokenPair(token);
                String tagValue = star3.getToken();
                if (tagValue == null) {
                    throw new ParseException("File exhausted getting tokenin \"" + name + "\"");
                }
                if (tokenPair[1].equals("Sf_category")) {
                    saveframeCategory = tagValue;
                } else if (tokenPair[1].equals("sf_category")) {
                    saveframeCategory = tagValue;
                } else if (tokenPair[0].equals(currentTagCategory) || (tokenMap.size() == 0)) {
                    tokenMap.put(tokenPair[1], tagValue);
                } else {
                    processTokenMap(currentTagCategory, tokenMap);
                    tokenMap.clear();
                    tokenMap.put(tokenPair[1], tagValue);
                }
                currentTagCategory = tokenPair[0];
            }
        }
    }

    public String getLabelValue(String tagCategory, String tag) throws ParseException {
        String value = getValue(tagCategory, tag);
        String result = null;
        if (value.equals(".") || value.equals("?")) {
            result = "";
        } else if (value.startsWith("$")) {
            result = value.substring(1);
        } else {
            result = value;
        }
        return result;
    }

    public String getOptionalLabelValue(String tagCategory, String tag) {
        Category category = getCategory(tagCategory);
        String value = category.get(tag);
        String result = "";
        if (value != null) {
            result = value;
            if (value.startsWith("$")) {
                result = value.substring(1);
            }
        }

        return result;
    }

    public int getIntegerValue(String tagCategory, String tag) throws ParseException {
        String value = getValue(tagCategory, tag);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException nfE) {
            throw new ParseException(nfE.getMessage());
        }
    }

    public double getDoubleValue(String tagCategory, String tag) throws ParseException {
        String value = getValue(tagCategory, tag);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException nfE) {
            throw new ParseException(nfE.getMessage());
        }
    }

    public Double getDoubleValue(String tagCategory, String tag, Double defaultValue) throws ParseException {
        String value = getValue(tagCategory, tag);
        if ((value == null) || value.equals(".") || value.equals("?")) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException nfE) {
            throw new ParseException(nfE.getMessage());
        }
    }

    public String getValue(String tagCategory, String tag) throws ParseException {
        Category category = getCategory(tagCategory);
        String result = (String) category.get(tag);
        if (result == null) {
            throw new ParseException("Can't find tag \"" + tagCategory + "." + tag + "\"");
        }

        return result;
    }

    public String getValue(String tagCategory, String tag, String defaultValue) throws ParseException {
        Category category = getCategory(tagCategory);
        String result = (String) category.get(tag);
        if (result == null) {
            result = defaultValue;
        }
        return result;
    }

    public String getOptionalValue(String tagCategory, String tag) throws ParseException {
        Category category = getCategory(tagCategory);
        String value = (String) category.get(tag);
        String result = "";
        if (value != null) {
            result = value;
        }
        return result;
    }

    public double getDoubleValue(String tagCategory, String tag, int loopIndex) throws ParseException {
        String value = getValue(tagCategory, tag, loopIndex);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException nfE) {
            throw new ParseException(nfE.getMessage());
        }
    }

    public int getIntegerValue(String tagCategory, String tag, int loopIndex) throws ParseException {
        String value = getValue(tagCategory, tag, loopIndex);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException nfE) {
            throw new ParseException(nfE.getMessage());
        }
    }

    public String getValue(String tagCategory, String tag, int loopIndex) throws ParseException {
        Loop loop = (Loop) loops.get(tagCategory);

        if (loop == null) {
            throw new ParseException("Can't find loop \"" + tagCategory + "." + tag + "\"");
        }
        String result = loop.getValue(tag, loopIndex);
        return result;
    }

    public List<String> getLoopRow(String tagCategory, int loopIndex) throws ParseException {
        Loop loop = (Loop) loops.get(tagCategory);

        if (loop == null) {
            throw new ParseException("Can't find loop \"" + tagCategory + "\"");
        }
        return loop.getRowValuesAsList(loopIndex);
    }

    public String getValueIfPresent(String tagCategory, String tag, int loopIndex) throws ParseException {
        String result = null;
        Loop loop = (Loop) loops.get(tagCategory);

        if (loop != null) {
            result = loop.getValueIfPresent(tag, loopIndex);
            if ((result != null) && (result.equals(".") || result.equals("?"))) {
                result = null;
            }
        }
        return result;
    }

    public List<String> getCategories() {
        List<String> list = new ArrayList<String>();
        Iterator iter = categoryMap.keySet().iterator();
        while (iter.hasNext()) {
            String key = (String) iter.next();
            list.add(key);
        }
        return list;
    }

    public List<List<String>> getCategories2() throws ParseException {
        List<List<String>> list = new ArrayList<>();
        Iterator iter = categoryMap.keySet().iterator();
        while (iter.hasNext()) {
            String key = (String) iter.next();
            Category category = getCategory(key);
            ArrayList<String> list2 = new ArrayList<>();
            list2.add(key);
            if (category.isLoop) {
                list2.add("1");
            } else {
                list2.add("0");
            }
            list.add(list2);
        }
        return list;
    }

    public List<String> getTags(String tagCategory) throws ParseException {
        List<String> list = new ArrayList<String>();
        Category category = (Category) categoryMap.get(tagCategory);
        if (category == null) {
            throw new ParseException("No category \"" + tagCategory + "\"");
        }
        Iterator iter = category.tagMap.keySet().iterator();
        while (iter.hasNext()) {
            String key = (String) iter.next();
            list.add(key);
        }
        return list;
    }

    public ArrayList<String> getTagsIgnoreMissing(String tagCategory) {
        ArrayList<String> list = new ArrayList<String>();
        Category category = (Category) categoryMap.get(tagCategory);
        if (category != null) {
            category.tagMap.keySet().stream().forEach((key) -> {
                list.add(key);
            });
        }
        return list;
    }

    public void addLoop(String loopName, final List<String> names, final List<String> values) throws ParseException {
        Category category = getCategory(loopName);
        category.setLoop(true);
        Loop loop = new Loop(this);
        loops.put(loopName, loop);
        loop.addValues(names, values);
    }

    public Loop getLoop(String tagCategory) throws ParseException {
        Loop loop = (Loop) loops.get(tagCategory);
        return loop;
    }

    public Map<String, String> getLoopRowMap(String tagCategory, int iRow) throws ParseException {
        Loop loop = (Loop) loops.get(tagCategory);
        if (loop == null) {
            throw new ParseException("Can't find loop \"" + tagCategory + "\"");
        }
        return loop.getRowMap(iRow);
    }

    public int loopCount(String tagCategory) throws ParseException {
        Loop loop = (Loop) loops.get(tagCategory);
        if (loop == null) {
            throw new ParseException("Can't find loop \"" + tagCategory + "\"");
        }
        return loop.getNRows();
    }

    public List<String> getLoopTags(String tagCategory) throws ParseException {
        Loop loop = (Loop) loops.get(tagCategory);

        if (loop == null) {
            throw new ParseException("Can't find loop \"" + tagCategory + "\"");
        }
        return loop.getTags();
    }

    public List<String> getColumn(String tagCategory, String tag) throws ParseException {
        Loop loop = (Loop) loops.get(tagCategory);

        if (loop == null) {
            throw new ParseException("Can't find loop \"" + tagCategory + "." + tag + "\"");
        }
        return loop.getColumn(tag);
    }
}
