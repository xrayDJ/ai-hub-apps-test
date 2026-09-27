# Roadmap

Work that has been discussed and agreed in principle, with a plan for each.
Every item keeps to what Sunflower is: private by construction, offline, and
polished enough that people who never bothered with AI on their phone find it
worth opening. Anything that would need the internet permission is designed
around that constraint, not by relaxing it.

---

## Accessibility pass

**What.** Make every screen work well with TalkBack, large system fonts and the
light theme.

**Why.** The audience cares about craft; an app that falls apart at 200% font
size or reads "button, button" to a screen reader isn't polished.

**Plan.**
- Audit every icon-only control for a content description (header icons,
  composer, version arrows, ⓘ hints, copy/regenerate). Most exist; verify
  wording is an action ("Search chats"), not a noun.
- Group rows semantically: a chat row reads as one item ("Garden planning,
  Qwen3 4B, 2 minutes ago") with custom actions for Pin, Rename and Delete,
  so long-press isn't the only way in.
- Test with the largest font and display size: status line, chat rows,
  settings headers, the composer and the Think pill must wrap, not clip.
  Message size (Settings) multiplies the system font scale; cap the product
  so text never exceeds the bubble width.
- Check contrast of every text/background pair in both themes (4.5:1 for body
  text, 3:1 for large text and icons). Light theme surfaces (`LightExtras`)
  have had the least attention.
- Respect "Remove animations": when the system animator scale is 0, springs,
  word fades, shimmer and the breathing mark should settle instantly.
- Minimum touch targets of 48 dp where icons are currently 34–40 dp (message
  actions, version arrows); expand the touch area without changing the look.

**Done when.** A full chat can be run with TalkBack alone, the app is usable
at the largest font and display size, and a contrast check passes in both themes.

---

## Encrypted backup and restore

**What.** Export chats, prompts and model settings to a single file protected
by a passphrase; import it on another phone or after a reinstall.

**Why.** Chats are deliberately excluded from Android backups, so changing
phones loses them. Held back for now as a clear later upgrade.

**Plan.**
- Export: serialize conversations, messages (visible versions only), prompts
  and per-model settings to JSON; derive a key from the passphrase with a
  memory-hard KDF (Argon2id, or scrypt if a vetted Android implementation is
  preferred); encrypt with AES-256-GCM; write a small header (format version,
  KDF parameters, salt, nonce).
- Save through the system file picker (`ACTION_CREATE_DOCUMENT`), so the user
  chooses the location; Sunflower still needs no network or storage permission.
- Import: pick the file, ask for the passphrase, decrypt, and merge
  (conversations by id; duplicates skipped) inside one database transaction.
- Model files are never included; only which models chats used, so a restored
  chat offers "Locate file" for its model.
- Wording is plain: what is in the file, that the passphrase cannot be
  recovered, and nothing more.

**Done when.** A backup made on one phone restores on another with every chat,
version and prompt intact, and a wrong passphrase fails cleanly.

---

## Files as input: documents, images and audio

**What.** Attach a PDF, an image or an audio clip to a message.

**Why.** Asking about a document or a photo is one of the most common reasons
to reach for a model; doing it without uploading anything is Sunflower's point.

**Plan.**
- Documents: extract text on-device (PDF text layer via PdfRenderer is
  image-only; use a bundled text extractor such as PdfBox-Android or a
  minimal parser for the text layer). Long documents are chunked; the context
  meter shows how much of the window the attachment uses.
- Images: requires vision-capable GGUF models and their projector files
  (`mmproj`). Check what GenieX exposes for multimodal input in llama.cpp
  (mtmd); if it is not exposed, this waits for the SDK.
- Audio: depends on the speech-to-text work below; a clip becomes text first.
- Attachments are stored inside the encrypted database (or as encrypted files
  keyed to the conversation) and removed with the chat.
- UI: an attach control in the composer, a quiet chip above the message box
  showing the attachment, and a compact preview in the sent message.

**Done when.** A PDF and a photo can be asked about in a chat, fully offline,
and deleting the chat removes the attachments.

---

## Curated models

**What.** A page of recommended GGUF models, with their licenses and the
phones they suit, published on a Sunflower blog and linked from the app.

**Why.** People new to local AI don't know which file to download. A short,
opinionated list helps them start, and the blog can bring people to the app.

**Plan.**
- The list lives on the web, not in the app: Sunflower opens the page in the
  browser and stays without the internet permission.
- Each entry: what the model is good at, size and quantization to pick, the
  license (Apache 2.0, MIT, Gemma terms, Llama community license…) with a link,
  and a pinned link to the exact file on Hugging Face.
- Only models whose licenses allow this use; non-commercial or research-only
  models are left out or clearly marked.
- After downloading, Sunflower can notice a new `.gguf` in Downloads (with
  "All files access" in the full build) and offer to import it in one tap.
- Wording follows the rest of the app: informative, no hype.

**Done when.** A first-time user can go from the Models screen to a working
model through the page without guessing file names.

---

## Local tools

**What.** Let models call small built-in tools during a reply: a calculator,
the current date and time, a search over the user's saved chats, and similar.

**Why.** Small models are much more useful with tools, and every tool here runs
on the phone, so privacy is unchanged.

**Plan.**
- GenieX's `applyChatTemplate` already accepts tool definitions; templates that
  support tools (Qwen3, Llama 3.x, Gemma, gpt-oss…) render them natively.
- Parse tool calls from the stream (template-specific formats, like the
  reasoning formats already handled in `ChatLogic`), run the tool, append the
  result as a tool message and continue generation.
- First tools: calculator (a safe expression evaluator, no scripting), date
  and time, unit conversion, search saved chats (reuses the existing search),
  read an attached document (after files as input).
- Per-model toggle in model settings; tool calls appear in the chat as a quiet,
  foldable line ("Used calculator"), like the thinking section.
- Tool definitions stay small to spare the context window on small models.

**Done when.** A tool-capable model answers "what's 17% of 2,340?" and "what
did we say about Lisbon last week?" correctly by calling tools, offline.

---

## Pre-release checks and owner tasks

**What.** The checks and tasks that stand between the current build and a
public release.

**Plan.**
- On a phone: fresh install; update over an older build (chats survive the
  database migrations, now at version 9); app lock on and off; hide content;
  theme switching; "Delete all data" restarts clean; the play build hides
  "Allow file access" and "Copy into app" works; the Models screen on a
  phone without a Snapdragon chip.
- Host `docs/PRIVACY.md` at a public URL for the store listing.
- Read Qualcomm's Terms of Use, which the GenieX SDK points to (see README,
  Licenses); keep Qualcomm's name out of the listing except as a plain fact.
- Create the upload key and add it as repository secrets (`docs/RELEASE.md`).

**Done when.** Every item in the checklist in `docs/RELEASE.md` is ticked on a
real device and the play bundle is signed with the upload key.

---

## Reading replies aloud

**What.** A "Read aloud" action on replies, using Android's text-to-speech.

**Why.** Cheap to add and useful hands-free; suggested, not yet decided.

**Plan.**
- Use `TextToSpeech` with a voice that is installed offline; if the default
  engine would use the network, say so and let the user choose.
- Speak the reply without Markdown syntax (reuse the parsed blocks), skipping
  code blocks unless asked.
- Controls: play/stop on the reply's actions; speaking stops when the chat is
  left.

**Done when.** A reply can be read aloud offline and stopped at any point.

---

## Reasoning effort per model

**What.** Beyond reasoning on/off, expose the effort levels some models define
in their own chat templates (gpt-oss low/medium/high, and similar switches).

**Why.** Today Think is on or off, which covers most small models. Models with
graded effort would benefit from their own control.

**Plan.**
- Render chat templates in Sunflower with a Jinja implementation (for example
  a Kotlin/JVM Jinja engine) so extra template variables (`reasoning_effort`,
  `thinking_budget`…) can be set, then pass the rendered prompt to the runtime.
- Detect the variables a template reads (the reader already detects
  `enable_thinking` and `reasoning_effort`) and show only the choices that
  template supports, next to Think.
- Fall back to the SDK's own template application when rendering fails.

**Done when.** gpt-oss can be switched between its effort levels from the chat,
and other models keep working unchanged.

---

## Report a reply

**What.** A way to report an offensive or harmful reply from inside the app.

**Why.** Google Play's policy for apps that generate content with AI requires
in-app reporting.

**Plan.**
- A "Report" action on each reply (in its actions row, not prominent).
- It opens the user's email app with a prefilled message to a Sunflower
  address: the reply text, the model's file name and the app version, and
  nothing else. The user sees and edits everything before sending.
- Because email is handled by another app, Sunflower still needs no internet
  permission; the privacy policy states what a report contains.

**Done when.** Reporting a reply produces a clear, editable email and the store
listing can answer the policy question.

---

## Speech input and other apps

**What.** Talk to Sunflower with a local speech-to-text model, and reach it
from other apps: "Ask Sunflower" in the text selection menu and Share to
Sunflower. These ship together.

**Why.** Voice and quick access from anywhere make a local assistant feel
native to the phone.

**Plan.**
- Speech-to-text: a local model (Whisper-family GGUF via llama.cpp/whisper.cpp,
  or what GenieX supports); a hold-to-talk control in the composer; audio never
  leaves the phone and isn't stored after transcription.
- `ACTION_PROCESS_TEXT`: selected text in any app opens a new chat with the
  text quoted and the cursor ready.
- `ACTION_SEND` (text): shared text or a link's title starts a new chat.
- Both entry points respect app lock and open in the last-used model.

**Done when.** A user can select text in a browser, pick "Ask Sunflower", and
speak a follow-up question, all offline.

---

## Two-pane layout on tablets and foldables

**What.** On wide screens, show the chat list beside the open chat.

**Why.** Foldables and tablets with recent Snapdragon chips are among the
best devices for local models; suggested, not yet decided.

**Plan.**
- Use window size classes: compact keeps today's navigation; expanded shows
  home and chat side by side, with Models and Settings as overlays.
- Keep the fade-and-rise transition inside the chat pane; selecting a chat
  swaps the pane without navigating.

**Done when.** On an unfolded phone or tablet, chats can be browsed and read
without leaving either pane.
