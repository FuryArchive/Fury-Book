package com.furybook.dubl.acquisition

/** Source-bound group ownership is stored in ordinary canonical acquisition instances. */
data class OwnedWeaponProficiency(val instanceId: String, val groupId: String, val groupName: String,
    val sourceRef: RuleSourceRef, val weaponKinds: List<WeaponProficiencyKind>)

/** Labels never establish membership. Custom groups remain owned without inferred weapon kinds. */
object AcquisitionWeaponProficiencies {
    fun ownedGroups(instances: List<AcquisitionInstance>, catalog: AcquisitionCatalog): List<OwnedWeaponProficiency> =
        EnvelopeList(instances.mapNotNull { owned ->
            val definition = catalog.definitions[owned.definition] ?: return@mapNotNull null
            val source = definition.classificationSources["weapon-proficiency"]
                ?.takeIf { it.originalText.isNotBlank() } ?: return@mapNotNull null
            if (definition.kind != AcquisitionKind.DEVELOPMENT || owned.rank < 1 || definition.sourceStatus == SourceStatus.Unavailable)
                return@mapNotNull null
            val parameter = definition.parameterDefinitions.singleOrNull { it.identityDomain == "weapon-proficiency-group" }
                ?: return@mapNotNull null
            val groupId = owned.parameters[parameter.key] ?: return@mapNotNull null
            val name = parameter.displayNameKey?.let { owned.parameters[it] } ?: return@mapNotNull null
            val choice = parameter.choices.singleOrNull { it.id == groupId }
            OwnedWeaponProficiency(owned.instanceId, groupId, name, source, EnvelopeList(choice?.weaponKinds.orEmpty()))
        })

    fun ownedKinds(instances: List<AcquisitionInstance>, catalog: AcquisitionCatalog): List<WeaponProficiencyKind> =
        EnvelopeList(ownedGroups(instances, catalog).flatMap { it.weaponKinds }.groupBy { it.id }.values.mapNotNull { candidates ->
            candidates.firstOrNull()?.takeIf { candidates.map { candidate -> candidate.label }.distinct().size == 1 }
        }.sortedWith(compareBy({ it.label }, { it.id })))
}

class NewParameterValueRequirement(parameterKeys: Set<String>, val minimumNewValues: Int, val sourceRef: RuleSourceRef) {
    val parameterKeys: Set<String> = object : AbstractSet<String>() {
        private val values = parameterKeys.toList()
        override val size get() = values.size
        override fun iterator(): Iterator<String> = object : Iterator<String> {
            private var index = 0
            override fun hasNext() = index < values.size
            override fun next(): String = if (hasNext()) values[index++] else throw NoSuchElementException()
        }
    }
    init { require(this.parameterKeys.isNotEmpty() && this.parameterKeys.all(String::isNotBlank))
        require(minimumNewValues in 1..this.parameterKeys.size && sourceRef.originalText.isNotBlank()) }
}
