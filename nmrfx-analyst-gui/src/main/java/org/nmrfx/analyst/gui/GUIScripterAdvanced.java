package org.nmrfx.analyst.gui;

import javafx.stage.Stage;
import org.nmrfx.analyst.gui.spectra.StripController;
import org.nmrfx.analyst.gui.tools.RunAboutGUI;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.*;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.structure.seqassign.RunAbout;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;

@PythonAPI("gscript_adv")
public class GUIScripterAdvanced extends GUIScripter {
    private static final String PEAKLIST = "peaklist";
    private static final String ARRANGEMENT = "arrangement";
    private static final String REFLIST = "reflist";
    private static final String UNIFYLIMITS = "unifylimits";
    private static final String WIDTHS = "widths";
    private static final String TOLERANCES = "tolerances";
    private static final String GEOMETRY = "geometry";
    private static final String SCONFIG = "sconfig";
    private static final String SPECTRA = "spectra";
    private static final String CONFIG = "config";
    private static final String CCONFIG = "cconfig";
    private static final String ANNOTATIONS = "annotations";
    private static final String PEAKLISTS = "peaklists";
    private static final String STRIPS = "strips";
    private static final String RUNABOUT = "runabout";
    private static final String INSET = "inset";
    private static final String PROJECTION = "projection";

    public Map<String, String> strips(FXMLController controller) {
        StripController stripController = (StripController) controller.getTool(StripController.class);

        Map<String, String> result = new HashMap<>();
        if (stripController != null) {
            PeakList peakList = stripController.getControlList();
            if (peakList != null) {
                String[] dimNames = stripController.getDimNames();
                String xDim = dimNames[0];
                String zDim = dimNames[1];
                result.put(PEAKLIST, peakList.getName());
                result.put("xdim", xDim);
                result.put("zdim", zDim);
            }
        }
        return result;
    }

    public void strips(FXMLController controller, String peakListName, String xDim, String zDim) {
        StripController stripController = controller.showStripsBar();
        PeakList peakList = PeakList.get(peakListName);
        stripController.loadFromCharts(peakList, xDim, zDim);
    }

    public Map<String, Object> runabout(FXMLController controller) {
        RunAboutGUI runAboutGUI = (RunAboutGUI) controller.getTool(RunAboutGUI.class);
        Map<String, Object> result = new HashMap<>();
        if (runAboutGUI != null) {
            String arrangement = runAboutGUI.getArrangement();
            result.put(ARRANGEMENT, arrangement);
            RunAbout runAbout = runAboutGUI.getRunAbout();
            String refListName = runAbout.getRefList() == null ? "" : runAbout.getRefList().getName();
            result.put(REFLIST, refListName);
            result.put(UNIFYLIMITS, runAboutGUI.unifyLimits());
            Map<String, Double> widthMap = new HashMap<>();
            var currentMap = runAboutGUI.getWidthMap();
            for (var entry : currentMap.entrySet()) {
                widthMap.put(entry.getKey().getNumberName(), entry.getValue().doubleValue());
            }
            result.put(WIDTHS, widthMap);
            Map<String, Double> tolMap = new HashMap<>();
            var currentTolMap = runAboutGUI.getWidthMap();
            for (var entry : currentTolMap.entrySet()) {
                tolMap.put(entry.getKey().getNumberName(), entry.getValue().doubleValue());
            }
            result.put(TOLERANCES, tolMap);
        }
        return result;
    }

    public void runabout(FXMLController controller, Map<String, Object> runAboutData) {
        Optional<RunAboutGUI> runAboutGUIOpt = controller.showRunAboutTool();
        runAboutGUIOpt.ifPresent(runAboutGUI -> {
            String arrangement = Objects.requireNonNullElse(runAboutData.getOrDefault(ARRANGEMENT, "HC"), "HC").toString();
            String refListName = Objects.requireNonNullElse(runAboutData.getOrDefault(REFLIST, ""), "").toString();
            Object unifyObject = Objects.requireNonNullElse(runAboutData.getOrDefault(UNIFYLIMITS, false), false);
            Boolean unifyLimits = Boolean.FALSE;
            if (unifyObject instanceof Boolean unifyBoolean) {
                unifyLimits = unifyBoolean;
            } else {
                unifyLimits = unifyLimits.toString().equals("1");
            }
            Map<String, Double> widthMap = (Map<String, Double>) Objects.requireNonNullElse(runAboutData.getOrDefault(WIDTHS, Map.of()), Map.of());
            Map<String, Double> tolMap = (Map<String, Double>) Objects.requireNonNullElse(runAboutData.getOrDefault(TOLERANCES, Map.of()), Map.of());
            PeakList refList = PeakList.get(refListName);
            runAboutGUI.getRunAbout().setRefList(refList);
            runAboutGUI.unifyLimits(unifyLimits);
            runAboutGUI.genWin(arrangement);
        });
    }

    public String genYAMLOnFx(FXMLController controller) {
        Stage stage = controller.getStage();
        Map<String, Object> winMap = new LinkedHashMap<>();
        winMap.put(GEOMETRY, geometryOnFx(controller));
        winMap.put("title", stage.getTitle());
        winMap.put("grid", gridOnFx(controller));
        winMap.put(SCONFIG, controller.getPublicPropertiesValues());
        List<Object> spectra = new ArrayList<>();
        winMap.put(SPECTRA, spectra);
        for (PolyChart chart : controller.getAllCharts()) {
            Map<String, Object> sd = new HashMap<>();
            spectra.add(sd);
            var insetOpt = chart.getInsetChart();
            if (insetOpt.isPresent()) {
                InsetChart insetChart = insetOpt.get();
                sd.put(INSET, insetChart.getPosition());
            } else {
                GridPaneCanvas.GridPosition gridValue = controller.getGridPaneCanvas().getGridLocation(chart);
                sd.put("grid", List.of(gridValue.rows(), gridValue.columns(), gridValue.rowSpan(), gridValue.columnSpan()));
            }
            sd.put("lim", limitOnFx(chart));
            sd.put(CCONFIG, chart.getChartProperties().getPublicPropertiesValues());
            List<CanvasAnnotation> annoTypes = chart.getCanvasAnnotations();
            Yaml annoYaml = new Yaml();
            String annoString = annoYaml.dump(annoTypes);
            sd.put(ANNOTATIONS, annoString);
            List<Map<String, Object>> datasetList = new ArrayList<>();
            sd.put("datasets", datasetList);

            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();

            dataAttrs.stream().filter(dataAttr -> !dataAttr.getDataset().isProjection()).forEach(dataAttr -> {
                String datasetName = dataAttr.getFileName();
                Map<String, Object> dSet = new HashMap<>();
                dSet.put("name", datasetName);
                dSet.put(CONFIG, configOnFx(chart, datasetName));
                dSet.put("dims", getDimsOnFx(chart, datasetName));
                Dataset.ProjectionMode projectionMode = dataAttr.getDataset().getProjectionViewMode();
                dSet.put(PROJECTION, projectionMode.name());
                datasetList.add(dSet);
            });
            List<Map<String, Object>> peakLists = new ArrayList<>();
            sd.put(PEAKLISTS, peakLists);
            List<String> peakListNames = peakListsOnFx(chart);

            for (String peakListName : peakListNames) {
                Map<String, Object> pSet = new HashMap<>();
                pSet.put("name", peakListName);
                pSet.put(CONFIG, pconfigOnFx(chart, peakListName));
                peakLists.add(pSet);
                peakLists.add(pSet);
            }
        }
        Map<String, String> stripData = strips(controller);
        if ((stripData != null) && stripData.containsKey(PEAKLIST)) {
            winMap.put(STRIPS, stripData);
        }
        Map<String, Object> runaboutData = runabout(controller);
        if ((runaboutData != null) && runaboutData.containsKey(ARRANGEMENT)) {
            winMap.put(RUNABOUT, runaboutData);
        }
        var yaml = new Yaml();
        return yaml.dump(winMap);

    }

    public String genYAML() throws ExecutionException, InterruptedException {
        return Fx.runOnFxThreadAndWait(() -> genYAMLOnFx(getActiveController()));
    }

    public String genYAML(FXMLController controller) throws ExecutionException, InterruptedException {
        return Fx.runOnFxThreadAndWait(() -> genYAMLOnFx(controller));

    }

    public void loadYAML(String inputData, String pathName, boolean createNewStage) {
        Fx.runOnFxThread(() -> loadYAMLOnFx(inputData, pathName, createNewStage));
    }

    public void loadYAMLOnFx(String inputData, String pathName, boolean createNewStage) {
        Yaml yaml = getYamlReader();
        var data = (Map<String, Object>) yaml.load(inputData);
        FXMLController controller = getStageController(createNewStage, pathName);

        Stage stage = controller.getStage();
        if (data.containsKey(GEOMETRY)) {
            var geometry = (List<Double>) data.get(GEOMETRY);
            geometryOnFx(stage, geometry);
        }
        List<Map<String, Object>> spectraList = new ArrayList<>();
        if (data.containsKey(SPECTRA)) {
            spectraList = (List<Map<String, Object>>) data.get(SPECTRA);
        }
        if (data.containsKey("grid")) {
            int nGridSpectra = countGridSpectra(spectraList);
            var grid = (List<Integer>) data.get("grid");
            gridOnFx(controller, grid.get(0), grid.get(1), nGridSpectra);
        }
        if (data.containsKey(SCONFIG)) {
            var map = (Map<String, Object>) data.get(SCONFIG);
            sconfigOnFx(controller, map);
        }
        processSpectraData(controller, spectraList);
        if (data.containsKey(STRIPS)) {
            var stripData = (Map<String, Object>) data.get(STRIPS);
            String peakListName = stripData.get(PEAKLIST).toString();
            String xDim = stripData.get("xdim").toString();
            String zDim = stripData.get("zdim").toString();
            strips(controller, peakListName, xDim, zDim);
        }
        if (data.containsKey(RUNABOUT)) {
            var runAboutData = (Map<String, Object>) data.get(RUNABOUT);
            runabout(controller, runAboutData);
        }
    }

    private FXMLController getStageController(boolean createNewStage, String pathName) {
        FXMLController controller;
        if (createNewStage) {
            File file = new File(pathName);
            String title = file.getName();
            if (title.endsWith("_fav.yaml")) {
                title = title.replace("_fav.yaml", "");
            } else if (title.endsWith(".yaml")) {
                title = title.replace(".yaml", "");
            }
            controller = AnalystApp.getFXMLControllerManager().newController(title);
            PolyChart chartActive = controller.getCharts().get(0);
            controller.setActiveChart(chartActive);
        } else {
            controller = getActiveController();
        }
        return controller;
    }

    private int countGridSpectra(List<Map<String, Object>> spectraList) {
        return spectraList.size() - (int) spectraList.stream().filter(map -> map.containsKey(INSET)).count();
    }

    private void processSpectraData(FXMLController controller, List<Map<String, Object>> spectraList) {
        int iWin = 0;
        PolyChart lastGridChart = null;
        for (var spectraMap : spectraList) {
            int iRow;
            int iCol;
            int rowSpan = 1;
            int columnSpan = 1;
            PolyChart chart;
            if (spectraMap.containsKey(INSET)) {
                var inset = (List<Double>) spectraMap.get(INSET);
                double fX = inset.get(0);
                double fY = inset.get(1);
                double fWidth = inset.get(2);
                double fHeight = inset.get(3);
                InsetChart insetChart = controller.addInsetChartTo(lastGridChart);
                insetChart.setFractionalPosition(fX, fY, fWidth, fHeight);
                chart = insetChart.chart();
            } else {
                chart = controller.getCharts().get(iWin++);
                if (spectraMap.containsKey("grid")) {
                    var grid = (List<Integer>) spectraMap.get("grid");
                    iRow = grid.get(0);
                    iCol = grid.get(1);
                    if (grid.size() > 3) {
                        rowSpan = grid.get(2);
                        columnSpan = grid.get(3);
                    }
                    lastGridChart = chart;
                } else {
                    iRow = 0;
                    iCol = 0;
                    lastGridChart = chart;
                }
                grid(chart, iRow, iCol, rowSpan, columnSpan);
                controller.setActiveChart(chart);
            }
            processChart(chart, spectraMap);
            chart.refresh();

            Optional<Dataset.ProjectionMode> viewModeOpt = chart.getDatasetAttributes().stream()
                    .map(dataAttr -> dataAttr.getDataset().getProjectionViewMode()).filter(pMode -> pMode != Dataset.ProjectionMode.OFF).findFirst();
            viewModeOpt.ifPresent(chart::projectDataset);
        }
    }

    private void processChart(PolyChart chart, Map<String, Object> spectraMap) {
        if (spectraMap.containsKey(CCONFIG)) {
            var cconfigMap = (Map<String, Object>) spectraMap.get(CCONFIG);
            cconfigOnFx(chart, cconfigMap);
        }
        var datasetList = (List<Map<String, Object>>) spectraMap.get("datasets");
        List<String> datasetNames = datasetList.stream().filter(m -> m.containsKey("name")).map(m -> m.get("name").toString()).toList();
        chart.updateDatasetsByNames(datasetNames);

        if (spectraMap.containsKey("lim")) {
            var limMap = (Map<String, List<Double>>) spectraMap.get("lim");
            for (var entry : limMap.entrySet()) {
                String axName = entry.getKey();
                List<Double> limits = entry.getValue();
                double v1 = limits.get(0);
                double v2 = limits.get(1);
                limitOnFx(chart, axName, v1, v2);
            }
        }
        processDatasets(chart, datasetList);
        if (spectraMap.containsKey(PEAKLISTS)) {
            var peakListList = (List<Map<String, Object>>) spectraMap.get(PEAKLISTS);
            processPeakLists(chart, peakListList);
        }
        if (spectraMap.containsKey(ANNOTATIONS)) {
            String annotations = spectraMap.get(ANNOTATIONS).toString();
            loadAnnotationsOnFx(chart, annotations);
        }
    }

    private void processDatasets(PolyChart chart, List<Map<String, Object>> datasetList) {
        for (var datasetMap : datasetList) {
            String name = datasetMap.get("name").toString();
            if (datasetMap.containsKey(CONFIG)) {
                var configMap = (Map<String, Object>) datasetMap.get(CONFIG);
                configOnFx(chart, List.of(name), configMap);
            }
            if (datasetMap.containsKey("dims")) {
                List<Integer> dimList = (List<Integer>) datasetMap.get("dims");
                int[] dims = new int[dimList.size()];
                for (int i = 0; i < dims.length; i++) {
                    dims[i] = dimList.get(i);
                }
                setDimsOnFx(chart, name, dims);
            }
            if (datasetMap.containsKey(PROJECTION)) {
                Object viewModeObj = datasetMap.get(PROJECTION);
                if (viewModeObj != null) {
                    setProjectionOnFx(chart, name, viewModeObj.toString());
                }
            }
        }
    }

    private void processPeakLists(PolyChart chart, List<Map<String, Object>> peakListList) {
        List<String> peakListNames = new ArrayList<>();
        for (var peakListMap : peakListList) {
            String name = peakListMap.get("name").toString();
            peakListNames.add(name);
        }
        chart.updatePeakListsByName(peakListNames);
        for (var peakListMap : peakListList) {
            String name = peakListMap.get("name").toString();
            var peakConfigMap = (Map<String, Object>) peakListMap.get(CONFIG);
            pconfigOnFx(chart, List.of(name), peakConfigMap);
        }
    }
}
