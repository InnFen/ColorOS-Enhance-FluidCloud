[CmdletBinding()]
param([switch]$ReleaseCheck, [string]$Apk, [string]$ReportPath)
$ErrorActionPreference = 'Stop'
$moduleRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$privateRoot = Join-Path $moduleRoot '.local'
if (-not $ReportPath) { $ReportPath = Join-Path $privateRoot 'publish-prep/audit-current.json' }
$ReportPath = [System.IO.Path]::GetFullPath($ReportPath)
if (-not $ReportPath.StartsWith($privateRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keep audit reports under the ignored .local directory.'
}

# Report only kinds and locations. Never echo matched credentials or identifiers.
$issues = [System.Collections.Generic.List[object]]::new()
function Add-Issue([string]$Kind, [string]$File, [int]$Line = 0) {
    $issues.Add([pscustomobject]@{ Kind = $Kind; File = $File; Line = $Line })
}
$patterns = [ordered]@{
    PersonalPath = '(?i)\b[A-Z]:[\\/]Users[\\/][^\\/\s<>''"]+|/(?:Users|home)/[^/\s<>''"]+/'
    MachinePath = '(?i)\b[A-Z]:[\\/](?:dev|work|projects)[\\/]'
    DeviceSerial = '(?<![A-Za-z0-9_$])(?=[A-Z0-9]{14,20}(?![A-Za-z0-9_$]))(?=[A-Z0-9]*[A-Z])[0-9][A-Z0-9]{13,19}(?![A-Za-z0-9_$])'
    AdbTarget = '(?i)\badb(?:\.exe)?\s+-s\s+(?![<$\[{]|YOUR_|DEVICE_|SERIAL)[A-Za-z0-9][A-Za-z0-9._:-]{6,}'
    PrivateKey = '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'
    AccessToken = '(?<!\w)(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})(?!\w)'
    EmailReview = '(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}'
    ChatReference = '(?:thread|codex)://|chatgpt\.com/(?:c|share)/'
}
function Test-PrivacyPattern([string]$Text, [string]$Kind) {
    foreach ($match in [regex]::Matches($Text, $patterns[$Kind])) {
        # A standard hexadecimal alphabet in library code is not a device ID.
        if ($Kind -eq 'DeviceSerial' -and $match.Value -eq '0123456789ABCDEF') { continue }
        return $true
    }
    return $false
}
$privateArtifact = '(?i)(^|/)(?:\.local|\.signing|\.gradle|\.kotlin|build|captures|shared_prefs)(/|$)|\.(?:p12|pfx|jks|keystore|pem|key|apk|aab|dex|log|mp4|hprof|jfr)$|(^|/)local\.properties$|(^|/)\.env(?:\.|$)'
$publicDocs = @('docs/PUBLISHING.md', 'docs/PRIVACY.md', 'docs/MIGRATION.md')
$files = @(& git -C $moduleRoot -c core.quotepath=false ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate candidate Git files.' }
$files = @($files | Sort-Object -Unique)
$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
foreach ($file in $files) {
    if ($file -match $privateArtifact -or $file.StartsWith('docs/') -and $file -notin $publicDocs) {
        Add-Issue 'PrivateArtifact' $file
    }
    $absolute = [System.IO.Path]::GetFullPath((Join-Path $moduleRoot $file))
    if (-not $absolute.StartsWith($moduleRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $absolute -PathType Leaf)) { throw 'Unexpected candidate Git path.' }
    if (([System.IO.File]::GetAttributes($absolute) -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        Add-Issue 'SymlinkReview' $file
        continue
    }
    # The checked-in Wrapper is the one intentional binary in this source tree.
    if ($file -eq 'gradle/wrapper/gradle-wrapper.jar') { continue }
    try { $text = $utf8.GetString([System.IO.File]::ReadAllBytes($absolute)) }
    catch [System.Text.DecoderFallbackException] { Add-Issue 'BinaryReview' $file; continue }
    if ($text.Contains([char]0)) { Add-Issue 'BinaryReview' $file; continue }
    $lines = $text -split '\r?\n'
    foreach ($kind in $patterns.Keys) {
        for ($i = 0; $i -lt $lines.Length; $i++) {
            if (Test-PrivacyPattern $lines[$i] $kind) { Add-Issue $kind $file ($i + 1) }
        }
    }
}

$apkEntries = 0
if ($Apk) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (-not ('PublishAuditDex' -as [type])) {
        # Read the actual DEX string table, not random instruction bytes that can
        # accidentally resemble an address or device serial. No decompiler needed.
        Add-Type -TypeDefinition @'
using System;
using System.Text;
public static class PublishAuditDex {
    public static string Strings(byte[] data) {
        if (data.Length < 112 || Encoding.ASCII.GetString(data, 0, 4) != "dex\n")
            throw new ArgumentException("Unsupported DEX header");
        uint count = BitConverter.ToUInt32(data, 56), start = BitConverter.ToUInt32(data, 60);
        if ((ulong)start + (ulong)count * 4 > (ulong)data.Length)
            throw new ArgumentException("Invalid DEX string table");
        var result = new StringBuilder();
        for (uint i = 0; i < count; i++) {
            uint rawOffset = BitConverter.ToUInt32(data, checked((int)(start + i * 4)));
            if (rawOffset >= data.Length) throw new ArgumentException("Invalid DEX string offset");
            int offset = (int)rawOffset, bytes = 0;
            while (true) {
                if (offset >= data.Length || ++bytes > 5) throw new ArgumentException("Invalid DEX length");
                if ((data[offset++] & 128) == 0) break;
            }
            int end = Array.IndexOf(data, (byte)0, offset);
            if (end < offset) throw new ArgumentException("Unterminated DEX string");
            result.Append(Encoding.UTF8.GetString(data, offset, end - offset)).Append('\n');
        }
        return result.ToString();
    }
}
'@
    }
    $apkPath = (Resolve-Path -LiteralPath $Apk).Path
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
    try {
        foreach ($entry in $archive.Entries) {
            $apkEntries++
            if ($entry.FullName -match $privateArtifact) {
                # DEX is required inside the APK, not inside the source repository.
                if ($entry.FullName -notmatch '^classes\d*\.dex$' -and
                        $entry.FullName -ne 'META-INF/com/android/build/gradle/app-metadata.properties') {
                    Add-Issue 'ApkPrivateArtifact' $entry.FullName
                }
            }
            if ($entry.FullName -notmatch '^(?:classes\d*\.dex|resources\.arsc|assets/.+|AndroidManifest\.xml)$') { continue }
            $stream = $entry.Open()
            $buffer = [System.IO.MemoryStream]::new()
            try { $stream.CopyTo($buffer); $data = $buffer.ToArray() }
            finally { $buffer.Dispose(); $stream.Dispose() }
            $texts = if ($entry.FullName -match '^classes\d*\.dex$') { @([PublishAuditDex]::Strings($data)) }
                else { @([System.Text.Encoding]::UTF8.GetString($data), [System.Text.Encoding]::Unicode.GetString($data)) }
            foreach ($text in $texts) {
                foreach ($kind in $patterns.Keys) {
                    if (Test-PrivacyPattern $text $kind) { Add-Issue ('Apk' + $kind) $entry.FullName }
                }
            }
        }
    } finally { $archive.Dispose() }
}

$gitName = & git -C $moduleRoot config --get user.name
$hasName = -not [string]::IsNullOrWhiteSpace($gitName)
$gitEmail = & git -C $moduleRoot config --get user.email
$hasEmail = -not [string]::IsNullOrWhiteSpace($gitEmail)
$noreply = $hasEmail -and $gitEmail -match '@users\.noreply\.github\.com$'
if ($ReleaseCheck) {
    if (-not (Test-Path -LiteralPath (Join-Path $moduleRoot 'LICENSE') -PathType Leaf)) { Add-Issue 'MissingLicense' 'LICENSE' }
    if (-not $hasName) { Add-Issue 'MissingGitAuthor' '(local Git configuration)' }
    if (-not $noreply) { Add-Issue 'GitEmailReview' '(local Git configuration)' }
}
$report = [pscustomobject]@{
    Files = $files.Count; ApkEntries = $apkEntries; ReleaseCheck = [bool]$ReleaseCheck
    GitNameConfigured = $hasName; GitEmailConfigured = $hasEmail; GitEmailUsesNoreply = [bool]$noreply
    Findings = @($issues.ToArray())
    Limits = 'Heuristic review only. Check Git history, public identity, licensing, screenshots and release assets separately.'
}
[System.IO.Directory]::CreateDirectory((Split-Path -Parent $ReportPath)) | Out-Null
[System.IO.File]::WriteAllText($ReportPath, ($report | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
Write-Output "Reviewed $($files.Count) candidate Git files; $apkEntries APK entries; $($issues.Count) review items."
if ($issues.Count) { $issues | Format-Table Kind, File, Line -AutoSize; throw 'Publish audit has unresolved review items. Values were not printed.' }
Write-Output 'No configured privacy patterns matched. Manual review and publication decisions are still required.'
