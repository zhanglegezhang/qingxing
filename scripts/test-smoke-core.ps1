param([switch]$Minimal,[ValidatePattern('^emulator-[0-9]+$')][string]$TestSerial='emulator-5556')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if(!$env:QINGXING_WORKFLOW_LOCKED -or !$env:QINGXING_CANDIDATE){throw 'Use the public test script'}
$taskCandidate=Get-Content -LiteralPath $env:QINGXING_CANDIDATE -Raw | ConvertFrom-Json
$taskCandidateApk=Join-Path (Split-Path -Parent $env:QINGXING_CANDIDATE) $taskCandidate.file
$taskExpectedCode=[string]$taskCandidate.versionCode
& .\scripts\test-android-core.ps1 -BuildOnly -TestSerial $TestSerial
$adb=Join-Path $PWD '.tooling\adb\platform-tools\adb.exe'
$avd=& $adb -s $TestSerial emu avd name
if(($avd | Select-Object -First 1) -ne 'QingxingSmoke16'){throw 'Tests require the disposable QingxingSmoke16 AVD; the user-facing emulator is protected'}

& $adb -s $TestSerial install -r build\integration\signed.apk
if($LASTEXITCODE -ne 0){throw 'Test package install failed'}
& $adb -s $TestSerial install -r $taskCandidateApk
if($LASTEXITCODE -ne 0){throw 'App install failed'}
foreach($taskPermission in @('POST_NOTIFICATIONS','ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','ACCESS_BACKGROUND_LOCATION')){
    & $adb -s $TestSerial shell pm grant cn.qingxing.reminder "android.permission.$taskPermission"
    if($LASTEXITCODE -ne 0){throw 'Could not prepare emulator-only test permissions'}
}
& $adb -s $TestSerial shell appops set cn.qingxing.reminder SCHEDULE_EXACT_ALARM allow
if($LASTEXITCODE -ne 0){throw 'Could not prepare emulator-only alarms'}
& $adb -s $TestSerial logcat -c
$taskTest=if($Minimal){'MinimalSmokeTests'}else{'SmokeTests'}
$taskReport=if($Minimal){'docs\smoke-min-test-output.txt'}else{'docs\smoke-test-output.txt'}
$taskCrash=if($Minimal){'docs\smoke-min-crash-buffer.txt'}else{'docs\smoke-crash-buffer.txt'}
$result=& $adb -s $TestSerial shell am instrument -w -e expectedCode $taskExpectedCode ('cn.qingxing.reminder.tests/cn.qingxing.reminder.'+$taskTest)
$result | Tee-Object -FilePath $taskReport
if(($result -join "`n") -notmatch 'PASS \d+ final smoke checks' -or ($result -join "`n") -match 'FAIL'){throw 'Smoke test failed'}
$crash=& $adb -s $TestSerial logcat -d -b crash
$taskCrashText=$crash -join "`n"
$taskLegacyKey=Join-Path $PWD '.tooling\baidu-ak.txt'
if(Test-Path -LiteralPath $taskLegacyKey){
    $taskAk=[System.IO.File]::ReadAllText($taskLegacyKey).Trim()
    if($taskAk.Length -gt 0){$taskCrashText=$taskCrashText.Replace($taskAk,'[AK REDACTED]')}
}
$taskCrashText | Out-File -Encoding utf8 $taskCrash
if(($crash -join "`n") -match 'FATAL EXCEPTION'){throw 'Crash recorded during smoke run'}
