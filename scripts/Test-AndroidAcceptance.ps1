param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^emulator-\d+$')]
    [string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [ValidatePattern('^[A-Za-z0-9_.,#]+$')]
    [string]$TestClass
)

# Run only after building/installing both APKs. This does not open a vehicle link,
# dismiss Android error dialogs, clear app data, or turn failed tests into retries.
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Adb)) { throw "ADB not found: $Adb" }
$boot = & $Adb -s $Serial shell getprop sys.boot_completed
if ($LASTEXITCODE -ne 0 -or ($boot -join '').Trim() -ne '1') {
    throw "Test emulator $Serial is not booted."
}
$windows = & $Adb -s $Serial shell dumpsys window
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect emulator window state.' }
if ($windows -match 'Application Not Responding:|Application Error:') {
    throw 'Android has an ANR or crash dialog. Repair/restart the emulator before testing; do not dismiss it during a run.'
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$runName = 'acceptance-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$evidence = New-Item -ItemType Directory -Path (Join-Path $repoRoot "app/build/$runName")
$log = Join-Path $evidence.FullName 'instrumentation.txt'
$healthLog = Join-Path $evidence.FullName 'android-health.txt'
$crashLog = Join-Path $evidence.FullName 'android-crash-logcat.txt'
$anrLog = Join-Path $evidence.FullName 'android-anr-events.txt'

# Match the AVD name to a process, not merely the first emulator.exe on the host.
# Record the selected launch option only; the process command line may contain local paths.
$avdLines = & $Adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0 -or -not $avdLines) { throw "Cannot identify emulator AVD $Serial." }
$avdName = ($avdLines | Select-Object -First 1).Trim()
$gpuMode = 'unavailable'
try {
    $matchingProcesses = @(Get-CimInstance Win32_Process -Filter "name='emulator.exe'" |
        Where-Object { $_.CommandLine -match ('@' + [regex]::Escape($avdName) + '(\s|$)') })
    if ($matchingProcesses.Count -eq 1) {
        $gpuMatch = [regex]::Match($matchingProcesses[0].CommandLine, '(?:^|\s)-gpu\s+(\S+)')
        $gpuMode = if ($gpuMatch.Success) { $gpuMatch.Groups[1].Value } else { 'default (no -gpu option)' }
    }
} catch {
    Write-Warning "Cannot inspect emulator graphics process: $($_.Exception.Message)"
}
$startEpochLines = & $Adb -s $Serial shell date +%s
[long]$startEpoch = 0
if ($LASTEXITCODE -ne 0 -or -not $startEpochLines -or
    -not [long]::TryParse(($startEpochLines -join '').Trim(), [ref]$startEpoch)) {
    throw "Cannot obtain emulator time for Android health gate."
}
@("Serial: $Serial", "AVD: $avdName", "GPU launch option: $gpuMode", "Device start epoch (seconds): $startEpoch") |
    Set-Content -LiteralPath $healthLog
$adbArguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
if ($TestClass) { $adbArguments += @('-e', 'class', $TestClass) }
$adbArguments += 'com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner'
Write-Host "Evidence: $log"
Write-Host 'Do not run another UI automation client on this emulator during instrumentation.'
& $Adb @adbArguments | Tee-Object -FilePath $log
$adbExitCode = $LASTEXITCODE
$result = Get-Content -LiteralPath $log -Raw
$success = [regex]::Match($result, 'OK \([1-9][0-9]* tests?\)')
# Log buffers are read, never cleared. Epoch filtering keeps old emulator failures
# from being attributed to this run; a same-second old event can conservatively fail it.
$crashLines = & $Adb -s $Serial logcat -b crash -d -v epoch 2>&1
$crashExitCode = $LASTEXITCODE
$anrLines = & $Adb -s $Serial logcat -b events -d -v epoch -s am_anr:I 2>&1
$anrExitCode = $LASTEXITCODE
$recentCrash = @($crashLines | Where-Object {
    $_ -match '^\s*(\d+)(?:\.\d+)?\s' -and [long]$Matches[1] -ge $startEpoch
})
$recentAnr = @($anrLines | Where-Object {
    $_ -match '^\s*(\d+)(?:\.\d+)?\s' -and [long]$Matches[1] -ge $startEpoch -and $_ -match '\bam_anr\b'
})
if ($recentCrash.Count -eq 0) {
    'No new crash records.' | Set-Content -LiteralPath $crashLog
} else {
    $recentCrash | Set-Content -LiteralPath $crashLog
}
if ($recentAnr.Count -eq 0) {
    'No new ANR events.' | Set-Content -LiteralPath $anrLog
} else {
    $recentAnr | Set-Content -LiteralPath $anrLog
}
$postBoot = & $Adb -s $Serial shell getprop sys.boot_completed
$postBootExitCode = $LASTEXITCODE
$postWindows = & $Adb -s $Serial shell dumpsys window
$postWindowsExitCode = $LASTEXITCODE
$androidHealthy = $crashExitCode -eq 0 -and $anrExitCode -eq 0 -and
    $postBootExitCode -eq 0 -and ($postBoot -join '').Trim() -eq '1' -and
    $postWindowsExitCode -eq 0 -and $postWindows -notmatch 'Application Not Responding:|Application Error:' -and
    $recentCrash.Count -eq 0 -and $recentAnr.Count -eq 0
@("ADB instrumentation exit: $adbExitCode", "JUnit result: $($success.Value)",
    "Crash-buffer read exit: $crashExitCode", "New crash log lines: $($recentCrash.Count)",
    "ANR-event read exit: $anrExitCode", "New ANR events: $($recentAnr.Count)",
    "Post-run boot query exit: $postBootExitCode", "Post-run boot complete: $(($postBoot -join '').Trim())",
    "Post-run window query exit: $postWindowsExitCode",
    "Post-run ANR/error dialog: $([bool]($postWindows -match 'Application Not Responding:|Application Error:'))",
    "Android health gate: $androidHealthy") | Add-Content -LiteralPath $healthLog
# am instrument can return shell exit 0 even when JUnit reports failures or crashes.
if ($adbExitCode -ne 0 -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|INSTRUMENTATION_STATUS_CODE: -[12]' -or
    -not $success.Success -or -not $androidHealthy) {
    throw "Android acceptance did not pass. Read $log and $healthLog"
}
Write-Host "PASS: $($success.Value); Android healthy; GPU option $gpuMode. Evidence: $log and $healthLog"
