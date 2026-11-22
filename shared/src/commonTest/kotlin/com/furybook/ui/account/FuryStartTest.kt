package com.furybook.ui.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuryStartTest {
    @Test
    fun startModeDecidesLogin() {
        assertFalse(furyShowsLogin(FuryStartMode.SIGNED_IN, signedIn = true, hasLocalCharacters = true))
        assertFalse(furyShowsLogin(FuryStartMode.NONE, signedIn = true, hasLocalCharacters = false))
        assertFalse(furyShowsLogin(FuryStartMode.GUEST, signedIn = false, hasLocalCharacters = true))
        assertTrue(furyShowsLogin(FuryStartMode.NONE, signedIn = false, hasLocalCharacters = false))
        // An update from before the login screen: characters on the device, no account, no choice yet.
        assertTrue(furyShowsLogin(FuryStartMode.NONE, signedIn = false, hasLocalCharacters = true))
        // Signed out after being signed in: back to the login screen.
        assertTrue(furyShowsLogin(FuryStartMode.SIGNED_IN, signedIn = false, hasLocalCharacters = false))
    }

    @Test
    fun startModeKeysRoundTrip() {
        FuryStartMode.entries.forEach { assertEquals(it, FuryStartMode.fromKey(it.key)) }
        assertEquals(FuryStartMode.NONE, FuryStartMode.fromKey(null))
        assertEquals(FuryStartMode.NONE, FuryStartMode.fromKey("garbage"))
    }
}
