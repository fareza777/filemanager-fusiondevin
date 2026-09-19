# Sorta — Architecture & Implementation Spec

Sorta is an offline Android file manager whose differentiator is the **Inbox**: new files
from Downloads (and user-chosen folders) surface in one place, get previewed, and are
"tidied" (renamed + moved to a favorite folder) individually or in bulk.

## Stack (fixed — do not substitute)

| Concern | Choice |
|---|---|
| Language / UI | Kotlin 2.0.21, Jetpack Compose, Material 3, `org.jetbrains.kotlin.plugin.compose` |
| Build | AGP 8.7.x, Gradle 8.9, compileSdk 35, minSdk 26, targetSdk 35, JDK 17 |
| Navigation | `androidx.navigation:navigation-compose` (single Activity) |
| Persistence | Room 2.6.x (KSP), DataStore Preferences |
| Images/thumbnails | Coil 2.7 (`coil-compose`, `coil-video`), plus `ThumbnailUtils`/`PdfRenderer` fetchers |
| DI | Manual: `SortaApp : Application` holds an `AppContainer` (no Hilt/Koin) |
| Async | Coroutines + Flow; long ops run in a `ForegroundService` (`FileOperationService`) |
| Ads | `play-services-ads` 23.x, test IDs only; Billing `billing-ktx` 7.x |
| File access | `java.io.File` + `MANAGE_EXTERNAL_STORAGE` (API 30+), `READ/WRITE_EXTERNAL_STORAGE` (<30); `StorageManager` for volumes; `MediaScannerConnection` after writes |

App id: `app.sorta.files`. App name: **Sorta**. Tagline: "Sort it out." UI strings in
English (default) and Indonesian (`values-in`). Every string via `strings.xml`.

Never promise access to `Android/data` / `Android/obb` of other apps: show these dirs as
"Restricted by Android" with an explanatory row, not as an error.

## Module layout (single `:app` module, packages)

```
app.sorta.files
├─ SortaApp.kt                 Application, AppContainer
├─ MainActivity.kt             single activity, edge-to-edge, theme
├─ core/
│  ├─ fs/        FileItem, StorageVolumes, FileSystem (list/stat/mime), MimeUtil, FileTypeCategory
│  ├─ ops/       FileOperation (sealed), OperationEngine, ConflictPolicy, OperationProgress, FileOperationService
│  ├─ scan/      RecentFilesScanner, InboxScanner, LargeFilesScanner, DuplicateScanner (hash), StorageUsage
│  ├─ zip/       ZipCompressor, ZipExtractor
│  ├─ rename/    BatchRenamePattern (tokens: {name} {ext} {n} {date}; start/step/padding), preview()
│  ├─ rules/     SortRule (match by ext/name-contains/name-regex → target folder), RuleEngine.preview()
│  └─ trash/     TrashManager (app-private trash dir on same volume: <volume>/.sorta_trash/<uuid>/ + Room record)
├─ data/
│  ├─ db/        SortaDatabase, entities+DAOs: FavoriteFolder, InboxSource, InboxState, TrashEntry, HistoryEntry, SortRule, RecentLocation, FolderPref(sort/view/scroll)
│  └─ prefs/     UserPrefs (DataStore): theme, defaultView, showHidden, adsRemoved, lastTab, onboardingDone
├─ ui/
│  ├─ theme/     Color.kt (brand palette), Theme.kt (dynamic color off → fixed premium palette), Type.kt, Shapes
│  ├─ navigation/ SortaNavHost, Destinations (Home, Inbox, Browse, Storage + detail screens)
│  ├─ components/ FileRow, FileGridCell, Thumbnail, SelectionTopBar, BottomActionBar, EmptyState, ProgressSheet, ConflictDialog, RenameDialog, NewFolderDialog, FolderPicker, Breadcrumbs, SortMenu, AdBanner
│  ├─ home/      HomeScreen + HomeViewModel
│  ├─ inbox/     InboxScreen, TidyFlowSheet (preview→rename→pick favorite→confirm), InboxSourcesScreen
│  ├─ browse/    BrowseScreen (folder), CategoryScreen (images/videos/audio/docs/apks/archives), BrowseViewModel
│  ├─ storage/   StorageScreen (usage rings, large files, duplicates, trash), TrashScreen, DuplicatesScreen, LargeFilesScreen
│  ├─ search/    SearchScreen + filters (type, date range, size range, location)
│  ├─ preview/   ImagePreview (zoom), PdfPreview (PdfRenderer pages lazily), TextPreview
│  ├─ rename/    BatchRenameScreen (live preview list)
│  ├─ rules/     RulesScreen, RuleEditor, RulePreviewScreen
│  ├─ history/   HistoryScreen
│  ├─ basket/    SelectionBasket (cross-folder cart), BasketSheet
│  └─ settings/  SettingsScreen (theme, view, hidden files, remove ads purchase, about, licenses)
└─ ads/          AdsManager (AdMob init, banner ids), BillingManager (one-time "remove_ads" product)
```

## Data safety rules (non-negotiable)

1. **Move** = copy to temp name in destination (`.sorta_tmp_<name>`) → fsync → verify size (and
   for files ≤ 64 MB, SHA-256 of both) → atomic rename to final name → delete source. Same-volume
   moves may use `File.renameTo` first; on failure fall back to copy+verify+delete.
2. **Delete** through the app always goes to **Trash** (unless the user explicitly picks "Delete
   permanently" from Trash screen or the source is already inside trash). Trash keeps original
   path + deletion time; auto-purge after 30 days (WorkManager not required — purge on app start).
3. **Conflicts**: never overwrite silently. `ConflictPolicy` = `ASK | SKIP | KEEP_BOTH | OVERWRITE`;
   `ASK` suspends the engine and the UI shows `ConflictDialog` with "apply to all" checkbox. `KEEP_BOTH`
   uses ` (1)`, ` (2)` … suffix. `OVERWRITE` requires the confirmation dialog.
4. **Cancellation**: operations are `Job`s; cancel deletes the partial temp file, never the source.
5. **Disk full**: check `usableSpace` before starting; on `IOException` mid-copy → remove temp,
   mark item FAILED with reason, continue with next item, summarize at end.
6. **Permission revoked** mid-op: catch `SecurityException`, fail the item, surface a banner with
   a "Grant access" button that reopens the permission screen.
7. Every operation writes a `HistoryEntry` per batch with per-item status (SUCCESS/FAILED/SKIPPED)
   and failure reason; History screen shows these clearly.

## Inbox semantics

- `InboxSource` table: default rows = Downloads (`Environment.DIRECTORY_DOWNLOADS`) and, if they
  exist, `Pictures/Screenshots`, `DCIM/Camera` is NOT default (too noisy) — user can add any folder.
- Inbox lists files (non-recursive, or recursive with depth ≤ 2 toggle) in sources whose
  `InboxState` is missing or `UNTIDIED`, sorted newest first. Tabs: **Baru** (untidied) / **Sudah rapi** (tidied).
- "Rapikan" (Tidy) flow, for 1..N selected files: bottom sheet stepper → (1) preview/thumbnail
  strip, (2) optional rename (single) or batch-rename pattern (multi), (3) pick target from
  Favorites list or "Browse…" folder picker, (4) confirm summary → runs a MOVE via the engine.
  On success the file's `InboxState` becomes `TIDIED` (keyed by new path) and it disappears from Baru.
- "Tandai sudah rapi" (Mark tidied) sets `InboxState = TIDIED` for the current path without moving.
- Scanning is incremental: `InboxScanner` emits per-source chunks via Flow; UI renders as they arrive.

## Four tabs

1. **Beranda (Home)**: search bar (→ SearchScreen), storage mini-summary card, **AdBanner** (only here
   and Storage), Recent files (MediaStore `DATE_MODIFIED` last 7 days + fs scan of Downloads,
   grouped by day, horizontally scrollable thumbnails + "See all" → full Recent list with type filter),
   Favorite folders grid (add/remove/reorder), Last locations (last 5 browsed folders).
2. **Inbox**: as above. Top-right: manage sources, toggle recursive.
3. **Jelajah (Browse)**: root screen lists Internal storage, SD card / USB (from `StorageManager.storageVolumes`,
   only mounted), then categories (Images, Videos, Audio, Documents, APKs, Archives, Downloads).
   Folder screen: breadcrumbs, sort (name/date/size/type, asc/desc), list/grid toggle, hidden files
   toggle, FAB (new folder / new file), long-press multi-select with range select (long-press A, then
   long-press B selects A..B), bottom action bar (Copy, Move, Rename, Delete, Share, Compress,
   Add to basket, More: Open with, Details, Mark tidied, Add to favorites).
   Per-folder sort/view/scroll offset persisted in `FolderPref`.
4. **Penyimpanan (Storage)**: per-volume usage bar + breakdown by category (computed incrementally,
   cached), **AdBanner**, cards: Large files (>50 MB, top 100), Duplicates (size-group then
   SHA-256 chunked hash; identical only), Trash (count + size, restore/purge), Clean suggestions.

Paste is via a persistent **clipboard/basket** (`SelectionBasket`): items + intended op
(copy/move). A "Paste here" bar appears at the bottom of any folder when basket non-empty.

## Search

`SearchScreen`: query on filename (case-insensitive, contains), filters: type category, date modified
(today/7d/30d/custom), size (<1MB, 1-10, 10-100, >100MB), location (all / current volume / a folder).
Runs a coroutine `File.walk` bounded by location, emits results in batches of 50, cancellable
on query change (debounce 300 ms). Also supports typing an extension like `.pdf`.

## Preview

Tap opens: image → `ImagePreview` (pager over sibling images, pinch zoom); pdf → `PdfPreview`;
text/code/json/md/log/csv (≤ 2 MB) → `TextPreview` (monospace, share); everything else →
`ACTION_VIEW` via `FileProvider` with chooser fallback "Open with". Video/audio → `ACTION_VIEW`.

## Ads & billing

- `AdsManager.init()` in `SortaApp`. Banner unit id from `BuildConfig.ADMOB_BANNER_ID`, App id in
  manifest from `manifestPlaceholders`. Debug and release both default to Google **test** ids
  (`ca-app-pub-3940256099942544~3347511713`, `ca-app-pub-3940256099942544/6300978111`).
  Production ids belong in `gradle.properties` keys `SORTA_ADMOB_APP_ID`, `SORTA_ADMOB_BANNER_ID`
  — marked `TODO(PROD)` in `docs/RELEASE.md`.
- `BillingManager`: product id `remove_ads` (INAPP, one-time). On purchase/acknowledge →
  `UserPrefs.adsRemoved = true`; restore on startup via `queryPurchasesAsync`. `AdBanner` composable
  renders nothing when `adsRemoved`.

## Theming / UX

Fixed premium palette (not dynamic color): primary deep indigo `#4F5BD5`, secondary teal `#2BB3A3`,
tertiary amber `#F5A524`; dark surfaces `#0F1117/#161922`; light `#F7F8FC/#FFFFFF`. Rounded 16dp
cards, 12dp rows. Bottom `NavigationBar` with 4 items; content areas reachable one-handed (actions
in bottom bars/sheets, not top). Animations: `AnimatedVisibility` for selection bars, shared
`animateContentSize`, no heavy transitions. Remember: scroll positions (`LazyListState` saved in
ViewModel per folder), selection survives rotation (ViewModel), preferences via DataStore.

Thumbnails: Coil with `crossfade`, memory+disk cache, request size = cell size; images/videos via
Coil fetchers, PDFs via custom `PdfThumbnailFetcher` (first page), APKs via `PackageManager` icon,
other types → Material icon per `FileTypeCategory` on tinted rounded background.

## Permissions (manifest)

`MANAGE_EXTERNAL_STORAGE` (core file-manager use case — Play declaration form required),
`READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE` maxSdk 32/29, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` (op progress), `INTERNET` + `ACCESS_NETWORK_STATE`
(ads/billing only), `com.android.vending.BILLING`. Onboarding screen explains and requests access;
app is usable read-only on nothing granted (shows CTA).

## Testing expectations

Unit tests (JUnit4) for: `BatchRenamePattern`, `ConflictPolicy` name generation, `RuleEngine.preview`,
`OperationEngine` copy/move/verify/cancel/disk-full-simulation using temp dirs, `DuplicateScanner`.
Instrumented/emulator manual test script in `docs/TEST_PLAN.md` executed via adb with real files.
