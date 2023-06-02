package org.nmrfx.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * @author brucejohnson
 */
public class NMRFxFileWatcher implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(NMRFxFileWatcher.class);
    protected static final Map<String, WatchService> watchServices = new HashMap<>();
    protected List<FileWatchListener> listeners = new ArrayList<>();
    File watchDir;

    public NMRFxFileWatcher(File dir) {
        this.watchDir = dir;
    }

    public void monitor() {
        if (watchDir.exists()) {
            Path path = Paths.get(watchDir.getAbsolutePath());
            if (!watchServices.containsKey(path.toString())) {
                Thread thread = new Thread(this);
                thread.setDaemon(true);
                thread.start();
            }
        }
    }

    public NMRFxFileWatcher addListener(FileWatchListener listener) {
        listeners.add(listener);
        return this;

    }

    @Override
    public void run() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Path path = Paths.get(watchDir.getAbsolutePath());
            path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

            watchServices.put(path.toString(), watchService);

            while (pollEvents(watchService)) {
            }

        } catch (IOException | InterruptedException | ClosedWatchServiceException e) {
            Thread.currentThread().interrupt();
        }

    }

    protected boolean pollEvents(WatchService watchService) throws InterruptedException {
        WatchKey key = watchService.take();
        Path path = (Path) key.watchable();

        for (WatchEvent<?> event : key.pollEvents()) {
            notifyListeners(event.kind(), path.resolve((Path) event.context()).toFile());
        }

        return key.reset();

    }

    private void notifyListeners(WatchEvent.Kind<?> kind, File file) {
        if (kind == ENTRY_CREATE) {
            for (FileWatchListener listener : listeners) {
                listener.onCreated(file);
            }
        } else if (kind == ENTRY_DELETE) {
            for (FileWatchListener listener : listeners) {
                listener.onDeleted(file);
            }
        } else if (kind == ENTRY_MODIFY) {
            for (FileWatchListener listener : listeners) {
                listener.onModified(file);
            }
        }
    }

    public static WatchService getWatcher(String pathString) {
        return watchServices.get(pathString);
    }

    public static boolean remove(String pathString) {
        WatchService service = watchServices.remove(pathString);
        if (service != null) {
            try {
                service.close();
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        return service != null;
    }
}
