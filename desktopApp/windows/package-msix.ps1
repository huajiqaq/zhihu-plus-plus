[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$AppImage,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9][A-Za-z0-9.-]{2,49}$')][string]$IdentityName,
    [Parameter(Mandatory = $true)][string]$Publisher,
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+\.\d+$')][string]$Version,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$WindowsSdkRoot,
    [string]$WindowsSdkVersion
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed ($LASTEXITCODE)" }
}
function Cpp-String([string]$Value) {
    if ($Value -match '[\x00-\x1f]') { throw 'Control character in package input' }
    return '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"'
}
function Write-Utf8([string]$Path, [string]$Value) {
    [IO.File]::WriteAllText($Path, $Value, [Text.UTF8Encoding]::new($false))
}
function Xml-Text([string]$Value) { return [Security.SecurityElement]::Escape($Value) }

$AppImage = (Resolve-Path -LiteralPath $AppImage).Path
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $OutputDirectory) { throw 'OutputDirectory must be new; never overwrite an app-image or existing output.' }
if ($OutputDirectory.StartsWith($AppImage.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'OutputDirectory must be outside the input app-image.' }
if ($Publisher -notmatch '^CN=' -or $Publisher -match '[\x00-\x1f]') { throw 'Publisher must be the explicit signing certificate subject (CN=...). This script never signs or installs.' }
$parts = @($Version.Split('.') | ForEach-Object { [uint32]$_ })
if ($parts[0] -eq 0 -or @($parts | Where-Object { $_ -gt 65535 }).Count) { throw 'MSIX requires four 16-bit version parts and a nonzero major.' }
if (!$JavaHome) {
    $java = (Get-Command javac.exe -ErrorAction Stop).Source
    $JavaHome = Split-Path (Split-Path $java -Parent) -Parent
}
if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'include\jni.h'))) { throw 'JDK 21 JAVA_HOME is required.' }

# Only the project's normal jpackage image is an input; no executable is run from it.
$cfgPath = Join-Path $AppImage 'app\Zhihu++.cfg'
$jvmPath = Join-Path $AppImage 'runtime\bin\server\jvm.dll'
if (!(Test-Path -LiteralPath $cfgPath) -or !(Test-Path -LiteralPath $jvmPath)) { throw 'Expected the Windows Zhihu++ jpackage app-image.' }
$reader = [IO.BinaryReader]::new([IO.File]::OpenRead($jvmPath))
try {
    if ($reader.BaseStream.Length -lt 64 -or $reader.ReadUInt16() -ne 0x5a4d) { throw 'Invalid JVM PE image.' }
    $reader.BaseStream.Position = 0x3c
    $pe = $reader.ReadUInt32()
    if ($pe -gt $reader.BaseStream.Length - 6) { throw 'Invalid JVM PE offset.' }
    $reader.BaseStream.Position = $pe
    if ($reader.ReadUInt32() -ne 0x4550 -or $reader.ReadUInt16() -ne 0x8664) { throw 'MSIX host requires an x64 app-image JVM.' }
} finally { $reader.Dispose() }
$cfg = @(Get-Content -LiteralPath $cfgPath)
if ('app.mainclass=com.github.zly2006.zhihu.desktop.MainKt' -notin $cfg) { throw 'Unexpected app-image main class.' }
$jars = @()
foreach ($line in $cfg) {
    if ($line.StartsWith('app.classpath=')) {
        foreach ($entry in $line.Substring('app.classpath='.Length).Split(';')) {
            if ($entry -notmatch '^\$APPDIR[\\/]([^\\/:;*?"<>|]+\.jar)$') { throw "Unsupported app-image classpath entry: $entry" }
            $jar = $Matches[1]
            if (!(Test-Path -LiteralPath (Join-Path $AppImage "app\$jar") -PathType Leaf)) { throw "Missing app-image jar: $jar" }
            $jars += $jar
        }
    }
}
if (!$jars.Count) { throw 'No application JARs in app-image configuration.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$requiredClasses = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$null = $requiredClasses.Add('com/github/zly2006/zhihu/desktop/MainKt.class')
$null = $requiredClasses.Add('com/github/zly2006/zhihu/notification/WindowsNotificationBridge.class')
foreach ($jar in $jars) {
    $archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $AppImage "app\$jar"))
    try {
        foreach ($entry in @($requiredClasses)) {
            if ($null -ne $archive.GetEntry($entry)) { $null = $requiredClasses.Remove($entry) }
        }
    } finally { $archive.Dispose() }
}
if ($requiredClasses.Count) { throw 'App-image does not contain the retained Windows JNI bridge and MainKt. Rebuild the current release distribution.' }
# Do not turn jpackage cfg into a general JVM-option runner. Extend this allowlist only with a reviewed project need.
$allowedOptions = @('-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Dcompose.application.configure.swing.globals=true', '--enable-native-access=ALL-UNNAMED', '-XX:+IgnoreUnrecognizedVMOptions')
$options = @()
$appVersion = $null
foreach ($line in $cfg) {
    if (!$line.StartsWith('java-options=')) { continue }
    $option = $line.Substring('java-options='.Length)
    if ($option -cmatch '^-Djpackage\.app-version=([0-9][0-9A-Za-z.+_-]{0,63})$') {
        if ($null -ne $appVersion) { throw 'Duplicate jpackage application version.' }
        $appVersion = $Matches[1]
        continue
    }
    if ($option -ceq '-Dskiko.library.path=$APPDIR') {
        if (!(Test-Path -LiteralPath (Join-Path $AppImage 'app\skiko-windows-x64.dll') -PathType Leaf)) { throw 'Missing packaged Skiko library.' }
        continue
    }
    if ($option -cmatch '^-D(jpackage\.app-path|compose\.application\.resources\.dir)=') { continue }
    if ($option -cnotin $allowedOptions) { throw "Unreviewed JVM option in app-image: $option" }
    $options += $option
}
if (!$appVersion) { throw 'Missing jpackage application version.' }
if (@(Get-ChildItem -LiteralPath $AppImage -Recurse -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'App-image must not contain reparse points.' }

$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$vs = @(& $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath)
if ($LASTEXITCODE -ne 0 -or $vs.Count -ne 1) { throw 'Install Visual Studio C++ desktop tools.' }
$cmake = Join-Path $vs[0] 'Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe'
if (!(Test-Path -LiteralPath $cmake)) { $cmake = (Get-Command cmake.exe -ErrorAction Stop).Source }
$vsVersion = @(& $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationVersion)[0]
$generator = switch (([version]$vsVersion).Major) { 17 { 'Visual Studio 17 2022' } 18 { 'Visual Studio 18 2026' } default { throw 'Use a supported Visual Studio CMake generator.' } }
if (!$WindowsSdkRoot) { $WindowsSdkRoot = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows Kits\Installed Roots' -Name KitsRoot10).KitsRoot10 }
if (!$WindowsSdkVersion) {
    $WindowsSdkVersion = Get-ChildItem (Join-Path $WindowsSdkRoot 'bin') -Directory | Where-Object {
        $_.Name -match '^10\.0\.\d+\.0$' -and (Test-Path (Join-Path $_.FullName 'x64\makeappx.exe'))
    } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1 -ExpandProperty Name
}
$makeappx = Join-Path $WindowsSdkRoot "bin\$WindowsSdkVersion\x64\makeappx.exe"
if (!(Test-Path -LiteralPath $makeappx)) { throw 'Windows SDK MakeAppx is required.' }

New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$inputs = Join-Path $OutputDirectory 'inputs'
$build = Join-Path $OutputDirectory 'native-build'
$stage = Join-Path $OutputDirectory 'package'
New-Item -ItemType Directory -Path $inputs, $stage | Out-Null
$header = "#pragma once`n#include <array>`n"
$header += 'inline constexpr wchar_t PackageName[] = L' + (Cpp-String $IdentityName) + ";`n"
$header += 'inline constexpr wchar_t PackagePublisher[] = L' + (Cpp-String $Publisher) + ";`n"
$header += 'inline constexpr wchar_t ApplicationVersion[] = L' + (Cpp-String $appVersion) + ";`n"
$header += 'inline constexpr std::array<const wchar_t*, ' + $jars.Count + '> ApplicationJars{' + (($jars | ForEach-Object { 'L' + (Cpp-String $_) }) -join ', ') + "};`n"
$header += 'inline constexpr std::array<const char*, ' + $options.Count + '> ApplicationOptions{' + (($options | ForEach-Object { Cpp-String $_ }) -join ', ') + "};`n"
Write-Utf8 (Join-Path $inputs 'package_config.h') $header
$oldJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    Invoke-Checked $cmake @('-S', $PSScriptRoot, '-B', $build, '-G', $generator, '-A', 'x64', "-DZHIHU_PACKAGE_INPUTS=$inputs", "-DCMAKE_SYSTEM_VERSION=$WindowsSdkVersion")
    Invoke-Checked $cmake @('--build', $build, '--config', 'Release')
} finally { $env:JAVA_HOME = $oldJavaHome }
Copy-Item -LiteralPath $AppImage -Destination (Join-Path $stage 'app-image') -Recurse
Copy-Item -LiteralPath (Join-Path $build 'Release\ZhihuNotifications.exe') -Destination $stage
$manifest = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'AppxManifest.xml.in')).Replace('@NAME@', (Xml-Text $IdentityName)).Replace('@PUBLISHER@', (Xml-Text $Publisher)).Replace('@VERSION@', $Version)
Write-Utf8 (Join-Path $stage 'AppxManifest.xml') $manifest

# Package logos are resized from this project's icon; no downloaded assets or external imaging runtime.
Add-Type -AssemblyName System.Drawing
$assets = Join-Path $stage 'Assets'
New-Item -ItemType Directory -Path $assets | Out-Null
$icon = [Drawing.Image]::FromFile((Join-Path $PSScriptRoot '..\src\main\resources\desktop-icon.png'))
try {
    foreach ($asset in @(@('StoreLogo', 50), @('Square44x44Logo', 44), @('Square150x150Logo', 150))) {
        $bitmap = [Drawing.Bitmap]::new([int]$asset[1], [int]$asset[1])
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.DrawImage($icon, 0, 0, [int]$asset[1], [int]$asset[1])
            $bitmap.Save((Join-Path $assets ($asset[0] + '.png')), [Drawing.Imaging.ImageFormat]::Png)
        } finally { $graphics.Dispose(); $bitmap.Dispose() }
    }
} finally { $icon.Dispose() }
Invoke-Checked $makeappx @('pack', '/d', $stage, '/p', (Join-Path $OutputDirectory 'Zhihu++.unsigned.msix'), '/o')
Write-Output 'Unsigned MSIX created and SDK-validated. No signing, installation, registration, application launch or notification was performed.'
