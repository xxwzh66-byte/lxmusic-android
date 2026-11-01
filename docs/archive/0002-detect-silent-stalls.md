# Detect a frozen track by the absence of progress, not by waiting for an error

## Context

Users reported that some tracks stop playing around 1:50, with no error, no
notification, and no crash — the position simply stops moving while the player
still reports `isPlaying = true`. Nothing in the app reacted, because every
recovery path was wired to `Player.onPlayerError`, and a freeze raises no error
at all. The player was healthy as far as every API it exposes was concerned.

## Decision

A `StallWatchdog` observes the playback position on the existing 300 ms progress
tick and decides when playback has frozen. It escalates in two steps because the
two causes need different cures:

1. **Retry** — `prepare()` + `seekTo(position)` + `play()`. Recovers a decoder
   hiccup or a seek that never landed, and costs nothing.
2. **Rebuild** — the stream URL is resolved again and the media item is
   replaced, resuming at the frozen position. Recovers an expired URL, which is
   the common case for online tracks and is invisible to the player.

A **stuck buffer** is timed separately (and more patiently) because the player is
not claiming to play, so the stall branch can never see it.

## Considered options

- **React to `onPlayerError` / `onPlaybackStateChanged` only.** These never fire
  for a silent freeze; this is precisely the bug.
- **Rebuild immediately on the first detection.** Restarts the media source for
  a hiccup that a `prepare()` fixes, and loses the buffer, so the user hears a
  gap. Also it re-resolves a URL that may be perfectly valid.
- **Let the user skip manually.** That is what they were already doing, and it
  was the complaint.

## Consequences

- The watchdog is a pure function of `(position, playing, buffering, clock)` with
  no Android types, so the timing budget is unit-tested with a fake clock rather
  than slept through on a device.
- The attempt counter resets as soon as the position moves, so every new freeze
  gets its own cheap retry before anything heavy happens.
- `retryFrozenPlayback()` / `rebuildFrozenSource()` log at `WARN`; a future
  investigation of "playback is unstable" can grep for them.
