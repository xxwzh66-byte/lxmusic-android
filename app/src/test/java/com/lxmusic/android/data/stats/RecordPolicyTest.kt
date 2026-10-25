package com.lxmusic.android.data.stats

import com.lxmusic.android.data.settings.AppSettings
import com.lxmusic.android.data.settings.recordPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * V5.3.6: 播放统计不再被「记录历史」开关绑架（HANDOVER §9-7）。
 *
 * 用户诉求（原样保留）：在设置里关掉「播放时记录历史」只想隐藏「最近播放」列表，
 * 不想要「播放统计」页也停止累计。
 *
 * 缝：`SettingsStore.kt` 里的 [recordPolicy] —— 这是 MainViewModel
 * `onTrackStarted` 唯一读取「该记什么」的入口，所以在这里断言等于在调用点断言。
 */
class RecordPolicyTest {

    @Test
    fun historyOffStillRecordsStatistics() {
        val policy = AppSettings(recordHistory = false).recordPolicy()
        assertFalse(policy.history)
        // the switch must not leak into the statistics
        assertEquals(true, policy.stats)
    }

    @Test
    fun statsOffStillRecordsHistory() {
        val policy = AppSettings(recordStats = false).recordPolicy()
        assertFalse(policy.stats)
        assertEquals(true, policy.history)
    }

    @Test
    fun bothIndependentFromDefaults() {
        val on = AppSettings().recordPolicy()
        assertEquals(true, on.history)
        assertEquals(true, on.stats)
        val off = AppSettings(recordHistory = false, recordStats = false).recordPolicy()
        assertEquals(false, off.history)
        assertEquals(false, off.stats)
    }
}
