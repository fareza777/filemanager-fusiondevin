# Sorta — manual test plan (adb-driven)

Prereqs: `app-debug.apk` installed on emulator-5554,
`adb shell appops set app.sorta.files MANAGE_EXTERNAL_STORAGE allow`,
sample files in `/sdcard/Download`.

## File operations
- **Copy**: select file(s) → Copy → navigate elsewhere → Paste here. Verify both copies exist.
- **Move**: select → Move → paste in target. Source gone, dest present.
- **Rename**: single select → Rename → new name shown.
- **Delete → trash**: select → Delete → confirm → snackbar "Moved to trash" with Undo; verify file
  under `/sdcard/.sorta_trash/*/`. Undo restores.
- **Restore/purge**: Storage → Trash → select → Restore (file back at original path);
  Delete permanently (gone); Empty trash.

## Conflicts
- Copy a file into a folder that has same-named file → dialog: Skip / Keep both / Overwrite.
  - Keep both → `name (1).ext` created, original intact.
  - Overwrite (destructive red) → dest replaced, warning shown.
  - "Apply to all" checkbox suppresses further prompts.

## Cancel
- Copy a ~50MB file (`adb push big.bin /sdcard/Download/`) → cancel in progress sheet →
  no `.sorta_tmp_*` remains in dest, source intact. (Unit test covers this too.)

## Disk full
- Covered by unit test `disk full precheck fails all items` (sparse file > free space).
  On emulator, `adb shell dd` to fill a small test dir is impractical — rely on test.

## Permission revoke
- With app open: `adb shell appops set app.sorta.files MANAGE_EXTERNAL_STORAGE deny`,
  run a copy → items fail `permission_denied`, top bar shows
  "Storage access lost — Grant access" → reopens settings.
  Re-grant: `adb shell appops set app.sorta.files MANAGE_EXTERNAL_STORAGE allow`.

## Inbox
- Inbox tab shows New files grouped by day with source chips; badge count on tab.
- Select → Tidy → step 1 preview strip (+ rename/pattern preview) → step 2 favorites /
  recent / Browse → step 3 confirm → files moved, marked TIDIED, snackbar.
- Mark tidied → moves to Tidied tab; Unmark returns it.
- Manage sources: toggle enable/recursive, add folder via picker, remove.

## ZIP
- Select items → More → Compress → name → zip created in current folder (progress sheet).
- Tap .zip → sheet: Extract here / Extract to <name>/ / Extract to… / Open with.
- Extracted tree verified; zip-slip entries rejected (unit test).

## Batch rename
- Multi-select → Rename → pattern `{name}_{n}{ext}` with start/step/padding, or
  find/replace → live preview, conflicts flagged red, Apply disabled until resolved.

## Rules
- Inbox ⋮ → Sort rules → add rule (EXT pdf → folder). ⋮ → Run rules → preview list
  (file → target, uncheck to skip) → Move N files → moved + marked TIDIED.

## Screens
- No crashes: `adb logcat -d | grep -E "FATAL|AndroidRuntime"` after each flow.
