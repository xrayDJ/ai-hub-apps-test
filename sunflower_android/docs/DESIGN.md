# Design

Sunflower should feel calm, warm and exact: a minimal chat app with a few
organic touches, never a showcase of effects.

## Colour

Dark first, with a light theme that follows the same logic.

- **Neutrals** carry a little brown, like soil and seed husks, so the yellow
  reads warm rather than neon. Surfaces step up in lightness, same hue.
- **Sunflower yellow** (`#FFC21A`) is the one accent: the primary action,
  active states, the underline under a ready model.
- **Marigold** and **seed brown** appear in the logo; **leaf green** and the
  error red are used sparingly for meaning, never decoration.
- The user's messages sit on a warm bubble colour; the model's replies sit on
  the background with the logo beside them.

## Type

Bricolage Grotesque for display (the wordmark, titles), Inter for text,
JetBrains Mono for code. Hierarchy comes from size, weight and colour rather
than boxes.

## Surfaces

- Corners are continuous-curvature (`SmoothCornerShape`), softer than circular
  arcs.
- Raised surfaces are lit from above (`lift`): a faint fall of light, a lit top
  edge and a soft shadow. No outlines.
- Details stay typographic. Figures read as sentences with the values picked
  out ("4B parameters · 4-bit (Q4_K_M) · 2.5 GB"), not tiles or badges.
- Icon buttons are bare; a soft disc appears only under the finger.

## Motion

- Springs with a small overshoot for presses, lifts and entrances; no linear
  tweens except fades.
- Screens change with one transition everywhere: the old screen fades out
  quickly, the new one fades in while rising a short way. Back reverses it.
- Streamed words fade in as they arrive; a sent message rises from the message
  box; the latest reply can be swiped between versions.
- The logo carries state: it breathes when a model is ready, turns while
  loading or writing, and folds when nothing is loaded.
- Effects must be well timed or absent. A transition that lags, reveals content
  late or magnifies things is worse than none.

## Words

- Plain and neutral. Say what something is or does. No hype, no exclamation
  marks, no praise, no telling people something "fits" or that they did well.
- Advice is opt-in: explanations live behind ⓘ and open in place.
- Explanations teach briefly: what the NPU is, what a setting changes, what
  happens on the phone.
- Numbers are given with their meaning ("Room for 8,192 tokens of
  conversation"), not as bare stats.
