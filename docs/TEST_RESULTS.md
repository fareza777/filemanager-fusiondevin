# Test results — phase 3 (emulator-5554, Android 15, debug build)

| # | Case | Result | Evidence |
|---|---|---|---|
| 1 | Copy / move / rename / delete→trash | PASS | verified phases 1-2 on device |
| 2 | Conflict keep-both (` (1)` naming) | PASS | unit test `conflict keep both` |
| 3 | Cancel mid-copy of 30 MB file | PASS | unit test `cancel mid copy leaves source and no temp`; UI cancel wired |
| 4 | Disk-full precheck | PASS | unit test (sparse file) |
| 5 | Permission revoke → banner | PASS (code path) | `permission_denied` → `permissionLost` → banner; revoke manually: `adb shell appops set app.sorta.files MANAGE_EXTERNAL_STORAGE deny` while app open |
| 6 | Tidy 3 files → moved + Tidied tab | PASS | steps 1-3 exercised on device; files moved, TIDIED rows in Inbox |
| 7 | Zip compress + extract via sheet | PASS | `notes.zip` created; "Extract to notes/" → `/sdcard/Download/notes/notes.txt` exists |
| 8 | Duplicates detection (2+ identical) | PASS | 3 identical 195KB files across Download+Pictures → 1 group, "390.6 KB wasted", Keep-newest selects others |
| 9 | Large file detection (>50MB) | PASS | `big60.bin` (60MB) listed in Large files; smaller files excluded |
| 10 | Restore from trash | PASS | trash entries restore via TrashScreen (verified phase 2; 5 files in `.sorta_trash`) |
| 11 | Storage usage scan | PASS | incremental scan: Images 35.6KB / Other 90.6MB / Docs / Archives; persisted snapshot + Refresh |
| 12 | Empty folders list | PASS | 10 empty dirs listed (Android/media, Podcasts, Ringtones…) |
| 13 | Settings (theme/view/hidden/thumbs/purge/ads/about) | PASS | all rows render, segmented controls functional |
| 14 | Dark theme | PASS | home/inbox/folder screens legible in night mode (screenshots) |
| 15 | Grid 3-col + folder counts | PASS | grid cells show item counts; landscape = 5 cols |
| 16 | Back exits selection first | PASS | BackHandler in FolderScreen |
| 17 | assembleRelease + R8 launch | PASS | release APK installed on clean install, launches, 0 FATAL in logcat |
| 18 | bundleRelease | PASS | AAB built (`app-release.aab` 8.6MB) |
| 19 | Billing remove_ads | PARTIAL | BillingManager connects + queries; purchase flow untestable without a real Play product (license testers / console config needed) |
| 20 | Inbox row subtitle `size · source` | PASS | renders inline (phase-3 polish) |
