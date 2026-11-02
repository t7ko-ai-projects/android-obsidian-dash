package t7helpers.obsidiandash

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private const val COLUMNS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(app: AppState, onAddVault: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Obsidian Dash") },
                actions = {
                    IconButton(onClick = { app.screen = Screen.Settings }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            if (app.vaults.isNotEmpty()) {
                FloatingActionButton(onClick = { app.screen = Screen.Search }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add a note")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                app.vaults.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Give the app access to an Obsidian vault folder to start.", textAlign = TextAlign.Center)
                    Button(onClick = onAddVault, modifier = Modifier.padding(top = 16.dp)) { Text("Add a vault") }
                }
                app.pins.isEmpty() -> Text(
                    "Nothing here yet. Tap + to find a note and put it on the dashboard.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                )
                else -> PinGrid(app)
            }
        }
    }
}

@Composable
private fun PinGrid(app: AppState) {
    var menuId by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Pin?>(null) }
    val pins = app.pins
    val density = LocalDensity.current
    val cellH = 108.dp
    val cellHPx = with(density) { cellH.toPx() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val cellWDp = maxWidth / COLUMNS
        val cellWPx = with(density) { cellWDp.toPx() }
        val rows = (pins.size + COLUMNS - 1) / COLUMNS
        fun slot(i: Int) = Offset((i % COLUMNS) * cellWPx, (i / COLUMNS) * cellHPx)

        Box(Modifier.fillMaxWidth().height(cellH * (rows + 1))) {
            pins.forEachIndexed { index, pin ->
                androidx.compose.runtime.key(pin.id) {
                    val currentIndex by rememberUpdatedState(index)
                    val dragging = dragId == pin.id
                    val target by animateIntOffsetAsState(
                        IntOffset(slot(index).x.roundToInt(), slot(index).y.roundToInt()), label = "slot",
                    )
                    val at = if (dragging) IntOffset(dragPos.x.roundToInt(), dragPos.y.roundToInt()) else target
                    Box(
                        Modifier
                            .offset { at }
                            .zIndex(if (dragging) 1f else 0f)
                            .width(cellWDp)
                            .height(cellH)
                            .alpha(if (dragging) 0.85f else 1f)
                            .pointerInput(pin.id) { detectTapGestures(onTap = { app.open(pin) }) }
                            .pointerInput(pin.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragId = pin.id
                                        dragPos = slot(currentIndex)
                                        moved = 0f
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragPos += delta
                                        moved += delta.getDistance()
                                        val col = ((dragPos.x + cellWPx / 2) / cellWPx).toInt().coerceIn(0, COLUMNS - 1)
                                        val row = ((dragPos.y + cellHPx / 2) / cellHPx).toInt().coerceAtLeast(0)
                                        val to = (row * COLUMNS + col).coerceIn(0, pins.size - 1)
                                        if (to != currentIndex) {
                                            pins.add(to, pins.removeAt(currentIndex))
                                        }
                                    },
                                    onDragEnd = {
                                        if (moved < 20f) menuId = pin.id
                                        dragId = null
                                        app.savePins()
                                    },
                                    onDragCancel = { dragId = null; app.savePins() },
                                )
                            },
                    ) {
                        PinTile(pin, dragging)
                        DropdownMenu(expanded = menuId == pin.id, onDismissRequest = { menuId = null }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuId = null; renaming = pin })
                            DropdownMenuItem(text = { Text("Remove") }, onClick = {
                                menuId = null
                                pins.remove(pin)
                                app.savePins()
                            })
                        }
                    }
                }
            }
        }
    }

    renaming?.let { pin ->
        var text by remember(pin.id) { mutableStateOf(pin.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val i = pins.indexOfFirst { it.id == pin.id }
                    if (i >= 0 && text.isNotBlank()) {
                        pins[i] = pin.copy(label = text.trim())
                        app.savePins()
                    }
                    renaming = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PinTile(pin: Pin, lifted: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .size(60.dp)
                .shadow(if (lifted) 12.dp else 0.dp, shape)
                .background(tileColor(pin.vaultUri + pin.path), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(pin.label), color = Color.White, fontSize = 22.sp)
        }
        Text(
            pin.label,
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun initials(label: String): String {
    val words = label.split(' ', '-', '_').filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.replaceFirstChar { it.uppercase() }
}

private fun tileColor(seed: String): Color {
    val hue = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    return Color.hsv(hue, 0.55f, 0.65f)
}
