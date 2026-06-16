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

/**
 * Foreground-Service: pollt den Server, druckt offene Bons per ESC/POS über TCP,
 * spielt einen Ton und sendet Bestell-Updates an die Übersicht (MainActivity).
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
                status("⚠ Server nicht erreichbar")
            }
            try { Thread.sleep(4000) } catch (e: InterruptedException) { break }
        }
    }

    private fun handleJob(job: JSONObject, ip: String, port: Int, server: String, token: String) {
        val id = job.getInt("id")
        if (!seen.add(id)) return

        val orderId = job.optInt("order_id")
        val type = job.optString("type")
        val total = job.optString("total")
        val name = job.optString("name")
        val time = job.optString("time")

        broadcastOrder(orderId, type, total, name, time, "neu")
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
        } catch (e: Exception) {
            err = e.message ?: "Druckfehler"
        }

        broadcastOrder(orderId, type, total, name, time, if (ok) "gedruckt" else "fehler")

        try {
            httpPost(
                "$server/ug/druck/agent/done",
                "id=$id&success=$ok&token=$token&error=" + URLEncoder.encode(err, "UTF-8")
            )
        } catch (e: Exception) { /* nächster Poll holt ihn ggf. erneut */ }
        if (ok) seen.remove(id)
    }

    private fun broadcastOrder(orderId: Int, type: String, total: String, name: String, time: String, status: String) {
        sendBroadcast(
            Intent("de.myurfa.druck.ORDER").setPackage(packageName)
                .putExtra("orderId", orderId)
                .putExtra("type", type)
                .putExtra("total", total)
                .putExtra("name", name)
                .putExtra("time", time)
                .putExtra("status", status)
        )
    }

    private fun status(msg: String) {
        sendBroadcast(Intent("de.myurfa.druck.STATUS").setPackage(packageName).putExtra("status", "Status: $msg"))
    }

    private fun alarm() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 800)
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
