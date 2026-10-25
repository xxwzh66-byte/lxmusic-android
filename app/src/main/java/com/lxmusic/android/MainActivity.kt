package com.lxmusic.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.lxmusic.android.ui.CrashReportDialog
import com.lxmusic.android.ui.LxApp
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.theme.JiJianTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results ignored; UI shows empty state if denied */ }

    // A4: pause when headphones are unplugged (audio reroutes to the speaker).
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (viewModel.player.value.isPlaying) viewModel.pause()
            }
        }
    }

    // B1/B3: system-confirmed deletes for MediaStore rows.
    private var pendingDeleteTrack: com.lxmusic.android.data.model.MusicInfo? = null
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val track = pendingDeleteTrack
        pendingDeleteTrack = null
        if (track == null) return@registerForActivityResult
        if (result.resultCode == RESULT_OK) viewModel.onDeleteConfirmed(track) else viewModel.onDeleteCancelled(track)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // V6.0: 极光必须一路画到状态栏与导航栏底下。之前窗口是 fitsSystemWindows，
        // 状态栏那一条由系统按 ?android:colorBackground 填成不透明灰（截图实测 #303030），
        // 于是页面顶部出现一条与极光完全无关的灰带——这是"看起来像另一个 App"的主要原因。
        // 打开 edge-to-edge 之后，各顶栏自己已经有 statusBarsPadding()，
        // 二级页骨架有 navigationBarsPadding()，迷你播放条单独补（见 LxApp）。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteRequest.collect { sender ->
                    try {
                        deleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    } catch (_: Exception) { /* sender expired */ }
                }
            }
        }
        registerReceiver(
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )

        setContent {
            val settings by viewModel.settings.collectAsState()
            JiJianTheme(
                themeMode = settings.themeMode,
                themeColor = settings.themeColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LxApp(viewModel)
                }
                // J1: surface the trace of the previous crash (if any).
                CrashReportDialog(onClear = { CrashLogger.clear(this@MainActivity) })
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        super.onDestroy()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, audioPerm) != PackageManager.PERMISSION_GRANTED) {
            needed.add(audioPerm)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
