param([switch]$Full,[string[]]$AddSuite=@(),[switch]$SkipTests)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskArguments=@('build')
if($SkipTests){$taskArguments+='--skip-tests'}
if($Full){$taskArguments+='--full'}
foreach($taskSuite in $AddSuite){$taskArguments+=@('--add-suite',$taskSuite)}
& 'C:\Users\86137\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/release_workflow.py @taskArguments
if($LASTEXITCODE -ne 0){throw 'Qingxing workflow failed; see output above.'}
