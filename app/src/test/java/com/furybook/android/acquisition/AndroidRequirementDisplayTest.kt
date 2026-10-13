package com.furybook.android.acquisition

import com.furybook.android.ui.acquisition.acquisitionRequirementValue
import com.furybook.dubl.acquisition.*
import org.junit.Assert.*
import org.junit.Test

class AndroidRequirementDisplayTest {
    @Test fun actual_required_counts_flags_and_unknowns_keep_distinct_readable_values() {
        assertEquals("3", acquisitionRequirementValue(RequirementValue.Number(3), "Различные крики"))
        assertEquals("1", acquisitionRequirementValue(RequirementValue.Number(1), "Различные крики"))
        assertEquals("да", acquisitionRequirementValue(RequirementValue.Flag(true), "Заклинание изучено"))
        assertEquals("нет", acquisitionRequirementValue(RequirementValue.Flag(false), "Заклинание изучено"))
        assertEquals("не установлено", acquisitionRequirementValue(null, "Заклинание изучено"))
        assertEquals("после создания", acquisitionRequirementValue(RequirementValue.Phase(RequirementNode.CreationPhase.Phase.Finalized), "Этап"))
        assertEquals("Холодное оружие", acquisitionRequirementValue(RequirementValue.StableId("hidden-pack-id", "hidden-stable-id"), "Холодное оружие"))
    }
}
