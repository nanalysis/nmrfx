package org.nmrfx.utilities;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.xfer.FileSystemFile;

import java.io.File;
import java.io.IOException;

/**
 * @author brucejohnson
 */
public class RemoteDatasetAccess {
    private static final int CHANNEL_TIMEOUT = 5000;

    public final String userName;
    public final String remoteHost;
    SSHClient ssh = null;
    String password = null;
    boolean passwordValid = false;

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

    public boolean fetchFile(File remoteFile, File localFile) throws IOException {
        try {
            getSession();
            File directory = localFile.getAbsoluteFile().getParentFile();
            boolean directoryExists = directory.exists();
            if (!directoryExists) {
                directoryExists = directory.mkdirs();
            }
            if (directoryExists) {
                ssh.newSCPFileTransfer().download(remoteFile.toString(), new FileSystemFile(localFile.getAbsolutePath()));
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

}
