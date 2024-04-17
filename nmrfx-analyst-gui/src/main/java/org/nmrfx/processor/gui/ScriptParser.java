package org.nmrfx.processor.gui;

import org.nmrfx.processor.processing.*;

import java.util.*;

public class ScriptParser {
    private final ChartProcessor chartProcessor;
    private final NavigatorGUI navigatorGUI;
    HashSet<String> refOps = new HashSet<>();
    ApodizationGroup apodizationGroup = null;
    BaselineGroup baselineGroup = null;
    NUSGroup nusGroup = null;
    Map<ProcessingSection, List<ProcessingOperationInterface>> mapOpLists = new LinkedHashMap<>();

    String dimNum = "";
    int order;
    List<ProcessingOperationInterface> dimList = null;

    public ScriptParser(ChartProcessor chartProcessor, NavigatorGUI navigatorGUI) {
        this.chartProcessor = chartProcessor;
        this.navigatorGUI = navigatorGUI;
        initRefOps();
    }

    private void initGroups() {
        apodizationGroup = null;
        baselineGroup = null;
        nusGroup = null;
    }

    private void initRefOps() {
        refOps.add("skip");
        refOps.add("sw");
        refOps.add("sf");
        refOps.add("ref");
        refOps.add("label");
        refOps.add("acqOrder");
        refOps.add("acqarray");
        refOps.add("acqmode");
        refOps.add("acqsize");
        refOps.add("tdsize");
        refOps.add("fixdsp");
    }

    private void parseGroup(String opName, String line) {
        if (opName.equals("BaselineGroup")) {
            if (baselineGroup == null) {
                baselineGroup = new BaselineGroup();
                dimList.add(baselineGroup);
            }
            baselineGroup.update("BCWHIT", "BCWHIT()");
            baselineGroup.disabled(true);
        } else if (opName.equals("NUSGroup")) {
            if (nusGroup == null) {
                nusGroup = new NUSGroup();
                dimList.add(nusGroup);
            }
            nusGroup.update("NESTA", "NESTA()");
            nusGroup.disabled(false);
        } else if (ApodizationGroup.opInGroup(opName)) {
            if (apodizationGroup == null) {
                apodizationGroup = new ApodizationGroup();
                dimList.add(apodizationGroup);
            }
            apodizationGroup.update(opName, line);
        } else if (BaselineGroup.opInGroup(opName)) {
            if (baselineGroup == null) {
                baselineGroup = new BaselineGroup();
                dimList.add(baselineGroup);
            }
            baselineGroup.update(opName, line);
        } else if (NUSGroup.opInGroup(opName)) {
            if (nusGroup == null) {
                nusGroup = new NUSGroup();
                dimList.add(nusGroup);
            }
            nusGroup.update(opName, line);
        } else if (!opName.equals("run")) {
            dimList.add(new ProcessingOperation(line));
        }
    }

    private void parseDIM(String args) {
        String newDim = args;
        String[] fields = args.split(",");
        int[] dimNums;
        if (newDim.isBlank()) {
            dimNums = new int[0];
            newDim = "_ALL";
        } else {
            dimNums = new int[fields.length];
            for (int i = 0; i < fields.length; i++) {
                dimNums[i] = Integer.parseInt(fields[i]) - 1;
            }
        }
        if (!newDim.equals(dimNum)) {
            dimList = new ArrayList<>();
            final String dimName;
            if ((dimNums.length == 0) || (dimNums.length == 1)) {
                dimName = "D";
            } else {
                dimName = "I";
            }
            ProcessingSection section = chartProcessor.getProcessingSection(order, dimNums, dimName);
            if (mapOpLists.containsKey(section)) {
                order++;
                section = chartProcessor.getProcessingSection(order, dimNums, "D");
            }
            mapOpLists.put(section, dimList);

            dimNum = newDim;
            apodizationGroup = null;
            baselineGroup = null;
            nusGroup = null;
        }

    }

    public void parseScript(String scriptString) {
        String[] lines = scriptString.split("\n");
        List<String> headerList = new ArrayList<>();
        initGroups();
        mapOpLists.clear();

        dimNum = "";
        order = 1;
        dimList = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank() || line.charAt(0) == '#') {
                continue;
            }
            int index = line.indexOf('(');
            boolean lastIsClosePar = line.charAt(line.length() - 1) == ')';
            if ((index != -1) && lastIsClosePar) {
                String opName = line.substring(0, index);
                String args = line.substring(index + 1, line.length() - 1);
                if (opName.equals("DIM")) {
                    parseDIM(args);
                } else if (dimList != null) {
                    parseGroup(opName, line);
                } else if (refOps.contains(opName)) {
                    headerList.add(line);
                } else if (opName.equals("markrows")) {
                    navigatorGUI.parseMarkRows(args);
                }
            }
        }
        chartProcessor.setScripts(headerList, mapOpLists);
    }
}
