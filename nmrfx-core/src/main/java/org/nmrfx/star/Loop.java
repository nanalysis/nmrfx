/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2021 One Moon Scientific, Inc., Westfield, N.J., USA
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
 * Loop.java
 */
package org.nmrfx.star;

import java.util.*;

/**
 * @author brucejohnson
 */
public class Loop {

    String name = "";
    ArrayList<String>[] columns = null;
    final HashMap<String, ArrayList<String>> loopTags = new HashMap<>();
    ArrayList<String> tags = new ArrayList();
    private int nTags;
    private int nRows = 0;
    final Saveframe saveFrame;

    /**
     * Creates a new instance of Loop
     *
     * @param saveFrame the saveframe this loop is in
     */
    public Loop(Saveframe saveFrame) {
        this.saveFrame = saveFrame;
    }

    public int getNRows() {
        return nRows;
    }

    public void addRow(String[] values) throws ParseException {
        if (values.length != nTags) {
            throw new ParseException("Invalid number of values for adding row to loop");

        }
        for (int i = 0; i < nTags; i++) {
            columns[i].add(values[i]);
        }
    }

    public String[] getLoopRow(int nTokens) throws ParseException {
        STAR3Base star3 = saveFrame.getSTAR3();
        String token = star3.getToken();
        if (star3 instanceof MMCIF) {
            if (token == null) {
                return null;
            }
            if (token.startsWith("_")) {
                star3.unGetToken();
                return null;
            }
            if (token.equals(STAR3Base.LOOP)) {
                star3.unGetToken();
                return null;
            }
        } else {
            if (token == null) {
                throw new ParseException("File exhausted before all tokens found in loop of \"" + saveFrame.name + "\"");
            }
        }
        String[] tokenRow = null;
        if (!token.equals(STAR3Base.STOP)) {
            tokenRow = new String[nTokens];
            tokenRow[0] = token;
            for (int i = 1; i < nTokens; i++) {
                tokenRow[i] = star3.getToken();
                if (tokenRow[i] == null) {
                    throw new ParseException("File exhausted before all tokens found in row \"" + nRows + "\" in loop of \"" + saveFrame.name + "\"");
                }
                if (tokenRow[i].equals(STAR3Base.STOP)) {
                    throw new ParseException("Found stop_ at unexpected position in row \"" + nRows + "\" in loop of \"" + saveFrame.name + "\"");
                }
            }
        }

        return tokenRow;
    }

    public ArrayList<String> processLoopTags(STAR3Base star3) throws ParseException {
        ArrayList<String> tokens = new ArrayList();
        boolean firstTag = true;
        while (true) {
            String token = star3.getToken();
            if (token == null) {
                throw new ParseException("File exhausted before all tags found in loop of \"" + saveFrame.name + "\"");
            }
            if (token.charAt(0) != '_') {
                star3.usePrevious = true;
                break;
            } else {
                String[] tokenPair = STAR3Base.getTokenPair(token);
                if (firstTag) {
                    name = tokenPair[0];
                    firstTag = false;
                }
                tokens.add(tokenPair[1]);
            }
        }
        return tokens;
    }

    public String processLoop() throws ParseException {
        STAR3Base star3 = saveFrame.getSTAR3();
        tags = processLoopTags(star3);
        nTags = tags.size();
        columns = new ArrayList[nTags];
        for (int i = 0; i < nTags; i++) {
            columns[i] = new ArrayList();
            String tag = tags.get(i);
            loopTags.put(tag, columns[i]);
        }
        nRows = 0;
        while (true) {
            String[] tokenRow = getLoopRow(nTags);
            if (tokenRow == null) {
                break;
            }
            addRow(tokenRow);
            nRows++;
        }

        return (name);
    }

    public void addValues(final List<String> names, final List<String> values) throws ParseException {
        nTags = names.size();
        columns = new ArrayList[nTags];
        tags = new ArrayList();
        for (int i = 0; i < nTags; i++) {
            columns[i] = new ArrayList();
            String tag = names.get(i);
            loopTags.put(tag, columns[i]);
            tags.add(tag);
        }
        nRows = 0;
        String[] tokenRow = new String[nTags];
        int k = 0;
        nRows = values.size() / nTags;
        for (int j = 0; j < nRows; j++) {
            for (int i = 0; i < nTags; i++) {
                tokenRow[i] = values.get(k++);
            }
            addRow(tokenRow);
        }
    }

    public Map<String, String> getRowMap(int loopIndex) throws ParseException {
        Map<String, String> map = new LinkedHashMap<>();

        for (Map.Entry eSet : loopTags.entrySet()) {
            ArrayList column = (ArrayList) eSet.getValue();
            if ((loopIndex < 0) || (loopIndex >= column.size())) {
                throw new ParseException("Invalid loop index \"" + loopIndex + "\"");
            }

            String value = (String) column.get(loopIndex);
            String loopTag = (String) eSet.getKey();
            map.put(loopTag, value);
        }
        return map;
    }

    public String getValueIfPresent(String tag, int loopIndex) throws ParseException {
        String result = null;
        ArrayList<String> column = (ArrayList) loopTags.get(tag);
        if (column != null) {
            if ((loopIndex < 0) || (loopIndex >= column.size())) {
                throw new ParseException("Invalid loop index \"" + loopIndex + "\"");
            }
            result = (String) column.get(loopIndex);
        }
        return result;
    }

    public String getValue(String tag, int loopIndex) throws ParseException {
        ArrayList<String> column = (ArrayList) loopTags.get(tag);

        if (column == null) {
            throw new ParseException("Can't find column \"" + tag + "\"");
        }
        if ((loopIndex < 0) || (loopIndex >= column.size())) {
            throw new ParseException("Invalid loop index \"" + loopIndex + "\"");
        }
        String result = (String) column.get(loopIndex);
        return result;
    }

    public String[] getRowValues(int loopIndex) throws IllegalArgumentException {
        String[] result = new String[columns.length];
        if ((loopIndex < 0) || (loopIndex >= nRows)) {
            throw new IllegalArgumentException("Invalid loop index \"" + loopIndex + "\"");
        }
        for (int i = 0; i < columns.length; i++) {
            result[i] = (String) columns[i].get(loopIndex);
        }
        return result;
    }

    public List<String> getRowValuesAsList(int loopIndex) throws ParseException {
        if ((loopIndex < 0) || (loopIndex >= nRows)) {
            throw new ParseException("Invalid loop index \"" + loopIndex + "\"");
        }
        List<String> list = new ArrayList<>();
        for (ArrayList<String> column : columns) {
            list.add(column.get(loopIndex));
        }
        return list;
    }

    public List<String> getColumnAsList(String tag) throws ParseException {
        ArrayList<String> column = loopTags.get(tag);
        if (column == null) {
            throw new ParseException("Can't find column \"" + tag + "\"");
        }
        return column;
    }

    public List<String> getColumnAsList(String tag, String defaultValue) {
        ArrayList<String> column = loopTags.get(tag);
        if (column == null) {
            column = new ArrayList<>();
            for (int i = 0; i < nRows; i++) {
                column.add(defaultValue);
            }
        }
        return column;
    }

    public List<Double> getColumnAsDoubleList(String tag, Double defaultValue) throws ParseException {
        ArrayList<String> column = loopTags.get(tag);
        List<Double> values;
        if (column == null) {
            values = new ArrayList<>();
            for (int i = 0; i < nRows; i++) {
                values.add(defaultValue);
            }
        } else {
            values = new ArrayList<>();
            for (String s : column) {
                switch (s) {
                    case ".":
                        values.add(defaultValue);
                        break;
                    case "?":
                        values.add(defaultValue);
                        break;
                    default:
                        values.add(Double.parseDouble(s));
                        break;
                }
            }
        }
        return values;
    }

    public List<Integer> getColumnAsIntegerList(String tag, Integer defaultValue) throws ParseException {
        ArrayList<String> column = loopTags.get(tag);
        List<Integer> values;
        if (column == null) {
            values = Collections.nCopies(nRows, (Integer) null);
        } else {
            values = new ArrayList<>();
            for (String s : column) {
                switch (s) {
                    case ".":
                        values.add(defaultValue);
                        break;
                    case "?":
                        values.add(defaultValue);
                        break;
                    default:
                        values.add(Integer.parseInt(s));
                        break;
                }
            }
        }
        return values;
    }

    public List<String> getColumnAsListIfExists(String tag) throws ParseException {
        ArrayList<String> column = loopTags.get(tag);
        return column;
    }

    public List<String> getTags() throws ParseException {
        ArrayList<String> list = new ArrayList<>();
        for (String tag : tags) {
            list.add(tag);
        }
        return list;
    }

    public boolean hasTag(String tag) {
        return loopTags.containsKey(tag);
    }
    public List<String> getColumn(String tag) throws ParseException {
        ArrayList<String> column = loopTags.get(tag);
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < nRows; i++) {
            String value = column.get(i);
            list.add(value);
        }
        return list;
    }
}
