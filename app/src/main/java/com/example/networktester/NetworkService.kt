package com.example.networktester

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
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

class NetworkService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Contadores globales acumulados
    private var totalPeticionesUdp = 0L
    private var totalPeticionesPost = 0L
    private var totalBytesDescargados = 0L

    // Factor de multiplicación (1 = Normal, 2 = Doble, 4 = Full Burst)
    private var multiplicador = 1
    private var hilosDescarga = 1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        multiplicador = intent?.getIntExtra("MULTIPLICADOR", 1) ?: 1
        hilosDescarga = when (multiplicador) {
            2 -> 2
            4 -> 4
            else -> 1
        }

        crearCanalNotificacion()
        val notification = NotificationCompat.Builder(this, "NetworkTesterChannel")
            .setContentTitle("Pruebas de Red Activas (Modo x$multiplicador)")
            .setContentText("Generando tráfico y recopilando métricas...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        logToUI("🚀 [INICIO] Servicio arrancado en Modo x$multiplicador con $hilosDescarga hilos de descarga.")

        lanzarSaturacionUdp()
        lanzarPeticionesPost()
        lanzarDescargasMasivas()
        lanzarReportePeriodico()

        return START_STICKY
    }

    // 1. Ráfagas UDP (P2P / Torrenting)
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
                    val paquetesAEnviar = 20 * multiplicador

                    for (i in 1..paquetesAEnviar) {
                        val packet = DatagramPacket(buffer, buffer.size, address, port)
                        socket.send(packet)
                        totalPeticionesUdp++
                    }
                    socket.close()

                    logToUI("🌊 [TORRENT] Ráfaga UDP enviada ($paquetesAEnviar pkts -> $trackerHost:$port)")
                    actualizarMetricasUI()
                } catch (e: Exception) {
                    logToUI("⚠️ [UDP ERROR] ${e.localizedMessage}")
                }
                delay((1000 / multiplicador).toLong())
            }
        }
    }

    // 2. Sincronización HTTP POST (Cloud / Malware telemetry)
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
                            totalPeticionesPost++
                            logToUI("☁️ [NUBE SYNC] POST HTTP enviado correctamente")
                            actualizarMetricasUI()
                        }
                    }
                } catch (e: Exception) {
                    logToUI("⚠️ [POST ERROR] ${e.localizedMessage}")
                }
                delay((1500 / multiplicador).toLong())
            }
        }
    }

    // 3. Descargas pesadas paralelas
    private fun lanzarDescargasMasivas() {
        repeat(hilosDescarga) { hiloId ->
            serviceScope.launch {
                val downloadUrl = "https://speed.cloudflare.com/__down?bytes=100000000"
                while (isActive) {
                    try {
                        logToUI("⬇️ [DESCARGA Hilo #$hiloId] Iniciando bloque de 100 MB...")
                        val request = Request.Builder().url(downloadUrl).header("User-Agent", "Mozilla/5.0").build()

                        client.newCall(request).execute().use { response ->
                            val inputStream = response.body?.byteStream()
                            val buffer = ByteArray(65536)
                            var bytesRead: Int

                            while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1 && isActive) {
                                totalBytesDescargados += bytesRead
                                if (totalBytesDescargados % (5 * 1024 * 1024) == 0L) {
                                    actualizarMetricasUI()
                                }
                            }
                            logToUI("✅ [DESCARGA Hilo #$hiloId] Bloque completado")
                        }
                    } catch (e: Exception) {
                        logToUI("⚠️ [DESCARGA ERROR Hilo #$hiloId] ${e.localizedMessage}")
                        delay(2000)
                    }
                }
            }
        }
    }

    // 4. Módulo de envío de reporte automático (Tablero / Webhook)
    private fun lanzarReportePeriodico() {
        serviceScope.launch {
            while (isActive) {
                delay(60000) // Cada 1 minuto genera un reporte acumulado
                enviarReporteTablero()
            }
        }
    }

    private fun enviarReporteTablero() {
        val mbTotales = totalBytesDescargados / (1024.0 * 1024.0)
        val peticionesTotales = totalPeticionesUdp + totalPeticionesPost

        logToUI("📊 [REPORTE ENVIADO] Total Peticiones: $peticionesTotales | Consumo: String.format('%.2f', mbTotales) MB")
    }

    private fun actualizarMetricasUI() {
        val intent = Intent("com.example.networktester.METRICS_EVENT").apply {
            putExtra("TOTAL_UDP", totalPeticionesUdp)
            putExtra("TOTAL_POST", totalPeticionesPost)
            putExtra("TOTAL_BYTES", totalBytesDescargados)
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
