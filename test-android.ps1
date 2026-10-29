param([ValidateSet('all','android','location','holiday')][string]$Group='all',[switch]$BuildOnly,[ValidatePattern('^emulator-[0-9]+$')][string]$TestSerial='emulator-5556')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskSuite=if($Group -eq 'all'){'android-all'}else{$Group}
$taskArguments=@('test','--suite',$taskSuite,'--serial',$TestSerial)
if($BuildOnly){$taskArguments=@('test','--suite','android','--serial',$TestSerial,'--build-only')}
& 'C:\Users\86137\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/release_workflow.py @taskArguments
if($LASTEXITCODE -ne 0){throw 'Qingxing workflow failed; see output above.'}
