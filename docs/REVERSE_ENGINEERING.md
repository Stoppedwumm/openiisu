# iiSU Alpha 7.4 – reverse-engineering notes

Source: `iiSU-Alpha-7.4.apk` (release tag 0.0.7.4, 128 MB), sha256 `0e9008b2b66c48f98edb7cfa42b3dbde2185ea439179ac673ed2e360769de6e8`.
Tooling: unzip, jadx 1.5.0, androguard. Notes are for building an interoperable open-source launcher; no APK assets (audio, art, native libs) are redistributed here.

## Identity
- Package `com.iisulauncher`, versionName `0.1.6.1`, minSdk 30, targetSdk 34.
- Kotlin + Jetpack Compose UI, Hilt (DI), Room (SQLite), WorkManager, Coil, Lottie, Media3, OkHttp.
- Code is R8-obfuscated: only 56 classes keep names under `com.iisulauncher`; ~8.7k are in `defpackage`. `launcher/MainActivity` alone is ~15k decompiled lines.

## Components (manifest)
| Component | Role |
|---|---|
| `launcher.MainActivity` | Single-activity Compose UI; also handles the HOME intent |
| `launcher.SecondaryHomeActivity` | SECONDARY_HOME for dual-screen devices (AYN Thor, etc.) |
| `launcher.dualdisplay.LauncherKeepAliveService` | Foreground service to keep the launcher alive |
| `launcher.RootlessExternalBackstopActivity` | Helper activity used while an external emulator runs |
| `launcher.StartupSafeModeActivity`, `diagnostics.EarlyCrashCaptureProvider` | Crash capture / safe mode on repeated startup failure |
| `notifications.SystemNotificationListenerService` | Reads system notifications for the launcher |
| `discord.*` | Discord Social SDK integration (`libdiscord_jni.so`, `libdiscord_partner_sdk.so`) |
| `backup.AutoBackupWorker` | Periodic settings/library backup |
| `retroachievements.*` | RetroAchievements hashing (`librcheevos_jni.so`, rcheevos `rc_hash_*`) |
| `thegamesdb.*`, `scrapers.*` | Metadata/art scraping + Room cache |
| `launcher.playtime.*` | Playtime tracking + Room DB |

Notable permissions: MANAGE_EXTERNAL_STORAGE, REQUEST_INSTALL_PACKAGES, READ_CALENDAR, RECORD_AUDIO, BLUETOOTH(_CONNECT), RECEIVE_BOOT_COMPLETED, foreground-service types (special use, microphone, media playback), notification listener.

## Native libraries (all four ABIs)
`libdiscord_jni`, `libdiscord_partner_sdk` (Discord), `librcheevos_jni` (JNI: `RetroAchievementsHash.nativeComputeHash/nativeComputePathHash`, wraps rcheevos hash + libchdr for CHD), `libdatastore_shared_counter`, `libandroidx.graphics.path`, `libc++_shared`, `libz`.

## Core design: emulator launching
The launcher does not emulate. It scans ROM folders per console and fires Android intents at installed emulators.
- Per-console config (`docs/reference/emuladores_default.jsonc`): `shortName`, `longName`, `releaseYear/Date`, `manufacturer`, `retroAchievementsId`, `romExtensions` (case-sensitive), and an `emulators[]` list.
- Emulator entry: `id`, `name`, `routeType` (`path` = filesystem path, `uri` = SAF URI), `packages[]`, `commands[]`.
- Command templates substitute `%ROM%`, `%ROM_PATH%`, `%ROM_URI%`, `%ROM_DIR%`, `%ROM_NAME%`, `%PACKAGE%` (expanded once per package).
- `supported_emulators_default.json` (152 entries): `{name, packages[]}` used to detect installed emulators (e.g. RetroArch = `com.retroarch`, `com.retroarch.aarch64`, `com.retroarch.ra32`).
- `default_emulator_options.json`: per-console default `emulator`, `commandLabel`, `routeType`, `launchPreference` (`Ask` / `Internal`).
- Launch is a `MAIN`/`LAUNCHER` intent with an explicit component, or an intent built from the command template; RetroArch is special-cased (`com.retroarch.browser.retroactivity.RetroActivityFuture`).
- Other assets: `psvita_title_ids.json` (title-id -> name), `iiSU_StarterPack.zip` (396 platform images under `platforms/`), `borders/*.png`, `shaders/`, UI sounds (`*.wav`) and music (`*.ogg`).

## Network endpoints
| Endpoint | Use |
|---|---|
| `api.thegamesdb.net/v1/...` (Games/ByGameID, v1.1 ByGameName, Games/Images, Genres, Developers, Publishers) | Metadata + boxart |
| `api.screenscraper.fr/api2/jeuInfos.php`, `jeuRecherche.php` | ScreenScraper (user credentials) |
| `www.steamgriddb.com/api/v2/{grids,heroes,logos,icons}/game/` | Artwork (user API key) |
| `retroachievements.org`, `media.retroachievements.org/Badge/` | Achievements |
| `icons.duckduckgo.com/ip3/` | Favicons |
| `api.github.com/repos/iisu-network/iiSU/releases/latest` | Update check |
| `raw.githubusercontent.com/iisu-network/iiSU/main/updates/catalog.json` | Update catalog |
| `supporter-updates.ishade55.workers.dev` | Supporter-build update service (Cloudflare Worker) |
| `oauth2.googleapis.com/token`, calendar.readonly/events scopes | Google Calendar widget |
| Discord Social SDK | Friends, DMs, guild channels, presence, lobbies |

## Data model (visible bits)
- `PlaytimeEntry`: `entryId, title, launchedPkg, type (PlaytimeEntryType), totalPlaytimeMs, sessionCount, lastPlayedAtMs, mostRecentSession`; `PlaytimeSession` rows in `PlaytimeDatabase`.
- `ScraperMetadataDatabase`: cache for TheGamesDB/ScreenScraper results.
- Discord models: User, Friend, FriendActivity, Guild, Channel, Message, Lobby, LobbyMember.

## Next steps for openiisu
1. Reimplement the ROM scanner + launch-command templating using the reference configs (cleanest, fully documented part).
2. Scraper layer against TheGamesDB / ScreenScraper / SteamGridDB with user-supplied keys.
3. Playtime tracker (foreground-app session timing) with Room.
4. RetroAchievements via upstream rcheevos (open source) rather than the shipped binary.
5. Remaining UI/theme behaviour requires deeper reading of the obfuscated `defpackage` classes; `MainActivity` is the entry point.

## Project layout (skeleton)
- `core/` – pure Kotlin/JVM: config models + JSONC loader, `am start`-style command parser (`CommandTemplate`) producing an `IntentSpec`. Tested with `gradle :core:test`.
- `app/` – Android + Compose shell (HOME-capable `MainActivity`, `EmulatorLauncher` mapping `IntentSpec` -> `Intent`). Included only when `ANDROID_HOME` or `local.properties` exists; **not yet built** (no Android SDK in the dev container).
