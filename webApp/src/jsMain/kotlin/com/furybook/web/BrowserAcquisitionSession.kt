package com.furybook.web

import com.furybook.dubl.acquisition.*
import kotlinx.browser.window

/** Verified-owner startup precedes all durable enumeration and legacy hydration. No rollout opt-in. */
internal class BrowserAcquisitionSession private constructor(
    val store:BrowserAcquisitionStore,initialStates:Map<String,StoredCharacter>,private val owner:BrowserOwnerScope,
) {
    var states:Map<String,StoredCharacter> = initialStates;private set
    /** Main and regression tests share this exact received-source-before-display boundary. */
    suspend fun ingestBootstrap(remote:CloudBootstrap) {
        owner.requireCurrent();require(remote.originalSourceReceived) { "Original legacy source has not been received" }
        receiveBrowserLegacySource(owner.id,remote.originalLegacySource)
        val loaded=linkedMapOf<String,StoredCharacter>()
        for(id in store.characterIds()) when(val head=store.load(id)) {
            is LoadResult.Loaded -> loaded[id]=head.state
            else -> throw AcquisitionPreviewUnavailable(head)
        }
        owner.requireCurrent()
        BrowserCharacterStore().replaceRaw(remote.snapshot,migrationSource=false)
        BrowserCharacterExtrasStore().replaceRaw(remote.extras)
        BrowserPackState.chiEnabled=remote.chiEnabled
        states=loaded.toMap()
    }
    companion object {
        suspend fun open(session:WebAuthSession,currentOwner:()->String?,databaseName:String="furybook-acquisitions-v1",
            indexedDb:()->dynamic={window.asDynamic().indexedDB},localRulesEnabled:Boolean=false,
            verifyOwner:suspend (WebAuthSession)->WebAuthSession?):BrowserAcquisitionSession {
            require(session.userId.isNotBlank() && currentOwner()==session.userId) { "Verified browser account changed" }
            // No owner-selected native access can occur until authentic principal verification succeeds.
            val verified=checkNotNull(verifyOwner(session)) { "Authenticated browser owner unavailable" }
            require(verified.userId.isNotBlank() && verified.userId==session.userId && currentOwner()==session.userId) { "Verified browser account changed" }
            val owner=BrowserOwnerScope.fromVerifiedSession(verified,currentOwner)
            owner.requireCurrent()
            val store=BrowserAcquisitionStore(owner,databaseName,localWritesEnabled=localRulesEnabled,
                indexedDb=indexedDb,gameplayFormatEnabled=true)
            try {
                check(store.initialize()) { store.storageError ?: "Browser durable storage unavailable" }
                val loaded=linkedMapOf<String,StoredCharacter>()
                for(id in store.characterIds()) {
                    owner.requireCurrent()
                    when(val head=store.load(id)) {
                        is LoadResult.Loaded -> loaded[id]=head.state
                        else -> throw AcquisitionPreviewUnavailable(head)
                    }
                }
                owner.requireCurrent()
                return BrowserAcquisitionSession(store,loaded.toMap(),owner)
            } catch(failure:Throwable) { store.close();throw failure }
        }
    }
}
