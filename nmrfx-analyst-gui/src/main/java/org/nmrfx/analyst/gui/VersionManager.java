package org.nmrfx.analyst.gui;

import dev.hydraulic.conveyor.control.SoftwareUpdateController;
import javafx.concurrent.Task;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utils.GUIUtils;

import java.util.Optional;

public class VersionManager {

    public static void checkVersionWithConveyor(String lastChecked) {
        SoftwareUpdateController controller = SoftwareUpdateController.getInstance();
        if (controller == null) {
            String currentVersion = AnalystApp.getVersion();
            GUIUtils.warn("Version Check", "Current version: " + currentVersion
                    + "\nCan't get software update controller\nso can't check for latest");
            return;
        }

        Task<Optional<SoftwareVersions>> task = new Task<>() {
            @Override
            protected Optional<SoftwareVersions> call() {
                return checkVersionWithConveyor(controller);
            }
        };

        task.setOnSucceeded(e -> {
            Optional<SoftwareVersions> newVersionOpt = task.getValue();
            newVersionOpt.ifPresent(version -> {
                AnalystPrefs.setLastVersion(version.latest.getVersion());
                if (version.isNewer() && (lastChecked.isEmpty() || !lastChecked.equals(version.latest.getVersion()))) {
                    if (GUIUtils.affirm("Update to version " + version.latest.getVersion())) {
                        if (!ProjectBase.getActive().projectChanged() || GUIUtils.affirm("Project changed, really quite and update?")) {
                            controller.triggerUpdateCheckUI();
                        }
                    }
                } else {
                    if (lastChecked.isEmpty()) {
                        GUIUtils.acknowledge("You're running current version " + version.current.getVersion());
                    }
                }
            });
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            ExceptionDialog exceptionDialog = new ExceptionDialog(ex);
            exceptionDialog.showAndWait();
        });

        Thread thread = new Thread(task, "version-check");
        thread.setDaemon(true);
        thread.start();
    }

    record SoftwareVersions(SoftwareUpdateController.Version current, SoftwareUpdateController.Version latest) {
        boolean isNewer() {
            return latest != null && latest.compareTo(current) > 0;
        }
    }

    private static Optional<SoftwareVersions> checkVersionWithConveyor(SoftwareUpdateController controller) {
        SoftwareUpdateController.Version currentVersion = controller.getCurrentVersion();
        if (currentVersion == null) {
            return Optional.empty();
        }

        try {
            SoftwareUpdateController.Version latestVersion = controller.getCurrentVersionFromRepository();
            SoftwareVersions softwareVersions = new SoftwareVersions(currentVersion, latestVersion);
            return Optional.of(softwareVersions);

        } catch (SoftwareUpdateController.UpdateCheckException e) {
            return Optional.empty();
        }
    }

}
