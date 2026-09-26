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
./gradlew assembleDebug
adb install -t app/build/outputs/apk/debug/app-debug.apk
```

CI builds the debug APK on every push that touches this folder; see
`.github/workflows/sunflower-android.yml`.

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
