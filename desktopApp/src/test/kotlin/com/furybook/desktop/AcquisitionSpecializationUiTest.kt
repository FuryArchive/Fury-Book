package com.furybook.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.furybook.content.FcpTextSource
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.content.DublFcp
import com.furybook.ui.components.SkillSpecializationPicker
import com.furybook.ui.theme.DublTheme
import org.junit.Test
import kotlin.test.*

/** The real skill catalog supplies choices; the player enters names, never storage IDs. */
@OptIn(ExperimentalTestApi::class)
class AcquisitionSpecializationUiTest {
    @Test fun custom_name_and_source_choice_create_explicit_stable_targets() = runDesktopComposeUiTest(width=800,height=700) {
        val catalog = DublFcp.open(FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }).loadAcquisition().catalog
        val definition = catalog.definitions.getValue(DefinitionRef("dubl-3.69", "performance_template"))
        val choices = AcquisitionSkillSpecializations.choices(definition).map { AcquisitionParameterOption(it.id,it.label) }
        var selected: AcquisitionTarget? = null
        setContent { DublTheme {
            SkillSpecializationPicker(choices, true) { name, choice ->
                selected = AcquisitionSkillSpecializations.newTarget(definition,"chosen-instance",name,choiceId=choice)
            }
        } }
        onNodeWithTag("acquisition.specialization.name").performTextInput("Кукольный театр")
        onNodeWithTag("acquisition.specialization.select").assertIsEnabled().performClick()
        runOnIdle {
            assertEquals("Кукольный театр",selected?.parameters?.get("specializationName"))
            assertEquals("custom:chosen-instance",selected?.parameters?.get("specialization"))
        }
        onNodeWithTag("acquisition.specialization.option.dance").performClick()
        onNodeWithTag("acquisition.specialization.select").performClick()
        runOnIdle {
            assertEquals("Танцы",selected?.parameters?.get("specializationName"))
            assertEquals("dance",selected?.parameters?.get("specialization"))
        }
    }
}
