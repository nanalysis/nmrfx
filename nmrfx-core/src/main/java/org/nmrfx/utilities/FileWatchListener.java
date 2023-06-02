package org.nmrfx.utilities;

import java.io.File;

/**
 * @author brucejohnson
 */
public interface FileWatchListener {

    public void onCreated(File file);

    public void onModified(File file);

    public void onDeleted(File file);

}
