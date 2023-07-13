package org.nmrfx.processor.gui;

public class ProcessingOperation {
    String opName;
    String opArgs;
    boolean disabled = false;

    public ProcessingOperation(String string) {
        int index = string.indexOf('(');
        boolean lastIsClosePar = string.charAt(string.length() - 1) == ')';
        if ((index != -1) && lastIsClosePar) {
            opName = string.substring(0, index);
            opArgs = string.substring(index + 1, string.length() - 1);
        } else {
            opName = string;
            opArgs = "";
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
