package org.nmrfx.utilities;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

/**
 *
 * @author brucejohnson
 */
public class RemoteDatasetAccess {

    public static String USERNAME = "bjohnson";
    public static String REMOTE_HOST = "cobalt.nmrbox.org";
    public static int REMOTE_PORT = 22;
    public static int SESSION_TIMEOUT = 10000;
    public static int CHANNEL_TIMEOUT = 5000;
    String remoteFile = "/public/groups/comd-nmr/share/scripts/test.json";

    public boolean fetchIndex(File localFile) {
        String userdir = System.getProperty("user.home");
        FileSystem fileSystem = FileSystems.getDefault();
        String knownHostsFile = fileSystem.getPath(userdir, ".ssh", "known_hosts").toString();
        String identityFile = fileSystem.getPath(userdir, ".ssh", "id_rsa").toString();

        Session jschSession = null;
        try {

            JSch jsch = new JSch();
            jsch.setKnownHosts(knownHostsFile);
            jsch.addIdentity(identityFile);
            jschSession = jsch.getSession(USERNAME, REMOTE_HOST, REMOTE_PORT);
            jschSession.connect(SESSION_TIMEOUT);
            Channel sftp = jschSession.openChannel("sftp");
            sftp.connect(CHANNEL_TIMEOUT);
            ChannelSftp channelSftp = (ChannelSftp) sftp;
            channelSftp.get(remoteFile, localFile.getAbsolutePath().toString());
            channelSftp.exit();
        } catch (JSchException | SftpException e) {
            return false;
        } finally {
            if (jschSession != null) {
                jschSession.disconnect();
            }
        }
        return true;

    }

    public void parseIndex() {

    }
}
