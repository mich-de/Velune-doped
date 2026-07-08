# Gemini Persistent Memory Log - Velune Android Auto Optimization

## Architectural Context
- Velune uses Android Media3 library APIs to support media browser & playback capabilities.
- The playback service is `MusicService` (`MediaLibraryService`) with callback helper `MediaLibrarySessionCallback` (`MediaLibrarySession.Callback`).
- The user is using a **7-inch vehicle touch screen** which has limited display real estate and requires safety-compliant, shallow menu trees.
- **Android SDK Path:** `C:\Users\mdeangelis\AppData\Local\Android\Sdk`
- **ADB Executable:** [adb.exe](file:///C:/Users/mdeangelis/AppData/Local/Android/Sdk/platform-tools/adb.exe)
- **Android Studio Executable:** [studio64.exe](file:///C:/Program%20Files/Android/Android%20Studio/bin/studio64.exe)

## Architectural Decisions
1. **Shortcut Root Nodes**: Rather than browsing through nested menus, we will surface "Liked Songs" and "Downloaded Songs" directly on the main root menu. This allows users to start playing music in 2 taps.
2. **Category Content Styles**: Top-level directory folders (Songs, Artists, Albums, Playlists) will use `CONTENT_STYLE_LIST_ITEM` styling hints instead of grids to fit the compact 7-inch display better. Actual folders with album/playlist art will retain `CONTENT_STYLE_GRID_ITEM`.
3. **Custom Playback Commands**: The custom player actions layout (`Like`, `Repeat`, `Shuffle`, `Start Radio`) is already implemented and properly priority-ordered, ensuring the most useful commands (like Favoriting) are prominent on the Android Auto player screen.
4. **Serialization Safety**: Made `USER_ID`, `SNG_ID` and other ID variables typed as `JsonElement` inside [GwModels.kt](file:///D:/android_player/deezer/src/main/kotlin/com/nikhil/yt/deezer/models/GwModels.kt) to handle dynamic JSON type mappings cleanly without decoding crashes.

## Session Log
- **2026-07-03**: Cloned codebase and performed static analysis of the playback architecture. Drafted the implementation plan (`IMPLEMENTATION_PLAN.MD`) and task list (`TASKS.MD`).
- **2026-07-03**: Implemented Android Auto 7-inch touch screen optimizations:
  - Modified `browsableMediaItem` helper in `MediaLibrarySessionCallback.kt` to accept `browsableHint` and `playableHint`.
  - Configured top-level root folders (Songs, Artists, Albums, Playlists) to display as List items (`CONTENT_STYLE_LIST_ITEM`) to prevent bulky, hard-to-interact grids on compact displays.
  - Added direct menu shortcuts for `Liked Songs` and `Downloaded Songs` on the root level for immediate 2-tap playback start.
  - Updated `TASKS.MD` to mark playback services tasks as complete.
- **2026-07-03**: Conducted security and privacy audit. Verified dependency declarations, network protocols, the JavaScript execution engine (Rhino), database queries (Room), and Discord Rich Presence tokens. Confirmed the codebase is **SAFE** and contains no trojans or data leak backdoors. Documented details in [security_audit_report.md](file:///C:/Users/mdeangelis/.gemini/antigravity-cli/brain/20ea1c87-5ba5-48e9-bf5f-b7532c93810a/security_audit_report.md).
- **2026-07-03**: Completely disabled Discord RPC (Kizzy integration) inside `MusicService.kt` to ensure user privacy, prevent background HTTP requests to `workers.dev`, and optimize battery usage for Android Auto.
- **2026-07-03**: Installed Android Studio via `winget` and verified paths of `adb` and `studio64` are saved in memory.
- **2026-07-06**: Implemented Android Auto Custom UI (Phase 3):
  - Added `androidx.car.app:app:1.7.0` dependency via version catalog.
  - Updated `automotive_app_desc.xml` with `template` capability requirement.
  - Declared `VeluneCarAppService` in `AndroidManifest.xml` with `androidx.car.app.CarAppService` intent filter.
  - Created `VeluneCarAppService.kt` — extends `CarAppService`, creates `VeluneSession`.
  - Created `VeluneSession.kt` — connects to `MusicService` via `MediaController`, renders:
    - `BrowseRootScreen`: `ListTemplate` with 5 categories (Liked Songs, Songs, Artists, Albums, Playlists).
    - `SearchScreen`: `SearchTemplate` with `onSearchSubmitted` callback.
  - Playback triggered via `MediaController.setMediaItems()` → resolved by existing `onSetMediaItems` in `MediaLibrarySessionCallback.kt`.
  - Built and installed APK successfully on device.
  - Fixed build issues: Gradle SSL certificate (used cached dist), JDK 21 path (set to Studio's JBR), API mismatches (`CarAppService.onCreateSession()`, `MediaController` implements `Player`, no `Player.UNSET_TIME`, `SearchCallback` as explicit object).
  - **Branch:** `feat/android-auto-ui`
  - **Next (V2):** Sub-category browsing with database-backed lists, `PlaybackTemplate` with custom controls.
- **2026-07-07**: Implemented Deezer direct downloader optimizations:
  - Fixed the Deezer `login()` silent parsing crash by modifying `LoginUser.USER_ID` and other API keys in `GwModels.kt` to load as dynamic `JsonElement` values, and adjusted `Deezer.kt` to read `userObj.userId` as a Kotlin String.
  - Added the "Download from Deezer" option in [SongMenu.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/menu/SongMenu.kt) and [YouTubeSongMenu.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/menu/YouTubeSongMenu.kt).
  - Implemented an ID3v2.3 tag writer directly inside `Deezer.kt` to fetch and embed album cover art along with basic ID3 text tags (Title, Artist, Album) inside downloaded MP3 files.
  - Created and added a dynamic @Composable `DeezerDownloadButton` shortcut directly inside `PlayerTopActions` in [PlayerComponents.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/player/PlayerComponents.kt) with real-time download visual loading spinners.
- **2026-07-08**: Implemented VU Meter asticella consolidation, Android Auto browsing, and Spotify Playlist Importer:
  - Modified [VUMeter.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/component/VUMeter.kt) to draw a single combined needle based on the maximum of both channels, resolving the double-needle user request.
  - Upgraded [VeluneSession.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/car/VeluneSession.kt) from MediaController to MediaBrowser. Added `BrowseScreen` to dynamically load and display playlist/artists/albums/songs children, enabling full multi-level browsing inside Android Auto. Integrated `SearchResultScreen` to show clean lists of search results rather than immediately playing.
  - Implemented a robust **Spotify Playlist Importer**:
    - Created [SpotifyImporter.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/utils/SpotifyImporter.kt) to extract playlist names and tracks from public Spotify URLs without API authentication. Migrated the scraper from open.spotify.com/playlist/ to open.spotify.com/embed/playlist/ to completely bypass anti-bot and Google reCAPTCHA restrictions.
    - Created [SpotifyImportDialog.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/component/SpotifyImportDialog.kt) to provide a premium input UI, loading indicators, and recursive YouTube Music mapping for imported tracks.
    - Added the choice dialog in [LibraryPlaylistsScreen.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/screens/library/LibraryPlaylistsScreen.kt) to trigger either New Playlist or Spotify Import on FAB click.
    - Added a dedicated "Spotify Import" icon button at the top header of [LibraryPlaylistsScreen.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/screens/library/LibraryPlaylistsScreen.kt) and registered the Spotify Importer preference group in the Integration settings screen ([IntegrationScreen.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/screens/settings/IntegrationScreen.kt)) to ensure the feature is highly visible.
    - Added prefilled 1-click shortcut buttons in [SpotifyImportDialog.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/component/SpotifyImportDialog.kt) for popular Spotify daily chart playlists (Top 50 Mondo, Europa, Sud America, Italia, Brasile, Spagna).
    - Created [LibrarySpotifyScreen.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/screens/library/LibrarySpotifyScreen.kt) containing a dedicated library screen section under the "Spotify" tab chip. It displays a beautiful selection of curated playlists (such as Today's Top Hits, RapCaviar, Rock Classics, and Lofi Beats) decorated with custom vector Spotify branding ([ic_spotify.xml](file:///D:/android_player/app/src/main/res/drawable/ic_spotify.xml)) for instant 1-tap importing.
    - Enabled automatic navigation to the newly imported local playlist detail screen upon successful completion of the Spotify import sequence, ensuring a smooth and intuitive user experience.
    - Integrated a `BackHandler` inside [LibrarySpotifyScreen.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/screens/library/LibrarySpotifyScreen.kt) and linked it in [LibraryScreen.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/screens/library/LibraryScreen.kt) to return the user to the default Library overview screen when pressing the system back button.
    - Added player-aware bottom padding using `LocalPlayerAwareWindowInsets.current` inside [LibrarySpotifyScreen.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/screens/library/LibrarySpotifyScreen.kt)'s `LazyVerticalGrid` to prevent the bottom of the playlist list from being cut off behind the floating player bar.
    - Removed the BackHandler from [LibrarySpotifyScreen.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/screens/library/LibrarySpotifyScreen.kt) to ensure standard Android back button behavior matches all other library tabs.
  - Optimized **Crossfade Playback & Now Playing UI**:
    - Resolved crossfade abrupt cutting bugs inside [CrossfadeAudio.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/playback/CrossfadeAudio.kt) by delaying next-track audio playback (`playWhenReady = false`) during background buffering to avoid AudioTrack conflicts, and expanded seek sync intervals/tolerances (3s interval, 800ms threshold) to prevent rapid seek storms.
    - Added direct quick-action buttons for the **Sleep Timer** (`bedtime` moon icon) and **Equalizer** (`equalizer` sliders icon) in the action bar of [PlayerComponents.kt](file:///D:/android_player/app/src/main/kotlin/com/nikhil/yt/ui/player/PlayerComponents.kt), positioning the `...` menu as the final element in the row.
