# Sunflower

A private, offline chat app for Android that runs any GGUF language model
on-device, built on the [GenieX SDK](https://github.com/qualcomm/geniex)
(`com.qualcomm.qti:geniex-android`).

- **Bring your own model**: import any `.gguf` file from local storage.
- **Runs on the phone**: llama.cpp on the Snapdragon NPU (Hexagon), GPU (OpenCL) or CPU.
- **Nothing leaves the device**: the app has no network permission at all.
- **Encrypted history**: conversations are stored in a SQLCipher database
  (AES-256, HMAC-SHA512 page authentication). Its 256-bit key is sealed by an
  AES-256-GCM key held in the Android Keystore, in StrongBox where available.

## Documentation

| Document | Contents |
|---|---|
| [`docs/OVERVIEW.md`](docs/OVERVIEW.md) | Who Sunflower is for, what it is and isn't, its principles |
| [`docs/STATUS.md`](docs/STATUS.md) | What is built, what has been verified, known gaps |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Code layout, how models load and replies are produced, data |
| [`docs/DESIGN.md`](docs/DESIGN.md) | Colour, surfaces, motion and wording |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Planned work, each with a plan |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Builds, signing, Play Console, checklist |
| [`docs/PRIVACY.md`](docs/PRIVACY.md) | Privacy policy |

## Build

Requires JDK 17 and the Android SDK (platform 36).

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

CI runs the unit tests and lint, then builds the minified release APK on every
push that touches this folder (`.github/workflows/sunflower-android.yml`).
Builds are signed with a committed development key so updates install over each
other; see `signing/README.md` for adding a private upload key for store releases.

### Size

The GenieX SDK ships ~208 MB of native code, ~180 MB of which is Qualcomm's
QNN/QAIRT runtime for AI Hub's precompiled models. Sunflower only runs GGUF
through llama.cpp (whose NPU path is `ggml-hexagon`), so those libraries are
excluded in `app/build.gradle.kts`.

## Layout

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full picture.

| Package | Responsibility |
|---|---|
| `security` | `KeyVault`: hardware-backed key that encrypts the database key |
| `data` | Room + SQLCipher database, conversations, models, prompts, search |
| `engine` | GenieX runtime, model loading and generation, GGUF reading, settings, device profile |
| `ui/theme` | Colour, type, motion and surface tokens |
| `ui/components` | Sunflower mark, buttons, hints, shared pieces |
| `ui/*` | Screens: home, chat, models, settings, Markdown rendering |

## Licenses

Sunflower bundles open-source code whose licenses ask for their notices to
travel with the app. The full texts are in `licenses/` and in the app under
Settings → About → Open-source licenses:

| Component | License |
|---|---|
| GenieX SDK (native libraries) | BSD 3-Clause, © 2024-2026 Qualcomm Technologies, Inc. |
| llama.cpp / ggml | MIT, © 2023-2026 The ggml authors |
| LLVM OpenMP runtime (`libomp.so`) | Apache 2.0 with LLVM Exceptions |
| SQLCipher for Android | BSD 3-Clause, © 2008-2023 Zetetic LLC (includes public-domain SQLite) |
| AndroidX, Compose, Room, Kotlin libraries | Apache 2.0 |
| Bricolage Grotesque, Inter, JetBrains Mono | SIL Open Font License 1.1 |

GenieX is also "subject to Qualcomm's Terms of Use"
(qualcomm.com/site/terms-of-use). Its BSD license forbids using Qualcomm's name
to endorse or promote Sunflower, so describe it factually ("runs on the GenieX
SDK"), not as endorsed by Qualcomm. The Qualcomm AI Engine Direct (QNN/QAIRT)
libraries are excluded from the APK and are not redistributed.
