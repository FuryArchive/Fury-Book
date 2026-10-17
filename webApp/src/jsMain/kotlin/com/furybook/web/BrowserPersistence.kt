package com.furybook.web

import com.furybook.dubl.data.LegacyExtrasDocument
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.CharacterSheetExtras

private object BrowserSessionMemory {
    var ownerId: String? = null
    var snapshotRaw: String? = null
    var legacySourceEligible: Boolean = true
    var originalSource: BrowserLegacySourceState = BrowserLegacySourceState.NotLoaded
    var originalSnapshotReceived:Boolean = false
    var originalExtrasReceived:Boolean = false
    var extrasRaw: String = "{\"version\":1,\"characters\":{}}"
    var chiEnabled: Boolean = false

    fun clear() {
        ownerId = null
        snapshotRaw = null
        legacySourceEligible = true
        originalSource = BrowserLegacySourceState.NotLoaded
        originalSnapshotReceived=false;originalExtrasReceived=false
        extrasRaw = "{\"version\":1,\"characters\":{}}"
        chiEnabled = false
    }
}

internal sealed interface BrowserLegacySourceState {
    data object NotLoaded:BrowserLegacySourceState
    data object Absent:BrowserLegacySourceState
    data class Present(val source:BrowserLegacySource):BrowserLegacySourceState
    data class Changed(val previous:BrowserLegacySourceState):BrowserLegacySourceState
}
internal fun BrowserLegacySourceState.originalSourceForExport():BrowserLegacySource? = when(this) {
    is BrowserLegacySourceState.Present -> source
    is BrowserLegacySourceState.Changed -> previous.originalSourceForExport()
    else -> null
}
private fun invalidateReceivedLegacySource() {
    if(BrowserSessionMemory.originalSource !is BrowserLegacySourceState.Changed)
        BrowserSessionMemory.originalSource=BrowserLegacySourceState.Changed(BrowserSessionMemory.originalSource)
}

fun clearBrowserSessionData() = BrowserSessionMemory.clear()

/** Capture actual present tab bytes only. Never ask the fresh-generating legacy store for a source. */
internal fun bindBrowserSessionOwner(ownerId:String) {
    require(ownerId.isNotBlank())
    require(BrowserSessionMemory.ownerId==null || BrowserSessionMemory.ownerId==ownerId) { "Browser tab belongs to another account" }
    BrowserSessionMemory.ownerId=ownerId
}
internal fun captureBrowserLegacySource(ownerId:String):BrowserLegacySource? {
    return browserLegacySourceState(ownerId).originalSourceForExport()
}
internal fun browserLegacySourceState(ownerId:String):BrowserLegacySourceState =
    if(BrowserSessionMemory.ownerId==ownerId) BrowserSessionMemory.originalSource else BrowserLegacySourceState.NotLoaded
internal fun receiveBrowserLegacySource(ownerId:String,source:BrowserLegacySource?) {
    bindBrowserSessionOwner(ownerId)
    BrowserSessionMemory.originalSource=source?.let(BrowserLegacySourceState::Present) ?: BrowserLegacySourceState.Absent
    BrowserSessionMemory.originalSnapshotReceived=true;BrowserSessionMemory.originalExtrasReceived=true
}
private fun captureGenuineLegacySnapshot(raw:String) {
    val previous=BrowserSessionMemory.originalSource.originalSourceForExport()
    BrowserSessionMemory.originalSource=BrowserLegacySourceState.Present(BrowserLegacySource(raw,previous?.extrasRaw ?: BrowserSessionMemory.extrasRaw))
}
private fun captureGenuineLegacyExtras(raw:String) {
    val previous=BrowserSessionMemory.originalSource.originalSourceForExport() ?: return
    BrowserSessionMemory.originalSource=BrowserLegacySourceState.Present(previous.copy(extrasRaw=raw))
}

fun webUuid(): String = js("crypto.randomUUID()") as String

class BrowserCharacterStore : CharacterStore {
    override fun load() = BrowserSessionMemory.snapshotRaw
        ?.let { raw -> runCatching { SnapshotCodec.decode(raw, ::webUuid) }.getOrNull() }
        ?: SnapshotCodec.fresh(::webUuid)

    override fun save(snapshot: com.furybook.dubl.model.AppSnapshot) {
        BrowserSessionMemory.snapshotRaw = SnapshotCodec.encode(snapshot)
        if(BrowserSessionMemory.originalSnapshotReceived) invalidateReceivedLegacySource()
        else if(BrowserSessionMemory.legacySourceEligible) captureGenuineLegacySnapshot(BrowserSessionMemory.snapshotRaw!!)
    }

    fun hasStoredState(): Boolean = BrowserSessionMemory.snapshotRaw != null

    fun raw(): String = BrowserSessionMemory.snapshotRaw
        ?: SnapshotCodec.encode(SnapshotCodec.fresh(::webUuid)).also { BrowserSessionMemory.snapshotRaw = it }

    fun replaceRaw(raw: String, migrationSource: Boolean = true) {
        if(migrationSource) {
            captureGenuineLegacySnapshot(raw);BrowserSessionMemory.originalSnapshotReceived=true
        }
        SnapshotCodec.decode(raw, ::webUuid)
        BrowserSessionMemory.snapshotRaw = raw
        BrowserSessionMemory.legacySourceEligible = migrationSource
    }
}

class BrowserCharacterExtrasStore : CharacterExtrasStore {
    private var document = LegacyExtrasDocument.decode(BrowserSessionMemory.extrasRaw)

    override fun load(characterId: String): CharacterSheetExtras = document.load(characterId)

    override fun save(characterId: String, extras: CharacterSheetExtras) {
        val next = document.withExtras(characterId, extras)
        persist(next)
    }

    override fun delete(characterId: String) {
        val next = document.withoutCharacter(characterId)
        if (next !== document) persist(next)
    }

    fun raw(): String = document.encode()

    fun replaceRaw(raw: String) {
        val next = LegacyExtrasDocument.decode(raw)
        if (BrowserSessionMemory.legacySourceEligible) {
            captureGenuineLegacyExtras(raw); BrowserSessionMemory.originalExtrasReceived = true
        }
        document = next
        BrowserSessionMemory.extrasRaw = raw
    }

    private fun persist(next: LegacyExtrasDocument) {
        BrowserSessionMemory.extrasRaw = next.encode()
        document = next
        if (BrowserSessionMemory.originalExtrasReceived) invalidateReceivedLegacySource()
        else if (BrowserSessionMemory.legacySourceEligible) captureGenuineLegacyExtras(BrowserSessionMemory.extrasRaw)
    }
}

object BrowserPackState {
    var chiEnabled: Boolean
        get() = BrowserSessionMemory.chiEnabled
        set(value) { BrowserSessionMemory.chiEnabled = value }
}
