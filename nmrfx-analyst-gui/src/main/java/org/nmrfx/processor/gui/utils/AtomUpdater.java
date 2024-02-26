package org.nmrfx.processor.gui.utils;

import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utilities.Updater;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AtomUpdater implements Updater {
    private static final AtomicBoolean atomUpdated = new AtomicBoolean(false);
    private static final AtomicBoolean needToFireEvent = new AtomicBoolean(false);

    protected ScheduledThreadPoolExecutor schedExecutor = new ScheduledThreadPoolExecutor(2);
    ScheduledFuture futureUpdate = null;
    Molecule molecule;

    public AtomUpdater(Molecule molecule) {
        this.molecule = molecule;
    }

    @Override
    public void shutdown() {
        schedExecutor.shutdown();
        schedExecutor = null;
        molecule = null;
    }

    @Override
    public void update(Object object) {
        molecule.atomUpdated.set(true);
        molecule.atomTableUpdated.set(true);
        atomUpdated.set(true);
        startTimer();
    }

    class UpdateTask implements Runnable {

        @Override
        public void run() {
            if (atomUpdated.get()) {
                needToFireEvent.set(true);
                atomUpdated.set(false);
                startTimer();
            } else if (needToFireEvent.get()) {
                needToFireEvent.set(false);
                checkForUpdates();
                if (atomUpdated.get()) {
                    startTimer();
                }
            }
        }
    }

    synchronized void startTimer() {
        if (molecule != null && (schedExecutor != null)) {
            if (needToFireEvent.get() || (futureUpdate == null) || futureUpdate.isDone()) {
                AtomUpdater.UpdateTask updateTask = new AtomUpdater.UpdateTask();
                futureUpdate = schedExecutor.schedule(updateTask, 50, TimeUnit.MILLISECONDS);
            }
        }

    }

    void checkForUpdates() {
        if (molecule != null) {
            if (molecule.atomUpdated.get()) {
                molecule.atomUpdated.set(false);
                molecule.notifyAtomChangeListener();
            }
            if (molecule.atomTableUpdated.get()) {
                molecule.atomTableUpdated.set(false);
                molecule.notifyAtomTableListener();
            }
        }
    }


}
