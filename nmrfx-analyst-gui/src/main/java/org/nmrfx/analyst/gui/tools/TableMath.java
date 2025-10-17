package org.nmrfx.analyst.gui.tools;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ExpressionEvaluator;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.utils.GUIUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.regex.*;

import static org.nmrfx.analyst.gui.tools.TableMath.VariableNameScanner.findVariables;

public class TableMath {
    Stage stage = null;
    ScannerTool scannerTool;
    Map<String, ChoiceBox<String>> choiceMap = new HashMap<>();
    TextField expressionField = new TextField();
    TextField resultField = new TextField();
    Map<String, String> parMap = new HashMap<>();
    static Pattern headerPattern = Pattern.compile("^([_$a-zA-Z][_$a-zA-Z0-9]*)(:.*)*");
    static Pattern exprVarPattern = Pattern.compile("(?<!\\.)\\b[_$a-zA-Z][_$a-zA-Z0-9]*\\b(?!\\s*\\(|\\s*\\.)");

    public TableMath(ScannerTool scannerTool) {
        this.scannerTool = scannerTool;
    }

    public static class VariableNameScanner {
        // List of Java reserved keywords
        private static final Set<String> JAVA_KEYWORDS = Set.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "const", "continue", "default", "do", "double",
                "else", "enum", "extends", "final", "finally", "float", "for",
                "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private",
                "protected", "public", "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this", "throw", "throws",
                "transient", "try", "void", "volatile", "while", "null", "true", "false"
        );

        private VariableNameScanner() {

        }

        public static List<String> findVariables(String text) {

            // Regex for valid identifier pattern
            Matcher matcher = exprVarPattern.matcher(text);

            List<String> validVariables = new ArrayList<>();

            while (matcher.find()) {
                String candidate = matcher.group();
                if (!JAVA_KEYWORDS.contains(candidate)) {
                    validVariables.add(candidate);
                }
            }

            return validVariables;
        }
    }

    List<String> getColumnNames(ScanTable scanTable) {
        return scanTable.getHeaders().stream().filter(scanTable::isNumeric).toList();
    }

    Map<String, String> getVMap(ScanTable scanTable) {
        Map<String, String> vMap = new HashMap<>();
        for (String header : scanTable.getHeaders()) {
            Matcher matcher = headerPattern.matcher(header.trim());
            if (matcher.matches()) {
                String vGroup = matcher.group(1);
                vMap.put(vGroup, header);
            }
        }
        return vMap;
    }

    ExpressionEvaluator createExpressionEvaluator(String[] parNames, Class[] parClasses, String expression) throws CompileException {
        ExpressionEvaluator ee = new ExpressionEvaluator();
        ee.setParameters(parNames, parClasses);
        ee.setExpressionType(Double.class);
        ee.cook(expression);
        return ee;
    }

    Double performExpression(ExpressionEvaluator ee, Double[] parValues) throws InvocationTargetException {
        return (Double) ee.evaluate(parValues);
    }

    void processExpress(ScanTable scanTable, Map<String, String> variableMap, String expression, String resultName) {
        Double[] values = new Double[variableMap.size()];
        String[] names = new String[variableMap.size()];
        Class[] classes = new Class[variableMap.size()];
        scanTable.addTableColumn(resultName, "D");
        ExpressionEvaluator expressionEvaluator;
        int iVar = 0;
        for (var entry : variableMap.entrySet()) {
            names[iVar] = entry.getKey();
            classes[iVar] = Double.class;
            iVar++;
        }

        try {
            expressionEvaluator = createExpressionEvaluator(names, classes, expression);
        } catch (CompileException compileException) {
            ExceptionDialog exceptionDialog = new ExceptionDialog(compileException);
            exceptionDialog.showAndWait();
            return;
        }

        for (FileTableItem item : scanTable.getItems()) {
            int jVar = 0;
            for (var entry : variableMap.entrySet()) {
                String varName = entry.getValue();
                values[jVar++] = item.getDouble(varName);
            }
            try {
                Double result = performExpression(expressionEvaluator, values);
                item.setExtra(resultName, result);
            } catch (InvocationTargetException e) {
                ExceptionDialog exceptionDialog = new ExceptionDialog(e);
                exceptionDialog.showAndWait();
                break;
            }
        }
    }

    private void doMath() {
        ScanTable scanTable = scannerTool.getScanTable();
        var vMap = getVMap(scanTable);
        for (var entry : choiceMap.entrySet()) {
            ChoiceBox<String> choiceBox = entry.getValue();
            String tableColumn = choiceBox.getValue();
            if ((tableColumn != null) && !tableColumn.isBlank()) {
                parMap.put(entry.getKey(), tableColumn);
            }
        }

        List<String> exprVariables = findVariables(expressionField.getText());
        for (String exprVar : exprVariables) {
            String headerName = vMap.get(exprVar);
            if (!parMap.containsKey(exprVar)) {
                if (!vMap.containsKey(exprVar)) {
                    GUIUtils.warn("Invalid variable", exprVar + " doesn't exist");
                    return;
                } else {
                    parMap.put(exprVar, headerName);
                }
            }
        }

        processExpress(scanTable, parMap, expressionField.getText(), resultField.getText());
        scanTable.getTableView().refresh();
    }

    private void updateChoiceBox(ChoiceBox<String> choiceBox) {
        ScanTable scanTable = scannerTool.getScanTable();
        List<String> columnNames = getColumnNames(scanTable);
        choiceBox.getItems().clear();
        choiceBox.getItems().addAll(columnNames);
    }

    public void showTableMath() {
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Table Math");
            BorderPane borderPane = new BorderPane();
            Scene stageScene = new Scene(borderPane);
            GridPane grid = new GridPane();
            grid.setVgap(10);
            grid.setHgap(10);
            grid.setPadding(new Insets(10, 10, 10, 10));
            borderPane.setCenter(grid);

            String[] parNames = {"A", "B", "C", "D"};
            int iRow = 0;
            for (String parName : parNames) {
                Label varLabel = new Label(parName);
                ChoiceBox<String> choiceBox = new ChoiceBox<>();
                choiceBox.setOnShowing(e -> updateChoiceBox(choiceBox));
                grid.add(varLabel, 0, iRow);
                grid.add(choiceBox, 1, iRow);
                iRow++;
                choiceMap.put(parName, choiceBox);
            }

            Label exprLabel = new Label("Expression");
            expressionField.setPrefWidth(300);
            grid.add(exprLabel, 0, iRow);
            grid.add(expressionField, 1, iRow);
            iRow++;

            grid.add(new Label("Result"), 0, iRow);
            grid.add(resultField, 1, iRow);

            stage.setScene(stageScene);

            Button mathButton = new Button("Evaluate");
            mathButton.setOnAction(e -> doMath());
            ToolBar toolBar = new ToolBar();
            toolBar.getItems().add(mathButton);
            borderPane.setBottom(toolBar);

        }
        stage.show();
        stage.toFront();

    }
}
