package dev.fabik.bluetoothhid.shihab

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import app.shihab.shared.diag.ShihabLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * The PC link (docs/PC-LINK.md): Barcode sends each scan to Barcode Receiver on Windows over the USB cable
 * (127.0.0.1 through `adb reverse`) or Wi-Fi, TLS pinned to the PC's certificate from the pairing QR code, then
 * the pairing key. Scans wait in order until the PC confirms them (ACK) and are sent again after a reconnect.
 */
class PcLink(private val context: Context, private val scope: CoroutineScope) {

    data class Pairing(val pcName: String, val hosts: List<String>, val port: Int, val fingerprint: String, val key: String)

    sealed class State {
        object NotPaired : State()
        data class Connecting(val pcName: String) : State()
        data class Connected(val pcName: String, val cable: Boolean) : State()
    }

    private class Pending(val id: Long, val text: String) {
        @Volatile var sentAt = 0L
    }

    private val prefs = context.getSharedPreferences("pclink", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<State>(if (load() == null) State.NotPaired else State.Connecting(load()!!.pcName))
    val state: StateFlow<State> = _state.asStateFlow()

    private val pending = ArrayDeque<Pending>()   // guarded by `pending`
    private var job: Job? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var socket: SSLSocket? = null

    /** Saves the pairing read from the receiver's QR code; false when it is not one. */
    fun pair(uri: String): Pairing? {
        val p = parse(uri) ?: return null
        prefs.edit().putString("name", p.pcName).putString("hosts", p.hosts.joinToString(","))
            .putInt("port", p.port).putString("fp", p.fingerprint).putString("key", p.key).apply()
        ShihabLog.info("pc_paired", mapOf("hosts" to p.hosts.size))
        if (job != null) { stop(); start() } else _state.value = State.Connecting(p.pcName)
        return p
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        closeSocket()
        _state.value = load()?.let { State.Connecting(it.pcName) } ?: State.NotPaired
    }

    /** Queues a scan (already with its suffix, "\n" for Enter); it is sent now or as soon as the PC is reached. */
    fun send(text: String) {
        val item = synchronized(pending) {
            Pending(nextId(), text).also {
                pending.addLast(it)
                while (pending.size > MAX_PENDING) pending.removeFirst()
            }
        }
        out?.let { o -> scope.launch(Dispatchers.IO) { runCatching { writeScan(o, item) }.onFailure { closeSocket() } } }
    }

    private suspend fun run() {
        var wait = 1000L
        while (scope.isActive && job?.isActive == true) {
            val p = load()
            if (p == null) {
                _state.value = State.NotPaired
                delay(2000)
                continue
            }
            if (_state.value !is State.Connecting) _state.value = State.Connecting(p.pcName)
            val connected = connectAny(p)
            if (connected) wait = 1000L
            delay(wait)
            wait = (wait * 2).coerceAtMost(15_000L)
        }
    }

    /** Cable first (127.0.0.1), then each Wi-Fi address; serves the first that answers until it drops. */
    private fun connectAny(p: Pairing): Boolean {
        val targets = listOf(LOOPBACK) + p.hosts
        for (host in targets) {
            val (s, reader) = try { open(host, p) } catch (e: Exception) { null } ?: continue
            serve(s, reader, p, cable = host == LOOPBACK)
            return true
        }
        return false
    }

    private fun open(host: String, p: Pairing): Pair<SSLSocket, BufferedReader>? {
        val raw = Socket()
        raw.connect(InetSocketAddress(host, p.port), if (host == LOOPBACK) 800 else 2500)
        raw.soTimeout = READ_TIMEOUT_MS
        raw.tcpNoDelay = true
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(PinnedTrust(p.fingerprint)), SecureRandom()) }
        val s = ctx.socketFactory.createSocket(raw, host, p.port, true) as SSLSocket
        s.enabledProtocols = s.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
        s.startHandshake()
        val o = s.outputStream
        write(o, "HELLO 1 ${p.key} ${phoneId()} ${b64(phoneName())}")
        val reader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
        val answer = reader.readLine() ?: run { s.close(); return null }
        if (!answer.startsWith("OK ")) {
            ShihabLog.error(BarcodeErrors.PC_REFUSED.code, "pc refused: ${answer.take(12)}")
            s.close()
            return null
        }
        return s to reader
    }

    private fun serve(s: SSLSocket, reader: BufferedReader, p: Pairing, cable: Boolean) {
        socket = s
        val o = s.outputStream
        out = o
        _state.value = State.Connected(p.pcName, cable)
        ShihabLog.info("pc_connected", mapOf("cable" to cable))
        val pinger = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(PING_MS)
                runCatching { write(o, "PING") }.onFailure { closeSocket() }
                // A scan the PC has not confirmed for a while: reconnect, it is sent again.
                val oldest = synchronized(pending) { pending.firstOrNull() }
                if (oldest != null && oldest.sentAt > 0 && System.currentTimeMillis() - oldest.sentAt > ACK_TIMEOUT_MS) closeSocket()
            }
        }
        try {
            synchronized(pending) { pending.toList() }.forEach { writeScan(o, it) }
            while (true) {
                val line = reader.readLine() ?: break
                val parts = line.split(' ')
                when (parts[0]) {
                    "ACK", "ERR" -> {
                        val id = parts.getOrNull(1)?.toLongOrNull() ?: continue
                        synchronized(pending) { pending.removeAll { it.id == id } }
                        if (parts[0] == "ERR") ShihabLog.error(BarcodeErrors.PC_NOT_TYPED.code, "pc: ${parts.getOrNull(2)}")
                    }
                }
            }
        } catch (e: Exception) {
            ShihabLog.info("pc_link_closed", mapOf("reason" to e.javaClass.simpleName))
        } finally {
            pinger.cancel()
            closeSocket()
            _state.value = State.Connecting(p.pcName)
        }
    }

    private fun closeSocket() {
        out = null
        socket?.let { runCatching { it.close() } }
        socket = null
    }

    private fun write(o: OutputStream, line: String) = synchronized(o) {
        o.write((line + "\n").toByteArray(Charsets.UTF_8))
        o.flush()
    }

    private fun writeScan(o: OutputStream, p: Pending) {
        p.sentAt = System.currentTimeMillis()
        write(o, "SCAN ${p.id} ${b64(p.text)}")
    }

    private fun nextId(): Long {
        val id = prefs.getLong("next_id", 1L)
        prefs.edit().putLong("next_id", id + 1).apply()
        return id
    }

    private fun phoneId(): String = prefs.getString("phone_id", null) ?: ByteArray(8).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }.also { prefs.edit().putString("phone_id", it).apply() }

    private fun phoneName(): String =
        runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    fun load(): Pairing? {
        val fp = prefs.getString("fp", null) ?: return null
        return Pairing(
            prefs.getString("name", "") ?: "",
            prefs.getString("hosts", "")!!.split(',').filter { it.isNotBlank() },
            prefs.getInt("port", 5870), fp, prefs.getString("key", "") ?: "",
        )
    }

    /** Pins the receiver's certificate: only the one whose SHA-256 is in the QR code is accepted. */
    private class PinnedTrust(private val fingerprint: String) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val cert = chain?.firstOrNull() ?: throw CertificateException("no certificate")
            val fp = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
            if (fp != fingerprint) throw CertificateException("not the paired PC")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            throw CertificateException("client certificates are not used")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        const val PREFIX = "shihabbarcode://pair?"
        private const val LOOPBACK = "127.0.0.1"
        private const val READ_TIMEOUT_MS = 40_000
        private const val PING_MS = 15_000L
        private const val ACK_TIMEOUT_MS = 8_000L
        private const val MAX_PENDING = 200

        fun b64(s: String): String =
            Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        /** The pairing QR code (docs/PC-LINK.md, "Pairing"), or null. */
        fun parse(text: String): Pairing? {
            if (!text.startsWith(PREFIX)) return null
            val uri = Uri.parse(text)
            if (uri.getQueryParameter("v") != "1") return null
            val fp = uri.getQueryParameter("fp")?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return null
            val key = uri.getQueryParameter("k")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{16,64}")) } ?: return null
            val port = uri.getQueryParameter("p")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val hosts = uri.getQueryParameter("h").orEmpty().split(',')
                .filter { it.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) }.take(4)
            return Pairing(uri.getQueryParameter("n").orEmpty().take(64), hosts, port, fp, key)
        }
    }
}
