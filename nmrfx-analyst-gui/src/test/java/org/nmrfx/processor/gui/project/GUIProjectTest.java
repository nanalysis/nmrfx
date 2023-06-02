package org.nmrfx.processor.gui.project;

import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;


public class GUIProjectTest {
    GUIProject testProject = Mockito.spy(new GUIProject("A Test Project"));
    @ClassRule
    public static final TemporaryFolder tmpFolder = TemporaryFolder.builder()
            .parentFolder(new File(System.getProperty("user.dir")))
            .assureDeletion()
            .build();

    @Before
    public void setUp() {
        Mockito.doReturn(null).when(testProject).createAndInitializeGitObject(any());
    }

    @Test
    public void testCreateProjectInvalidConfigFolder() throws IOException {
        Mockito.doReturn(null).when(testProject).getEnvironmentVariable("XDG_CONFIG_HOME");
        Mockito.doReturn(tmpFolder.getRoot().toPath().resolve("non-existent-folder").toString()).when(testProject).getEnvironmentVariable("HOME");
        FS.DETECTED.setUserHome(tmpFolder.getRoot().toPath().resolve("non-existent-folder").toFile());
        testProject.createProject(tmpFolder.getRoot().toPath().resolve("new_proj_dir_invalid"));
        assertEquals(System.getProperty("user.home"), FS.DETECTED.userHome().toString());
    }

    @Test
    public void testCreateProjectValidConfigFolder() throws IOException {
        Mockito.doReturn(null).when(testProject).getEnvironmentVariable("XDG_CONFIG_HOME");
        Mockito.doReturn(null).when(testProject).getEnvironmentVariable("HOME");
        Mockito.doReturn(tmpFolder.getRoot().toPath().toString()).when(testProject).getEnvironmentVariable("HOMEDRIVE");
        Mockito.doReturn("valid_config_directory").when(testProject).getEnvironmentVariable("HOMEPATH");
        Files.createDirectories(tmpFolder.getRoot().toPath().resolve("valid_config_directory"));
        // set the user home and make sure it hasn't been overwritten in the assertion
        String path = "\\path\\to\\not\\be\\overwritten";
        FS.DETECTED.setUserHome(new File(path));
        testProject.createProject(tmpFolder.getRoot().toPath().resolve("new_proj_dir_valid"));
        assertEquals(path, FS.DETECTED.userHome().toString());
    }
}
