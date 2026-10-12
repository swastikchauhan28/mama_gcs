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
$adbArguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
if ($TestClass) { $adbArguments += @('-e', 'class', $TestClass) }
$adbArguments += 'com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner'
Write-Host "Evidence: $log"
Write-Host 'Do not run another UI automation client on this emulator during instrumentation.'
& $Adb @adbArguments | Tee-Object -FilePath $log
$adbExitCode = $LASTEXITCODE
$result = Get-Content -LiteralPath $log -Raw
$success = [regex]::Match($result, 'OK \([1-9][0-9]* tests?\)')
# am instrument can return shell exit 0 even when JUnit reports failures or crashes.
if ($adbExitCode -ne 0 -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|INSTRUMENTATION_STATUS_CODE: -[12]' -or
    -not $success.Success) {
    throw "Android acceptance did not pass. Read $log"
}
Write-Host "PASS: $($success.Value). Evidence: $log"
