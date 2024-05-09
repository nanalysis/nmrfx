package org.nmrfx.analyst.gui.BMRB;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chemistry.io.NMRStarWriter;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.star.BMRBio;
import org.nmrfx.utils.GUIUtils;

import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class BMRBDepositionController implements Initializable, StageBasedController {
    private Stage stage;

    @FXML
    private VBox vBox = new VBox();
    TextField emailField = new TextField();
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setUp();
    }
    public static BMRBDepositionController create() {
        BMRBDepositionController controller = Fxml.load(BMRBDepositionController.class, "BMRBDepositionScene.fxml")
                .withNewStage("Deposit to BMRB")
                .getController();
        controller.getStage().show();
        return controller;
    }

    private Stage getStage() { return this.stage;}

    void setUp() {
        Text title = new Text("Select data to deposit:");
        GridPane gridPane = new GridPane();
        vBox.getChildren().addAll(title, gridPane);
        vBox.setSpacing(10);

        ColumnConstraints col0 = new ColumnConstraints(125);
        ColumnConstraints col1 = new ColumnConstraints(125);
        gridPane.getColumnConstraints().addAll(col0,col1);
        gridPane.setVgap(20);

        Map<NMRStarWriter.StarTypes, SimpleBooleanProperty> starTypesPropertiesMap = new HashMap<>();
        int row = 0;
        int col = 0;
        for (NMRStarWriter.StarTypes starType : NMRStarWriter.StarTypes.values()) {
            CheckBox checkBox = new CheckBox(starType.name);
            SimpleBooleanProperty simpleBooleanProperty = new SimpleBooleanProperty();
            simpleBooleanProperty.bind(checkBox.selectedProperty());
            starTypesPropertiesMap.put(starType, simpleBooleanProperty);
            gridPane.add(checkBox, col, row);
            col++;
            if (col == 2) {
                col = 0;
                row++;
            }
        }

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        emailField.setMinWidth(200);
        emailField.setPromptText("required");
        hBox.getChildren().addAll(new Label("Email:"), emailField);
        hBox.setAlignment(Pos.CENTER);

        HBox hBox1 = new HBox();
        Button depositButton = new Button();
        depositButton.setText("Deposit");
        depositButton.setMinWidth(30);
        depositButton.disableProperty().bind(emailField.textProperty().isEmpty());
        depositButton.setOnAction(e -> depositSTAR(starTypesPropertiesMap));
        hBox1.getChildren().add(depositButton);
        hBox1.setAlignment(Pos.CENTER);
        vBox.getChildren().addAll(hBox, hBox1);
    }

    @Override
    public void setStage(Stage stage) {this.stage = stage;}

    void depositSTAR(Map<NMRStarWriter.StarTypes, SimpleBooleanProperty> starTypesMap ) {
        String emailAddress = emailField.getText();

        String projectName = GUIProject.getActive().getDirectory() == null ? "NMRFx_Project" :
                GUIProject.getActive().getDirectory().getFileName().toString();

        if (projectName.isBlank()) {
            projectName = "NMRFx_Project";
        }
        CompletableFuture<String> futureResponse;
        StringWriter starStr = NMRStarWriter.writeToString(starTypesMap);

        try {
            futureResponse = BMRBio.depositEntry(emailAddress, projectName, starStr);
        } catch (Exception e) {
            ExceptionDialog dialog = new ExceptionDialog(e);
            dialog.showAndWait();
            return;
        }

        futureResponse.thenAccept(r -> {
            Fx.runOnFxThread(() ->
                    GUIUtils.affirm(r));
        });
        stage.close();
    }
}
