# Random playback is one shuffle cycle per pass, not a fresh draw per press

## Context

The player used to answer "next" by drawing a brand-new order over the whole
queue every time it was asked, and by throwing the previous one away on every
track change. The result was statistically indistinguishable from "pick any
other track uniformly at random": over a 20-track queue, a repeat within the
first 10 tracks was ~89% likely. Users hear that as "shuffle keeps playing the
same few songs", and the app's own next button disagreed with the notification's
next button, because each path drew its own order.

## Decision

A shuffle **cycle** is one order walked front to back; every track appears
exactly once per cycle. The cycle survives track changes and survives the
player asking the same question more than once. When the cycle's last track
finishes, the next press starts a new cycle *anchored on the track that just
played*, so the transition never immediately repeats the current track.

"Previous" in shuffle mode is **not** the cycle's predecessor — that is a track
this cycle has not reached yet, so it sounds like a random jump forward. It walks
the play history stack instead, and restarts the current track when the history
is empty.

## Considered options

- **Fresh order per press** (the old behaviour). Simple, but produces the
  repeat-heavy distribution above and makes two decision paths impossible to
  reconcile.
- **Pre-shuffled queue order** (reorder the list once). Trivially consistent, but
  destroys the user's queue order and makes "turn shuffle off" ambiguous.
- **Cycle + shared state** (chosen). The order lives in one object that
  both the app buttons and the media player's `ShuffleOrder` read from, so the
  two paths cannot drift.

## Consequences

- The queue itself is never reordered; only the order in which positions are
  visited changes. Shuffle off restores the user's order for free.
- `repeatMode == OFF` does not stop a shuffle pass at the "end of the list" —
  there is no end. Documenting this was necessary; an earlier README claimed the
  opposite.
- Queue edits (insert, move, remove) must invalidate the cycle, because indices
  measured against the old length mean nothing. The `RandomShuffleState.version`
  generation is what tells the installer the order needs re-pushing.
