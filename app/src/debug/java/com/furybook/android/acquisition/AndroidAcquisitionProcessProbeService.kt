package com.furybook.android.acquisition

import android.app.Service
import android.content.Intent
import android.os.*
import com.furybook.android.data.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path

/** Non-exported debug-only, synthetic namespace. It can terminate only its own probe process. */
class AndroidAcquisitionProcessProbeService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val messenger=Messenger(object:Handler(Looper.getMainLooper()) {
        override fun handleMessage(message:Message) {
            val args=Bundle(message.data);val reply=message.replyTo
            scope.launch {
                try {
                    val id=requireNotNull(args.getString("fixture"));require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
                    if(args.getString("mode")=="stop") { Process.killProcess(Process.myPid());return@launch }
                    val root=fixtureRoot(dataDir.toPath(),id);Files.createDirectories(root)
                    val requested=args.getString("fault")
                    val store=AndroidAcquisitionStore(root,AndroidLegacyAcquisitionSource(root.resolve("shared_prefs")),fault={ stage ->
                        if(stage.name==requested) Process.killProcess(Process.myPid())
                    })
                    val result=when(args.getString("mode")) {
                        "initialize" -> store.compareAndSet("synthetic",-1,fixtureState()).toString()
                        "purchase" -> fixtureService(store).apply(fixtureCommand()).let { it::class.simpleName.orEmpty() }
                        "cas" -> store.compareAndSet("synthetic",0,fixtureState().copy(storageRevision=1)).toString()
                        "load" -> "Loaded"
                        else -> error("Unknown probe operation")
                    }
                    val loaded=(store.load("synthetic") as LoadResult.Loaded).state
                    reply.send(Message.obtain().apply { data=Bundle().apply {
                        putInt("pid",Process.myPid());putString("result",result);putLong("xp",balance(loaded.ledger).xp)
                        putInt("receipts",loaded.receipts.size);putInt("outbox",loaded.outbox.size);putLong("storageRevision",loaded.storageRevision)
                    } })
                } catch(failure:Exception) { reply.send(Message.obtain().apply { data=Bundle().apply { putString("error",failure.toString()) } }) }
            }
        }
    })
    override fun onBind(intent:Intent):IBinder=messenger.binder
    override fun onUnbind(intent:Intent):Boolean { Process.killProcess(Process.myPid());return false }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}
internal fun fixtureRoot(dataDir:Path,id:String)=dataDir.resolve("files/t13-synthetic-$id")
internal fun fixtureState()=StoredCharacter(DublCharacter("synthetic"),CharacterSheetExtras(),emptyList(),
    Ledger(Opening("new/synthetic","synthetic instrumentation",Amount(100,10),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
    emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
internal fun fixtureCatalog():AcquisitionCatalog {
    val ref=DefinitionRef("dubl-3.69","synthetic-process-probe");val source=RuleSourceRef("t13-synthetic","1","Synthetic process fixture")
    return AcquisitionCatalog(mapOf(ref to AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,1,RepeatPolicy.Single,
        RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"None")),PriceRule.Fixed(20,0,source),SourceStatus.Confirmed,"Synthetic",source)),"test","test")
}
internal fun fixtureService(store:AcquisitionStore)=AcquisitionService(store,::fixtureCatalog) { "synthetic-instance" }
internal suspend fun fixtureCommand():ConfirmAcquisition {
    // Build the identical deterministic token from the fixture opening, without writing any data.
    val memory=object:AcquisitionStore {
        override suspend fun load(characterId:String)=LoadResult.Loaded(fixtureState())
        override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter)=error("read only")
    }
    val request=AcquisitionRequest("synthetic",listOf(AcquisitionTarget(DefinitionRef("dubl-3.69","synthetic-process-probe"),"synthetic-instance",1)))
    val plan=fixtureService(memory).preview(request)
    return ConfirmAcquisition("synthetic-purchase","synthetic",request,plan.baseRevision,plan.previewToken)
}
