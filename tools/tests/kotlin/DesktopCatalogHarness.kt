import com.furybook.desktop.data.DesktopCatalogLoader
import java.net.URLClassLoader
import java.nio.file.Path

fun main(args: Array<String>) {
    val root = Path.of(args.single())
    val resources = root.resolve("shared/src/commonMain/resources").toUri().toURL()
    URLClassLoader(arrayOf(resources), DesktopCatalogLoader::class.java.classLoader).use { classLoader ->
        val loader = DesktopCatalogLoader(classLoader)
        val conditions = loader.loadConditions()
        val development = loader.loadDevelopment()
        val chi = loader.loadChi()
        val magic = loader.loadMagicEquipment()
        val effects = loader.loadSkillEffects()
        check(conditions.conditions.size == 23)
        check(conditions.conditions.any { it.name == "Слабость" && it.description.isNotBlank() })
        check(development.entries.size == 768)
        val withChi = loader.loadDevelopment(includeChi = true)
        check(withChi.entries.size == 796)
        check(withChi.entries.map { it.id }.toSet().containsAll(development.entries.map { it.id }))
        check(chi.schools.size == 9 && chi.techniques.size == 68)
        check(magic.spells.size == 265 && magic.gear.size == 260)
        check(effects.effects.size == 283)
        check(development.entries.any { it.name == "Плавание" })
        check(chi.techniques.any { it.name == "Вихрь ударов" })
        check(magic.spells.any { it.name.isNotBlank() })
        println("DESKTOP_CATALOGS_OK dev=${development.entries.size} spells=${magic.spells.size} gear=${magic.gear.size}")
    }
}
