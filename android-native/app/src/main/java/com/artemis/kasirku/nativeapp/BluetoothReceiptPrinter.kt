package com.artemis.kasirku.nativeapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.artemis.kasirku.nativeapp.data.CheckoutLine
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PairedPrinter(val name: String, val address: String)

object BluetoothReceiptPrinter {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val PREFS = "kasirku_printer"
    private const val KEY_ADDRESS = "selected_printer_address"

    fun selectedAddress(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ADDRESS, null)

    fun saveSelectedAddress(context: Context, address: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ADDRESS, address).apply()
    }

    fun pairedPrinters(context: Context): List<PairedPrinter> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.orEmpty()
            .map { PairedPrinter(it.name ?: "Printer Bluetooth", it.address) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun print(
        context: Context,
        address: String,
        storeName: String,
        branchName: String,
        saleId: String,
        lines: List<CheckoutLine>,
        subtotal: Long,
        total: Long,
        paid: Long,
        change: Long,
        discount: Long = 0L
    ) = withContext(Dispatchers.IO) {
        require(address.isNotBlank()) { "Pilih printer Bluetooth terlebih dahulu." }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) error("Izin Bluetooth belum diberikan.")
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: error("Bluetooth tidak tersedia di perangkat ini.")
        if (!adapter.isEnabled) error("Aktifkan Bluetooth terlebih dahulu.")
        val device = adapter.getRemoteDevice(address)
        var socket: BluetoothSocket? = null
        try {
            adapter.cancelDiscovery()
            socket = device.createRfcommSocketToServiceRecord(sppUuid)
            socket.connect()
            socket.outputStream.use { output ->
                output.write(receiptBytes(storeName, branchName, saleId, lines, subtotal, discount, total, paid, change))
                output.flush()
            }
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun receiptBytes(
        storeName: String, branchName: String, saleId: String, lines: List<CheckoutLine>,
        subtotal: Long, discount: Long, total: Long, paid: Long, change: Long
    ): ByteArray {
        val out = ArrayList<Byte>()
        fun raw(vararg values: Int) { values.forEach { out.add(it.toByte()) } }
        fun line(value: String) { (ReceiptTextFormatter.printable(value) + "\n").toByteArray(Charsets.US_ASCII).forEach(out::add) }
        fun wrapped(value: String, width: Int = 32) =
            ReceiptTextFormatter.wrap(value, width)
        fun money(value: Long) = ReceiptTextFormatter.money(value)
        fun rupiah(value: Long) = ReceiptTextFormatter.rupiah(value)
        raw(0x1B, 0x40) // initialize
        raw(0x1B, 0x61, 1)
        raw(0x1B, 0x45, 1)
        wrapped(storeName).forEach(::line)
        raw(0x1B, 0x45, 0)
        wrapped(branchName).forEach(::line)
        line("--------------------------------")
        raw(0x1B, 0x61, 0)
        line("No: " + saleId.take(12))
        line("--------------------------------")
        lines.forEach { item ->
            wrapped(item.productName).forEach(::line)
            wrapped("  ${item.quantity} x ${money(item.unitPrice)} = ${money(item.lineTotal)}").forEach(::line)
        }
        line("--------------------------------")
        wrapped("Subtotal : ${rupiah(subtotal)}").forEach(::line)
        if (discount > 0L) wrapped("Diskon   : ${rupiah(discount)}").forEach(::line)
        wrapped("TOTAL    : ${rupiah(total)}").forEach(::line)
        wrapped("Tunai    : ${rupiah(paid)}").forEach(::line)
        wrapped("Kembali  : ${rupiah(change)}").forEach(::line)
        raw(0x1B, 0x61, 1)
        line("")
        line("Terima kasih")
        line("")
        raw(0x1D, 0x56, 0x41, 0x10) // partial cut
        return out.toByteArray()
    }
}
