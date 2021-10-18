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
import org.fourthline.cling.model.UnsupportedDataException;
import org.fourthline.cling.model.gena.CancelReason;
import org.fourthline.cling.model.gena.GENASubscription;
import org.fourthline.cling.model.gena.RemoteGENASubscription;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.state.StateVariableValue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import freenet.pluginmanager.DetectedIP;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.transport.ip.IPUtil;

/**
 * UPnP device event callbacks. Internet Gateway Devices will return its external IP in its callback
 * values. We use it to monitor external IP changes.
 */
class IGDSubscriptionCallback extends SubscriptionCallback {

    private static volatile boolean logMINOR;

    static {
        Logger.registerLogThresholdCallback(new LogThresholdCallback() {
            @Override
            public void shouldUpdate() {
                logMINOR = Logger.shouldLog(Logger.LogLevel.MINOR, this);
            }
        });
    }

    private final ServiceManager serviceManager;
    private int renewalFailedCount = 0;

    public IGDSubscriptionCallback(Service connectionService, ServiceManager serviceManager) {
        super(connectionService, 600);
        this.serviceManager = serviceManager;
    }

    @Override
    public void established(GENASubscription sub) {
        Logger.normal(this, "GENA Established: " + sub.getSubscriptionId());
    }

    @Override
    protected void failed(GENASubscription subscription,
                          UpnpResponse responseStatus,
                          Exception exception,
                          String defaultMsg) {
        Logger.warning(this, "GENA Failed: " + defaultMsg);
    }

    @Override
    public void ended(GENASubscription sub,
                      CancelReason reason,
                      UpnpResponse response) {
        Logger.normal(this, "GENA Ended: " + reason);
        if (logMINOR) Logger.minor(this, "GENA Response: " + response);
        if (reason == CancelReason.RENEWAL_FAILED) {
            renewalFailedCount++;

            if (renewalFailedCount == 5) {
                // Some routers doesn't response correct header and Cling won't be able
                // to renew it. Then we need to remove and re-subscribe.

                Logger.warning(this, "Renewal failed. Try to re-subscribe.");

                UpnpService upnpService = serviceManager.getUpnpService();

                // Remove current subscription from registry
                upnpService.getRegistry().removeRemoteSubscription((RemoteGENASubscription)
                        sub);

                SubscriptionCallback callback = new IGDSubscriptionCallback(service,
                        serviceManager);
                upnpService.getControlPoint().execute(callback);
                serviceManager.addSubscriptionCallback(service, callback);
            }

        }
    }

    @Override
    public void eventReceived(GENASubscription sub) {

        // Once we received an event, it means Cling has found at least one device. So we can set
        // booted to true.
        serviceManager.setBooted(true);

        Map<String, StateVariableValue<?>> values = sub.getCurrentValues();

        StateVariableValue<?> externalIPAddress =
                values.get("ExternalIPAddress");

        try {
            InetAddress inetAddress = InetAddress.getByName
                    (externalIPAddress.toString());
            if (IPUtil.isValidAddress(inetAddress, false)) {
                DetectedIP detectedIP = new DetectedIP(inetAddress, DetectedIP.NOT_SUPPORTED);
                if (!serviceManager.getDetectedIPs().containsValue(detectedIP)) {
                    Logger.normal(this, "New External IP found: " + externalIPAddress
                            .toString());
                    Logger.normal(this, "For device: " +
                            sub.getService().getDevice().getRoot().getDisplayString());
                    serviceManager.addDetectedIP(sub.getService().getDevice().getRoot(),
                            detectedIP);
                }
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void eventsMissed(GENASubscription sub, int numberOfMissedEvents) {
        Logger.warning(this, "Missed events: " + numberOfMissedEvents);
    }

    @Override
    protected void invalidMessage(RemoteGENASubscription sub,
                                  UnsupportedDataException ex) {
        // Log/send an error report?
    }

}
