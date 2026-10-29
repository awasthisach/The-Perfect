package com.vvf.smartmanager.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PinWrappedVaultKeyTest {
    @Test
    fun roundTripUsesAuthenticatedVersionedEnvelopeAndClearsPinBuffers() {
        val helper = PinWrappedVaultKey()
        val dataKey = ByteArray(32) { (it * 3 + 1).toByte() }
        val pin = "482951".toCharArray()
        val envelope = helper.wrap(dataKey, pin)

        assertTrue(pin.all { it == '\u0000' })
        assertTrue(envelope.size > dataKey.size)
        assertArrayEquals(byteArrayOf(0x56, 0x4b, 1), envelope.copyOfRange(0, 3))

        val unlockPin = "482951".toCharArray()
        val unwrapped = helper.unwrap(envelope, unlockPin)
        assertArrayEquals(dataKey, unwrapped)
        assertTrue(unlockPin.all { it == '\u0000' })
        unwrapped.fill(0)
        dataKey.fill(0)
    }

    @Test
    fun wrongPinAndTamperingFailAuthentication() {
        val helper = PinWrappedVaultKey()
        val dataKey = ByteArray(32) { it.toByte() }
        val envelope = helper.wrap(dataKey, "482951".toCharArray())

        try {
            helper.unwrap(envelope, "000000".toCharArray())
            throw AssertionError("Wrong PIN unexpectedly unwrapped the vault key")
        } catch (expected: SecurityException) {
            assertTrue(expected.message.orEmpty().contains("authentication"))
        }

        val tampered = envelope.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        try {
            helper.unwrap(tampered, "482951".toCharArray())
            throw AssertionError("Tampered envelope unexpectedly unwrapped")
        } catch (expected: SecurityException) {
            assertTrue(expected.message.orEmpty().contains("authentication"))
        }
        dataKey.fill(0)
    }

    @Test
    fun malformedOrUnsupportedEnvelopeIsRejectedAndPinIsCleared() {
        val helper = PinWrappedVaultKey()
        val pin = "1234".toCharArray()
        try {
            helper.unwrap(byteArrayOf(1, 2, 3), pin)
            throw AssertionError("Malformed envelope unexpectedly accepted")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("length"))
        }
        assertTrue(pin.all { it == '\u0000' })
    }

    @Test
    fun saltAndNonceAreRandomForEachWrap() {
        val helper = PinWrappedVaultKey()
        val key = ByteArray(32) { 7 }
        val first = helper.wrap(key, "482951".toCharArray())
        val second = helper.wrap(key, "482951".toCharArray())
        assertFalse(first.contentEquals(second))
        key.fill(0)
    }
}
