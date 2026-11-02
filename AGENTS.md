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
- Per vault, `Vault.advUri` picks that link or Obsidian's own `obsidian://open?vault=&file=`
  (which does not focus an already open tab). Each scan sets it from the vault's
  `.obsidian/community-plugins.json` (lists `obsidian-advanced-uri`) unless the user set the
  switch by hand (`advManual`; the "Auto" button hands it back). No `.obsidian` folder
  readable: the old value stays.
- The grid has exactly the rows that fit the screen, sharing its height, so it does not
  scroll; only pins placed lower (say after a screen size change) add rows and scroll.

Machine-specific notes are in the git-ignored `AGENTS.local.md`. Keep paths and other
details of one machine out of tracked files. Build setup follows the sibling app
`quick-alarms`; see the notes one folder up.

Code: `Model.kt` (data, JSON formats, link, fuzzy matching: all unit-tested),
`Storage.kt` (SharedPreferences, cache, vault scan), `MainActivity.kt` (state and
navigation), `Dashboard.kt` (grid and drag), `Screens.kt` (search, settings).

## Testing

- Emulator (see the notes one folder up): `scripts/emulator-ui.py` taps by text;
  `adb shell input draganddrop x1 y1 x2 y2 ms` does a long-press drag. Push a sample vault
  to `/sdcard/Documents`, pick it in the app's folder picker. Obsidian is not installed on
  the emulator, so the tap only fires the intent (check `logcat` for `ActivityTaskManager`).
  Storage is flaky for a while after boot: wait before pushing files.
- Opening notes in real Obsidian was tried by the user on the phone and works.

## Releases

`scripts/release.ps1` builds, tags `v<versionName>`, pushes and creates the GitHub release
(Obtainium reads it). Raise `versionName` and `versionCode` in `app/build.gradle.kts` first.
The README screenshots are in `docs/screenshots` (emulator, demo vault).
