[CmdletBinding()]
param([switch]$Release)
$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Enter-AndroidEnvironment.ps1')
Push-Location -LiteralPath $moduleRoot
try {
    if (-not (Test-Path -LiteralPath '.local/debug.p12')) {
        & .\scripts\Initialize-DebugSigning.ps1
    }
    $tasks = @('testDebugUnitTest','lintDebug','assembleDebug')
    if ($Release) {
        if (-not (Test-Path -LiteralPath '.signing/release.p12')) {
            throw 'Initialize module-local release signing with scripts/Initialize-ReleaseSigning.ps1 before building Release.'
        }
        $tasks += @('lintRelease','assembleRelease')
    }
    & .\gradlew.bat @tasks --offline
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE; inspect the reports before retrying." }
} finally { Pop-Location }
