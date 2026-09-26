# Releasing Sunflower

## Builds

| Variant | What it's for | CI artifact |
|---|---|---|
| `fullRelease` | Sideloading / GitHub releases. Can request "All files access". | `sunflower-apk` |
| `playRelease` | Google Play. No restricted permissions. | `sunflower-play-bundle` (only when an upload key is configured) |

Both are minified (R8), resource-shrunk, and exclude the unused QNN/QAIRT
runtime. `versionCode` = CI run number + 100, so every build is newer.

## One-time setup for Google Play

1. **Upload key.** On your computer (never share it):
   `keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload`
2. **Repository secrets** (GitHub → Settings → Secrets and variables → Actions):
   `SUNFLOWER_UPLOAD_KEYSTORE_BASE64` (`base64 -w0 upload.jks`),
   `SUNFLOWER_UPLOAD_KEYSTORE_PASSWORD`, `SUNFLOWER_UPLOAD_KEY_ALIAS`,
   `SUNFLOWER_UPLOAD_KEY_PASSWORD`. The next CI run produces a signed `.aab`.
3. **Play Console:** create the app, enable **Play App Signing**, upload the `.aab`
   to an internal testing track first.
4. **Check the GenieX SDK's license** (github.com/qualcomm/geniex) permits
   redistributing its native libraries in your app.

## Play Console answers

**Data safety:** No data collected. No data shared. Data is encrypted at rest.
Users can request deletion (in-app "Delete all data"). Privacy policy: host
`docs/PRIVACY.md` at a public URL.

**Foreground service (special use) declaration:**
> Sunflower runs a language model on the device to write a reply the user just
> requested. Generation can take from seconds to several minutes on phone
> hardware; the foreground service keeps that user-initiated computation alive
> while the user switches apps or the screen turns off, and stops as soon as the
> reply is finished. It shows an ongoing notification with a Stop action. No
> network or data transfer is involved (the app has no internet permission).

**Content rating:** the app generates text with models the user supplies;
answer the questionnaire for user-generated/AI-generated content accordingly.

**Target audience:** 18+ is the safe choice for an app that runs arbitrary
user-supplied models.

## Store listing draft

**Short description (80 chars):**
Private AI chat that runs any GGUF model entirely on your phone. Nothing leaves.

**Full description:**
Sunflower runs AI language models directly on your phone: on the Snapdragon NPU,
the GPU or the CPU. Bring any GGUF model from your storage and chat with it with
no account, no internet and no one else involved.

• Private by design: the app has no internet permission at all.
• Encrypted: conversations are stored with AES-256 and a hardware-protected key.
• Yours to tune: every sampling and loading setting, each explained in plain words.
• Reasoning models: thinking is shown in a quiet, foldable section.
• Speculative decoding, custom chat templates, saved system prompts.
• Optional app lock and screenshot blocking.

## Pre-release checklist

- [ ] Fresh install → import → load on NPU/GPU/CPU → chat → background reply
- [ ] Update install over the previous build keeps chats
- [ ] App lock on/off, hide content on/off, theme switching
- [ ] Delete all data restarts clean
- [ ] Play build: no "Allow file access", Copy into app works
