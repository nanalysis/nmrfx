/*
 * CoMD/NMR Software : A Program for Analyzing NMR Dynamics Data
 * Copyright (C) 2018-2019 Bruce A Johnson
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.analyst.gui.git;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

/**
 * This class shows the Git History for a project.
 *
 * @author mbeckwith
 *
 */
public class GitHistoryController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(GitHistoryController.class);

    static GitHistoryController controller = null;
    @FXML
    TreeTableView<HistoryData> historyTable = new TreeTableView<>();
    Stage stage;
    GUIProject project;
    GitManager gitManager;
    @FXML
    MenuButton actionMenu = new MenuButton();
    @FXML
    TextArea commitMessage = new TextArea();
    @FXML
    Button saveButton = new Button();

    private enum GitAction {
        DIFF("Diff to Selected Commit"),
        MERGE("Merge Selected Commit into Master Branch"),
        NEW("Create New Branch from Selected Commit"),
        DELETE("Delete Selected Branch");


        private final String label;

        GitAction(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }    
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        project = GUIProject.getActive();
        initializeHistory();
        List<GitAction> choices = Arrays.asList(GitAction.values());
        setUpMenu(actionMenu, choices);

    }

    /**
     * Create the controller.
     * 
     * @return GitHistoryController. The git history controller.
     */
    public static GitHistoryController create(GitManager gitManager) {
        FXMLLoader loader = new FXMLLoader(GitHistoryController.class.getResource("/fxml/HistoryScene.fxml"));
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            controller = loader.getController();
            controller.stage = stage;
            controller.gitManager = gitManager;
            Screen screen = Screen.getPrimary();
            Rectangle2D screenSize = screen.getBounds();
            stage.toFront();
            stage.setY(screenSize.getHeight() - stage.getHeight());
        } catch (IOException ioE) {
            log.error("Error creating history controller", ioE);
        }

        return controller;

    }

    public String getCommitMessage(){
        String msg = commitMessage.getText();
        commitMessage.clear();
        return msg;
    }
    
    private void setUpMenu(MenuButton menu, List<GitAction> values) {
        for (var choice:values) {
            MenuItem menuItem = new MenuItem(choice.label);
            menu.getItems().add(menuItem);
            menuItem.setOnAction(e -> gitMenuAction(choice));
        }

        saveButton.setOnAction(e -> {
            try {
                project.saveProject();
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        });
    }
    
    private void initializeHistory()  {
        TreeItem<HistoryData> root = new TreeItem<>(new HistoryData("Branches"));
        historyTable.setRoot(root);
        historyTable.setShowRoot(false);

        TreeTableColumn<HistoryData, String> branchColumn = new TreeTableColumn<>("Branch");
        TreeTableColumn<HistoryData, Integer> indexColumn = new TreeTableColumn<>("Index");
        TreeTableColumn<HistoryData, String> dateColumn = new TreeTableColumn<>("Date");
        TreeTableColumn<HistoryData, String> userColumn = new TreeTableColumn<>("User");
        TreeTableColumn<HistoryData, String> revisionColumn = new TreeTableColumn<>("Revision");
        TreeTableColumn<HistoryData, String> parentColumn = new TreeTableColumn<>("Parent");
        TreeTableColumn<HistoryData, String> messageColumn = new TreeTableColumn<>("Message");

        branchColumn.setCellValueFactory(cellData -> cellData.getValue().getValue().branchProperty());
        indexColumn.setCellValueFactory(cellData -> cellData.getValue().getValue().indexProperty().asObject());
        dateColumn.setCellValueFactory(cellData -> cellData.getValue().getValue().dateProperty());
        userColumn.setCellValueFactory(cellData -> cellData.getValue().getValue().userProperty());
        revisionColumn.setCellValueFactory(cellData -> cellData.getValue().getValue().revisionProperty());
        parentColumn.setCellValueFactory(cellData -> cellData.getValue().getValue().parentProperty());
        messageColumn.setCellValueFactory(cellData -> cellData.getValue().getValue().messageProperty());

        branchColumn.setPrefWidth(150);
        dateColumn.setPrefWidth(150);
        messageColumn.setPrefWidth(150);
        userColumn.setPrefWidth(75);
        revisionColumn.setPrefWidth(75);
        parentColumn.setPrefWidth(75);

        historyTable.getColumns().clear();
        historyTable.getColumns().setAll(branchColumn, indexColumn, dateColumn, messageColumn, userColumn, revisionColumn, parentColumn);
    }

    public HistoryData getSelectedItem() {
        return historyTable.getSelectionModel().getSelectedItem().getValue();
    }

    /**
     * Update the table with the git history.
     */
    public void updateHistory() {
        if (controller != null && controller.stage != null) {
            String currentBranch = gitManager.gitCurrentBranch();
            if (currentBranch.length() == 40) {
                currentBranch = currentBranch.substring(0,8);
            }
            controller.stage.setTitle("Git History (Project = " + project.getName() + ", Current Branch = " + currentBranch + ")");
        }

        TreeItem<HistoryData> root = historyTable.getRoot();
        root.getChildren().clear();

        List<Ref> branches = gitManager.gitBranches();
        for (Ref branch : branches) {
            ObservableList<TreeItem<HistoryData>> data = FXCollections.observableArrayList();
            String branchName = branch.getName();
            List<RevCommit> gitLog = gitManager.gitLog(branchName);

            TreeItem<HistoryData> branchItem = new TreeItem<>(new HistoryData(branchName));
            root.getChildren().add(branchItem);

            int idx = gitLog.size();
            for (RevCommit entry : gitLog) {
                HistoryData historyData = new HistoryData(entry, idx, branchName);
                TreeItem<HistoryData> rowItem = new TreeItem<>(historyData);
                data.addAll(rowItem);
                idx--;
            }
            branchItem.getChildren().addAll(data);
        }

        ObservableList<TreeItem<HistoryData>> children = root.getChildren();
        for (TreeItem<HistoryData> child : children) {
            String childBranch = child.getValue().getShortBranch();
            if (childBranch.equals(gitManager.gitCurrentBranch())) {
                child.setExpanded(true);
            }
        }

        historyTable.refresh();
        stage.show();
        stage.toFront();
    }
    
    private void gitMenuAction(GitAction choice) {
        switch (choice) {
            case DIFF -> gitDiff();
            case MERGE -> gitMerge();
            case NEW -> gitCreateBranch();
            case DELETE -> gitDeleteBranch();
        }
    }
    
    /**
     * Load the yaml file with the window information for the given commit.
     *
     * @param branch String. The branch name of the commit.
     * @param currentBranch String. The name of the currently checked out branch.
     */
    public void gitLoad(String branch, String currentBranch) {
        String shortBranch = gitManager.gitShortBranch(branch);
        String shortCurrentBranch = gitManager.gitShortBranch(currentBranch);
        if (shortCurrentBranch.equals(shortBranch)) {
            gitManager.gitCheckout(branch);
        } else {
            alert("Currently on the " + shortCurrentBranch + " branch. Cannot load windows from the " + shortBranch + " branch.");
        }
        
    }

    
    /**
     * Reset to the selected commit. All subsequent commits will be deleted.
     */
    public void gitReset() {
        HistoryData data = historyTable.getSelectionModel().getSelectedItem().getValue();
        if (data != null) {
            int idx = data.getIndex();
            RevCommit commit = data.commitInfo;
            String branch = data.getBranch();
            String shortBranch = gitManager.gitShortBranch(branch);
            String currentBranch = gitManager.gitShortBranch(gitManager.gitCurrentBranch());
            if (shortBranch.equals(currentBranch)) {
                boolean reset = gitManager.gitResetToCommit(idx, commit, branch);
                if (reset) {
                    updateHistory();
                }
            } else {
                alert("Currently on the " + currentBranch + " branch. Cannot reset to a commit on the " + shortBranch + " branch.");
            }
        } else {
            alert("No commit selected");
        }
    }
    
    /**
     * Revert the selected commit. 
     */
    public void gitRevert() {
        HistoryData data = historyTable.getSelectionModel().getSelectedItem().getValue();
        if (data != null) {
            int idx = data.getIndex();
            RevCommit commit = data.commitInfo;
            String branch = data.getBranch();
            String shortBranch = gitManager.gitShortBranch(branch);
            String currentBranch = gitManager.gitShortBranch(gitManager.gitCurrentBranch());
            if (shortBranch.equals(currentBranch)) {
                gitManager.gitRevertCommit(idx, commit, branch);
                updateHistory();
            } else {
                alert("Currently on the " + currentBranch + " branch. Cannot reset to a commit on the " + shortBranch + " branch.");
            }
        } else {
            alert("No commit selected");
        }
    }
    
    /**
     * Merge the selected commit into the master branch.
     */
    public void gitMerge() {
        HistoryData data = historyTable.getSelectionModel().getSelectedItem().getValue();
        if (data != null) {
            RevCommit commit = data.commitInfo;
            String branch = data.getShortBranch();
            int idx = data.getIndex();
            gitManager.gitMerge("master", commit, branch, idx);
            updateHistory();
        } else {
            alert("No commit selected");
        }
    }
    
    /**
     * Perform a git diff to get the changes from the selected commit.
     */
    public void gitDiff() {
        HistoryData data = historyTable.getSelectionModel().getSelectedItem().getValue();
        if (data != null) {
            String commit = data.getRevision();
            gitManager.gitDiff(commit);
        } else {
            alert("No commit selected");
        }
    }
    
    /**
     * Create a new branch starting from the selected commit.
     */
    public void gitCreateBranch() {
        HistoryData data = historyTable.getSelectionModel().getSelectedItem().getValue();
        if (data != null) {
            String origBranch = data.getShortBranch();
            int idx = data.getIndex();
            if (origBranch.equals("master") && idx < gitManager.gitLog("master").size()) {
                alert("Branches created from the master branch must be from the most recent commit.");
            } else {
                String newBranch = GUIUtils.input("Enter New Branch Name");
                if (!newBranch.isBlank()) {
                    String commit = data.getRevision();
                    gitManager.gitCreateBranch(origBranch, newBranch, commit, idx);
                    updateHistory();
                    String currentBranch = gitManager.gitCurrentBranch();
                    gitLoad(newBranch, currentBranch);
                }
            }
        } else {
            alert("No branch selected");
        }
        stage.show();
    }
    
    /**
     * Delete the selected branch.
     */
    public void gitDeleteBranch() {
        HistoryData data = historyTable.getSelectionModel().getSelectedItem().getValue();
        if (data != null) {
            String branch = data.getShortBranch();
            gitManager.gitDeleteBranch(branch);
            updateHistory();
        } else {
            alert("No branch selected");
        }
        stage.show();
    }
    
    /**
     * Checkout to the selected branch.
     */
    public void gitCheckoutBranch() {
        HistoryData data = historyTable.getSelectionModel().getSelectedItem().getValue();
        if (data != null) {
            String branch = data.getShortBranch();
            gitManager.gitCheckout(branch);
            updateHistory();
        } else {
            alert("No branch selected");
        }
    }
    /**
     * Checkout to the selected commit.
     */
    public void gitCheckoutCommit() {
        HistoryData data = historyTable.getSelectionModel().getSelectedItem().getValue();
        if (data != null) {
            String revision = data.getRevision();
            gitManager.gitCheckout(revision);
            updateHistory();
        } else {
            alert("No commit selected");
        }
    }
    private void alert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
    
    public void close() {
        stage.hide();
    }

    public void show() {
        stage.show();
        stage.toFront();
    }
    
    public Stage getStage() {
        return stage;
    }
    
    public GUIProject getProject() {
        return project;
    }
    
    public void setProject(GUIProject newProject) {
        project = newProject;
    }

}
