package com.furybook.dev

import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.data.DesktopCharacterExtrasStore
import com.furybook.dubl.data.DesktopCharacterStore
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test

/**
 * Dev fixture, not a check: with SEED_DIR set, writes two filled characters for screenshots —
 * Шершень (an ordinary sheet) and the baron (9 resources, 8 conditions, 20+ perks, 12 notes, a long name).
 * Run: rm -rf /tmp/seedhome && SEED_DIR=/tmp/seedhome ./gradlew :shared:desktopTest --tests '*SeedDevCharacters*'
 * then start the desktop app with XDG_DATA_HOME=/tmp/seedhome.
 */
class SeedDevCharacters {
    @Test
    fun seed() {
        val dir = System.getenv("SEED_DIR")?.takeIf { it.isNotBlank() } ?: return
        val root = Path.of(dir).resolve("dubl-character")
        val app = DublApplication(
            characterStore = DesktopCharacterStore(root.resolve("characters.json")),
            extrasStore = DesktopCharacterExtrasStore(root.resolve("sheet-extras.json")),
            idFactory = { UUID.randomUUID().toString() },
        )
        seedHornet(app)
        app.character.createCharacter()
        seedBaron(app)
    }

    private fun DublApplication.attributes(values: List<Int>) =
        AttributeId.entries.zip(values).forEach { (id, value) -> repeat(value) { character.changeAttribute(id, 1) } }

    private fun DublApplication.skillRanks(ranks: Map<String, Int>) =
        ranks.forEach { (id, rank) -> repeat(rank) { skills.changeRank(id, 1) } }

    private fun seedHornet(app: DublApplication) = with(app) {
        character.setIdentity("Шершень", "Наёмник из Нижнего города, бывший вор", 5, 2, true)
        attributes(listOf(2, 8, 6, 8, 4, 6, 7, 1))
        character.setEconomy(4580, 1200, 0, null)
        skillRanks(
            mapOf(
                "melee_weapon" to 7, "stealth" to 5, "driving" to 2, "awareness" to 3, "athletics" to 4, "shooting" to 3,
                "intimidation" to 2, "lockpicking" to 4, "medicine" to 1, "survival" to 2, "unarmed" to 3, "eloquence" to 1,
            ),
        )
        listOf(
            "feat_e05a30e954c90cd6", "feat_0682bdd416ebe1c5", "access_11f3a49ee9802ad0",
            "feat_fe005b143b217b52", "feat_3f69c42fe3a960dd", "martial_style_f5dae0d0bb88dcd1",
        ).forEach { development.setRank(it, 1) }
        magic.setManaRank(2)
        character.completeCreation()
        character.changeHp(-9)
        character.changeEndurance(-1)
        character.addCustomResource("Ярость", 5, 3)
        sheet.toggleCondition(CharacterConditionId.DAZED)
        sheet.addCustomCondition("Отравлен", "−1 ко всем проверкам до отдыха", true)
        sheet.addNote("Долги", "Должен 300 крон Хромому Гансу. Срок — до следующей луны.")
        sheet.addNote("Контакты", "Мира — скупщица в порту. Отец Альберт прячет беглецов.")
    }

    private fun seedBaron(app: DublApplication) = with(app) {
        character.setIdentity(
            "Огюст-Анри-Себастьян де Монревель д’Аржантель, Барон де Стигийский",
            "Аристократ в изгнании, маг-дуэлянт, должник половины столицы", 5, 2, true,
        )
        attributes(listOf(4, 6, 5, 5, 7, 6, 8, 9))
        character.setEconomy(9800, 1500, 0, null)
        skillRanks(
            mapOf(
                "melee_weapon" to 6, "shooting" to 4, "unarmed" to 3, "eloquence" to 6, "leadership" to 5, "intimidation" to 3,
                "barter" to 4, "performance_vocal" to 2, "awareness" to 4, "search" to 3, "survival" to 2, "stealth" to 1,
                "knowledge_academic" to 5, "medicine" to 2, "driving" to 1, "athletics" to 2, "riding" to 4,
            ),
        )
        mapOf(
            "feat_bf7b04eb68961725" to 3, "feat_46408580837ac2b3" to 1, "feat_36930110e6df163a" to 1, "feat_5d04a18357baa173" to 1,
            "feat_d93dd2d62f378864" to 1, "feat_5b6c5408dcdf4c96" to 1, "martial_style_945b0cbe583e071d" to 1,
            "martial_style_6f736b57bce826e7" to 1, "feat_e05a30e954c90cd6" to 1, "feat_0682bdd416ebe1c5" to 1,
            "feat_4add442b30aea005" to 2, "feat_a5d3b291d6db6600" to 1, "feat_5fdb014afbcc0807" to 2, "feat_208b697799296d8e" to 1,
            "feat_5b3b68dd9e06774a" to 1, "feat_2e91e66aac3641d8" to 1,
            "access_17f99cc35c559d82" to 1, "feat_9813ba6aff7624ad" to 1, "access_5b50c9c8ead88fd5" to 1, "feat_43a03b64a656118f" to 1,
            "access_40c3d6bcbdac132a" to 1, "feat_d311a68493a4872f" to 2, "access_3080526640119bbc" to 1, "feat_c5ca770b130c02b4" to 1,
            "feat_f798eee5d98707fa" to 1, "access_11f3a49ee9802ad0" to 1, "feat_3f69c42fe3a960dd" to 1,
        ).forEach { (id, rank) -> development.setRank(id, rank) }
        magic.setManaRank(3)
        character.completeCreation()
        character.changeHp(-17)
        character.changeEndurance(-2)
        listOf("Ярость" to (5 to 3), "Удача" to (3 to 2), "Патроны" to (60 to 37), "Порча" to (10 to 6),
            "Влияние при дворе" to (20 to 12), "Концентрация" to (4 to 1))
            .forEach { (name, values) -> character.addCustomResource(name, values.first, values.second) }
        listOf(
            CharacterConditionId.DAZZLED, CharacterConditionId.POISONED, CharacterConditionId.EXHAUSTION, CharacterConditionId.FEAR,
            CharacterConditionId.ENTANGLED, CharacterConditionId.PRONE, CharacterConditionId.INSPIRED,
        ).forEach(sheet::toggleCondition)
        sheet.addCustomCondition("Проклятие ворона", "Каждый крит мастера — бросок порчи. До заката.", true)
        listOf(
            "Долги" to "Банк Вессели — 4000 крон, срок через месяц. Графиня Ланж — фамильное кольцо в залоге. Хромой Ганс — 300 крон, проценты каждую неделю.",
            "Цель кампании" to "Вернуть титул и поместье в Стигии; доказать, что брат подделал завещание.",
            "Контакты" to "Мира — скупщица в порту. Отец Альберт прячет беглецов. Капитан стражи Рейнер должен услугу.",
            "Враги" to "Брат Филипп, гильдия Чёрного пера, инквизитор Морла.",
            "Заклинания на подготовке" to "Щит маны, Магическое пламя, Исцеляющее касание.",
            "Снаряжение в тайнике" to "Под мостом у мельницы: второй пистолет, 40 патронов, письма отца.",
            "Слухи" to "В катакомбах под собором видели ворона с человеческими глазами.",
            "Сессия 12" to "Нашли печать гильдии у убитого курьера. Мира узнала гравёра.",
            "Сессия 13" to "Дуэль с виконтом отложена до бала. Поставил на кон кольцо.",
            "Сессия 14" to "Порча выросла до 6 — после ритуала в склепе.",
            "Карта города" to "Безопасные дома: таверна «Сломанное колесо», часовня у ворот.",
            "Идеи" to "Подкупить писаря нотариуса? Узнать, кто платит Морле.",
        ).forEach { (title, body) -> sheet.addNote(title, body) }
    }
}
