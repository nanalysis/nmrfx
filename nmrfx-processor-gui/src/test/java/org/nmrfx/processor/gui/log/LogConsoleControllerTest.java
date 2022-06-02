package org.nmrfx.processor.gui.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Marker;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Predicate;

public class LogConsoleControllerTest extends TestCase {

    private static class LoggingEventTest implements ILoggingEvent {
        private final String message;
        private final Level logLevel;
        private final String loggerName;
        private final String methodName;

        public LoggingEventTest (String message, Level logLevel, String loggerName, String methodName) {
            this.message = message;
            this.logLevel = logLevel;
            this.loggerName = loggerName;
            this.methodName = methodName;
        }
        @Override
        public String getThreadName() {
            return null;
        }

        @Override
        public Level getLevel() {
            return logLevel;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public Object[] getArgumentArray() {
            return new Object[0];
        }

        @Override
        public String getFormattedMessage() {
            return message;
        }

        @Override
        public String getLoggerName() {
            return loggerName;
        }

        @Override
        public LoggerContextVO getLoggerContextVO() {
            return null;
        }

        @Override
        public IThrowableProxy getThrowableProxy() {
            return null;
        }

        @Override
        public StackTraceElement[] getCallerData() {
            StackTraceElement ste = new StackTraceElement("TestClass", methodName, "TestClass.java", 123);
            return new StackTraceElement[] {ste};
        }

        @Override
        public boolean hasCallerData() {
            return true;
        }

        @Override
        public Marker getMarker() {
            return null;
        }

        @Override
        public Map<String, String> getMDCPropertyMap() {
            return null;
        }

        @Override
        public Map<String, String> getMdc() {
            return null;
        }

        @Override
        public long getTimeStamp() {
            return System.currentTimeMillis();
        }

        @Override
        public void prepareForDeferredProcessing() {

        }
    }

    private LogConsoleController logConsoleController;
    private LogRecord record1;
    private LogRecord record2;
    private LogRecord record3;
    private ObservableList<LogRecord> recordsList;

    @Before
    public void setUp() {
        logConsoleController = new LogConsoleController();
        record1 = new LogRecord(new LoggingEventTest("Test message 1", Level.ERROR, "org.nmrfx.analyst.gui.plugin.PluginLoader", "doSomething"));
        record2 = new LogRecord(new LoggingEventTest("Test message 2", Level.WARN, "org.nmrfx.analyst.AnalystApp", "doAnotherThing"));
        record3 = new LogRecord(new LoggingEventTest("Test message 3", Level.INFO, "org.nmrfx.processor.gui.LogConsoleController", "doDifferentThing"));
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
