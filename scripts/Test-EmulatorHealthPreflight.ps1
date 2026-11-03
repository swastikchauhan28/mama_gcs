$ErrorActionPreference = 'Stop'
$preflight = Join-Path $PSScriptRoot 'Wait-AndroidEmulatorHealthy.ps1'
$fakeAdb = Join-Path ([System.IO.Path]::GetTempPath()) ("mama-gcs-fake-adb-" + [guid]::NewGuid().ToString('N') + '.ps1')
$oldMode = $env:MAMA_GCS_PREFLIGHT_FAKE
try {
    @'
$command = $args -join ' '
$mode = $env:MAMA_GCS_PREFLIGHT_FAKE
if ($command -match 'getprop sys.boot_completed') { if ($mode -eq 'unbooted') { '0' } else { '1' }; exit 0 }
if ($command -match 'dumpsys window') { if ($mode -eq 'dialog') { 'Application Not Responding: system' } else { 'healthy window' }; exit 0 }
if ($command -match 'date \+%s') { if ($mode -eq 'badclock') { 'not-a-time' } else { '1000' }; exit 0 }
if ($command -match 'logcat -b crash') {
    if ($mode -eq 'unreadable') { exit 1 }
    if ($mode -eq 'crash') { '1000.123 42 42 E crash : synthetic' } else { '999.000 42 42 E crash : old' }; exit 0
}
if ($command -match 'logcat -b events') {
    if ($mode -eq 'unreadable-anr') { exit 1 }
    if ($mode -eq 'anr') { '1000.123 42 42 I am_anr : synthetic' } else { '999.000 42 42 I am_anr : old' }; exit 0
}
exit 1
'@ | Set-Content -LiteralPath $fakeAdb
    foreach ($mode in @('healthy', 'unbooted', 'dialog', 'badclock', 'crash', 'anr', 'unreadable', 'unreadable-anr')) {
        $env:MAMA_GCS_PREFLIGHT_FAKE = $mode
        $failed = $false
        try { & $preflight -Serial 'emulator-5554' -Adb $fakeAdb -QuietSeconds 1 } catch { $failed = $true }
        if (($mode -eq 'healthy' -and $failed) -or ($mode -ne 'healthy' -and -not $failed)) {
            throw "Unexpected preflight result for synthetic case: $mode"
        }
        Write-Host "PASS synthetic case: $mode"
    }
    $rejected = $false
    try { & $preflight -Serial 'physical-device' -Adb $fakeAdb -QuietSeconds 1 } catch { $rejected = $true }
    if (-not $rejected) { throw 'A non-emulator serial was accepted.' }
    Write-Host 'PASS synthetic case: non-emulator serial rejected'
} finally {
    $env:MAMA_GCS_PREFLIGHT_FAKE = $oldMode
    Remove-Item -LiteralPath $fakeAdb -ErrorAction SilentlyContinue
}
