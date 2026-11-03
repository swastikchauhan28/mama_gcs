param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^emulator-\d+$')]
    [string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [ValidateRange(1, 300)]
    [int]$QuietSeconds = 30
)

# Read-only preflight: this never dismisses dialogs, clears logs or starts tests.
# A quiet window is evidence for this interval, not a prediction of future health.
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Adb)) { throw "ADB not found: $Adb" }

function Assert-EmulatorWindow {
    $boot = & $Adb -s $Serial shell getprop sys.boot_completed
    if ($LASTEXITCODE -ne 0 -or ($boot -join '').Trim() -ne '1') {
        throw 'Emulator is not booted or cannot be inspected.'
    }
    $windows = & $Adb -s $Serial shell dumpsys window
    if ($LASTEXITCODE -ne 0 -or $windows -match 'Application Not Responding:|Application Error:') {
        throw 'Android has an error dialog or its windows cannot be inspected.'
    }
}

Assert-EmulatorWindow
$epoch = & $Adb -s $Serial shell date +%s
[long]$startEpoch = 0
if ($LASTEXITCODE -ne 0 -or -not [long]::TryParse(($epoch -join '').Trim(), [ref]$startEpoch)) {
    throw 'Cannot read emulator time.'
}
Write-Host "Observing $Serial for $QuietSeconds seconds; keep the emulator untouched."
$timer = [System.Diagnostics.Stopwatch]::StartNew()
while ($timer.Elapsed.TotalSeconds -lt $QuietSeconds) {
    Start-Sleep -Milliseconds 500
}
Assert-EmulatorWindow
$endEpoch = & $Adb -s $Serial shell date +%s
[long]$finishEpoch = 0
if ($LASTEXITCODE -ne 0 -or -not [long]::TryParse(($endEpoch -join '').Trim(), [ref]$finishEpoch) -or $finishEpoch -lt $startEpoch) {
    throw 'Emulator time could not be read or moved backwards.'
}
$crash = & $Adb -s $Serial logcat -b crash -d -v epoch 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Cannot read Android crash buffer.' }
$anr = & $Adb -s $Serial logcat -b events -d -v epoch -s am_anr:I 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Cannot read Android ANR events.' }
$newCrash = @($crash | Where-Object { $_ -match '^\s*(\d+)(?:\.\d+)?\s' -and [long]$Matches[1] -ge $startEpoch })
$newAnr = @($anr | Where-Object { $_ -match '^\s*(\d+)(?:\.\d+)?\s' -and [long]$Matches[1] -ge $startEpoch -and $_ -match '\bam_anr\b' })
if ($newCrash.Count -or $newAnr.Count) {
    throw "Emulator observation failed: $($newCrash.Count) new crash lines and $($newAnr.Count) new ANR events. Preserve logs before restarting."
}
Write-Host "PASS: boot and window checks passed; no new crash or ANR events detected in the $QuietSeconds-second observation."
