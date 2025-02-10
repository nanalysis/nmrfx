package org.nmrfx.project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitBase {
    private static final Logger log = LoggerFactory.getLogger(GitBase.class);
    protected static boolean commitActive = false;
    protected ProjectBase project;
    protected Path projectDir;
    protected Git git;

    public GitBase(ProjectBase project) throws IllegalArgumentException {
        projectDir = project.getProjectDir();
        try {
            git = Git.open(projectDir.toFile());
        } catch (IOException ioE) {
            ProjectBase.checkUserHomePath();
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

    public boolean gitOpen() {
        if (git == null) {
            try {
                git = Git.open(projectDir.toFile());
            } catch (IOException ioE) {
                ProjectBase.checkUserHomePath();
                git = createAndInitializeGitObject(projectDir.toFile());
                if (git == null) {
                    return false;
                }
                project.writeIgnore();
            }
        }
        return true;
    }

    public void setProject(ProjectBase project) {
        this.project = project;
        Path currentDir = projectDir;
        this.projectDir = project.getProjectDir();
        if ((git == null) || (currentDir == null) || !currentDir.equals(this.projectDir)) {
            if (git != null) {
                git.close();
            }
            git = null;
            gitOpen();
        }
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

    /**
     * Get the current git status.
     *
     * @return Status. The git status.
     */
    public Status gitStatus() {
        Status status = null;
        try {
            if (git == null) {
                gitOpen();
            }
            status = git.status().call();
        } catch (GitAPIException ex) {
            log.error("Error getting status", ex);
        }
        return status;
    }

    /**
     * Get the git log, a list of commits, for the given branch.
     *
     * @param branchName String. The name of the branch to get the log of.
     * @return List<RevCommit>. The git log: a list of commits for the branch.
     */
    public List<RevCommit> gitLog(String branchName) {
        List<RevCommit> gitLog = new ArrayList<>();
        try {
            if (git == null) {
                gitOpen();
            }
            ObjectId branch = git.getRepository().resolve(branchName);
            if (branch != null) {
                Iterable<RevCommit> iterable = git.log().add(branch).call();
                iterable.forEach(gitLog::add);
            }
        } catch (IOException | GitAPIException | RevisionSyntaxException ex) {
            log.error("Error getting log", ex);
        }
        return gitLog;
    }
    /**
     * List the branches in the current git repository.
     *
     * @return List<Ref>. List of the git branches.
     */
    public List<Ref> gitBranches() {
        List<Ref> branchList = new ArrayList<>();
        try {
            if (git == null) {
                gitOpen();
            } else {
                branchList = git.branchList().call();
            }
        } catch (GitAPIException ex) {
            log.error("Error getting branches", ex);
        }
        return branchList;
    }

    /**
     * Get the full name (filepath) of the current branch.
     *
     * @return String. Full name of the current branch.
     */
    public String gitCurrentBranch() {
        String branch = "";
        try {
            if (git == null) {
                gitOpen();
            } else {
                branch = git.getRepository().getBranch();
            }
        } catch (IOException ex) {
            log.error("Error getting current branch", ex);
        }
        return branch;
    }

    /**
     * Get the short name of the given branch, not the full filepath.
     *
     * @param branch String. The full name (filepath) of the branch.
     * @return String. The branch name.
     */
    public String gitShortBranch(String branch) {
        String[] branchSplit = branch.split(File.separator);
        return branchSplit[branchSplit.length - 1];
    }

    private ObjectLoader gitFindFile(String branch, String fileName) throws IOException {
        ObjectLoader fileObject = null;
        Repository repository = git.getRepository();
        ObjectId treeId = repository.resolve(String.join(File.separator, "refs", "heads", branch + "^{tree}"));
        TreeWalk treeWalk = TreeWalk.forPath(repository, fileName, treeId);
        if (treeWalk != null) {
            ObjectId blobID = treeWalk.getObjectId(0);
            fileObject = repository.open(blobID);
        }
        return fileObject;
    }
}
