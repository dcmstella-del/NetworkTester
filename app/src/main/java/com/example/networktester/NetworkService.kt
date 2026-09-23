package com.example.networktester

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.Dispatcher
import okhttp3.FormBody
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

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val dispatcher = Dispatcher().apply {
        maxRequests = 2000
        maxRequestsPerHost = 500
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val totalPeticionesUdp = AtomicLong(0)
    private val totalPeticionesPost = AtomicLong(0)
    private val totalPeticionesSpeedTest = AtomicLong(0)
    private val totalBytesDescargados = AtomicLong(0)

    private var multiplicador = 1
    private var correoDestino = ""
    private var horaProgramada = "23:00"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        multiplicador = intent?.getIntExtra("MULTIPLICADOR", 1) ?: 1
        correoDestino = intent?.getStringExtra("CORREO") ?: ""
        horaProgramada = intent?.getStringExtra("HORA") ?: "23:00"

        crearCanalNotificacion()
        val notification = NotificationCompat.Builder(this, "NetworkTesterChannel")
            .setContentTitle("Prueba Ultra Intensiva (x$multiplicador)")
            .setContentText("Saturación de red activa...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        logToUI("🚀 [FULL BURST ULTRA] Iniciando saturación multicanal modo x$multiplicador.")

        lanzarRafagasUdp()
        lanzarPeticionesPostCloud()
        lanzarSpeedTestLatencia()
        lanzarDescargasMasivasExtremas()
        lanzarProgramadorDeCorreo()
        lanzarActualizadorUI()

        return START_STICKY
    }

    private fun lanzarActualizadorUI() {
        serviceScope.launch {
            while (isActive) {
                actualizarMetricasUI()
                delay(250)
            }
        }
    }

    // 1. UDP Saturante: 120 (Normal), 300 (x2), 1200 ráfagas/min (Full Burst)
    private fun lanzarRafagasUdp() {
        serviceScope.launch {
            val trackerHost = "tracker.opentrackr.org"
            val port = 6969
            val mensajeUdp = "ANNOUNCE_P2P_SIMULATION_PACKET_TEST_EXTREME"
            val buffer = mensajeUdp.toByteArray()

            val intervalo = when (multiplicador) {
                4 -> 50L   // Full Burst Extreme
                2 -> 200L  // Double
                else -> 500L
            }

            while (isActive) {
                try {
                    val address = InetAddress.getByName(trackerHost)
                    val socket = DatagramSocket()
                    val packet = DatagramPacket(buffer, buffer.size, address, port)
                    
                    // Envío por bloques para no saturar la CPU
                    val rafagaTamano = if (multiplicador == 4) 5 else 1
                    repeat(rafagaTamano) {
                        socket.send(packet)
                        totalPeticionesUdp.incrementAndGet()
                    }
                    socket.close()

                    if (totalPeticionesUdp.get() % 500L == 0L) {
                        logToUI("🌊 [UDP ULTRA] Ráfagas enviadas acumuladas: ${totalPeticionesUdp.get()}")
                    }
                } catch (e: Exception) {
                    // Manejo silencioso de red
                }
                delay(intervalo)
            }
        }
    }

    // 2. POST Cloud: Sincronización continua de payloads
    private fun lanzarPeticionesPostCloud() {
        serviceScope.launch {
            val urlPost = "https://httpbin.org/post"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            val intervalo = when (multiplicador) {
                4 -> 150L  // ~400 POST/min
                2 -> 375L  // ~160 POST/min
                else -> 750L // ~80 POST/min
            }

            while (isActive) {
                try {
                    val jsonPayload = "{\"sync_data\":\"" + "X".repeat(10000) + "\"}"
                    val body = jsonPayload.toRequestBody(mediaType)
                    val request = Request.Builder().url(urlPost).post(body).build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val total = totalPeticionesPost.incrementAndGet()
                            if (total % 50L == 0L) {
                                logToUI("☁️ [POST CLOUD] Confirmadas $total peticiones.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Reintento
                }
                delay(intervalo)
            }
        }
    }

    // 3. SpeedTest Latencia continua
    private fun lanzarSpeedTestLatencia() {
        serviceScope.launch {
            val pingEndpoints = listOf(
                "https://1.1.1.1/cdn-cgi/trace",
                "https://8.8.8.8",
                "https://www.google.com/generate_204"
            )

            val intervalo = when (multiplicador) {
                4 -> 500L   // 120/min
                2 -> 1000L  // 60/min
                else -> 2000L // 30/min
            }

            while (isActive) {
                try {
                    val target = pingEndpoints[(totalPeticionesSpeedTest.get() % pingEndpoints.size).toInt()]
                    val inicio = System.currentTimeMillis()
                    val request = Request.Builder().url(target).build()

                    client.newCall(request).execute().use { response ->
                        val latencia = System.currentTimeMillis() - inicio
                        val count = totalPeticionesSpeedTest.incrementAndGet()
                        if (count % 10L == 0L) {
                            logToUI("⚡ [SPEEDTEST] Latencia medida: ${latencia}ms ($target)")
                        }
                    }
                } catch (e: Exception) {
                    // Ignorar
                }
                delay(intervalo)
            }
        }
    }

    // 4. Descargas Masivas Agresivas: Objetivo de ~2 GB/min en Full Burst
    private fun lanzarDescargasMasivasExtremas() {
        val hilos = when (multiplicador) {
            4 -> 64 // 64 Hilos concurrentes para exprimir la banda ancha Wi-Fi
            2 -> 24
            else -> 10
        }

        val cdnEndpoints = listOf(
            "https://speed.cloudflare.com/__down?bytes=1000000000",
            "https://proof.ovh.net/files/1Gb.dat",
            "http://ipv4.download.thinkbroadband.com/1GB.zip",
            "https://fsn1-speed.hetzner.com/1GB.bin"
        )

        repeat(hilos) { hiloId ->
            serviceScope.launch {
                val buffer = ByteArray(1048576) // Buffer de 1 MB por lectura para máximo throughput
                
                while (isActive) {
                    try {
                        val targetUrl = cdnEndpoints[hiloId % cdnEndpoints.size]
                        val request = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .build()

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
                        delay(100)
                    }
                }
            }
        }
    }

    private fun lanzarProgramadorDeCorreo() {
        serviceScope.launch {
            var correoEnviadoHoy = false
            while (isActive) {
                val horaActual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                if (horaActual == horaProgramada && !correoEnviadoHoy) {
                    logToUI("⏰ [REPORTE] Hora programada ($horaActual) alcanzada. Transmitiendo datos...")
                    enviarCorreoDeReporte()
                    correoEnviadoHoy = true
                }

                if (horaActual == "00:01") {
                    correoEnviadoHoy = false
                }

                delay(10000)
            }
        }
    }

    private fun enviarCorreoDeReporte() {
        serviceScope.launch {
            try {
                val bytes = totalBytesDescargados.get()
                val udp = totalPeticionesUdp.get()
                val post = totalPeticionesPost.get()
                val speed = totalPeticionesSpeedTest.get()
                val mbTotales = bytes / (1024.0 * 1024.0)
                val gbTotales = mbTotales / 1024.0
                val totalPeticiones = udp + post + speed
                val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                // Guardado local de respaldo
                val prefs = getSharedPreferences("NetworkTesterStats", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("ULTIMO_REPORTE_FECHA", fecha)
                    putFloat("ULTIMO_REPORTE_GB", gbTotales.toFloat())
                    putLong("ULTIMO_REPORTE_PETICIONES", totalPeticiones)
                    apply()
                }

                val formBody = FormBody.Builder()
                    .add("email", correoDestino)
                    .add("_replyto", correoDestino)
                    .add("_subject", "Reporte Diario NetworkTester - $fecha")
                    .add("Fecha_Registro", fecha)
                    .add("Peticiones_Totales", totalPeticiones.toString())
                    .add("Rafagas_UDP", udp.toString())
                    .add("Peticiones_POST_Cloud", post.toString())
                    .add("Pruebas_SpeedTest", speed.toString())
                    .add("Megabytes_Consumidos", String.format(Locale.US, "%.2f MB", mbTotales))
                    .add("Gigabytes_Consumidos", String.format(Locale.US, "%.3f GB", gbTotales))
                    .add("Modo_Ejecucion", "Modo x$multiplicador (64 Hilos)")
                    .build()

                val request = Request.Builder()
                    .url("https://formspree.io/f/mqkvpaby")
                    .post(formBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        logToUI("✉️ [CORREO ENVIADO] Transmisión del día exitosa hacia $correoDestino")
                    } else {
                        logToUI("⚠️ [CORREO REINTENTANDO] Código HTTP: ${response.code}. Guardado localmente.")
                    }
                }
            } catch (e: Exception) {
                logToUI("⚠️ [SIN CONEXIÓN] Guardado localmente. Reintentará al conectar.")
            }
        }
    }

    private fun actualizarMetricasUI() {
        val intent = Intent("com.example.networktester.METRICS_EVENT").apply {
            putExtra("TOTAL_UDP", totalPeticionesUdp.get())
            putExtra("TOTAL_POST", totalPeticionesPost.get() + totalPeticionesSpeedTest.get())
            putExtra("TOTAL_BYTES", totalBytesDescargados.get())
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
