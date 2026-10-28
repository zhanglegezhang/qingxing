param([switch]$Full,[string[]]$AddSuite=@(),[ValidatePattern('^emulator-[0-9]+$')][string]$TestSerial='emulator-5556')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskArguments=@('release','--serial',$TestSerial)
if($Full){$taskArguments+='--full'}
foreach($taskSuite in $AddSuite){$taskArguments+=@('--add-suite',$taskSuite)}
& 'C:\Users\86137\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/release_workflow.py @taskArguments
if($LASTEXITCODE -ne 0){throw 'Qingxing workflow failed; see output above.'}
