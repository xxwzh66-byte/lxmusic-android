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

**Draw**:
In shuffle mode, the choice of which Queue position to visit next. A draw is a
uniform pick over every position except the current one, made once per track
change and remembered until the next track actually starts.
_Avoid_: shuffle cycle, shuffle bag, shuffle pass, permutation

**Next (shuffle mode)**:
A fresh Draw that excludes the current track. It says nothing about what played
before, so pressing next twice can visit tracks in any relation to each other.
_Avoid_: shuffle-next, cycle successor

**Previous (shuffle mode)**:
Also a fresh Draw — the same rule as Next — except that it additionally excludes
the track that just played, so pressing previous cannot bounce between two
tracks. It is *not* a walk back through what the user heard.
_Avoid_: shuffle-previous, random-previous, undo, back stack

**Repeat mode**:
Whether finishing the Queue loops it (`ALL`), repeats the track (`ONE`), or
stops (`OFF`). Shuffle has no "end" to stop at, because every draw produces a
next track as long as the Queue holds more than one entry.
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
