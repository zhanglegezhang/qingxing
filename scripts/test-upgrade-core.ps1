param([ValidatePattern('^emulator-[0-9]+$')][string]$TestSerial='emulator-5556')
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

function Checked([string[]]$arguments){& $adb @arguments;if($LASTEXITCODE -ne 0){throw 'Emulator command failed'}}
# Reset only the disposable emulator's test App, never a physical device.
$installed=& $adb -s $TestSerial shell pm list packages cn.qingxing.reminder
if(($installed -join "`n") -match 'package:cn.qingxing.reminder'){Checked @('-s',$TestSerial,'uninstall','cn.qingxing.reminder')}
Checked @('-s',$TestSerial,'install','tests\fixtures\Qingxing-0.3.0.apk')
Checked @('-s',$TestSerial,'install','-r','build\integration\signed.apk')
Checked @('-s',$TestSerial,'shell','pm','grant','cn.qingxing.reminder','android.permission.POST_NOTIFICATIONS')
Checked @('-s',$TestSerial,'shell','appops','set','cn.qingxing.reminder','SCHEDULE_EXACT_ALARM','allow')
$seed=& $adb -s $TestSerial shell am instrument -w -e mode seed cn.qingxing.reminder.tests/cn.qingxing.reminder.UpgradeProbe
if(($seed -join "`n") -notmatch 'PASS upgrade seed'){throw ($seed -join "`n")}
$seed | Tee-Object -FilePath docs\upgrade-test-output.txt
Checked @('-s',$TestSerial,'install','-r',$taskCandidateApk)
$result=& $adb -s $TestSerial shell am instrument -w -e mode check -e expectedCode $taskExpectedCode cn.qingxing.reminder.tests/cn.qingxing.reminder.UpgradeProbe
$result | Tee-Object -FilePath docs\upgrade-test-output.txt -Append
if(($result -join "`n") -notmatch ("PASS versionCode 4 -> "+$taskExpectedCode) -or ($result -join "`n") -match 'FAIL'){throw 'Upgrade retention test failed'}

# Verify the immediately preceding delivered 0.4.0 APK independently.
Checked @('-s',$TestSerial,'uninstall','cn.qingxing.reminder')
Checked @('-s',$TestSerial,'install','tests\fixtures\Qingxing-0.4.0.apk')
Checked @('-s',$TestSerial,'install','-r','build\integration\signed.apk')
Checked @('-s',$TestSerial,'shell','pm','grant','cn.qingxing.reminder','android.permission.POST_NOTIFICATIONS')
Checked @('-s',$TestSerial,'shell','appops','set','cn.qingxing.reminder','SCHEDULE_EXACT_ALARM','allow')
$compactSeed=& $adb -s $TestSerial shell am instrument -w -e mode seed cn.qingxing.reminder.tests/cn.qingxing.reminder.CompactUpgradeProbe
$compactSeed | Tee-Object -FilePath docs\upgrade-test-output.txt -Append
if(($compactSeed -join "`n") -notmatch 'PASS compact upgrade seed'){throw 'Compact seed failed'}
Checked @('-s',$TestSerial,'install','-r',$taskCandidateApk)
$compact=& $adb -s $TestSerial shell am instrument -w -e mode check -e expectedCode $taskExpectedCode cn.qingxing.reminder.tests/cn.qingxing.reminder.CompactUpgradeProbe
$compact | Tee-Object -FilePath docs\upgrade-test-output.txt -Append
if(($compact -join "`n") -notmatch ("PASS versionCode 5 -> "+$taskExpectedCode) -or ($compact -join "`n") -match 'FAIL'){throw 'Compact upgrade failed'}
