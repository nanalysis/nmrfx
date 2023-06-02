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
package org.nmrfx.jmx;

import org.nmrfx.jmx.mbeans.Analyst;
import org.nmrfx.jmx.mbeans.AnalystMBean;
import org.nmrfx.jmx.mbeans.Console;
import org.nmrfx.jmx.mbeans.ConsoleMBean;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.util.Map;
import java.util.Set;

/**
 * A plugin to enable JMX server with NMRfx specific MBeans.
 */
public class JmxConnectorPlugin implements NMRFxPlugin {

    private static final Logger log = LoggerFactory.getLogger(JmxConnectorPlugin.class);

    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {
        return Set.of(EntryPoint.STARTUP);
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        try {
            int port = findFreePort();
            JMXServiceURL url = startJmxServer(port, Map.of(
                    new ObjectName(AnalystMBean.NAME), new Analyst(),
                    new ObjectName(ConsoleMBean.NAME), new Console()));
            writeToFile(url);
            System.out.println("Running JMX on port " + port);
        } catch (JMException | IOException e) {
            log.warn("Unable to setup JMX connector! {}", e.getMessage(), e);
        }
    }

    private int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private void writeToFile(JMXServiceURL url) throws IOException {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String jmxFileName = "NMRFx_" + System.getProperty("user.name") + "_jmx.txt";
        Files.writeString(Path.of(tmpDir, jmxFileName), url.toString());
    }

    private JMXServiceURL startJmxServer(int port, Map<ObjectName, Object> mbeans) throws IOException, JMException {
        LocateRegistry.createRegistry(port);
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        for (Map.Entry<ObjectName, Object> entry : mbeans.entrySet()) {
            mbs.registerMBean(entry.getValue(), entry.getKey());
        }

        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://localhost/jndi/rmi://localhost:" + port + "/jmxrmi");
        JMXConnectorServer svr = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
        svr.start();

        return url;
    }
}
