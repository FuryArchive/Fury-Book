@file:Suppress("UnsafeCastFromDynamic")
package com.furybook.web

import com.furybook.dubl.acquisition.*
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An authenticated-session identity and its current-account check, never an auth credential. */
class BrowserOwnerScope private constructor(internal val id:String,private val currentOwner:()->String?) {
    internal fun requireCurrent() { require(id.isNotBlank() && currentOwner()==id) { "Verified browser account changed" } }
    companion object {
        internal fun fromVerifiedSession(session:WebAuthSession,currentOwner:()->String?):BrowserOwnerScope {
            require(session.userId.isNotBlank());return BrowserOwnerScope(session.userId,currentOwner)
        }
    }
}

data class BrowserLegacySource(val snapshotRaw:String,val extrasRaw:String?)
enum class BrowserAcquisitionWriteStage { BeforePut, AfterPutSucceeded }

/** Whole exact envelope, one native IDB readwrite transaction, acknowledged only on complete. */
class BrowserAcquisitionStore(
    private val owner:BrowserOwnerScope,
    private val databaseName:String="furybook-acquisitions-v1",
    private val localWritesEnabled:Boolean=false,
    private val legacySource:()->BrowserLegacySource?={captureBrowserLegacySource(owner.id)},
    private val fault:(BrowserAcquisitionWriteStage)->Unit={},
    private val indexedDb:()->dynamic={window.asDynamic().indexedDB},
) : AcquisitionStore {
    private var database:dynamic=null
    private var ready=false
    private var ids:Set<String> = emptySet()
    var observedDurability:String?=null;private set
    var storageError:String?=null;private set
    val capabilities:AcquisitionCapabilities get()=AcquisitionCapabilities(localWritesEnabled && ready && storageError==null && observedDurability=="strict" && runCatching { owner.requireCurrent() }.isSuccess,false,localWritesEnabled && ready && storageError==null)
    val knownCharacterIds:Set<String> get() { owner.requireCurrent();return ids.toSet() }

    suspend fun initialize():Boolean = try {
        owner.requireCurrent()
        if(database==null) database=openDatabase()
        transaction<Unit>("readwrite") { _,_,set -> set(Unit) }
        owner.requireCurrent();ready=observedDurability=="strict"
        check(ready) { "Strict IndexedDB durability is unavailable" }
        storageError=null;true
    } catch(cancelled:CancellationException) { throw cancelled }
      catch(failure:Throwable) { ready=false;storageError=failure.message ?: "Browser durable storage unavailable";false }

    fun close() { database?.close();database=null;ready=false;ids=emptySet() }

    private suspend fun openDatabase():dynamic = suspendCancellableCoroutine { continuation ->
        owner.requireCurrent()
        val request=try { indexedDb().open(databaseName,1) }
            catch(failure:Throwable) { continuation.resumeWithException(failure);return@suspendCancellableCoroutine }
        request.onupgradeneeded={ _:dynamic ->
            try {
                val db=request.result
                if(!db.objectStoreNames.contains(ENVELOPES)) db.createObjectStore(ENVELOPES)
                if(!db.objectStoreNames.contains(SOURCES)) db.createObjectStore(SOURCES)
            } catch(failure:Throwable) { request.transaction.abort();if(continuation.isActive) continuation.resumeWithException(failure) }
        }
        request.onsuccess={ _:dynamic ->
            val db=request.result
            if(continuation.isActive) {
                db.onversionchange={ _:dynamic -> close() }
                continuation.resume(db)
            } else db.close()
        }
        request.onerror={ _:dynamic -> if(continuation.isActive) continuation.resumeWithException(storageFailure(request.error)) }
        request.onblocked={ _:dynamic -> if(continuation.isActive) continuation.resumeWithException(IllegalStateException("IndexedDB opening is blocked by another context")) }
    }

    /** No suspension inside request callbacks: a Promise gap could auto-commit the native tx. */
    private suspend fun <T> transaction(mode:String,body:(dynamic,dynamic,(T)->Unit)->Unit):T = suspendCancellableCoroutine { continuation ->
        owner.requireCurrent()
        var answer:T?=null;var answered=false;var failure:Throwable?=null
        val tx=try {
            check(database!=null) { "Browser durable storage is not initialized" }
            if(mode=="readwrite") {
                val options=js("({durability:'strict'})")
                val value=database.transaction(arrayOf(ENVELOPES,SOURCES),mode,options)
                observedDurability=value.durability as? String
                if(observedDurability!="strict") { value.abort();error("Strict IndexedDB durability is unavailable") }
                value
            } else database.transaction(arrayOf(ENVELOPES,SOURCES),mode)
        } catch(error:Throwable) { continuation.resumeWithException(error);return@suspendCancellableCoroutine }
        tx.oncomplete={ _:dynamic ->
            if(continuation.isActive) try {
                owner.requireCurrent();check(answered) { "IndexedDB transaction completed without a result" }
                @Suppress("UNCHECKED_CAST") continuation.resume(answer as T)
            } catch(error:Throwable) { continuation.resumeWithException(error) }
        }
        tx.onabort={ _:dynamic -> if(continuation.isActive) continuation.resumeWithException(failure ?: storageFailure(tx.error)) }
        tx.onerror={ _:dynamic -> Unit } // Native errors abort; only abort/complete finishes the call.
        continuation.invokeOnCancellation { runCatching { tx.abort() } }
        val abort:(Throwable)->Unit={ error -> failure=error;runCatching { tx.abort() } }
        // Every asynchronous callback must use this guard before touching typed state.
        tx.__furybookFailure=abort
        try { body(tx,tx.objectStore(ENVELOPES)) { value -> answer=value;answered=true } }
        catch(error:Throwable) { abort(error) }
    }

    private fun callback(tx:dynamic,action:()->Unit) {
        try { owner.requireCurrent();action() } catch(error:Throwable) { tx.__furybookFailure(error) }
    }
    private fun absent(value:dynamic):Boolean=value==null || js("typeof value === 'undefined'") as Boolean
    private fun source(raw:dynamic):BrowserLegacySource? {
        if(absent(raw)) return null
        val text=raw as? String ?: error("Corrupt legacy source backup")
        canonicalJson(text) // Duplicate/invalid JSON refusal before native parsing.
        val root=js("JSON.parse")(text)
        val keys=js("Object.keys")(root) as Array<String>
        require(keys.toSet()==setOf("snapshotRaw","extrasRaw")) { "Unsupported legacy source backup" }
        val snapshot=root.snapshotRaw as? String ?: error("Corrupt legacy snapshot backup")
        val extras=if(root.extrasRaw==null) null else root.extrasRaw as? String ?: error("Corrupt legacy extras backup")
        return BrowserLegacySource(snapshot,extras)
    }
    private fun encodeSource(value:BrowserLegacySource):String {
        val root=js("({})");root.snapshotRaw=value.snapshotRaw;root.extrasRaw=value.extrasRaw
        return canonicalJson(js("JSON.stringify")(root) as String)
    }
    private fun classify(characterId:String,head:dynamic,backup:BrowserLegacySource?):LoadResult {
        val present=legacySource()
        if(!absent(head)) {
            val raw=head as? String ?: return LoadResult.Corrupt("","Envelope is not text; original IDB value retained")
            val loaded=decodeEnvelope(raw)
            if(loaded is LoadResult.MigrationRequired) return LoadResult.Corrupt(raw,"Legacy snapshot found in acquisition container")
            if(loaded is LoadResult.Loaded) {
                if(loaded.state.character.id!=characterId) return LoadResult.Corrupt(raw,"Character scope differs")
                loaded.state.ledger.opening.legacyMigration?.origin?.let { origin ->
                    if(origin.extrasSource==null || backup==null) return LoadResult.Unsupported(raw,"Exact legacy source backup unavailable")
                    if(origin.raw!=backup.snapshotRaw || origin.extrasSource?.raw!=backup.extrasRaw)
                        return LoadResult.Corrupt(raw,"Legacy backup differs from migration origin")
                    if(present!=null && present!=backup) return LoadResult.LegacyImportConflict(raw,present.snapshotRaw,present.extrasRaw,"Legacy source changed after migration")
                }
            }
            return loaded
        }
        if(present!=null && backup!=null && present!=backup)
            return LoadResult.LegacyImportConflict("",present.snapshotRaw,present.extrasRaw,"Legacy source changed after another migration")
        val input=backup ?: present ?: return LoadResult.Missing(characterId)
        val legacy=decodeEnvelope(input.snapshotRaw)
        if(legacy !is LoadResult.MigrationRequired) return when(legacy) {
            is LoadResult.Loaded -> LoadResult.Corrupt(input.snapshotRaw,"Acquisition envelope is not a legacy snapshot")
            else -> legacy
        }
        // Strict validation above precedes the existing supported legacy compatibility projection.
        val rows=js("JSON.parse")(canonicalJson(input.snapshotRaw)).characters as Array<dynamic>
        return if(rows.any { it.id==characterId }) legacy else LoadResult.Missing(characterId)
    }
    private suspend fun <T> read(characterId:String,mode:String="readonly",action:(dynamic,dynamic,BrowserLegacySource?,(T)->Unit)->Unit):T =
        transaction(mode) { tx,store,set ->
            val head=store.get(arrayOf(owner.id,characterId));val backup=tx.objectStore(SOURCES).get(owner.id)
            var pending=2
            val complete:()->Unit={ if(--pending==0) callback(tx) { action(tx,head.result,source(backup.result),set) } }
            head.onsuccess={ _:dynamic -> complete() };backup.onsuccess={ _:dynamic -> complete() }
        }
    override suspend fun load(characterId:String):LoadResult {
        owner.requireCurrent();require(characterId.isNotBlank());check(ready) { storageError ?: "Browser durable storage unavailable" }
        return read(characterId) { _,head,backup,set -> set(classify(characterId,head,backup)) }
    }
    suspend fun rawForExport(characterId:String):String? {
        owner.requireCurrent();check(ready) { storageError ?: "Browser durable storage unavailable" }
        return transaction("readonly") { tx,store,set ->
            val request=store.get(arrayOf(owner.id,characterId))
            request.onsuccess={ _:dynamic -> callback(tx) {
                val head=request.result
                set(if(absent(head)) null else head as? String ?: error("Envelope is not exportable text; original value retained"))
            } }
        }
    }
    suspend fun characterIds():Set<String> {
        owner.requireCurrent();check(ready) { storageError ?: "Browser durable storage unavailable" }
        val loaded=transaction<Set<String>>("readonly") { tx,store,set ->
            val result=linkedSetOf<String>();val request=store.openCursor()
            request.onsuccess={ _:dynamic -> callback(tx) {
                val cursor=request.result
                if(absent(cursor)) set(result.toSet()) else {
                    val key=cursor.key as? Array<dynamic> ?: error("Unsupported envelope key")
                    require(key.size==2 && key[0] is String && key[1] is String) { "Corrupt envelope key" }
                    if(key[0]==owner.id) result += key[1] as String
                    cursor.`continue`()
                }
            } }
        }
        ids=loaded;return loaded
    }
    suspend fun prepareMigration(characterId:String,activePackIds:List<String>,catalog:LegacyCatalogSnapshot?):MigrationResult {
        val loaded=load(characterId)
        return when(loaded) {
            is LoadResult.Loaded -> MigrationResult.Ready(loaded.state)
            is LoadResult.Unsupported -> MigrationResult.Unsupported(loaded.raw,loaded.reason)
            is LoadResult.Corrupt -> MigrationResult.Corrupt(loaded.raw,loaded.reason)
            is LoadResult.LegacyImportConflict -> MigrationResult.Corrupt(loaded.canonicalRaw,loaded.reason)
            is LoadResult.Missing -> MigrationResult.Corrupt("","Legacy character is missing")
            is LoadResult.MigrationRequired -> try {
                val captured=read<BrowserLegacySource?>(characterId) { _,_,backup,set -> set(backup ?: legacySource()) } ?: error("Actual legacy source absent")
                require(captured.snapshotRaw==loaded.raw) { "Legacy source changed during preparation" }
                val schema=(js("JSON.parse")(canonicalJson(loaded.raw)).schema as Number).toInt()
                val extras=decodeBrowserLegacyExtras(captured.extrasRaw)[characterId] ?: com.furybook.dubl.model.CharacterSheetExtras()
                prepareLegacy(loaded.raw,LegacyOrigin(characterId,schema,legacySourceFingerprint(loaded.raw),loaded.raw,activePackIds,extrasSource=LegacyExtrasSource(captured.extrasRaw)),catalog,extras)
            } catch(failure:Throwable) { MigrationResult.Corrupt(loaded.raw,failure.message ?: "Invalid legacy source") }
        }
    }
    override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult = try {
        owner.requireCurrent();check(capabilities.localDurable && capabilities.formatWritable) { "Local acquisition writes are disabled or durable storage is unavailable" }
        val encoded=encodeEnvelope(next)
        val captured=(decodeEnvelope(encoded) as LoadResult.Loaded).state
        require(captured.character.id==characterId) { "Character scope differs" }
        val result=read<CommitResult>(characterId,"readwrite") { tx,head,backup,set ->
            val current=classify(characterId,head,backup)
            var migrationSource:BrowserLegacySource?=null
            val accepted=when(current) {
                is LoadResult.Loaded -> current.state.storageRevision==expectedStorageRevision && expectedStorageRevision<Long.MAX_VALUE && captured.storageRevision==expectedStorageRevision+1
                is LoadResult.Missing -> expectedStorageRevision == -1L && captured.storageRevision==0L && captured.ledger.opening.legacyMigration==null
                is LoadResult.MigrationRequired -> {
                    val actual=backup ?: legacySource()
                    val origin=captured.ledger.opening.legacyMigration?.origin
                    if(origin==null || origin.extrasSource==null || actual==null) { set(CommitResult.StorageFailure("Legacy source requires explicit migration"));return@read }
                    migrationSource=actual
                    val extras=decodeBrowserLegacyExtras(actual.extrasRaw)[characterId] ?: com.furybook.dubl.model.CharacterSheetExtras()
                    expectedStorageRevision == -1L && captured.storageRevision==0L && origin.raw==actual.snapshotRaw && origin.extrasSource?.raw==actual.extrasRaw && captured.extras==extras
                }
                is LoadResult.LegacyImportConflict -> { set(CommitResult.StorageFailure(current.reason));return@read }
                is LoadResult.Unsupported -> { set(CommitResult.StorageFailure("Unsupported envelope/source: ${current.reason}"));return@read }
                is LoadResult.Corrupt -> { set(CommitResult.StorageFailure("Corrupt envelope/source: ${current.reason}"));return@read }
            }
            if(!accepted) { set(CommitResult.Conflict);return@read }
            fault(BrowserAcquisitionWriteStage.BeforePut)
            migrationSource?.let { tx.objectStore(SOURCES).put(encodeSource(it),owner.id) }
            val request=tx.objectStore(ENVELOPES).put(encoded,arrayOf(owner.id,characterId))
            request.onsuccess={ _:dynamic -> callback(tx) { fault(BrowserAcquisitionWriteStage.AfterPutSucceeded) } }
            set(CommitResult.Committed)
        }
        if(result==CommitResult.Committed) ids=ids+characterId
        result
    } catch(cancelled:CancellationException) { throw cancelled }
      catch(failure:Throwable) { storageError=storageError ?: failure.message ?: "Browser storage write failed";CommitResult.StorageFailure(storageError!!) }

    companion object {
        private const val ENVELOPES="envelopes"
        private const val SOURCES="legacy-sources"
        private fun storageFailure(error:dynamic)=IllegalStateException("IndexedDB transaction failed: "+((error?.name as? String) ?: "AbortError")+": "+((error?.message as? String) ?: "Storage unavailable"))
    }
}
