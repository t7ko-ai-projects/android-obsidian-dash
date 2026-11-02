package t7helpers.obsidiandash

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(app: AppState, onAddVault: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Obsidian Dash") },
                actions = {
                    IconButton(onClick = { app.toggleCompact() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = if (app.compact) "Normal view" else "Compact view",
                            tint = if (app.compact) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
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
    val compact = app.compact
    val columns = Layout.columns(compact)
    val density = LocalDensity.current
    val cellH = if (compact) 92.dp else 108.dp
    val cellHPx = with(density) { cellH.toPx() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableFloatStateOf(0f) }

    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewH = with(density) { maxHeight.toPx() }
        val cellWDp = maxWidth / columns
        val cellWPx = with(density) { cellWDp.toPx() }
        // Rows fill the screen, keep one empty row below the last icon, and grow while an icon
        // is dragged further down.
        val dragRow = if (dragId != null) (dragPos.y / cellHPx).roundToInt() + 1 else 0
        val rows = maxOf((pins.maxOfOrNull { it.cell(compact).row } ?: 0) + 2, (viewH / cellHPx).toInt() + 1, dragRow + 1)
        fun origin(c: Cell) = Offset(c.col * cellWPx, c.row * cellHPx)
        fun cellAt(pos: Offset) = Cell(
            (pos.x / cellWPx).roundToInt().coerceIn(0, columns - 1),
            (pos.y / cellHPx).roundToInt().coerceIn(0, rows - 1),
        )

        // Scroll the grid while an icon is held near the top or bottom edge.
        LaunchedEffect(dragId) {
            while (dragId != null) {
                val y = dragPos.y + cellHPx / 2 - scroll.value
                val step = when {
                    y > viewH - 140f -> 16f
                    y < 140f && scroll.value > 0 -> -16f
                    else -> 0f
                }
                if (step != 0f) dragPos += Offset(0f, scroll.scrollBy(step))
                delay(16)
            }
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().height(cellH * rows)) {
            if (dragId != null) {
                val t = origin(cellAt(dragPos))
                Box(
                    Modifier
                        .offset { IntOffset(t.x.roundToInt(), t.y.roundToInt()) }
                        .width(cellWDp).height(cellH).padding(3.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                )
            }
            pins.forEach { pin ->
                androidx.compose.runtime.key(pin.id) {
                    val currentPin by rememberUpdatedState(pin)
                    val dragging = dragId == pin.id
                    val slot = origin(pin.cell(compact))
                    val target by animateIntOffsetAsState(
                        IntOffset(slot.x.roundToInt(), slot.y.roundToInt()), label = "slot",
                    )
                    val at = if (dragging) IntOffset(dragPos.x.roundToInt(), dragPos.y.roundToInt()) else target
                    Box(
                        Modifier
                            .offset { at }
                            .zIndex(if (dragging) 1f else 0f)
                            .width(cellWDp)
                            .height(cellH)
                            .alpha(if (dragging) 0.85f else 1f)
                            .pointerInput(pin.id) { detectTapGestures(onTap = { app.open(currentPin) }) }
                            .pointerInput(pin.id, compact) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragId = pin.id
                                        dragPos = origin(currentPin.cell(compact))
                                        moved = 0f
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragPos += delta
                                        moved += delta.getDistance()
                                    },
                                    onDragEnd = {
                                        if (moved < 20f) menuId = pin.id else app.movePin(pin.id, cellAt(dragPos))
                                        dragId = null
                                    },
                                    onDragCancel = { dragId = null },
                                )
                            },
                    ) {
                        PinTile(pin, dragging, compact)
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
                        pins[i] = pins[i].copy(label = text.trim())
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
private fun PinTile(pin: Pin, lifted: Boolean, compact: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = if (compact) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(if (compact) 13.dp else 16.dp)
        Box(
            Modifier
                .size(if (compact) 48.dp else 60.dp)
                .shadow(if (lifted) 12.dp else 0.dp, shape)
                .background(tileColor(pin.vaultUri + pin.path), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(pin.label), color = Color.White, fontSize = if (compact) 18.sp else 22.sp)
        }
        Text(
            pin.label,
            Modifier.padding(top = 4.dp),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
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
