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

package plugins.UPnP2.actions;

import org.fourthline.cling.controlpoint.ActionCallback;
import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UnsignedVariableInteger;

public abstract class GetLinkLayerMaxBitRates extends ActionCallback {

    public GetLinkLayerMaxBitRates(Service service) {
        this(service, null);
    }

    @SuppressWarnings("unchecked")
    public GetLinkLayerMaxBitRates(Service service, ControlPoint controlPoint) {
        super(new ActionInvocation(service.getAction("GetLinkLayerMaxBitRates")), controlPoint);
    }

    @Override
    public void success(ActionInvocation invocation) {
        int newUpstreamMaxBitRate = ((UnsignedVariableInteger) invocation.getOutput
                ("NewUpstreamMaxBitRate").getValue()).getValue().intValue();
        int newDownstreamMaxBitRate = ((UnsignedVariableInteger) invocation.getOutput
                ("NewDownstreamMaxBitRate").getValue()).getValue().intValue();

        success(newUpstreamMaxBitRate, newDownstreamMaxBitRate);

    }

    protected abstract void success(int newUpstreamMaxBitRate, int newDownstreamMaxBitRate);

}
