package com.riego.inteligente

import android.Manifest
import android.app.Activity
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
import android.view.Gravity
import android.view.View
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
    private val serviceUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var connected = false
    private var automatic = false
    private lateinit var status: TextView
    private lateinit var humidity: TextView
    private lateinit var pump: TextView
    private lateinit var clock: TextView
    private lateinit var mode: TextView
    private lateinit var log: TextView
    private val dark = Color.rgb(13, 48, 39)
    private val green = Color.rgb(16, 132, 88)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        updateClock()
        requestBtPermission()
    }

    private fun rounded(color: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = 28f }

    private fun text(value: String, size: Float, color: Int = dark, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(6, 5, 6, 5)
        }

    private fun card(parent: LinearLayout): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE)
            setPadding(25, 22, 25, 22)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.bottomMargin = 20
            parent.addView(this, lp)
        }

    private fun button(parent: LinearLayout, label: String, color: Int, click: () -> Unit) {
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(color)
            setOnClickListener { click() }
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = 10
            parent.addView(this, lp)
        }
    }

    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(238, 247, 242)) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 38, 32, 36)
        }
        scroll.addView(body)
        setContentView(scroll)
        body.addView(text("🌱 Sistema de Riego", 27f, dark, true))
        body.addView(text("Panel inteligente · Arduino UNO", 14f, green))

        val btCard = card(body)
        btCard.addView(text("Conexión Bluetooth", 19f, dark, true))
        status = text("● Desconectado", 15f, Color.rgb(170, 70, 60))
        btCard.addView(status)
        button(btCard, "Conectar al HC-05", green) { chooseDevice() }
        button(btCard, "Desconectar", Color.rgb(91, 105, 104)) { disconnect() }

        val sensorCard = card(body)
        sensorCard.addView(text("💧 Humedad del suelo", 19f, dark, true))
        humidity = text("-- %", 36f, green, true)
        sensorCard.addView(humidity)
        sensorCard.addView(text("Lectura enviada por el Arduino", 12f, Color.GRAY))

        val dateCard = card(body)
        dateCard.addView(text("📅 Fecha y hora del teléfono", 19f, dark, true))
        clock = text("", 20f, dark, true)
        dateCard.addView(clock)

        val control = card(body)
        control.addView(text("🚿 Control de riego", 19f, dark, true))
        pump = text("Bomba: sin datos", 16f)
        control.addView(pump)
        mode = text("Modo: manual", 15f)
        control.addView(mode)
        button(control, "Encender riego", green) { send("ON") }
        button(control, "Apagar riego", Color.rgb(178, 67, 57)) { send("OFF") }
        button(control, "Activar modo automático", Color.rgb(48, 99, 160)) { send("AUTO") }
        button(control, "Activar modo manual", Color.rgb(95, 102, 112)) { send("MANUAL") }

        val console = card(body)
        console.addView(text("📟 Terminal Bluetooth", 19f, dark, true))
        log = text("Esperando conexión…", 12f, Color.DKGRAY)
        console.addView(log)
        button(console, "Solicitar lectura", green) { send("STATUS") }
        val row = LinearLayout(this)
        val entry = EditText(this).apply {
            hint = "Comando personalizado"
            isSingleLine = true
        }
        row.addView(entry, LinearLayout.LayoutParams(0, -2, 1f))
        console.addView(row)
        row.addView(Button(this).apply {
            text = "Enviar"
            setOnClickListener {
                val cmd = entry.text.toString().trim()
                if (cmd.isNotEmpty()) send(cmd)
                entry.text.clear()
            }
        })
    }

    private fun updateClock() {
        if (isFinishing) return
        clock.text = SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault()).format(Date())
        main.postDelayed({ updateClock() }, 1000)
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestBtPermission() {
        if (Build.VERSION.SDK_INT >= 31 && !hasPermission())
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
    }

    private fun chooseDevice() {
        if (!hasPermission()) {
            requestBtPermission()
            toast("Concede permiso Bluetooth y vuelve a pulsar Conectar")
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            toast("Activa Bluetooth y empareja HC-05 desde Ajustes")
            return
        }
        val devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) {
            toast("Primero empareja el HC-05 en los ajustes Bluetooth del teléfono")
            return
        }
        val names = devices.map { "${it.name ?: "Dispositivo"} (${it.address})" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Dispositivos emparejados")
            .setItems(names) { _, which -> connect(adapter, devices[which]) }
            .show()
    }

    private fun connect(adapter: BluetoothAdapter, device: BluetoothDevice) {
        status.text = "● Conectando…"
        thread {
            try {
                // RFCOMM SPP: conexión clásica HC-05, no BLE.
                val s = device.createRfcommSocketToServiceRecord(serviceUuid)
                adapter.cancelDiscovery()
                s.connect()
                socket = s
                connected = true
                main.post { status.text = "● Conectado a ${device.name}"; status.setTextColor(green) }
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                while (connected) {
                    val line = reader.readLine() ?: break
                    main.post { processLine(line) }
                }
            } catch (e: Exception) {
                main.post { toast("Error Bluetooth: ${e.message ?: "sin conexión"}") }
            } finally {
                closeSocket()
                main.post { status.text = "● Desconectado"; status.setTextColor(Color.RED) }
            }
        }
    }

    private fun processLine(line: String) {
        log.text = (line + "\n" + log.text.toString()).take(1500)
        // Protocolo: HUM:45;PUMP:ON;MODE:AUTO
        line.split(";").forEach { field ->
            val pair = field.trim().split(":", limit = 2)
            if (pair.size != 2) return@forEach
            when (pair[0].uppercase(Locale.US)) {
                "HUM" -> humidity.text = "${pair[1]} %"
                "PUMP" -> pump.text = "Bomba: ${if (pair[1] == "ON") "encendida" else "apagada"}"
                "MODE" -> {
                    automatic = pair[1] == "AUTO"
                    mode.text = "Modo: ${if (automatic) "automático" else "manual"}"
                }
            }
        }
    }

    private fun send(command: String) {
        val s = socket
        if (!connected || s == null) {
            toast("Conecta el HC-05 primero")
            return
        }
        thread {
            try {
                synchronized(s) {
                    s.outputStream.write("$command\n".toByteArray(Charsets.UTF_8))
                    s.outputStream.flush()
                }
                main.post { log.text = "> $command\n${log.text}" }
            } catch (e: Exception) {
                main.post { toast("No se pudo enviar: ${e.message}") }
            }
        }
    }

    private fun disconnect() {
        connected = false
        closeSocket()
        status.text = "● Desconectado"
        status.setTextColor(Color.RED)
    }

    private fun closeSocket() {
        connected = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}
