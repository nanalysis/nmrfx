package org.nmrfx.utilities;

import com.jcraft.jsch.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author brucejohnson
 */
public class RemoteDatasetAccess {

    public String userName = "";
    public String remoteHost = "";
    public static int REMOTE_PORT = 22;
    public static int SESSION_TIMEOUT = 10000;
    public static int CHANNEL_TIMEOUT = 5000;
    JSch jsch = new JSch();
    String userdir = System.getProperty("user.home");
    FileSystem fileSystem = FileSystems.getDefault();
    Session jschSession = null;
    ChannelSftp sftp = null;
    String password = null;
    boolean passwordValid = false;
    Optional<Boolean> useIdentity = Optional.empty();

    public RemoteDatasetAccess(String userName, String remoteHost) {
        this.userName = userName;
        this.remoteHost = remoteHost;
    }

    public void connect() throws JSchException {
        getSession();
    }

    public void setPassword(String value) {
        this.password = value;
    }

    public boolean passwordValid() {
        return passwordValid;
    }

    public boolean useIdentity() {
        if (useIdentity.isPresent()) {
            return useIdentity.get();
        }
        File knownHostsFile = fileSystem.getPath(userdir, ".ssh", "known_hosts").toFile();
        File identityFile = fileSystem.getPath(userdir, ".ssh", "id_rsa").toFile();
        boolean ok = false;

        if (knownHostsFile.exists() && identityFile.exists()) {
            try (Stream<String> lines = Files.lines(identityFile.toPath())) {
                ok = lines.anyMatch(line -> line.startsWith(remoteHost));
            } catch (IOException ex) {
                ok = false;
            }
        }
        useIdentity = Optional.of(ok);
        return useIdentity.get();
    }

    Session getSession() throws JSchException {
        if ((jschSession == null) || !jschSession.isConnected()) {
            System.out.println("create session");
            File knownHostsFile = fileSystem.getPath(userdir, ".ssh", "known_hosts").toFile();
            File identityFile = fileSystem.getPath(userdir, ".ssh", "id_rsa").toFile();
            System.out.println("set known hosts");
            jsch.setKnownHosts(knownHostsFile.toString());

            if ((password == null) || password.isEmpty()) {
                System.out.println("set identity");
                jsch.addIdentity(identityFile.toString());

            }

            System.out.println("get session now");
            jschSession = jsch.getSession(userName, remoteHost, REMOTE_PORT);
            System.out.println("got session");

            if ((password != null) && !password.isEmpty()) {
                System.out.println("set password");
                jschSession.setPassword(password);
                passwordValid = false;
            }

            jschSession.connect(SESSION_TIMEOUT);
            if ((password != null) && !password.isEmpty()) {
                passwordValid = jschSession.isConnected();
            }
        }
        return jschSession;
    }

    ChannelSftp getSftp() throws JSchException {
        Session session = getSession();
        if ((sftp == null) || sftp.isClosed() || !sftp.isConnected()) {
            System.out.println("create channel");
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CHANNEL_TIMEOUT);
        }
        return sftp;
    }

    public boolean fetchFile(String remoteFile, File localFile) {
        try {
            ChannelSftp sftpChannel = getSftp();
            sftpChannel.get(remoteFile, localFile.getAbsolutePath().toString());
            sftpChannel.exit();
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
