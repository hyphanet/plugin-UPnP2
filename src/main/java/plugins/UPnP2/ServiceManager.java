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

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.support.model.PortMapping;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import freenet.pluginmanager.DetectedIP;
import freenet.pluginmanager.ForwardPort;
import freenet.pluginmanager.ForwardPortCallback;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.transport.ip.IPUtil;
import plugins.UPnP2.actions.GetCommonLinkProperties;
import plugins.UPnP2.actions.GetExternalIPSync;
import plugins.UPnP2.actions.GetLinkLayerMaxBitRates;
import plugins.UPnP2.models.IGDRates;

/**
 * Manage UPnP Services.
 * Singleton.
 */
class ServiceManager {

    private static volatile boolean logMINOR;

    static {
        Logger.registerLogThresholdCallback(new LogThresholdCallback() {
            @Override
            public void shouldUpdate() {
                logMINOR = Logger.shouldLog(Logger.LogLevel.MINOR, this);
            }
        });
    }

    /**
     * Cling Core UPnP stack
     */
    private UpnpService upnpService = new UpnpServiceImpl();

    /**
     * Services of type WANIPConnection or WANPPPConnection
     */
    private Set<Service> connectionServices = new CopyOnWriteArraySet<>();

    /**
     * Services of type WANCommonInterfaceConfig
     */
    private Set<Service> commonServices = new CopyOnWriteArraySet<>();

    /**
     * Callbacks for UPnP events
     */
    private Map<Service, SubscriptionCallback> subscriptionCallbacks = new ConcurrentHashMap<>();
    /**
     * Store detected External IPs for different services
     */
    private Map<Device, DetectedIP> detectedIPs = new ConcurrentHashMap<>();

    private volatile boolean booted = false;

    private IGDRegistryListener registryListener;

    /**
     * Initialize service manager
     */
    public void init() {

        // This will create necessary network resources for UPnP right away
        Logger.normal(this, "Starting Cling...");

        // Add listeners for upnpService
        registryListener = new IGDRegistryListener(this);
        upnpService.getRegistry().addListener(registryListener);

        // Send a search message to all devices and services, they should respond soon
        upnpService.getControlPoint().search();

    }

    public void shutdown() {
        // Release all resources and advertise BYEBYE to other UPnP devices
        upnpService.shutdown();
    }

    synchronized public void waitForBooting() {
        // If no connection services available and it's the first call,
        // we retry 10 times for the plugin to get enough IGDs
        if (!booted) {
            for (int count = 0; count < 10; count++) {
                if (connectionServices.size() == 0) {
                    // No devices found yet
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    // Devices found. Wait for 5 more seconds for more devices
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
                // Whatever any devices found, we won't sleep any more. If new devices later
                // added, Cling will automatically subscribe the service's event and report IPs
            }
            booted = true;
        }

    }

    public Collection<DetectedIP> getExternalIPs() {
        waitForBooting();

        if (connectionServices.size() == 0) {
            return null;
        }

        if (detectedIPs.size() > 0) {
            // IP is found from Service event callback
            return detectedIPs.values();
        } else {
            // Maybe the event is not fired for some reason. We need to request the IP manually.
            realGetExternalIPs();

            if (detectedIPs.size() > 0) {
                return detectedIPs.values();
            } else {
                return null;
            }
        }
    }

    /**
     * Actively request external IP addresses. This method blocks.
     */
    private void realGetExternalIPs() {

        if (connectionServices.size() == 0) {
            Logger.warning(this, "No internet gateway device detected. Unable to get external " +
                    "address.");
            return;
        }

        Logger.normal(this, "Try to get external IP");

        for (Service connectionService : connectionServices) {

            new GetExternalIPSync(connectionService, upnpService.getControlPoint()) {

                @Override
                protected void success(String externalIPAddress) {
                    try {
                        System.out.println("Get external IP: " + externalIPAddress);

                        InetAddress inetAddress = InetAddress.getByName
                                (externalIPAddress);
                        if (IPUtil.isValidAddress(inetAddress, false)) {
                            detectedIPs.put(getActionInvocation().getAction()
                                            .getService().getDevice().getRoot(),
                                    new DetectedIP(inetAddress,
                                            DetectedIP.NOT_SUPPORTED));
                        }

                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void failure(ActionInvocation invocation,
                                    UpnpResponse operation,
                                    String defaultMsg) {
                    Logger.warning(this, "Unable to get external IP. Reason: " +
                            defaultMsg);
                }
            }.run(); // Synchronous!

        }
    }

    public int getUpstreamMaxBitRate() {
        Logger.normal(this, "Calling getUpstreamMaxBitRate()");

        waitForBooting();

        if (connectionServices.size() < 0 && commonServices.size() < 0) {
            return -1;
        }

        IGDRates rates = getRates();


        if (rates == null) {
            return -1;
        }

        Logger.normal(this, "Upstream MaxBitRate: " + rates.getUpstreamMax());

        return rates.getUpstreamMax();
    }

    public int getDownstreamMaxBitRate() {
        Logger.normal(this, "Calling getDownstreamMaxBitRate()");

        waitForBooting();

        if (connectionServices.size() < 0 && commonServices.size() < 0) {
            return -1;
        }

        IGDRates rates = getRates();

        if (rates == null) {
            return -1;
        }

        Logger.normal(this, "Downstream MaxBitRate: " + rates.getDownstreamMax());

        return rates.getDownstreamMax();
    }


    private IGDRates getRates() {

        final List<Integer> upRates = new ArrayList<>();
        final List<Integer> downRates = new ArrayList<>();

        for (final Service service : connectionServices) {
            if (logMINOR) Logger.minor(this, "Service Type: " + service.getServiceType().getType());
            if (service.getServiceType().getType().equals("WANPPPConnection")
                    // Make sure the device isn't double natted
                    // Double natted devices won't have a valid external IP
                    && detectedIPs.containsKey(service.getDevice().getRoot())
                    ) {

                new GetLinkLayerMaxBitRates(service, upnpService.getControlPoint()) {
                    @Override
                    protected void success(int newUpstreamMaxBitRate, int newDownstreamMaxBitRate) {
                        if (logMINOR)
                            Logger.minor(this, "newUpstreamMaxBitRate: " + newUpstreamMaxBitRate);
                        if (logMINOR)
                            Logger.minor(this, "newDownstreamMaxBitRate: " +
                                    newDownstreamMaxBitRate);

                        upRates.add(newUpstreamMaxBitRate);
                        downRates.add(newDownstreamMaxBitRate);
                    }

                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse operation,
                                        String defaultMsg) {
                        Logger.warning(this, "Unable to get MaxBitRates. Reason: " +
                                defaultMsg);
                    }
                }.run(); // Synchronous!

            }
        }

        if (upRates.size() > 0) {
            int upRatesSum = 0;
            for (int rate : upRates) {
                upRatesSum += rate;
            }
            int downRatesSum = 0;
            for (int rate : downRates) {
                downRatesSum += rate;
            }
            return new IGDRates(upRatesSum, downRatesSum);
        }

        // We get nothing from GetLinkLayerMaxBitRates. Try GetCommonLinkProperties

        final List<Integer> upRates2 = new ArrayList<>();
        final List<Integer> downRates2 = new ArrayList<>();

        for (final Service service : commonServices) {
            if (logMINOR) Logger.minor(this, "Service Type: " + service.getServiceType().getType());

            // Make sure the device isn't double natted
            // Double natted devices won't have a valid external IP
            if (detectedIPs.containsKey(service.getDevice().getRoot())) {
                new GetCommonLinkProperties(service, upnpService.getControlPoint()) {
                    @Override
                    protected void success(int newUpstreamMaxBitRate, int newDownstreamMaxBitRate) {
                        if (logMINOR)
                            Logger.minor(this, "newUpstreamMaxBitRate: " + newUpstreamMaxBitRate);
                        if (logMINOR)
                            Logger.minor(this, "newDownstreamMaxBitRate: " +
                                    newDownstreamMaxBitRate);

                        upRates2.add(newUpstreamMaxBitRate);
                        downRates2.add(newDownstreamMaxBitRate);
                    }

                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse operation,
                                        String defaultMsg) {
                        Logger.warning(this, "Unable to get GetCommonLinkProperties. Reason: " +
                                defaultMsg);
                    }
                }.run(); // Synchronous!
            }
        }

        if (upRates2.size() > 0) {
            int upRatesSum = 0;
            for (int rate : upRates2) {
                upRatesSum += rate;
            }
            int downRatesSum = 0;
            for (int rate : downRates2) {
                downRatesSum += rate;
            }
            return new IGDRates(upRatesSum, downRatesSum);
        }

        return null;
    }

    public void addPortMappings(Set<ForwardPort> ports, ForwardPortCallback cb) {
        if (connectionServices.size() > 0) {

            Set<PortMapping> portMappings = new HashSet<>();
            Map<PortMapping, ForwardPort> forwardPortMap = new HashMap<>();
            for (Service connectionService : connectionServices) {
                for (ForwardPort port : ports) {

                    PortMapping.Protocol protocol;
                    String protocolName;
                    switch (port.protocol) {
                        case ForwardPort.PROTOCOL_UDP_IPV4:
                            protocol = PortMapping.Protocol.UDP;
                            protocolName = "UDP";
                            break;
                        case ForwardPort.PROTOCOL_TCP_IPV4:
                            protocol = PortMapping.Protocol.TCP;
                            protocolName = "TCP";
                            break;
                        default:
                            protocol = PortMapping.Protocol.UDP;
                            protocolName = "UDP";
                    }

                    Logger.normal(this, String.format("Mapping port: %s %d (%s)%n", protocolName,
                            port.portNumber, port.name));

                    // Each service has its own local IP
                    String localIP = ((RemoteDevice) connectionService.getDevice())
                            .getIdentity()
                            .getDiscoveredOnLocalAddress().getHostAddress();


                    if (logMINOR)
                        Logger.minor(this, "For device: " + connectionService.getDevice());
                    if (logMINOR) Logger.minor(this, "For service: " + connectionService);
                    if (logMINOR) Logger.minor(this, "For local IP: " + localIP);

                    PortMapping portMapping = new PortMapping(
                            port.portNumber,
                            localIP,
                            protocol,
                            "Freenet 0.7 " + port.name
                    );


                    // Mapping for each local IP
                    portMappings.add(portMapping);

                    forwardPortMap.put(portMapping, port);
                }

                // Add this port's mappings for this service
                registryListener.addPortMappings(connectionService, portMappings,
                        forwardPortMap,
                        cb);
                // Clear portMappings and get ready for next action
                portMappings.clear();
                forwardPortMap.clear();

            }
        } else {
            Logger.warning(this, "Unable to get localIPs.");
        }

    }

    public void removeAllPortMappings() {
        registryListener.removeAllPortMappings();
    }

    // #############################
    // Getters and Setters
    // #############################

    public UpnpService getUpnpService() {
        return upnpService;
    }

    public Set<Service> getConnectionServices() {
        return connectionServices;
    }

    public void addConnectionService(Service connectionService) {
        connectionServices.add(connectionService);
    }

    public void removeConnectionService(Service connectionService) {
        connectionServices.remove(connectionService);
    }

    public Set<Service> getCommonServices() {
        return commonServices;
    }

    public void addCommonService(Service commonService) {
        commonServices.add(commonService);
    }

    public void removeCommonService(Service commonService) {
        commonServices.remove(commonService);
    }

    public Map<Service, SubscriptionCallback> getSubscriptionCallbacks() {
        return subscriptionCallbacks;
    }

    public void addSubscriptionCallback(Service service, SubscriptionCallback callback) {
        subscriptionCallbacks.put(service, callback);
    }

    public void removeSubscriptionCallback(Service service) {
        subscriptionCallbacks.remove(service);
    }

    public SubscriptionCallback getSubscriptionCallback(Service service) {
        return subscriptionCallbacks.get(service);
    }

    public Map<Device, DetectedIP> getDetectedIPs() {
        return detectedIPs;
    }

    public DetectedIP getDetectedIP(Device device) {
        return detectedIPs.get(device);
    }

    public void addDetectedIP(Device device, DetectedIP detectedIP) {
        detectedIPs.put(device, detectedIP);
    }

    public void clearDetectedIPs() {
        detectedIPs.clear();
    }

    public boolean isBooted() {
        return booted;
    }

    public void setBooted(boolean booted) {
        this.booted = booted;
    }
}
