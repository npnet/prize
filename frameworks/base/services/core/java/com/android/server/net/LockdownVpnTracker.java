/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.net;

import static android.Manifest.permission.CONNECTIVITY_INTERNAL;
import static android.net.NetworkPolicyManager.FIREWALL_CHAIN_NONE;
import static android.net.NetworkPolicyManager.FIREWALL_RULE_ALLOW;
import static android.net.NetworkPolicyManager.FIREWALL_RULE_DEFAULT;
import static android.provider.Settings.ACTION_VPN_SETTINGS;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.LinkAddress;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.NetworkPolicyManager;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.security.Credentials;
import android.security.KeyStore;
import android.system.Os;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.net.VpnConfig;
import com.android.internal.net.VpnProfile;
import com.android.internal.util.Preconditions;
import com.android.server.ConnectivityService;
import com.android.server.EventLogTags;
import com.android.server.connectivity.Vpn;

import java.util.List;

/**
 * State tracker for lockdown mode. Watches for normal {@link NetworkInfo} to be
 * connected and kicks off VPN connection, managing any required {@code netd}
 * firewall rules.
 */
public class LockdownVpnTracker {
    private static final String TAG = "LockdownVpnTracker";

    /** Number of VPN attempts before waiting for user intervention. */
    private static final int MAX_ERROR_COUNT = 4;

    private static final String ACTION_LOCKDOWN_RESET = "com.android.server.action.LOCKDOWN_RESET";
    ///M:  To handle keystore reset
    private static final String ACTION_KEYSTORE_RESET =
        "com.mediatek.android.keystore.action.KEYSTORE_RESET";

    private static final int ROOT_UID = 0;

    private final Context mContext;
    private final INetworkManagementService mNetService;
    private final ConnectivityService mConnService;
    private final Vpn mVpn;
    private final VpnProfile mProfile;

    private final Object mStateLock = new Object();

    private final PendingIntent mConfigIntent;
    private final PendingIntent mResetIntent;

    private String mAcceptedEgressIface;
    private String mAcceptedIface;
    private List<LinkAddress> mAcceptedSourceAddr;

    private int mErrorCount;

    public static boolean isEnabled() {
        return KeyStore.getInstance().contains(Credentials.LOCKDOWN_VPN);
    }

    ///M: Fix file corrupt issue ALPS01378269
    public static boolean isFileUsable() {
        return KeyStore.getInstance().get(Credentials.LOCKDOWN_VPN) != null;
    }

    public LockdownVpnTracker(Context context, INetworkManagementService netService,
            ConnectivityService connService, Vpn vpn, VpnProfile profile) {
        mContext = Preconditions.checkNotNull(context);
        mNetService = Preconditions.checkNotNull(netService);
        mConnService = Preconditions.checkNotNull(connService);
        mVpn = Preconditions.checkNotNull(vpn);
        mProfile = Preconditions.checkNotNull(profile);

        final Intent configIntent = new Intent(ACTION_VPN_SETTINGS);
        mConfigIntent = PendingIntent.getActivity(mContext, 0, configIntent, 0);

        final Intent resetIntent = new Intent(ACTION_LOCKDOWN_RESET);
        resetIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mResetIntent = PendingIntent.getBroadcast(mContext, 0, resetIntent, 0);
    }

    private BroadcastReceiver mResetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reset();
        }
    };

    ///M:  To handle keystore reset
    private BroadcastReceiver mKeystoreResetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mVpn != null) mVpn.forceDisconnect();
            reset();
            if (mConnService != null) mConnService.updateLockdownVpn();
        }
    };

    /**
     * Watch for state changes to both active egress network, kicking off a VPN
     * connection when ready, or setting firewall rules once VPN is connected.
     */
    private void handleStateChangedLocked() {

        final NetworkInfo egressInfo = mConnService.getActiveNetworkInfoUnfiltered();
        final LinkProperties egressProp = mConnService.getActiveLinkProperties();

        final NetworkInfo vpnInfo = mVpn.getNetworkInfo();
        final VpnConfig vpnConfig = mVpn.getLegacyVpnConfig();

        // Restart VPN when egress network disconnected or changed
        final boolean egressDisconnected = egressInfo == null
                || State.DISCONNECTED.equals(egressInfo.getState());
        final boolean egressChanged = egressProp == null
                || !TextUtils.equals(mAcceptedEgressIface, egressProp.getInterfaceName());

        final String egressTypeName = (egressInfo == null) ?
                null : ConnectivityManager.getNetworkTypeName(egressInfo.getType());
        final String egressIface = (egressProp == null) ?
                null : egressProp.getInterfaceName();
        Slog.d(TAG, "handleStateChanged: egress=" + egressTypeName +
                " " + mAcceptedEgressIface + "->" + egressIface);

        if (egressDisconnected || egressChanged) {
            clearSourceRulesLocked();
            mAcceptedEgressIface = null;
            mVpn.stopLegacyVpnPrivileged();
        }
        if (egressDisconnected) {
            hideNotification();
            return;
        }

        final int egressType = egressInfo.getType();
        if (vpnInfo.getDetailedState() == DetailedState.FAILED) {
            EventLogTags.writeLockdownVpnError(egressType);
        }

        if (mErrorCount > MAX_ERROR_COUNT) {
            showNotification(R.string.vpn_lockdown_error, R.drawable.vpn_disconnected);

        } else if (egressInfo.isConnected() && !vpnInfo.isConnectedOrConnecting()) {
            if (mProfile.isValidLockdownProfile()) {
                Slog.d(TAG, "Active network connected; starting VPN");
                EventLogTags.writeLockdownVpnConnecting(egressType);
                showNotification(R.string.vpn_lockdown_connecting, R.drawable.vpn_disconnected);

                mAcceptedEgressIface = egressProp.getInterfaceName();
                try {
                    // Use the privileged method because Lockdown VPN is initiated by the system, so
                    // no additional permission checks are necessary.
                    mVpn.startLegacyVpnPrivileged(mProfile, KeyStore.getInstance(), egressProp);
                } catch (IllegalStateException e) {
                    mAcceptedEgressIface = null;
                    Slog.e(TAG, "Failed to start VPN", e);
                    showNotification(R.string.vpn_lockdown_error, R.drawable.vpn_disconnected);
                }
            } else {
                Slog.e(TAG, "Invalid VPN profile; requires IP-based server and DNS");
                showNotification(R.string.vpn_lockdown_error, R.drawable.vpn_disconnected);
            }

        } else if (vpnInfo.isConnected() && vpnConfig != null) {
            final String iface = vpnConfig.interfaze;
            final List<LinkAddress> sourceAddrs = vpnConfig.addresses;

            if (TextUtils.equals(iface, mAcceptedIface)
                  && sourceAddrs.equals(mAcceptedSourceAddr)) {
                return;
            }

            Slog.d(TAG, "VPN connected using iface=" + iface +
                    ", sourceAddr=" + sourceAddrs.toString());
            EventLogTags.writeLockdownVpnConnected(egressType);
            showNotification(R.string.vpn_lockdown_connected, R.drawable.vpn_connected);

            try {
                clearSourceRulesLocked();

                mNetService.setFirewallInterfaceRule(iface, true);
                for (LinkAddress addr : sourceAddrs) {
                    setFirewallEgressSourceRule(addr, true);
                }

                mNetService.setFirewallUidRule(FIREWALL_CHAIN_NONE, ROOT_UID, FIREWALL_RULE_ALLOW);
                mNetService.setFirewallUidRule(FIREWALL_CHAIN_NONE, Os.getuid(), FIREWALL_RULE_ALLOW);

                mErrorCount = 0;
                mAcceptedIface = iface;
                mAcceptedSourceAddr = sourceAddrs;
            } catch (RemoteException e) {
                throw new RuntimeException("Problem setting firewall rules", e);
            }

            final NetworkInfo clone = new NetworkInfo(egressInfo);
            augmentNetworkInfo(clone);
            mConnService.sendConnectedBroadcast(clone);
        }
    }

    public void init() {
        synchronized (mStateLock) {
            initLocked();
        }
    }

    private void initLocked() {
        Slog.d(TAG, "initLocked()");

        mVpn.setEnableTeardown(false);

        final IntentFilter resetFilter = new IntentFilter(ACTION_LOCKDOWN_RESET);
        mContext.registerReceiver(mResetReceiver, resetFilter, CONNECTIVITY_INTERNAL, null);

        ///M:  To handle keystore reset
        final IntentFilter keystoreResetFilter = new IntentFilter(ACTION_KEYSTORE_RESET);
        keystoreResetFilter.addAction(ACTION_KEYSTORE_RESET);
        mContext.registerReceiver(mKeystoreResetReceiver, keystoreResetFilter);

        try {
            // TODO: support non-standard port numbers
            mNetService.setFirewallEgressDestRule(mProfile.server, 500, true);
            mNetService.setFirewallEgressDestRule(mProfile.server, 4500, true);
            mNetService.setFirewallEgressDestRule(mProfile.server, 1701, true);


            ///M: Support PPTP for VPN  @{
            if (mProfile.type == VpnProfile.TYPE_PPTP) {
                mNetService.setFirewallEgressDestRule(mProfile.server, 1723, true);
                mNetService.setFirewallEgressProtoRule("gre", true);
            }
            ///@}
        } catch (RemoteException e) {
            throw new RuntimeException("Problem setting firewall rules", e);
        }

        handleStateChangedLocked();
    }

    public void shutdown() {
        synchronized (mStateLock) {
            shutdownLocked();
        }
    }

    private void shutdownLocked() {
        Slog.d(TAG, "shutdownLocked()");

        mAcceptedEgressIface = null;
        mErrorCount = 0;

        mVpn.stopLegacyVpnPrivileged();
        try {
            mNetService.setFirewallEgressDestRule(mProfile.server, 500, false);
            mNetService.setFirewallEgressDestRule(mProfile.server, 4500, false);
            mNetService.setFirewallEgressDestRule(mProfile.server, 1701, false);

            ///M: Support PPTP for VPN @{
            if (mProfile.type == VpnProfile.TYPE_PPTP) {
                mNetService.setFirewallEgressDestRule(mProfile.server, 1723, false);
                mNetService.setFirewallEgressProtoRule("gre", false);
            }
            ///@}
        } catch (RemoteException e) {
            throw new RuntimeException("Problem setting firewall rules", e);
        }
        clearSourceRulesLocked();
        hideNotification();

        mContext.unregisterReceiver(mResetReceiver);
        ///M:  To handle keystore reset
        mContext.unregisterReceiver(mKeystoreResetReceiver);
        mVpn.setEnableTeardown(true);
    }

    public void reset() {
        Slog.d(TAG, "reset()");
        synchronized (mStateLock) {
            // cycle tracker, reset error count, and trigger retry
            shutdownLocked();
            initLocked();
            handleStateChangedLocked();
        }
    }

    private void clearSourceRulesLocked() {
        try {
            if (mAcceptedIface != null) {
                mNetService.setFirewallInterfaceRule(mAcceptedIface, false);
                mAcceptedIface = null;
            }
            if (mAcceptedSourceAddr != null) {
                for (LinkAddress addr : mAcceptedSourceAddr) {
                    setFirewallEgressSourceRule(addr, false);
                }

                mNetService.setFirewallUidRule(FIREWALL_CHAIN_NONE, ROOT_UID, FIREWALL_RULE_DEFAULT);
                mNetService.setFirewallUidRule(FIREWALL_CHAIN_NONE,Os.getuid(), FIREWALL_RULE_DEFAULT);

                mAcceptedSourceAddr = null;
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Problem setting firewall rules", e);
        }
    }

    private void setFirewallEgressSourceRule(
            LinkAddress address, boolean allow) throws RemoteException {
        // Our source address based firewall rules must only cover our own source address, not the
        // whole subnet
        final String addrString = address.getAddress().getHostAddress();
        mNetService.setFirewallEgressSourceRule(addrString, allow);
    }

    public void onNetworkInfoChanged() {
        synchronized (mStateLock) {
            handleStateChangedLocked();
        }
    }

    public void onVpnStateChanged(NetworkInfo info) {
        if (info.getDetailedState() == DetailedState.FAILED) {
            mErrorCount++;
        }
        synchronized (mStateLock) {
            handleStateChangedLocked();
        }
    }

    public void augmentNetworkInfo(NetworkInfo info) {
        if (info.isConnected()) {
            final NetworkInfo vpnInfo = mVpn.getNetworkInfo();
            info.setDetailedState(vpnInfo.getDetailedState(), vpnInfo.getReason(), null);
        }
    }

    private void showNotification(int titleRes, int iconRes) {
        final Notification.Builder builder = new Notification.Builder(mContext)
                .setWhen(0)
                .setSmallIcon(iconRes)
                .setContentTitle(mContext.getString(titleRes))
                .setContentText(mContext.getString(R.string.vpn_lockdown_config))
                .setContentIntent(mConfigIntent)
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .addAction(R.drawable.ic_menu_refresh, mContext.getString(R.string.reset),
                        mResetIntent)
                .setColor(mContext.getColor(
                        com.android.internal.R.color.system_notification_accent_color));

        NotificationManager.from(mContext).notify(TAG, 0, builder.build());
    }

    private void hideNotification() {
        NotificationManager.from(mContext).cancel(TAG, 0);
    }
}
