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

    private lateinit var tvDeviceId: TextView
    private lateinit var tvPeticiones: TextView
    private lateinit var tvConsumo: TextView
    private lateinit var tvLogConsole: TextView
    private lateinit var tvEstadoSheets: TextView
    private lateinit var chkAutoSheets: CheckBox
    private lateinit var scrollConsole: ScrollView

    private lateinit var rgIntensidad: RadioGroup
    private lateinit var rbNormal: RadioButton
    private lateinit var rbDoble: RadioButton
    private lateinit var rbFull: RadioButton
    private lateinit var rbRequestBurst: RadioButton
    private lateinit var rbAutoCycle: RadioButton

    private lateinit var btnIniciar: Button
    private lateinit var btnDetener: Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.networktester.METRICS_EVENT" -> {
                    val udp = intent.getLongExtra("TOTAL_UDP", 0)
                    val post = intent.getLongExtra("TOTAL_POST", 0)
                    val bytes = intent.getLongExtra("TOTAL_BYTES", 0)
                    val deviceId = intent.getStringExtra("DEVICE_ID") ?: ""

                    val totalPeticiones = udp + post
                    val mb = bytes / (1024.0 * 1024.0)
                    val gb = mb / 1024.0

                    tvDeviceId.text = "Dispositivo: $deviceId"
                    tvPeticiones.text = "Peticiones Totales: $totalPeticiones (UDP: $udp | POST/Ping: $post)"
                    tvConsumo.text = String.format("Consumo: %.2f MB (%.3f GB)", mb, gb)
                }
                "com.example.networktester.LOG_EVENT" -> {
                    val log = intent.getStringExtra("LOG_MESSAGE") ?: ""
                    tvLogConsole.append("$log\n")
                    scrollConsole.post { scrollConsole.fullScroll(ScrollView.FOCUS_DOWN) }
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
        tvLogConsole = findViewById(R.id.tvLogConsole)
        tvEstadoSheets = findViewById(R.id.tvEstadoSheets)
        chkAutoSheets = findViewById(R.id.chkAutoSheets)
        scrollConsole = findViewById(R.id.scrollConsole)

        rgIntensidad = findViewById(R.id.rgIntensidad)
        rbNormal = findViewById(R.id.rbNormal)
        rbDoble = findViewById(R.id.rbDoble)
        rbFull = findViewById(R.id.rbFull)
        rbRequestBurst = findViewById(R.id.rbRequestBurst)
        rbAutoCycle = findViewById(R.id.rbAutoCycle)

        btnIniciar = findViewById(R.id.btnIniciar)
        btnDetener = findViewById(R.id.btnDetener)

        btnIniciar.setOnClickListener {
            val modoSelected = when {
                rbAutoCycle.isChecked -> "AUTO_CYCLE"
                rbRequestBurst.isChecked -> "REQUEST_BURST"
                rbFull.isChecked -> "FULL_BURST"
                rbDoble.isChecked -> "DOBLE"
                else -> "NORMAL"
            }

            val serviceIntent = Intent(this, NetworkService::class.java).apply {
                putExtra("MODO_OPERACION", modoSelected)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            chkAutoSheets.isChecked = true
            tvEstadoSheets.text = "Sincronización a Google Sheets: TRANSMITIENDO (Cada 1 min)"
            tvEstadoSheets.setTextColor(android.graphics.Color.parseColor("#00FF66"))
            Toast.makeText(this, "Pruebas iniciadas en modo $modoSelected", Toast.LENGTH_SHORT).show()
        }

        btnDetener.setOnClickListener {
            val serviceIntent = Intent(this, NetworkService::class.java)
            stopService(serviceIntent)

            tvEstadoSheets.text = "Sincronización a Google Sheets: DETENIDA"
            tvEstadoSheets.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            Toast.makeText(this, "Pruebas detenidas.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("com.example.networktester.METRICS_EVENT")
            addAction("com.example.networktester.LOG_EVENT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}
