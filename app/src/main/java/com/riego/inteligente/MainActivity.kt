
package com.riego.inteligente

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private val uuid = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    )

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var connected = false
    @Volatile private var connecting = false

    private lateinit var status: TextView
    private lateinit var humidity: TextView
    private lateinit var pump: TextView
    private lateinit var mode: TextView
    private lateinit var clock: TextView
    private lateinit var log: TextView

    private val green = Color.rgb(16, 132, 88)
    private val dark = Color.rgb(13, 48, 39)
    private val red = Color.rgb(178, 67, 57)

    private val clockTask = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                clock.text = SimpleDateFormat(
                    "dd/MM/yyyy  HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())
                main.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
        main.post(clockTask)
        requestBluetoothPermissions()
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions().all {
            checkSelfPermission(it) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        if (!hasPermissions()) {
            requestPermissions(requiredPermissions(), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == 101) {
            if (hasPermissions()) {
                toast("Permisos Bluetooth concedidos")
            } else {
                toast(
                    "Permite Dispositivos cercanos en Ajustes"
                )
            }
        }
    }

    private fun rounded(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 28f
        }
    }

    private fun textView(
        value: String,
        size: Float,
        color: Int = dark,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(6, 8, 6, 8)
        }
    }

    private fun card(parent: LinearLayout): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE)
            setPadding(24, 20, 24, 20)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = 18
            parent.addView(this, params)
        }
    }

    private fun button(
        parent: LinearLayout,
        label: String,
        color: Int,
        action: () -> Unit
    ) {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.background = rounded(color)
        b.setOnClickListener { action() }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.topMargin = 8
        parent.addView(b, params)
    }

    private fun createScreen() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(
            Color.rgb(238, 247, 242)
        )

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(28, 35, 28, 35)
        scroll.addView(body)
        setContentView(scroll)

        body.addView(
            textView("🌱 Sistema de Riego", 27f, dark, true)
        )
        body.addView(
            textView(
                "Panel inteligente · Arduino UNO",
                14f, green
            )
        )

        val bt = card(body)
        bt.addView(
            textView("Conexión Bluetooth", 20f, dark, true)
        )

        status = textView("● Desconectado", 16f, red)
        bt.addView(status)

        button(bt, "Conectar al HC-05", green) {
            chooseDevice()
        }
        button(bt, "Desconectar", Color.DKGRAY) {
            disconnect()
        }

        val sensor = card(body)
        sensor.addView(
            textView("💧 Humedad del suelo", 19f, dark, true)
        )
        humidity = textView("-- %", 35f, green, true)
        sensor.addView(humidity)
        sensor.addView(
            textView("Lectura enviada por Arduino", 12f)
        )

        val date = card(body)
        date.addView(
            textView("📅 Fecha y hora", 19f, dark, true)
        )
        clock = textView("", 20f, dark, true)
        date.addView(clock)

        val control = card(body)
        control.addView(
            textView("🚿 Control de riego", 19f, dark, true)
        )

        pump = textView("Bomba: sin datos", 16f)
        mode = textView("Modo: sin datos", 16f)
        control.addView(pump)
        control.addView(mode)

        button(control, "Encender riego", green) {
            send("1")
        }
        button(control, "Apagar riego", red) {
            send("0")
        }
        button(
            control,
            "Activar modo automático",
            Color.rgb(48, 99, 160)
        ) {
            send("AUTO")
        }
        button(
            control,
            "Activar modo manual",
            Color.DKGRAY
        ) {
            send("MANUAL")
        }

        val terminal = card(body)
        terminal.addView(
            textView("📟 Terminal Bluetooth", 19f, dark, true)
        )

        log = textView("Esperando conexión...", 13f)
        terminal.addView(log)

        button(terminal, "Solicitar lectura", green) {
            send("R")
        }

        button(terminal, "Consultar horarios", green) {
            send("HORARIOS")
        }

        val entry = EditText(this)
        entry.hint = "Comando personalizado"
        entry.isSingleLine = true
        terminal.addView(entry)

        button(terminal, "Enviar comando", green) {
            val command = entry.text.toString().trim()
            if (command.isNotEmpty()) {
                send(command)
                entry.text.clear()
            }
        }
    }

    private fun chooseDevice() {
        if (!hasPermissions()) {
            requestBluetoothPermissions()
            toast("Concede los permisos y vuelve a intentar")
            return
        }

        try {
            val manager = getSystemService(
                BluetoothManager::class.java
            )
            val adapter = manager?.adapter

            if (adapter == null || !adapter.isEnabled) {
                toast("Activa Bluetooth en tu teléfono")
                return
            }

            val devices = adapter.bondedDevices.toList()

            if (devices.isEmpty()) {
                toast(
                    "Primero vincula HC-05 desde Ajustes Bluetooth"
                )
                return
            }

            val names = devices.map {
                "${it.name ?: "Dispositivo"} (${it.address})"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Selecciona tu HC-05")
                .setItems(names) { _, index ->
                    connect(adapter, devices[index])
                }
                .setNegativeButton("Cancelar", null)
                .show()

        } catch (e: SecurityException) {
            requestBluetoothPermissions()
            toast("Faltan permisos Bluetooth")
        } catch (e: Exception) {
            toast("Error: ${e.message}")
        }
    }

    private fun connect(
        adapter: BluetoothAdapter,
        device: BluetoothDevice
    ) {
        if (!hasPermissions()) {
            requestBluetoothPermissions()
            return
        }

        if (connected || connecting) {
            toast("Ya existe una conexión o intento en curso")
            return
        }

        connecting = true
        status.text = "● Conectando..."

        thread {
            var newSocket: BluetoothSocket? = null

            try {
                newSocket =
                    device.createRfcommSocketToServiceRecord(uuid)

                socket = newSocket

                adapter.cancelDiscovery()
                newSocket.connect()

                connected = true
                connecting = false

                val deviceName = device.name ?: "HC-05"

                main.post {
                    status.text = "● Conectado a $deviceName"
                    status.setTextColor(green)
                    appendLog("Bluetooth conectado")
                }

                val reader = BufferedReader(
                    InputStreamReader(newSocket.inputStream)
                )

                // Solicitar lectura inicial
                newSocket.outputStream.write(
                    "R\n".toByteArray(Charsets.UTF_8)
                )
                newSocket.outputStream.flush()

                while (connected) {
                    val line = reader.readLine() ?: break
                    main.post { processLine(line) }
                }

            } catch (e: Exception) {
                if (connecting || connected) {
                    main.post {
                        appendLog(
                            "Error Bluetooth: ${e.message}"
                        )
                        toast("No se pudo conectar al HC-05")
                    }
                }
            } finally {
                connected = false
                connecting = false

                try {
                    newSocket?.close()
                } catch (_: Exception) {}

                if (socket === newSocket) {
                    socket = null
                    main.post {
                        status.text = "● Desconectado"
                        status.setTextColor(red)
                    }
                }
            }
        }
    }

    private fun processLine(line: String) {
        appendLog(line)

        // Arduino envia:
        // [Hum: 45% | Bomba: ON | Hora: 12:30:00]
        val hum = Regex(
            """Hum:\s*(\d+)%""",
            RegexOption.IGNORE_CASE
        ).find(line)

        if (hum != null) {
            humidity.text = "${hum.groupValues[1]} %"
        }

        val bomba = Regex(
            """Bomba:\s*(ON|OFF)""",
            RegexOption.IGNORE_CASE
        ).find(line)

        if (bomba != null) {
            pump.text = if (
                bomba.groupValues[1].equals(
                    "ON", ignoreCase = true
                )
            ) {
                "Bomba: encendida"
            } else {
                "Bomba: apagada"
            }
        }

        val modo = Regex(
            """Modo:\s*(AUTO|MANUAL)""",
            RegexOption.IGNORE_CASE
        ).find(line)

        if (modo != null) {
            mode.text = if (
                modo.groupValues[1].equals(
                    "AUTO", ignoreCase = true
                )
            ) {
                "Modo: automático"
            } else {
                "Modo: manual"
            }
        }
    }

    private fun appendLog(message: String) {
        log.text = (
            message + "\n" + log.text.toString()
        ).take(2500)
    }

    private fun send(command: String) {
        val s = socket

        if (!connected || s == null) {
            toast("Primero conecta el HC-05")
            return
        }

        thread {
            try {
                synchronized(s) {
                    s.outputStream.write(
                        "$command\n".toByteArray(
                            Charsets.UTF_8
                        )
                    )
                    s.outputStream.flush()
                }

                main.post {
                    appendLog("> $command")
                }

            } catch (e: Exception) {
                main.post {
                    appendLog(
                        "Error al enviar: ${e.message}"
                    )
                }
            }
        }
    }

    private fun disconnect() {
        connected = false
        connecting = false

        try {
            socket?.close()
        } catch (_: Exception) {}

        socket = null
        status.text = "● Desconectado"
        status.setTextColor(red)
    }

    private fun toast(message: String) {
        Toast.makeText(
            this, message, Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        main.removeCallbacks(clockTask)
        disconnect()
        super.onDestroy()
    }
}
