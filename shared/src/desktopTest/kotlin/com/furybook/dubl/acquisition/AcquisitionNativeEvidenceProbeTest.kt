package com.furybook.dubl.acquisition

import com.furybook.content.*
import com.furybook.core.json.*
import com.furybook.dubl.data.DesktopAcquisitionStore
import com.furybook.dubl.model.*
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Real saved native records for a bounded independent corpus subset, not whole-catalogue acceptance. */
class AcquisitionNativeEvidenceProbeTest {
    @Test fun fixed_skill_purchase_reopen_and_undo_export_native_envelopes() = runBlocking {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "rulesets/dubl-3.69/acquisition/expected_prices.json").isFile }
        val inputPath = System.getProperty("furybook.acquisition.native.input")
        val input = inputPath?.let { JsonReader(File(it).readText(), strict = true).read() as JsonValue.Obj }
        fun verifyInputs() {
            if (input == null) return
            val commit = ProcessBuilder("git", "rev-parse", "HEAD").directory(root).start().let { p ->
                val text = p.inputStream.bufferedReader().readText().trim(); assertEquals(0, p.waitFor()); text
            }
            assertEquals((input.values.getValue("testedCommit") as JsonValue.Str).value, commit)
            val hashes = input.values.getValue("inputDigests") as JsonValue.Obj
            for ((path, value) in hashes.values) {
                val hash = MessageDigest.getInstance("SHA-256").digest(File(root, path).readBytes()).joinToString("") { "%02x".format(it) }
                assertEquals((value as JsonValue.Str).value, hash, path)
            }
        }
        verifyInputs()
        val source = FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }
        val catalog = loadAcquisitionCatalog(listOf("dubl-3.69", "dubl-chi-3.69")
            .map { FcpContentPack.load("fcp/$it", source) }, true).catalog
        val fixedSkills = listOf("athletics", "barter", "awareness", "riding", "lockpicking", "driving", "survival", "animal_handling", "intimidation", "knowledge_academic", "engineering_repair", "performance_vocal", "leadership", "eloquence", "medicine", "piloting", "search", "stealth", "shooting", "melee_weapon", "throwing", "unarmed", "computers")
        val identities = fixedSkills.flatMap { skill -> listOf("CREATION", "AFTER_CREATION")
            .map { phase -> "$skill|0|1|DEFAULT|$phase|NONE|EXISTING|FIXED" } }.toSet()
        val rows = File(root, "rulesets/dubl-3.69/acquisition/expected_prices.json").useLines { lines ->
            lines.filter { it.startsWith("{\"caseId\":") }.map { JsonReader(it.removeSuffix(",")).read() as JsonValue.Obj }
                .filter { (it.values.getValue("caseId") as JsonValue.Str).value in identities }.toList()
        }
        assertEquals(46, rows.size)
        val records = rows.map { row ->
            val id = (row.values.getValue("caseId") as JsonValue.Str).value
            val fixture = AcquisitionPriceFixtures({ error("exact row only") }, catalog).decode(jsonStringify(row))
            val directory = Files.createTempDirectory("fury-native-probe-")
            val store = DesktopAcquisitionStore(directory)
            val character = DublCharacter("native-probe", creationComplete = fixture.context.creationPhase == RequirementNode.CreationPhase.Phase.Finalized)
            val initial = StoredCharacter(character, CharacterSheetExtras(), emptyList(),
                Ledger(Opening("probe-opening", "independent-corpus", fixture.opening, fixture.context.creationPhase,
                    OpeningProvenance.UserConfirmed, "Independent native probe"), emptyList()), emptyList(), emptyList(),
                SyncMetadata(0), 0, 0, fixture.context.creationPhase)
            assertEquals(CommitResult.Committed, store.compareAndSet(character.id, -1, initial))
            val before = Files.readString(store.envelopePath(character.id))
            val request = AcquisitionRequest(character.id, listOf(AcquisitionTarget(fixture.change.definition,
                fixture.change.instanceId, fixture.change.toRank)))
            val service = AcquisitionService(store, { catalog }, { "unexpected-generated-instance" })
            val preview = service.preview(request)
            assertTrue(preview.executable, id)
            assertEquals(fixture.expected.cost, preview.cost, id)
            val applied = assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("probe-purchase", character.id,
                request, preview.baseRevision, preview.previewToken)), id)
            assertEquals(fixture.expected.cost, applied.receipt.result.cost, id)
            val after = Files.readString(store.envelopePath(character.id))
            val reopenedStore = DesktopAcquisitionStore(directory)
            val reopened = assertIs<LoadResult.Loaded>(reopenedStore.load(character.id)).state
            assertEquals(after, encodeEnvelope(reopened), id)
            assertEquals(fixture.expected.remaining, balance(reopened.ledger), id)
            assertIs<ApplyResult.Applied>(AcquisitionUndo(reopenedStore).undo(character.id, "probe-undo", reopened.revision), id)
            val undone = Files.readString(store.envelopePath(character.id))
            val reopenedUndone = assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(directory).load(character.id)).state
            assertEquals(undone, encodeEnvelope(reopenedUndone), id)
            assertEquals(fixture.expected.undoBalance, balance(reopenedUndone.ledger), id)
            fun parsed(text: String) = JsonReader(text, strict = true).read()
            jsonObject(
                "caseId" to jsonString(id), "outcome" to jsonString("APPLIED"),
                "binding" to jsonObject("kind" to jsonString("SKILL"),
                    "definitionRef" to jsonObject("packId" to jsonString(fixture.change.definition.packId), "entryId" to jsonString(fixture.change.definition.entryId)),
                    "characterId" to jsonString(character.id), "instanceId" to jsonString(fixture.change.instanceId),
                    "parameters" to jsonObject(), "optionId" to JsonValue.Null),
                "native" to jsonObject("before" to parsed(before), "after" to parsed(after), "reopened" to parsed(encodeEnvelope(reopened)),
                    "undone" to parsed(undone), "reopenedUndone" to parsed(encodeEnvelope(reopenedUndone)),
                    "requestCanonical" to jsonString(applied.receipt.requestCanonical))
            )
        }
        verifyInputs()
        if (input != null) {
            val output = File(System.getProperty("furybook.acquisition.native.output") ?: error("Missing output path"))
            check(!output.exists()) { "Refuse to overwrite prior evidence" }
            output.parentFile.mkdirs()
            val result = JsonValue.Obj(input.values + mapOf(
                "schema" to jsonString("FURY_NATIVE_PROBE_V1"),
                "producer" to jsonString("AcquisitionNativeEvidenceProbeTest.fixed_skill_purchase_reopen_and_undo_export_native_envelopes"),
                "records" to JsonValue.Arr(records)))
            output.writeText(jsonStringify(result))
        }
        Unit
    }
}
