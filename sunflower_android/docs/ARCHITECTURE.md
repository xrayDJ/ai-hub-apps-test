# Architecture

A single-module Android app (`app/`), Kotlin and Jetpack Compose, minSdk 31,
targetSdk 36. Inference is the GenieX SDK 0.7.0 (`com.qualcomm.qti:geniex-android`)
running llama.cpp; storage is Room over SQLCipher.

## Packages

| Package | Responsibility |
|---|---|
| `app.sunflower` | `SunflowerApp`, `AppContainer` (manual dependency wiring), `MainActivity` (theme, app lock, hide content), `AppPreferences` (theme, lock, message size) |
| `security` | `KeyVault`: a random 256-bit database key, sealed by an AES-256-GCM Android Keystore key (StrongBox when present) |
| `data` | Room database and DAOs (`data/db`), `ConversationRepository` (chats, versions, search), `ModelLibrary` (imports, file access, per-model settings), `PromptLibrary`, search excerpts (`Snippet`) |
| `engine` | `GenieXRuntime` (SDK start-up), `InferenceEngine` (load, generate, stop), `GenerationService` (foreground service for background replies), `GgufReader`, `ModelSettings`, `ChatLogic` (reasoning formats, context fitting), `DeviceProfile`, `CrashReports` |
| `ui/theme` | Colours, type, motion (springs), surfaces (`SmoothCornerShape`, `lift`, shimmer) |
| `ui/components` | The Sunflower mark, buttons, chips, ⓘ hints, headers, rename field, haptics |
| `ui/markdown` | A small Markdown parser and renderer, and `TextFade` for streamed words |
| `ui/home`, `ui/chat`, `ui/models`, `ui/settings` | Screens and their view models |
| `ui/navigation` | Routes and the shared screen transition |

## Loading a model

1. `ModelLibrary.open` hands the native loader a readable path: the private
   copy if there is one, else the file's real path (full build with "All files
   access"), else `/proc/self/fd/N` from the picker's descriptor.
2. `InferenceEngine.load` resolves the model's settings for the chosen backend
   (`ModelSettings.resolve`), checks memory, writes a crash marker, and asks
   GenieX for a `LlmWrapper` with `runtime_id = "llama_cpp"` and the backend's
   `compute_unit`. Auto tries backends in `DeviceProfile.autoOrder`, starting
   with the one that worked last.
3. If the process dies while loading, the marker names the backend on the next
   start; Auto skips it and a crash report is shown on the model.

## Producing a reply

1. `ChatViewModel.send` saves the user message (a new *turn*) and calls
   `InferenceEngine.send` with the visible history.
2. The engine trims history to the context window (`fitToContext`), applies the
   chat template through GenieX (`applyChatTemplate`, with `enableThinking` from
   the model's settings), and streams tokens with `generateStreamFlow`.
3. Streamed text is split into reasoning and answer (`splitThinking`), published
   as a `Generation` for the UI, and checkpointed to the database every few
   seconds so a crash or a kill keeps what was written.
4. A foreground service keeps generation alive when the app is in the background.

## Data

Room database version 9, encrypted with SQLCipher 4.17 (`secure_delete` on).
Migrations 1 → 9 are written by hand and add columns only.

| Table | Holds |
|---|---|
| `conversations` | title, system prompt snapshot, pinned, the model it last used |
| `messages` | role, content, reasoning, timing and token stats, `turnId` / `variant` / `active` for versions of the last exchange |
| `system_prompts` | the saved prompt library and the default |
| `models` | imported files, their header details, per-model settings (JSON), backend history |

Versions: regenerating or editing marks the current exchange inactive and adds
a new variant with the same `turnId`; only active rows are shown or sent to the
model. Sending the next message deletes inactive rows.

## UI

Compose with Material 3 as a base, restyled: warm dark and light schemes,
`SmoothCornerShape` (continuous-curvature corners), `lift` (surfaces lit from
above), and springs from `Motion`. Screens move with one shared transition (fade
and a short rise). See `DESIGN.md`.

## Build and CI

- Flavours: `full` (can request "All files access") and `play` (cannot).
- QNN/QAIRT libraries from the SDK are excluded from the APK; only llama.cpp,
  ggml-hexagon, OpenCL and CPU backends ship.
- `.github/workflows/sunflower-android.yml`: unit tests, lint, the full release
  APK (artifact `sunflower-apk`) and, when an upload key is configured, the Play
  bundle. `versionCode` is the CI run number plus 100.
- Unit tests live in `app/src/test`; GGUF files for tests are built in memory
  (`GgufFixtures`).
