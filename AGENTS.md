# Velune — Playback + AA fork

## Branch workflow
- `origin` = `git@github.com:mich-de/velune-doped.git`
- `upstream` = `git@github.com:nikhilvishwakarma00/Velune.git`
- Feature branch only, never `main`. Sync: `git fetch upstream && git rebase upstream/main`

## Build
SSL cert blocks `gradlew`. Use cached Gradle directly:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.5.1-all\f1duamibpuutrzlxrpekwo5vz\gradle-9.5.1\bin\gradle.bat" assembleArm64Debug
```

ADB: `C:\Users\mdeangelis\AppData\Local\Android\Sdk\platform-tools\adb.exe`
Debug package: `com.nikhil.yt.debug` → `run-as com.nikhil.yt.debug`
Tests: `gradlew :app:testArm64DebugUnitTest`

## Modules
9 lib modules (all pure Kotlin JVM — no Android SDK):

| Module | Type | Purpose |
|--------|------|---------|
| `:app` | android-app | UI, playback, DI |
| `:innertube` | jvm | YouTube InnerTube API |
| `:kugou` | jvm | KuGou lyrics |
| `:lrclib` | jvm | LrcLib lyrics |
| `:lastfm` | jvm | Last.fm scrobbling |
| `:simpmusic` | jvm | SimpMusic lyrics |
| `:betterlyrics` | jvm | TTML lyrics |
| `:canvas` | jvm | Animated artwork |
| `:kizzy` | jvm | Discord RPC |
| `:deezer` | jvm | Deezer ARL auth + download |

**Kotlin JVM modules cannot use `android.util.Log`** — use file-based logging via `File.appendText` or `println`.

## Gradle quirks
- `ksp.incremental=false` — fixes `Storage already registered` crash
- `16kPageAlignment=true` — required for Pixel 10 (Android 15+)
- Separate `ioScope` and `scope` in `MusicService` — `collectLatest(ioScope)` not `scope`

## Lyrics
- 6 providers: SimpMusic, BetterLyrics, LrcLib, KuGou, YouTubeSubtitle, YouTubeLyrics
- `ShowLyricsKey` default = `true` (MusicService pre-fetches on song change)
- **Do not save `LYRICS_NOT_FOUND` to DB** — causes permanent "no lyrics" cache with no retry
- `LyricsScreen.kt` auto-fetches on open; `MusicService.kt` pre-fetches on play
- Lyrics only visible in full-screen `LyricsScreen` (bottom sheet), not in main player view

## PlayerMenu
- Deezer button at line 648 is **unconditional** (no `if` guard)
- **Do NOT add `userScrollEnabled = !isPortrait`** back — that blocked scrolling in portrait mode
- Two Deezer download entry points: `PlayerMenu.kt:648` (menu) + `PlayerComponents.kt` (player top actions)

## Deezer (`:deezer` module)
- ARL-based auth (256-char hex string), 128kbps MP3 download
- File-based logging: `Deezer.setLogDir(context.cacheDir)` → `cacheDir/deezer_log.txt`
- Retrieve log: `adb exec-out run-as com.nikhil.yt.debug cat /data/data/com.nikhil.yt.debug/cache/deezer_log.txt`
- Download path: `Music/Deezer/` via MediaStore `RELATIVE_PATH` (API 29+)

## Android Auto (`feat/android-auto-ui`)
- `car/VeluneCarAppService.kt` → `VeluneSession.kt`
- `VeluneSession.kt`: `ListTemplate` (root categories) + `SearchTemplate`
- Playback via `MediaController.setMediaItems()` → resolved by `onSetMediaItems` in `MediaLibrarySessionCallback`
- `MediaController` **is** `Player` (implements the interface)
- No `Player.UNSET_TIME` — use `C.TIME_UNSET`
- `SearchTemplate.Builder` requires explicit `SearchCallback` object (no lambda)

## Key files
| File | Purpose |
|------|---------|
| `playback/MusicService.kt` | ~4800-line MediaLibraryService — ExoPlayer, crossfade, equalizer, auto-mix, together |
| `playback/MediaLibrarySessionCallback.kt` | Media3 session callback — AA root items, search, `onSetMediaItems` |
| `playback/PlayerConnection.kt` | UI↔Service bridge via StateFlow |
| `car/VeluneSession.kt` | AA custom UI screens |
| `lyrics/LyricsHelper.kt` | Provider orchestration, LRU cache, romanization |
| `deezer/Deezer.kt` | ARL auth, search, StripeDecryptor download |
| `ui/menu/PlayerMenu.kt` | Song context menu — Deezer button at line 648 |
| `ui/player/PlayerComponents.kt` | Player top actions — Deezer download icon |
