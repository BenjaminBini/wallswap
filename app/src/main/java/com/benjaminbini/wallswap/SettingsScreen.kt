package com.benjaminbini.wallswap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val INTERVALS = listOf(0, 15, 30, 60, 180, 360, 720, 1440)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    interval: Int,
    auto: Map<Screen, Boolean>,
    batteryUnrestricted: Boolean,
    onBack: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    onAutoChange: (Screen, Boolean) -> Unit,
    onAllowBattery: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Header("Auto-change")
            Screen.entries.forEach { screen ->
                ListItem(
                    headlineContent = { Text(screen.title) },
                    supportingContent = { Text("Changes while the phone is ${screen.inUseWhen}") },
                    trailingContent = { Switch(checked = auto.getValue(screen), onCheckedChange = { onAutoChange(screen, it) }) },
                )
            }
            IntervalItem(interval, onIntervalChange)

            Header("Background")
            ListItem(
                headlineContent = { Text("Run unrestricted") },
                supportingContent = {
                    Text(
                        if (batteryUnrestricted) "Allowed: automatic changes keep running"
                        else "Samsung may put WallSwap to sleep and skip changes",
                    )
                },
                trailingContent = {
                    if (batteryUnrestricted) {
                        Icon(Icons.Filled.Check, contentDescription = "Allowed")
                    } else {
                        Button(onClick = onAllowBattery) { Text("Allow") }
                    }
                },
            )

            Header("About")
            ListItem(
                headlineContent = { Text("Photos from Unsplash") },
                supportingContent = {
                    Text("A wallpaper change uses 2 of the 50 Unsplash requests per hour, and each page of album photos uses 1.")
                },
            )
        }
    }
}

@Composable
private fun Header(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun IntervalItem(minutes: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Change every") },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) { Text(intervalLabel(minutes)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    INTERVALS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(intervalLabel(option)) },
                            onClick = {
                                expanded = false
                                onChange(option)
                            },
                        )
                    }
                }
            }
        },
    )
}

private fun intervalLabel(minutes: Int): String = when {
    minutes == 0 -> "Never (manual only)"
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "Hour"
    minutes < 1440 -> "${minutes / 60} hours"
    else -> "Day"
}
