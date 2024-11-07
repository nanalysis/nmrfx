package org.nmrfx.analyst.gui.BMRB;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
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
import java.util.regex.Pattern;

public class BMRBDepositionController implements Initializable, StageBasedController {
    private Stage stage;

    public enum DepositionMode {
        Production,
        Test
    }

    @FXML
    private VBox vBox = new VBox();
    TextField emailField = new TextField();

    ChoiceBox<DepositionMode> modeChoice = new ChoiceBox<>();

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

    private Stage getStage() {
        return this.stage;
    }

    private static boolean validateEmail(String emailAddress) {
        String regexPattern = "^(.+)@(\\S+)$";
        return Pattern.compile(regexPattern)
                .matcher(emailAddress)
                .matches();
    }

    void setUp() {
        Text title = new Text("Select data to deposit:");
        GridPane gridPane = new GridPane();
        HBox modeBox = new HBox();
        Label modeLabel = new Label("Mode");
        modeBox.setSpacing(20);
        modeBox.getChildren().addAll(modeLabel, modeChoice);
        modeChoice.getItems().addAll(DepositionMode.Production, DepositionMode.Test);
        modeChoice.setValue(DepositionMode.Production);
        vBox.getChildren().addAll(title, modeBox, gridPane);
        vBox.setSpacing(10);

        ColumnConstraints col0 = new ColumnConstraints(125);
        ColumnConstraints col1 = new ColumnConstraints(125);
        gridPane.getColumnConstraints().addAll(col0, col1);
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
        hBox.setMinHeight(20);
        hBox.setSpacing(10);
        emailField.setMinWidth(200);
        emailField.setPromptText("required");
        hBox.getChildren().addAll(new Label("Email:"), emailField);
        hBox.setAlignment(Pos.CENTER);

        HBox hBox1 = new HBox();
        hBox1.setMinHeight(20);
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
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    void depositSTAR(Map<NMRStarWriter.StarTypes, SimpleBooleanProperty> starTypesMap) {
        String emailAddress = emailField.getText();
        if (!validateEmail(emailAddress)) {
            Fx.runOnFxThread(() -> GUIUtils.warn("Invalid email address", "Invalid email address"));
            return;
        }

        String projectName = GUIProject.getActive().getDirectory() == null ? "NMRFx_Project" :
                GUIProject.getActive().getDirectory().getFileName().toString().replace(' ', '_');

        if (projectName.isBlank()) {
            projectName = "NMRFx_Project";
        }
        CompletableFuture<String> futureResponse;
        StringWriter starStr = NMRStarWriter.writeToString(starTypesMap);

        try {
            futureResponse = BMRBio.depositEntry(modeChoice.getValue() == DepositionMode.Production, emailAddress, projectName, starStr);
        } catch (Exception e) {
            ExceptionDialog dialog = new ExceptionDialog(e);
            dialog.showAndWait();
            return;
        }

        futureResponse.thenAccept(r ->
                Fx.runOnFxThread(() -> {
                    String[] parts = r.split(":");
                    String depID = parts.length == 2 ? parts[1].replace("}","") : r;
                    String message = "Check your email for a link to the deposition\n" + depID;
                    GUIUtils.acknowledge(message);
                }));
        stage.close();
    }
}
