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

package plugins.UPnP2.models;

/**
 * Model object that represent Max and Min upstream rates. Immutable.
 */
public final class IGDRates {

    private final int upstreamMax;
    private final int downstreamMax;

    public IGDRates(int upstreamMax, int downstreamMax) {
        this.upstreamMax = upstreamMax;
        this.downstreamMax = downstreamMax;
    }

    public int getUpstreamMax() {
        return upstreamMax;
    }

    public int getDownstreamMax() {
        return downstreamMax;
    }
}
