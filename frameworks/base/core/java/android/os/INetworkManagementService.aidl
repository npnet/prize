/* //device/java/android/android/os/INetworkManagementService.aidl
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package android.os;

import android.net.InterfaceConfiguration;
import android.net.INetd;
import android.net.INetworkManagementEventObserver;
import android.net.Network;
import android.net.NetworkStats;
import android.net.RouteInfo;
import android.net.UidRange;
import android.net.wifi.WifiConfiguration;
import android.os.INetworkActivityListener;
/**
 * @hide
 */
interface INetworkManagementService
{
    /**
     ** GENERAL
     **/

    /**
     * Register an observer to receive events.
     */
    void registerObserver(INetworkManagementEventObserver obs);

    /**
     * Unregister an observer from receiving events.
     */
    void unregisterObserver(INetworkManagementEventObserver obs);

    /**
     * Retrieve an INetd to talk to netd.
     */
    INetd getNetdService();

    /**
     * Returns a list of currently known network interfaces
     */
    String[] listInterfaces();

    /**
     * Retrieves the specified interface config
     *
     */
    InterfaceConfiguration getInterfaceConfig(String iface);

    /**
     * Sets the configuration of the specified interface
     */
    void setInterfaceConfig(String iface, in InterfaceConfiguration cfg);

    /**
     * Clear all IP addresses on the specified interface
     */
    void clearInterfaceAddresses(String iface);

    /**
     * Set interface down
     */
    void setInterfaceDown(String iface);

    /**
     * Set interface up
     */
    void setInterfaceUp(String iface);

    /**
     * Set interface IPv6 privacy extensions
     */
    void setInterfaceIpv6PrivacyExtensions(String iface, boolean enable);

    /**
     * Disable IPv6 on an interface
     */
    void disableIpv6(String iface);

    /**
     * Enable IPv6 on an interface
     */
    void enableIpv6(String iface);

    /**
     * Enables or enables IPv6 ND offload.
     */
    void setInterfaceIpv6NdOffload(String iface, boolean enable);

    /**
     * Add the specified route to the interface.
     */
    void addRoute(int netId, in RouteInfo route);

    /**
     * Remove the specified route from the interface.
     */
    void removeRoute(int netId, in RouteInfo route);

    /**
     * Set the specified MTU size
     */
    void setMtu(String iface, int mtu);

    /**
     * Shuts down the service
     */
    void shutdown();

    /**
     ** TETHERING RELATED
     **/

    /**
     * Returns true if IP forwarding is enabled
     */
    boolean getIpForwardingEnabled();

    /**
     * Enables/Disables IP Forwarding
     */
    void setIpForwardingEnabled(boolean enabled);

    /**
     * Start tethering services with the specified dhcp server range
     * arg is a set of start end pairs defining the ranges.
     */
    void startTethering(in String[] dhcpRanges);

    /**
     * Stop currently running tethering services
     */
    void stopTethering();

    /**
     * Returns true if tethering services are started
     */
    boolean isTetheringStarted();

    /**
     * Tethers the specified interface
     */
    void tetherInterface(String iface);

    /**
     * Untethers the specified interface
     */
    void untetherInterface(String iface);

    /**
     * Returns a list of currently tethered interfaces
     */
    String[] listTetheredInterfaces();

    /**
     * Sets the list of DNS forwarders (in order of priority)
     */
    void setDnsForwarders(in Network network, in String[] dns);

    /**
     * Returns the list of DNS forwarders (in order of priority)
     */
    String[] getDnsForwarders();

    /**
     * Enables unidirectional packet forwarding from {@code fromIface} to
     * {@code toIface}.
     */
    void startInterfaceForwarding(String fromIface, String toIface);

    /**
     * Disables unidirectional packet forwarding from {@code fromIface} to
     * {@code toIface}.
     */
    void stopInterfaceForwarding(String fromIface, String toIface);

    /**
     *  Enables Network Address Translation between two interfaces.
     *  The address and netmask of the external interface is used for
     *  the NAT'ed network.
     */
    void enableNat(String internalInterface, String externalInterface);

    /**
     *  Disables Network Address Translation between two interfaces.
     */
    void disableNat(String internalInterface, String externalInterface);

    /**
     ** PPPD
     **/

    /**
     * Returns the list of currently known TTY devices on the system
     */
    String[] listTtys();

    /**
     * Attaches a PPP server daemon to the specified TTY with the specified
     * local/remote addresses.
     */
    void attachPppd(String tty, String localAddr, String remoteAddr, String dns1Addr,
            String dns2Addr);

    /**
     * Detaches a PPP server daemon from the specified TTY.
     */
    void detachPppd(String tty);

    /**
     * Load firmware for operation in the given mode. Currently the three
     * modes supported are "AP", "STA" and "P2P".
     */
    void wifiFirmwareReload(String wlanIface, String mode);

    /**
     * Start Wifi Access Point
     */
    void startAccessPoint(in WifiConfiguration wifiConfig, String iface);

    /**
     * Stop Wifi Access Point
     */
    void stopAccessPoint(String iface);

    /**
     * Set Access Point config
     */
    void setAccessPoint(in WifiConfiguration wifiConfig, String iface);

    /**
     ** DATA USAGE RELATED
     **/

    /**
     * Return global network statistics summarized at an interface level,
     * without any UID-level granularity.
     */
    NetworkStats getNetworkStatsSummaryDev();
    NetworkStats getNetworkStatsSummaryXt();

    /**
     * Return detailed network statistics with UID-level granularity,
     * including interface and tag details.
     */
    NetworkStats getNetworkStatsDetail();

    /**
     * Return detailed network statistics for the requested UID,
     * including interface and tag details.
     */
    NetworkStats getNetworkStatsUidDetail(int uid);

    /**
     * Return summary of network statistics all tethering interfaces.
     */
    NetworkStats getNetworkStatsTethering();

    /**
     * Set quota for an interface.
     */
    void setInterfaceQuota(String iface, long quotaBytes);

    /**
     * Remove quota for an interface.
     */
    void removeInterfaceQuota(String iface);

    /**
     * Set alert for an interface; requires that iface already has quota.
     */
    void setInterfaceAlert(String iface, long alertBytes);

    /**
     * Remove alert for an interface.
     */
    void removeInterfaceAlert(String iface);

    /**
     * Set alert across all interfaces.
     */
    void setGlobalAlert(long alertBytes);

    /**
     * Control network activity of a UID over interfaces with a quota limit.
     */
    void setUidMeteredNetworkBlacklist(int uid, boolean enable);
    void setUidMeteredNetworkWhitelist(int uid, boolean enable);
    boolean setDataSaverModeEnabled(boolean enable);

    void setUidCleartextNetworkPolicy(int uid, int policy);

    /**
     * Return status of bandwidth control module.
     */
    boolean isBandwidthControlEnabled();

    /**
     * Sets idletimer for an interface.
     *
     * This either initializes a new idletimer or increases its
     * reference-counting if an idletimer already exists for given
     * {@code iface}.
     *
     * {@code type} is the type of the interface, such as TYPE_MOBILE.
     *
     * Every {@code addIdleTimer} should be paired with a
     * {@link removeIdleTimer} to cleanup when the network disconnects.
     */
    void addIdleTimer(String iface, int timeout, int type);

    /**
     * Removes idletimer for an interface.
     */
    void removeIdleTimer(String iface);

    /**
     * Configure name servers, search paths, and resolver parameters for the given network.
     */
    void setDnsConfigurationForNetwork(int netId, in String[] servers, String domains);

    /**
     * Bind name servers to a network in the DNS resolver.
     */
    void setDnsServersForNetwork(int netId, in String[] servers, String domains);

    void setFirewallEnabled(boolean enabled);
    boolean isFirewallEnabled();
    void setFirewallInterfaceRule(String iface, boolean allow);
    void setFirewallEgressSourceRule(String addr, boolean allow);
    void setFirewallEgressDestRule(String addr, int port, boolean allow);
    void setFirewallUidRule(int chain, int uid, int rule);
    void setFirewallUidRules(int chain, in int[] uids, in int[] rules);
    void setFirewallChainEnabled(int chain, boolean enable);

    /**
     * Set all packets from users in ranges to go through VPN specified by netId.
     */
    void addVpnUidRanges(int netId, in UidRange[] ranges);

    /**
     * Clears the special VPN rules for users in ranges and VPN specified by netId.
     */
    void removeVpnUidRanges(int netId, in UidRange[] ranges);

    /**
     * Start the clatd (464xlat) service on the given interface.
     */
    void startClatd(String interfaceName);

    /**
     * Stop the clatd (464xlat) service on the given interface.
     */
    void stopClatd(String interfaceName);

    /**
     * Determine whether the clatd (464xlat) service has been started on the given interface.
     */
    boolean isClatdStarted(String interfaceName);

    /**
     * Start listening for mobile activity state changes.
     */
    void registerNetworkActivityListener(INetworkActivityListener listener);

    /**
     * Stop listening for mobile activity state changes.
     */
    void unregisterNetworkActivityListener(INetworkActivityListener listener);

    /**
     * Check whether the mobile radio is currently active.
     */
    boolean isNetworkActive();

    /**
     * Setup a new physical network.
     * @param permission null if no permissions required to access this network.  PERMISSION_NETWORK
     *                   or PERMISSION_SYSTEM to set respective permission.
     */
    void createPhysicalNetwork(int netId, String permission);

    /**
     * Setup a new VPN.
     */
    void createVirtualNetwork(int netId, boolean hasDNS, boolean secure);

    /**
     * Remove a network.
     */
    void removeNetwork(int netId);

    /**
     * Add an interface to a network.
     */
    void addInterfaceToNetwork(String iface, int netId);

    /**
     * Remove an Interface from a network.
     */
    void removeInterfaceFromNetwork(String iface, int netId);

    void addLegacyRouteForNetId(int netId, in RouteInfo routeInfo, int uid);

    void setDefaultNetId(int netId);
    void clearDefaultNetId();

    /**
     * Set permission for a network.
     * @param permission null to clear permissions. PERMISSION_NETWORK or PERMISSION_SYSTEM to set
     *                   permission.
     */
    void setNetworkPermission(int netId, String permission);

    void setPermission(String permission, in int[] uids);
    void clearPermission(in int[] uids);

    /**
     * Allow UID to call protect().
     */
    void allowProtect(int uid);

    /**
     * Deny UID from calling protect().
     */
    void denyProtect(int uid);

    void addInterfaceToLocalNetwork(String iface, in List<RouteInfo> routes);
    void removeInterfaceFromLocalNetwork(String iface);
    int removeRoutesFromLocalNetwork(in List<RouteInfo> routes);

    void setAllowOnlyVpnForUids(boolean enable, in UidRange[] uidRanges);

    //M:
    /**
     * Stop currently running tethering services
     */
    void disablePPPOE();

    /**
     * ipv6 tethering
     * @hide
     */
     void enableNatIpv6(String internalInterface, String externalInterface);

    /**
     * ipv6 tethering
     * @hide
     */
     void disableNatIpv6(String internalInterface, String externalInterface);

    /**
     * ipv6 tethering
     * @hide
     */
     void setIpv6ForwardingEnabled(boolean enable);

    /**
     * ipv6 tethering
     * @hide
     */
     boolean getIpv6ForwardingEnabled();

    /**
     * ipv6 tethering
     * @hide
     */
     void setRouteIpv6(String internalInterface, String externalInterface);

    /**
     * ipv6 tethering
     * @hide
     */
     void clearRouteIpv6(String internalInterface, String externalInterface);

    /**
     * ipv6 tethering + dedicated apn
     * @hide
     */
     void setSourceRouteIpv6(String internalInterface, String externalInterface);

    /**
     * ipv6 tethering + dedicated apn
     * @hide
     */
     void clearSourceRouteIpv6(String internalInterface, String externalInterface);

    /**
     * Always on VPN - Support PPTP
     * @hide
     */
    void setFirewallEgressProtoRule(String proto, boolean allow);

    /**
     * Dhcpv6 - stateless
     * @hide
     */
    void setDhcpv6Enabled(boolean enable, String ifc);


    /**
     * [NS-IOT Support]Support UDP packets forwarding from device to tethering terminal
     * @hide
     */
    void enableUdpForwarding(boolean enabled, String internalInterface, String externalInterface, String ipAddr);

    /**
     * [NS-IOT Support]Retrieve client ip address from arp result
     * @hide
     */
    void getUsbClient(String iface);

    /**
     * Cnfigure firewall rule by uid and chain
     * @hide
     */
    void setFirewallUidChainRule(int uid, int networkType, boolean allow);

    /** @} */

    /**
     * Delete all rules in  chain or all chains
     * @hide
     */
    void clearFirewallChain(String chain);

    /** @} */

    /** @} */
    /**
     * WiFi Hotspot manager - BandwidthControl
     * Configures bandwidth throttling on an interface.
     * @hide
     */
    void setInterfaceThrottle(String iface, int rxKbps, int txKbps);

    /**
     * WiFi Hotspot manager - BandwidthControl
     * Returns the currently configured RX throttle values
     * for the specified interface
     * @hide
     */
    int getInterfaceRxThrottle(String iface);

    /**
     * WiFi Hotspot manager - BandwidthControl
     * Returns the currently configured TX throttle values
     * for the specified interface
     * @hide
     */
    int getInterfaceTxThrottle(String iface);

    /**
     * Support controlling app network for sleeping. prize-linkh-20160630
     * @hide
     */
    void setFirewallUidRuleForSleeping(int uid, boolean allow);

    /**
     *  sip info
     * @param interfaceName input
     * @param service input
     * @param protocol input
     * @param result_array output, String[0] = hostname, String[1] = port
     * @hide
     */
    String[] getSipInfo(String interfaceName, String service, String protocol);

    /**
     *  sip info
     * @hide
     * @param interfaceName input
     */
    void clearSipInfo(String interfaceName);

    /**
     * Delete all NS-IOT firewall rules
     * @hide
     */
    void clearIotFirewall();

    /**
     * Set all NS-IOT firewall rules
     * @hide
     */
    void setIotFirewall();

    /**
     * M: Delete all NS-IOT firewall rules for VoLTE test
     * @hide
     */
    void clearVolteIotFirewall(String ifc);

    /**
     * M: Set all NS-IOT firewall rules for VoLTE test
     * @hide
     */
    void setVolteIotFirewall(String ifc);

    /**
     * M: MD direct tethering
     * @hide
     */
    void addBridge(String bridgeInterface);

    /**
     * M: MD direct tethering
     * @hide
     */
    void deleteBridge(String bridgeInterface);

    /**
     * M: MD direct tethering
     * @hide
     */
    void addBridgeInterface(String bridgeInterface, String portInterface);

    /**
     * M: MD direct tethering
     * @hide
     */
    void deleteBridgeInterface(String bridgeInterface, String portInterface);

    /**
     * M: MD direct tethering
     * @hide
     */
    void clearBridgeMac(String bridgeInterface);    
}
