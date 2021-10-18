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
import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.gena.RemoteGENASubscription;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.igd.PortMappingListener;
import org.fourthline.cling.support.igd.callback.PortMappingAdd;
import org.fourthline.cling.support.model.PortMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import freenet.pluginmanager.ForwardPort;
import freenet.pluginmanager.ForwardPortCallback;
import freenet.pluginmanager.ForwardPortStatus;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import plugins.UPnP2.actions.GetSpecificPortMappingEntry;

/**
 * Cling UpnpService registry listener. Extends PortMappingListener to reuse its port mapping code.
 */
public class IGDRegistryListener extends PortMappingListener {

    private ServiceManager serviceManager;
    private UpnpService upnpService;

    public IGDRegistryListener(ServiceManager serviceManager) {
        super(new PortMapping[0]);

        this.serviceManager = serviceManager;
        upnpService = serviceManager.getUpnpService();
    }

    @Override
    synchronized public void deviceAdded(Registry registry, Device device) {

        Logger.normal(this, "Remote device available: " + device.getDisplayString());

        Service commonService = discoverCommonService(device);
        if (commonService == null) {
            return;
        }

        serviceManager.addCommonService(commonService);

        Service connectionService = discoverConnectionService(device);
        if (commonService == null) {
            return;
        }

        serviceManager.addConnectionService(connectionService);

        // Add service events listener
        SubscriptionCallback callback = new IGDSubscriptionCallback(connectionService, serviceManager);
        upnpService.getControlPoint().execute(callback);
        serviceManager.addSubscriptionCallback(connectionService, callback);

    }

    @Override
    synchronized public void deviceRemoved(Registry registry, Device device) {

        Logger.normal(this, "Remote device unavailable: " + device.getDisplayString());

        super.deviceRemoved(registry, device);

        for (Service service : device.findServices()) {
            // End the subscription
            SubscriptionCallback callback = serviceManager.getSubscriptionCallback(service);

            if (callback != null) {
                callback.end();

                if (callback.getSubscription() instanceof RemoteGENASubscription) {
                    // Remove subscription from registry
                    upnpService.getRegistry().removeRemoteSubscription(
                            (RemoteGENASubscription) callback.getSubscription());
                }

                // Remove subscription callback
                serviceManager.removeSubscriptionCallback(service);
            }
            // Remove Services
            serviceManager.removeConnectionService(service);
            serviceManager.removeCommonService(service);
        }

        // Clear detected IPs
        serviceManager.clearDetectedIPs();

    }

    synchronized public void addPortMappings(final Service connectionService, Set<PortMapping>
            newPortMappings, final Map<PortMapping, ForwardPort> forwardPortMap, final
                                             ForwardPortCallback cb) {

        if (connectionService == null || newPortMappings.size() == 0) return;

        Logger.normal(this, "Activating port mappings on: " + connectionService);

        final List<PortMapping> activeForService = new ArrayList<>();
        for (final PortMapping pm : newPortMappings) {

            Logger.normal(this, "Checking if the Port is already Mapped: " + pm);

            new GetSpecificPortMappingEntry(connectionService, upnpService.getControlPoint(),
                    pm) {
                @Override
                public void success(ActionInvocation invocation) {
                    Logger.normal(this, "Port is already Mapped: " + pm);
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation,
                                    String defaultMsg) {
                    Logger.normal(this, "Port is not Mapped: " + pm);
                    Logger.normal(this, "Adding Port Mapping: " + pm);

                    final ForwardPort forwardPort = forwardPortMap.get(pm);

                    new PortMappingAdd(connectionService, upnpService.getControlPoint(), pm) {

                        @Override
                        public void success(ActionInvocation invocation) {
                            Logger.normal(this, "Port mapping added: " + pm);
                            activeForService.add(pm);

                            // Notify Fred the port mapping is successful
                            ForwardPortStatus status = new ForwardPortStatus(ForwardPortStatus
                                    .MAYBE_SUCCESS, "", pm.getExternalPort().getValue()
                                    .intValue());

                            Map<ForwardPort, ForwardPortStatus> statuses = new HashMap<>();
                            statuses.put(forwardPort, status);

                            cb.portForwardStatus(statuses);
                        }

                        @Override
                        public void failure(ActionInvocation invocation, UpnpResponse operation,
                                            String defaultMsg) {
                            Logger.warning(this, "Failed to add port mapping: " + pm);
                            Logger.warning(this, "Reason: " + defaultMsg);

                            // Notify Fred the port mapping is failed
                            ForwardPortStatus status = new ForwardPortStatus(ForwardPortStatus
                                    .DEFINITE_FAILURE, defaultMsg, forwardPort.portNumber);

                            Map<ForwardPort, ForwardPortStatus> statuses = new HashMap<>();
                            statuses.put(forwardPort, status);

                            cb.portForwardStatus(statuses);
                        }
                    }.run(); // Synchronous!
                }
            }.run(); // Synchronous!

            activePortMappings.put(connectionService, activeForService);

        }

    }

    public void removeAllPortMappings() {
        this.beforeShutdown(upnpService.getRegistry());
    }

    protected Service discoverCommonService(Device device) {
        if (!device.getType().equals(IGD_DEVICE_TYPE)) {
            return null;
        }

        UDADeviceType wanDeviceType = new UDADeviceType("WANDevice");
        Device[] wanDevices = device.findDevices(wanDeviceType);
        if (wanDevices.length == 0) {
            Logger.normal(this, "IGD doesn't support '" + wanDeviceType + "': " + device);
            return null;
        }

        Device wanDevice = wanDevices[0];
        Logger.normal(this, "Using first discovered WAN device: " + wanDevice);

        return wanDevice.findService(new UDAServiceType("WANCommonInterfaceConfig"));
    }
}
