package com.happyworldgames.vpn

import android.content.Intent
import android.net.VpnService
import android.util.Log
import java.io.IOException
import kotlin.concurrent.thread

class HwgVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.happyworldgames.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.happyworldgames.vpn.ACTION_DISCONNECT"
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startVpn()
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {

    }

    private fun stopVpn() {

    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
