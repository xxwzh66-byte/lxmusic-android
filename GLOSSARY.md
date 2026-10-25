# 极简music — Domain Glossary

This context is the playback domain of a local/online music player: what a
"queue" is, what "random playback" promises, and what the user is allowed to
expect when they press next, previous, or restart the app.

## Language

### Playback

**Queue**:
The ordered list of tracks the player will walk. It is the single source of
truth — the player's internal playlist is a mirror of it, never a second
opinion.
_Avoid_: playlist (that word is reserved for the mirrored copy inside the media
player and for user-curated 歌单), tracklist

**Mirrored playlist**:
The copy of the Queue handed to the media player so that the notification,
lockscreen and Bluetooth buttons can navigate it. It exists only when every
Queue entry resolves to a URL synchronously.
_Avoid_: native queue, ExoPlayer queue

**Current track**:
The Queue entry being played, identified by position in the Queue. There is no
second "shuffled position" — random playback changes which position is visited
next, never the Queue itself.
_Avoid_: playing index, shuffled index

**Shuffle cycle**:
One complete pass through the Queue in a random order. Every track appears
exactly once per cycle. A cycle ends when its last track finishes; the next
track starts a new cycle anchored on the track that just played.
_Avoid_: shuffle bag, shuffle pass, permutation

**Play history**:
The ordered stack of tracks that were actually heard, most recent last. It is
what "previous" walks backwards. It is bounded: the oldest entries fall off.
_Avoid_: back stack, previous list

**Previous (shuffle mode)**:
The track the user heard before this one — a step back in Play history, not the
Shuffle cycle's predecessor. When the history is empty, "previous" restarts the
current track.
_Avoid_: shuffle-previous, random-previous, undo

**Repeat mode**:
Whether finishing the Queue loops it (`ALL`), repeats the track (`ONE`), or
stops (`OFF`). In shuffle mode a cycle always continues into a new cycle — the
Queue has no meaningful "end" when the order is random.
_Avoid_: loop mode

**Stall**:
A track whose playback position stops advancing while the player still reports
that it is playing. It raises no error; the only evidence is the absence of
progress.
_Avoid_: freeze, hang, dead air

**Stuck buffer**:
The player is not playing and has been buffering longer than a slow network can
explain. Distinct from a Stall because the player is not claiming to play.
_Avoid_: loading forever, spinner

### Settings

**Preference**:
A user choice that survives app restarts. Preferences describe what the user
wants, never what the device currently happens to be doing.
_Avoid_: setting, config, option

**Equalizer profile**:
The user's tone curve: enabled flag, chosen preset, per-band levels, and the
bass / virtualizer / loudness amounts. It is a Preference because the underlying
audio effect is rebuilt empty on every audio session.
_Avoid_: EQ state, equalizer settings

**Effect state**:
What the audio effect object reports right now — available, current band ranges,
last error. Device-dependent, never persisted.
_Avoid_: equalizer state (ambiguous with Equalizer profile)
