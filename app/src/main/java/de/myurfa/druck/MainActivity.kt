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
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: OrderAdapter
    private lateinit var overviewView: View
    private lateinit var settingsView: View
    private lateinit var emptyState: View
    private lateinit var statusView: TextView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            i ?: return
            when (i.action) {
                "de.myurfa.druck.ORDER" -> {
                    val o = Order(
                        i.getIntExtra("orderId", 0),
                        i.getStringExtra("type") ?: "",
                        i.getStringExtra("total") ?: "",
                        i.getStringExtra("name") ?: "",
                        i.getStringExtra("time") ?: "",
                        i.getStringExtra("status") ?: "neu"
                    )
                    runOnUiThread {
                        adapter.addOrUpdate(o)
                        emptyState.visibility = if (adapter.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                "de.myurfa.druck.STATUS" -> {
                    val s = i.getStringExtra("status") ?: ""
                    runOnUiThread { statusView.text = s }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        prefs = getSharedPreferences("ug", MODE_PRIVATE)

        overviewView = findViewById(R.id.overviewView)
        settingsView = findViewById(R.id.settingsView)
        emptyState = findViewById(R.id.emptyState)
        statusView = findViewById(R.id.status)

        val list = findViewById<RecyclerView>(R.id.orderList)
        adapter = OrderAdapter()
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val server = findViewById<EditText>(R.id.server)
        val token = findViewById<EditText>(R.id.token)
        val ip = findViewById<EditText>(R.id.ip)
        val port = findViewById<EditText>(R.id.port)

        server.setText(prefs.getString("server", "https://myurfa.de"))
        token.setText(prefs.getString("token", ""))
        ip.setText(prefs.getString("ip", "192.168.178.49"))
        port.setText(prefs.getInt("port", 9100).toString())

        findViewById<View>(R.id.startBtn).setOnClickListener {
            prefs.edit()
                .putString("server", server.text.toString().trim().trimEnd('/'))
                .putString("token", token.text.toString().trim())
                .putString("ip", ip.text.toString().trim())
                .putInt("port", port.text.toString().trim().toIntOrNull() ?: 9100)
                .apply()
            ContextCompat.startForegroundService(this, Intent(this, PollService::class.java))
            statusView.text = "Status: Läuft ✓"
        }
        findViewById<View>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, PollService::class.java))
            statusView.text = "Status: Gestoppt"
        }

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_overview -> { showOverview(true); true }
                R.id.nav_settings -> { showOverview(false); true }
                else -> false
            }
        }
        nav.selectedItemId = R.id.nav_overview
        showOverview(true)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun showOverview(show: Boolean) {
        overviewView.visibility = if (show) View.VISIBLE else View.GONE
        settingsView.visibility = if (show) View.GONE else View.VISIBLE
        supportActionBar?.title = if (show) "Urfa Druck — Übersicht" else "Urfa Druck — Einstellungen"
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("de.myurfa.druck.ORDER")
            addAction("de.myurfa.druck.STATUS")
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
    }
}
