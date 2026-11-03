// ═══════════════════════════════════════════════════════════════════════
// v0.51.2 (crash-fix wave) — DeviceId derivation unit tests.
//
// The LIVE Firestore rules pin every device-scoped path to a deviceId of
// EXACTLY 24 lowercase hex characters (devicePresence/liveLocations doc
// ids, locationAuthorizations.deviceId, locationHistory record ids).
// The W1 stableDeviceId returned raw ANDROID_ID (16 hex) or a dashed
// UUID (36 chars) — every such write was PERMISSION_DENIED, and the
// unguarded heartbeat then killed the app seconds after sign-in (the
// Owner's "opens, closes after a few seconds" report).
//
// These tests pin the contract of the fix, deriveDeviceId: exact length,
// lowercase-hex-only alphabet, determinism (same seed → same id),
// seed sensitivity (different seeds → different ids), and real-world
// seed shapes (16-hex ANDROID_ID, dashed UUID, blank-adjacent junk).
// Pure JVM, no Android dependency — same gate shape as the Phase 10
// MovementClassifier suite (:app:testReleaseUnitTest runs it in CI
// before assemble).
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdTest {

    private val hex24 = Regex("^[0-9a-f]{24}$")

    @Test
    fun `exact 24 lowercase hex characters`() {
        assertTrue(hex24.matches(deriveDeviceId("9774d56d682e549c")))
        assertTrue(hex24.matches(deriveDeviceId("550e8400-e29b-41d4-a716-446655440000")))
        assertTrue(hex24.matches(deriveDeviceId("")))
    }

    @Test
    fun `deterministic for the same seed`() {
        assertEquals(
            deriveDeviceId("9774d56d682e549c"),
            deriveDeviceId("9774d56d682e549c"),
        )
        // Two calls with the persisted-UUID seed stay stable too.
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals(deriveDeviceId(uuid), deriveDeviceId(uuid))
    }

    @Test
    fun `different seeds give different ids`() {
        assertNotEquals(
            deriveDeviceId("9774d56d682e549c"),
            deriveDeviceId("9774d56d682e549d"),
        )
        assertNotEquals(
            deriveDeviceId("550e8400-e29b-41d4-a716-446655440000"),
            deriveDeviceId("550e8400-e29b-41d4-a716-446655440001"),
        )
    }

    @Test
    fun `android id and its uuid fallback never collide`() {
        // The two real seed shapes of stableDeviceId must not fold onto
        // the same id (the fallback fires when ANDROID_ID is blank).
        assertNotEquals(
            deriveDeviceId("9774d56d682e549c"),
            deriveDeviceId("6ba7b810-9dad-11d1-80b4-00c04fd430c8"),
        )
    }

    @Test
    fun `rule regexes accept the derived id in every device-scoped shape`() {
        val id = deriveDeviceId("9774d56d682e549c")
        val uid = "xQ7cFakeUidForRulesTests000"
        // devicePresence / liveLocations doc ids (literal underscore —
        // Java regex rejects the \_ escape Firestore's rules use)
        assertTrue("${uid}_$id".matches(Regex("$uid" + "_[0-9a-f]{24}")))
        // locationHistory record id (deviceId_timestamp)
        assertTrue("${id}_1730000000000".matches("[0-9a-f]{24}_[0-9]+".toRegex()))
        // locationAuthorizations.deviceId field
        assertTrue(id.matches("[0-9a-f]{24}".toRegex()))
    }

    @Test
    fun `seed length does not leak into the id`() {
        // A 36-char dashed UUID seed still yields a 24-hex id — the
        // format contract is independent of the seed shape.
        assertEquals(24, deriveDeviceId("550e8400-e29b-41d4-a716-446655440000").length)
        assertEquals(24, deriveDeviceId("a").length)
        assertEquals(24, deriveDeviceId("a".repeat(4096)).length)
    }
}
