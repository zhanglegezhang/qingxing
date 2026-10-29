param([ValidateSet('all','location','map','address')][string]$Scope='all',[ValidatePattern('^emulator-[0-9]+$')][string]$TestSerial='emulator-5556',[switch]$Preview)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskSuite=if($Scope -eq 'all'){'baidu'}else{'baidu-'+$Scope}
$taskArguments=@('test','--suite',$taskSuite,'--serial',$TestSerial)
if($Preview){$taskArguments+='--preview'}
& 'C:\Users\86137\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/release_workflow.py @taskArguments
if($LASTEXITCODE -ne 0){throw 'Qingxing workflow failed; see output above.'}
