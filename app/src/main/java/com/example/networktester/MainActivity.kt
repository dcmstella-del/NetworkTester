package com.example.networktester

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvLogs: TextView
    private lateinit var tvTotalPeticiones: TextView
    private lateinit var tvTotalDatos: TextView
    private lateinit var scrollViewLogs: ScrollView
    private lateinit var rgIntensidad: RadioGroup
    private lateinit var etEmail: EditText
    private lateinit var etHoraEnvio: EditText

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.networktester.LOG_EVENT" -> {
                    val mensaje = intent.getStringExtra("LOG_MESSAGE") ?: return
                    tvLogs.append("$mensaje\n")
                    scrollViewLogs.post { scrollViewLogs.fullScroll(ScrollView.FOCUS_DOWN) }
                }
                "com.example.networktester.METRICS_EVENT" -> {
                    val udp = intent.getLongExtra("TOTAL_UDP", 0)
                    val post = intent.getLongExtra("TOTAL_POST", 0)
                    val bytes = intent.getLongExtra("TOTAL_BYTES", 0)

                    val mb = bytes / (1024.0 * 1024.0)
                    val gb = mb / 1024.0

                    tvTotalPeticiones.text = "Peticiones Totales: ${udp + post} (UDP: $udp | POST: $post)"
                    tvTotalDatos.text = String.format("Datos Consumidos: %.2f MB (%.3f GB)", mb, gb)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        tvLogs = findViewById(R.id.tvLogs)
        tvTotalPeticiones = findViewById(R.id.tvTotalPeticiones)
        tvTotalDatos = findViewById(R.id.tvTotalDatos)
        scrollViewLogs = findViewById(R.id.scrollViewLogs)
        rgIntensidad = findViewById(R.id.rgIntensidad)
        etEmail = findViewById(R.id.etEmail)
        etHoraEnvio = findViewById(R.id.etHoraEnvio)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        btnStart.setOnClickListener {
            val multiplicador = when (rgIntensidad.checkedRadioButtonId) {
                R.id.rbDoble -> 2
                R.id.rbFull -> 4
                else -> 1
            }

            val correo = etEmail.text.toString().trim()
            val hora = etHoraEnvio.text.toString().trim().ifEmpty { "23:00" }

            val intent = Intent(this, NetworkService::class.java).apply {
                putExtra("MULTIPLICADOR", multiplicador)
                putExtra("CORREO", correo)
                putExtra("HORA", hora)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tvLogs.append("\n[SISTEMA] Servicio iniciado en modo x$multiplicador. Reporte programado a las $hora...\n")
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, NetworkService::class.java))
            tvLogs.append("\n[SISTEMA] Pruebas deteniéndose...\n")
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("com.example.networktester.LOG_EVENT")
            addAction("com.example.networktester.METRICS_EVENT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(dataReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(dataReceiver) } catch (e: Exception) { }
    }
}
