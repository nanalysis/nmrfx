package org.nmrfx.utilities;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author brucejohnson
 */
public class NMRFxFileWatcher implements Runnable {

    File watchDir;
    protected static final Map<String, WatchService> watchServices = new HashMap<>();
    protected List<FileWatchListener> listeners = new ArrayList<>();

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

    public static WatchService getWatcher(String pathString) {
        return watchServices.get(pathString);
    }

    public NMRFxFileWatcher addListener(FileWatchListener listener) {
        listeners.add(listener);
        return this;

    }

    public static boolean remove(String pathString) {
        WatchService service = watchServices.remove(pathString);
        if (service != null) {
            try {
                service.close();
            } catch (IOException ex) {
            }
        }
        return service != null;
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
}
