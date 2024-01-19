package org.nmrfx.processor.gui.utils;

import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utilities.Updater;

import java.util.EventObject;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
public class AtomUpdater implements Updater {
    private static final AtomicBoolean atomUpdated = new AtomicBoolean(false);
    private static final AtomicBoolean needToFireEvent = new AtomicBoolean(false);

    protected ScheduledThreadPoolExecutor schedExecutor = new ScheduledThreadPoolExecutor(2);
    ScheduledFuture futureUpdate = null;
    static Molecule molecule;

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
        atomUpdated.set(true);
        startTimer();
        System.out.println("timer started, atom updated: " + atomUpdated);
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

    static void checkForUpdates(){
        if (molecule != null) {
            if (molecule.atomUpdated.get()) {
                System.out.println("notifyAtomChangeListener in AtomUpdater");
                molecule.atomUpdated.set(false);
                molecule.notifyAtomChangeListener();
            }
        }
    }


}
