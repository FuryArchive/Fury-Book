package com.furybook.android.acquisition

import com.furybook.android.data.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** Actual JDK files/DOM and Shared migration services only. No Android/device execution evidence. */
class AndroidLegacyAcquisitionSourceTest {
    @Test fun capture_keeps_exact_snapshot_all_preference_types_and_original_xml() = runBlocking {
        withRoot { root ->
            val raw = " \n\t" + snapshot() + "\n  "
            val stateValues = linkedMapOf<String, Any>(
                "state_v1" to raw, "ordinary_string" to "< & \" ' \\ \r\n",
                "boolean" to true, "int" to Int.MIN_VALUE, "long" to Long.MAX_VALUE,
                "float" to -0.0f, "set" to linkedSetOf("z", "a<&", ""),
            )
            val extrasValues = linkedMapOf<String, Any>("unrelated.counter" to 7L)
            val stateBytes = writePreferences(root, STATE, stateValues)
            val extrasBytes = writePreferences(root, EXTRAS, extrasValues)
            val source = source(root)
            source.memoryReader = { stateValues to extrasValues }
            val captured = source.capture()

            assertEquals(raw, captured.snapshotRaw)
            assertEquals(typedPreferences(stateValues), captured.stateEntries)
            assertEquals(typedPreferences(extrasValues), captured.extrasEntries)
            assertEquals("int", captured.stateEntries.getValue("int").kind)
            assertEquals("long", captured.stateEntries.getValue("long").kind)
            assertEquals("\"9223372036854775807\"", captured.stateEntries.getValue("long").valueJson)
            assertEquals("\"-0.0\"", captured.stateEntries.getValue("float").valueJson)
            assertEquals(listOf("", "a<&", "z"), captured.stateEntries.getValue("set").strings())
            assertArrayEquals(stateBytes, captured.rawFiles.getValue(STATE))
            assertArrayEquals(extrasBytes, captured.rawFiles.getValue(EXTRAS))

            val origin = captured.origin("real", PACKS)
            assertEquals(raw, origin.raw)
            val bundle = AndroidRawJson.fields(origin.extrasSource!!.raw!!)
            assertEquals("android-preferences-source-v1", AndroidRawJson.string(bundle.getValue("format")))
            assertEquals(stateValues.keys, AndroidRawJson.fields(bundle.getValue("stateEntries")).keys)
            assertEquals(extrasValues.keys, AndroidRawJson.fields(bundle.getValue("extrasEntries")).keys)
            assertBundleFile(bundle, STATE, stateBytes)
            assertBundleFile(bundle, EXTRAS, extrasBytes)
            assertBundleFile(bundle, "$STATE.bak", null)
            assertBundleFile(bundle, "$EXTRAS.bak", null)
            assertExport(store(root, source).recoveryExport("real"), captured.rawFiles)
            assertArrayEquals(stateBytes, Files.readAllBytes(preferences(root).resolve(STATE)))
            assertArrayEquals(extrasBytes, Files.readAllBytes(preferences(root).resolve(EXTRAS)))
        }
    }

    @Test fun absent_extras_file_and_present_empty_file_have_distinct_backups() {
        withRoot { root ->
            writePreferences(root, STATE, mapOf("state_v1" to snapshot()))
            val absent = source(root).capture().origin("real", PACKS)
            val emptyBytes = writePreferences(root, EXTRAS, emptyMap())
            val present = source(root).capture().origin("real", PACKS)
            assertEquals(absent.sourceRevision, present.sourceRevision)
            assertNotEquals(absent.sourceFingerprint, present.sourceFingerprint)
            assertBundleFile(AndroidRawJson.fields(absent.extrasSource!!.raw!!), EXTRAS, null)
            assertBundleFile(AndroidRawJson.fields(present.extrasSource!!.raw!!), EXTRAS, emptyBytes)
        }
    }

    @Test fun populated_ten_extras_fields_and_retired_favorites_survive_capture_and_migration() = runBlocking {
        withRoot { root ->
            val raw = snapshot()
            val extras = populatedExtras()
            val values = extrasPreferences(extras).toMutableMap().apply {
                put("character.real.favorite_skill_ids", setOf("retired-skill"))
                put("character.real.favorites", setOf("retired-stat"))
            }
            writePreferences(root, STATE, mapOf("state_v1" to raw))
            val original = writePreferences(root, EXTRAS, values)
            val xml = String(original, UTF_8)
            assertTrue("Fixture contains real codec separator references", xml.contains("&#31;"))
            assertTrue(xml.contains("&#30;"))
            val captured = source(root).capture()
            assertEquals(extras, captured.extras("real"))
            assertEquals(CharacterSheetExtras(), captured.extras("other"))
            val origin = captured.origin("real", PACKS)
            val bundleEntries = AndroidRawJson.fields(AndroidRawJson.fields(origin.extrasSource!!.raw!!).getValue("extrasEntries"))
            assertEquals(values.keys, bundleEntries.keys)
            assertEquals(listOf("retired-skill"), captured.extrasEntries.getValue("character.real.favorite_skill_ids").strings())
            assertEquals(listOf("retired-stat"), captured.extrasEntries.getValue("character.real.favorites").strings())

            val pending = pending(store(root).prepareMigration("real", PACKS, null))
            assertEquals(extras, pending.extras)
            val confirmed = confirmLegacyBalance(pending, Amount(12, 3), "Old sheet verified")
            assertEquals(extras, (decodeEnvelope(encodeEnvelope(confirmed)) as LoadResult.Loaded).state.extras)
            assertBundleFile(AndroidRawJson.fields(confirmed.ledger.opening.legacyMigration!!.origin.extrasSource!!.raw!!), EXTRAS, original)
            assertExport(store(root).recoveryExport("real"), mapOf(STATE to Files.readAllBytes(preferences(root).resolve(STATE)), EXTRAS to original))
            assertArrayEquals(original, Files.readAllBytes(preferences(root).resolve(EXTRAS)))
        }
    }

    @Test fun numeric_control_references_do_not_collide_with_private_markers_or_literal_reference_text() {
        withRoot { root ->
            writePreferences(root, STATE, mapOf("state_v1" to snapshot()))
            val controls = (0..31).map { it.toChar() }.joinToString("")
            val text = "private \uE0000000 \uE000001F \uE000\uE001\uE002\uF8FF; literal &#31; &#x1e;; controls $controls"
            val bytes = writePreferences(root, EXTRAS, mapOf("character.real.notes" to text))
            assertTrue(String(bytes, UTF_8).contains("&amp;#31;"))
            assertTrue("Android's numeric NUL reference is preserved", String(bytes, UTF_8).contains("&#0;"))
            assertEquals(text, source(root).capture().extras("real").notes)
            // The hexadecimal spelling is also accepted without changing the captured bytes.
            val hexBytes = String(bytes, UTF_8).replace("&#0;", "&#x0;").replace("&#31;", "&#x1F;").replace("&#30;", "&#x1e;").toByteArray(UTF_8)
            Files.write(preferences(root).resolve(EXTRAS), hexBytes)
            val captured = source(root).capture()
            assertEquals(text, captured.extras("real").notes)
            assertArrayEquals(hexBytes, captured.rawFiles.getValue(EXTRAS))
            for (markerReference in listOf("&#57344;", "&#xE000;")) {
                val markerBytes = String(hexBytes, UTF_8).replace("\uE000", markerReference).toByteArray(UTF_8)
                Files.write(preferences(root).resolve(EXTRAS), markerBytes)
                val markerCapture = source(root).capture()
                assertEquals(text, markerCapture.extras("real").notes)
                assertArrayEquals(markerBytes, markerCapture.rawFiles.getValue(EXTRAS))
            }
        }
    }

    @Test fun unknown_extras_fields_enums_and_lossy_nested_codecs_refuse_with_original_export() = runBlocking {
        val cases = listOf<Pair<String, Any>>(
            "future_field" to "must retain", "conditions" to setOf("FUTURE_CONDITION"),
            "hidden_resources" to setOf("FUTURE_RESOURCE"),
            "preferred_skill_attributes" to setOf("skill::FUTURE_ATTRIBUTE"),
            "preferred_skill_attributes" to setOf("skill::STRENGTH", "skill::WILL"),
            "skill_groups" to "malformed", "development_groups" to "malformed",
            "condition_overrides" to "malformed", "custom_conditions" to "malformed",
            "note_entries" to "malformed", "portrait" to 7,
        )
        for ((key, value) in cases) withRoot { root ->
            val stateBytes = writePreferences(root, STATE, mapOf("state_v1" to snapshot()))
            val extrasBytes = writePreferences(root, EXTRAS, mapOf("character.real.$key" to value))
            val acquisition = store(root)
            assertTrue("$key must refuse migration", acquisition.prepareMigration("real", PACKS, null) is MigrationResult.Unsupported)
            assertFalse(Files.exists(acquisition.envelopePath("real")))
            assertExport(acquisition.recoveryExport("real"), mapOf(STATE to stateBytes, EXTRAS to extrasBytes))
            assertArrayEquals(extrasBytes, Files.readAllBytes(preferences(root).resolve(EXTRAS)))
        }
    }

    @Test fun preference_backup_refuses_before_any_branch_is_consumed() = runBlocking {
        for (backupName in listOf("$STATE.bak", "$EXTRAS.bak")) withRoot { root ->
            val stateBytes = writePreferences(root, STATE, mapOf("state_v1" to snapshot()))
            val extrasBytes = writePreferences(root, EXTRAS, mapOf("character.real.notes" to "head"))
            val backupBytes = "unparsed alternate branch <$backupName>".toByteArray(UTF_8)
            Files.write(preferences(root).resolve(backupName), backupBytes)
            val source = source(root)
            var memoryRead = false
            source.memoryReader = { memoryRead = true; emptyMap<String, Any>() to emptyMap<String, Any>() }
            val failure = unavailable { source.capture() }
            assertFalse(failure.unsupported)
            assertFalse("A backup refuses before consulting framework memory", memoryRead)
            val expected = mapOf(STATE to stateBytes, EXTRAS to extrasBytes, backupName to backupBytes)
            assertExport(failure.rawFiles, expected)
            val acquisition = store(root, source)
            assertTrue(acquisition.load("real") is LoadResult.Corrupt)
            assertTrue(acquisition.prepareMigration("real", PACKS, null) is MigrationResult.Corrupt)
            assertTrue(acquisition.compareAndSet("real", -1, t13State()) is CommitResult.StorageFailure)
            assertExport(acquisition.recoveryExport("real"), expected)
            assertFalse(Files.exists(acquisition.envelopePath("real")))
            expected.forEach { (name, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(preferences(root).resolve(name))) }
        }
    }

    @Test fun framework_memory_disk_disagreement_in_value_or_type_refuses_without_rewriting() {
        val raw = snapshot()
        val diskState = mapOf<String, Any>("state_v1" to raw, "counter" to 7)
        val diskExtras = mapOf<String, Any>("character.real.conditions" to setOf("TIRED"))
        val memoryCases = listOf(
            (diskState + ("state_v1" to "$raw ")) to diskExtras,
            (diskState + ("counter" to 7L)) to diskExtras,
            diskState to (diskExtras + ("character.real.conditions" to setOf("FEAR"))),
        )
        for ((memoryState, memoryExtras) in memoryCases) withRoot { root ->
            val stateBytes = writePreferences(root, STATE, diskState)
            val extrasBytes = writePreferences(root, EXTRAS, diskExtras)
            val source = source(root)
            source.memoryReader = { memoryState to memoryExtras }
            val failure = unavailable { source.capture() }
            assertTrue(failure.message.orEmpty().contains("memory/disk"))
            assertExport(failure.rawFiles, mapOf(STATE to stateBytes, EXTRAS to extrasBytes))
            assertArrayEquals(stateBytes, Files.readAllBytes(preferences(root).resolve(STATE)))
            assertArrayEquals(extrasBytes, Files.readAllBytes(preferences(root).resolve(EXTRAS)))
        }
    }

    @Test fun duplicate_entities_invalid_utf8_and_illegal_control_xml_refuse_with_bytes_intact() = runBlocking {
        val validMap = String(preferenceXml(mapOf("state_v1" to snapshot())), UTF_8)
        val extra = "<string name=\"other\">one</string>"
        val invalidReferences = listOf(
            "&#+31;", "&#-0;", "&#x+1F;", "&#x-0;", "&#\u0663\u0661;", "&#x\u0661f;", "&#X1F;",
            "&#99999999999999999999999999999;", "&#xFFFFFFFFFFFFFFFFFFFFFFFF;",
        )
        val cases = listOf(
            validMap.replace("</map>", "$extra$extra</map>").toByteArray(UTF_8),
            validMap.replace("</map>", "<set name=\"other\"><string>same</string><string>same</string></set></map>").toByteArray(UTF_8),
            ("<!DOCTYPE map [<!ENTITY leak SYSTEM \"file:///not-a-fixture\">]>" + validMap.replace("</map>", "<string name=\"other\">&leak;</string></map>")).toByteArray(UTF_8),
            validMap.replace("</map>", "<!-- unexpected --></map>").toByteArray(UTF_8),
            validMap.replace("</map>", "<string name=\"other\">&undeclared;</string></map>").toByteArray(UTF_8),
            validMap.replace("</map>", "<string name=\"other\">\u001f</string></map>").toByteArray(UTF_8),
            validMap.replace("</map>", "<string name=\"other\">\u0000</string></map>").toByteArray(UTF_8),
            validMap.toByteArray(UTF_8) + byteArrayOf(0xc3.toByte(), 0x28),
        ) + invalidReferences.map { reference ->
            validMap.replace("</map>", "<string name=\"other\">$reference</string></map>").toByteArray(UTF_8)
        }
        for (bytes in cases) withRoot { root ->
            Files.createDirectories(preferences(root))
            Files.write(preferences(root).resolve(STATE), bytes)
            val failure = unavailable { source(root).capture() }
            assertFalse(failure.unsupported)
            assertArrayEquals(bytes, failure.rawFiles.getValue(STATE))
            val acquisition = store(root)
            assertTrue(acquisition.load("real") is LoadResult.Corrupt)
            assertTrue(acquisition.compareAndSet("real", -1, t13State()) is CommitResult.StorageFailure)
            assertExport(acquisition.recoveryExport("real"), mapOf(STATE to bytes))
            assertFalse(Files.exists(acquisition.envelopePath("real")))
            assertArrayEquals(bytes, Files.readAllBytes(preferences(root).resolve(STATE)))
        }
    }

    @Test fun malformed_or_future_snapshot_and_existing_preferences_without_state_never_become_fresh() = runBlocking {
        val malformed = listOf(
            mapOf<String, Any>("state_v1" to "{broken"),
            mapOf<String, Any>("state_v1" to "{\"schema\":99,\"characters\":[{\"id\":\"real\"}]}"),
            mapOf<String, Any>("state_v1" to 11),
            mapOf<String, Any>("unrelated" to "existing data"),
        )
        for (values in malformed) withRoot { root ->
            val bytes = writePreferences(root, STATE, values)
            val acquisition = store(root)
            val loaded = acquisition.load("real")
            assertTrue(loaded is LoadResult.Corrupt || loaded is LoadResult.Unsupported)
            assertTrue(acquisition.compareAndSet("real", -1, t13State()) is CommitResult.StorageFailure)
            assertExport(acquisition.recoveryExport("real"), mapOf(STATE to bytes))
            assertFalse(Files.exists(acquisition.envelopePath("real")))
        }
        withRoot { root ->
            val bytes = writePreferences(root, EXTRAS, mapOf("character.real.notes" to "orphaned extras"))
            assertTrue(store(root).load("real") is LoadResult.Corrupt)
            assertExport(store(root).recoveryExport("real"), mapOf(EXTRAS to bytes))
        }
    }

    @Test fun repeated_capture_is_stable_and_unrelated_character_or_active_selection_does_not_stale_selected_source() {
        withRoot { root ->
            val a = DublCharacter("real", name = "A")
            val b = DublCharacter("other", name = "B")
            val values = mapOf<String, Any>("character.real.notes" to "A notes", "character.other.notes" to "B notes")
            writePreferences(root, STATE, mapOf("state_v1" to snapshot(listOf(a, b), "real")))
            writePreferences(root, EXTRAS, values)
            val source = source(root)
            val original = source.capture().origin("real", PACKS)
            assertEquals(original, source.capture().origin("real", PACKS))
            assertEquals(original.sourceFingerprint, source.capture().origin("real", PACKS).sourceFingerprint)
            assertTrue(source.unchanged(original))

            writePreferences(root, STATE, mapOf("state_v1" to snapshot(listOf(a, b.copy(name = "B changed")), "other"), "global" to true))
            writePreferences(root, EXTRAS, values + ("character.other.notes" to "B changed") + ("global" to 9))
            val unrelated = source.capture().origin("real", PACKS)
            assertEquals(original.sourceRevision, unrelated.sourceRevision)
            assertNotEquals(original.sourceFingerprint, unrelated.sourceFingerprint)
            assertTrue(source.unchanged(original))

            writePreferences(root, EXTRAS, values + ("character.real.notes" to "A changed"))
            assertFalse(source.unchanged(original))
            assertNotEquals(original.sourceRevision, source.capture().origin("real", PACKS).sourceRevision)
            writePreferences(root, EXTRAS, values)
            writePreferences(root, STATE, mapOf("state_v1" to snapshot(listOf(a.copy(name = "A changed"), b), "real")))
            assertFalse(source.unchanged(original))
        }
    }

    @Test fun selected_character_fingerprint_keeps_raw_financial_number_lexemes() {
        withRoot { root ->
            val first = """{"schema":11,"characters":[{"id":"real","gear":{"loadManual":-0,"items":[]}}]}"""
            val second = first.replace("\"loadManual\":-0", "\"loadManual\":-0.0")
            writePreferences(root, STATE, mapOf("state_v1" to first))
            val source = source(root)
            val origin = source.capture().origin("real", PACKS)
            writePreferences(root, STATE, mapOf("state_v1" to second))
            assertFalse(source.unchanged(origin))
            assertNotEquals(origin.sourceRevision, source.capture().origin("real", PACKS).sourceRevision)
        }
    }

    @Test fun explicit_migration_requires_balance_then_preserves_negative_opening_and_unfinished_creation() = runBlocking {
        withRoot { root ->
            val raw = " \n" + snapshot(listOf(DublCharacter("real", experience = 400, creationComplete = false))) + "\n"
            val stateBytes = writePreferences(root, STATE, mapOf("state_v1" to raw))
            val extrasBytes = writePreferences(root, EXTRAS, mapOf("character.real.notes" to "Keep this"))
            val acquisition = store(root)
            assertTrue(acquisition.load("real") is LoadResult.MigrationRequired)
            assertFalse(Files.exists(acquisition.envelopePath("real")))
            val declared = listOf("dubl-3.69", "unavailable-old-addon")
            val pending = pending(acquisition.prepareMigration("real", declared, null))
            assertEquals(declared, pending.origin.activePackIds)
            assertEquals(raw, pending.origin.raw)
            assertFalse(pending.character.creationComplete)
            assertFalse(Files.exists(acquisition.envelopePath("real")))
            assertTrue(acquisition.prepareMigration("real", emptyList(), null) is MigrationResult.Corrupt)
            assertFalse(Files.exists(acquisition.envelopePath("real")))
            val confirmed = confirmLegacyBalance(pending, Amount(-23, -4), "Verified old debt")
            assertEquals(OpeningProvenance.UserConfirmed, confirmed.ledger.opening.provenance)
            assertEquals(Amount(-23, -4), balance(confirmed.ledger))
            assertEquals(RequirementNode.CreationPhase.Phase.Creating, confirmed.creationPhase)
            assertTrue(confirmed.ledger.entries.isEmpty())
            assertTrue(confirmed.receipts.isEmpty())
            assertNull(confirmed.ledger.opening.legacyMigration!!.catalogIdentity)
            assertEquals(CommitResult.Committed, acquisition.compareAndSet("real", -1, confirmed))

            val reopened = (store(root).load("real") as LoadResult.Loaded).state
            assertEquals(confirmed, reopened)
            assertEquals(Amount(-23, -4), balance(reopened.ledger))
            assertEquals(raw, reopened.ledger.opening.legacyMigration!!.origin.raw)
            assertEquals(declared, reopened.ledger.opening.legacyMigration!!.origin.activePackIds)
            assertEquals("Keep this", reopened.extras.notes)
            assertArrayEquals(stateBytes, Files.readAllBytes(preferences(root).resolve(STATE)))
            assertArrayEquals(extrasBytes, Files.readAllBytes(preferences(root).resolve(EXTRAS)))
        }
    }

    @Test fun raw_slices_keep_escaped_delimiters_quotes_backslashes_and_lone_surrogate_escape() {
        val raw = """ { "text":"comma,: braces{}[] quote\" slash\\ end\uD800", "nested":[{"key,:[]":"value},:[\\\""},"tail"], "number":2e1 } """
        val fields = AndroidRawJson.fields(raw)
        assertEquals(setOf("text", "nested", "number"), fields.keys)
        assertEquals("comma,: braces{}[] quote\" slash\\ end\uD800", AndroidRawJson.string(fields.getValue("text")))
        val nested = AndroidRawJson.array(fields.getValue("nested"))
        assertEquals(2, nested.size)
        assertEquals("value},:[\\\"", AndroidRawJson.string(AndroidRawJson.fields(nested[0]).getValue("key,:[]")))
        assertEquals("tail", AndroidRawJson.string(nested[1]))
        assertEquals("2e1", fields.getValue("number"))
        assertEquals("\"\\ud800\"", AndroidRawJson.quote("\uD800"))
        assertEquals("\uD800", AndroidRawJson.string("\"\\uD800\""))
    }

    @Test fun raw_slices_keep_signed_zero_exponents_and_long_decimal_tokens_without_double_roundtrip() {
        val tokens = listOf("-0", "-0.0", "2e1", "200E-1", "1e-400", "1.00000000000000000001", "2147483647.0000001")
        assertEquals(tokens, AndroidRawJson.array(tokens.joinToString(",", "[", "]")))
        val fields = AndroidRawJson.fields(tokens.mapIndexed { index, token -> "\"n$index\":$token" }.joinToString(",", "{", "}"))
        tokens.forEachIndexed { index, token -> assertEquals(token, fields.getValue("n$index")) }
    }

    @Test fun raw_slices_refuse_duplicate_keys_after_shared_validation_including_escaped_aliases() {
        for (raw in listOf("""{"same":1,"same":2}""", """{"a":1,"\u0061":2}""", """{"nested":{"same":1,"same":2}}""")) {
            rejects { AndroidRawJson.fields(raw) }
        }
        rejects { AndroidRawJson.array("""[{"same":1,"same":2}]""") }
        rejects { AndroidRawJson.fields("""{"a":[1,],"b":2}""") }
    }

    private fun source(root: Path) = AndroidLegacyAcquisitionSource(preferences(root))
    private fun store(root: Path, legacy: AndroidLegacyAcquisitionSource = source(root)) = AndroidAcquisitionStore(root, legacy, JvmFileOps)
    private fun preferences(root: Path) = root.resolve("shared_prefs")
    private fun snapshot(characters: List<DublCharacter> = listOf(DublCharacter("real")), active: String = characters.first().id) =
        SnapshotCodec.encode(AppSnapshot(characters, active))

    private fun populatedExtras() = CharacterSheetExtras(
        portraitUri = "content://portraits/real?x=1&y=2",
        activeConditions = setOf(CharacterConditionId.TIRED, CharacterConditionId.INSPIRED),
        hiddenResourceIds = setOf(CharacterSheetResourceId.MANA, CharacterSheetResourceId.CHI),
        preferredSkillAttributes = mapOf("skill::nested" to AttributeId.WILL, "skill-other" to AttributeId.STRENGTH),
        skillGroups = listOf(SheetGroup("skills-one", "Skills <&>", listOf("skill::nested", "skill-other"), true), SheetGroup("skills-two", "Empty")),
        developmentGroups = listOf(SheetGroup("development", "Development", listOf("owned-one"))),
        conditionOverrides = mapOf(CharacterConditionId.TIRED to ConditionLocalOverride("Tired <&>", "Local description")),
        customConditions = listOf(CustomCondition("custom-one", "Custom condition", "Description", true), CustomCondition("custom-two", "Second")),
        notes = "Legacy notes \uE000\uE001 and literal &#31; <&>\nKeep both note forms",
        noteEntries = listOf(CharacterNote("note-one", "Title <&>", "Body with spaces /+%"), CharacterNote("note-two", "Second", "Another")),
    )

    private fun extrasPreferences(extras: CharacterSheetExtras): Map<String, Any> = linkedMapOf(
        "character.real.portrait" to requireNotNull(extras.portraitUri),
        "character.real.conditions" to extras.activeConditions.map { it.name }.toSet(),
        "character.real.hidden_resources" to extras.hiddenResourceIds.map { it.name }.toSet(),
        "character.real.preferred_skill_attributes" to extras.preferredSkillAttributes.map { (id, attribute) -> "$id::${attribute.name}" }.toSet(),
        "character.real.skill_groups" to SheetGroupingRules.encode(extras.skillGroups),
        "character.real.development_groups" to SheetGroupingRules.encode(extras.developmentGroups),
        "character.real.condition_overrides" to ConditionLocalDataCodec.encodeOverrides(extras.conditionOverrides),
        "character.real.custom_conditions" to ConditionLocalDataCodec.encodeCustom(extras.customConditions),
        "character.real.notes" to extras.notes,
        "character.real.note_entries" to CharacterNoteDataCodec.encode(extras.noteEntries),
    )

    /** Synthetic preference XML, including Android-writer numeric references for codec controls. */
    private fun preferenceXml(values: Map<String, Any>): ByteArray = buildString {
        append("<map>\n")
        values.forEach { (name, value) ->
            val key = xmlEscape(name)
            when (value) {
                is String -> append("<string name=\"$key\">${xmlEscape(value)}</string>\n")
                is Set<*> -> {
                    append("<set name=\"$key\">")
                    value.forEach { append("<string>${xmlEscape(it as String)}</string>") }
                    append("</set>\n")
                }
                is Boolean -> append("<boolean name=\"$key\" value=\"$value\" />\n")
                is Int -> append("<int name=\"$key\" value=\"$value\" />\n")
                is Long -> append("<long name=\"$key\" value=\"$value\" />\n")
                is Float -> append("<float name=\"$key\" value=\"$value\" />\n")
                else -> error("Unsupported test preference type")
            }
        }
        append("</map>\n")
    }.toByteArray(UTF_8)

    private fun xmlEscape(value: String): String = buildString {
        value.forEach { character -> when (character) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;")
            '"' -> append("&quot;"); '\'' -> append("&apos;"); '\r' -> append("&#13;")
            else -> if (character.code in 0..31 && character != '\n' && character != '\t') append("&#${character.code};") else append(character)
        } }
    }

    private fun writePreferences(root: Path, name: String, values: Map<String, Any>): ByteArray {
        val bytes = preferenceXml(values)
        Files.createDirectories(preferences(root))
        Files.write(preferences(root).resolve(name), bytes)
        return bytes
    }

    private fun assertBundleFile(bundle: Map<String, String>, name: String, expected: ByteArray?) {
        val files = AndroidRawJson.fields(bundle.getValue("files"))
        assertEquals(setOf(STATE, "$STATE.bak", EXTRAS, "$EXTRAS.bak"), files.keys)
        val file = AndroidRawJson.fields(files.getValue(name))
        assertEquals((expected != null).toString(), file.getValue("present"))
        if (expected == null) assertEquals("null", file.getValue("bytes"))
        else assertArrayEquals(expected, Base64.getDecoder().decode(AndroidRawJson.string(file.getValue("bytes"))))
    }

    private fun assertExport(actual: Map<String, ByteArray>, expected: Map<String, ByteArray>) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (name, bytes) -> assertArrayEquals(name, bytes, actual.getValue(name)) }
    }

    private fun pending(result: MigrationResult): PendingMigration {
        assertTrue("Explicit balance confirmation required: $result", result is MigrationResult.NeedsBalanceConfirmation)
        return (result as MigrationResult.NeedsBalanceConfirmation).pending
    }

    private fun unavailable(action: () -> Unit): AndroidSourceUnavailable {
        try { action() } catch (failure: AndroidSourceUnavailable) { return failure }
        throw AssertionError("Expected source refusal")
    }

    private fun rejects(action: () -> Unit) {
        try { action() } catch (_: IllegalArgumentException) { return } catch (_: IllegalStateException) { return }
        throw AssertionError("Expected strict JSON refusal")
    }

    private inline fun <T> withRoot(action: (Path) -> T): T {
        val root = Files.createTempDirectory("t13-legacy-host-")
        try { return action(root) } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    private companion object {
        const val STATE = "dubl_native_state.xml"
        const val EXTRAS = "dubl_character_sheet_extras.xml"
        val PACKS = listOf("dubl-3.69")
    }
}
