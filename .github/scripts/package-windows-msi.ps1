<#
Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
Copyright (C) 2024-2026, zly2006 <i@zly2006.me>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation (version 3 only).

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$AppImage,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$WixDirectory = $env:WIX_PATH
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed ($LASTEXITCODE)" }
}

$AppImage = (Resolve-Path -LiteralPath $AppImage).Path
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $OutputDirectory) { throw 'OutputDirectory must not already exist.' }
if ($OutputDirectory.StartsWith($AppImage.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must be outside the input app-image.'
}
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
foreach ($file in @('Zhihu++.exe', 'app\Zhihu++.cfg', 'runtime\bin\server\jvm.dll',
                    'app\resources\reading\zhihu-reading-host.exe',
                    'app\resources\native-notifications\ZhihuPortableNotifications.dll',
                    'app\resources\native-notifications\ZhihuNotificationActivator.exe')) {
    if (!(Test-Path -LiteralPath (Join-Path $AppImage $file) -PathType Leaf)) { throw "Incomplete app-image: $file" }
}
$cfg = [IO.File]::ReadAllText((Join-Path $AppImage 'app\Zhihu++.cfg'))
if ($cfg -notmatch '(?m)^app\.mainclass=com\.github\.zly2006\.zhihu\.desktop\.MainKt\r?$') { throw 'Unexpected application main class.' }
$versionMatch = [regex]::Match($cfg, '(?m)^java-options=-Djpackage\.app-version=(\d+\.\d+\.\d+)\r?$')
if (!$versionMatch.Success) { throw 'Expected the three-part application version in the jpackage configuration.' }
$version = $versionMatch.Groups[1].Value
# The JNI share library lives in the application JAR, not in appResources.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$hasShare = $false
foreach ($jar in Get-ChildItem -LiteralPath (Join-Path $AppImage 'app') -Filter '*.jar' -File) {
    $archive = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try { if ($null -ne $archive.GetEntry('native-share/ZhihuFileShare.dll')) { $hasShare = $true } }
    finally { $archive.Dispose() }
}
if (!$hasShare) { throw 'App-image does not contain the native-share JNI resource.' }
if (!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'jmods\jdk.jpackage.jmod'))) {
    throw 'Pass a full desktop JDK containing jmods\jdk.jpackage.jmod with -JavaHome.'
}
if (!$WixDirectory -and (Test-Path -LiteralPath (Join-Path $root 'build\wix311\candle.exe'))) {
    $WixDirectory = Join-Path $root 'build\wix311'
}
if (!$WixDirectory -and $env:WIX) { $WixDirectory = Join-Path $env:WIX 'bin' }
if (!$WixDirectory) {
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    if ($candle) { $WixDirectory = Split-Path $candle.Source -Parent }
}
foreach ($tool in @('candle.exe', 'light.exe')) {
    if (!$WixDirectory -or !(Test-Path -LiteralPath (Join-Path $WixDirectory $tool))) {
        throw 'WiX 3 is required; pass WixDirectory or set WIX_PATH to its binary directory.'
    }
}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$resources = Join-Path $OutputDirectory 'resources'
New-Item -ItemType Directory -Path $resources | Out-Null
$jdkResources = Join-Path $OutputDirectory 'jdk-resources'
Invoke-Checked (Join-Path $JavaHome 'bin\jmod.exe') @('extract', '--dir', $jdkResources, (Join-Path $JavaHome 'jmods\jdk.jpackage.jmod'))
# Use this exact JDK's template instead of maintaining a fork of the installer.
[xml]$main = [IO.File]::ReadAllText((Join-Path $jdkResources 'classes\jdk\jpackage\internal\resources\main.wxs'))
$ns = [Xml.XmlNamespaceManager]::new($main.NameTable)
$ns.AddNamespace('w', 'http://schemas.microsoft.com/wix/2006/wi')
$features = @($main.SelectNodes('/w:Wix/w:Product/w:Feature[@Id="DefaultFeature"]', $ns))
if ($features.Count -ne 1) { throw 'Unsupported jpackage WiX template: expected one DefaultFeature.' }
$fragmentPath = Join-Path $root 'desktopApp\native-notifications-portable\PortableNotifications.wxi'
[xml]$registration = [IO.File]::ReadAllText($fragmentPath)
$registrationNs = [Xml.XmlNamespaceManager]::new($registration.NameTable)
$registrationNs.AddNamespace('w', 'http://schemas.microsoft.com/wix/2006/wi')
$groupId = 'ZhihuPortableNotificationRegistration'
if (@($registration.SelectNodes("/w:Include/w:Fragment/w:ComponentGroup[@Id='$groupId']", $registrationNs)).Count -ne 1) {
    throw 'Missing portable notification registration group.'
}
$reference = $main.CreateElement('ComponentGroupRef', $ns.LookupNamespace('w'))
$reference.SetAttribute('Id', $groupId)
$null = $features[0].AppendChild($reference)
# Expanding the include here avoids relying on jpackage to copy nonstandard .wxi files.
foreach ($fragment in $registration.SelectNodes('/w:Include/w:Fragment', $registrationNs)) {
    $null = $main.DocumentElement.AppendChild($main.ImportNode($fragment, $true))
}
$null = $main.DocumentElement.PrependChild($main.CreateComment('PortableNotifications.wxi: Copyright (C) 2024-2026, zly2006. SPDX-License-Identifier: AGPL-3.0-only'))
$main.Save((Join-Path $resources 'main.wxs'))
Copy-Item -LiteralPath $fragmentPath -Destination $resources
$oldPath = $env:PATH
try {
    $env:PATH = "$WixDirectory;$oldPath"
    Invoke-Checked (Join-Path $JavaHome 'bin\jpackage.exe') @(
        '--type', 'msi', '--app-image', $AppImage, '--dest', $OutputDirectory,
        '--name', 'Zhihu++', '--app-version', $version, '--vendor', 'zly2006',
        '--description', 'Free and ad-free third-party Zhihu client',
        '--icon', (Join-Path $root 'desktopApp\desktop-icon.ico'),
        '--resource-dir', $resources, '--temp', (Join-Path $OutputDirectory 'jpackage-work'),
        '--win-per-user-install', '--win-shortcut', '--win-dir-chooser',
        '--win-upgrade-uuid', '84FED2C6-8F40-4FDA-B84A-616602D7A888'
    )
} finally { $env:PATH = $oldPath }
# PortableNotifications.wxi owns the single Start-menu shortcut, so --win-menu is deliberately absent.
$packages = @(Get-ChildItem -LiteralPath $OutputDirectory -Filter '*.msi' -File)
if ($packages.Count -ne 1 -or $packages[0].Length -eq 0) { throw 'jpackage did not produce exactly one MSI.' }
Write-Output $packages[0].FullName
Write-Output 'Per-user MSI built with installer-owned notification registration. No signing, installation, registration or application launch was performed.'
