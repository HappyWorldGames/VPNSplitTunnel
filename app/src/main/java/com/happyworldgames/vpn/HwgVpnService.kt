package com.happyworldgames.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Pair
import android.widget.Toast
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HwgVpnService : VpnService(), Handler.Callback {

    companion object {
        private val TAG = HwgVpnService::class.java.simpleName

        val ACTION_CONNECT = "com.happyworldgames.vpn.START"
        val ACTION_DISCONNECT = "com.happyworldgames.vpn.STOP"

        private class Connection(thread: Thread?, pfd: ParcelFileDescriptor?) :
            Pair<Thread?, ParcelFileDescriptor?>(thread, pfd)
    }

    private var mHandler: Handler? = null

    private val mConnectingThread = AtomicReference<Thread?>()
    private val mConnection: AtomicReference<Connection?> = AtomicReference<Connection?>()

    private val mNextConnectionId = AtomicInteger(1)

    private var mConfigureIntent: PendingIntent? = null

    override fun onCreate() {
        // The handler is only used to show messages.
        if (mHandler == null) {
            mHandler = Handler(this)
        }

        // Create the intent to "configure" the connection (just start ToyVpnClient).
        mConfigureIntent = PendingIntent.getActivity(
            this, 0, Intent(
                this,
                MainActivity::class.java
            ),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && ACTION_DISCONNECT == intent.action) {
            disconnect()
            return Service.START_NOT_STICKY
        } else {
            connect()
            return Service.START_STICKY
        }
    }

    override fun onDestroy() {
        disconnect()
    }

    override fun handleMessage(message: Message): Boolean {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show()
        if (message.what != R.string.disconnected) {
            updateForegroundNotification(message.what)
        }
        return true
    }

    private fun connect() {
        // Become a foreground service. Background services can be VPN services too, but they can
        // be killed by background check before getting a chance to receive onRevoke().
        updateForegroundNotification(R.string.connecting)
        mHandler!!.sendEmptyMessage(R.string.connecting)

        // Extract information from the shared preferences.
        val prefs: SharedPreferences =
            getSharedPreferences(MainActivity.Prefs.NAME, Context.MODE_PRIVATE)
        val server = prefs.getString(MainActivity.Prefs.SERVER_ADDRESS, "")
        val secret = prefs.getString(MainActivity.Prefs.SHARED_SECRET, "")!!.toByteArray()
        val allow = prefs.getBoolean(MainActivity.Prefs.ALLOW, true)
        val packages =
            prefs.getStringSet(MainActivity.Prefs.PACKAGES, emptySet<String>())
        val port = prefs.getInt(MainActivity.Prefs.SERVER_PORT, 0)
        val proxyHost = prefs.getString(MainActivity.Prefs.PROXY_HOSTNAME, "")
        val proxyPort = prefs.getInt(MainActivity.Prefs.PROXY_PORT, 0)
        startConnection(
            HwgVpnConnection(
                this, mNextConnectionId.getAndIncrement(), server, port, secret,
                proxyHost, proxyPort, allow, packages
            )
        )
    }

    private fun startConnection(connection: HwgVpnConnection) {
        // Replace any existing connecting thread with the  new one.
        val thread = Thread(connection, "ToyVpnThread")
        setConnectingThread(thread)

        // Handler to mark as connected once onEstablish is called.
        connection.setConfigureIntent(mConfigureIntent)
        connection.setOnEstablishListener(listener = object: HwgVpnConnection.OnEstablishListener{
            override fun onEstablish(tunInterface: ParcelFileDescriptor?) {
                mHandler!!.sendEmptyMessage(R.string.connected)
                mConnectingThread.compareAndSet(thread, null)
                setConnection(Connection(thread, tunInterface))
            }
        })
        thread.start()
    }

    private fun setConnectingThread(thread: Thread?) {
        val oldThread = mConnectingThread.getAndSet(thread)
        oldThread?.interrupt()
    }

    private fun setConnection(connection: Connection?) {
        val oldConnection: Connection? = mConnection.getAndSet(connection)
        if (oldConnection != null) {
            try {
                oldConnection.first?.interrupt()
                oldConnection.second?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Closing VPN interface", e)
            }
        }
    }

    private fun disconnect() {
        mHandler!!.sendEmptyMessage(R.string.disconnected)
        setConnectingThread(null)
        setConnection(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateForegroundNotification(message: Int) {
        val NOTIFICATION_CHANNEL_ID = "HwgVpn"
        val mNotificationManager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        mNotificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        startForeground(
            1, Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.vpn_key_24px)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build()
        )
    }
}
