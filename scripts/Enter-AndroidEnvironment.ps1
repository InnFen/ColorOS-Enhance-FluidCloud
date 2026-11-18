[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'

# Reuse an explicitly configured shared environment, or the workspace sibling.
# Outside that workspace, use the contributor's existing JDK and Android SDK.
$moduleRoot = Split-Path -Parent $PSScriptRoot
$sharedRoot = $null
if ($env:CODEX_ANDROID_ROOT) {
    $sharedRoot = $env:CODEX_ANDROID_ROOT
    if (-not (Test-Path -LiteralPath (Join-Path $sharedRoot 'env.ps1'))) {
        throw 'CODEX_ANDROID_ROOT is configured but its env.ps1 is missing.'
    }
} else {
    $candidate = Join-Path (Split-Path -Parent (Split-Path -Parent $moduleRoot)) 'codex-android'
    if (Test-Path -LiteralPath (Join-Path $candidate 'env.ps1')) { $sharedRoot = $candidate }
}
if ($sharedRoot) { . (Join-Path $sharedRoot 'env.ps1') }

if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to an existing JDK 21 installation before building.'
}
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $sdkRoot -or -not (Test-Path -LiteralPath $sdkRoot)) {
    throw 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an existing Android SDK.'
}
if (-not $env:GRADLE_USER_HOME) {
    $userProfile = [Environment]::GetFolderPath('UserProfile')
    $env:GRADLE_USER_HOME = Join-Path $userProfile '.gradle'
}
