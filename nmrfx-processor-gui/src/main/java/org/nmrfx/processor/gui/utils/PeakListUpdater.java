/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.gui.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakEvent;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.PeakListener;
import org.nmrfx.utilities.Updater;

/**
 *
 * @author brucejohnson
 */
public class PeakListUpdater implements Updater {

    static List<PeakListener> globalListeners = new ArrayList<>();

    protected ScheduledThreadPoolExecutor schedExecutor = new ScheduledThreadPoolExecutor(2);
    PeakList peakList;
    static AtomicBoolean aListUpdated = new AtomicBoolean(false);
    static AtomicBoolean needToFireEvent = new AtomicBoolean(false);
    ScheduledFuture futureUpdate = null;

    public PeakListUpdater(PeakList peakList) {
        this.peakList = peakList;
    }

    @Override
    public void shutdown() {
        schedExecutor.shutdown();
        schedExecutor = null;
        peakList = null;
    }

    @Override
    public void update(Object object) {
        if (object instanceof Peak) {
            setPeakUpdatedFlag(true);
        } else if (object instanceof PeakList) {
            setPeakListUpdatedFlag(true);
        } else if (object instanceof List) {
            setPeakCountUpdatedFlag(true);
        }
        startTimer();
    }

    class UpdateTask implements Runnable {

        @Override
        public void run() {
            if (aListUpdated.get()) {
                needToFireEvent.set(true);
                aListUpdated.set(false);
                startTimer();
            } else if (needToFireEvent.get()) {
                needToFireEvent.set(false);
                scanListsForUpdates();
                if (aListUpdated.get()) {
                    startTimer();
                }
            }
        }
    }

    synchronized void startTimer() {
        if (peakList.valid() && (schedExecutor != null)) {
            if (needToFireEvent.get() || (futureUpdate == null) || futureUpdate.isDone()) {
                UpdateTask updateTask = new UpdateTask();
                futureUpdate = schedExecutor.schedule(updateTask, 50, TimeUnit.MILLISECONDS);
            }
        }

    }

    static void scanListsForUpdates() {
        boolean anyUpdated = false;
        Iterator iter = PeakList.iterator();
        while (iter.hasNext()) {
            PeakList peakList = (PeakList) iter.next();
            if (peakList != null) {
                if (peakList.peakUpdated.get()) {
                    peakList.peakUpdated.set(false);
                    // fixme should only do if necessary
                    //peakList.sortMultiplets();
                    peakList.notifyPeakChangeListeners();
                    anyUpdated = true;
                }
                if (peakList.peakListUpdated.get()) {
                    peakList.peakListUpdated.set(false);
                    peakList.notifyPeakListChangeListeners();
                    anyUpdated = true;
                }
                if (peakList.peakCountUpdated.get()) {
                    peakList.peakCountUpdated.set(false);
                    peakList.notifyPeakCountChangeListeners();
                    anyUpdated = true;
                }
            }
        }
        if (anyUpdated) {
            notifyGlobalListeners();
        }
    }

    void setPeakUpdatedFlag(boolean value) {
        peakList.peakUpdated.set(value);
        if (value) {
            aListUpdated.set(value);
        }
    }
    void setPeakListUpdatedFlag(boolean value) {
        peakList.peakListUpdated.set(value);
        if (value) {
            aListUpdated.set(value);
        }
    }
    void setPeakCountUpdatedFlag(boolean value) {
        peakList.peakCountUpdated.set(value);
        if (value) {
            aListUpdated.set(value);
        }
    }

    static void registerGlobalListener(PeakListener newListener) {
        if (!globalListeners.contains(newListener)) {
            globalListeners.add(newListener);
        }
    }

    public static void notifyGlobalListeners() {
        for (PeakListener listener : globalListeners) {
            listener.peakListChanged(new PeakEvent("*"));
        }
    }

}
