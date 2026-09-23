package com.example.networktester

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
        maxRequests = 500
        maxRequestsPerHost = 150
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val totalPeticionesUdp = AtomicLong(0)
    private val totalPeticionesPost = AtomicLong(0)
    private val totalBytesDescargados = AtomicLong(0)

    private var multiplicador = 1
    private var hilosDescarga = 12
    private var correoDestino = ""
    private var horaProgramada = "23:00"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        multiplicador = intent?.getIntExtra("MULTIPLICADOR", 1) ?: 1
        correoDestino = intent?.getStringExtra("CORREO") ?: ""
        horaProgramada = intent?.getStringExtra("HORA") ?: "23:00"

        // Escalado masivo para saturación extrema (Full Burst = 32 hilos concurrentes)
        hilosDescarga = when (multiplicador) {
            2 -> 18      // Modo Doble (x2)
            4 -> 32      // Full Burst (Meta: 1 GB/min)
            else -> 8    // Normal
        }

        crearCanalNotificacion()
        val notification = NotificationCompat.Builder(this, "NetworkTesterChannel")
            .setContentTitle("Saturación de Red Ultra (x$multiplicador)")
            .setContentText("Transferencia activa hacia $correoDestino")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        logToUI("🚀 [MODO ULTRA] Lanzando $hilosDescarga hilos concurrentes de descarga masiva.")

        lanzarSaturacionUdp()
        lanzarPeticionesPost()
        lanzarDescargasMasivas()
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
                    val paquetesAEnviar = 60 * multiplicador

                    for (i in 1..paquetesAEnviar) {
                        val packet = DatagramPacket(buffer, buffer.size, address, port)
                        socket.send(packet)
                        totalPeticionesUdp.incrementAndGet()
                    }
                    socket.close()
                    logToUI("🌊 [UDP] Ráfaga de $paquetesAEnviar paquetes enviada")
                } catch (e: Exception) {
                    // Ignorar errores puntuales de UDP
                }
                delay((300 / multiplicador).toLong())
            }
        }
    }

    private fun lanzarPeticionesPost() {
        serviceScope.launch {
            val urlPost = "https://httpbin.org/post"
            val mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8")

            while (isActive) {
                try {
                    val body = okhttp3.RequestBody.create(mediaType, "{\"data\":\"" + "X".repeat(8000) + "\"}")
                    val request = Request.Builder().url(urlPost).post(body).build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            totalPeticionesPost.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    // Reintento
                }
                delay((400 / multiplicador).toLong())
            }
        }
    }

    // Descarga distribuida multicanal para alcanzar 1 GB por minuto en Full Burst
    private fun lanzarDescargasMasivas() {
        val cdnEndpoints = listOf(
            "https://speed.cloudflare.com/__down?bytes=1000000000",
            "https://proof.ovh.net/files/1Gb.dat",
            "http://ipv4.download.thinkbroadband.com/1GB.zip",
            "https://fsn1-speed.hetzner.com/1GB.bin"
        )

        repeat(hilosDescarga) { hiloId ->
            serviceScope.launch {
                val buffer = ByteArray(524288) // Buffer ultra rápido de 512 KB
                
                while (isActive) {
                    try {
                        val targetUrl = cdnEndpoints[hiloId % cdnEndpoints.size]
                        val request = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .build()

                        client.newCall(request).execute().use { response ->
                            val inputStream: InputStream? = response.body()?.byteStream()
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

    private fun lanzarProgramadorDeCorreo() {
        serviceScope.launch {
            var correoEnviadoHoy = false
            while (isActive) {
                val horaActual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                if (horaActual == horaProgramada && !correoEnviadoHoy) {
                    logToUI("⏰ [REPORTE] Hora alcanzada ($horaActual). Transmitiendo correo...")
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
                val mbTotales = bytes / (1024.0 * 1024.0)
                val gbTotales = mbTotales / 1024.0
                val totalPeticiones = udp + post
                val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                // FormBody ajustado con campos estándar de envío directo
                val formBody = FormBody.Builder()
                    .add("email", correoDestino)
                    .add("_replyto", correoDestino)
                    .add("_subject", "Reporte NetworkTester - $fecha")
                    .add("Fecha_Registro", fecha)
                    .add("Peticiones_Totales", totalPeticiones.toString())
                    .add("Ráfagas_UDP", udp.toString())
                    .add("Peticiones_POST", post.toString())
                    .add("Megabytes_Consumidos", String.format(Locale.US, "%.2f MB", mbTotales))
                    .add("Gigabytes_Consumidos", String.format(Locale.US, "%.3f GB", gbTotales))
                    .add("Modo_Ejecucion", "Modo x$multiplicador ($hilosDescarga hilos)")
                    .build()

                val request = Request.Builder()
                    .url("https://formspree.io/f/mqkvpaby")
                    .post(formBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        logToUI("✉️ [CORREO ENVIADO EXITOSAMENTE] Revisa tu bandeja de entrada o Spam.")
                    } else {
                        logToUI("⚠️ [CORREO ERROR HTTP] Código: ${response.code()}")
                    }
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
