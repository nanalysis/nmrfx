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
package org.nmrfx.processor.star;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class STAR3 {

    LineNumberReader lineReader = null;
    PrintWriter out = null;
    BufferedReader bfR;
    STARTokenizer stTokenizer = null;
    String string;
    public boolean usePrevious;
    String lastToken = null;
    private LinkedHashMap<String, Saveframe> saveFrames = new LinkedHashMap<String, Saveframe>();
    private final String name;
    private final String fileName;

    public STAR3(final String name) {
        usePrevious = false;
        this.name = name;
        this.fileName = "";

    }

    public STAR3(final String fileName, final String name) {
        this.name = name;
        this.fileName = fileName;
        try {
            bfR = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bfR);
        } catch (IOException ioe) {
            System.err.println("Cannot open the STAR3 file.");
            System.err.println(ioe.getMessage());

            return;
        }

        usePrevious = false;
    }

    public STAR3(BufferedReader bfR, final String name) {
        this.name = name;
        this.fileName = "";
        lineReader = new LineNumberReader(bfR);

        usePrevious = false;
    }

    public Saveframe getSaveframe(String saveFrameName) throws ParseException {
        Saveframe saveframe = (Saveframe) getSaveFrames().get(saveFrameName);
        return saveframe;
    }

    public List<String> getSaveFrameNames() throws ParseException {
        List<String> list = new ArrayList<>();
        Iterator iter = getSaveFrames().keySet().iterator();
        while (iter.hasNext()) {
            String key = (String) iter.next();
            Saveframe saveframe = (Saveframe) getSaveFrames().get(key);
            list.add(key);
            list.add(saveframe.getCategoryName());
        }
        return list;

    }

    public void writeToken(String token) {
        out.print(token);
    }

    static public String[] getTokenPair(String token) throws ParseException {
        // fixme prepare pattern matcher for efficiency, or use indexOf?
        if (token.charAt(0) != '_') {
            throw new ParseException("Incorrect tag format \"" + token + "\"");
        }
        String[] tokenPair = token.split("\\.");
        if (tokenPair.length != 2) {
            throw new ParseException("Incorrect tag format \"" + token + "\"");
        }
        return tokenPair;
    }

    public void processSaveFrame(String saveFrameName) throws ParseException {
        /*
         String token = getToken(interp);
         String[] tokenPair = getTokenPair(token);
         if (!tokenPair[1].equals("Sf_category")) {
         throw new ParseException(interp,"Saveframe \""+saveFrameName+"\" doesn't start with Sf_category token");
         }
         token = getToken(interp);
         */
        Saveframe saveFrame = null;
        if (getSaveFrames().containsKey(saveFrameName)) {
            System.err.println("Skipping duplicate save frame \"" + saveFrameName + "\"");
            saveFrame = new Saveframe(this, "duplicate_" + saveFrameName);
        } else {
            saveFrame = new Saveframe(this, saveFrameName);
            getSaveFrames().put(saveFrameName, saveFrame);
        }
        saveFrame.read();

    }

    public void addSaveframe(final String saveFrameName, final String saveFrameCategory) {
        Saveframe saveFrame = new Saveframe(this, saveFrameName, saveFrameCategory);
        getSaveFrames().put(saveFrameName, saveFrame);
    }

    public void scanFile() throws ParseException {
        while (true) {
            String token = getNextToken();
            if (token == null) {
                break;
            } else if (token.startsWith("save_")) {
                processSaveFrame(token);
            }
        }
    }

    public void scanFile(String saveName) throws ParseException {
        while (true) {
            String token = getNextToken();
            if (token == null) {
                break;
            } else if (token.startsWith(saveName)) {
                processSaveFrame(token);
                break;
            }
        }
    }

    public void close() {
        lineReader = null;
        bfR = null;
    }

    public void addLoop(String saveFrameName, String tagGroup, final List<String> names, final List<String> values) throws ParseException {
        Saveframe saveFrame = (Saveframe) getSaveFrames().get(saveFrameName);
        if (saveFrame == null) {
            throw new ParseException("Can't find saveframe \"" + saveFrameName + "\"");
        }
        saveFrame.addLoop(tagGroup, names, values);
    }

    public List<String> getLoopTags(String saveFrameName, String tagGroup) throws ParseException {
        Saveframe saveFrame = (Saveframe) getSaveFrames().get(saveFrameName);
        if (saveFrame == null) {
            throw new ParseException("Can't find saveframe \"" + saveFrameName + "\"");
        }
        List<String> result = saveFrame.getLoopTags(tagGroup);
        return result;
    }

    public int loopCount(String saveFrameName, String tagGroup) throws ParseException {
        Saveframe saveFrame = (Saveframe) getSaveFrames().get(saveFrameName);
        if (saveFrame == null) {
            throw new ParseException("Can't find saveframe \"" + saveFrameName + "\"");
        }
        int result = saveFrame.loopCount(tagGroup);
        return result;
    }

    public List<String> getCategories(String saveFrameName) throws ParseException {
        Saveframe saveFrame = (Saveframe) getSaveFrames().get(saveFrameName);
        if (saveFrame == null) {
            throw new ParseException("Can't find saveframe \"" + saveFrameName + "\"");
        }
        List<String> result = saveFrame.getCategories();
        return result;
    }

    public List<String> getTags(String saveFrameName, String category) throws ParseException {
        Saveframe saveFrame = (Saveframe) getSaveFrames().get(saveFrameName);
        if (saveFrame == null) {
            throw new ParseException("Can't find saveframe \"" + saveFrameName + "\"");
        }
        List<String> result = saveFrame.getTags(category);
        return result;
    }

    public List<String> getColumn(String saveFrameName, String tagGroup, String tag) throws ParseException {
        Saveframe saveFrame = (Saveframe) getSaveFrames().get(saveFrameName);
        if (saveFrame == null) {
            throw new ParseException("Can't find saveframe \"" + saveFrameName + "\"");
        }
        List<String> result = saveFrame.getColumn(tagGroup, tag);
        return result;
    }

    public String getValue(String saveFrameName, String tagGroup, String tag) throws ParseException {
        Saveframe saveFrame = (Saveframe) getSaveFrames().get(saveFrameName);
        if (saveFrame == null) {
            throw new ParseException("Can't find saveframe \"" + saveFrameName + "\"");
        }
        String result = saveFrame.getValue(tagGroup, tag);
        return result;
    }

    public String getValue(String saveFrameName, String tagGroup, String tag, int loopIndex) throws ParseException {
        Saveframe saveFrame = (Saveframe) getSaveFrames().get(saveFrameName);
        if (saveFrame == null) {
            throw new ParseException("Can't find saveframe \"" + saveFrameName + "\"");
        }
        String result = saveFrame.getValue(tagGroup, tag, loopIndex);
        return result;
    }

    void setupTokenizer(StreamTokenizer tokenizer) {
        tokenizer.resetSyntax();
        tokenizer.wordChars('a', 'z');
        tokenizer.wordChars('A', 'Z');
        tokenizer.wordChars('\u00A0', '\u00FF');
        tokenizer.whitespaceChars(0000, 32);
        tokenizer.quoteChar('"');
        tokenizer.wordChars('\'', '\'');
        tokenizer.wordChars('0', '9');
        tokenizer.wordChars('+', '+');
        tokenizer.wordChars('-', '-');
        tokenizer.wordChars('$', '$');
        tokenizer.wordChars('.', '.');
        tokenizer.wordChars(',', ',');
        tokenizer.wordChars('?', '?');
        tokenizer.commentChar('#');
        tokenizer.wordChars('_', '_');
        tokenizer.wordChars('@', '@');
        tokenizer.wordChars('(', '(');
        tokenizer.wordChars(')', ')');
        tokenizer.wordChars('*', '*');
        tokenizer.wordChars('<', '<');
        tokenizer.wordChars('>', '>');
        tokenizer.wordChars('%', '%');
        tokenizer.wordChars('/', '/');
        tokenizer.wordChars('[', '[');
        tokenizer.wordChars(']', ']');
        tokenizer.wordChars('=', '=');
    }

    public String getToken() throws ParseException {
        String token = getNextToken();
        if (token == null) {
            throw new ParseException("File exhausted before all tokens found");
        }
        return token;
    }

    /**
     * @return the saveFrames
     */
    public LinkedHashMap<String, Saveframe> getSaveFrames() {
        return saveFrames;
    }

    /**
     * @param saveFrames the saveFrames to set
     */
    public void setSaveFrames(LinkedHashMap<String, Saveframe> saveFrames) {
        this.saveFrames = saveFrames;
    }

    class STARTokenizer {

        String regex = "\"([^\"]|\"[^ \t])*\"|'([^']|'[^ \t])*'|[^ \t]+";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = null;

        void initialize(final String s) {
//            this.s = s;
//            currentPosition = 0;
//            if (s == null) {
//                stringLength = 0;
//            } else {
//                stringLength = s.length();
//            }
            matcher = pattern.matcher(s);
        }

        String nextToken() {
            if (matcher.find()) {
                String group = matcher.group();
                if (group != null) {
                    int length = group.length();
                    if (length > 1) {
                        char firstChar = group.charAt(0);
                        char lastChar = group.charAt(length - 1);
                        if ((lastChar == firstChar) && ((firstChar == '\'') || (firstChar == '"'))) {
                            group = group.substring(1, length - 1);
                        }
                    }
                }
                return group;

            } else {
                return null;
            }
        }
    }

    private String getNextToken() {
        StringBuffer text = new StringBuffer();
        boolean inText = false;

        if (usePrevious) {
            usePrevious = false;
            return (lastToken);
        }

        usePrevious = false;

        if (stTokenizer != null) {
            String nextToken = stTokenizer.nextToken();
            if (nextToken != null) {
                return nextToken;
            }
        }

        while (true) {
            string = getLine();
            if (string == null) {
                lineReader = null;
                bfR = null;
                lastToken = null;
                return lastToken;
            }
            if (inText) {
                if (string.startsWith(";")) {
                    inText = false;
                    if (string.length() > 1) {
                        stTokenizer = new STARTokenizer();
                        stTokenizer.initialize(string.substring(1));
                    }

                    lastToken = text.toString();
                    return lastToken;
                }

                text.append(string + '\n');
            } else {
                if (string.startsWith("#")) {
                    continue;
                }

                if (string.startsWith(";")) {
                    text.setLength(0);
                    text.append(string.substring(1).trim());
                    inText = true;
                } else {
                    stTokenizer = new STARTokenizer();
                    stTokenizer.initialize(string);
                    String nextToken = stTokenizer.nextToken();
                    if (nextToken == null) {
                        continue;
                    } else {
                        lastToken = nextToken;
                        return lastToken;
                    }
                }
            }
        }
    }

    public String getLine() {
        string = null;

        if (lineReader == null) {
            return (null);
        }

        try {
            string = lineReader.readLine();
        } catch (IOException e) {
            return (string);
        }

        return (string);
    }

    public static String valueOf(Number number) {
        String value = ".";
        if (number != null) {
            value = String.valueOf(number);
        }
        return value;
    }

    public static String getTokenFromMap(Map tokenMap, String tokenName) throws ParseException {
        String tokenValue = (String) tokenMap.get(tokenName);
        if (tokenValue == null) {
            throw new ParseException("Token \"" + tokenName + "\" not in tokenMap");
        }
        return tokenValue;
    }

    public static String getTokenFromMap(Map tokenMap, String tokenName, String defaultValue) throws ParseException {
        String tokenValue = (String) tokenMap.get(tokenName);
        if (tokenValue == null) {
            if (defaultValue == null) {
                throw new ParseException("Token \"" + tokenName + "\" not in tokenMap");
            } else {
                tokenValue = defaultValue;
            }
        }
        return tokenValue;
    }

    public static String quote(String s) {
        String result = s;
        char stringQuote = '"';
        if (s.indexOf(' ') != -1) {
            result = stringQuote + s + stringQuote;
        }
        return result;
    }

    public static void writeLoopStrings(FileWriter chan, String[] loopStrings) throws ParseException, IOException {
        chan.write("\nloop_\n");
        for (int j = 0; j < loopStrings.length; j++) {
            chan.write(loopStrings[j] + "\n");
        }
        chan.write("\n\n");
    }

    public static void writeString(FileWriter chan, String s, int maxLen) throws ParseException, IOException {
        if (s.length() > maxLen) {
            chan.write("\n;\n");
            int propLen = s.length();
            int j = 0;
            while (j < propLen) {
                int endIndex = j + maxLen;
                if (endIndex > propLen) {
                    endIndex = propLen;
                }
                String segment = s.substring(j, endIndex);
                chan.write(segment);
                chan.write("\n");
                j += maxLen;
            }
            chan.write(";\n");
        } else {
            chan.write("'" + s + "'\n");
        }
    }

    public static void main(String[] argv) {
        if (argv.length != 1) {
            System.err.println("usage: fileName");
        } else {
            STAR3 star3 = new STAR3(argv[0]);
            while (true) {
                String token = star3.getNextToken();
                if (token == null) {
                    break;
                } else {
                    System.err.println(token);
                }
            }
        }
    }
}
