package io.github.jqssun.airplay.audio

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors

/**
 * Sends DACP (Digital Audio Control Protocol) commands back to the AirPlay sender.
 * Resolves the sender's control port via mDNS, then sends HTTP GET requests.
 */
class DacpController(ctx: Context) {

    private val nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val connectivity = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val exec = Executors.newSingleThreadExecutor()

    @Volatile var dacpId = ""
    @Volatile var activeRemote = ""
    @Volatile private var host = ""
    @Volatile private var port = 0

    val ready: Boolean get() = host.isNotEmpty() && activeRemote.isNotEmpty()

    fun update(dacpId: String, activeRemote: String) {
        this.dacpId = dacpId
        this.activeRemote = activeRemote
        _resolve()
    }

    fun play() = _send("/ctrl-int/1/play")
    fun pause() = _send("/ctrl-int/1/pause")
    fun nextItem() = _send("/ctrl-int/1/nextitem")
    fun prevItem() = _send("/ctrl-int/1/previtem")
    fun volumeUp() = _send("/ctrl-int/1/volumeup")
    fun volumeDown() = _send("/ctrl-int/1/volumedown")
    fun beginFastForward() = _send("/ctrl-int/1/beginff")
    fun beginRewind() = _send("/ctrl-int/1/beginrew")
    fun playResume() = _send("/ctrl-int/1/playresume")

    fun reset() {
        dacpId = ""
        activeRemote = ""
        host = ""
        port = 0
    }

    fun release() {
        reset()
        exec.shutdownNow()
    }

    private fun _resolve() {
        if (dacpId.isEmpty()) return
        val serviceName = "iTunes_Ctrl_$dacpId"
        val info = NsdServiceInfo().apply {
            serviceType = "_dacp._tcp"
            this.serviceName = serviceName
        }
        try {
            nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo, code: Int) {
                    Log.w(TAG, "DACP resolve failed: $code")
                }
                override fun onServiceResolved(si: NsdServiceInfo) {
                    val address = si.host ?: return
                    val resolvedHost = address.hostAddress ?: return
                    // Older NSD implementations can omit the zone on link-local IPv6.
                    // These addresses require the local interface, including on wired TVs.
                    host = if (address is Inet6Address && address.isLinkLocalAddress && !resolvedHost.contains('%')) {
                        val iface = connectivity.getLinkProperties(connectivity.activeNetwork)?.interfaceName
                        if (iface == null) {
                            Log.w(TAG, "DACP link-local address has no network interface")
                            return
                        }
                        "$resolvedHost%$iface"
                    } else resolvedHost
                    port = si.port
                    Log.i(TAG, "DACP resolved: $host:$port")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "DACP resolve error", e)
        }
    }

    private fun _send(path: String): ListenableFuture<Unit> {
        val result = SettableFuture.create<Unit>()
        if (!ready) {
            result.setException(IOException("dacp endpoint not resolved"))
            return result
        }
        try {
            exec.execute {
                try {
                    // NSD can return IPv6 on either Ethernet or Wi-Fi. Let URL bracket
                    // the address so its colons cannot be interpreted as the HTTP port.
                    val url = URL("http", host, port, path)
                    val code = if (host.contains('%')) {
                        // Android's HTTP URL parser does not preserve IPv6 zone IDs.
                        // Connect the scoped address directly for link-local senders.
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(InetAddress.getByName(host), port), 2000)
                            socket.soTimeout = 2000
                            val authority = "[${host.substringBefore('%')}]:$port"
                            val request = "GET $path HTTP/1.1\r\nHost: $authority\r\n" +
                                "Active-Remote: $activeRemote\r\nConnection: close\r\n\r\n"
                            socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                            val status = socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                            status?.split(' ')?.getOrNull(1)?.toIntOrNull()
                                ?: throw IOException("Invalid DACP HTTP response")
                        }
                    } else {
                        val conn = url.openConnection() as HttpURLConnection
                        try {
                            conn.requestMethod = "GET"
                            conn.setRequestProperty("Active-Remote", activeRemote)
                            conn.setRequestProperty("Host", url.authority)
                            conn.connectTimeout = 2000
                            conn.readTimeout = 2000
                            conn.responseCode
                        } finally {
                            conn.disconnect()
                        }
                    }
                    if (code in 200..299) {
                        result.set(Unit)
                    } else {
                        Log.w(TAG, "DACP $path -> HTTP $code")
                        result.setException(IOException("HTTP $code"))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DACP send failed: $path (${e.javaClass.simpleName}: ${e.message})", e)
                    result.setException(e)
                }
            }
        } catch (e: Exception) {
            result.setException(e)
        }
        return result
    }

    companion object {
        private const val TAG = "DacpController"
    }
}
