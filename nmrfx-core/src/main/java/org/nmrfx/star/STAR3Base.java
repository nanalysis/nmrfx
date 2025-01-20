/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.star;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * @author brucejohnson
 */
public class STAR3Base {

    private static final Logger log = LoggerFactory.getLogger(STAR3Base.class);

    public static final String SAVE = "save_";
    public static final String LOOP = "loop_";
    public static final String STOP = "stop_";
    LineNumberReader lineReader = null;
    PrintWriter out = null;
    BufferedReader bfR;
    STAR3.STARTokenizer stTokenizer = null;
    String string;
    public boolean usePrevious;
    String lastToken = null;
    final String name;
    final String fileName;
    LinkedHashMap<String, Saveframe> saveFrames = new LinkedHashMap<>();

    public STAR3Base(final String name) {

        usePrevious = false;
        this.name = name;
        this.fileName = "";

    }

    public STAR3Base(final String fileName, final String name) {
        this.name = name;
        this.fileName = fileName;
        try {
            bfR = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bfR);
        } catch (IOException ioe) {
            log.warn("Cannot open the STAR3 file. {}", ioe.getMessage(), ioe);
            return;
        }

        usePrevious = false;
    }

    public STAR3Base(BufferedReader bfR, final String name) {
        this.name = name;
        this.fileName = "";
        lineReader = new LineNumberReader(bfR);

        usePrevious = false;
    }

    public int getLastLine() {
        return lineReader.getLineNumber();
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

    class STARTokenizer {

        String s;
        int pos;
        int length = 0;

        void initialize(final String newStr) {
            pos = 0;
            s = newStr;
            if (newStr == null) {
                length = 0;
            } else {
                length = s.length();
            }
        }

        String nextToken() {
            if (pos >= length) {
                return null;
            }
            int fChar;
            int lChar;
            boolean gotWS = false;
            while (pos < length) {
                if (Character.isWhitespace(s.charAt(pos))) {
                    gotWS = true;
                } else {
                    if (pos == 0) {
                        gotWS = true;
                    }
                    break;
                }
                pos++;
            }
            if (pos == length) {
                return null;
            }
            fChar = pos;
            lChar = pos;
            if (gotWS && (s.charAt(pos) == '\'' || s.charAt(pos) == '"')) {
                char qChar = s.charAt(pos);
                pos++;
                while (pos < length) {
                    if ((s.charAt(pos) == qChar) && ((pos == length - 1) || Character.isWhitespace(s.charAt(pos + 1)))) {
                        lChar = pos;
                        pos++;
                        break;
                    }
                    pos++;
                }
            } else {
                while (pos < length) {
                    if (Character.isWhitespace(s.charAt(pos))) {
                        break;
                    }
                    lChar = pos;
                    pos++;
                }
            }
            String token = s.substring(fChar, lChar + 1);
            token = token.trim();
            int length = token.length();
            if (length > 1) {
                char firstChar = token.charAt(0);
                char lastChar = token.charAt(length - 1);
                if ((lastChar == firstChar) && ((firstChar == '\'') || (firstChar == '"'))) {
                    token = token.substring(1, length - 1);
                }
            }

            return token;
        }
    }

    public void unGetToken() {
        usePrevious = true;
    }

    public String getToken() throws ParseException {
        String token = getNextToken();
        return token;
    }

    String getNextToken() {
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
        if (s.isBlank()) {
            return ".";
        }
        String result = s;
        char stringQuote = '"';
        if (s.indexOf(' ') != -1) {
            if (s.indexOf('"') != -1) {
                stringQuote = '\'';
            }
            result = stringQuote + s + stringQuote;
        }
        return result;
    }

    public void processSaveFrame(String saveFrameName) throws ParseException {
        Saveframe saveFrame = null;
        if (getSaveFrames().containsKey(saveFrameName)) {
            log.warn("Skipping duplicate save frame \"{}\"", saveFrameName);
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

    public static void writeLoopStrings(Writer chan, String[] loopStrings) throws ParseException, IOException {
        chan.write("\nloop_\n");
        for (int j = 0; j < loopStrings.length; j++) {
            chan.write(loopStrings[j] + "\n");
        }
        chan.write("\n\n");
    }

    public static void writeLoopStrings(Writer chan, String category, List<String> loopStrings) throws ParseException, IOException {
        chan.write("\nloop_\n");
        for (String loopString : loopStrings) {
            chan.write(category + "." + loopString + "\n");
        }
    }

    public static void writeString(Writer chan, String s, int maxLen) throws ParseException, IOException {
        if (s.length() > maxLen) {
            chan.write("\n;");
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
            chan.write(quote(s) + "\n");
        }
    }

}
