package t7helpers.obsidiandash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun linkReusesTabAndEncodes() {
        assertEquals(
            "obsidian://adv-uri?vault=My%20Vault&filepath=Daily%2FToday%20%26%20more&openmode=true",
            ObsidianLink.open("My Vault", "Daily/Today & more.md"),
        )
    }

    @Test
    fun pinsAndVaultsRoundTrip() {
        val pins = listOf(Pin("1", "content://x", "a/b.md", "B \"quoted\""))
        assertEquals(pins, Format.pinsFromJson(Format.pinsToJson(pins)))
        val vaults = listOf(Vault("content://x", "V"))
        assertEquals(vaults, Format.vaultsFromJson(Format.vaultsToJson(vaults)))
        assertTrue(Format.pinsFromJson("garbage").isEmpty())
    }

    @Test
    fun notesCacheRoundTrip() {
        val notes = listOf(Note("a/b.md", 5), Note("c.md", 7))
        assertEquals(notes, Format.notesFromText(Format.notesToText(notes)))
        assertTrue(Format.notesFromText("").isEmpty())
    }

    @Test
    fun fuzzyFindsSubsequencesAndRanksTitles() {
        val notes = listOf(
            Note("Projects/Meeting notes.md", 1),
            Note("Archive/meeting-2020/misc.md", 2),
            Note("Recipes/Pancakes.md", 3),
        )
        assertNull(Fuzzy.score("zzz", notes[0]))
        assertNotNull(Fuzzy.score("mtng nts", notes[0]))
        assertEquals("Projects/Meeting notes.md", Fuzzy.search("meeting", notes).first().path)
        assertEquals("Recipes/Pancakes.md", Fuzzy.search("pnck", notes).single().path)
        assertEquals("Recipes/Pancakes.md", Fuzzy.search("", notes).first().path)
    }
}
