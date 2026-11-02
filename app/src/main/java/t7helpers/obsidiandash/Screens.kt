package t7helpers.obsidiandash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(app: AppState) {
    var vaultUri by remember { mutableStateOf(app.vaults.first().uri) }
    var query by remember { mutableStateOf("") }
    val vault = app.vaults.firstOrNull { it.uri == vaultUri } ?: app.vaults.first()
    val all = app.notes[vault.uri].orEmpty()
    val results = remember(query, all) { Fuzzy.search(query, all) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Find a note") },
            navigationIcon = {
                IconButton(onClick = { app.screen = Screen.Dashboard }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (app.vaults.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.vaults.forEach { v ->
                        FilterChip(selected = v.uri == vault.uri, onClick = { vaultUri = v.uri }, label = { Text(v.name) })
                    }
                }
            }
            OutlinedTextField(
                query, { query = it },
                Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Type part of a name or path") },
                singleLine = true,
            )
            val status = when {
                vault.uri in app.scanning && all.isEmpty() -> "Reading the vault…"
                all.isEmpty() -> "No notes found."
                vault.uri in app.scanning -> "${all.size} notes, refreshing…"
                query.isBlank() -> "Recently changed notes"
                else -> "${results.size} shown"
            }
            Text(status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.path }) { note ->
                    val pinned = app.pins.any { it.vaultUri == vault.uri && it.path == note.path }
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                app.pin(vault, note)
                                app.screen = Screen.Dashboard
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            note.title + if (pinned) "  ✓ on dashboard" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            note.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: AppState, onAddVault: () -> Unit) {
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<Vault?>(null) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = { app.screen = Screen.Dashboard }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Vaults", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick the vault folder itself. The name must match the vault's name in Obsidian, " +
                    "because links to notes are made with it.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            app.vaults.forEach { v ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${app.notes[v.uri].orEmpty().size} notes" + if (v.uri in app.scanning) ", reading…" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { renaming = v }) { Icon(Icons.Filled.Edit, "Rename") }
                    IconButton(onClick = { scope.launch { app.rescan(v) } }) { Icon(Icons.Filled.Refresh, "Rescan") }
                    IconButton(onClick = { app.removeVault(v) }) { Icon(Icons.Filled.Delete, "Remove") }
                }
            }
            Button(onClick = onAddVault, modifier = Modifier.padding(top = 12.dp)) { Text("Add a vault") }
            Text(
                "Notes open through the Advanced URI plugin, which must be installed in each vault.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
    renaming?.let { v ->
        var text by remember(v.uri) { mutableStateOf(v.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Vault name in Obsidian") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { app.renameVault(v, text); renaming = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}
