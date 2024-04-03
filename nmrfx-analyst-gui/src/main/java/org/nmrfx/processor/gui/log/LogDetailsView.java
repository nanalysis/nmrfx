package org.nmrfx.processor.gui.log;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import org.nmrfx.chemistry.utilities.NvUtil;

import java.awt.*;
import java.awt.datatransfer.StringSelection;


/**
 * Class to display the details of a LogRecord.
 */
public class LogDetailsView extends GridPane {

    private static final int SPACING = 5;
    private final TextField datetime = new TextField();
    private final TextField location = new TextField();
    private final TextArea message = new TextArea();
    private final Button copyButton = GlyphsDude.createIconButton(FontAwesomeIcon.COPY);
    private LogRecord logRecord;

    public LogDetailsView() {
        // Set controls options
        datetime.setEditable(false);
        location.setEditable(false);
        message.setEditable(false);
        copyButton.setOnAction(this::toClipboardClicked);
        // Add controls to GridPane (4 rows x 2 columns)
        add(new Text("Log details: "), 0, 0, 1, 1);
        add(copyButton, 1, 0, 1, 1);
        add(new Text("When: "), 0, 1, 1, 1);
        add(datetime, 1, 1, 1, 1);
        add(new Text("Where: "), 0, 2, 1, 1);
        add(location, 1, 2, 1, 1);
        add(message, 1, 3, 1, 3);
        // Set Formatting
        setVgap(SPACING);
        setHgap(SPACING);
        setPadding(new Insets(SPACING, SPACING, SPACING, SPACING));
        setHalignment(copyButton, HPos.RIGHT);
        ColumnConstraints noConstraints = new ColumnConstraints();
        ColumnConstraints stretchColumn = new ColumnConstraints();
        // set Column to stretch to fill remaining horizontal space
        stretchColumn.setHgrow(Priority.ALWAYS);
        getColumnConstraints().addAll(noConstraints, stretchColumn);

        clearDetails();
    }

    private void clearDetails() {
        this.logRecord = null;
        datetime.setText("");
        location.setText("");
        message.setText("");
    }

    /**
     * Sets the controls with the values from logRecord.
     *
     * @param logRecord The LogRecord object.
     */
    public void setDetails(LogRecord logRecord) {
        if (logRecord == null) {
            clearDetails();
        } else {
            this.logRecord = logRecord;
            datetime.setText(logRecord.getTime().toString());
            location.setText(logRecord.getLoggerName() + "#" + logRecord.getSourceMethodName());
            message.setText(logRecord.getMessage());
        }
    }

    /**
     * Copies the values of the controls as well as the software version to the
     * system clipboard.
     *
     * @param actionEvent
     */
    private void toClipboardClicked(ActionEvent actionEvent) {
        String content = "";
        if (logRecord != null) {
            content += "[" + logRecord.getLevel().toString() + ":" + datetime.getText() + "] " + location.getText() + "\n" + message.getText()
                    + "\n\nSoftware version: " + NvUtil.getVersion();
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content), null);
    }


}
