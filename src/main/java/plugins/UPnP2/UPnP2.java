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

import java.util.Collection;
import java.util.Set;

import freenet.pluginmanager.DetectedIP;
import freenet.pluginmanager.ForwardPort;
import freenet.pluginmanager.ForwardPortCallback;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginBandwidthIndicator;
import freenet.pluginmanager.FredPluginIPDetector;
import freenet.pluginmanager.FredPluginPortForward;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;

/**
 * Second generation of UPnP plugin for Fred which is based on Cling.
 *
 * @see <a href="http://4thline.org/projects/cling/">Cling - Java/Android UPnP library and tools</a>
 */
public class UPnP2 implements FredPlugin, FredPluginThreadless, FredPluginIPDetector,
        FredPluginPortForward, FredPluginVersioned, FredPluginRealVersioned,
        FredPluginBandwidthIndicator {

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
    private PortMappingTicker portMappingTicker;


    // ###################################
    // FredPlugin method(s)
    // ###################################

    @Override
    public void terminate() {
        portMappingTicker.stopPortMapping();
        serviceManager.shutdown();

        Logger.normal(this, "UPnP2 plugin ended");
    }

    @Override
    public void runPlugin(PluginRespirator pr) {
        Logger.normal(this, "UPnP2 plugin started");

        serviceManager = new ServiceManager();
        serviceManager.init();

        portMappingTicker = new PortMappingTicker(serviceManager, pr.getNode().getTicker());
    }

    // ###################################
    // FredPluginIPDetector method(s)
    // ###################################

    @Override
    public DetectedIP[] getAddress() {
        Logger.normal(this, "Calling getAddress()");

        Collection<DetectedIP> externalIPs = serviceManager.getExternalIPs();
        if (externalIPs != null) {
            return externalIPs.toArray(new DetectedIP[externalIPs.size()]);
        }
        else {
            return null;
        }

    }

    // ###################################
    // FredPluginPortForward method(s)
    // ###################################

    @Override
    public void onChangePublicPorts(Set<ForwardPort> ports, ForwardPortCallback cb) {
        Logger.normal(this, "Calling onChangePublicPorts()");

        portMappingTicker.startPortMapping(ports, cb);
    }

    // ###################################
    // FredPluginRealVersioned method(s)
    // ###################################

    @Override
    public long getRealVersion() {
        return 6;
    }

    // ###################################
    // FredPluginVersioned method(s)
    // ###################################

    @Override
    public String getVersion() {
        return UPnP2.class.getPackage().getImplementationVersion();
    }

    // ###################################
    // FredPluginBandwidthIndicator method(s)
    // ###################################

    @Override
    public int getUpstramMaxBitRate() {
        System.out.println("Calling getUpstreamMaxBitRate()");

        return serviceManager.getUpstreamMaxBitRate();
    }

    @Override
    public int getDownstreamMaxBitRate() {
        System.out.println("Calling getDownstreamMaxBitRate()");

        return serviceManager.getDownstreamMaxBitRate();
    }
}
