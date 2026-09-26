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

| Package | Responsibility |
|---|---|
| `security` | `KeyVault`: hardware-backed key that encrypts the database key |
| `data` | Room + SQLCipher database, conversation repository |
| `engine` | GenieX runtime lifecycle; model loading and generation |
| `ui/theme` | Colour, type and motion tokens |
| `ui/components` | Sunflower mark, buttons, haptics, shared pieces |
| `ui/*` | Screens: home, models, chat |

## Licenses

Fonts (Bricolage Grotesque, Inter, JetBrains Mono) are bundled under the SIL
Open Font License; see `licenses/`.
