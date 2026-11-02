package t7helpers.obsidiandash

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.util.UUID

/** Vaults and dashboard pins, kept as JSON in SharedPreferences. */
class Store(private val context: Context) {
    private val prefs = context.getSharedPreferences("dash", Context.MODE_PRIVATE)

    fun vaults(): List<Vault> = Format.vaultsFromJson(prefs.getString("vaults", null))
    fun saveVaults(v: List<Vault>) = prefs.edit().putString("vaults", Format.vaultsToJson(v)).apply()

    fun pins(): List<Pin> = Format.pinsFromJson(prefs.getString("pins", null))
    fun savePins(p: List<Pin>) = prefs.edit().putString("pins", Format.pinsToJson(p)).apply()

    var compact: Boolean
        get() = prefs.getBoolean("compact", false)
        set(v) = prefs.edit().putBoolean("compact", v).apply()

    fun newPinId(): String = UUID.randomUUID().toString()

    private fun cacheFile(vaultUri: String) = File(context.filesDir, "index-${vaultUri.hashCode()}.txt")

    fun cachedNotes(vaultUri: String): List<Note> =
        cacheFile(vaultUri).takeIf { it.exists() }?.let { Format.notesFromText(it.readText()) } ?: emptyList()

    fun cacheNotes(vaultUri: String, notes: List<Note>) = cacheFile(vaultUri).writeText(Format.notesToText(notes))

    fun dropCache(vaultUri: String) {
        cacheFile(vaultUri).delete()
    }
}

/** Reads a vault folder through the Storage Access Framework. */
object VaultScanner {
    /** Grants and remembers read access, and names the vault after its folder. */
    fun adopt(context: Context, tree: Uri): Vault {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        var name = rootId.substringAfterLast('/').substringAfterLast(':')
        runCatching {
            context.contentResolver.query(
                rootUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
            )?.use { if (it.moveToFirst()) name = it.getString(0) }
        }
        return Vault(tree.toString(), name.ifEmpty { "vault" })
    }

    /**
     * Whether Advanced URI is enabled in the vault: true or false when its `.obsidian` folder can
     * be read, null when there is none (so the old setting stays).
     */
    fun detectAdvancedUri(context: Context, vault: Vault): Boolean? {
        val tree = Uri.parse(vault.uri)
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        fun child(parentId: String, name: String): String? {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) if (c.getString(1) == name) return c.getString(0)
            }
            return null
        }
        val config = child(rootId, ".obsidian") ?: return null
        val file = child(config, "community-plugins.json") ?: return false
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, file)
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return null
        return ObsidianLink.listsAdvancedUri(text)
    }

    /** All `.md` notes below the vault root, skipping hidden folders such as `.obsidian`. */
    fun scan(context: Context, vault: Vault): List<Note> {
        val tree = Uri.parse(vault.uri)
        val out = ArrayList<Note>()
        walk(context, tree, DocumentsContract.getTreeDocumentId(tree), "", out)
        return out
    }

    private fun walk(context: Context, tree: Uri, parentId: String, prefix: String, out: MutableList<Note>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val folders = ArrayList<Pair<String, String>>()
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    folders += id to name
                } else if (name.endsWith(".md", ignoreCase = true)) {
                    out += Note(prefix + name, c.getLong(3))
                }
            }
        }
        for ((id, name) in folders) walk(context, tree, id, "$prefix$name/", out)
    }
}
