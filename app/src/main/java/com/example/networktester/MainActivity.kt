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

    private lateinit var tvPeticionesTotales: TextView
    private lateinit var tvDatosConsumidos: TextView
    private lateinit var rgIntensidad: RadioGroup
    private lateinit var etCorreo: EditText
    private lateinit var etHora: EditText
    private lateinit var btnIniciar: Button
    private lateinit var btnDetener: Button
    private lateinit var tvConsole: TextView

    private val metricsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val udp = intent?.getLongExtra("TOTAL_UDP", 0L) ?: 0L
            val post = intent?.getLongExtra("TOTAL_POST", 0L) ?: 0L
            val bytes = intent?.getLongExtra("TOTAL_BYTES", 0L) ?: 0L

            val totalPeticiones = udp + post
            val mb = bytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0

            tvPeticionesTotales.text = "Peticiones Totales: $totalPeticiones (UDP: $udp | POST: $post)"
            tvDatosConsumidos.text = String.format("Datos Consumidos: %.2f MB (%.3f GB)", mb, gb)
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mensaje = intent?.getStringExtra("LOG_MESSAGE") ?: return
            val textoActual = tvConsole.text.toString()
            val lineas = textoActual.lines()
            
            // Mantener un historial fluido de hasta 30 líneas en pantalla
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

        tvPeticionesTotales = findViewById(R.id.tvPeticionesTotales)
        tvDatosConsumidos = findViewById(R.id.tvDatosConsumidos)
        rgIntensidad = findViewById(R.id.rgIntensidad)
        etCorreo = findViewById(R.id.etCorreo)
        etHora = findViewById(R.id.etHora)
        btnIniciar = findViewById(R.id.btnIniciar)
        btnDetener = findViewById(R.id.btnDetener)
        tvConsole = findViewById(R.id.tvConsole)

        btnIniciar.setOnClickListener {
            val multiplicador = when (rgIntensidad.checkedRadioButtonId) {
                R.id.rbDoble -> 2
                R.id.rbFullBurst -> 4
                else -> 1
            }

            val serviceIntent = Intent(this, NetworkService::class.java).apply {
                putExtra("MULTIPLICADOR", multiplicador)
                putExtra("CORREO", etCorreo.text.toString())
                putExtra("HORA", etHora.text.toString())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        btnDetener.setOnClickListener {
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
            // Ignorar si no estaban registrados
        }
    }
}
