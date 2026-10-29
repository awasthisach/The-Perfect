package com.vvf.smartmanager.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class PinWrappedVaultKeyTest {
    private val envelope = PinWrappedVaultKey(SecureRandom())

    @Test
    fun wrapsAndUnwrapsWithCorrectPinAndDevicePepper() {
        val dataKey = ByteArray(32) { (it * 7 + 3).toByte() }
        val pin = "482951".toCharArray()
        val pepper = ByteArray(32) { (it + 19).toByte() }
        val wrapped = envelope.wrap(dataKey, pin, pepper)

        assertTrue(pin.all { it == '\u0000' })
        assertTrue(wrapped.copyOfRange(0, 4).contentEquals(byteArrayOf(86, 86, 80, 75)))
        val unlockPin = "482951".toCharArray()
        val unwrapped = envelope.unwrap(wrapped, unlockPin, pepper)

        assertArrayEquals(dataKey, unwrapped)
        assertTrue(unlockPin.all { it == '\u0000' })
        dataKey.fill(0)
        unwrapped.fill(0)
        pepper.fill(0)
    }

    @Test
    fun rejectsWrongPinAndWrongDevicePepper() {
        val dataKey = ByteArray(32) { it.toByte() }
        val pepper = ByteArray(32) { (it + 1).toByte() }
        val wrapped = envelope.wrap(dataKey, "123456".toCharArray(), pepper)

        assertThrows(AEADBadTagException::class.java) {
            envelope.unwrap(wrapped, "654321".toCharArray(), pepper)
        }
        assertThrows(AEADBadTagException::class.java) {
            envelope.unwrap(wrapped, "123456".toCharArray(), ByteArray(32) { 99 })
        }
        dataKey.fill(0)
        pepper.fill(0)
    }

    @Test
    fun rejectsTamperingTruncationAndUnsupportedVersion() {
        val dataKey = ByteArray(32) { (it + 2).toByte() }
        val pepper = ByteArray(32) { (it + 4).toByte() }
        val wrapped = envelope.wrap(dataKey, "482951".toCharArray(), pepper)

        val tampered = wrapped.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(AEADBadTagException::class.java) {
            envelope.unwrap(tampered, "482951".toCharArray(), pepper)
        }
        assertThrows(IOException::class.java) {
            envelope.unwrap(wrapped.copyOf(20), "482951".toCharArray(), pepper)
        }
        val unsupported = wrapped.copyOf().also { it[4] = 2 }
        assertThrows(IOException::class.java) {
            envelope.unwrap(unsupported, "482951".toCharArray(), pepper)
        }
        dataKey.fill(0)
        pepper.fill(0)
    }

    @Test
    fun refusesWeakPepperAndInvalidDataKey() {
        assertThrows(IllegalArgumentException::class.java) {
            envelope.wrap(ByteArray(16), "482951".toCharArray(), ByteArray(32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            envelope.wrap(ByteArray(32), "482951".toCharArray(), ByteArray(8))
        }
        assertFalse(ByteArray(0).isNotEmpty())
    }
}
