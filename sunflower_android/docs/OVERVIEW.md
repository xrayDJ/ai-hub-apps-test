# Sunflower

## Who it's for

People with a powerful Android phone, curious about technology, who have never
run an AI model on it. Not because it's impossible (a recent Snapdragon can run
a capable model at 10+ tokens per second) but because nothing made it worth the
trouble: the options were command-line ports, rough demo apps, or cloud apps
that send every word to a server.

They know what a GGUF file is or are happy to learn. They care about privacy,
notice craft, and dislike being talked down to.

## What it is

A chat app that runs language models entirely on the phone.

- **Bring any model.** Import a `.gguf` file from local storage; Sunflower
  reads its header and shows what it is.
- **Runs on the hardware that's there.** On recent Snapdragon chips models run
  on the NPU, a processor built for neural networks; elsewhere on the GPU or
  CPU. Sunflower detects the chip and picks the right path.
- **Private by construction.** The app has no internet permission, so nothing
  can leave the phone. Chats are stored in an encrypted database whose key is
  held in the phone's security hardware.
- **Every setting, explained.** Sampling, context, loading, speculative
  decoding, chat templates: all adjustable, each with a plain explanation one
  tap away.
- **Polished.** Soft surfaces, spring motion, words that fade in as they arrive,
  a logo that shows the model's state. It should feel like an app people
  choose, not a tool they tolerate.

## What it isn't

- Not a cloud client. No accounts, no sync, no telemetry, no network.
- Not a model store. Sunflower doesn't download models; people bring their own
  (a curated list on the web is planned, see `ROADMAP.md`).
- Not a benchmark. Speed is shown quietly per reply; comparing backends or
  models is not the point.

## Principles

1. **Private is not a setting.** Privacy comes from what the app can't do (no
   network permission), not from promises.
2. **Explain on request.** Advice is opt-in behind ⓘ buttons. Screens are not
   covered in tips.
3. **Plain, neutral words.** Say what something is or does. No hype, no
   cheerleading, no telling people they did well.
4. **Everything is adjustable, nothing is required.** Defaults work; experts
   can change everything, including loading options for unusual models.
5. **Minimal, then refined.** Fewer elements, done carefully. Details are
   typography, not badges and tiles.

## The rest of the documentation

| Document | Contents |
|---|---|
| `STATUS.md` | What exists today, what has been verified, known gaps |
| `ARCHITECTURE.md` | How the code is organised and how a reply is produced |
| `DESIGN.md` | Visual language, motion, and how the app speaks |
| `ROADMAP.md` | Planned work, with a plan for each item |
| `RELEASE.md` | Builds, signing, Play Console answers, pre-release checklist |
| `PRIVACY.md` | The privacy policy |
