
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskArguments=@('status')
& 'C:\Users\86137\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/release_workflow.py @taskArguments
if($LASTEXITCODE -ne 0){throw 'Qingxing workflow failed; see output above.'}
