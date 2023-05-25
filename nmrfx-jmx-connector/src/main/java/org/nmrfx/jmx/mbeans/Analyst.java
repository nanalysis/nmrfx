/*
 * NMRFx: A Program for Processing NMR Data
 * Copyright (C) 2004-2022 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.jmx.mbeans;

import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.jmx.NotificationType;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.events.DatasetSavedEvent;
import org.nmrfx.processor.gui.ChartProcessor;

import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import java.io.File;

/**
 * The main entrypoint to control NMRFx Analyst Gui by JMX.
 */
public class Analyst extends NotificationBroadcasterSupport implements AnalystMBean {
    private int notificationSequenceNumber = 0;

    public Analyst() {
        EventBus.getDefault().register(this);
    }

    @Override
    public void open(String path) {
        DatasetType preferredProcessedFormat = DatasetType.typeFromFile(new File(path)).orElse(DatasetType.NMRFX);

        Fx.runOnFxThread(() -> {
            AnalystApp.getFXMLControllerManager().getOrCreateActiveController().openFile(path, false, true, preferredProcessedFormat);
            sendNotification(NotificationType.MESSAGE, "File opened", path);
        });
    }

    @Override
    public void setWindowOnFront() {
        Fx.runOnFxThread(() -> {
            Stage stage = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getStage();
            stage.toFront();
            stage.requestFocus();
        });
    }

    @Override
    public void generateAutoScript(boolean isPseudo2D) {
        // getting the chart processor must be done in fx thread, because otherwise, a client calling
        // open() then generateAutoScript() could try to access the chart processor before its creation.
        Fx.runOnFxThread(() -> {
            ChartProcessor chartProcessor = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getChartProcessor();
            String script = chartProcessor.getGenScript(isPseudo2D);
            chartProcessor.getProcessorController().parseScript(script);
        });
    }

    @Subscribe
    public void onDatasetSavedEvent(DatasetSavedEvent event) {
        sendNotification(NotificationType.DATASET_SAVED, event.getType().name(), event.getPath().toAbsolutePath().toString());
    }

    protected void sendNotification(NotificationType type, String message, Object userData) {
        var notification = new Notification(type.name(), this, notificationSequenceNumber++, System.currentTimeMillis(), message);
        notification.setUserData(userData);
        sendNotification(notification);
    }
}
