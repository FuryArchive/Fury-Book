package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import java.io.File

fun main(args: Array<String>) {
    var assertions = 0
    fun expect(value: Boolean) { check(value); assertions++ }
    val original = StoredCharacter(DublCharacter("hero"), CharacterSheetExtras(notes = "keep"), emptyList(),
        Ledger(Opening("opening", "source", Amount.ZERO, RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating)
    val oldRaw = encodeEnvelope(original)
    expect((decodeEnvelope(oldRaw) as LoadResult.Loaded).state == original)
    expect(!oldRaw.contains("gameplay"))
    expect(runCatching { encodeEnvelope(original.copy(gameplay = GameplayJournal())) }.isFailure)
    expect(runCatching { encodeEnvelope(original.copy(formatVersion = 2)) }.isFailure)
    val empty = original.copy(formatVersion = 2, gameplay = GameplayJournal())
    expect((decodeEnvelope(encodeEnvelope(empty), allowGameplayFormat = true) as LoadResult.Loaded).state == empty)
    expect(empty.copy(extras = empty.extras.copy(notes = "changed")).gameplay == empty.gameplay)
    val active = (GameplayActions.start(GameplayState("hero", 0, 3),
        StartOverexertion("begin", "hero", 0, PhysicalAttribute.STRENGTH)) as ActionTransition.Applied).state
    val envelope = empty.copy(character = empty.character.copy(enduranceCurrent = active.endurance),
        gameplay = GameplayJournal.from(active), storageRevision = 1)
    val raw = encodeEnvelope(envelope)
    expect((decodeEnvelope(raw) as LoadResult.Unsupported).raw == raw)
    val reopened = (decodeEnvelope(raw, allowGameplayFormat = true) as LoadResult.Loaded).state
    expect(reopened == envelope && reopened.character.enduranceCurrent == 2)
    expect(reopened.gameplay?.pending?.startOperationId == "begin")
    expect(reopened.ledger == original.ledger && reopened.receipts == original.receipts && reopened.revision == 0L)
    expect(encodeEnvelope(reopened) == raw)
    expect(runCatching { encodeSyncEnvelope(envelope) }.isFailure)
    expect(runCatching { encodeEnvelope(envelope.copy(syncMetadata = SyncMetadata(1))) }.isFailure)
    expect(runCatching { encodeEnvelope(envelope.copy(formatVersion = 1)) }.isFailure)
    expect(decodeEnvelope(raw.replace("\"formatVersion\":2", "\"formatVersion\":3")) is LoadResult.Unsupported)
    expect(decodeEnvelope(raw.replace("\"formatVersion\":2", "\"formatVersion\":1")) !is LoadResult.Loaded)
    if (args.isNotEmpty()) {
        File(args[0]).mkdirs()
        File(args[0], "format1.json").writeText(oldRaw)
        File(args[0], "format2.json").writeText(raw)
    }
    println("GameplayEnvelopeHarness: $assertions assertions passed")
}
