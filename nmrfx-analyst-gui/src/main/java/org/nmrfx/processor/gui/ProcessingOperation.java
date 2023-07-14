package org.nmrfx.processor.gui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessingOperation {
    static final String DISABLE_PATTERN = "disabled *= *(True|False)";
    static final Pattern disablePattern = Pattern.compile(DISABLE_PATTERN);
    String opName;
    String opArgs;
    boolean disabled = false;

    public ProcessingOperation(String string) {
        update(string);
    }

    public void update(String string) {
        int index = string.indexOf('(');
        boolean lastIsClosePar = string.charAt(string.length() - 1) == ')';
        if ((index != -1) && lastIsClosePar) {
            opName = string.substring(0, index);
            opArgs = string.substring(index + 1, string.length() - 1);
        } else {
            opName = string;
            opArgs = "";
        }
        if (!opArgs.isBlank()) {
            Matcher matcher = disablePattern.matcher(opArgs);
            if (matcher.matches()) {
                String status = matcher.group(1);
                System.out.println("match " + opArgs + " " + status);
            }
        }
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(opName).append("(").append(opArgs);
        if (disabled) {
            if (!opArgs.isBlank()) {
                stringBuilder.append(",");
            }
            stringBuilder.append("disabled=True");
        }
        stringBuilder.append(")");
        return stringBuilder.toString();
    }
}
