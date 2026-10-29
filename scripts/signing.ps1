# Keep signing credentials in ignored local files. Only file paths reach apksigner.
function Get-QingxingSigningArguments {
    param([Parameter(Mandatory=$true)][string]$ProjectRoot)
    $taskPrivateRoot = Join-Path $ProjectRoot '.tooling'
    $taskKeyStore = Join-Path $taskPrivateRoot 'personal.keystore'
    $taskStorePassword = Join-Path $taskPrivateRoot 'signing-store-password.txt'
    $taskKeyPassword = Join-Path $taskPrivateRoot 'signing-key-password.txt'
    if (!(Test-Path -LiteralPath $taskKeyStore -PathType Leaf)) {
        throw 'Existing signing key is required; refusing to generate a replacement.'
    }
    if (!(Test-Path -LiteralPath $taskStorePassword -PathType Leaf) -or (Get-Item -LiteralPath $taskStorePassword).Length -eq 0) {
        throw 'Missing local signing-store-password.txt; see docs/BUILD.md.'
    }
    if (!(Test-Path -LiteralPath $taskKeyPassword -PathType Leaf) -or (Get-Item -LiteralPath $taskKeyPassword).Length -eq 0) {
        throw 'Missing or empty local signing-key-password.txt; see docs/BUILD.md.'
    }
    @('--ks', $taskKeyStore, '--ks-key-alias', 'personal',
      '--ks-pass', ('file:' + $taskStorePassword), '--key-pass', ('file:' + $taskKeyPassword))
}
