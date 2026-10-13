package com.furybook.web

import com.furybook.dubl.acquisition.*
import kotlinx.browser.window

/** Verified-owner startup precedes all durable enumeration and legacy hydration. No rollout opt-in. */
internal class BrowserAcquisitionSession private constructor(
    val store:BrowserAcquisitionStore,val states:Map<String,StoredCharacter>,
) {
    companion object {
        suspend fun open(session:WebAuthSession,currentOwner:()->String?,databaseName:String="furybook-acquisitions-v1",
            indexedDb:()->dynamic={window.asDynamic().indexedDB},verifyOwner:suspend (WebAuthSession)->WebAuthSession?):BrowserAcquisitionSession {
            require(session.userId.isNotBlank() && currentOwner()==session.userId) { "Verified browser account changed" }
            // No owner-selected native access can occur until authentic principal verification succeeds.
            val verified=checkNotNull(verifyOwner(session)) { "Authenticated browser owner unavailable" }
            require(verified.userId.isNotBlank() && verified.userId==session.userId && currentOwner()==session.userId) { "Verified browser account changed" }
            val owner=BrowserOwnerScope.fromVerifiedSession(verified,currentOwner)
            owner.requireCurrent()
            val store=BrowserAcquisitionStore(owner,databaseName,indexedDb=indexedDb) // Production local rollout stays default-off.
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
                return BrowserAcquisitionSession(store,loaded.toMap())
            } catch(failure:Throwable) { store.close();throw failure }
        }
    }
}
