package org.nmrfx.utilities;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.xfer.FileSystemFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * @author brucejohnson
 */
public class RemoteDatasetAccess {
    private static final Logger log = LoggerFactory.getLogger(RemoteDatasetAccess.class);
    private static final int CHANNEL_TIMEOUT = 5000;
    public final String userName;
    public final String remoteHost;
    private SSHClient ssh = null;
    private String password = null;
    private boolean passwordValid = false;

    public RemoteDatasetAccess(String userName, String remoteHost) {
        this.userName = userName;
        this.remoteHost = remoteHost;
    }

    public void connect() throws IOException {
        getSession();
    }

    public void setPassword(String value) {
        this.password = value;
    }

    public boolean passwordValid() {
        return passwordValid;
    }

    void getSession() throws  IOException {
        if ((ssh == null) || !ssh.isConnected() || !ssh.isAuthenticated()) {
            ssh = new SSHClient();
            ssh.loadKnownHosts();
            ssh.setConnectTimeout(CHANNEL_TIMEOUT);
            ssh.setTimeout(CHANNEL_TIMEOUT);
            ssh.connect(remoteHost);
            if (password != null) {
                ssh.authPassword(userName, password);
            } else {
                ssh.authPublickey(userName);
            }
        }
        passwordValid = (password != null) && ssh.isAuthenticated();
    }

    public boolean fetchFile(String remoteFile, File localFile) {
        try {
            getSession();
            File directory = localFile.getAbsoluteFile().getParentFile();
            boolean directoryExists = directory.exists();
            if (!directoryExists) {
                directoryExists = directory.mkdirs();
            }
            if (directoryExists) {
                ssh.newSCPFileTransfer().download(remoteFile, new FileSystemFile(localFile.getAbsolutePath()));
            }
        } catch (IOException e) {
            try {
                // Try retrieving the file with a different style of file separators
                String formattedFilename;
                if (SystemUtils.IS_OS_WINDOWS) {
                    formattedFilename = FilenameUtils.separatorsToUnix(remoteFile);
                } else {
                    formattedFilename = FilenameUtils.separatorsToWindows(remoteFile);
                }
                ssh.newSCPFileTransfer().download(formattedFilename, new FileSystemFile(localFile.getAbsolutePath()));
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
                return false;
            }
        }
        return true;
    }

    public boolean isConnected() {
        return ssh != null && ssh.isConnected();
    }

}
