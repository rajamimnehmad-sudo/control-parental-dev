package com.glosh.remote.spike.guide.autopilot;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.glosh.remote.spike.RemotePairingService;
import com.glosh.remote.spike.session.SessionState;

public final class AutopilotCapabilityProbe {
    public boolean supportConnected() {
        return RemotePairingService.getSessionState() == SessionState.CONNECTED;
    }

    public boolean wifiReady(Context context) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) {
            return false;
        }
        Network active = manager.getActiveNetwork();
        NetworkCapabilities capabilities = active == null
                ? null
                : manager.getNetworkCapabilities(active);
        return capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
