package com.example.networktester

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvDeviceId: TextView
    private lateinit var tvPeticiones: TextView
    private lateinit var tvConsumo: TextView
    private lateinit var tvConsole: TextView
    private lateinit var etHoraReporte: EditText
    private lateinit var btnIniciar: Button
    private lateinit var btnEnviarAhora: Button
    private lateinit var btnDetener: Button
    private lateinit var rgIntensidad: RadioGroup

    private val metricsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val udp = it.getLongExtra("TOTAL_UDP", 0)
                val post = it.getLongExtra("TOTAL_POST", 0)
                val bytes = it.getLongExtra("TOTAL_BYTES", 0)
                val deviceId = it.getStringExtra("DEVICE_ID") ?: "UNKNOWN"

                val mb = bytes / (1024.0 * 1024.0)
                val gb = mb / 1024.0

                tvDeviceId.text = "Dispositivo: $deviceId"
                tvPeticiones.text = "Peticiones Totales: ${udp + post} (UDP: $udp | POST/Ping: $post)"
                tvConsumo.text = String.format(Locale.US, "Consumo: %.2f MB (%.3f GB)", mb, gb)
            }
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("LOG_MESSAGE")?.let { mensaje ->
                tvConsole.append("\n$mensaje")
                val scrollAmount = tvConsole.layout?.getLineTop(tvConsole.lineCount) ?: 0
                if (scrollAmount > tvConsole.height) {
                    tvConsole.scrollTo(0, scrollAmount - tvConsole.height)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDeviceId = findViewById(R.id.tvDeviceId)
        tvPeticiones = findViewById(R.id.tvPeticiones)
        tvConsumo = findViewById(R.id.tvConsumo)
        tvConsole = findViewById(R.id.tvConsole)
        etHoraReporte = findViewById(R.id.etHoraReporte)
        btnIniciar = findViewById(R.id.btnIniciar)
        btnEnviarAhora = findViewById(R.id.btnEnviarAhora)
        btnDetener = findViewById(R.id.btnDetener)
        rgIntensidad = findViewById(R.id.rgIntensidad)

        tvConsole.movementMethod = ScrollingMovementMethod()

        etHoraReporte.setText("23:55")

        btnIniciar.setOnClickListener {
            val mult = when (rgIntensidad.checkedRadioButtonId) {
                R.id.rbBurst -> 4
                R.id.rbDoble -> 2
                else -> 1
            }

            val horaProgramada = etHoraReporte.text.toString().trim()

            val serviceIntent = Intent(this, NetworkService::class.java).apply {
                putExtra("MULTIPLICADOR", mult)
                putExtra("HORA_PROGRAMADA", horaProgramada)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Toast.makeText(this, "Pruebas iniciadas. Reporte programado a las $horaProgramada", Toast.LENGTH_SHORT).show()
        }

        btnEnviarAhora.setOnClickListener {
            val serviceIntent = Intent(this, NetworkService::class.java).apply {
                action = "FORZAR_ENVIAR_METRICAS"
            }
            startService(serviceIntent)
            Toast.makeText(this, "Enviando reporte manual a Google Sheets...", Toast.LENGTH_SHORT).show()
        }

        btnDetener.setOnClickListener {
            stopService(Intent(this, NetworkService::class.java))
            Toast.makeText(this, "Pruebas detenidas", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(metricsReceiver, IntentFilter("com.example.networktester.METRICS_EVENT"), Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(logReceiver, IntentFilter("com.example.networktester.LOG_EVENT"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(metricsReceiver, IntentFilter("com.example.networktester.METRICS_EVENT"))
            registerReceiver(logReceiver, IntentFilter("com.example.networktester.LOG_EVENT"))
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(metricsReceiver)
        unregisterReceiver(logReceiver)
    }
}
