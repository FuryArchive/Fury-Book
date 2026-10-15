package com.furybook.android.ui.acquisition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.furybook.dubl.acquisition.AcquisitionInstanceView
import com.furybook.dubl.acquisition.AcquisitionKind
import com.furybook.dubl.acquisition.acquisitionAbilityOptionLabel
import com.furybook.ui.components.DublCard

/** Opens the saved instance directly, preserving its choices and independent rank. */
@Composable
fun CanonicalOwnedDevelopmentCard(instance: AcquisitionInstanceView, modifier: Modifier = Modifier) {
    val actions=LocalAndroidAcquisition.current ?: return
    if(!actions.canonical) return
    val definition=actions.view?.catalog?.definitions?.get(instance.definition) ?: return
    DublCard(modifier.fillMaxWidth().testTag("acquisition.instance.${instance.instanceId}")) {
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(instance.label,style=MaterialTheme.typography.titleSmall)
            Text("Ранг ${instance.rank}",style=MaterialTheme.typography.bodyMedium)
            instance.optionId?.let { Text(acquisitionAbilityOptionLabel(definition,it),style=MaterialTheme.typography.bodySmall) }
            Button(enabled=definition.maxRank?.let { instance.rank<it }!=false,
                onClick={ actions.openTarget(AcquisitionKind.DEVELOPMENT,instance.instanceId,instance.rank+1) }) {
                Text("Ранг ${instance.rank} → ${instance.rank+1}")
            }
        }
    }
}
