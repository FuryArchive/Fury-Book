package com.furybook.core.cloud

import com.furybook.core.json.jsonStringify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeCloudSyncProtocolTest {
    @Test
    fun packStateRoundTripsAllEnabledPackIds() {
        val value = CloudProtocol.packStateValue(setOf("dubl-chi-3.69", "homebrew-combat"))
        val bootstrap = CloudProtocol.parseBootstrap(
            """{
              "profile":{"nickname":"Tester"},
              "state":{"activeCharacterId":"","revision":7,"packState":${jsonStringify(value)}},
              "characters":[]
            }""".trimIndent(),
        )
        assertEquals(setOf("dubl-chi-3.69", "homebrew-combat"), bootstrap.enabledPackIds)
    }

    @Test
    fun legacyChiFlagStillRestoresChiPack() {
        val bootstrap = CloudProtocol.parseBootstrap(
            """{
              "profile":{},
              "state":{"revision":1,"packState":{"chiEnabled":true}},
              "characters":[]
            }""".trimIndent(),
        )
        assertTrue("dubl-chi-3.69" in bootstrap.enabledPackIds)
    }
}
