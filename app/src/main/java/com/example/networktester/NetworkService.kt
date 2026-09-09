package com.example.networktester

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class NetworkService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val client = OkHttpClient()

    private val SERVER_URL = "https://httpbin.org/bytes/1048576" 
    private val IPERF_SERVER_IP = "192.168.1.100" // Cambiar por tu servidor objetivo
    private val TRACKER_UDP_IP = "tracker.opentrackr.org"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalNotificacion()

        val notification = NotificationCompat.Builder(this, "NET_CHANNEL")
            .setContentTitle("Pruebas de Red Activas")
            .setContentText("Generando tráfico de diagnóstico...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)

        serviceScope.launch {
            while (isActive) {
                ejecutarCargasyDescargas()
                ejecutarPruebaVelocidad()
                ejecutarSimulacionTorrent()
                ejecutarIperf3(IPERF_SERVER_IP)
                delay(2000)
            }
        }

        return START_STICKY
    }

    private suspend fun ejecutarCargasyDescargas() {
        try {
            val requestGet = Request.Builder().url(SERVER_URL).build()
            client.newCall(requestGet).execute().use { response ->
                val inputStream = response.body?.byteStream()
                val buffer = ByteArray(8192)
                while (inputStream?.read(buffer) != -1 && serviceScope.isActive) {}
            }

            val body = RequestBody.create(MediaType.parse("text/plain"), "payload_sincronizacion")
            val requestPost = Request.Builder().url(SERVER_URL).post(body).build()
            client.newCall(requestPost).execute().close()
        } catch (e: Exception) {
            delay(3000)
        }
    }

    private fun ejecutarPruebaVelocidad() {
        try {
            val startTime = System.currentTimeMillis()
            val request = Request.Builder().url(SERVER_URL).build()
            
            client.newCall(request).execute().use { response ->
                val bytesRead = response.body?.bytes()?.size ?: 0
                val endTime = System.currentTimeMillis()
                val durationSeconds = (endTime - startTime) / 1000.0
                if (durationSeconds > 0) {
                    val megabits = (bytesRead * 8) / 1000000.0
                    val speedMbps = megabits / durationSeconds
                }
            }
        } catch (e: Exception) { }
    }

    private fun ejecutarSimulacionTorrent() {
        try {
            val socket = DatagramSocket()
            val address = InetAddress.getByName(TRACKER_UDP_IP)
            val buffer = ByteArray(16)
            val packet = DatagramPacket(buffer, buffer.size, address, 6969)
            socket.send(packet)
            socket.close()
        } catch (e: Exception) { }
    }

    private fun ejecutarIperf3(ipServidor: String) {
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
                val process = ProcessBuilder(iperfPath, "-c", ipServidor, "-t", "5")
                    .redirectErrorStream(true)
                    .start()
                process.waitFor()
            }
        } catch (e: Exception) { }
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
        serviceScope.cancel()
        super.onDestroy()
    }
}
