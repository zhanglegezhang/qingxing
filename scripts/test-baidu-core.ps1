param([ValidateSet('location','map','address')][string]$Scope='location',[ValidatePattern('^emulator-[0-9]+$')][string]$TestSerial='emulator-5556',[switch]$Preview)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if(!$env:QINGXING_WORKFLOW_LOCKED -or !$env:QINGXING_CANDIDATE){throw 'Use the public test script'}
$taskCandidate=Get-Content -LiteralPath $env:QINGXING_CANDIDATE -Raw | ConvertFrom-Json
$taskCandidateApk=Join-Path (Split-Path -Parent $env:QINGXING_CANDIDATE) $taskCandidate.file
$taskExpectedCode=[string]$taskCandidate.versionCode
$adb=Join-Path $PWD '.tooling\adb\platform-tools\adb.exe'
$avd=& $adb -s $TestSerial emu avd name
if(($avd | Select-Object -First 1) -ne 'QingxingSmoke16'){throw 'Baidu checks require the disposable QingxingSmoke16 AVD'}
& .\scripts\test-android-core.ps1 -BuildOnly -TestSerial $TestSerial
& $adb -s $TestSerial install -r $taskCandidateApk
if($LASTEXITCODE -ne 0){throw 'App install failed'}
& $adb -s $TestSerial install -r build\integration\signed.apk
if($LASTEXITCODE -ne 0){throw 'Test install failed'}
foreach($taskPermission in @('POST_NOTIFICATIONS','ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','ACCESS_BACKGROUND_LOCATION')){
    & $adb -s $TestSerial shell pm grant cn.qingxing.reminder "android.permission.$taskPermission"
    if($LASTEXITCODE -ne 0){throw 'Could not prepare emulator-only test permissions'}
}
& $adb -s $TestSerial shell appops set cn.qingxing.reminder SCHEDULE_EXACT_ALARM allow
if($Scope -eq 'location'){ & $adb -s $TestSerial emu geo fix 113.264 23.129 }
& $adb -s $TestSerial shell input keyevent 224
& $adb -s $TestSerial shell wm dismiss-keyguard
$arguments=@('-s',$TestSerial,'shell','am','instrument','-w','-e','scope',$Scope)
if($Preview){$arguments+=@('-e','preview','true')}
$arguments+='cn.qingxing.reminder.tests/cn.qingxing.reminder.BaiduTests'
$result=& $adb @arguments
$safe=$result -join "`n"
$safe | Tee-Object -FilePath (Join-Path 'docs' ('baidu-'+$Scope+'-test-output.txt'))
if($safe -notmatch 'PASS \d+ Baidu tool checks' -or $safe -match 'FAIL'){throw 'Baidu location tool checks failed'}
