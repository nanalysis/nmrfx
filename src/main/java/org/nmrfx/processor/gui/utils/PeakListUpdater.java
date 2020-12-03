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
    static boolean aListUpdated = false;
    static boolean needToFireEvent = false;
    ScheduledFuture futureUpdate = null;

    public void shutdown() {
        schedExecutor.shutdown();
        schedExecutor = null;
        peakList = null;
    }

    public void update() {
        setUpdatedFlag(true);
        startTimer();
    }

    class UpdateTask implements Runnable {

        @Override
        public void run() {
            if (aListUpdated) {
                needToFireEvent = true;
                setAUpdatedFlag(false);
                startTimer();
            } else if (needToFireEvent) {
                needToFireEvent = false;
                scanListsForUpdates();
                if (aListUpdated) {
                    startTimer();
                }
            }
        }
    }

    synchronized void startTimer() {
        if (peakList.valid() && (schedExecutor != null)) {
            if (needToFireEvent || (futureUpdate == null) || futureUpdate.isDone()) {
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
            if ((peakList != null) && (peakList.thisListUpdated)) {
                peakList.setUpdatedFlag(false);
                // fixme should only do if necessary
                //peakList.sortMultiplets();
                peakList.notifyListeners();
                anyUpdated = true;
            }
        }
        if (anyUpdated) {
            notifyGlobalListeners();
        }
    }
    // FIXME need to make safe

    synchronized static void setAUpdatedFlag(boolean value) {
        aListUpdated = value;
    }

    synchronized void setUpdatedFlag(boolean value) {
        peakList.thisListUpdated = value;
        if (value) {
            setAUpdatedFlag(value);
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
