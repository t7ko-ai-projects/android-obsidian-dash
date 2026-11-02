# Obsidian Dash

A home-screen-style dashboard of icons, one per Obsidian note you open often.

- Add a vault folder in Settings (read-only access).
- Tap **+**, type part of a note's name or path (fuzzy), tap the result: it is on the dashboard.
- Long press an icon and drag to rearrange; long press without moving to rename or remove.
- Tap an icon to open the note in Obsidian. If the note is already open in a tab, that tab is focused.

Requires the [Advanced URI](https://github.com/Vinzent03/obsidian-advanced-uri) plugin in the vault.
The vault name in Settings must match the vault's name in Obsidian.

## Building

`powershell -ExecutionPolicy Bypass -File scripts/build.ps1` (release APK, needs a signing key in
`local.properties`), or `gradlew assembleDebug`.
