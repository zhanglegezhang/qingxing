param([ValidateSet('all','android','location','holiday')][string]$Group='all',[switch]$BuildOnly,[ValidatePattern('^emulator-[0-9]+$')][string]$TestSerial='emulator-5556')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'signing.ps1')
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if(!$env:QINGXING_WORKFLOW_LOCKED -or !$env:QINGXING_CANDIDATE){throw 'Use the public test script'}
$taskCandidate=Get-Content -LiteralPath $env:QINGXING_CANDIDATE -Raw | ConvertFrom-Json
$taskCandidateApk=Join-Path (Split-Path -Parent $env:QINGXING_CANDIDATE) $taskCandidate.file
$taskExpectedCode=[string]$taskCandidate.versionCode
$jdk=(Get-ChildItem .tooling\jdk -Directory | Select-Object -First 1).FullName
$platform=(Get-ChildItem .tooling\platform -Filter android.jar -Recurse | Select-Object -First 1).FullName
$bt=(Get-ChildItem .tooling\build-tools -Filter aapt2.exe -Recurse | Select-Object -First 1).Directory.FullName
$adb=Join-Path $PWD '.tooling\adb\platform-tools\adb.exe'
function Run-Checked([string]$exe,[string[]]$arguments){& $exe @arguments;if($LASTEXITCODE -ne 0){throw "Failed: $exe"}}
$taskOwnerFile=Join-Path $PWD 'build\integration\owner.json'
$taskCached=$false
if(Test-Path -LiteralPath $taskOwnerFile){
    $taskOwner=Get-Content -LiteralPath $taskOwnerFile -Raw | ConvertFrom-Json
    if($taskOwner.apk -eq $taskCandidate.sha256 -and $taskOwner.source -eq $taskCandidate.sourceSha256 -and (Test-Path build\integration\signed.apk)){
        $taskCached=(Get-FileHash build\integration\signed.apk -Algorithm SHA256).Hash.ToLower() -eq $taskOwner.signed
    }
}
if(!$taskCached){
$taskClasses=Join-Path 'build\integration' ('classes-'+$taskCandidate.sourceSha256.Substring(0,16))
New-Item -ItemType Directory -Force build\integration,$taskClasses,build\integration\dex,build\integration\assets | Out-Null
Copy-Item -LiteralPath tests\fixtures\official-2026.txt,tests\fixtures\holiday-2026.json -Destination build\integration\assets
Run-Checked "$bt\aapt2.exe" @('link','-o','build\integration\test.apk','--manifest','tests\AndroidManifest.xml','-A','build\integration\assets','-I',(Resolve-Path -LiteralPath $platform -Relative))
$testSources=@(Get-ChildItem tests -Filter '*.java' | Where-Object {$_.Name -notin @('RulesTest.java','HolidayParserTest.java')} | ForEach-Object {$_.FullName})
$taskSdkJars=@(Get-Content build/sdk-jars.txt)
$taskTestClasspath=(@($platform,'build\classes')+$taskSdkJars) -join ';'
Run-Checked "$jdk\bin\javac.exe" (@('-encoding','UTF-8','-Xlint:-options','--release','8','-cp',$taskTestClasspath,'-d',$taskClasses)+$testSources)
Run-Checked "$jdk\bin\jar.exe" @('cf','build\integration\classes.jar','-C',$taskClasses,'.')
Run-Checked "$jdk\bin\java.exe" @('-cp',"$bt\lib\d8.jar",'com.android.tools.r8.D8','--release','--min-api','28','--lib',$platform,'--classpath','build\classes.jar','--output','build\integration\dex','build\integration\classes.jar')
Run-Checked "$jdk\bin\jar.exe" @('uf','build\integration\test.apk','-C','build\integration\dex','classes.dex')
Run-Checked "$bt\zipalign.exe" @('-f','4','build\integration\test.apk','build\integration\aligned.apk')
Run-Checked "$jdk\bin\java.exe" (@('-jar',"$bt\lib\apksigner.jar",'sign') + @(Get-QingxingSigningArguments -ProjectRoot $PWD.Path) + @('--out','build\integration\signed.apk','build\integration\aligned.apk'))
    @{apk=$taskCandidate.sha256;source=$taskCandidate.sourceSha256;signed=(Get-FileHash build\integration\signed.apk -Algorithm SHA256).Hash.ToLower()} | ConvertTo-Json | Set-Content -Encoding utf8 $taskOwnerFile
}
if($BuildOnly){return}
$avd=& $adb -s $TestSerial emu avd name
if(($avd | Select-Object -First 1) -ne 'QingxingSmoke16'){throw 'Tests require the disposable QingxingSmoke16 AVD; the user-facing emulator is protected'}

# Serial and AVD guards restrict destructive checks to the separate disposable test emulator.
Run-Checked $adb @('-s',$TestSerial,'install','-r',$taskCandidateApk)
Run-Checked $adb @('-s',$TestSerial,'install','-r','build\integration\signed.apk')
Run-Checked $adb @('-s',$TestSerial,'shell','pm','grant','cn.qingxing.reminder','android.permission.POST_NOTIFICATIONS')
Run-Checked $adb @('-s',$TestSerial,'shell','pm','grant','cn.qingxing.reminder','android.permission.ACCESS_COARSE_LOCATION')
Run-Checked $adb @('-s',$TestSerial,'shell','pm','grant','cn.qingxing.reminder','android.permission.ACCESS_FINE_LOCATION')
Run-Checked $adb @('-s',$TestSerial,'shell','pm','grant','cn.qingxing.reminder','android.permission.ACCESS_BACKGROUND_LOCATION')
Run-Checked $adb @('-s',$TestSerial,'shell','appops','set','cn.qingxing.reminder','SCHEDULE_EXACT_ALARM','allow')
$taskGroups=@{
    android=@('FlowTests','android-test-output.txt','Android integration/UI checks')
    location=@('LocationTests','location-test-output.txt','location callback/status checks')
    holiday=@('HolidayTests','holiday-test-output.txt','holiday sync/cache checks')
}
$taskSelection=if($Group -eq 'all'){@('android','location','holiday')}else{@($Group)}
foreach($taskGroup in $taskSelection){
    $taskSpec=$taskGroups[$taskGroup]
    $taskOutput=& $adb -s $TestSerial shell am instrument -w ('cn.qingxing.reminder.tests/cn.qingxing.reminder.'+$taskSpec[0])
    $taskOutput | Tee-Object -FilePath (Join-Path 'docs' $taskSpec[1])
    if($LASTEXITCODE -ne 0 -or ($taskOutput -join "`n") -notmatch ('PASS \d+ '+[regex]::Escape($taskSpec[2])) -or ($taskOutput -join "`n") -match 'FAIL'){throw ('Android test group failed: '+$taskGroup)}
    if($taskGroup -eq 'android'){
        $taskHomeOutput=& $adb -s $TestSerial shell am instrument -w cn.qingxing.reminder.tests/cn.qingxing.reminder.HomeSyncTests
        $taskHomeOutput | Tee-Object -FilePath (Join-Path 'docs' $taskSpec[1]) -Append
        if($LASTEXITCODE -ne 0 -or ($taskHomeOutput -join "`n") -notmatch 'PASS \d+ notification/home/debug checks' -or ($taskHomeOutput -join "`n") -match 'FAIL'){throw 'Home synchronization/debug checks failed'}
        $taskReminderOutput=& $adb -s $TestSerial shell am instrument -w cn.qingxing.reminder.tests/cn.qingxing.reminder.ReminderTests
        $taskReminderOutput | Tee-Object -FilePath (Join-Path 'docs' $taskSpec[1]) -Append
        if($LASTEXITCODE -ne 0 -or ($taskReminderOutput -join "`n") -notmatch 'PASS \d+ bounded reminder lifecycle checks' -or ($taskReminderOutput -join "`n") -match 'FAIL'){throw 'Bounded reminder lifecycle tests failed'}
        $taskHiddenOutput=& $adb -s $TestSerial shell am instrument -w cn.qingxing.reminder.tests/cn.qingxing.reminder.HiddenLocationTests
        $taskHiddenOutput | Tee-Object -FilePath (Join-Path 'docs' $taskSpec[1]) -Append
        if($LASTEXITCODE -ne 0 -or ($taskHiddenOutput -join "`n") -notmatch 'PASS \d+ hidden location access checks' -or ($taskHiddenOutput -join "`n") -match 'FAIL'){throw 'Hidden location access tests failed'}
    }
}
