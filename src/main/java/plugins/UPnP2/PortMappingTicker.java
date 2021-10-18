/*
 * This file is part of UPnP2, a plugin for Freenet.
 *
 * UPnP2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * UPnP2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with UPnP2.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugins.UPnP2;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import freenet.pluginmanager.ForwardPort;
import freenet.pluginmanager.ForwardPortCallback;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.Ticker;

/**
 * Created by xiaoyu on 1/18/16. 2
 */
public class PortMappingTicker {

    private static volatile boolean logMINOR;

    static {
        Logger.registerLogThresholdCallback(new LogThresholdCallback() {
            @Override
            public void shouldUpdate() {
                logMINOR = Logger.shouldLog(Logger.LogLevel.MINOR, this);
            }
        });
    }

    private ServiceManager serviceManager;
    private Ticker ticker;
    private Set<ForwardPort> ports;
    private ForwardPortCallback cb;
    private Runnable portMappingRunnable = new Runnable() {
        @Override
        public void run() {
            serviceManager.addPortMappings(ports, cb);
        }
    };
    private boolean started = false;

    PortMappingTicker(ServiceManager serviceManager, Ticker ticker) {
        this.serviceManager = serviceManager;
        this.ticker = ticker;
    }

    public void startPortMapping(Set<ForwardPort> ports, ForwardPortCallback cb) {

        serviceManager.waitForBooting();

        synchronized (this) {
            if (started) {
                if (ports.equals(this.ports) && cb.equals(this.cb)) {
                    // Nothing changes. Just return.
                    return;
                }

                // Something changed. Remove old port mappings
                serviceManager.removeAllPortMappings();
            }

            started = true;

            this.ports = ports;
            this.cb = cb;
        }

        realDoPortMapping();

        Logger.normal(this, "Port Mapping ticker started.");

    }

    public void stopPortMapping() {
        if (!started) return;

        ticker.removeQueuedJob(portMappingRunnable);

        Logger.normal(this, "Port Mapping ticker stopped.");

    }

    private void realDoPortMapping() {

        serviceManager.addPortMappings(ports, cb);

        long now = System.currentTimeMillis();
        ticker.queueTimedJob(portMappingRunnable, "portMappingRunnable" + now,
                TimeUnit.MINUTES.toMillis(5), false, false);

    }

}
