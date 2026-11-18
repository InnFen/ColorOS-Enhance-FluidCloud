[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$moduleRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $moduleRoot '.local'
$storeFile = Join-Path $signingDirectory 'debug.p12'
$propertiesFile = Join-Path $signingDirectory 'debug.properties'

if ((Test-Path -LiteralPath $storeFile) -or (Test-Path -LiteralPath $propertiesFile)) {
    throw 'Debug signing material already exists. Keep the original key for updates; no files were overwritten.'
}
if (-not $env:JAVA_HOME) { throw 'Load the shared Android env.ps1 before running this script.' }
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw "Missing keytool: $keytool" }

New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
# Keep key/password material accessible only to the current Windows user and SYSTEM.
$acl = [System.Security.AccessControl.DirectorySecurity]::new()
$acl.SetAccessRuleProtection($true, $false)
$inheritance = [System.Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
$acl.SetOwner($currentUser)
$systemUser = [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18')
foreach ($identity in @($currentUser, $systemUser)) {
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
        $identity, 'FullControl', $inheritance, 'None', 'Allow')
    $acl.SetAccessRule($rule)
}
Set-Acl -LiteralPath $signingDirectory -AclObject $acl

$randomBytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
$releasePassword = [Convert]::ToBase64String($randomBytes)
# Persist the password first so a failed keytool invocation cannot leave an unrecoverable key.
[System.IO.File]::WriteAllLines($propertiesFile, @(
    "storePassword=$releasePassword", 'keyAlias=coloros-enhance-debug', "keyPassword=$releasePassword"
), [System.Text.UTF8Encoding]::new($false))

$previousPassword = [Environment]::GetEnvironmentVariable('COLOROS_ENHANCE_DEBUG_PASSWORD', 'Process')
try {
    $env:COLOROS_ENHANCE_DEBUG_PASSWORD = $releasePassword
    & $keytool -genkeypair -alias coloros-enhance-debug -keyalg RSA -keysize 3072 -validity 10000 `
        -storetype PKCS12 -keystore $storeFile -dname 'CN=ColorOS Enhance Debug' `
        -storepass:env COLOROS_ENHANCE_DEBUG_PASSWORD -keypass:env COLOROS_ENHANCE_DEBUG_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Debug key generation failed; preserve .signing for recovery.' }
} finally {
    [Environment]::SetEnvironmentVariable('COLOROS_ENHANCE_DEBUG_PASSWORD', $previousPassword, 'Process')
    $releasePassword = $null
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}
Write-Output 'Debug signing initialized in ignored .local/. Back up this directory securely; never publish it.'
