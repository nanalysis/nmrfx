package org.nmrfx.processor.gui.log;

import ch.qos.logback.classic.Level;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LogConsoleControllerTest {

    private LogConsoleController logConsoleController;
    private LogRecord record1;
    private LogRecord record2;
    private LogRecord record3;
    private ObservableList<LogRecord> recordsList;

    @Before
    public void setUp() {
        logConsoleController = new LogConsoleController();
        record1 = mock(LogRecord.class);
        when(record1.getLoggerName()).thenReturn("org.nmrfx.analyst.gui.plugin.PluginLoader");
        when(record1.getSourceMethodName()).thenReturn("doSomething");
        when(record1.getLevel()).thenReturn(LogLevel.ERROR);
        when(record1.getMessage()).thenReturn("Test message 1");
        record2 = mock(LogRecord.class);
        when(record2.getLoggerName()).thenReturn("org.nmrfx.analyst.AnalystApp");
        when(record2.getSourceMethodName()).thenReturn("doAnotherThing");
        when(record2.getLevel()).thenReturn(LogLevel.WARNING);
        when(record2.getMessage()).thenReturn("Test message 2");
        record3 = mock(LogRecord.class);
        when(record3.getLoggerName()).thenReturn("org.nmrfx.processor.gui.LogConsoleController");
        when(record3.getSourceMethodName()).thenReturn("doDifferentThing");
        when(record3.getLevel()).thenReturn(LogLevel.INFO);
        when(record3.getMessage()).thenReturn("Test message 3");
        recordsList = FXCollections.observableList(new ArrayList<>());
        recordsList.add(record1);
        recordsList.add(record2);
        recordsList.add(record3);
    }

    @Test
    public void testBuildFilterByInt() {
        Predicate<LogRecord> testPredicate = logConsoleController.buildFilter(Level.ERROR_INTEGER, null, "");
        FilteredList<LogRecord> filteredRecordsList = new FilteredList<>(recordsList, testPredicate);
        assertEquals(1, filteredRecordsList.size());
        assertEquals(record1, filteredRecordsList.get(0));
    }

    @Test
    public void testBuildFilterByLogSection() {
        Predicate<LogRecord> testPredicate = logConsoleController.buildFilter(Level.ALL_INTEGER, LogSection.PROCESSOR_GUI, "");
        FilteredList<LogRecord> filteredRecordsList = new FilteredList<>(recordsList, testPredicate);
        assertEquals(1, filteredRecordsList.size());
        assertEquals(record3, filteredRecordsList.get(0));
    }

    @Test
    public void testBuildFilterByString() {
        Predicate<LogRecord> testPredicate = logConsoleController.buildFilter(Level.ALL_INTEGER, null, "doSome");
        FilteredList<LogRecord> filteredRecordsList = new FilteredList<>(recordsList, testPredicate);
        assertEquals(1, filteredRecordsList.size());
        assertEquals(record1, filteredRecordsList.get(0));
    }

    @Test
    public void testBuildFilterWithAll() {
        Predicate<LogRecord> testPredicate = logConsoleController.buildFilter(Level.INFO_INTEGER, LogSection.ANALYST, "ert");
        FilteredList<LogRecord> filteredRecordsList = new FilteredList<>(recordsList, testPredicate);
        assertEquals(1, filteredRecordsList.size());
        assertEquals(record2, filteredRecordsList.get(0));
    }
}
