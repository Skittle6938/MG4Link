package com.andrerinas.openheadunit.aap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.aap.protocol.Channel
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.BluetoothHelper
import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message

/**
 * Relays Android's Bluetooth passkey to the current Android Auto peer, as stock AllGo does.
 * Only a successful authentication response for a challenge sent in this session can confirm it.
 */
internal class AapControlBluetooth(
        private val aapTransport: AapTransport,
        private val context: Context) : AapControl {

    private class Pairing(val address: String, val method: Int) {
        val expiresAt = SystemClock.elapsedRealtime() + PAIRING_TIMEOUT_MS
        var device: BluetoothDevice? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pairing: Pairing? = null
    private var receiverRegistered = false
    private var closed = false
    private val pairingTimeout = Runnable { expirePairing() }
    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_PAIRING_REQUEST) return
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1) !=
                BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION) return
            sendChallenge(device, intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1))
        }
    }

    @Synchronized
    override fun execute(message: AapMessage): Int {
        if (closed) return 0
        when (message.type) {
            AUTHENTICATION_RESULT -> {
                val result = message.parse(DynamicMessage.newBuilder(resultDescriptor)).build()
                val field = resultDescriptor.findFieldByName("status")
                if (result.hasField(field)) finishAuthentication(result.getField(field) as Int)
            }
            PAIRING_REQUEST -> handlePairingRequest(message)
            else -> AppLog.i("Bluetooth channel: unhandled message type=${message.type} size=${message.size}")
        }
        return 0
    }

    private fun handlePairingRequest(message: AapMessage) {
        val request = message.parse(DynamicMessage.newBuilder(requestDescriptor)).build()
        val phoneAddress = request.getField(requestDescriptor.findFieldByName("phone_address")) as String
        val method = request.getField(requestDescriptor.findFieldByName("pairing_method")) as Int
        if (!BluetoothAdapter.checkBluetoothAddress(phoneAddress.uppercase(java.util.Locale.ROOT))) {
            aapTransport.send(AapMessage(Channel.ID_BTH, PAIRING_RESPONSE, pairingResponse(false, INVALID_ADDRESS)))
            return
        }
        AllgoRelayNotifier.phoneAddressSeen(phoneAddress)
        val alreadyPaired = isBonded(phoneAddress)
        if (alreadyPaired) {
            clearPairing()
        } else if (!pairing?.address.equals(phoneAddress, ignoreCase = true) || pairing?.method != method) {
            clearPairing()
            ensureReceiver()
            if (receiverRegistered) {
                pairing = Pairing(phoneAddress, method)
                handler.postDelayed(pairingTimeout, PAIRING_TIMEOUT_MS)
            }
        }
        AppLog.i("Bluetooth pairing request: method=$method alreadyPaired=$alreadyPaired")
        // #59: over USB (cable or wireless adapter) the car may hold another phone on hands-free,
        // and calls from Android Auto start on the phone until the car switches. Not on native
        // wireless, where the hands-free link is the one that woke the phone (#36).
        if (alreadyPaired && !com.andrerinas.openheadunit.App.provide(context).commManager.isWirelessSession) {
            com.andrerinas.openheadunit.utils.BluetoothHelper.moveCarHandsFreeTo(context, phoneAddress)
        }
        aapTransport.send(AapMessage(Channel.ID_BTH, PAIRING_RESPONSE, pairingResponse(alreadyPaired)))
    }

    private fun ensureReceiver() {
        if (receiverRegistered) return
        runCatching {
            ContextCompat.registerReceiver(
                context, pairingReceiver, IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST),
                "android.permission.BLUETOOTH_PRIVILEGED", null, ContextCompat.RECEIVER_EXPORTED,
            )
        }.onSuccess { receiverRegistered = true }
            .onFailure { AppLog.w("Bluetooth pairing: cannot listen for the car's confirmation request") }
    }

    @Synchronized
    private fun sendChallenge(device: BluetoothDevice, key: Int) {
        val current = pairing ?: return
        if (current.device != null || key !in 0..999999 ||
            !current.address.equals(device.address, ignoreCase = true) ||
            SystemClock.elapsedRealtime() >= current.expiresAt) return
        current.device = device
        aapTransport.send(AapMessage(Channel.ID_BTH, AUTHENTICATION_DATA, authenticationData(key, current.method)))
        AppLog.i("Bluetooth pairing: sent authentication data to Android Auto")
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun finishAuthentication(status: Int?) {
        val current = pairing ?: return
        val device = current.device
        if (device == null && status != null) return
        clearPairing()
        if (device == null) return
        val accept = status == MESSAGE_STATUS_SUCCESS && SystemClock.elapsedRealtime() < current.expiresAt
        val confirmationSent = runCatching {
            device.bondState == BluetoothDevice.BOND_BONDING && device.setPairingConfirmation(accept)
        }.getOrDefault(false)
        AppLog.i("Bluetooth pairing: authentication status=$status, accept=$accept, confirmation sent=$confirmationSent")
    }

    @Synchronized
    private fun expirePairing() {
        val current = pairing ?: return
        if (SystemClock.elapsedRealtime() >= current.expiresAt) finishAuthentication(null)
    }

    private fun clearPairing() {
        handler.removeCallbacks(pairingTimeout)
        pairing = null
    }

    @Synchronized
    fun close() {
        closed = true
        clearPairing()
        if (receiverRegistered) {
            context.unregisterReceiver(pairingReceiver)
            receiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun isBonded(address: String): Boolean = try {
        BluetoothHelper.getBluetoothAdapter(context)?.bondedDevices
            ?.any { it.address.equals(address, ignoreCase = true) } ?: false
    } catch (e: Exception) {
        AppLog.w("Bluetooth pairing: bonded-device lookup failed: ${e.message}")
        false
    }

    companion object {
        const val PAIRING_REQUEST = 32769
        const val PAIRING_RESPONSE = 32770
        private const val AUTHENTICATION_DATA = 32771
        private const val AUTHENTICATION_RESULT = 32772
        private const val MESSAGE_STATUS_SUCCESS = 0
        private const val INVALID_ADDRESS = -12
        private const val PAIRING_TIMEOUT_MS = 30_000L

        private val messageTypes: List<Descriptors.Descriptor> by lazy {
            fun field(name: String, number: Int, type: FieldDescriptorProto.Type) =
                FieldDescriptorProto.newBuilder().setName(name).setNumber(number).setType(type)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL).build()
            val file = FileDescriptorProto.newBuilder()
                .setName("diauto_bluetooth_pairing.proto")
                .addMessageType(DescriptorProto.newBuilder().setName("BluetoothPairingRequest")
                    .addField(field("phone_address", 1, FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(field("pairing_method", 2, FieldDescriptorProto.Type.TYPE_INT32)))
                .addMessageType(DescriptorProto.newBuilder().setName("BluetoothPairingResponse")
                    .addField(field("status", 1, FieldDescriptorProto.Type.TYPE_INT32))
                    .addField(field("already_paired", 2, FieldDescriptorProto.Type.TYPE_BOOL)))
                .addMessageType(DescriptorProto.newBuilder().setName("BluetoothAuthenticationData")
                    .addField(field("auth_data", 1, FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(field("pairing_method", 2, FieldDescriptorProto.Type.TYPE_INT32)))
                .addMessageType(DescriptorProto.newBuilder().setName("BluetoothAuthenticationResult")
                    .addField(field("status", 1, FieldDescriptorProto.Type.TYPE_INT32)))
                .build()
            Descriptors.FileDescriptor.buildFrom(file, arrayOf()).messageTypes
        }
        private val requestDescriptor get() = messageTypes[0]
        private val responseDescriptor get() = messageTypes[1]
        private val authDescriptor get() = messageTypes[2]
        private val resultDescriptor get() = messageTypes[3]

        internal fun pairingResponse(alreadyPaired: Boolean, status: Int = MESSAGE_STATUS_SUCCESS): Message =
            DynamicMessage.newBuilder(responseDescriptor)
                .setField(responseDescriptor.findFieldByName("status"), status)
                .setField(responseDescriptor.findFieldByName("already_paired"), alreadyPaired)
                .build()

        internal fun authenticationData(passkey: Int, method: Int): Message =
            DynamicMessage.newBuilder(authDescriptor)
                .setField(authDescriptor.findFieldByName("auth_data"), passkey.toString())
                .setField(authDescriptor.findFieldByName("pairing_method"), method)
                .build()
    }
}
