package com.furybook.ui.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DetachedSignInTest {
    /** The login screen leaves composition as soon as the session appears; the first sync must still finish. */
    @Test
    fun signInWorkSurvivesTheCallerBeingCancelled() = runBlocking {
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var finished = false
        val caller = launch(Dispatchers.Default) {
            detachedFromCaller(controllerScope) { delay(200); finished = true }
        }
        delay(50)
        caller.cancel()
        delay(400)
        assertTrue(finished)
    }
}
