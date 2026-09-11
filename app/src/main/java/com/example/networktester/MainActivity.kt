package com.example.networktester

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvLogs: TextView
    private lateinit var scrollView: ScrollView

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mensaje = intent?.getStringExtra("LOG_MESSAGE") ?: return
            tvLogs.append("$mensaje\n")
            
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Solicitar permiso de notificaciones dinámicamente en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, 
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 
                    101
                )
            }
        }

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
            tvLogs.append("\n[SISTEMA] Iniciando servicio de pruebas...\n")
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
        // Compatibilidad de registro Broadcast para distintas versiones de Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logReceiver)
        } catch (e: Exception) { }
    }
}
