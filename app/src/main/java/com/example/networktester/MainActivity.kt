package com.example.networktester

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvMetrics: TextView
    private lateinit var rgIntensity: RadioGroup
    private lateinit var etEmail: EditText
    private lateinit var etReportTime: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvConsole: TextView

    private val metricsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val udp = intent?.getLongExtra("TOTAL_UDP", 0L) ?: 0L
            val post = intent?.getLongExtra("TOTAL_POST", 0L) ?: 0L
            val bytes = intent?.getLongExtra("TOTAL_BYTES", 0L) ?: 0L

            val totalPeticiones = udp + post
            val mb = bytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0

            tvMetrics.text = String.format(
                "Peticiones Totales: %d (UDP: %d | POST: %d)\nDatos Consumidos: %.2f MB (%.3f GB)",
                totalPeticiones, udp, post, mb, gb
            )
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mensaje = intent?.getStringExtra("LOG_MESSAGE") ?: return
            val textoActual = tvConsole.text.toString()
            val lineas = textoActual.lines()
            
            val nuevoTexto = if (lineas.size > 30) {
                lineas.takeLast(30).joinToString("\n") + "\n" + mensaje
            } else {
                if (textoActual.isEmpty()) mensaje else "$textoActual\n$mensaje"
            }
            tvConsole.text = nuevoTexto
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvMetrics = findViewById(R.id.tvMetrics)
        rgIntensity = findViewById(R.id.rgIntensity)
        etEmail = findViewById(R.id.etEmail)
        etReportTime = findViewById(R.id.etReportTime)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvConsole = findViewById(R.id.tvConsole)

        btnStart.setOnClickListener {
            val multiplicador = when (rgIntensity.checkedRadioButtonId) {
                R.id.rbDouble -> 2
                R.id.rbFull -> 4
                else -> 1
            }

            val serviceIntent = Intent(this, NetworkService::class.java).apply {
                putExtra("MULTIPLICADOR", multiplicador)
                putExtra("CORREO", etEmail.text.toString())
                putExtra("HORA", etReportTime.text.toString())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, NetworkService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val filterMetrics = IntentFilter("com.example.networktester.METRICS_EVENT")
        val filterLog = IntentFilter("com.example.networktester.LOG_EVENT")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(metricsReceiver, filterMetrics, RECEIVER_EXPORTED)
            registerReceiver(logReceiver, filterLog, RECEIVER_EXPORTED)
        } else {
            registerReceiver(metricsReceiver, filterMetrics)
            registerReceiver(logReceiver, filterLog)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(metricsReceiver)
            unregisterReceiver(logReceiver)
        } catch (e: Exception) {
            // Ignorar des-registro previo
        }
    }
}
