package t7helpers.obsidiandash

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Screen { Dashboard, Search, Settings }

/** Everything the screens show: vaults, pins, note indexes and which screen is up. */
class AppState(private val context: Context) {
    private val store = Store(context)
    val vaults = mutableStateListOf<Vault>().apply { addAll(store.vaults()) }
    val pins = mutableStateListOf<Pin>().apply { addAll(Layout.normalize(store.pins())) }
    var compact by mutableStateOf(store.compact)
        private set
    val notes = mutableStateMapOf<String, List<Note>>()
    val scanning = mutableStateListOf<String>()
    var screen by mutableStateOf(Screen.Dashboard)

    init {
        vaults.forEach { notes[it.uri] = store.cachedNotes(it.uri) }
    }

    fun toggleCompact() = useCompact(!compact)

    fun useCompact(on: Boolean) {
        compact = on
        store.compact = on
    }

    fun movePin(id: String, target: Cell) {
        val moved = Layout.move(pins.toList(), id, compact, target)
        pins.clear()
        pins.addAll(moved)
        savePins()
    }

    fun savePins() = store.savePins(pins.toList())
    private fun saveVaults() = store.saveVaults(vaults.toList())

    fun addVault(tree: Uri) {
        val vault = VaultScanner.adopt(context, tree)
        if (vaults.none { it.uri == vault.uri }) {
            vaults += vault
            saveVaults()
        }
    }

    fun renameVault(vault: Vault, name: String) {
        val i = vaults.indexOf(vault)
        if (i >= 0 && name.isNotBlank()) {
            vaults[i] = vault.copy(name = name.trim())
            saveVaults()
        }
    }

    fun removeVault(vault: Vault) {
        vaults.remove(vault)
        pins.removeAll { it.vaultUri == vault.uri }
        notes.remove(vault.uri)
        store.dropCache(vault.uri)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(vault.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        saveVaults()
        savePins()
    }

    suspend fun rescan(vault: Vault) {
        if (vault.uri in scanning) return
        scanning += vault.uri
        try {
            val found = withContext(Dispatchers.IO) {
                runCatching { VaultScanner.scan(context, vault) }.getOrNull()
            }
            if (found != null) {
                notes[vault.uri] = found
                store.cacheNotes(vault.uri, found)
            } else {
                toast("Cannot read the vault “${vault.name}”. Add it again in Settings.")
            }
        } finally {
            scanning -= vault.uri
        }
    }

    fun pin(vault: Vault, note: Note) {
        if (pins.none { it.vaultUri == vault.uri && it.path == note.path }) {
            val fresh = Pin(store.newPinId(), vault.uri, note.path, note.title)
            pins += fresh.copy(
                normal = Layout.firstFree(Layout.NORMAL_COLUMNS, pins.map { it.normal }),
                compact = Layout.firstFree(Layout.COMPACT_COLUMNS, pins.map { it.compact }),
            )
            savePins()
        }
    }

    fun open(pin: Pin) {
        val vault = vaults.firstOrNull { it.uri == pin.vaultUri }
            ?: return toast("The vault of this note is no longer in Settings.")
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(ObsidianLink.open(vault.name, pin.path)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            toast("Obsidian is not installed.")
        }
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val app = remember { AppState(context.applicationContext) }
    val addVault = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) app.addVault(uri)
    }
    // Refresh the note lists in the background whenever the vault list changes.
    LaunchedEffect(app.vaults.map { it.uri }) { app.vaults.toList().forEach { app.rescan(it) } }
    BackHandler(app.screen != Screen.Dashboard) { app.screen = Screen.Dashboard }
    when (app.screen) {
        Screen.Dashboard -> DashboardScreen(app) { addVault.launch(null) }
        Screen.Search -> SearchScreen(app)
        Screen.Settings -> SettingsScreen(app) { addVault.launch(null) }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
