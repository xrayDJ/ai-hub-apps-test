# Where the project is

As of 27 September 2026, branch `claude/sunflower-android`. Version 1.0.0,
not yet published.

## Built

**Models**
- Import any GGUF; its header (architecture, parameters, quantization, context
  length, chat template, MTP layers, recommended sampling) is read on import.
- Loaded in place through a persisted file grant, the file's real path with
  "All files access" (full build), or a private copy inside the app.
- Backends: NPU (Hexagon HTP v73–v81), GPU (Adreno, OpenCL) and CPU. Auto picks
  by chip (`DeviceProfile`) and remembers what worked; a backend that crashed
  the app is skipped next time, with a readable crash report.
- Memory check before loading, with "Load anyway".
- Speculative decoding: draft models, MTP heads (including Gemma 4's
  assistant), EAGLE3 heads, n-gram methods.
- Moved or renamed files can be located again without losing settings.

**Model settings** (per model)
- Replies: presets (Precise, Balanced, Creative, the model's own), temperature,
  top-p, top-k, min-p, repetition penalties, reply length, stop sequences,
  seed, GBNF grammar. Summarised in a sentence at the top.
- Conversation: reasoning on/off, trimming old messages, sliding window.
- Loading: backend, context size, threads, batch sizes, GPU layers, power mode,
  custom chat template, speculative decoding. Changes that need a reload are
  listed until applied.

**Chat**
- Streaming replies with Markdown (headings, lists, tables, code with copy),
  words fading in as they arrive.
- Reasoning shown in a folded "Thinking…" section with its duration; formats
  for Qwen/DeepSeek, Gemma 4, Magistral and gpt-oss.
- Think toggle next to the message box for models whose template can switch
  reasoning.
- Versions: regenerating or editing the last message keeps the previous
  version; swipe the reply or use ‹ › to flip between them.
- Each chat remembers its model and offers to switch back to it.
- System prompts per chat, with a saved library and a default.
- Context meter; replies continue in the background with a notification.
- Message size setting (85–150%).

**Home**
- Chats with search (titles and text, with highlighted excerpts), pinning,
  renaming and deleting; long-press lifts a chat and shows its options.
- A status line for the model; the logo breathes when a model is ready,
  turns while loading and folds when none is loaded.

**Privacy and security**
- No internet permission. SQLCipher database (AES-256), key sealed by an
  Android Keystore key (StrongBox when available). Backups disabled.
- Optional app lock (biometric or screen lock) and "Hide content" (blocks
  screenshots and the recent-apps preview). "Delete all data".

**Release preparation**
- Full and Play build flavours; minified release builds (~13 MB APK after
  removing the unused QNN/QAIRT runtime).
- Privacy policy, Play Console answers and store listing draft
  (`RELEASE.md`), open-source licences in the app and in `licenses/`.

## Verified

- On a device (the owner's phone, Snapdragon, NPU at about 12 tokens/s):
  import, loading on NPU and GPU (in place and as a private copy), chatting,
  MTP heads, the model settings screens, and the first version of the
  redesign, whose feedback shaped the current one.
- In CI on every push: 83 unit tests (GGUF parsing, chat logic, reasoning
  formats and detection, Markdown, settings, search excerpts, versions, device
  profiles, text fading, model labels), Android lint, release builds.

## Not yet verified on a device

The most recent work has only passed CI:
- the latest revision of the redesign: the simplified screen transitions and
  the wording on the Models and model settings screens;
- versions, search/pin/rename, the Think toggle, per-chat models;
- database migrations 7 → 9 on a phone with existing chats;
- the thinking section after the Gemma 4 format fix;
- replies continuing in the background, locating a moved file;
- app lock, hide content, light theme, delete all data;
- a phone without a Snapdragon chip.

## Known gaps

- Nothing yet for Google Play's AI-content reporting requirement.
- Accessibility has not been audited.
- Light theme has had less attention than dark.
- Reasoning can only be on or off; graded effort needs template rendering.

All of these are planned in `ROADMAP.md`.
