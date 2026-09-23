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
import okhttp3.MediaType.Companion.toMediaType
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

    // Configuración de OkHttp liberada sin límites de conexiones concurrentes por host
    private val dispatcher = Dispatcher().apply {
        maxRequests = 200
        maxRequestsPerHost = 100
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val totalPeticionesUdp = AtomicLong(0)
    private val totalPeticionesPost = AtomicLong(0)
    private val totalBytesDescargados = AtomicLong(0)

    private var multiplicador = 1
    private var hilosDescarga = 8
    private var correoDestino = ""
    private var horaProgramada = "23:00"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        multiplicador = intent?.getIntExtra("MULTIPLICADOR", 1) ?: 1
        correoDestino = intent?.getStringExtra("CORREO") ?: ""
        horaProgramada = intent?.getStringExtra("HORA") ?: "23:00"

        // Escalamos agresivamente la cantidad de hilos para saturar la interfaz de red
        hilosDescarga = when (multiplicador) {
            2 -> 12
            4 -> 24
            else -> 8
        }

        crearCanalNotificacion()
        val notification = NotificationCompat.Builder(this, "NetworkTesterChannel")
            .setContentTitle("Saturación de Red de Alto Rendimiento")
            .setContentText("Transferencia en vivo a $correoDestino")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        logToUI("🚀 [INICIO POTENCIADO] Ejecutando $hilosDescarga hilos paralelos de descarga.")

        lanzarSaturacionUdp()
        lanzarPeticionesPost()
        lanzarDescargasMasivas()
        lanzarProgramadorDeCorreo()
        lanzarActualizadorUI()

        return START_STICKY
    }

    // Refresco continuo de la UI cada 300 milisegundos
    private fun lanzarActualizadorUI() {
        serviceScope.launch {
            while (isActive) {
                actualizarMetricasUI()
                delay(300)
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
                    val paquetesAEnviar = 30 * multiplicador

                    for (i in 1..paquetesAEnviar) {
                        val packet = DatagramPacket(buffer, buffer.size, address, port)
                        socket.send(packet)
                        totalPeticionesUdp.incrementAndGet()
                    }
                    socket.close()
                    logToUI("🌊 [UDP] Ráfaga de $paquetesAEnviar paquetes enviada.")
                } catch (e: Exception) {
                    // Manejo silencioso de reconexiones
                }
                delay((500 / multiplicador).toLong())
            }
        }
    }

    private fun lanzarPeticionesPost() {
        serviceScope.launch {
            val urlPost = "https://httpbin.org/post"
            val jsonPayload = "{\"data\":\"" + "X".repeat(5000) + "\"}"
            val mediaType = "application/json; charset=utf-8".toMediaType()

            while (isActive) {
                try {
                    val body = jsonPayload.toRequestBody(mediaType)
                    val request = Request.Builder().url(urlPost).post(body).build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            totalPeticionesPost.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    // Retry automático
                }
                delay((600 / multiplicador).toLong())
            }
        }
    }

    // Descarga masiva paralela usando múltiples servidores CDN para evitar throttling del servidor
    private fun lanzarDescargasMasivas() {
        val urlsPrueba = listOf(
            "https://speed.cloudflare.com/__down?bytes=100000000",
            "https://proof.ovh.net/files/100Mb.dat",
            "http://ipv4.download.thinkbroadband.com/100MB.zip"
        )

        repeat(hilosDescarga) { hiloId ->
            serviceScope.launch {
                val buffer = ByteArray(262144) // Buffer óptimo de 256 KB
                
                while (isActive) {
                    try {
                        val url = urlsPrueba[hiloId % urlsPrueba.size]
                        val request = Request.Builder()
                            .url(url)
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
                        delay(500)
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
                    logToUI("⏰ [REPORTE] Hora alcanzada ($horaActual). Enviando correo...")
                    enviarCorreoDeReporte()
                    correoEnviadoHoy = true
                }

                if (horaActual == "00:01") {
                    correoEnviadoHoy = false
                }

                delay(15000)
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
                    logToUI("✉️ [CORREO] Reporte enviado a $correoDestino")
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
