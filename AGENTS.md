# Notes for AI coding agents: obsidian-dash

An Android home-screen-like dashboard of quick links to favourite Obsidian notes.

- Settings: the user grants read access to one or more vault folders (Storage Access
  Framework, `OpenDocumentTree`, persisted). The app lists the `.md` files (skipping
  hidden folders) and caches the list per vault in `filesDir`; it rescans at start.
- "+" opens a fuzzy search over the notes (every word must match, in order, in the title
  or path; the empty query shows recently changed notes). Picking a note pins it.
- The dashboard is a grid with free placement: long press then drag drops an icon on any
  cell (empty cells stay empty; dropping on an occupied cell swaps the two). A long press
  without moving opens Rename / Remove. A tap opens the note.
- Two views, 4 columns and a compact one with 5 columns and smaller icons (toggle in the
  top bar and in Settings). Each pin keeps a separate cell per view (`Pin.normal`,
  `Pin.compact`), so neither arrangement is lost when switching. Pins saved before cells
  existed get cells in list order (`Layout.normalize`).
- Opening: `obsidian://adv-uri?vault=<name>&filepath=<path>&openmode=true`. With the
  Advanced URI plugin, `openmode=true` opens a new tab only if the note is not already
  open, otherwise focuses the existing tab (`false` would reuse the current pane instead).
  The vault name is the picked folder's name, editable in Settings.

Machine-specific notes are in the git-ignored `AGENTS.local.md`. Keep paths and other
details of one machine out of tracked files. Build setup follows the sibling app
`quick-alarms`; see the notes one folder up.

Code: `Model.kt` (data, JSON formats, link, fuzzy matching: all unit-tested),
`Storage.kt` (SharedPreferences, cache, vault scan), `MainActivity.kt` (state and
navigation), `Dashboard.kt` (grid and drag), `Screens.kt` (search, settings).

## Not done yet

- Tried on the emulator (2026-11-02, small vault): adding a vault, hidden-folder skip,
  fuzzy search, pinning, drag reorder (`adb shell input draganddrop`), rename, remove,
  persistence and the tap intent all work. Obsidian is not installed there, so the
  link opening in Obsidian itself, drag feel on a touchscreen and scan speed on a large
  vault are unchecked.
- No release key, `scripts/release.ps1` or GitHub repository yet.
