param([switch]$SkipTests)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'signing.ps1')
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if(!$env:QINGXING_WORKFLOW_LOCKED -or !$env:QINGXING_CANDIDATE){throw 'Use build.ps1; direct core invocation is unsupported'}
$taskCandidate=Get-Content -LiteralPath $env:QINGXING_CANDIDATE -Raw | ConvertFrom-Json
$taskCandidateFolder=Split-Path -Parent $env:QINGXING_CANDIDATE
$taskCandidateApk=Join-Path $taskCandidateFolder $taskCandidate.file
$taskManifest=Join-Path $taskCandidateFolder 'AndroidManifest.xml'
$jdk = (Get-ChildItem -LiteralPath '.tooling\jdk' -Directory | Select-Object -First 1).FullName
$platform = (Get-ChildItem -LiteralPath '.tooling\platform' -Filter android.jar -Recurse | Select-Object -First 1).FullName
$buildTools = (Get-ChildItem -LiteralPath '.tooling\build-tools' -Filter aapt2.exe -Recurse | Select-Object -First 1).Directory.FullName
if (!$jdk -or !$platform -or !$buildTools) { throw 'Missing project-local tools. See docs/BUILD.md.' }
$env:JAVA_HOME=$jdk
$env:PATH="$jdk\bin;$env:PATH"
$platformRelative=Resolve-Path -LiteralPath $platform -Relative
function Run-Checked([string]$exe,[string[]]$arguments) { & $exe @arguments; if($LASTEXITCODE -ne 0){throw "Command failed ($LASTEXITCODE): $exe"} }
$python='C:\Users\86137\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
New-Item -ItemType Directory -Force -Path build,build\classes,build\generated,build\dex,build\tests,dist | Out-Null
foreach($taskFolder in @('build\classes','build\generated','build\dex','build\tests')){
    $taskAbsolute=[System.IO.Path]::GetFullPath((Join-Path $PWD $taskFolder))
    $taskBuildRoot=[System.IO.Path]::GetFullPath((Join-Path $PWD 'build'))+[System.IO.Path]::DirectorySeparatorChar
    if(!$taskAbsolute.StartsWith($taskBuildRoot,[System.StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe output cleanup path'}
    Get-ChildItem -LiteralPath $taskAbsolute -Force | Remove-Item -Force -Recurse
}
New-Item -ItemType Directory -Force build\wifi-res\values | Out-Null
[System.IO.File]::WriteAllText((Join-Path $PWD 'build\wifi-res\values\version.xml'),('<resources><string name="app_version_name">'+$taskCandidate.versionName+'</string></resources>'),[System.Text.UTF8Encoding]::new($false))
Run-Checked $python @('scripts/sdk_build.py')
$taskSdkJars=@(Get-Content build/sdk-jars.txt)
$taskCompileClasspath=(@($platform)+$taskSdkJars) -join ';'
Run-Checked "$buildTools\aapt2.exe" @('compile','--dir','app\src\main\res','-o','build\resources.zip')
Run-Checked "$buildTools\aapt2.exe" @('compile','--dir','build\wifi-res','-o','build\private-resources.zip')
Run-Checked "$buildTools\aapt2.exe" @('link','-o','build\resources.apk','--manifest',$taskManifest,'-I',$platformRelative,'--java','build\generated','--auto-add-overlay','build\resources.zip','build\private-resources.zip')
$sources=@(Get-ChildItem app\src\main\java,build\generated -Filter '*.java' -Recurse | ForEach-Object { '"'+$_.FullName.Replace('\','/')+'"' })
[System.IO.File]::WriteAllLines((Join-Path $PWD 'build\sources.txt'),$sources,[System.Text.UTF8Encoding]::new($false))
Run-Checked "$jdk\bin\javac.exe" @('-J-Dfile.encoding=UTF-8','-encoding','UTF-8','-Xlint:-options','--release','8','-classpath',$taskCompileClasspath,'-d','build\classes','@build\sources.txt')
Run-Checked "$jdk\bin\jar.exe" @('cf','build\classes.jar','-C','build\classes','.')
$taskD8Arguments=@('-cp',"$buildTools\lib\d8.jar",'com.android.tools.r8.D8','--release','--min-api','28','--lib',$platform,'--output','build\dex','build\classes.jar')
$taskD8Arguments+=$taskSdkJars
& "$jdk\bin\java.exe" @taskD8Arguments *> build\d8-diagnostics.txt
if($LASTEXITCODE -ne 0){Get-Content build\d8-diagnostics.txt -Tail 35;throw 'D8 failed; see build/d8-diagnostics.txt'}
Write-Output ('D8 completed; SDK diagnostic lines: '+(Get-Content build\d8-diagnostics.txt | Measure-Object -Line).Lines)
Copy-Item -LiteralPath build\resources.apk -Destination build\unsigned.apk -Force
foreach($dex in (Get-ChildItem build\dex -Filter '*.dex')){Run-Checked "$jdk\bin\jar.exe" @('uf','build\unsigned.apk','-C','build\dex',$dex.Name)}
Run-Checked $python @('scripts/package_baidu.py')
Run-Checked "$buildTools\zipalign.exe" @('-f','-P','16','4','build\unsigned.apk','build\aligned.apk')
if(!(Test-Path -LiteralPath .tooling\personal.keystore)){throw 'Existing signing key is required; refusing to generate an incompatible replacement.'}
Run-Checked "$jdk\bin\java.exe" (@('-jar',"$buildTools\lib\apksigner.jar",'sign') + @(Get-QingxingSigningArguments -ProjectRoot $PWD.Path) + @('--out',$taskCandidateApk,'build\aligned.apk'))
Run-Checked "$jdk\bin\java.exe" @('-jar',"$buildTools\lib\apksigner.jar",'verify','--verbose',$taskCandidateApk)
if(!$SkipTests){
    Run-Checked $python @('-m','unittest','discover','-s','tests','-p','test_*.py','-v')
    Run-Checked "$jdk\bin\javac.exe" @('-J-Dfile.encoding=UTF-8','-encoding','UTF-8','-Xlint:-options','--release','8','-d','build\tests','app\src\main\java\cn\qingxing\reminder\Rules.java','tests\RulesTest.java')
    Run-Checked "$jdk\bin\java.exe" @('-ea','-cp','build\tests','cn.qingxing.reminder.RulesTest')
    Run-Checked "$jdk\bin\javac.exe" @('-encoding','UTF-8','-Xlint:-options','--release','8','-d','build\tests','app\src\main\java\cn\qingxing\reminder\Rules.java','app\src\main\java\cn\qingxing\reminder\HolidayParser.java','tests\HolidayParserTest.java')
    Run-Checked "$jdk\bin\java.exe" @('-ea','-cp','build\tests','cn.qingxing.reminder.HolidayParserTest')
}
Write-Output "Built candidate: $taskCandidateApk"
