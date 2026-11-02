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
        assertEquals(
            "obsidian://open?vault=My%20Vault&file=Daily%2FToday%20%26%20more",
            ObsidianLink.open("My Vault", "Daily/Today & more.md", advUri = false),
        )
    }

    @Test
    fun detectsAdvancedUriInPluginList() {
        assertTrue(ObsidianLink.listsAdvancedUri("""["dataview","obsidian-advanced-uri"]"""))
        assertTrue(!ObsidianLink.listsAdvancedUri("""["dataview"]"""))
        assertTrue(!ObsidianLink.listsAdvancedUri("not json"))
    }

    @Test
    fun pinsAndVaultsRoundTrip() {
        val pins = listOf(Pin("1", "content://x", "a/b.md", "B \"quoted\""))
        assertEquals(pins, Format.pinsFromJson(Format.pinsToJson(pins)))
        val vaults = listOf(Vault("content://x", "V"), Vault("content://y", "W", advUri = false, advManual = true))
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

    private fun pin(id: String, n: Cell = Cell(-1, -1)) = Pin(id, "v", id, id, normal = n)

    @Test
    fun layoutPlacesUnplacedPinsAndFixesClashes() {
        val placed = Layout.normalize(listOf(pin("a"), pin("b", Cell(1, 0)), pin("c", Cell(1, 0)), pin("d", Cell(4, 0))))
        assertEquals(listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0)), placed.map { it.normal })
        assertEquals(listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0)), placed.map { it.compact })
    }

    @Test
    fun movingToAFreeCellLeavesOthersAndOccupiedCellSwaps() {
        val pins = Layout.normalize(listOf(pin("a"), pin("b"), pin("c")))
        val free = Layout.move(pins, "a", false, Cell(3, 5))
        assertEquals(listOf(Cell(3, 5), Cell(1, 0), Cell(2, 0)), free.map { it.normal })
        val swap = Layout.move(pins, "a", false, Cell(2, 0))
        assertEquals(listOf(Cell(2, 0), Cell(1, 0), Cell(0, 0)), swap.map { it.normal })
        assertEquals(pins.map { it.compact }, swap.map { it.compact })
    }

    @Test
    fun oldPinsWithoutCellsStillLoad() {
        val old = """[{"id":"1","vault":"v","path":"p","label":"l"}]"""
        assertEquals(Cell(-1, -1), Format.pinsFromJson(old).single().normal)
        val pins = listOf(pin("a", Cell(2, 3)))
        assertEquals(pins, Format.pinsFromJson(Format.pinsToJson(pins)))
    }
}
