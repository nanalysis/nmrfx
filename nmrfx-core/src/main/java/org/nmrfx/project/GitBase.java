package org.nmrfx.project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitBase {
    private static final Logger log = LoggerFactory.getLogger(GitBase.class);
    ProjectBase project;
    Path projectDir;
    Git git;

    public GitManager(ProjectBase project) throws IllegalArgumentException {
        projectDir = project.getProjectDir();
        try {
            git = Git.open(projectDir.toFile());
        } catch (IOException ioE) {
            //project.checkUserHomePath();
            git = createAndInitializeGitObject(projectDir.toFile());
            if (git == null) {
                throw new IllegalArgumentException("Can't create git");
            }
            project.writeIgnore();
        }
    }

    public Git createAndInitializeGitObject(File gitDirectory) {
        try {
            git = Git.init().setDirectory(gitDirectory).call();
            return git;
        } catch (GitAPIException ex) {
            log.error(ex.getMessage(), ex);
        }
        return null;
    }

    public boolean gitCommit(String msg) {
        boolean didSomething = false;
        commitActive = true;
        if (git == null) {
            if (!gitOpen()) {
                return false;
            }
        }
        try {

            DirCache index = git.add().addFilepattern(".").call();
            Status status = git.status().call();
            StringBuilder sBuilder = new StringBuilder();
            Set<String> actionMap = new HashSet<>();
            if (!status.isClean() || status.hasUncommittedChanges()) {
                Set<String> addedFiles = status.getAdded();
                for (String addedFile : addedFiles) {
                    String action = "add:" + Paths.get(addedFile).getName(0);
                    actionMap.add(action);
                }
                Set<String> changedFiles = status.getChanged();
                for (String changedFile : changedFiles) {
                    String action = "change:" + Paths.get(changedFile).getName(0);
                    actionMap.add(action);
                }
                Set<String> removedFiles = status.getRemoved();
                for (String removedFile : removedFiles) {
                    String action = "remove:" + Paths.get(removedFile).getName(0);
                    actionMap.add(action);
                    git.rm().addFilepattern(removedFile).call();
                }
                Set<String> missingFiles = status.getMissing();
                for (String missingFile : missingFiles) {
                    String action = "missing:" + Paths.get(missingFile).getName(0);
                    actionMap.add(action);
                    git.rm().addFilepattern(missingFile).call();
                }
                actionMap.forEach(action -> sBuilder.append(action).append(","));
                git.commit().setMessage(msg + " " + sBuilder).call();
                didSomething = true;

            }
        } catch (GitAPIException ex) {
            log.error(ex.getMessage(), ex);
        }
        return didSomething;
    }
}
