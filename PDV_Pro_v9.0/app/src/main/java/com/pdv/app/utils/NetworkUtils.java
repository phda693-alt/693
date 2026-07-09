package com.pdv.app.utils;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

/** Utilitarios de rede sem dependencia de APIs restritas do Android. */
public final class NetworkUtils {
    private NetworkUtils() {}

    public static String getLocalIpv4() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Nao conectado";
    }
}
