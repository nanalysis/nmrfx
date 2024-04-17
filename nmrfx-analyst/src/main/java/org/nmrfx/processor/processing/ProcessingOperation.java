package org.nmrfx.processor.processing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessingOperation implements ProcessingOperationInterface {
    private static final String PATTERN_STRING = "(\\w+)=((\\[[^\\[]*])|(\"[^\"]*\")|('[^']*')|([^,]+))";
    static final Pattern opPattern = Pattern.compile(PATTERN_STRING);

    public record OperationParameter(String name, String value) {
    }


    static Map<String, String> longNameMap = Map.of(
            "TDCOMB", "Phase Sensitive Mode",
            "APODIZE", "Apodization",
            "ZF", "Zero Fill",
            "FT", "Fourier Transform",
            "PHASE", "Phasing",
            "BC", "Baseline Correction",
            "SUPPRESS", "Signal Suppression",
            "EXTRACT", "Extract Region",
            "EXTRACTP", "Extract Region"
    );
    String opName;
    List<OperationParameter> parameters = new ArrayList<>();
    boolean disabled = false;

    public ProcessingOperation(String string) {
        update(string);
    }

    public List<OperationParameter> getParameters() {
        return parameters;
    }

    public Map<String, OperationParameter> getParameterMap() {
        Map<String, OperationParameter> map = new HashMap<>();
        for (var opPar : parameters) {
            map.put(opPar.name, opPar);
        }
        return map;
    }

    public String getName() {
        return opName;
    }

    public String getTitle(boolean detailed) {
        return detailed ? toString() : longNameMap.getOrDefault(opName, opName);
    }

    public void update(String string) {
        parameters.clear();
        String opArgs;
        int index = string.indexOf('(');
        boolean lastIsClosePar = string.charAt(string.length() - 1) == ')';
        if ((index != -1) && lastIsClosePar) {
            opName = string.substring(0, index);
            opArgs = string.substring(index + 1, string.length() - 1);
        } else {
            opName = string;
            opArgs = "";
        }
        disabled = false;
        if (!opArgs.isBlank()) {
            Matcher matcher = opPattern.matcher(opArgs);
            while (matcher.find()) {
                if (matcher.groupCount() > 1) {
                    String parName = matcher.group(1);
                    String parValue = matcher.group(2);
                    if (parName.equals("disabled")) {
                        disabled = parValue.equals("True");
                    } else {
                        OperationParameter parameter = new OperationParameter(parName, parValue);
                        parameters.add(parameter);
                    }
                }
            }
        }
    }

    public void disabled(boolean state) {
        disabled = state;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(opName).append("(");
        boolean firstArg = true;
        for (var parameter : parameters) {
            if (!firstArg) {
                stringBuilder.append(",");
            } else {
                firstArg = false;
            }
            stringBuilder.append(parameter.name).append("=").append(parameter.value);
        }
        if (disabled) {
            if (!firstArg) {
                stringBuilder.append(",");
            }
            stringBuilder.append("disabled=True");
        }
        stringBuilder.append(")");
        return stringBuilder.toString();
    }
}
