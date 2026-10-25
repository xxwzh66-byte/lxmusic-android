package com.lxmusic.android

/**
 * Compile-time feature switches.
 *
 * Online search is intentionally disabled for the "极简music" local-first release.
 * The full online stack (search engine, custom-source runtime, bundled LX JS
 * assets) is kept intact and dormant so it can be re-enabled by flipping a
 * single flag — no code needs to be restored.
 */
object FeatureFlags {

    /**
     * When false:
     *  - the ONLINE tab is not shown,
     *  - SourceManager refuses online calls before starting any WebView,
     *  - no WebView process is created at all (saves 30–80 MB RAM).
     */
    const val ONLINE_ENABLED: Boolean = false

    /** Master switch for the custom-source (自定义源) playback path. */
    const val CUSTOM_SOURCE_ENABLED: Boolean = ONLINE_ENABLED
}
