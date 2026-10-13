package com.furybook.android.acquisition

import android.content.Context
import com.furybook.android.data.*
import com.furybook.android.state.CharacterController
import com.furybook.android.ui.acquisition.AndroidAcquisitionContent
import com.furybook.content.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.content.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

/** Debug-only synthetic namespace. Neither real preferences nor cloud services are initialized. */
class AndroidAcquisitionUiFixture(val store: AndroidAcquisitionStore, val controller: CharacterController,
    val content: AndroidAcquisitionContent, val runId: String) {
    companion object {
        const val SYNTHETIC_ID = "ui-synthetic-20"
        const val REAL_BLOCK_ID = "feat_fcdd74622d21435d"
        suspend fun open(context: Context, runId: String, resume: Boolean): AndroidAcquisitionUiFixture = withContext(Dispatchers.IO) {
            require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
            val root = context.filesDir.toPath().resolve("acquisition-ui-tests").resolve(runId)
            Files.createDirectories(root)
            val source = AndroidLegacyAcquisitionSource(root.resolve("shared_prefs"))
            val store = AndroidAcquisitionStore(root, source)
            val textSource = FcpTextSource { path -> runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull() }
            val core = DublFcp.open(textSource)
            val composition = FcpComposition.resolve(listOf(core.pack.manifest), setOf(DublFcp.PACK_ID), emptySet())
            val rules = core.loadAcquisition().catalog
            val syntheticRef = DefinitionRef(DublFcp.PACK_ID, SYNTHETIC_ID)
            val evidence = RuleSourceRef("SYNTHETIC_RULE", "t18a", "Debug-only fixed20 no-prerequisite UI input fixture; not source acceptance")
            val definition = AcquisitionDefinition(syntheticRef, AcquisitionKind.DEVELOPMENT, 1, RepeatPolicy.Single,
                RequirementNode.AllOf("none", evidence, emptyList(), NoRequirementsEvidence(evidence, "Synthetic no requirements")),
                PriceRule.Fixed(20, 0, evidence), SourceStatus.Confirmed, "Проверочный навык (SYNTHETIC_RULE)", evidence)
            val catalog = rules.copy(definitions = rules.definitions + (syntheticRef to definition), catalogRevision = rules.catalogRevision + ":debug-ui")
            val original = core.loadDevelopment()
            val display = original.entries.first().copy(id = SYNTHETIC_ID, name = definition.name, section = "Общие",
                category = "Проверка интерфейса", cost = 20, costType = DevelopmentCostType.XP, maxRank = 1,
                requirements = "", benefit = "Только проверочный пример интерфейса", notes = "SYNTHETIC_RULE",
                tags = emptyList(), accessId = null, abilityOptions = emptyList(), incomplete = false, repeatable = false,
                perfectRoot = false, mechanicsConflict = "", conflictNote = "")
            val content = AndroidAcquisitionContent(composition, DevelopmentCatalog("debug-ui", listOf(display) + original.entries.filter { it.id == REAL_BLOCK_ID }),
                ChiCatalog("disabled", emptyList(), emptyList()), core.loadMagicEquipment(), core.loadSkillEffects(), core.loadConditions(), catalog)
            val ids = listOf("ui-A", "ui-B")
            if (!resume) for (id in ids) {
                check(store.load(id) is LoadResult.Missing) { "Fresh fixture must not overwrite a saved head" }
                val character = DublCharacter(id, name = if (id == "ui-A") "Синтетический A" else "Синтетический B",
                    attributes = DublCharacter(id).attributes + (AttributeId.DEXTERITY to AttributeValue(3)) + (AttributeId.SPEED to AttributeValue(2)),
                    skills = mapOf("unarmed" to CharacterSkill("unarmed", definitionId = "unarmed", rank = 3)))
                val state = StoredCharacter(character, CharacterSheetExtras(), listOf(AcquisitionInstance("unarmed", DefinitionRef(DublFcp.PACK_ID, "unarmed"), 3, emptyMap(), null, AcquisitionOrigin.New)),
                    Ledger(Opening("debug:$id", "SYNTHETIC_RULE:$runId", Amount(100, 2), RequirementNode.CreationPhase.Phase.Creating, OpeningProvenance.NewCharacter), emptyList()),
                    emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
                check(store.compareAndSet(id, -1, state) == CommitResult.Committed)
            }
            val states = store.characterIds().associateWith { (store.load(it) as LoadResult.Loaded).state }
            check(ids.all { it in states }) { "Resume requires both exact fixture heads" }
            val characters = object : CharacterStore {
                override fun load() = AppSnapshot(states.values.map { it.character }, "ui-A")
                override fun save(snapshot: AppSnapshot) = error("Fixture cannot use a legacy write")
            }
            val extras = object : CharacterExtrasStore {
                override fun load(characterId: String) = states.getValue(characterId).extras
                override fun save(characterId: String, extras: CharacterSheetExtras) = error("Fixture cannot use legacy extras")
            }
            val controller = withContext(Dispatchers.Main.immediate) {
                CharacterController(characters, extras, store, states, source.gate, catalogProvider = { catalog },
                    capabilities = AcquisitionCapabilities(true, false, true))
            }
            AndroidAcquisitionUiFixture(store, controller, content, runId)
        }
    }
}
