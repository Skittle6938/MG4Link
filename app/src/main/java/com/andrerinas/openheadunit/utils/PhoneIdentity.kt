package com.andrerinas.openheadunit.utils

import android.content.Context
import android.os.SystemClock
import com.google.protobuf.UnknownFieldSet

/**
 * Which paired phone is on the projection when it never gives its Bluetooth address (#59), as
 * through a wireless adapter: the adapter keeps the phone's Bluetooth to itself, and the phone's
 * label in the service discovery request is often just "Android".
 *
 * The request also carries the device name (field 5) and a per-phone instance id (field 6,
 * PhoneInfo.instance_id). The phone is looked up by its label, then by its device name, then by
 * the Bluetooth address last seen with its instance id: one the phone sent in a Bluetooth pairing
 * request, or the phone the driver connected hands-free to by hand during the session.
 */
object PhoneIdentity {
    private const val FIELD_PHONE_INFO = 6
    private const val FIELD_INSTANCE_ID = 1
    private const val PREFS = "phone_identity"

    /** A hands-free link the car makes on its own comes in the first seconds; later it is the driver's. */
    const val LEARN_AFTER_MS = 30_000L

    @Volatile private var sessionInstanceId: String? = null
    @Volatile private var sessionStartedAtMs = 0L

    /** PhoneInfo.instance_id from the service discovery request's unknown fields, or null. */
    fun instanceId(fields: UnknownFieldSet): String? {
        return try {
            val info = fields.getField(FIELD_PHONE_INFO)?.lengthDelimitedList?.firstOrNull() ?: return null
            UnknownFieldSet.parseFrom(info).getField(FIELD_INSTANCE_ID)?.lengthDelimitedList?.firstOrNull()
                ?.toStringUtf8()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The paired phone's address: by [label], by [deviceName], else the one [learned] for this
     * instance id. Generic names ("Android") are skipped.
     */
    fun resolve(paired: List<Pair<String, String?>>, label: String?, deviceName: String?, learned: String?): Pair<String, String>? {
        if (!BluetoothHelper.isGenericPhoneName(label)) {
            BluetoothHelper.pairedAddressForName(paired, label)?.let { return it to "label" }
        }
        if (!BluetoothHelper.isGenericPhoneName(deviceName)) {
            BluetoothHelper.pairedAddressForName(paired, deviceName)?.let { return it to "device name" }
        }
        val known = learned?.let { address -> paired.firstOrNull { it.first.equals(address, ignoreCase = true) }?.first }
        return known?.let { it to "remembered" }
    }

    /** Learn only during a session over USB with an instance id, and not from the car's own first links. */
    fun shouldLearn(instanceId: String?, sinceSessionStartMs: Long): Boolean =
        instanceId != null && sinceSessionStartMs >= LEARN_AFTER_MS

    fun sessionStarted(instanceId: String?) {
        sessionInstanceId = instanceId
        sessionStartedAtMs = SystemClock.elapsedRealtime()
    }

    fun sessionEnded() {
        sessionInstanceId = null
    }

    fun learned(context: Context, instanceId: String?): String? =
        instanceId?.let { prefs(context).getString(it, null) }

    /** The phone sent its own address (Bluetooth pairing request): always right. */
    fun addressSeen(context: Context, address: String) {
        remember(context, sessionInstanceId, address, "pairing request")
    }

    /** A hands-free link came up: the driver's choice, once the car's own first links are past. */
    fun handsFreeConnected(context: Context, address: String) {
        val id = sessionInstanceId
        if (!shouldLearn(id, SystemClock.elapsedRealtime() - sessionStartedAtMs)) return
        remember(context, id, address, "hands-free connected during the session")
    }

    private fun remember(context: Context, instanceId: String?, address: String, why: String) {
        if (instanceId == null) return
        val prefs = prefs(context)
        if (prefs.getString(instanceId, null).equals(address, ignoreCase = true)) return
        prefs.edit().putString(instanceId, address.uppercase()).apply()
        AppLog.i("PhoneIdentity: phone of this projection remembered ($why) (#59)")
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
