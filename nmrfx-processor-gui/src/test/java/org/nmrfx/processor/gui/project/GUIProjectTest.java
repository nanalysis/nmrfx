package org.nmrfx.processor.gui.project;

import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;

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
    public void testCreateProjectInvalidFolder() throws IOException {
        Mockito.doReturn(null).when(testProject).getEnvironmentVariable("HOME");
        Mockito.doReturn(tmpFolder.getRoot().toPath().resolve("non-existent-folder").toString()).when(testProject).getEnvironmentVariable("HOME");
        Mockito.doReturn(tmpFolder.getRoot().toPath().resolve("non-existent-folder").toString()).when(testProject).getEnvironmentVariable("HOME");
        testProject.createProject(tmpFolder.getRoot().toPath().resolve("new_proj_dir_invalid"));
        assertEquals(System.getProperty("user.home"), FS.DETECTED.userHome().toString());
    }

    @Test
    public void testCreateProjectValidFolder() throws IOException {
        Mockito.doReturn(null).when(testProject).getEnvironmentVariable("HOME");
        Mockito.doReturn(tmpFolder.getRoot().toPath().toString()).when(testProject).getEnvironmentVariable("HOMEDRIVE");
        String initialUserHome = FS.DETECTED.userHome().toString();
        testProject.createProject(tmpFolder.getRoot().toPath().resolve("new_proj_dir_valid"));
        assertEquals(initialUserHome, FS.DETECTED.userHome().toString());
    }
}