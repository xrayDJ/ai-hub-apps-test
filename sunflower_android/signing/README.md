# Signing

`sunflower-dev.jks` is a **non-secret development key**, committed on purpose so
every CI build carries the same signature and installs over the previous one
(Android refuses updates signed with a different key, and uninstalling would
delete the app's encrypted data). Password and alias: `sunflower-dev`.

It must never be used for a store release. For that, create your own upload key
locally and add it as repository secrets; CI then also builds a signed release
bundle (`.aab`):

| Secret | Value |
|---|---|
| `SUNFLOWER_UPLOAD_KEYSTORE_BASE64` | `base64 -w0 upload.jks` |
| `SUNFLOWER_UPLOAD_KEYSTORE_PASSWORD` | keystore password |
| `SUNFLOWER_UPLOAD_KEY_ALIAS` | key alias |
| `SUNFLOWER_UPLOAD_KEY_PASSWORD` | key password |

```bash
keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload
```
