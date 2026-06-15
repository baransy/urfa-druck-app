package de.myurfa.druck

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground-Service: pollt den Server im Sekundentakt nach offenen Bons,
 * druckt sie direkt an die Drucker-IP (ESC/POS über TCP), spielt einen lauten
 * Ton und meldet das Ergebnis zurück.
 */
class PollService : Service() {

    @Volatile private var running = false
    private var worker: Thread? = null
    private val seen = HashSet<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true
        startForeground(1, buildNotification())
        worker = Thread { loop() }.also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        super.onDestroy()
    }

    private fun loop() {
        val p = getSharedPreferences("ug", MODE_PRIVATE)
        while (running) {
            try {
                val server = (p.getString("server", "") ?: "").trimEnd('/')
                val token = p.getString("token", "") ?: ""
                val ip = p.getString("ip", "") ?: ""
                val port = p.getInt("port", 9100)
                if (server.isNotEmpty() && token.isNotEmpty()) {
                    val resp = httpGet("$server/ug/druck/agent?token=$token")
                    val jobs = JSONObject(resp).optJSONArray("jobs")
                    if (jobs != null) {
                        for (i in 0 until jobs.length()) {
                            handleJob(jobs.getJSONObject(i), ip, port, server, token)
                        }
                    }
                }
            } catch (e: Exception) {
                log("Server nicht erreichbar: ${e.message}")
            }
            try { Thread.sleep(4000) } catch (e: InterruptedException) { break }
        }
    }

    private fun handleJob(job: JSONObject, ip: String, port: Int, server: String, token: String) {
        val id = job.getInt("id")
        if (!seen.add(id)) return  // im selben Lauf nicht doppelt
        log("🔔 Neue Bestellung #${job.optInt("order_id")} · ${job.optString("type")} · ${job.optString("total")} · ${job.optString("name")}")
        alarm()

        var ok = false
        var err = ""
        try {
            val bytes = Base64.decode(job.getString("escpos"), Base64.DEFAULT)
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), 5000)
                val out: OutputStream = s.getOutputStream()
                out.write(bytes)
                out.flush()
            }
            ok = true
            log("   → gedruckt ✓")
        } catch (e: Exception) {
            err = e.message ?: "Druckfehler"
            log("   → DRUCKFEHLER: $err")
        }
        try {
            httpPost(
                "$server/ug/druck/agent/done",
                "id=$id&success=$ok&token=$token&error=" + URLEncoder.encode(err, "UTF-8")
            )
        } catch (e: Exception) { /* nächster Poll holt ihn ggf. erneut */ }
        if (ok) seen.remove(id) // erledigt -> Speicher freigeben
    }

    private fun alarm() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
        } catch (e: Exception) {}
    }

    private fun httpGet(urlStr: String): String {
        val c = URL(urlStr).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun httpPost(urlStr: String, body: String): String {
        val c = URL(urlStr).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(body.toByteArray()) }
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.GERMAN).format(Date())
        sendBroadcast(
            Intent("de.myurfa.druck.LOG").setPackage(packageName).putExtra("msg", "$time  $msg")
        )
    }

    private fun buildNotification(): Notification {
        val chId = "ug_druck"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(chId, "Druck-Agent", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, chId)
            .setContentTitle("Urfa Druck aktiv")
            .setContentText("Wartet auf Bestellungen…")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .build()
    }
}
