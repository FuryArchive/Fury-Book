package com.furybook.ui.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountPanelLogicTest {
    @Test
    fun dotFollowsStatus() {
        assertEquals(AccountDot.GOLD, accountDotColor(FuryAccountStatus.CONFLICT))
        assertEquals(AccountDot.GREEN, accountDotColor(FuryAccountStatus.SYNCED))
        assertEquals(AccountDot.GREY, accountDotColor(FuryAccountStatus.SIGNED_OUT))
        assertEquals(AccountDot.RED, accountDotColor(FuryAccountStatus.ERROR))
        assertEquals(AccountDot.GREY, accountDotColor(FuryAccountStatus.PENDING))
    }

    @Test
    fun signOutAsksOnlyWithPending() {
        assertFalse(signOutNeedsChoice(0))
        assertTrue(signOutNeedsChoice(2))
    }
}
