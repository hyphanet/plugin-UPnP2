package plugins.UPnP2;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.message.header.STAllHeader;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.igd.PortMappingListener;
import org.fourthline.cling.support.model.PortMapping;

import java.util.HashSet;
import java.util.Set;

import plugins.UPnP2.actions.GetSpecificPortMappingEntry;

/**
 * Created by xiaoyu on 12/25/15.
 */
public class ClingTest {

	private static UpnpService upnpService;

	private Set<Service> connectionServices = new HashSet<>();
	private Set<Service> commonServices = new HashSet<>();

	public static void main(String[] args) throws InterruptedException {
		new ClingTest().start();
	}

	public void start() throws InterruptedException {
		// This will create necessary network resources for UPnP right away
		System.out.println("Starting Cling...");
		upnpService = new UpnpServiceImpl(new IGDRegistryListener());
//		upnpService.getRegistry().addListener();

		// Send a search message to all devices and services, they should respond soon
		upnpService.getControlPoint().search(new STAllHeader());

		// Let's wait 10 seconds for them to respond
		System.out.println("Waiting 10 seconds before shutting down...");
		Thread.sleep(10000);



		Thread.sleep(1000000);

		// Release all resources and advertise BYEBYE to other UPnP devices
		System.out.println("Stopping Cling...");
		upnpService.shutdown();

	}

	private class IGDRegistryListener extends PortMappingListener {

		public IGDRegistryListener() {
			super(new PortMapping[0]);
		}

		@Override
		synchronized public void deviceAdded(Registry registry, Device device) {

			System.out.println(
					"Remote device available222222: " + device.getDisplayString()
			);

			Service commonService = discoverCommonService(device);
			if (commonService == null) {
				return;
			}
			commonServices.add(commonService);

			Service connectionService = discoverConnectionService(device);
			if (commonService == null) {
				return;
			}

			connectionServices.add(connectionService);

//			ActionInvocation getStatusInvocation = new ActionInvocation(connectionService.getAction("GetSpecificPortMappingEntry"));

			PortMapping portMapping = new PortMapping(
					56487,
					"192.168.1.10",
					PortMapping.Protocol.UDP,
					"Freenet 0.7 darknet"
			);
//			getStatusInvocation.setInput("NewExternalPort", portMapping.getExternalPort());
//			getStatusInvocation.setInput("NewProtocol", portMapping.getProtocol());
//
//			ActionCallback getStatusCallback = new ActionCallback(getStatusInvocation) {
//				public void success(ActionInvocation invocation) {
//
//					System.out.println("Success");
//					System.out.println(invocation.getOutput("NewEnabled").getValue());
//				}
//				public void failure(ActionInvocation invocation,
//									UpnpResponse operation,
//									String defaultMsg) {
//					System.err.println(defaultMsg);
//				}
//			};

//			upnpService.getControlPoint().execute(getStatusCallback);


			new GetSpecificPortMappingEntry(connectionService,
					upnpService.getControlPoint(), portMapping) {

				@Override
				public void success(ActionInvocation invocation) {
					System.out.println("Yes");
				}

				@Override
				public void failure(ActionInvocation invocation, UpnpResponse operation, String
						defaultMsg) {
					System.out.println("No");
				}
			}.run();

		}

		@Override
		synchronized public void deviceRemoved(Registry registry, Device device) {
			super.deviceRemoved(registry, device);

			// Remove Services
			for (Service service : device.findServices()) {
				connectionServices.remove(service);
			}
		}
		protected Service discoverCommonService(Device device) {
			if (!device.getType().equals(IGD_DEVICE_TYPE)) {
				return null;
			}

			UDADeviceType wanDeviceType = new UDADeviceType("WANDevice");
			Device[] wanDevices = device.findDevices(wanDeviceType);
			if (wanDevices.length == 0) {
				System.out.println("IGD doesn't support '" + wanDeviceType + "': " + device);
				return null;
			}

			Device wanDevice = wanDevices[0];
			System.out.println("Using first discovered WAN device: " + wanDevice);

			return wanDevice.findService(new UDAServiceType("WANCommonInterfaceConfig"));
		}

	}


}
