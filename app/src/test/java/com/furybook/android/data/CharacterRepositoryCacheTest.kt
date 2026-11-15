package com.furybook.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CharacterRepositoryCacheTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun snapshot(name: String = "Тест") = AppSnapshot(listOf(DublCharacter("a", name = name).normalized()), "a")

    @Test fun repeatedLoadReusesDecodedSnapshot() {
        val repository = CharacterRepository(context)
        repository.save(snapshot())
        assertSame("Repeated observer reads must not decode the whole roster", repository.load(), repository.load())
    }

    @Test fun otherRepositoryWriteInvalidatesCache() {
        val first = CharacterRepository(context)
        val second = CharacterRepository(context)
        first.save(snapshot("до"))
        first.load()
        second.save(snapshot("после"))
        assertEquals("после", first.load().activeCharacter.name)
    }

    @Test fun rawPreferenceWriteInvalidatesCache() {
        val repository = CharacterRepository(context)
        repository.save(snapshot("before"))
        repository.load()
        context.getSharedPreferences("dubl_native_state", Context.MODE_PRIVATE).edit()
            .putString("state_v1", SnapshotCodec.encode(snapshot("cloud"))).commit()
        assertEquals("cloud", repository.load().activeCharacter.name)
    }

    @Test fun saveKeepsCanonicalRoundTripAndRestartSemantics() {
        val repository = CharacterRepository(context)
        val source = snapshot("Здоровье \"\n").let {
            it.copy(characters = it.characters + DublCharacter("b", size = 99, experience = -10), activeCharacterId = "absent")
        }
        repository.save(source)
        val raw = context.getSharedPreferences("dubl_native_state", Context.MODE_PRIVATE).getString("state_v1", null)!!
        assertEquals(SnapshotCodec.encode(source), raw)
        assertEquals(SnapshotCodec.decode(raw) { "unused" }, repository.load())
        assertEquals(repository.load(), CharacterRepository(context).load())
        repository.save(AppSnapshot(listOf(source.characters.first()), "a"))
        assertEquals(listOf("a"), repository.load().characters.map { it.id })
    }
}
