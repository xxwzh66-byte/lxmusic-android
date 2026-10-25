package com.lxmusic.android.ui.components

/**
 * Now-playing screen skins. Cycled by the top-bar button on the player screen
 * and persisted in SettingsStore.
 */
enum class PlayerSkin(val label: String) {
    VINYL("唱片"),
    LYRICS("歌词");

    companion object {
        /** Unknown / legacy values (TAPE, WAVE, MINIMAL, CARD) fall back to VINYL. */
        fun from(name: String?): PlayerSkin =
            entries.firstOrNull { it.name == name } ?: VINYL

        /** The other skin: VINYL <-> LYRICS. */
        fun next(current: PlayerSkin): PlayerSkin =
            if (current == VINYL) LYRICS else VINYL
    }
}
