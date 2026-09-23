package com.example.networktester

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Contadores thread-safe
    private val totalPeticionesUdp = AtomicLong(0)
    private val totalPeticionesPost = AtomicLong(0)
    private val totalBytesDescargados = AtomicLong(0)

    private var multiplicador = 1
    private var hilosDescarga = 4
    private var correoDestino = ""
    private var horaProgramada = "23:00"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        multiplicador = intent?.getIntExtra("MULTIPLICADOR", 1) ?: 1
        correoDestino = intent?.getStringExtra("CORREO") ?: ""
        horaProgramada = intent?.getStringExtra("HORA") ?: "23:00"

        // El valor base (Normal = 1) ahora equivale al DOBLE de la versión previa (4 hilos)
        hilosDescarga = when (multiplicador) {
            2 -> 8      // Modo Doble
            4 -> 16     // Modo Full Burst
            else -> 4   // Modo Normal (Doble del original)
        }

        crearCanalNotificacion()
        val notification = NotificationCompat.Builder(this, "NetworkTesterChannel")
            .setContentTitle("Pruebas de Red Duplicadas (Modo x$multiplicador)")
            .setContentText("Generando tráfico intensivo hacia $correoDestino")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        logToUI("🚀 [INICIO POTENCIADO] Ejecutando con $hilosDescarga hilos paralelos de descarga.")

        lanzarSaturacionUdp()
        lanzarPeticionesPost()
        lanzarDescargasMasivas()
        lanzarProgramadorDeCorreo()
        lanzarActualizadorUI()

        return START_STICKY
    }

    // Actualiza la UI de manera continua cada 500 ms
    private fun lanzarActualizadorUI() {
        serviceScope.launch {
            while (isActive) {
                actualizarMetricasUI()
                delay(500)
            }
        }
    }

    // 1. Ráfagas UDP (P2P / Torrenting) - Base duplicada a 2,400 pkts/min
    private fun lanzarSaturacionUdp() {
        serviceScope.launch {
            val trackerHost = "tracker.opentrackr.org"
            val port = 6969
            val mensajeUdp = "ANNOUNCE_P2P_SIMULATION_PACKET_TEST"
            val buffer = mensajeUdp.toByteArray()

            while (isActive) {
                try {
                    val address = InetAddress.getByName(trackerHost)
                    val socket = DatagramSocket()
                    // 40 paquetes por ráfaga cada 1 segundo = 2,400 UDP/minuto en modo Normal
                    val paquetesAEnviar = 40 * multiplicador

                    for (i in 1..paquetesAEnviar) {
                        val packet = DatagramPacket(buffer, buffer.size, address, port)
                        socket.send(packet)
                        totalPeticionesUdp.incrementAndGet()
                    }
                    socket.close()

                    logToUI("🌊 [TORRENT] Ráfaga UDP enviada ($paquetesAEnviar pkts -> $trackerHost:$port)")
                } catch (e: Exception) {
                    logToUI("⚠️ [UDP ERROR] ${e.localizedMessage}")
                }
                delay((1000 / multiplicador).toLong())
            }
        }
    }

    // 2. HTTP POST Sync - Base duplicada a 80 req/min
    private fun lanzarPeticionesPost() {
        serviceScope.launch {
            val urlPost = "https://httpbin.org/post"
            val jsonPayload = "{\"device\":\"android_tester\",\"data\":\"" + "X".repeat(10240) + "\"}"
            val mediaType = "application/json; charset=utf-8".toMediaType()

            while (isActive) {
                try {
                    val body = jsonPayload.toRequestBody(mediaType)
                    val request = Request.Builder().url(urlPost).post(body).build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            totalPeticionesPost.incrementAndGet()
                            logToUI("☁️ [NUBE SYNC] POST HTTP de 10 KB enviado")
                        }
                    }
                } catch (e: Exception) {
                    logToUI("⚠️ [POST ERROR] ${e.localizedMessage}")
                }
                // Delay base reducido a 750 ms (80 peticiones por minuto)
                delay((750 / multiplicador).toLong())
            }
        }
    }

    // 3. Descargas Masivas Continuas (Multi-Hilo)
    private fun lanzarDescargasMasivas() {
        repeat(hilosDescarga) { hiloId ->
            serviceScope.launch {
                val downloadUrl = "https://speed.cloudflare.com/__down?bytes=1000000000"
                
                while (isActive) {
                    try {
                        val request = Request.Builder()
                            .url(downloadUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .build()

                        client.newCall(request).execute().use { response ->
                            val inputStream = response.body?.byteStream()
                            val buffer = ByteArray(131072) // Buffer de 128 KB
                            var bytesRead: Int

                            while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1 && isActive) {
                                totalBytesDescargados.addAndGet(bytesRead.toLong())
                            }
                        }
                    } catch (e: Exception) {
                        delay(1000)
                    }
                }
            }
        }
    }

    // 4. Verificación y envío de correo programado
    private fun lanzarProgramadorDeCorreo() {
        serviceScope.launch {
            var correoEnviadoHoy = false
            while (isActive) {
                val horaActual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                if (horaActual == horaProgramada && !correoEnviadoHoy) {
                    logToUI("⏰ [REPORTE AUTOMÁTICO] Hora alcanzada ($horaActual). Enviando correo...")
                    enviarCorreoDeReporte()
                    correoEnviadoHoy = true
                }

                if (horaActual == "00:01") {
                    correoEnviadoHoy = false
                }

                delay(20000)
            }
        }
    }

    private fun enviarCorreoDeReporte() {
        serviceScope.launch {
            try {
                val bytes = totalBytesDescargados.get()
                val udp = totalPeticionesUdp.get()
                val post = totalPeticionesPost.get()
                val mbTotales = bytes / (1024.0 * 1024.0)
                val gbTotales = mbTotales / 1024.0
                val totalPeticiones = udp + post
                val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                val formBody = FormBody.Builder()
                    .add("email_destino", correoDestino)
                    .add("asunto", "Reporte de Pruebas de Red NetworkTester - $fecha")
                    .add("fecha", fecha)
                    .add("peticiones_totales", totalPeticiones.toString())
                    .add("peticiones_udp", udp.toString())
                    .add("peticiones_post", post.toString())
                    .add("megabytes_descargados", String.format("%.2f MB", mbTotales))
                    .add("gigabytes_descargados", String.format("%.3f GB", gbTotales))
                    .add("modo_ejecucion", "Modo x$multiplicador ($hilosDescarga hilos)")
                    .build()

                val request = Request.Builder()
                    .url("https://formspree.io/f/mqkvpaby")
                    .post(formBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    logToUI("✉️ [CORREO ENVIADO] Reporte transmitido correctamente a $correoDestino")
                }
            } catch (e: Exception) {
                logToUI("⚠️ [CORREO ERROR] ${e.localizedMessage}")
            }
        }
    }

    private fun actualizarMetricasUI() {
        val intent = Intent("com.example.networktester.METRICS_EVENT").apply {
            putExtra("TOTAL_UDP", totalPeticionesUdp.get())
            putExtra("TOTAL_POST", totalPeticionesPost.get())
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
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
