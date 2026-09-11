package com.example.networktester

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()

    private val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=25000000"
    private val UPLOAD_URL = "https://httpbin.org/post"
    private val IPERF_SERVER_IP = "192.168.1.100"
    private val TRACKER_UDP_IP = "tracker.opentrackr.org"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalNotificacion()

        val notification = NotificationCompat.Builder(this, "NET_CHANNEL")
            .setContentTitle("Pruebas de Red Activas")
            .setContentText("Consola de procesos en tiempo real...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
        logToUI("🚀 [INICIO] Servicio iniciado en segundo plano.")

        lanzarCargasYDescargasContinuas()
        lanzarInundacionTorrents()
        lanzarPruebasVelocidadLoop()
        lanzarSincronizacionConstante()
        lanzarIperf3Loop()

        return START_STICKY
    }

    private fun logToUI(mensaje: String) {
        val hora = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val intent = Intent("com.example.networktester.LOG_EVENT").apply {
            putExtra("LOG_MESSAGE", "[$hora] $mensaje")
        }
        sendBroadcast(intent)
    }

    private fun lanzarCargasYDescargasContinuas() {
        serviceScope.launch {
            while (isActive) {
                try {
                    logToUI("⬇️ [DESCARGA] Descargando bloque de datos...")
                    val requestGet = Request.Builder().url(DOWNLOAD_URL).build()
                    client.newCall(requestGet).execute().use { response ->
                        val inputStream = response.body?.byteStream()
                        val buffer = ByteArray(65536)
                        var bytesRead = 0
                        var totalDownloaded = 0
                        while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1 && isActive) {
                            totalDownloaded += bytesRead
                        }
                        logToUI("✅ [DESCARGA FIN] Finalizados ${totalDownloaded / 1024 / 1024} MB")
                    }
                } catch (e: Exception) {
                    logToUI("⚠️ [DESCARGA ERROR] ${e.localizedMessage}")
                    delay(2000)
                }
            }
        }
    }

    private fun lanzarInundacionTorrents() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val socket = DatagramSocket()
                    val address = InetAddress.getByName(TRACKER_UDP_IP)
                    val buffer = ByteArray(128)
                    val packet = DatagramPacket(buffer, buffer.size, address, 6969)
                    
                    repeat(20) { socket.send(packet) }
                    socket.close()
                    logToUI("🌊 [TORRENT] Ráfaga UDP enviada (20 pkts -> $TRACKER_UDP_IP:6969)")
                } catch (e: Exception) {
                    logToUI("⚠️ [TORRENT ERROR] Fallo envío UDP")
                }
                delay(1000)
            }
        }
    }

    private fun lanzarPruebasVelocidadLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    logToUI("⚡ [SPEEDTEST] Midiendo ancho de banda...")
                    val startTime = System.currentTimeMillis()
                    val request = Request.Builder().url("https://httpbin.org/bytes/1048576").build()
                    client.newCall(request).execute().use { response ->
                        val bytes = response.body?.bytes()?.size ?: 0
                        val duration = (System.currentTimeMillis() - startTime) / 1000.0
                        val mbps = if (duration > 0) ((bytes * 8) / 1_000_000.0) / duration else 0.0
                        logToUI("📊 [SPEEDTEST RESULT] Velocidad aprox: %.2f Mbps".format(mbps))
                    }
                } catch (e: Exception) {
                    logToUI("⚠️ [SPEEDTEST ERROR] Fallo la prueba de velocidad")
                }
                delay(4000)
            }
        }
    }

    private fun lanzarSincronizacionConstante() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val mediaType = "text/plain".toMediaTypeOrNull()
                    val payload = "X".repeat(10240)
                    val body = RequestBody.create(mediaType, payload)
                    val requestPost = Request.Builder().url(UPLOAD_URL).post(body).build()
                    
                    client.newCall(requestPost).execute().close()
                    logToUI("☁️ [NUBE SYNC] POST 10KB enviado a la nube")
                } catch (e: Exception) {
                    logToUI("⚠️ [NUBE SYNC ERROR] Fallo sincronización")
                }
                delay(1500)
            }
        }
    }

    private fun lanzarIperf3Loop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val iperfPath = "${applicationContext.filesDir}/iperf3"
                    val file = File(iperfPath)

                    if (!file.exists() && assets.list("")?.contains("iperf3") == true) {
                        assets.open("iperf3").use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        file.setExecutable(true)
                    }

                    if (file.exists()) {
                        logToUI("📡 [IPERF3] Ejecutando cliente contra $IPERF_SERVER_IP...")
                        val process = ProcessBuilder(iperfPath, "-c", IPERF_SERVER_IP, "-t", "5", "-u", "-b", "10M")
                            .redirectErrorStream(true)
                            .start()
                        process.waitFor()
                        logToUI("✅ [IPERF3 FIN] Prueba iperf3 finalizada")
                    } else {
                        //ogToUI("ℹ️ [IPERF3] Binario iperf3 no encontrado en assets")
                    }
                } catch (e: Exception) {
                    logToUI("⚠️ [IPERF3 ERROR] Fallo ejecución")
                }
                delay(6000)
            }
        }
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "NET_CHANNEL",
                "Pruebas de Red Continuas",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logToUI("🛑 [DETENIDO] Servicio de pruebas finalizado.")
        serviceScope.cancel()
        super.onDestroy()
    }
}
