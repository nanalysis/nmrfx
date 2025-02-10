package org.nmrfx.analyst.gui.git;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.project.GitBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class GitManager extends GitBase {
    private static final Logger log = LoggerFactory.getLogger(GitManager.class);
    public static GitConflictController conflictController = null;
    public static GitDiffController diffController = null;
    protected static GitHistoryController historyController = null;

    GUIProject guiProject;

    public GitManager(GUIProject guiProject) throws IllegalArgumentException {
        super(guiProject);
    }

    public static GitConflictController getConflictController() {
        return conflictController;
    }

    public static GitDiffController getDiffController() {
        return diffController;
    }

    public static boolean isCommitting() {
        return commitActive;
    }

    public void close() {
        if (git != null) {
            git.close();
        }
        if (historyController != null) {
            historyController.close();
            historyController = null;
        }
        if (diffController != null) {
            diffController.close();
            diffController = null;
        }
        if (conflictController != null) {
            conflictController.close();
            conflictController = null;
        }
    }

    @FXML
    public void showHistoryAction(ActionEvent event) {
        if (historyController == null) {
            historyController = GitHistoryController.create(this);
        }
        if (historyController != null) {
            guiProject = GUIProject.getActive();
            historyController.setProject(guiProject);
            if (guiProject.getProjectDir() != null) {
                historyController.getStage().setTitle("Git History (Project = " + guiProject.getName() + ", Current Branch = " + guiProject.getGitManager().gitCurrentBranch() + ")");
            }
            historyController.updateHistory();
            historyController.getStage().show();
            historyController.getStage().toFront();
        } else {
            log.error("Couldn't make controller");
        }
    }

    @FXML
    public void showConflictAction() {
        if (conflictController == null) {
            conflictController = GitConflictController.create(this);
        }
        if (conflictController != null) {
            conflictController.getStage().show();
        } else {
            log.error("Couldn't make conflict controller");
        }
    }

    @FXML
    public void showDiffAction() {
        if (diffController == null) {
            diffController = GitDiffController.create(this);
        }
        if (diffController != null) {
            if (historyController == null) {
                historyController = GitHistoryController.create(this);
            }
            HistoryData data = historyController.getSelectedItem();
            if (data != null) {
                String branch = data.getShortBranch();
                int idx = data.getIndex();
                diffController.getStage().setTitle("Git Diff to " + branch + " commit " + idx);
            }
            diffController.getStage().show();
        } else {
            log.error("Couldn't make diff controller");
        }
    }

    public void setProject(GUIProject guiProject) {
        super.setProject(guiProject);
        if (historyController != null) {
            historyController.setProject(guiProject);
            historyController.updateHistory();
        }
    }

    public void gitCommitOnThread() {
        String msg;
        if (historyController != null) {
            msg = historyController.getCommitMessage();
        } else {
            msg = "";
        }
        gitCommit(msg);
    }

    @Override
    public boolean gitCommit(String msg) {
        commitActive = true;
        if (git == null) {
            if (!gitOpen()) {
                return false;
            }
        }
        boolean didSomething = super.gitCommit(msg);
        if (didSomething) {
            // fixme, should we do this after each commit, or leave git open
            git.close();
            git = null;
            commitActive = false;
            Fx.runOnFxThread(() -> {
                if (historyController != null) {
                    historyController.updateHistory();
                }
            });
        }
        return didSomething;
    }

    private void resolveFileConflicts(String branchName, String message) {
        List<String> contents = Arrays.asList(message.split("\n"));
        List<String> files = contents.stream().filter(s -> s.contains(".")).collect(Collectors.toList());
        files.replaceAll(s -> String.join(File.separator, projectDir.toString(), s));
        message += "\n\nPush OK to resolve file conflicts, or";
        message += "\npush CANCEL to discard changes and force checkout to " + branchName + ".";
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.CANCEL, ButtonType.OK);
        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && (response.get() == ButtonType.OK) && (!files.isEmpty())) {
            if (getConflictController() == null) {
                showConflictAction();
            }
            GitConflictController conflictInterface = getConflictController();
            if (conflictInterface != null) {
                conflictInterface.getFileMenu().setItems(FXCollections.observableList(files));
                conflictInterface.getFileMenu().setValue(files.get(0));
                conflictInterface.viewFile(files.get(0));
            }
        } else if (response.isPresent() && (response.get() == ButtonType.CANCEL)) {
            try {
                git.checkout().setForced(true).setName(branchName).call();
            } catch (GitAPIException ex1) {
                log.error("Error resolving conflicts", ex1);
            }
        }
    }

    private void gitDeleteFile(String fileName) throws GitAPIException, IOException {
        git.rm().addFilepattern(fileName).call();
        String filePath = String.join(File.separator, projectDir.toString(), fileName);
        File file = new File(filePath);
        Files.deleteIfExists(file.toPath());
    }

    /**
     * Reset to the specified commit. All subsequent commits will be deleted.
     *
     * @param idx    int. The index of the commit to reset to.
     * @param commit RevCommit. The commit to reset to.
     * @param branch String. The name of the branch of the commit to reset to.
     * @return boolean. Confirmation of the reset.
     */
    public boolean gitResetToCommit(int idx, RevCommit commit, String branch) {
        boolean reset = false;
        String shortBranch = gitShortBranch(branch);
        String message = "Are you sure you want to reset to commit " + idx + " (" + commit.getName() + ") on the " + shortBranch + " branch?\n\n";
        message += "All commits and associated files on the " + shortBranch + " branch after commit " + idx + " will be deleted.";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.YES);
        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && (response.get() == ButtonType.YES)) {
            try {
                for (int i = idx + 1; i < gitLog(branch).size() + 1; i++) {
                    String file = String.join(File.separator, "windows", "commit_" + shortBranch + "_" + i + ".yaml");
                    gitDeleteFile(file);
                }
                Ref resetRef = git.reset().setRef(commit.getName()).call();
                reset = true;
            } catch (GitAPIException | IOException ex) {
                log.error("Error reseting to commit", ex);
            }
        }
        return reset;
    }

    /**
     * Revert the specified commit.
     *
     * @param idx    int. The index of the commit to reset to.
     * @param commit RevCommit. The commit to reset to.
     * @param branch String. The name of the branch of the commit to reset to.
     */
    public void gitRevertCommit(int idx, RevCommit commit, String branch) {
        String shortBranch = gitShortBranch(branch);
        String message = "Are you sure you want to revert commit " + idx + " (" + commit.getName() + ") on the " + shortBranch + " branch?\n\n";
        message += "Any files on the " + shortBranch + " branch associated with commit " + idx + " will be deleted.";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.YES);
        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && (response.get() == ButtonType.YES)) {
            try {
                GitHistoryController hold = historyController;
                Path oldProjectDir = projectDir;
                guiProject.close();
                git.revert().include(commit).call();
                guiProject.loadGUIProject(oldProjectDir);
                if (hold != null) {
                    historyController = hold;
                }
            } catch (GitAPIException | IOException | MoleculeIOException ex) {
                log.error("Reverting commit", ex);
                String errMessage = ex.getMessage();
                if (errMessage.contains("Checkout conflict with files:")) {
                    resolveFileConflicts(shortBranch, errMessage);
                }
            }
        }
    }


    /**
     * Create a new branch from a given commitID. The new branch includes all commits up to and including the given commitID.
     *
     * @param origBranch String. The name of the original branch.
     * @param newBranch  String. The name of the new branch.
     * @param commitID   String. The commitID where the new branch should start.
     * @param idx        int. The index of the commit in the history table.
     */
    public void gitCreateBranch(String origBranch, String newBranch, String commitID, int idx) {
        String origShortBranch = gitShortBranch(origBranch);
        String newShortBranch = gitShortBranch(newBranch);
        try {
            if (git == null) {
                gitOpen();
            }
            Path oldProjectDir = projectDir;
            guiProject.close();
            git.checkout().setCreateBranch(true).setName(newBranch).setStartPoint(commitID).call();
            guiProject.loadGUIProject(oldProjectDir);
        } catch (GitAPIException | IOException | MoleculeIOException ex) {
            log.error("Creating branch", ex);
            String message = ex.getMessage();
            if (message.contains("Checkout conflict with files:")) {
                resolveFileConflicts(newShortBranch, message);
            }
        }
    }

    /**
     * Delete the given branch.
     *
     * @param branchName String. The name of the branch to delete.
     */
    public void gitDeleteBranch(String branchName) {
        String shortBranch = gitShortBranch(branchName);
        if (!shortBranch.equals("master") && !gitShortBranch(gitCurrentBranch()).equals(shortBranch)) {
            String message = "Are you sure you want to delete the " + shortBranch + " branch?";
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.YES);
            Optional<ButtonType> response = alert.showAndWait();
            if (response.isPresent() && (response.get() == ButtonType.YES)) {
                try {
                    if (git == null) {
                        gitOpen();
                    }
                    git.branchDelete().setBranchNames(branchName).call();
                } catch (GitAPIException ex) {
                    log.error("Deleting branch", ex);
                    String exMessage = ex.getMessage();
                    if (exMessage.contains("Branch was not deleted as it has not been merged yet")) {
                        exMessage = "Branch was not deleted as it has not been merged yet. Push OK to force delete the " + shortBranch + " branch.";
                        Alert mergeAlert = new Alert(Alert.AlertType.ERROR, exMessage, ButtonType.CANCEL, ButtonType.OK);
                        Optional<ButtonType> mergeResponse = mergeAlert.showAndWait();
                        if (mergeResponse.isPresent() && (mergeResponse.get() == ButtonType.OK)) {
                            try {
                                git.branchDelete().setBranchNames(branchName).setForce(true).call();
                            } catch (GitAPIException ex1) {
                                log.error("Deleting branch", ex1);
                            }
                        }
                    }
                }
            }
        } else {
            String message = "";
            if (shortBranch.equals("master")) {
                message = "The master branch cannot be deleted.";
            }
            if (gitShortBranch(gitCurrentBranch()).equals(shortBranch)) {
                message = "You are currently on the " + shortBranch + " branch. Switch to another branch to delete it.";
            }
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.showAndWait();
        }
    }

    /**
     * Runs the git diff() command to get the changes since the specified commit.
     *
     * @param refName String. The commit ID.
     */
    public void gitDiff(String refName) {
        try {
            if (git == null) {
                gitOpen();
            }
            Repository repo = git.getRepository();
            CanonicalTreeParser oldTreeIter;
            CanonicalTreeParser newTreeIter;
            try (ObjectReader reader = repo.newObjectReader()) {
                oldTreeIter = new CanonicalTreeParser();
                ObjectId newTree = repo.resolve("HEAD^{tree}");
                oldTreeIter.reset(reader, newTree);
                newTreeIter = new CanonicalTreeParser();
                ObjectId oldTree = repo.resolve(refName + "^{tree}");
                newTreeIter.reset(reader, oldTree);
            }

            showDiffAction();
            GitDiffController diffInterface = getDiffController();
            if (diffInterface != null) {
                DiffFormatter formatter = diffInterface.getFormatter();
                formatter.setRepository(repo);
                List<DiffEntry> entries = formatter.scan(oldTreeIter, newTreeIter);
                if (!entries.isEmpty()) {
                    diffInterface.getEntryMenu().setItems(FXCollections.observableList(entries));
                    diffInterface.getEntryMenu().setValue(entries.get(0));
                    diffInterface.viewEntry(entries.get(0));
                }
            }
        } catch (IOException | RevisionSyntaxException ex) {
            log.error("Diff", ex);
        }
    }

    /**
     * Checkout to the given branch.
     *
     * @param name String. The name of the branch to check out to.
     */
    public void gitCheckout(String name) {
        try {
            if (git == null) {
                gitOpen();
            }
            Path oldProjectDir = projectDir;
            String projectName = guiProject.getName();
            guiProject.close();
            git.checkout().setName(name).call();
            GUIProject project = new GUIProject(projectName);
            guiProject.loadGUIProject(oldProjectDir);
        } catch (GitAPIException | IOException | MoleculeIOException ex) {
            log.error("Checkout", ex);
            String message = ex.getMessage();
            if (message.contains("Checkout conflict with files:")) {
                resolveFileConflicts(name, message);
            }
        }
    }



    /**
     * Merge the original branch at the given commit into the destination branch.
     *
     * @param mergeDestBranch String. The name of the branch to merge into.
     * @param commitToMerge   String. The commitID to merge. All commits up to and including commitID will be merged.
     * @param origBranch      String. The name of the original branch.
     * @param idx             int. The index of the commit to merge.
     */
    public void gitMerge(String mergeDestBranch, RevCommit commitToMerge, String origBranch, int idx) {
        try {
            String message = "Merge the " + origBranch + " branch into the " + mergeDestBranch + " branch?\n";
            message += "All commits in the " + origBranch + " branch up to and including commit " + idx + " will be merged into " + mergeDestBranch + ".";
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
            Optional<ButtonType> response = alert.showAndWait();
            if (response.isPresent() && (response.get() == ButtonType.OK)) {
                gitCheckout(mergeDestBranch);
                if (gitCurrentBranch().equals(mergeDestBranch)) {
                    git.merge().include(commitToMerge).call();
                    gitCommit("Merged branch " + origBranch + " into " + mergeDestBranch);
                }
            }
        } catch (GitAPIException ex) {
            log.error("Merge", ex);
        }
    }

}
