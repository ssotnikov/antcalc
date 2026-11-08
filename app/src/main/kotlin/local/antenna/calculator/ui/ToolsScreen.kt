package local.antenna.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R

@Composable
internal fun ToolsScreen(onOpenMatching: () -> Unit, onOpenCable: () -> Unit) {
    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToolCard(R.string.matching_title, R.string.matching_description, "open-matching", onOpenMatching)
        ToolCard(R.string.cable_title, R.string.cable_description, "open-cable", onOpenCable)
    }
}

@Composable
private fun ToolCard(title: Int, description: Int, tag: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
