package com.lxmusic.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lxmusic.android.player.EqBand
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.components.ConfirmSheet
import com.lxmusic.android.ui.neon.NeonScreen
import com.lxmusic.android.ui.components.toast
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val eq by viewModel.equalizer.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // V6.2: 重置会抹掉已持久化的曲线（设置里存着的那份也跟着变），一次误触
    // 就要重新调五段。破坏性操作走项目既有的确认面板，和删歌单/清队列一致。
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        ConfirmSheet(
            title = "重置均衡器？",
            message = "已保存的音效曲线会被清空，恢复为未调整状态。",
            confirmLabel = "重置",
            onConfirm = { viewModel.eqReset(); toast(context, "已恢复默认音效") },
            onDismiss = { confirmReset = false },
        )
    }
    NeonScreen(
        title = "均衡器",
        onBack = onBack,
        actions = {
            // V6.2: 只在效果器就绪时才给「重置」。以前这个按钮画在
            // `if (!eq.available)` 之外，用户看到「正在等待播放器就绪」的同时
            // 还能点到一个会把已保存曲线抹成全平的按钮 —— 那不是他要的操作。
            if (eq.available) {
                TextButton(onClick = { confirmReset = true }) {
                    Text("重置", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    ) {
        if (!eq.available) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    eq.error?.let { "均衡器不可用：" + it }
                        ?: "正在等待播放器就绪……请先播放一首歌曲",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@NeonScreen
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("启用均衡器", style = MaterialTheme.typography.titleMedium)
                    Text("实时生效于当前播放", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = eq.enabled, onCheckedChange = { viewModel.eqSetEnabled(it) })
            }
            Text("预设", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(eq.presets) { preset ->
                    AssistChip(onClick = { viewModel.eqApplyPreset(preset) }, label = { Text(presetLabel(preset.name)) })
                }
            }
            if (eq.currentPreset != null) {
                Text("当前预设：" + presetLabel(eq.currentPreset!!), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            if (eq.bands.isNotEmpty()) {
                Text("频段", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                EqCurve(bands = eq.bands)
                Spacer(Modifier.height(8.dp))
                eq.bands.forEach { band ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatFreq(band.centerFreqHz), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
                        Slider(
                            value = band.levelDb.toFloat(),
                            onValueChange = { viewModel.eqSetBand(band.index, it.toInt().toShort()) },
                            valueRange = band.minDb.toFloat()..band.maxDb.toFloat(),
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatDb(band.levelDb), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("音效", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            EffectSlider("低音增强", eq.bassStrength, 1000) { viewModel.eqSetBass(it) }
            EffectSlider("环绕扩展", eq.virtualizerStrength, 1000) { viewModel.eqSetVirtualizer(it) }
            EffectSlider("响度提升", eq.loudnessGainMb, 1500) { viewModel.eqSetLoudness(it) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun EffectSlider(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text((value * 100 / max).toString() + "%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..max.toFloat())
    }
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) (hz / 1000).toString() + "." + ((hz % 1000) / 100).toString() + "kHz" else hz.toString() + "Hz"

private fun formatDb(mb: Short): String = String.format("%.1f dB", mb / 100.0)

private fun presetLabel(name: String): String = when {
    name.contains("Normal", true) -> "标准"
    name.contains("Classical", true) -> "古典"
    name.contains("Dance", true) -> "舞曲"
    name.contains("Flat", true) -> "平直"
    name.contains("Folk", true) -> "民谣"
    name.contains("Heavy Metal", true) -> "重金属"
    name.contains("Hip Hop", true) -> "Hip-Hop"
    name.contains("Jazz", true) -> "爵士"
    name.contains("Pop", true) -> "流行"
    name.contains("Rock", true) -> "摇滚"
    else -> name
}

/** 4.7: visualises the 5-band gains as a connected curve (points not draggable yet). */
@Composable
private fun EqCurve(bands: List<com.lxmusic.android.player.EqBand>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp),
    ) {
        if (bands.size < 2) return@Canvas
        val minDb = bands.minOf { it.minDb }.toFloat()
        val maxDb = bands.maxOf { it.maxDb }.toFloat()
        val stepX = size.width / (bands.size - 1)
        val points = bands.mapIndexed { i, b ->
            val t = (b.levelDb.toFloat() - minDb) / (maxDb - minDb).coerceAtLeast(1f)
            Offset(i * stepX, size.height * (1f - t))
        }
        // zero line
        val zeroY = size.height * (1f - (0f - minDb) / (maxDb - minDb).coerceAtLeast(1f))
        drawLine(
            color = color.copy(alpha = 0.25f),
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
        )
        for (i in 0 until points.size - 1) {
            drawLine(color, points[i], points[i + 1], strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        points.forEach { p ->
            drawCircle(color, radius = 5.dp.toPx(), center = p)
        }
    }
}
