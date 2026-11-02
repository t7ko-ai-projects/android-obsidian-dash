package t7helpers.obsidiandash

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** A vault the user granted access to. [name] is what Obsidian calls it (normally the folder name). */
data class Vault(val uri: String, val name: String)

/** A note found in a vault. [path] is relative to the vault root and keeps the `.md` extension. */
data class Note(val path: String, val modified: Long) {
    val title: String get() = path.substringAfterLast('/').removeSuffix(".md")
}

/** A note on the dashboard. */
data class Pin(val id: String, val vaultUri: String, val path: String, val label: String)

object Format {
    fun vaultsToJson(vaults: List<Vault>): String = JSONArray().also { a ->
        vaults.forEach { a.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
    }.toString()

    fun vaultsFromJson(text: String?): List<Vault> = parse(text) { Vault(it.getString("uri"), it.getString("name")) }

    fun pinsToJson(pins: List<Pin>): String = JSONArray().also { a ->
        pins.forEach {
            a.put(JSONObject().put("id", it.id).put("vault", it.vaultUri).put("path", it.path).put("label", it.label))
        }
    }.toString()

    fun pinsFromJson(text: String?): List<Pin> =
        parse(text) { Pin(it.getString("id"), it.getString("vault"), it.getString("path"), it.getString("label")) }

    private fun <T> parse(text: String?, f: (JSONObject) -> T): List<T> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(text)
            (0 until a.length()).map { f(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** The cached note list of one vault: one `modified<TAB>path` line per note. */
    fun notesToText(notes: List<Note>): String = notes.joinToString("\n") { "${it.modified}\t${it.path}" }

    fun notesFromText(text: String): List<Note> = text.lineSequence().mapNotNull { line ->
        val tab = line.indexOf('\t')
        val time = if (tab > 0) line.substring(0, tab).toLongOrNull() else null
        if (time == null) null else Note(line.substring(tab + 1), time)
    }.toList()
}

object ObsidianLink {
    /**
     * Advanced URI link to a note. `openmode=true` opens a new tab only when the note is not
     * open yet; otherwise Obsidian focuses the tab that already shows it.
     */
    fun open(vaultName: String, notePath: String): String =
        "obsidian://adv-uri?vault=${enc(vaultName)}&filepath=${enc(notePath.removeSuffix(".md"))}&openmode=true"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** Fuzzy matching: every word of the query must occur, in order, in the note's title or path. */
object Fuzzy {
    fun score(query: String, note: Note): Int? {
        val words = query.lowercase().split(' ', '\t').filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0
        val path = note.path.lowercase()
        val title = note.title.lowercase()
        var total = 0
        for (w in words) {
            val inTitle = wordScore(w, title)?.let { it * 2 }
            val inPath = wordScore(w, path)
            val best = maxOf(inTitle ?: Int.MIN_VALUE, inPath ?: Int.MIN_VALUE)
            if (best == Int.MIN_VALUE) return null
            total += best
        }
        return total - title.length / 4
    }

    fun search(query: String, notes: List<Note>, limit: Int = 60): List<Note> {
        if (query.isBlank()) return notes.sortedByDescending { it.modified }.take(limit)
        return notes.mapNotNull { n -> score(query, n)?.let { n to it } }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun isBoundary(text: String, i: Int) = i == 0 || !text[i - 1].isLetterOrDigit()

    private fun wordScore(word: String, text: String): Int? {
        val at = text.indexOf(word)
        if (at >= 0) return 100 + (if (isBoundary(text, at)) 40 else 0) - at / 4
        var score = 0
        var pos = 0
        var prev = -2
        for (c in word) {
            val i = text.indexOf(c, pos)
            if (i < 0) return null
            score += 4
            if (i == prev + 1) score += 8
            if (isBoundary(text, i)) score += 8
            prev = i
            pos = i + 1
        }
        return score
    }
}
