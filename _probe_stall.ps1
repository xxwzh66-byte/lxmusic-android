# 长播探针：从头播一首，每 5 秒采样 media_session 的 position/state，跑满 170 秒。
# 目的：验证「播放中卡住」是否能在模拟器上复现（配合 player/StallWatchdog 的阈值）。
$adb = if ($env:ANDROID_SDK_ROOT) { Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe" } else { "adb" }
$pkg = "com.lxmusic.android.debug"
$act = "com.lxmusic.android.MainActivity"
$log = Join-Path $env:TEMP "stall_probe.log"

function W($msg) {
    $line = "$(Get-Date -Format 'HH:mm:ss') $msg"
    Add-Content -Path $log -Value $line -Encoding utf8
}

"=== probe start $(Get-Date -Format o) ===" | Set-Content -Path $log -Encoding utf8

& $adb wait-for-device
for ($i = 0; $i -lt 90; $i++) {
    $b = ((& $adb shell getprop sys.boot_completed 2>&1) -join "").Trim()
    if ($b -eq "1") { break }
    Start-Sleep -Seconds 2
}
W "boot_completed=$b"

& $adb shell am force-stop $pkg | Out-Null
Start-Sleep -Seconds 1
& $adb shell am start -n "$pkg/$act" | Out-Null
Start-Sleep -Seconds 10

# 主页第一首歌
& $adb shell input tap 293 1160
Start-Sleep -Seconds 6

$prevPos = -1
$stallCount = 0
for ($t = 0; $t -lt 34; $t++) {
    $line = ((& $adb shell dumpsys media_session 2>&1 | Select-String -Pattern "state=PlaybackState" | Select-Object -First 1) -join "").Trim()
    $pm = [regex]::Match($line, "position=(\d+)")
    $sm = [regex]::Match($line, "state=(\d+)")
    $pos = if ($pm.Success) { [long]$pm.Groups[1].Value } else { -1 }
    $st = if ($sm.Success) { $sm.Groups[1].Value } else { "?" }
    $delta = if ($prevPos -ge 0) { $pos - $prevPos } else { 0 }
    if ($prevPos -ge 0 -and $pos -eq $prevPos -and $st -eq "3") { $stallCount++ } else { $stallCount = 0 }
    W ("t={0,4}s state={1} pos={2,7} delta={3,6} stalls={4}" -f ($t * 5), $st, $pos, $delta, $stallCount)
    if ($stallCount -ge 3) { W "!!! STALL at pos=$pos" }
    $prevPos = $pos
    Start-Sleep -Seconds 5
}
W "=== probe end ==="
