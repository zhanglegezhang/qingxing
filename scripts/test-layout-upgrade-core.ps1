param([switch]$LatestOnly,[ValidatePattern('^emulator-[0-9]+$')][string]$TestSerial='emulator-5556')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if(!$env:QINGXING_WORKFLOW_LOCKED -or !$env:QINGXING_CANDIDATE){throw 'Use the public test script'}
$taskCandidate=Get-Content -LiteralPath $env:QINGXING_CANDIDATE -Raw | ConvertFrom-Json
$taskCandidateApk=Join-Path (Split-Path -Parent $env:QINGXING_CANDIDATE) $taskCandidate.file
$taskExpectedCode=[string]$taskCandidate.versionCode
$adb=Join-Path $PWD '.tooling\adb\platform-tools\adb.exe'
$avd=& $adb -s $TestSerial emu avd name
if(($avd | Select-Object -First 1) -ne 'QingxingSmoke16'){throw 'Upgrade checks require disposable QingxingSmoke16'}
& .\scripts\test-android-core.ps1 -BuildOnly -TestSerial $TestSerial
function Checked([string[]]$arguments){& $adb @arguments;if($LASTEXITCODE -ne 0){throw 'Emulator command failed'}}
$taskVersion=Get-Content release/version.json -Raw | ConvertFrom-Json
$taskReport=if($LatestOnly){'docs\latest-upgrade-test-output.txt'}else{'docs\layout-upgrade-test-output.txt'}
$taskSeeds=if($LatestOnly){@($taskCandidate.testPlan.baseline)}else{@($taskVersion.upgradeFrom)}
$taskSeeds=@($taskSeeds | Where-Object {$_})
if($taskSeeds.Count -eq 0){'PASS 0 preceding upgrade checks: no previous release baseline' | Set-Content -Encoding utf8 $taskReport;return}
$taskFirst=$true
foreach($taskSeedVersion in $taskSeeds){
    $taskSeedCode=[string]$taskSeedVersion.versionCode
    $taskSeedApk=if($LatestOnly){[string]$taskSeedVersion.apk}else{'tests\fixtures\Qingxing-'+$taskSeedVersion.versionName+'.apk'}
    if($LatestOnly -and (Get-FileHash -LiteralPath $taskSeedApk -Algorithm SHA256).Hash.ToLower() -ne $taskSeedVersion.sha256){throw 'Preceding formal APK hash changed'}
    $installed=& $adb -s $TestSerial shell pm list packages cn.qingxing.reminder
    if(($installed -join "`n") -match 'package:cn.qingxing.reminder'){Checked @('-s',$TestSerial,'uninstall','cn.qingxing.reminder')}
    Checked @('-s',$TestSerial,'install',$taskSeedApk)
    Checked @('-s',$TestSerial,'install','-r','build\integration\signed.apk')
    Checked @('-s',$TestSerial,'shell','pm','grant','cn.qingxing.reminder','android.permission.POST_NOTIFICATIONS')
    Checked @('-s',$TestSerial,'shell','appops','set','cn.qingxing.reminder','SCHEDULE_EXACT_ALARM','allow')
    $seed=& $adb -s $TestSerial shell am instrument -w -e mode seed -e seedCode $taskSeedCode cn.qingxing.reminder.tests/cn.qingxing.reminder.LayoutUpgradeProbe
    if($taskFirst){$seed | Out-File -Encoding utf8 $taskReport;$taskFirst=$false}else{$seed | Out-File -Encoding utf8 $taskReport -Append}
    $seed | Write-Output
    if(($seed -join "`n") -notmatch ('PASS layout upgrade seed on versionCode '+$taskSeedCode)){throw 'Layout seed failed'}
    Checked @('-s',$TestSerial,'install','-r',$taskCandidateApk)
    $result=& $adb -s $TestSerial shell am instrument -w -e mode check -e seedCode $taskSeedCode -e expectedCode $taskExpectedCode cn.qingxing.reminder.tests/cn.qingxing.reminder.LayoutUpgradeProbe
    $result | Tee-Object -FilePath $taskReport -Append
    if(($result -join "`n") -notmatch ('PASS versionCode '+$taskSeedCode+' -> '+$taskExpectedCode) -or ($result -join "`n") -match 'FAIL'){throw 'Layout upgrade failed'}
}
