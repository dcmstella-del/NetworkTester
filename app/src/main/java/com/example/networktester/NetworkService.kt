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

class NetworkService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    // Servidores objetivo de prueba
    private val DOWNLOAD_URL = "https://speed.hetzner.de/100MB.bin" // Archivo pesado para descarga continua
    private val UPLOAD_URL = "https://httpbin.org/post"
    private val IPERF_SERVER_IP = "192.168.1.100" // Cambiar por tu IP de iperf3 si aplica
    private val TRACKER_UDP_IP = "tracker.opentrackr.org"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalNotificacion()

        val notification = NotificationCompat.Builder(this, "NET_CHANNEL")
            .setContentTitle("Pruebas de Estrés de Red Activas")
            .setContentText("Generando tráfico intensivo simultáneo...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)

        // Lanza TODOS los módulos al mismo tiempo en hilos independientes (Paralelo)
        lanzarCargasYDescargasContinuas()
        lanzarInundacionTorrents()
        lanzarPruebasVelocidadLoop()
        lanzarSincronizacionConstante()
        lanzarIperf3Loop()

        return START_STICKY
    }

    // 1. Descargas y Cargas pesadas sin pausa
    private fun lanzarCargasYDescargasContinuas() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val requestGet = Request.Builder().url(DOWNLOAD_URL).build()
                    client.newCall(requestGet).execute().use { response ->
                        val inputStream = response.body?.byteStream()
                        val buffer = ByteArray(65536) // Buffer de 64KB para máxima velocidad
                        while (inputStream?.read(buffer) != -1 && isActive) {
                            // Descargando activamente saturando ancho de banda
                        }
                    }
                } catch (e: Exception) {
                    delay(1000)
                }
            }
        }
    }

    // 2. Torrents: Inundación de paquetes UDP al puerto 6969
    private fun lanzarInundacionTorrents() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val socket = DatagramSocket()
                    val address = InetAddress.getByName(TRACKER_UDP_IP)
                    val buffer = ByteArray(128)
                    val packet = DatagramPacket(buffer, buffer.size, address, 6969)
                    
                    // Envía ráfagas rápidas de 20 paquetes UDP por ciclo
                    repeat(20) {
                        socket.send(packet)
                    }
                    socket.close()
                } catch (e: Exception) { }
                delay(100) // Peticiones UDP cada 100ms (10 veces por segundo)
            }
        }
    }

    // 3. Pruebas de velocidad repetidas
    private fun lanzarPruebasVelocidadLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val request = Request.Builder().url("https://httpbin.org/bytes/10485760").build()
                    client.newCall(request).execute().close()
                } catch (e: Exception) { }
                delay(2000)
            }
        }
    }

    // 4. Sincronización nube / Peticiones POST tipo malware o sync agresivo
    private fun lanzarSincronizacionConstante() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val mediaType = "text/plain".toMediaTypeOrNull()
                    val payload = "X".repeat(10240) // Payload de 10KB enviándose constantemente
                    val body = RequestBody.create(mediaType, payload)
                    val requestPost = Request.Builder().url(UPLOAD_URL).post(body).build()
                    
                    client.newCall(requestPost).execute().close()
                } catch (e: Exception) { }
                delay(300) // Envía un POST de sincronización cada 300ms (3 requests por segundo)
            }
        }
    }

    // 5. iperf3 continuo
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
                        val process = ProcessBuilder(iperfPath, "-c", IPERF_SERVER_IP, "-t", "10", "-u", "-b", "10M")
                            .redirectErrorStream(true)
                            .start()
                        process.waitFor()
                    }
                } catch (e: Exception) { }
                delay(5000)
            }
        }
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "NET_CHANNEL",
                "Pruebas Intensivas de Red",
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
