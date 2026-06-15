package de.myurfa.druck

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var logView: TextView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val msg = i?.getStringExtra("msg") ?: return
            runOnUiThread {
                val cur = logView.text.toString()
                logView.text = msg + "\n" + cur.take(6000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("ug", MODE_PRIVATE)

        val server = findViewById<EditText>(R.id.server)
        val token = findViewById<EditText>(R.id.token)
        val ip = findViewById<EditText>(R.id.ip)
        val port = findViewById<EditText>(R.id.port)
        val status = findViewById<TextView>(R.id.status)
        logView = findViewById(R.id.log)

        server.setText(prefs.getString("server", "https://myurfa.de"))
        token.setText(prefs.getString("token", ""))
        ip.setText(prefs.getString("ip", "192.168.178.49"))
        port.setText(prefs.getInt("port", 9100).toString())

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            prefs.edit()
                .putString("server", server.text.toString().trim().trimEnd('/'))
                .putString("token", token.text.toString().trim())
                .putString("ip", ip.text.toString().trim())
                .putInt("port", port.text.toString().trim().toIntOrNull() ?: 9100)
                .apply()
            ContextCompat.startForegroundService(this, Intent(this, PollService::class.java))
            status.text = "Status: Läuft ✓"
        }
        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, PollService::class.java))
            status.text = "Status: Gestoppt"
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter("de.myurfa.druck.LOG"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
    }
}
