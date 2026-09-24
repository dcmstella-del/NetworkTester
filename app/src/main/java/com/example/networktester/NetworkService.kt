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

    private val URL_GOOGLE_SHEET = "https://script.google.com/macros/s/AKfycbwuBQg4vib23jPfmV8-sRqVA9Qw0MzIV89QE-yQF1jVJsMcppbkrFw9-jaWhUx-GPtt/exec"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val dispatcher = Dispatcher().apply {
        maxRequests = 500
        maxRequestsPerHost = 100
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val totalPeticionesUdp = AtomicLong(0)
    private val totalPeticionesPost = AtomicLong(0)
    private val totalPeticionesSpeedTest = AtomicLong(0)
    private val totalBytesDescargados = AtomicLong(0)

    private var multiplicador = 1
    private var horaProgramada = "23:55"
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
        horaProgramada = intent?.getStringExtra("HORA_PROGRAMADA") ?: "23:55"

        crearCanalNotificacion()
        val notification = NotificationCompat.Builder(this, "NetworkTesterChannel")
            .setContentTitle("NetworkTester - $deviceId")
            .setContentText("Ejecutando pruebas de red (x$multiplicador). Reporte minutal.")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        logToUI("🚀 [INICIO: $deviceId] Pruebas continuas activas con reporte automático cada 1 min.")

        lanzarRafagasUdp()
        lanzarPeticionesPostCloud()
        lanzarSpeedTestLatencia()
        lanzarDescargasModeradas()
        lanzarSincronizadorPorMinuto()
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
                delay(300)
            }
        }
    }

    // 1. PETICIONES UDP CONTINUAS
    private fun lanzarRafagasUdp() {
        serviceScope.launch {
            val trackerHost = "tracker.opentrackr.org"
            val port = 6969
            val mensajeUdp = "PING_TEST_PACKET"
            val buffer = mensajeUdp.toByteArray()

            val paquetesPorRafaga = when (multiplicador) {
                4 -> 30
                2 -> 15
                else -> 5
            }

            while (isActive) {
                try {
                    val address = InetAddress.getByName(trackerHost)
                    val socket = DatagramSocket()
                    repeat(paquetesPorRafaga) {
                        val packet = DatagramPacket(buffer, buffer.size, address, port)
                        socket.send(packet)
                        totalBytesDescargados.addAndGet(buffer.size.toLong())
                        val count = totalPeticionesUdp.incrementAndGet()
                        if (count % 2000L == 0L) {
                            logToUI("🌊 [UDP] $count paquetes transmitidos.")
                        }
                    }
                    socket.close()
                } catch (e: Exception) {
                    // Ignorar
                }
                delay(30)
            }
        }
    }

    // 2. PETICIONES POST CONCURRENTES
    private fun lanzarPeticionesPostCloud() {
        val hilosPost = when (multiplicador) {
            4 -> 6
            2 -> 3
            else -> 1
        }

        val urlPost = "https://httpbin.org/post"
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

        repeat(hilosPost) {
            serviceScope.launch {
                while (isActive) {
                    try {
                        val jsonPayload = "{\"device_id\":\"$deviceId\",\"status\":\"testing\"}"
                        val body = jsonPayload.toRequestBody(mediaType)
                        val request = Request.Builder().url(urlPost).post(body).build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                totalBytesDescargados.addAndGet(300)
                                val count = totalPeticionesPost.incrementAndGet()
                                if (count % 200L == 0L) {
                                    logToUI("☁️ [POST] $count peticiones confirmadas.")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Reintento
                    }
                    delay(50)
                }
            }
        }
    }

    // 3. SPEEDTEST Y LATENCIA
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
                    val inicio = System.currentTimeMillis()
                    val request = Request.Builder().url(target).build()

                    client.newCall(request).execute().use {
                        val latencia = System.currentTimeMillis() - inicio
                        totalBytesDescargados.addAndGet(250)
                        val count = totalPeticionesSpeedTest.incrementAndGet()
                        if (count % 50L == 0L) {
                            logToUI("⚡ [PING] #$count: ${latencia}ms")
                        }
                    }
                } catch (e: Exception) {
                    // Ignorar
                }
                delay(200)
            }
        }
    }

    // 4. DESCARGAS CONTINUAS CON CONTROL DE MEMORIA RAM
    private fun lanzarDescargasModeradas() {
        val hilosDescarga = when (multiplicador) {
            4 -> 6
            2 -> 3
            else -> 1
        }

        val cdnEndpoints = listOf(
            "https://speed.cloudflare.com/__down?bytes=10000000",
            "https://proof.ovh.net/files/10Mb.dat"
        )

        repeat(hilosDescarga) { hiloId ->
            serviceScope.launch {
                val buffer = ByteArray(32 * 1024) // Buffer de 32 KB para proteger la RAM
                
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
                        delay(200)
                    }
                }
            }
        }
    }

    // 5. REPORTE AUTOMÁTICO CADA 1 MINUTO
    private fun lanzarSincronizadorPorMinuto() {
        serviceScope.launch {
            while (isActive) {
                delay(60000) // 1 minuto
                enviarRegistroAGoogleSheet()
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

                logToUI("📊 [AUTO-REPORTE] Enviando métricas del minuto a Sheets...")

                val jsonPayload = """
                    {
                        "device_id": "$deviceId",
                        "timestamp": "$timestampCompleto",
                        "gigabytes_consumidos": ${String.format(Locale.US, "%.5f", gbTotales)},
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
                        logToUI("✅ [SHEET EXITO] Reporte de 1 minuto registrado.")
                    } else {
                        logToUI("⚠️ [SHEET ERROR] Código: ${response.code}")
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
