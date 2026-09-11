package com.example.networktester

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvLogs: TextView
    private lateinit var scrollView: ScrollView

    // Receptor de mensajes enviados por el servicio
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mensaje = intent?.getStringExtra("LOG_MESSAGE") ?: return
            tvLogs.append("$mensaje\n")
            
            // Hace scroll automático hacia el final del texto
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        tvLogs = findViewById(R.id.tvLogs)
        scrollView = findViewById(R.id.scrollView)

        btnStart.setOnClickListener {
            val intent = Intent(this, NetworkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, NetworkService::class.java)
            stopService(intent)
            tvLogs.append("\n[SISTEMA] Pruebas deteniéndose...\n")
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.example.networktester.LOG_EVENT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(logReceiver)
    }
}
