# Release guide — Sorta 1.0.0

## Building

```bash
./gradlew assembleRelease          # APK  -> app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease            # AAB  -> app/build/outputs/bundle/release/app-release.aab
```

Release build is minified (R8) + resource-shrunk; rules in `app/proguard-rules.pro`.

## Signing

`app/build.gradle.kts` reads `keystore.properties` (gitignored) at the project root:

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Copy `keystore.properties.example` → `keystore.properties` and fill in. If the file
is absent the release falls back to **debug signing** so `assembleRelease` always works.

A throwaway dev keystore exists at `/home/ubuntu/sorta-release.jks`
(alias `sorta`, password `sorta123`) — **replace before shipping** (TODO(PROD)).

## TODO(PROD) checklist

| Item | Where |
|---|---|
| Real AdMob app + banner ids | `SORTA_ADMOB_APP_ID` / `SORTA_ADMOB_BANNER_ID` in `gradle.properties` (defaults are Google test ids, marked TODO(PROD) in build.gradle.kts) |
| Create `remove_ads` INAPP product in Play Console | `BuildConfig.REMOVE_ADS_PRODUCT_ID` = `"remove_ads"` must match |
| Real upload keystore + Play App Signing | `keystore.properties` |
| Privacy policy URL | Settings → Privacy policy (currently `https://example.com/sorta/privacy` placeholder) |
| All-files-access Play declaration | see below |
| Data safety form answers | see below |

### Play Console — MANAGE_EXTERNAL_STORAGE declaration

Justification text suggestion: *"Sorta is a file manager. Its core purpose —
browsing, moving, copying, renaming, compressing and tidying files anywhere on
shared storage — is not possible with scoped storage or SAF alone."*

### Data safety form

- Collects/shares **no personal data**. Files never leave the device.
- Advertising ID used by AdMob banner (declare "App activity / Advertising ID —
  Advertising or marketing" unless ads removed).
- In-app purchase processed by Google Play (declare purchase history if asked).

### Permission rationale (for reviewers)

| Permission | Why |
|---|---|
| MANAGE_EXTERNAL_STORAGE | Core file-manager functionality (see above) |
| READ/WRITE_EXTERNAL_STORAGE (maxSdk 29) | Legacy devices |
| FOREGROUND_SERVICE + _DATA_SYNC | Progress notification for long file operations |
| POST_NOTIFICATIONS | Operation progress / completion notifications |
| INTERNET, ACCESS_NETWORK_STATE | AdMob banner |
| BILLING | "Remove ads" in-app purchase |
