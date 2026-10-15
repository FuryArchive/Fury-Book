package com.furybook.dubl.acquisition

import kotlin.test.*

class AcquisitionNamedParameterSelectionTest {
    @Test fun source_choice_captures_binding_and_custom_name_allocates_identity_only_at_explicit_selection() {
        val binding=mutableMapOf("weaponTypeId" to "ranged")
        val option=AcquisitionParameterOption("known-pistol","Пистолет",binding,sourceChoice=true)
        binding["weaponTypeId"]="melee"
        val field=AcquisitionParameterField("weaponId","Оружие",listOf(option),true,false,displayNameKey="weaponName",identityDomain="weapons")
        val selected=AcquisitionNamedParameters.selectOption(field,option.id,emptyMap())
        assertEquals(mapOf("weaponId" to "known-pistol","weaponName" to "Пистолет","weaponTypeId" to "ranged"),selected)
        var allocated=0
        repeat(3) { assertEquals("Пистолет",AcquisitionNamedParameters.selectedLabel(field,selected)) }
        assertEquals(0,allocated)
        val custom=AcquisitionNamedParameters.selectCustom(field,"  Мой   бластер  ",selected){ allocated++;"opaque" }
        assertEquals(1,allocated)
        assertEquals("custom:opaque",custom["weaponId"])
        assertEquals("Мой бластер",custom["weaponName"])
        assertFalse("weaponTypeId" in custom,"A custom weapon requires its own explicit class selection")
        assertFalse(custom is MutableMap<*,*>)
        assertFailsWith<IllegalArgumentException> { AcquisitionNamedParameters.selectCustom(field,"Пистолет",emptyMap()){ allocated++;"wrong" } }
        assertEquals(1,allocated)
        assertFailsWith<IllegalArgumentException> { AcquisitionNamedParameters.selectOption(field.copy(readOnly=true),option.id,selected) }
    }

    @Test fun equal_custom_organization_names_never_infer_or_merge_identity() {
        val field=AcquisitionParameterField("organizationId","Организация",listOf(AcquisitionParameterOption("saved-id","Братство")),
            true,false,displayNameKey="organizationName",identityDomain="organizations")
        val selected=AcquisitionNamedParameters.selectOption(field,"saved-id",emptyMap())
        assertEquals("saved-id",selected["organizationId"])
        val separate=AcquisitionNamedParameters.selectCustom(field,"Братство",emptyMap()){ "explicit-new" }
        assertEquals("custom:explicit-new",separate["organizationId"])
        assertEquals("Братство",separate["organizationName"])
    }
}
