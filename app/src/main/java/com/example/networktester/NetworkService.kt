package com.example.networktester

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class NetworkService : Service() {

    // URL de tu Google Apps Script
    private val URL_GOOGLE_SHEET = "https://script.google.com/macros/s/AKfycbwuBQg4vib23jPfmV8-sRqVA9Qw0MzIV89QE-yQF1jVJsMcppbkrFw9-jaWhUx-GPtt/exec"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val dispatcher = Dispatcher().apply {
        maxRequests = 4000
        maxRequestsPerHost = 1000
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val totalPeticionesUdp = AtomicLong(0)
    private val totalPeticionesPost = AtomicLong(0)
    private val totalPeticionesSpeedTest = AtomicLong(0)
    private val totalBytesDescargados = AtomicLong(0)

    private var multiplicador = 1
    private var deviceId = ""

    override fun onCreate() {
        super.onCreate()
        deviceId = obtenerDeviceId()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accion = intent?.action
        if (accion == "FORZAR_ENVIAR_METRICAS") {
            enviarRegistroAGoogleSheet()
            return START_STICKY
        }

        multiplicador = intent?.getIntExtra("MULTIPLICADOR", 1) ?: 1

        crearCanalNotificacion()
        val notification = NotificationCompat.Builder(this, "NetworkTesterChannel")
            .setContentTitle("NetworkTester - $deviceId")
            .setContentText("Ejecutando pruebas (x$multiplicador)...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        logToUI("🚀 [DISPOSITIVO: $deviceId] Iniciando pruebas en modo Full Burst (x$multiplicador).")

        lanzarRafagasUdp()
        lanzarPeticionesPostCloud()
        lanzarSpeedTestLatencia()
        lanzarDescargasUltraRapidas()
        lanzarSincronizadorDiario()
        lanzarActualizadorUI()

        return START_STICKY
    }

    private fun obtenerDeviceId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        val modelo = Build.MODEL.replace(" ", "_")
        return "${modelo}_${androidId.takeLast(6)}"
    }

    private fun lanzarActualizadorUI() {
        serviceScope.launch {
            while (isActive) {
                actualizarMetricasUI()
                delay(250)
            }
        }
    }

    // 1. RÁFAGAS UDP
    private fun lanzarRafagasUdp() {
        serviceScope.launch {
            val trackerHost = "tracker.opentrackr.org"
            val port = 6969
            val mensajeUdp = "ANNOUNCE_P2P_SIMULATION_PACKET_TEST_BURST"
            val buffer = mensajeUdp.toByteArray()

            val paquetesPorRafaga = when (multiplicador) {
                4 -> 25
                2 -> 10
                else -> 5
            }

            while (isActive) {
                try {
                    val address = InetAddress.getByName(trackerHost)
                    val socket = DatagramSocket()
                    repeat(paquetesPorRafaga) {
                        val packet = DatagramPacket(buffer, buffer.size, address, port)
                        socket.send(packet)
                        totalPeticionesUdp.incrementAndGet()
                    }
                    socket.close()
                } catch (e: Exception) {
                    // Control de socket
                }
                delay(80)
            }
        }
    }

    // 2. PETICIONES POST CLOUD
    private fun lanzarPeticionesPostCloud() {
        serviceScope.launch {
            val urlPost = "https://httpbin.org/post"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val hilosPost = if (multiplicador == 4) 8 else 2

            repeat(hilosPost) {
                serviceScope.launch {
                    while (isActive) {
                        try {
                            val jsonPayload = "{\"device_id\":\"$deviceId\",\"data\":\"" + "A".repeat(4000) + "\"}"
                            val body = jsonPayload.toRequestBody(mediaType)
                            val request = Request.Builder().url(urlPost).post(body).build()

                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    totalPeticionesPost.incrementAndGet()
                                }
                            }
                        } catch (e: Exception) {
                            // Reintento
                        }
                        delay(150)
                    }
                }
            }
        }
    }

    // 3. SPEEDTEST LATENCIA
    private fun lanzarSpeedTestLatencia() {
        serviceScope.launch {
            val pingEndpoints = listOf(
                "https://1.1.1.1/cdn-cgi/trace",
                "https://www.cloudflare.com/cdn-cgi/trace",
                "https://www.google.com/generate_204"
            )

            while (isActive) {
                try {
                    val target = pingEndpoints[(totalPeticionesSpeedTest.get() % pingEndpoints.size).toInt()]
                    val request = Request.Builder().url(target).build()

                    client.newCall(request).execute().use {
                        totalPeticionesSpeedTest.incrementAndGet()
                    }
                } catch (e: Exception) {
                    // Ignorar
                }
                delay(1000)
            }
        }
    }

    // 4. DESCARGAS MASIVAS ULTRA RÁPIDAS
    private fun lanzarDescargasUltraRapidas() {
        val hilos = when (multiplicador) {
            4 -> 64
            2 -> 24
            else -> 10
        }

        val cdnEndpoints = listOf(
            "https://speed.cloudflare.com/__down?bytes=100000000",
            "https://proof.ovh.net/files/100Mb.dat",
            "http://ipv4.download.thinkbroadband.com/100MB.zip"
        )

        repeat(hilos) { hiloId ->
            serviceScope.launch {
                val buffer = ByteArray(2097152) // 2 MB
                
                while (isActive) {
                    try {
                        val targetUrl = cdnEndpoints[hiloId % cdnEndpoints.size]
                        val request = Request.Builder().url(targetUrl).build()

                        client.newCall(request).execute().use { response ->
                            val inputStream: InputStream? = response.body?.byteStream()
                            var bytesRead: Int

                            if (inputStream != null) {
                                while (inputStream.read(buffer).also { bytesRead = it } != -1 && isActive) {
                                    if (bytesRead > 0) {
                                        totalBytesDescargados.addAndGet(bytesRead.toLong())
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        delay(50)
                    }
                }
            }
        }
    }

    private fun lanzarSincronizadorDiario() {
        serviceScope.launch {
            var enviadoHoy = false
            while (isActive) {
                val horaActual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                if (horaActual == "23:55" && !enviadoHoy) {
                    enviarRegistroAGoogleSheet()
                    enviadoHoy = true
                }

                if (horaActual == "00:01") {
                    enviadoHoy = false
                }

                delay(10000)
            }
        }
    }

    private fun enviarRegistroAGoogleSheet() {
        serviceScope.launch {
            try {
                val bytes = totalBytesDescargados.get()
                val udp = totalPeticionesUdp.get()
                val post = totalPeticionesPost.get()
                val speed = totalPeticionesSpeedTest.get()
                val gbTotales = bytes / (1024.0 * 1024.0 * 1024.0)
                val totalPeticiones = udp + post + speed

                val timestampCompleto = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                logToUI("📊 [GOOGLE SHEET] Enviando reporte de $deviceId...")

                val jsonPayload = """
                    {
                        "device_id": "$deviceId",
                        "timestamp": "$timestampCompleto",
                        "gigabytes_consumidos": ${String.format(Locale.US, "%.3f", gbTotales)},
                        "peticiones_totales": $totalPeticiones,
                        "rafagas_udp": $udp,
                        "peticiones_post": $post,
                        "speedtest_ping": $speed,
                        "modo_ejecucion": "x$multiplicador"
                    }
                """.trimIndent()

                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = jsonPayload.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(URL_GOOGLE_SHEET)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        logToUI("✅ [SHEET EXITOSO] Datos guardados en Google Sheets.")
                    } else {
                        logToUI("⚠️ [SHEET ERROR] Código de respuesta: ${response.code}")
                    }
                }

            } catch (e: Exception) {
                logToUI("❌ [ERROR TRANSMISIÓN] ${e.localizedMessage}")
            }
        }
    }

    private fun actualizarMetricasUI() {
        val intent = Intent("com.example.networktester.METRICS_EVENT").apply {
            putExtra("TOTAL_UDP", totalPeticionesUdp.get())
            putExtra("TOTAL_POST", totalPeticionesPost.get() + totalPeticionesSpeedTest.get())
            putExtra("TOTAL_BYTES", totalBytesDescargados.get())
            putExtra("DEVICE_ID", deviceId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun logToUI(mensaje: String) {
        val hora = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val intent = Intent("com.example.networktester.LOG_EVENT").apply {
            putExtra("LOG_MESSAGE", "[$hora] $mensaje")
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("NetworkTesterChannel", "Pruebas de Red", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
