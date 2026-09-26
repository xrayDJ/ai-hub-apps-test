# Sunflower privacy policy

_Last updated: 2026-09-27_

Sunflower is a chat app that runs AI language models entirely on your phone.

## What Sunflower collects

**Nothing.** Sunflower has no accounts, no analytics, no advertising, no crash
reporting service and no servers. The app does not request internet access, so
Android itself prevents it from sending anything off your phone.

## What stays on your phone

- **Conversations, system prompts and model settings** are stored in a database
  encrypted with AES-256 (SQLCipher). Its key is protected by a key held in your
  phone's security hardware (Android Keystore, StrongBox where available).
- **Model files** you import stay where you keep them. If you choose "Copy into
  app", a private copy is kept in Sunflower's own storage.
- **Crash reports** are kept on the phone only, contain engine log lines (no
  conversation text), and are shown to you on the model's card. They are never
  sent anywhere; you can copy one yourself if you want to share it.

Sunflower's data is excluded from Android backups and device-to-device transfer.

## Permissions

| Permission | Why |
|---|---|
| Notifications | Shows "Writing a reply…" while a reply finishes in the background. Never shows your text. |
| Foreground service, wake lock | Keeps a reply you asked for running when you leave the app or the screen turns off. |
| Biometric | Only if you turn on App lock. |
| All files access (sideloaded build only, optional) | Only if you choose it, to load a model file where it's stored instead of copying it. |

## Deleting your data

Settings → Delete all data erases every conversation, prompt, setting and
in-app model copy, and destroys the encryption keys. Uninstalling Sunflower does
the same.

## Contact

Questions: open an issue on the project's repository.
