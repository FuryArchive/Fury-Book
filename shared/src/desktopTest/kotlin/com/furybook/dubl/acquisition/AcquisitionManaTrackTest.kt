package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Current manifest sources and serialized service/store transitions, never reconstructed old prices. */
class AcquisitionManaTrackTest {
    private val reserve = DefinitionRef("dubl-3.69", "mana:rank")
    private val alias = DefinitionRef("dubl-3.69", MagicEquipmentRules.BASE_MANA_ENTRY_ID)
    private val catalog by lazy {
        loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
            FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog
    }
    private fun initial(creating: Boolean = true): StoredCharacter {
        val phase = if (creating) RequirementNode.CreationPhase.Phase.Creating else RequirementNode.CreationPhase.Phase.Finalized
        return StoredCharacter(DublCharacter("mana", creationComplete = !creating), CharacterSheetExtras(), emptyList(),
            Ledger(Opening("opening", "confirmed-existing-balance", Amount(1000, 0), phase, OpeningProvenance.UserConfirmed,
                "Explicit unchanged starting balance"), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }
    private fun store(state: StoredCharacter) = InMemoryAcquisitionStore(mapOf("mana" to encodeEnvelope(state)))
    private suspend fun saved(store: AcquisitionStore) = assertIs<LoadResult.Loaded>(store.load("mana")).state
    private fun service(store: AcquisitionStore, rules: AcquisitionCatalog = catalog) = AcquisitionService(store, { rules }, { "operation" })
    private suspend fun buy(service: AcquisitionService, request: AcquisitionRequest, operation: String): Receipt {
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        return assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition(operation, "mana", request,
            preview.baseRevision, preview.previewToken))).receipt
    }
    private fun request(ref: DefinitionRef, rank: Int) = AcquisitionRequest("mana", listOf(AcquisitionTarget(ref, targetRank = rank)))

    @Test fun actual_sources_identify_one_five_rank_track_and_explicit_first_power_grant() {
        for (ref in listOf(reserve, alias)) {
            val definition = catalog.definitions.getValue(ref)
            assertEquals(5, definition.maxRank)
            assertEquals(100L, assertIs<PriceRule.PerRank>(definition.priceRule).xp)
            assertEquals(RepeatPolicy.Single, definition.repeatPolicy)
            assertTrue(definition.classificationSources.getValue("economic:mana-reserve").originalText.isNotBlank())
        }
        val grant = catalog.definitions.getValue(reserve).classificationSources.getValue("mana:first-power")
        assertEquals("1BGajeMuh0clSbybt0IGyqv2_6zxdKEtHvnjh_TbKqGg", grant.documentId)
        assertEquals("t.eku5gf5eorqj", grant.sheet)
        assertTrue(grant.originalText.endsWith("При покупке 1 ранга Запаса маны, Вы автоматически получаете 1 ранг силы магии."))
    }

    @Test fun development_then_mana_purchase_uses_one_track_and_keeps_first_receipt_exact() = runBlocking {
        val original = initial()
        val backing = store(original)
        val app = service(backing)
        val scope = AcquisitionScope("local", "mana", 0, 0, 0)
        val routed = assertIs<AcquisitionRouteResult.QuoteRequest>(AcquisitionCommandRouting.request(
            AcquisitionIntent.development(AcquisitionTarget(alias, targetRank = 1)), scope, original, catalog)).request
        val firstReceipt = buy(app, routed, "first")
        val first = saved(backing)
        assertEquals(Amount(100, 0), firstReceipt.result.cost)
        assertEquals(reserve, firstReceipt.result.changes.single().definition)
        assertEquals(1, first.character.magic.manaRank)
        assertEquals(1, first.character.magic.power)
        assertEquals(1, first.character.development.getValue(alias.entryId).rank)
        val reopened = store(first)
        assertEquals(encodeEnvelope(first), encodeEnvelope(saved(reopened)))
        val secondReceipt = buy(service(reopened), request(reserve, 2), "second")
        val second = saved(reopened)
        assertEquals(Amount(100, 0), secondReceipt.result.cost)
        assertEquals(1, secondReceipt.result.changes.single().fromRank)
        assertEquals(Amount(800, 0), balance(second.ledger))
        assertEquals(2, second.character.magic.manaRank)
        assertEquals(2, second.character.development.getValue(alias.entryId).rank)
        assertEquals(1, second.character.magic.power)
        assertEquals(listOf(reserve), second.instances.map { it.definition })
        assertEquals(first.ledger.entries.single(), second.ledger.entries.first())
        assertEquals(first.receipts.single(), second.receipts.first())
        assertIs<ApplyResult.Applied>(AcquisitionUndo(reopened).undo("mana", "undo-second", second.revision))
        assertEquals(acquisitionProjectionCanonical(first), acquisitionProjectionCanonical(saved(reopened)))
    }

    @Test fun both_actual_entrypoints_in_one_plan_charge_each_rank_once() = runBlocking {
        val original = initial()
        val backing = store(original)
        val both = AcquisitionRequest("mana", listOf(AcquisitionTarget(alias, targetRank = 1), AcquisitionTarget(reserve, targetRank = 1)))
        val receipt = buy(service(backing), both, "both")
        assertEquals(Amount(100, 0), receipt.result.cost)
        assertEquals(1, receipt.result.changes.size)
        val owned = saved(backing)
        val noOp = plan(AcquisitionRequest("mana", listOf(AcquisitionTarget(reserve, targetRank = 1), AcquisitionTarget(alias, targetRank = 1))), owned, catalog)
        assertTrue(noOp.executable, noOp.issues.toString())
        assertTrue(noOp.changes.isEmpty())
        assertEquals(Amount.ZERO, noOp.cost)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("mana", "undo-first", owned.revision))
        assertEquals(acquisitionProjectionCanonical(original), acquisitionProjectionCanonical(saved(backing)))
    }

    @Test fun later_increase_needs_explicit_gm_approval_but_existing_ownership_does_not() = runBlocking {
        val seedStore = store(initial())
        buy(service(seedStore), request(reserve, 1), "creation")
        val first = saved(seedStore)
        assertIs<MutationResult.Applied>(CharacterMutationCoordinator(seedStore, { catalog }, { "operation" }).mutate(
            CanonicalMutation("complete-creation", "mana", first.revision, MutationPayload.CompleteCreation)))
        val finalized = saved(seedStore)
        val backing = store(finalized)
        val originalBytes = encodeEnvelope(saved(backing))
        val blocked = service(backing).preview(request(reserve, 2))
        assertFalse(blocked.executable)
        assertIs<ApplyResult.Blocked>(service(backing).apply(ConfirmAcquisition("no-approval", "mana", request(reserve, 2), blocked.baseRevision, blocked.previewToken)))
        assertEquals(originalBytes, encodeEnvelope(saved(backing)))
        val source = catalog.definitions.getValue(reserve).sourceRef
        val approval = RequirementWitness.External("gm-mana-reserve-increase", "mana:rank", "gm-explicit-approval", source)
        val approved = AcquisitionRequest("mana", request(reserve, 2).targets, externalConfirmations = listOf(approval))
        assertEquals(Amount(100, 0), buy(service(backing), approved, "approved").result.cost)
        val aliasLater = AcquisitionRequest("mana", request(alias, 3).targets, externalConfirmations = listOf(approval))
        assertFalse(plan(aliasLater, saved(backing), catalog).executable, "The creation-only development entry cannot be bought later")
        val need = RequirementNode.OwnedRank("already-owned", source, reserve.packId, reserve.entryId, ComparisonOperator.GE, 1)
        assertEquals(RequirementStatus.Satisfied, evaluate(need, acquisitionContext(finalized, catalog)).status)
    }

    @Test fun old_aliases_keep_opening_and_history_and_only_the_new_increment_is_paid() = runBlocking {
        val original = initial().copy(character = initial().character.copy(magic = CharacterMagic(manaRank = 1),
            development = mapOf(alias.entryId to OwnedDevelopment(2, optionIndex = 7))),
            instances = listOf(AcquisitionInstance("old-development", alias, 2, emptyMap(), null, AcquisitionOrigin.New),
                AcquisitionInstance("old-mana", reserve, 1, emptyMap(), null, AcquisitionOrigin.New)))
        val backing = store(original)
        val oldBytes = encodeEnvelope(saved(backing))
        val app = service(backing)
        val preview = app.preview(request(reserve, 3))
        assertEquals(oldBytes, encodeEnvelope(saved(backing)), "A preview must not rewrite old holdings")
        assertEquals(Amount(100, 0), preview.cost)
        assertEquals(2, preview.changes.single().fromRank)
        buy(app, request(reserve, 3), "new-increment")
        val after = saved(backing)
        assertEquals(original.ledger.opening, after.ledger.opening)
        assertEquals(Amount(900, 0), balance(after.ledger))
        assertEquals(1, after.instances.count { it.definition == reserve })
        assertEquals(original.instances.first { it.definition == alias }, after.instances.single { it.definition == alias })
        assertEquals(0, after.character.magic.power, "An old reserve does not receive a new first-rank grant")
        assertEquals(7, after.character.development.getValue(alias.entryId).optionIndex)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("mana", "undo-increment", after.revision))
        assertEquals(acquisitionProjectionCanonical(original), acquisitionProjectionCanonical(saved(backing)))
    }

    @Test fun missing_first_power_source_never_grants_magic_power() = runBlocking {
        val definition = catalog.definitions.getValue(reserve)
        val withoutGrant = catalog.copy(definitions = catalog.definitions + (reserve to definition.copy(
            classificationSources = definition.classificationSources - "mana:first-power")))
        val backing = store(initial())
        buy(service(backing, withoutGrant), request(reserve, 1), "no-invented-grant")
        assertEquals(0, saved(backing).character.magic.power)
    }

    @Test fun actual_one_mana_spell_reviews_first_reserve_as_predecessor_and_pays_each_once() = runBlocking {
        val spell = DefinitionRef("dubl-3.69", "spell_b2de2ad02456564c")
        val backing = store(initial())
        val intent = AcquisitionRequest("mana", listOf(AcquisitionTarget(spell, targetRank = 1)))
        val preview = service(backing).preview(intent)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(listOf(reserve, spell), preview.changes.map { it.definition })
        assertEquals(listOf(Amount(100, 0), Amount(10, 0)), preview.prices.map { it.cost })
        assertEquals(Amount(110, 0), buy(service(backing), intent, "learn-with-reserve").result.cost)
        val after = saved(backing)
        assertEquals(1, after.character.magic.power)
        assertTrue(after.character.magic.spells.single { it.catalogId == spell.entryId }.learned)
        assertEquals(Amount(890, 0), balance(after.ledger))
        val repeat = plan(AcquisitionRequest("mana", listOf(AcquisitionTarget(alias, targetRank = 1), AcquisitionTarget(spell, targetRank = 1))), after, catalog)
        assertTrue(repeat.executable, repeat.issues.toString())
        assertEquals(Amount.ZERO, repeat.cost)
        assertTrue(repeat.changes.isEmpty())
    }

    @Test fun zero_mana_spell_needs_no_invented_reserve_and_higher_power_is_not_inferred() = runBlocking {
        val zero = DefinitionRef("dubl-3.69", "spell_64339dedae079bd8")
        val backing = store(initial())
        val receipt = buy(service(backing), request(zero, 1), "zero-mana")
        assertEquals(Amount(10, 0), receipt.result.cost)
        assertEquals(listOf(zero), receipt.result.changes.map { it.definition })
        assertEquals(0, saved(backing).character.magic.manaRank)
        assertEquals(0, saved(backing).character.magic.power)
        val higher = plan(request(DefinitionRef("dubl-3.69", "spell_6339942bb23ec0f5"), 1), initial(), catalog)
        assertFalse(higher.executable)
        assertTrue(higher.changes.isEmpty(), "The first-reserve source only grants power one")
    }

    @Test fun undo_of_a_pre_alias_purchase_uses_its_recorded_transition_without_repricing() = runBlocking {
        val original = initial().copy(character = initial().character.copy(
            development = mapOf(alias.entryId to OwnedDevelopment(2))),
            instances = listOf(AcquisitionInstance("old-development", alias, 2, emptyMap(), null, AcquisitionOrigin.New)))
        val definition = catalog.definitions.getValue(reserve)
        val oldRules = catalog.copy(definitions = catalog.definitions + (reserve to definition.copy(
            classificationSources = definition.classificationSources - "economic:mana-reserve" - "mana:first-power")))
        val backing = store(original)
        val historical = buy(service(backing, oldRules), request(reserve, 1), "old-purchase")
        assertEquals(0, historical.result.changes.single().fromRank)
        assertEquals(Amount(100, 0), historical.result.cost)
        val beforeUndo = saved(backing)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("mana", "undo-old-purchase", beforeUndo.revision))
        val undone = saved(backing)
        assertEquals(acquisitionProjectionCanonical(original), acquisitionProjectionCanonical(undone))
        assertEquals(Amount(1000, 0), balance(undone.ledger))
        assertEquals(historical.requestCanonical, undone.receipts.first().requestCanonical)
        assertEquals(historical.result, undone.receipts.first().result)
    }

    @Test fun alias_entry_review_receipt_and_attained_card_show_one_known_economic_row() = runBlocking {
        val original = initial()
        val backing = store(original)
        val target = request(alias, 1)
        val preview = service(backing).preview(target)
        val scope = AcquisitionScope("local", "mana", 0, 0, 0)
        val card = AcquisitionPresentation.present(scope, AcquisitionQuoteKey("alias-card", 0), preview, original, catalog,
            AcquisitionCapabilities(true, false, true))
        assertEquals(Amount(100, 0), assertIs<AcquisitionDisplayedPrice.Known>(card.price).amount)
        assertEquals(1, card.rows.size)
        assertTrue(card.rows.single().isTarget)
        assertEquals(Amount(100, 0), assertIs<AcquisitionDisplayedPrice.Known>(card.rows.single().price).amount)
        val receipt = buy(service(backing), target, "alias-card-purchase")
        assertEquals(card.rows.map { it.label }, AcquisitionPresentation.receipt(receipt, catalog, false).rows.map { it.label })
        val after = saved(backing)
        val noOp = plan(target, after, catalog)
        val attained = AcquisitionPresentation.present(scope, AcquisitionQuoteKey("alias-owned", 0), noOp, after, catalog,
            AcquisitionCapabilities(true, false, true))
        assertEquals(1, attained.rows.size)
        assertTrue(attained.rows.single().alreadyOwned)
        assertEquals(Amount.ZERO, assertIs<AcquisitionDisplayedPrice.Known>(attained.rows.single().price).amount)
        val selection = selectionPrompts(target, after, catalog).single()
        assertEquals(1, selection.instances.size)
        assertEquals(1, selection.instances.single().rank)
        assertFalse(selection.canCreateNew)
    }

    @Test fun first_grant_preserves_zero_rank_or_unknown_school_rows_and_higher_existing_power() = runBlocking {
        for (schools in listOf(emptyList(), listOf(MagicSchool("Боевая магия", 0)), listOf(MagicSchool("Сохранённая неизвестная школа", 0)))) {
            val original = initial().copy(character = initial().character.copy(magic = CharacterMagic(schools = schools)))
            val backing = store(original)
            buy(service(backing), request(reserve, 1), "first-with-school")
            val after = saved(backing)
            assertEquals(schools, after.character.magic.schools)
            assertEquals(1, MagicEquipmentRules.highestMagicPower(after.character))
            assertEquals(1, after.character.magic.power)
            assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("mana", "undo-school-reserve", after.revision))
            assertEquals(acquisitionProjectionCanonical(original), acquisitionProjectionCanonical(saved(backing)))
        }
        val original = initial().copy(character = initial().character.copy(magic = CharacterMagic(power = 4)))
        val backing = store(original)
        buy(service(backing), request(reserve, 1), "preserve-power")
        assertEquals(4, saved(backing).character.magic.power)
    }
}
